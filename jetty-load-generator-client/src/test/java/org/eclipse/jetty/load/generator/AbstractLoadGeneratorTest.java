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


import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.fcgi.server.ServerFCGIConnectionFactory;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.load.generator.responsetime.ResponseTimeDisplayListener;
import org.eclipse.jetty.load.generator.responsetime.ResponseTimeListener;
import org.eclipse.jetty.load.generator.responsetime.SummaryResponseTimeListener;
import org.eclipse.jetty.load.generator.profile.Resource;
import org.eclipse.jetty.load.generator.profile.ResourceProfile;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.StatisticsServlet;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RunWith( Parameterized.class )
public abstract class AbstractLoadGeneratorTest
{
    SslContextFactory sslContextFactory;

    Server server;

    ServerConnector connector;

    final LoadGenerator.Transport transport;

    final int usersNumber;

    Logger logger = Log.getLogger( getClass() );

    StatisticsHandler statisticsHandler = new StatisticsHandler();

    TestRequestListener testRequestListener;

    ResponsePerPath responsePerPath;

    public AbstractLoadGeneratorTest( LoadGenerator.Transport transport, int usersNumber )
    {
        this.transport = transport;
        this.usersNumber = usersNumber;

        logger.info( "starting test with httpClientTransport {} for {} users", this.transport, this.usersNumber );
    }

    @Before
    public void start() throws Exception
    {
        startServer( new LoadHandler() );
    }

    @After
    public void stop() throws Exception
    {
        statisticsHandler.statsReset();
        server.stop();
    }

    @Parameterized.Parameters( name = "httpClientTransport/users: {0},{1}" )
    public static Collection<Object[]> data()
    {

        List<LoadGenerator.Transport> transports = new ArrayList<>();

        transports.add( LoadGenerator.Transport.HTTP );

        transports.add( LoadGenerator.Transport.HTTPS );
        transports.add( LoadGenerator.Transport.H2 );
        transports.add( LoadGenerator.Transport.H2C );
        transports.add( LoadGenerator.Transport.FCGI );


        // number of users
        List<Integer> users = Arrays.asList( 1, 2, 4 );

        List<Object[]> parameters = new ArrayList<>();

        for ( LoadGenerator.Transport transport : transports )
        {
            for ( Integer userNumber : users )
            {
                parameters.add( new Object[]{ transport, userNumber } );
            }

        }
        return parameters;
    }

    protected HttpClientTransport httpClientTransport()
    {
        switch ( this.transport )
        {
            case HTTP:
            case HTTPS:
            {
                return new HttpTransportBuilder().build();
            }
            case H2C:
            case H2:
            {
                return new Http2TransportBuilder().selectors( 1 ).build();
            }
            case FCGI:
            {
                return new HttpFCGITransportBuilder().build();
            }

            default:
            {
                // nothing this weird case already handled by #provideClientTransport
            }

        }
        throw new IllegalArgumentException( "unknow httpClientTransport" );
    }


    protected List<ResponseTimeListener> getResponseTimeListeners()
    {
        return Arrays.asList( new ResponseTimeDisplayListener(), new SummaryResponseTimeListener() );
    }

    protected LoadGenerator build( ResourceProfile profile ) throws Exception {

        responsePerPath = new ResponsePerPath();

        testRequestListener = new TestRequestListener( logger );

        List<ResponseTimeListener> responseTimeListeners = new ArrayList<>( getResponseTimeListeners() );

        responseTimeListeners.add( responsePerPath );

        LoadGenerator loadGenerator = new LoadGenerator.Builder() //
            .host( "localhost" ) //
            .port( connector.getLocalPort() ) //
            .users( this.usersNumber ) //
            .transactionRate( 1 ) //
            .transport( this.transport ) //
            .httpClientTransport( this.httpClientTransport() ) //
            .sslContextFactory( sslContextFactory ) //
            .loadProfile( profile ) //
            .responseTimeListeners( responseTimeListeners.toArray( new ResponseTimeListener[responseTimeListeners.size()]) ) //
            .requestListeners( testRequestListener ) //
            //.executor( new QueuedThreadPool() )
            .build();

        enhanceLoadGenerator( loadGenerator );

        return loadGenerator;
    }

    protected void enhanceLoadGenerator( LoadGenerator loadGenerator ) throws Exception {
        // no op
    }

    protected void runProfile( ResourceProfile profile )
        throws Exception
    {

        LoadGenerator loadGenerator = build( profile );

        loadGenerator.run();

        Thread.sleep( 5000 );

        loadGenerator.setTransactionRate( 10 );

        Thread.sleep( 4000 );

        loadGenerator.interrupt();

        Assert.assertTrue( currentTestRunInfos() + ",successReponsesReceived :" + testRequestListener.success.get(), //
                           testRequestListener.success.get() > 1 );

        Assert.assertEquals( currentTestRunInfos(), testRequestListener.committed.get(),
                             testRequestListener.success.get() );

        logger.info( "successReponsesReceived: {}", testRequestListener.success.get() );

        Assert.assertTrue( currentTestRunInfos() + ", failedReponsesReceived: " + testRequestListener.failed.get(), //
                           testRequestListener.failed.get() < 1 );

        Collection<String> paths = paths( profile );

        Assert.assertEquals( paths.size(), responsePerPath.recorderPerPath.keySet().size() );

    }

    private Collection<String> paths(ResourceProfile profile) {
        Set<String> paths = new HashSet<>(  );

        for(Resource resource : profile.getResources()) {
            paths.addAll( paths( resource ) );
        }

        return paths;
    }

    private Collection<String> paths( Resource resource) {
        Set<String> paths = new HashSet<>(  );
        paths.add( resource.getPath() );
        for(Resource child : resource.getResources()) {
            paths.addAll( paths( child ) );
        }
        return paths;
    }


    public String currentTestRunInfos()
    {
        return "users:" + this.usersNumber + ", httpClientTransport: " + this.transport;
    }

    //---------------------------------------------------
    // utilities
    //---------------------------------------------------

    static class TestRequestListener
        extends Request.Listener.Adapter
    {
        AtomicLong committed = new AtomicLong( 0 );

        AtomicLong success = new AtomicLong( 0 );

        AtomicLong failed = new AtomicLong( 0 );

        Logger logger;

        public TestRequestListener( Logger logger )
        {
            this.logger = logger;
        }

        @Override
        public void onCommit( Request request )
        {
            committed.incrementAndGet();
        }

        @Override
        public void onSuccess( Request request )
        {
            success.incrementAndGet();
        }

        @Override
        public void onFailure( Request request, Throwable failure )
        {
            failed.incrementAndGet();
            logger.info( "failure", failure );
        }
    }


    private void startServer( HttpServlet handler )
        throws Exception
    {
        sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath( "src/test/resources/keystore.jks" );
        sslContextFactory.setKeyStorePassword( "storepwd" );
        sslContextFactory.setTrustStorePath( "src/test/resources/truststore.jks" );
        sslContextFactory.setTrustStorePassword( "storepwd" );
        sslContextFactory.setUseCipherSuitesOrder( true );
        sslContextFactory.setCipherComparator( HTTP2Cipher.COMPARATOR );
        QueuedThreadPool serverThreads = new QueuedThreadPool();
        serverThreads.setName( "server" );
        server = new Server( serverThreads );
        server.setSessionIdManager( new HashSessionIdManager() );
        connector = newServerConnector( server );
        server.addConnector( connector );

        server.setHandler( statisticsHandler );

        ServletContextHandler statsContext = new ServletContextHandler( statisticsHandler, "/");


        statsContext.addServlet(new ServletHolder(new StatisticsServlet()), "/stats");

        statsContext.addServlet( new ServletHolder( handler ), "/" );

        statsContext.setSessionHandler(new SessionHandler());

        server.start();
    }


    protected ServerConnector newServerConnector( Server server )
    {
        return new ServerConnector( server, provideServerConnectionFactory( transport ) );
    }

    protected ConnectionFactory[] provideServerConnectionFactory( LoadGenerator.Transport transport )
    {
        List<ConnectionFactory> result = new ArrayList<>();
        switch ( transport )
        {
            case HTTP:
            {
                result.add( new HttpConnectionFactory( new HttpConfiguration() ) );
                break;
            }
            case HTTPS:
            {
                HttpConfiguration configuration = new HttpConfiguration();
                configuration.addCustomizer( new SecureRequestCustomizer() );
                HttpConnectionFactory http = new HttpConnectionFactory( configuration );
                SslConnectionFactory ssl = new SslConnectionFactory( sslContextFactory, http.getProtocol() );
                result.add( ssl );
                result.add( http );
                break;
            }
            case H2C:
            {
                result.add( new HTTP2CServerConnectionFactory( new HttpConfiguration() ) );
                break;
            }
            case H2:
            {
                HttpConfiguration configuration = new HttpConfiguration();
                configuration.addCustomizer( new SecureRequestCustomizer() );
                HTTP2ServerConnectionFactory h2 = new HTTP2ServerConnectionFactory( configuration );
                ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory( "h2" );
                SslConnectionFactory ssl = new SslConnectionFactory( sslContextFactory, alpn.getProtocol() );
                result.add( ssl );
                result.add( alpn );
                result.add( h2 );
                break;
            }
            case FCGI:
            {
                result.add( new ServerFCGIConnectionFactory( new HttpConfiguration() ) );
                break;
            }
            default:
            {
                throw new IllegalArgumentException();
            }
        }
        return result.toArray( new ConnectionFactory[result.size()] );
    }

    static class LoadHandler
        extends HttpServlet
    {

        private final Logger LOGGER = Log.getLogger( getClass() );

        @Override
        protected void service( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException
        {

            String method = request.getMethod().toUpperCase( Locale.ENGLISH );

            HttpSession httpSession = request.getSession();

            int contentLength = request.getIntHeader( "X-Download" );

            LOGGER.debug( "method: {}, contentLength: {}, id: {}, pathInfo: {}", //
                          method, contentLength, httpSession.getId(), request.getPathInfo() );

            switch ( method )

            {
                case "GET":
                {
                    if ( contentLength > 0 )
                    {
                        response.setHeader( "X-Content", String.valueOf( contentLength ) );
                        response.getOutputStream().write( new byte[contentLength] );
                    } else {
                        response.getOutputStream().write( "Hey mate howzagoing??".getBytes() );
                        response.flushBuffer();
                    }
                    break;
                }
                case "POST":
                {
                    response.setHeader( "X-Content", request.getHeader( "X-Upload" ) );
                    IO.copy( request.getInputStream(), response.getOutputStream() );
                    break;
                }
            }

            if ( Boolean.parseBoolean( request.getHeader( "X-Close" ) ) )
            {
                response.setHeader( "Connection", "close" );
            }
        }
    }


    public static class ResponsePerPath implements ResponseTimeListener
    {

        private final Map<String, AtomicLong> recorderPerPath = new ConcurrentHashMap<>(  );

        @Override
        public void onResponseTimeValue( Values values )
        {
            String path = values.getPath();
            AtomicLong response = recorderPerPath.get( path );
            if (response == null) {
                response = new AtomicLong( 1 );
                recorderPerPath.put( path, response );
            } else {
                response.incrementAndGet();
            }
        }


        @Override
        public void onLoadGeneratorStop()
        {

        }

        public Map<String, AtomicLong> getRecorderPerPath()
        {
            return recorderPerPath;
        }
    }

}
