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

package org.eclipse.jetty.load.collector;

import org.eclipse.jetty.load.generator.CollectorServer;
import org.eclipse.jetty.load.generator.LoadGenerator;
import org.eclipse.jetty.load.generator.LoadGeneratorProfile;
import org.eclipse.jetty.load.generator.LoadGeneratorResult;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.junit.After;
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
    public void collect_informations()
        throws Exception
    {
        LoadGeneratorProfile loadGeneratorProfile = LoadGeneratorProfile.Builder.builder() //
            .resource( "/index.html" ).size( 1024 ) //
            //.resource( "" ).size( 1024 ) //
            .build();

        runProfile( loadGeneratorProfile );
    }

    protected void runProfile( LoadGeneratorProfile profile )
        throws Exception
    {

        List<LoadGenerator> loadGenerators = new ArrayList<>( serverNumbers );
        List<CollectorClient> collectorClients = new ArrayList<>( serverNumbers );
        List<LoadGeneratorResult> results = new ArrayList<>( serverNumbers );

        List<CollectorResultHandler> collectorResultHandlers = Arrays.asList(new LoggerCollectorResultHandler());

        for ( Server server : servers )
        {
            CollectorServer collectorServer = new CollectorServer( 0 ).start();
            Scheduler scheduler = new ScheduledExecutorScheduler( getClass().getName() + "-scheduler", false );
            int port = ( (ServerConnector) server.getConnectors()[0] ).getLocalPort();
            LoadGenerator loadGenerator = LoadGenerator.Builder.builder() //
                .host( "localhost" ) //
                .port( port ) //
                .users( 2 ) //
                .requestRate( 5 ) //
                .transport( LoadGenerator.Transport.HTTP ) //
                .scheduler( scheduler ) //
                .loadProfile( profile ) //
                .latencyListeners( Arrays.asList( collectorServer ) ) //
                .responseTimeListeners( Arrays.asList( collectorServer ) ) //
                .build() //
                .start();

            LoadGeneratorResult result = loadGenerator.run();
            results.add( result );

            loadGenerators.add( loadGenerator );

            CollectorClient collectorClient = CollectorClient.Builder.builder() //
                .addAddress( "localhost:" + collectorServer.getPort() ) //
                .scheduleDelayInMillis( 500 ) //
                .collectorResultHandlers(collectorResultHandlers) //
                .build();

            collectorClient.start();

            collectorClients.add( collectorClient );

        }

        Thread.sleep( 3000 );

        for ( LoadGeneratorResult result : results )
        {
            Assert.assertTrue( "successReponsesReceived :" + result.getTotalSuccess().get(), //
                               result.getTotalSuccess().get() > 1 );

            logger.info( "successReponsesReceived: {}", result.getTotalSuccess().get() );

            Assert.assertTrue( "failedReponsesReceived: " + result.getTotalFailure().get(), //
                               result.getTotalFailure().get() < 1 );

            Assert.assertNotNull( result );
        }

        for ( CollectorClient collectorClient : collectorClients )
        {
            collectorClient.stop();
        }

        for ( LoadGenerator loadGenerator : loadGenerators )
        {
            loadGenerator.stop();
        }


    }

    protected Server startServer( HttpServlet handler )
        throws Exception
    {
        QueuedThreadPool serverThreads = new QueuedThreadPool();
        serverThreads.setName( "server" );
        Server server = new Server( serverThreads );
        server.setSessionIdManager( new HashSessionIdManager() );
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

}
