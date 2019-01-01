//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jetty.load.generator.collector;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mortbay.jetty.load.generator.HTTP1ClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.CollectorServer;

@RunWith( Parameterized.class )
public class CollectorTest
{

    Logger logger = Log.getLogger( getClass() );

    private int serverNumbers;

    private List<Server> servers;

    public CollectorTest( Integer serverNumbers )
        throws Exception
    {
        this.serverNumbers = serverNumbers;
        this.servers = new ArrayList<>( this.serverNumbers );
        for ( int i = 0; i < this.serverNumbers; i++ )
        {
            this.servers.add( startServer( new LoadHandler() ) );
        }

    }

    @Parameterized.Parameters( name = "servers: {0}" )
    public static Collection<Integer> data()
    {
        List<Integer> number = new ArrayList<>();
        number.add( Integer.valueOf( 1 ) );
        number.add( Integer.valueOf( 2 ) );

        return number;
    }

    @After
    public void shutdown()
        throws Exception
    {
        for ( Server server : this.servers )
        {
            server.stop();
        }
    }

    @Test
    //@Ignore("ATM not a priority ATM")
    public void collect_informations()
        throws Exception
    {
        Resource resource = new Resource( "/index.html" );

        runProfile( resource );
    }

    protected void runProfile( Resource profile )
        throws Exception
    {

        List<LoadGenerator> loadGenerators = new ArrayList<>( serverNumbers );
        List<CollectorClient> collectorClients = new CopyOnWriteArrayList<>();
        List<TestRequestListener> testRequestListeners = new ArrayList<>( serverNumbers );

        List<CollectorResultHandler> collectorResultHandlers = Arrays.asList( new LoggerCollectorResultHandler() );

        for ( Server server : servers )
        {
            CollectorServer collectorServer = new CollectorServer( 0 ).start();
            int port = ( (ServerConnector) server.getConnectors()[0] ).getLocalPort();

            TestRequestListener testRequestListener = new TestRequestListener();

            testRequestListeners.add( testRequestListener );

            LoadGenerator loadGenerator = new LoadGenerator.Builder() //
                .host( "localhost" ) //
                .port( port ) //
                .usersPerThread( 2 ) //
                .iterationsPerThread( 10 ) //
                .resourceRate( 20 ) //
//                .transport( LoadGenerator.Transport.HTTP ) //
                .httpClientTransportBuilder( new HTTP1ClientTransportBuilder() ) //
                //.scheduler( scheduler ) //
                .resource( profile ) //
                // FIXME here
                //.responseTimeListeners( collectorServer ) //
                .requestListener( testRequestListener ) //
                .build();

            loadGenerator.begin();

            loadGenerators.add( loadGenerator );

            CollectorClient collectorClient = new CollectorClient.Builder() //
                .addAddress( "localhost:" + collectorServer.getPort() ) //
                .scheduleDelayInMillis( 500 ) //
                .collectorResultHandlers( collectorResultHandlers ) //
                .build();

            collectorClient.start();

            collectorClients.add( collectorClient );

        }

        Thread.sleep( 3000 );

        for ( CollectorClient collectorClient : collectorClients )
        {
            collectorClient.stop();
        }

        for ( LoadGenerator loadGenerator : loadGenerators )
        {
            loadGenerator.interrupt();
        }

        for ( TestRequestListener testRequestListener : testRequestListeners )
        {
            Assert.assertTrue( "successReponsesReceived :" + testRequestListener.success.get(), //
                               testRequestListener.success.get() > 1 );

            logger.info( "successReponsesReceived: {}", testRequestListener.success.get() );

            Assert.assertTrue( "failedReponsesReceived: " + testRequestListener.failed.get(), //
                               testRequestListener.failed.get() < 1 );
        }

    }

    protected Server startServer( HttpServlet handler )
        throws Exception
    {
        QueuedThreadPool serverThreads = new QueuedThreadPool();
        serverThreads.setName( "server" );
        Server server = new Server( serverThreads );
//        server.setSessionIdManager( new HashSessionIdManager() );
        ServerConnector connector = new ServerConnector( server, new HttpConnectionFactory( new HttpConfiguration() ) );
        server.addConnector( connector );

        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );
        context.setContextPath( "/" );

        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers( new Handler[]{ context } );

        server.setHandler( handlerCollection );
        context.addServlet( new ServletHolder( handler ), "/*" );
        server.start();

        return server;
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

            LOGGER.debug( "method: {}, contentLength: {}, id: {}, pathInfo: {}", //
                          method, contentLength, httpSession.getId(), request.getPathInfo() );


        }
    }

    static class TestRequestListener
        extends Request.Listener.Adapter
    {
        AtomicLong committed = new AtomicLong( 0 );

        AtomicLong success = new AtomicLong( 0 );

        AtomicLong failed = new AtomicLong( 0 );

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
        }
    }

}
