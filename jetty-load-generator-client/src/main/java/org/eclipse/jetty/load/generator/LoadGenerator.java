//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.load.generator;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpProxy;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.load.generator.responsetime.ResponseTimeListener;
import org.eclipse.jetty.load.generator.profile.ResourceProfile;
import org.eclipse.jetty.toolchain.perf.PlatformTimer;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.annotation.ManagedOperation;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
@ManagedObject("this is the Jetty LoadGenerator")
public class LoadGenerator
    extends ContainerLifeCycle
{

    private static final Logger LOGGER = Log.getLogger( LoadGenerator.class );

    private int users;

    private volatile int transactionRate;

    private AtomicBoolean stop;

    /**
     * target host scheme
     */
    private String scheme;

    /**
     * target host
     */
    private String host;

    /**
     * target host port
     */
    private int port;

    private ResourceProfile profile;

    private HttpClientTransport httpClientTransport;

    private SslContextFactory sslContextFactory;

    private List<Request.Listener> requestListeners;

    private ExecutorService executorService;

    private ExecutorService runnersExecutorService;

    private CopyOnWriteArrayList<HttpClient> clients = new CopyOnWriteArrayList<>();

    private Scheduler scheduler;

    private SocketAddressResolver socketAddressResolver;

    private List<ResponseTimeListener> responseTimeListeners;

    private LoadGeneratorResultHandler _loadGeneratorResultHandler;

    private List<HttpProxy> httpProxies;

    private Transport transport;

    /**
     * path of the {@link org.eclipse.jetty.servlet.StatisticsServlet} on server side
     */
    private String statisticsPath = "/stats";

    public enum Transport
    {
        HTTP,
        HTTPS,
        H2C,
        H2,
        FCGI
    }

    LoadGenerator( int users, int transactionRate, String host, int port, ResourceProfile profile )
    {
        this.users = users;
        this.transactionRate = transactionRate;
        this.host = host;
        this.port = port;
        this.stop = new AtomicBoolean( false );
        this.profile = profile;
    }

    //--------------------------------------------------------------
    //  getters
    //--------------------------------------------------------------

    public int getUsers()
    {
        return users;
    }

    public int getTransactionRate()
    {
        return transactionRate;
    }

    @ManagedOperation
    public void setTransactionRate( int transactionRate )
    {
        this.transactionRate = transactionRate;
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }

    public HttpClientTransport getHttpClientTransport()
    {
        return httpClientTransport;
    }

    public SslContextFactory getSslContextFactory()
    {
        return sslContextFactory;
    }

    public List<Request.Listener> getRequestListeners()
    {
        return requestListeners;
    }

    public AtomicBoolean getStop()
    {
        return stop;
    }

    public Scheduler getScheduler()
    {
        return scheduler;
    }

    public SocketAddressResolver getSocketAddressResolver()
    {
        return socketAddressResolver;
    }

    public ResourceProfile getProfile()
    {
        return profile;
    }

    public String getScheme()
    {
        return scheme;
    }

    //--------------------------------------------------------------
    //  component implementation
    //--------------------------------------------------------------

    /**
     * start the generator lifecycle (this doesn't send any requests but just start few internal components)
     */
    private LoadGenerator startIt()
    {
        this.scheme = scheme( this.transport );

        int parallelism = Math.min( Runtime.getRuntime().availableProcessors(), getUsers() );

        this.executorService = Executors.newWorkStealingPool( parallelism );

        this.runnersExecutorService = Executors.newWorkStealingPool( parallelism );

        _loadGeneratorResultHandler = new LoadGeneratorResultHandler( responseTimeListeners );

        return this;

    }

    /**
     * interrupt (clear resources) the generator lifecycle
     */
    @ManagedOperation
    public void interrupt()
    {
        this.stop.set( true );

        collectStats();

        try
        {
            this.runnersExecutorService.shutdown();
            // wait the end?
            while( !runnersExecutorService.isTerminated()) {
                Thread.sleep( 2 );
            }

            this.executorService.shutdown();

            // wait the end?
            while( !executorService.isTerminated()) {
                Thread.sleep( 2 );
            }

            if ( responseTimeListeners != null )
            {
                for ( ResponseTimeListener responseTimeListener : responseTimeListeners )
                {
                    responseTimeListener.onLoadGeneratorStop();
                }
            }

            for ( HttpClient httpClient : this.clients )
            {
                httpClient.stop();
            }
        }
        catch ( Exception e )
        {
            LOGGER.warn( e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e.getCause() );
        }
    }

    /**
     * run the defined load (users / request numbers)
     */
    public void run()
        throws Exception
    {

        final List<Request.Listener> listeners = new ArrayList<>( getRequestListeners() );

        listeners.add( _loadGeneratorResultHandler );

        statsReset();

        executorService.submit( () ->
            {
                HttpClientTransport httpClientTransport = getHttpClientTransport();

                for ( int i = getUsers(); i > 0; i-- )
                {
                    try
                    {
                        HttpClient httpClient = newHttpClient( httpClientTransport, getSslContextFactory() );
                        // TODO dynamic depending on the rate??
                        httpClient.setMaxRequestsQueuedPerDestination( 2048 );
                        httpClient.setSocketAddressResolver( getSocketAddressResolver() );
                        httpClient.getRequestListeners().addAll( listeners );
                        if (this.httpProxies != null) {
                            httpClient.getProxyConfiguration().getProxies().addAll( this.httpProxies );
                        }
                        clients.add( httpClient );

                        LoadGeneratorRunner loadGeneratorRunner = //
                            new LoadGeneratorRunner( httpClient, this, _loadGeneratorResultHandler );

                        LoadGenerator.this.runnersExecutorService.submit( loadGeneratorRunner );
                    }
                    catch ( Exception e )
                    {
                        LOGGER.warn( "skip exception:" + e.getMessage(), e );
                        this.stop.set( true );
                    }
                }

                try
                {
                    while ( !LoadGenerator.this.stop.get() )
                    {
                        // wait until stopped
                        Thread.sleep( 1 );
                    }
                }
                catch ( Throwable e )
                {
                    LOGGER.warn( "skip exception:" + e.getMessage(), e );
                }
                LOGGER.debug( "exit run lambda" );
            }
        );
    }

    public void run( long time, TimeUnit timeUnit )
        throws Exception
    {
        this.run();
        PlatformTimer.detect().sleep( timeUnit.toMicros( time ) );
        this.interrupt();
    }

    /**
     * reset stats on Server side
     */
    protected void statsReset()
    {
        HttpClient httpClient = null;
        try
        {
            httpClient = newHttpClient( httpClientTransport, getSslContextFactory() );
            final String uri = getScheme() + "://" + getHost() + ":" + getPort() + statisticsPath + "?statsReset=true";
            Request request = httpClient.newRequest( uri );
            ContentResponse contentResponse = request.send();
            if (LOGGER.isDebugEnabled())
            {
                LOGGER.debug( "stats reset status: {}", contentResponse.getStatus() );
            }
            if (contentResponse.getStatus() != HttpServletResponse.SC_OK ) {
                LOGGER.warn( "cannot reset stats on Server side" );
            }
        }
        catch ( Exception e )
        {
            LOGGER.warn( "skip error getting stats", e );
        }
        finally
        {
            stopQuietly( httpClient );
        }
    }

    /**
     * collect stats on server side
     */
    protected void collectStats()
    {
        HttpClient httpClient = null;
        try
        {
            httpClient = newHttpClient( httpClientTransport, getSslContextFactory() );
            final String uri = getScheme() + "://" + getHost() + ":" + getPort() + statisticsPath + "?xml=true";
            Request request = httpClient.newRequest( uri );
            ContentResponse contentResponse = request.send();
            LOGGER.info( "content response xml: {}", contentResponse.getContentAsString() );
        }
        catch ( Exception e )
        {
            LOGGER.warn( "skip error getting stats", e );
        }
        finally
        {
            stopQuietly( httpClient );
        }
    }

    private void stopQuietly( HttpClient httpClient )
    {
        if ( httpClient != null )
        {
            try
            {
                httpClient.stop();
            }
            catch ( Exception e )
            {
                LOGGER.warn( "skip issue stopping httpclient:" + e.getMessage() );
            }
        }
    }

    protected HttpClient newHttpClient( HttpClientTransport httpClientTransport, SslContextFactory sslContextFactory )
        throws Exception
    {
        HttpClient httpClient = new HttpClient( httpClientTransport, sslContextFactory );
        switch ( this.transport )
        {
            case HTTP:
            case HTTPS:
            {
                httpClient.setMaxConnectionsPerDestination( 7 );
            }
            case H2C:
            case H2:
            {
                httpClient.setMaxConnectionsPerDestination( 1 );
            }
            /*
            no op
            case FCGI:
            {

            }
            */
            default:
            {
                // nothing this weird case already handled by #provideClientTransport
            }

        }

        // TODO ?
        //httpClient.setExecutor(  );

        if ( this.getScheduler() != null )
        {
            httpClient.setScheduler( this.getScheduler() );
        }

        //httpClientTransport.setHttpClient( httpClient );
        httpClient.start();

        return httpClient;
    }


    static String scheme( LoadGenerator.Transport transport)
    {
        switch ( transport )
        {
            case HTTP:
            case H2C:
            case FCGI:
                return HttpScheme.HTTP.asString();
            case HTTPS:
            case H2:
                return HttpScheme.HTTPS.asString();
            default:
                throw new IllegalArgumentException( "unknow scheme" );
        }

    }


    //--------------------------------------------------------------
    //  Builder
    //--------------------------------------------------------------

    public static class Builder
    {

        private int users;

        private int transactionRate;

        private String host;

        private int port;

        private HttpClientTransport httpClientTransport;

        private SslContextFactory sslContextFactory;

        private List<Request.Listener> requestListeners;

        private Scheduler httpScheduler;

        private SocketAddressResolver socketAddressResolver;

        private ResourceProfile profile;

        private List<ResponseTimeListener> responseTimeListeners;

        private List<HttpProxy> httpProxies;

        private Transport transport;

        private String statisticsPath = "/stats";

        public Builder()
        {
            // no op
        }

        public Builder users( int users )
        {
            this.users = users;
            return this;
        }

        /**
         * @param transactionRate number of transaction per second (transaction means the whole profile)
         * @return {@link Builder}
         */
        public Builder transactionRate( int transactionRate )
        {
            this.transactionRate = transactionRate;
            return this;
        }

        public Builder host( String host )
        {
            this.host = host;
            return this;
        }

        public Builder port( int port )
        {
            this.port = port;
            return this;
        }

        public Builder httpClientTransport( HttpClientTransport httpClientTransport )
        {
            this.httpClientTransport = httpClientTransport;
            return this;
        }

        public Builder sslContextFactory( SslContextFactory sslContextFactory )
        {
            this.sslContextFactory = sslContextFactory;
            return this;
        }

        public Builder requestListeners( Request.Listener... requestListeners )
        {
            this.requestListeners = new ArrayList<>( Arrays.asList( requestListeners ) );
            return this;
        }

        public Builder scheduler( Scheduler scheduler )
        {
            this.httpScheduler = scheduler;
            return this;
        }

        public Builder httpClientSocketAddressResolver( SocketAddressResolver socketAddressResolver )
        {
            this.socketAddressResolver = socketAddressResolver;
            return this;
        }

        public Builder loadProfile( ResourceProfile resourceProfile )
        {
            this.profile = resourceProfile;
            return this;
        }

        public Builder responseTimeListeners( ResponseTimeListener... responseTimeListeners )
        {
            this.responseTimeListeners = new ArrayList<>( Arrays.asList( responseTimeListeners ) );
            return this;
        }


        public Builder httpProxies( HttpProxy... httpProxies )
        {
            this.httpProxies = new ArrayList<>( Arrays.asList( httpProxies ) );
            return this;
        }

        public Builder transport( Transport transport )
        {
            this.transport = transport;
            return this;
        }

        public Builder statisticsPath( String statisticsPath )
        {
            this.statisticsPath = statisticsPath;
            return this;
        }

        public LoadGenerator build()
        {
            this.validate();
            LoadGenerator loadGenerator =
                new LoadGenerator( users, transactionRate, host, port, this.profile );
            loadGenerator.requestListeners = this.requestListeners == null ? new ArrayList<>() // //
                : this.requestListeners;
            loadGenerator.httpClientTransport = httpClientTransport;
            loadGenerator.sslContextFactory = sslContextFactory;
            loadGenerator.scheduler = httpScheduler;
            loadGenerator.socketAddressResolver = socketAddressResolver == null ? //
                new SocketAddressResolver.Sync() : socketAddressResolver;
            loadGenerator.responseTimeListeners = responseTimeListeners;
            loadGenerator.httpProxies = httpProxies;
            loadGenerator.transport = transport;
            loadGenerator.statisticsPath = statisticsPath;
            return loadGenerator.startIt();
        }

        public void validate()
        {
            if ( users < 1 )
            {
                throw new IllegalArgumentException( "users number must be at least 1" );
            }

            if ( transactionRate < 0 )
            {
                throw new IllegalArgumentException( "users number must be at least 0" );
            }

            if ( StringUtil.isBlank( host ) )
            {
                throw new IllegalArgumentException( "host cannot be null or blank" );
            }

            if ( port < 1 )
            {
                throw new IllegalArgumentException( "port must be a positive integer" );
            }

            if ( this.profile == null )
            {
                throw new IllegalArgumentException( "a profile is mandatory" );
            }

            if (this.httpClientTransport == null)
            {
                throw new IllegalArgumentException( "httpClientTransport cannot be null" );
            }

        }

    }

}
