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
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.fcgi.server.ServerFCGIConnectionFactory;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpScheme;
import org.eclipse.jetty.http2.HTTP2Cipher;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.junit.Assert;
import org.junit.Test;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RunWith( Parameterized.class )
public class LoadGeneratorTest
{
    SslContextFactory sslContextFactory;

    Server server;

    ServerConnector connector;

    final LoadGenerator.Transport transport;

    final int usersNumber;

    Logger logger = Log.getLogger( getClass() );

    StatisticsHandler statisticsHandler = new StatisticsHandler();

    public LoadGeneratorTest( LoadGenerator.Transport transport, int usersNumber )
    {
        this.transport = transport;
        this.usersNumber = usersNumber;
    }

    @Parameterized.Parameters( name = "transport/users: {0},{1}" )
    public static Collection<Object[]> data()
    {

        List<LoadGenerator.Transport> transports = new ArrayList<>();

        transports.add( LoadGenerator.Transport.HTTP );

        // FIXME LoadGenerator.Transport.H2, issue with ALPN
        // FIXME other transports
        //transports.add( LoadGenerator.Transport.HTTPS );
        //transports.add( LoadGenerator.Transport.H2 );
        //transports.add( LoadGenerator.Transport.H2C );
        //transports.add( LoadGenerator.Transport.FCGI);

        // number of users
        List<Integer> users = Arrays.asList( 1 );//, 2, 4 );

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

    @Test
    public void simple_test()
        throws Exception
    {

        LoadGeneratorProfile loadGeneratorProfile = LoadGeneratorProfile.Builder.builder() //
            .resource( "/index.html" ).size( 1024 ) //
            //.resource( "" ).size( 1024 ) //
            .build();

        runProfile( loadGeneratorProfile );

    }


    @Test
    public void simple_with_group()
        throws Exception
    {

        LoadGeneratorProfile loadGeneratorProfile = LoadGeneratorProfile.Builder.builder() //
            .resource( "/index.html" ).size( 1024 ) //
            .resourceGroup() //
            .resource( "/foo.html" ) //
            .resource( "/beer.html" ) //
            .then() //
            .resource( "/wine.html" ) //
            .build();

        runProfile( loadGeneratorProfile );

    }

    protected void runProfile( LoadGeneratorProfile profile )
        throws Exception
    {

        TestRequestListener testRequestListener = new TestRequestListener();

        startServer( new LoadHandler() );

        Scheduler scheduler = new ScheduledExecutorScheduler( getClass().getName() + "-scheduler", false );

        LoadGenerator loadGenerator = LoadGenerator.Builder.builder() //
            .host( "localhost" ) //
            .port( connector.getLocalPort() ) //
            .users( this.usersNumber ) //
            .requestRate( 1 ) //
            .scheme( scheme() ) //
            .requestListeners( Arrays.asList( testRequestListener ) ) //
            .transport( this.transport ) //
            .httpClientScheduler( scheduler ) //
            .loadGeneratorWorkflow( profile ) //
            .build() //
            .start();

        LoadGeneratorResult result = loadGenerator.run();

        Thread.sleep( 5000 );

        loadGenerator.setRequestRate( 10 );

        Thread.sleep( 3000 );

        Assert.assertTrue( "successReponsesReceived :" + result.getTotalSuccess().get(), //
                           result.getTotalSuccess().get() > 1 );

        logger.info( "successReponsesReceived: {}", result.getTotalSuccess().get() );

        Assert.assertTrue( "failedReponsesReceived: " + result.getTotalFailure().get(), //
                           result.getTotalFailure().get() < 1 );

        Assert.assertNotNull( result );

        HttpClient httpClient = new HttpClient();
        httpClient.start();
        Request request = httpClient.newRequest(
            "http://localhost:" + loadGenerator.getCollectorPort() + "/collector/client-latency" );
        ContentResponse response = request.method( HttpMethod.GET.asString() ).send();

        Assert.assertEquals( 200, response.getStatus() );

        logger.info( "latency recorder: {}", //
                     CollectorInformations.toString( result.getLatencyRecorder().getIntervalHistogram() ) );

        logger.info( "resp client latency: {}", response.getContentAsString() );

        request = httpClient.newRequest(
            "http://localhost:" + loadGenerator.getCollectorPort() + "/collector/response-times" );
        response = request.method( HttpMethod.GET.asString() ).send();

        Assert.assertEquals( 200, response.getStatus() );

        for ( Map.Entry<String, Recorder> entry : result.getRecorderPerPath().entrySet() )
        {
            logger.info( "recorder per path: {} : {}", entry.getKey(),
                         CollectorInformations.toString( entry.getValue().getIntervalHistogram() ) );
        }

        logger.info( "resp response times: {}", response.getContentAsString() );

        loadGenerator.stop();

        httpClient.stop();
    }

    //---------------------------------------------------
    // utilities
    //---------------------------------------------------


    String scheme()
    {
        switch ( this.transport )
        {
            case HTTP:
                return HttpScheme.HTTP.asString();
            case HTTPS:
                return HttpScheme.HTTPS.asString();
            default:
                throw new IllegalArgumentException( "unknow scheme" );
        }

    }

    static class TestRequestListener
        extends Request.Listener.Adapter
    {
        AtomicLong onSuccessNumber = new AtomicLong();

        @Override
        public void onSuccess( Request request )
        {
            onSuccessNumber.incrementAndGet();
        }

    }


    protected void startServer( HttpServlet handler )
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

        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
        context.setContextPath( "/" );

        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers( new Handler[]{ context, statisticsHandler } );

        server.setHandler( handlerCollection );

        context.addServlet( new ServletHolder( handler ), "/*" );

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

    private class LoadHandler
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

            LOGGER.info( "method: {}, contentLength: {}, id: {}, pathInfo: {}", //
                         method, contentLength, httpSession.getId(), request.getPathInfo() );

            switch ( method )
            {
                case "GET":
                {
                    if ( contentLength > 0 )
                    {
                        response.setHeader( "X-Content", String.valueOf( contentLength ) );
                        response.getOutputStream().write( new byte[contentLength] );
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
}
