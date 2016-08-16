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

import org.HdrHistogram.AtomicHistogram;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 *
 */
public class LoadGenerator
{

    private static final Logger LOGGER = Log.getLogger( LoadGenerator.class );

    private int users;

    private volatile int requestRate;

    private AtomicBoolean stop;

    private int selectors = 1;

    private String scheme;

    private String host;

    private int port;

    private LoadGeneratorProfile loadGeneratorProfile;

    private Transport transport;

    private HttpClientTransport httpClientTransport;

    private SslContextFactory sslContextFactory;

    private List<Request.Listener> requestListeners;

    private ExecutorService executorService;

    private CopyOnWriteArrayList<HttpClient> clients = new CopyOnWriteArrayList<>();

    private Scheduler httpScheduler;

    private SocketAddressResolver socketAddressResolver;

    private Map<String, AtomicHistogram> histogramPerPath = new ConcurrentHashMap<>();

    protected enum Transport
    {
        HTTP,
        HTTPS,
        H2C,
        H2,
        FCGI
    }

    LoadGenerator( int users, int requestRate, String host, int port, LoadGeneratorProfile loadGeneratorProfile )
    {
        this.users = users;
        this.requestRate = requestRate;
        this.host = host;
        this.port = port;
        this.stop = new AtomicBoolean( false );
        this.loadGeneratorProfile = loadGeneratorProfile;
    }

    //--------------------------------------------------------------
    //  getters
    //--------------------------------------------------------------

    public int getUsers()
    {
        return users;
    }

    public int getRequestRate()
    {
        return requestRate;
    }

    public void setRequestRate( int requestRate )
    {
        this.requestRate = requestRate;
    }

    public String getHost()
    {
        return host;
    }

    public int getPort()
    {
        return port;
    }

    public Transport getTransport()
    {
        return transport;
    }

    public HttpClientTransport getHttpClientTransport()
    {
        return httpClientTransport;
    }

    public SslContextFactory getSslContextFactory()
    {
        return sslContextFactory;
    }

    public int getSelectors()
    {
        return selectors;
    }

    public List<Request.Listener> getRequestListeners()
    {
        return requestListeners;
    }

    public AtomicBoolean getStop()
    {
        return stop;
    }

    public Scheduler getHttpScheduler()
    {
        return httpScheduler;
    }

    public SocketAddressResolver getSocketAddressResolver()
    {
        return socketAddressResolver;
    }

    public LoadGeneratorProfile getLoadGeneratorProfile()
    {
        return loadGeneratorProfile;
    }

    public String getScheme()
    {
        return scheme;
    }

    public Map<String, AtomicHistogram> getHistogramPerPath()
    {
        return histogramPerPath;
    }

    //--------------------------------------------------------------
    //  component implementation
    //--------------------------------------------------------------

    /**
     * start the generator lifecycle (this doesn't send any requests but just start few internal components)
     */
    public LoadGenerator start()
    {
        this.executorService = Executors.newWorkStealingPool( this.getUsers() );

        return this;
    }

    /**
     * stop (clear resources) the generator lifecycle
     */
    public LoadGenerator stop()
    {
        this.stop.set( true );
        try
        {
            for ( HttpClient httpClient : this.clients )
            {
                httpClient.stop();
            }
        }
        catch ( Exception e )
        {
            throw new RuntimeException( e.getMessage(), e.getCause() );
        }
        return this;
    }

    /**
     * run the defined load (users / request numbers)
     */
    public LoadGeneratorResult run()
        throws Exception
    {

        LoadGeneratorResult loadGeneratorResult = new LoadGeneratorResult();

        // we iterate over all request path to create HdrHistogram now
        // and do not have to worry about sync after that

        this.histogramPerPath = new ConcurrentHashMap<>(  );

        for ( LoadGeneratorProfile.Step step : getLoadGeneratorProfile().getSteps() )
        {
            for ( LoadGeneratorProfile.Resource resource : step.getResources() )
            {
                String path = resource.getPath();
                path = path == null ? "" : path.trim();
                if ( !histogramPerPath.containsKey( path ) )
                {
                    if (StringUtil.isBlank( path ))
                    {
                        path = "/";
                    }

                    AtomicHistogram atomicHistogram = new AtomicHistogram( TimeUnit.MICROSECONDS.toNanos( 1 ), //
                                                                           TimeUnit.MINUTES.toNanos( 1 ), //
                                                                           3 ) {

                        // a more human readable toString
                        @Override
                        public String toString()
                        {
                            return "max: " + this.getMaxValue()  //
                                + ", min: " + this.getMinValue() //
                                + ", mean: " + this.getMean() //
                                + ", minNonZero:" + this.getMinNonZeroValue() //
                                + ", totalCount:" + this.getTotalCount();
                        }

                    };

                    histogramPerPath.put( path, atomicHistogram );
                }
            }
        }

        LoadGeneratorResultHandler loadGeneratorResultHandler =
            new LoadGeneratorResultHandler( loadGeneratorResult, histogramPerPath );

        List<Request.Listener> listeners = new ArrayList<>( getRequestListeners() );

        listeners.add( loadGeneratorResultHandler );

        Executors.newWorkStealingPool( this.getUsers()).submit( () -> //
        {
            HttpClientTransport httpClientTransport = this.getHttpClientTransport() != null ? //
                this.getHttpClientTransport() : provideClientTransport( this.getTransport() );

            for (int i = this.getUsers(); i > 0; i--)
            {
                try
                {

                    HttpClient httpClient = newHttpClient( httpClientTransport, getSslContextFactory() );

                    // TODO dynamic depending on the rate??
                    httpClient.setMaxRequestsQueuedPerDestination( 2048 );

                    httpClient.setSocketAddressResolver( this.getSocketAddressResolver() );

                    this.clients.add( httpClient );

                    httpClient.getRequestListeners().add( loadGeneratorResultHandler );

                    httpClient.getRequestListeners().addAll( listeners );

                    LoadGeneratorRunner loadGeneratorRunner =
                        new LoadGeneratorRunner( httpClient, this, loadGeneratorResultHandler );

                    this.executorService.submit( loadGeneratorRunner );
                }
                catch ( Exception e )
                {
                    LOGGER.warn( "ignore exception", e );
                }
            }

            try
            {
                while ( !this.stop.get() )
                {
                    // wait until stopped
                    Thread.sleep( 1 );
                }
            }
            catch ( Throwable e )
            {
                LOGGER.warn( "ignore exception", e );
            }


        } );

        return loadGeneratorResult;
    }


    protected HttpClient newHttpClient( HttpClientTransport transport, SslContextFactory sslContextFactory )
        throws Exception
    {
        HttpClient httpClient = new HttpClient( transport, sslContextFactory );
        switch ( this.getTransport() )
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
            TODO
            case FCGI:
            {

            }
            */
            default:
            {
                // nothing this weird case already handled by #provideClientTransport
            }

        }


        // FIXME weird circularity
        transport.setHttpClient( httpClient );
        httpClient.start();

        if ( this.getHttpScheduler() != null )
        {
            httpClient.setScheduler( this.getHttpScheduler() );
        }

        return httpClient;
    }

    protected HttpClientTransport provideClientTransport( Transport transport )
    {
        switch ( transport )
        {
            case HTTP:
            case HTTPS:
            {
                return new HttpClientTransportOverHTTP( getSelectors() );
            }
            case H2C:
            case H2:
            {
                HTTP2Client http2Client = newHTTP2Client();
                return new HttpClientTransportOverHTTP2( http2Client );
            }
            /*
            TODO
            case FCGI:
            {
                return new HttpClientTransportOverFCGI(1, false, "");
            }
            */
            default:
            {
                throw new IllegalArgumentException();
            }
        }
    }


    protected HTTP2Client newHTTP2Client()
    {
        HTTP2Client http2Client = new HTTP2Client();
        http2Client.setSelectors( getSelectors() );
        return http2Client;
    }

    //--------------------------------------------------------------
    //  Builder
    //--------------------------------------------------------------

    public static class Builder
    {

        private int users;

        private int requestRate;

        private String scheme = "http";

        private String host;

        private int port;

        private Transport transport;

        private HttpClientTransport httpClientTransport;

        private SslContextFactory sslContextFactory;

        private int selectors = 1;

        private List<Request.Listener> requestListeners;

        private Scheduler httpScheduler;

        private SocketAddressResolver socketAddressResolver;

        private LoadGeneratorProfile loadGeneratorProfile;

        public static Builder builder()
        {
            return new Builder();
        }

        private Builder()
        {
            // no op
        }

        public Builder users( int users )
        {
            this.users = users;
            return this;
        }
        /**
         *
         * @param requestRate number of requests per second
         * @return {@link Builder}
         */
        public Builder requestRate( int requestRate )
        {
            this.requestRate = requestRate;
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

        public Builder transport( Transport transport )
        {
            this.transport = transport;
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

        public Builder selectors( int selectors )
        {
            this.selectors = selectors;
            return this;
        }

        public Builder scheme( String scheme )
        {
            this.scheme = scheme;
            return this;
        }

        public Builder requestListeners( List<Request.Listener> requestListeners )
        {
            this.requestListeners = requestListeners;
            return this;
        }

        public Builder httpClientScheduler( Scheduler scheduler )
        {
            this.httpScheduler = scheduler;
            return this;
        }

        public Builder httpClientSocketAddressResolver( SocketAddressResolver socketAddressResolver )
        {
            this.socketAddressResolver = socketAddressResolver;
            return this;
        }

        public Builder loadGeneratorWorkflow( LoadGeneratorProfile loadGeneratorProfile )
        {
            this.loadGeneratorProfile = loadGeneratorProfile;
            return this;
        }

        public LoadGenerator build()
        {
            this.validate();
            LoadGenerator loadGenerator =
                new LoadGenerator( users, requestRate, host, port, this.loadGeneratorProfile );
            loadGenerator.transport = this.transport;
            loadGenerator.requestListeners = this.requestListeners == null ? new ArrayList<>() // //
                : this.requestListeners;
            loadGenerator.httpClientTransport = httpClientTransport;
            loadGenerator.sslContextFactory = sslContextFactory;
            loadGenerator.selectors = selectors;
            loadGenerator.scheme = scheme;
            loadGenerator.httpScheduler = httpScheduler;
            loadGenerator.socketAddressResolver = socketAddressResolver == null ? //
                new SocketAddressResolver.Sync() : socketAddressResolver;
            return loadGenerator;
        }

        public void validate()
        {
            if ( users < 1 )
            {
                throw new IllegalArgumentException( "users number must be at least 1" );
            }

            if ( requestRate < 0 )
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

            if (this.loadGeneratorProfile == null) {
                throw new IllegalArgumentException( "a loadGeneratorProfile is mandatory" );
            }

        }

    }

}
