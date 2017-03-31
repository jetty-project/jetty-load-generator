//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jetty.load.generator;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.StatisticsServlet;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class FailFastTest
{

    private static final Logger LOGGER = Log.getLogger( FailFastTest.class );
    protected Resource resource;
    protected Server server;
    protected ServerConnector connector;
    TestHandler testHandler;


    @Before
    public void startJetty()
        throws Exception
    {
        StatisticsHandler statisticsHandler = new StatisticsHandler();
        QueuedThreadPool serverThreads = new QueuedThreadPool();
        serverThreads.setName( "server" );
        server = new Server( serverThreads );
        connector = new ServerConnector( server, new HttpConnectionFactory( new HttpConfiguration() ) );
        server.addConnector( connector );
        server.setHandler( statisticsHandler );
        ServletContextHandler statsContext = new ServletContextHandler( statisticsHandler, "/" );
        statsContext.addServlet( new ServletHolder( new StatisticsServlet() ), "/stats" );
        testHandler = new TestHandler();
        testHandler.server = server;
        statsContext.addServlet( new ServletHolder( testHandler ), "/" );
        statsContext.setSessionHandler( new SessionHandler() );
        server.start();
    }

    @After
    public void stopJetty()
        throws Exception
    {
        if ( server.isRunning() )
        {
            server.stop();
        }
    }

    @Test
    public void should_fail_fast_on_server_stop()
        throws Exception
    {
        AtomicInteger onFailure = new AtomicInteger( 0 ), onCommit = new AtomicInteger( 0 );
        LoadGenerator.Builder builder = //
            new LoadGenerator.Builder() //
                .host( "localhost" ) //
                .port( connector.getLocalPort() ) //
                .resource( new Resource( "/index.html?fail=5" ) ) //
                .warmupIterationsPerThread( 1 ) //
                .usersPerThread( 1 ) //
                .threads( 1 ) //
                .resourceRate( 5 )
                .iterationsPerThread( 25 ) //
                //.runFor( 10, TimeUnit.SECONDS ) //
                .requestListener( new Request.Listener.Adapter() {
                    @Override
                    public void onFailure( Request request, Throwable failure )
                    {
                        LOGGER.info( "fail: {}", onFailure.incrementAndGet() );
                    }

                    @Override
                    public void onCommit( Request request )
                    {
                        LOGGER.info( "onCommit: {}", onCommit.incrementAndGet() );
                    }
                } );
        boolean exception = false;
        try
        {
            builder.build().begin().get();
        }
        catch ( Exception e )
        {
            exception = true;
        }
        Assert.assertTrue( exception );
        LOGGER.info( "onFailure: {}, onCommit: {}", onFailure, onCommit);
        int onFailureCall = onFailure.get();
        Assert.assertTrue("onFailureCall is " + onFailureCall, onFailureCall < 5);
    }

    static class TestHandler
        extends HttpServlet
    {

        AtomicInteger getNumber = new AtomicInteger( 0 );
        Server server;

        @Override
        protected void service( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException
        {
            String fail = request.getParameter( "fail" );
            if ( getNumber.get() >= Integer.parseInt( fail ) )
            {
                try
                {
                    server.stop();
                }
                catch ( Exception e )
                {
                    throw new RuntimeException( e.getMessage(), e );
                }

            }
            response.getOutputStream().write( "Jetty rocks!!".getBytes() );
            response.flushBuffer();
            getNumber.addAndGet( 1 );
        }
    }

}
