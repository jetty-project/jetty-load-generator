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

import org.HdrHistogram.Recorder;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.load.generator.latency.LatencyListener;
import org.eclipse.jetty.load.generator.latency.LatencyRecorder;
import org.eclipse.jetty.load.generator.latency.LatencyValueListener;
import org.eclipse.jetty.load.generator.latency.SummaryLatencyListener;
import org.eclipse.jetty.load.generator.response.ResponseTimeListener;
import org.eclipse.jetty.load.generator.response.ResponseTimeRecorder;
import org.eclipse.jetty.load.generator.response.ResponseTimeValueListener;
import org.eclipse.jetty.load.generator.response.SummaryResponseTimeListener;
import org.eclipse.jetty.toolchain.perf.PlatformTimer;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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

    /**
     * port used to collect statistics data
     */
    private int collectorPort = 0;

    private LoadGeneratorProfile loadGeneratorProfile;

    private Transport transport;

    private HttpClientTransport httpClientTransport;

    private SslContextFactory sslContextFactory;

    private List<Request.Listener> requestListeners;

    private ExecutorService executorService;

    private CopyOnWriteArrayList<HttpClient> clients = new CopyOnWriteArrayList<>();

    private Scheduler httpScheduler;

    private SocketAddressResolver socketAddressResolver;

    private CollectorServer collectorServer;

    private List<LatencyListener> latencyListeners;

    private List<LatencyValueListener> latencyValueListeners;

    private List<ResponseTimeListener> responseTimeListeners;

    private List<ResponseTimeValueListener> responseTimeValueListeners;

    private SchedulerDetails latencySchedulerDetails, responseTimeSchedulerDetails;

    private LoadGeneratorResult loadGeneratorResult;

    private LoadGeneratorResultHandler _loadGeneratorResultHandler;

    private Map<String, Recorder> _recorderPerPath;

    private boolean latencyListening;

    public enum Transport
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

    public int getCollectorPort()
    {
        return collectorPort;
    }

    //--------------------------------------------------------------
    //  component implementation
    //--------------------------------------------------------------

    /**
     * start the generator lifecycle (this doesn't send any requests but just start few internal components)
     */
    public LoadGenerator start()
    {
        this.latencyListeners = latencyListening ? Arrays.asList( new LatencyRecorder( this.latencyValueListeners, //
                                                                    this.latencySchedulerDetails ), //
                                               new SummaryLatencyListener() ) : Collections.emptyList();

        this.executorService = Executors.newWorkStealingPool( this.getUsers() );

        // we iterate over all request path to create HdrHistogram now
        // and do not have to worry about sync after that

        _recorderPerPath = buildMap( loadGeneratorProfile );

        SummaryResponseTimeListener summaryResponseTimeListener =
            new SummaryResponseTimeListener( buildMap( loadGeneratorProfile ) );

        ResponseTimeRecorder responseTimeRecorder = new ResponseTimeRecorder( _recorderPerPath, //
                                                                              responseTimeValueListeners, //
                                                                              responseTimeSchedulerDetails );

        this.responseTimeListeners = Arrays.asList( responseTimeRecorder, summaryResponseTimeListener );

        loadGeneratorResult = new LoadGeneratorResult();

        _loadGeneratorResultHandler =
            new LoadGeneratorResultHandler( loadGeneratorResult, responseTimeListeners, latencyListeners );

        return this;

    }

    private static Map<String, Recorder> buildMap(LoadGeneratorProfile profile)
    {
        Map<String, Recorder> map = new ConcurrentHashMap<>();

        for ( LoadGeneratorProfile.Step step : profile.getSteps() )
        {
            for ( LoadGeneratorProfile.Resource resource : step.getResources() )
            {
                String path = resource.getPath();
                path = path == null ? "" : path.trim();
                if ( !map.containsKey( path ) )
                {
                    if ( StringUtil.isBlank( path ) )
                    {
                        path = "/";
                    }

                    Recorder recorder = new Recorder( TimeUnit.MICROSECONDS.toNanos( 1 ), //
                                                      TimeUnit.MINUTES.toNanos( 1 ), //
                                                      3 );

                    map.put( path, recorder );
                }
            }
        }

        return map;
    }

    /**
     * stop (clear resources) the generator lifecycle
     */
    public LoadGenerator stop()
    {
        this.stop.set( true );
        try
        {
            if ( latencyListeners != null )
            {
                for ( LatencyListener latencyListener : latencyListeners )
                {
                    latencyListener.onLoadGeneratorStop();
                }
            }
            if (responseTimeListeners != null)
            {
                for (ResponseTimeListener responseTimeListener : responseTimeListeners)
                {
                    responseTimeListener.onLoadGeneratorStop();
                }
            }
            if ( collectorServer != null )
            {
                collectorServer.stop();
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
        return this;
    }

    /**
     * run the defined load (users / request numbers)
     */
    public LoadGeneratorResult run()
        throws Exception
    {


        List<Request.Listener> listeners = new ArrayList<>( getRequestListeners() );

        listeners.add( _loadGeneratorResultHandler );

        Executors.newWorkStealingPool( this.getUsers() ).submit( () -> //
        {
            HttpClientTransport httpClientTransport =
                this.getHttpClientTransport() != null ?//
                 this.getHttpClientTransport() : provideClientTransport( this.getTransport() );

            for ( int i = this.getUsers(); i > 0; i-- )
            {
                try
                {
                    HttpClient httpClient = newHttpClient( httpClientTransport, getSslContextFactory() );
                    // TODO dynamic depending on the rate??
                    httpClient.setMaxRequestsQueuedPerDestination( 2048 );
                    httpClient.setSocketAddressResolver( this.getSocketAddressResolver() );
                    this.clients.add( httpClient );
                    httpClient.getRequestListeners().add( _loadGeneratorResultHandler );
                    httpClient.getRequestListeners().addAll( listeners );

                    LoadGeneratorRunner loadGeneratorRunner = //
                            new LoadGeneratorRunner( httpClient, this, _loadGeneratorResultHandler );

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

        if ( this.collectorPort >= 0 )
        {
            // starting collector part

            collectorServer = new CollectorServer( this, _recorderPerPath.keySet() );

            collectorServer.start();

            this.collectorPort = collectorServer.getPort();
        }
        return loadGeneratorResult;
    }

    public LoadGeneratorResult run( long time, TimeUnit timeUnit )
        throws Exception
    {
        LoadGeneratorResult result = this.run();
        PlatformTimer.detect().sleep( timeUnit.toMicros( time ) );
        return result;
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

        private int collectorPort = -1;

        private List<LatencyValueListener> latencyValueListeners;

        private List<ResponseTimeValueListener> responseTimeValueListeners;

        private boolean latencyListening;

        //TODO check if default are correct?
        private SchedulerDetails latencySchedulerDetails = new SchedulerDetails( 0, 1, TimeUnit.SECONDS ), //
            responseTimeSchedulerDetails = new SchedulerDetails( 0, 1, TimeUnit.SECONDS );

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

        public Builder collectorPort( int collectorPort )
        {
            this.collectorPort = collectorPort;
            return this;
        }

        public Builder latencyValueListeners( List<LatencyValueListener> latencyValueListeners )
        {
            this.latencyValueListeners = latencyValueListeners;
            return this;
        }

        public Builder latencyValueListeners( List<LatencyValueListener> latencyValueListeners, //
                                              long initialDelay, long delay, TimeUnit unit )
        {
            this.latencyValueListeners = latencyValueListeners;
            this.latencySchedulerDetails = new SchedulerDetails( initialDelay, delay, unit );
            return this;
        }

        public Builder responseTimeValueListeners( List<ResponseTimeValueListener> responseTimeValueListeners )
        {
            this.responseTimeValueListeners = responseTimeValueListeners;
            return this;
        }

        public Builder responseTimeValueListeners( List<ResponseTimeValueListener> responseTimeValueListeners, //
                                                   long initialDelay, long delay, TimeUnit unit)
        {
            this.responseTimeValueListeners = responseTimeValueListeners;
            this.responseTimeSchedulerDetails = new SchedulerDetails( initialDelay, delay, unit );
            return this;
        }

        public Builder latencyListening(boolean latencyListening)
        {
            this.latencyListening = latencyListening;
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
            loadGenerator.collectorPort = collectorPort;
            loadGenerator.latencyValueListeners = latencyValueListeners;
            loadGenerator.latencySchedulerDetails = latencySchedulerDetails;
            loadGenerator.responseTimeValueListeners = responseTimeValueListeners;
            loadGenerator.responseTimeSchedulerDetails = responseTimeSchedulerDetails;
            loadGenerator.latencyListening = latencyListening;
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

            if ( this.loadGeneratorProfile == null )
            {
                throw new IllegalArgumentException( "a loadGeneratorProfile is mandatory" );
            }

        }

    }

    public static class SchedulerDetails {
        public final Long initialDelay, delay;
        public final TimeUnit unit;

        public SchedulerDetails( long initialDelay, long delay, TimeUnit unit )
        {
            this.initialDelay = initialDelay;
            this.delay = delay;
            this.unit = unit;
        }
    }

}
