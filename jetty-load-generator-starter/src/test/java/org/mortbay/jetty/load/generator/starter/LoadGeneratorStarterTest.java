/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mortbay.jetty.load.generator.starter;

import com.beust.jcommander.JCommander;
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
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mortbay.jetty.load.generator.Resource;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class LoadGeneratorStarterTest
{
    private static final Logger LOGGER = Log.getLogger( LoadGeneratorStarterTest.class);

    Server server;

    ServerConnector connector;

    StatisticsHandler statisticsHandler = new StatisticsHandler();

    TestHandler testHandler;

    @Before
    public void startJetty()
        throws Exception
    {
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
        if (server.isRunning())
        {
            server.stop();
        }
    }

    @Test
    public void simpletest()
        throws Exception
    {

        List<String> args = new ArrayList<>();
        args.add( "--warmup-number" );
        args.add( "10" );
        args.add( "-h" );
        args.add( "localhost" );
        args.add( "--port" );
        args.add( Integer.toString( connector.getLocalPort() ) );
        args.add( "--running-time" );
        args.add( "10" );
        args.add( "--running-time-unit" );
        args.add( "s" );
        args.add( "--transaction-rate" );
        args.add( "3" );
        args.add( "--transport" );
        args.add( "http" );
        args.add( "--users" );
        args.add( "3" );
        args.add( "--profile-groovy-path" );
        args.add( "src/test/resources/loadgenerator_profile.groovy" );
        LoadGeneratorStarter.main( args.toArray( new String[args.size()] ) );
        int getNumber = testHandler.getNumber.get();
        LOGGER.debug( "received get: {}", getNumber );
        Assert.assertTrue( "getNumber return: " + getNumber, getNumber > 10 );
    }


    @Test
    public void fail_fast()
        throws Exception
    {

        List<String> args = new ArrayList<>();
        args.add( "--warmup-number" );
        args.add( "10" );
        args.add( "-h" );
        args.add( "localhost" );
        args.add( "--port" );
        args.add( Integer.toString( connector.getLocalPort() ) );
        args.add( "--running-time" );
        args.add( "10" );
        args.add( "--running-time-unit" );
        args.add( "s" );
        args.add( "--transaction-rate" );
        args.add( "3" );
        args.add( "--transport" );
        args.add( "http" );
        args.add( "--users" );
        args.add( "1" );
        args.add( "--profile-groovy-path" );
        args.add( "src/test/resources/single_resource.groovy" );

        LoadGeneratorStarterArgs runnerArgs = new LoadGeneratorStarterArgs();
        new JCommander( runnerArgs, args.toArray( new String[args.size()] ) );

        AtomicInteger onFailure = new AtomicInteger( 0 ), onCommit = new AtomicInteger( 0 );
        Request.Listener.Adapter adapter = new Request.Listener.Adapter() {
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
        };

        LoadGeneratorStarter runner = new LoadGeneratorStarter( runnerArgs );
        runner.setListeners( new Request.Listener[] { adapter } );
        boolean exception = false;
        try
        {
            runner.run();
        }
        catch ( Exception e )
        {
            exception = true;
        }
        LOGGER.info( "onFailure: {}, onCommit: {}", onFailure, onCommit);
        Assert.assertTrue("not in exception", exception );
        int getNumber = testHandler.getNumber.get();
        LOGGER.debug( "received get: {}", getNumber );
        Assert.assertTrue( "getNumber return: " + getNumber, getNumber == 5 );
        Assert.assertTrue( onFailure.get() < 10 );
    }

    @Test
    public void json_serial_deserial_from_groovy()
        throws Exception
    {
        try (Reader reader = Files.newBufferedReader( Paths.get( "src/test/resources/loadgenerator_profile.groovy" ) ))
        {
            Resource resource = (Resource) AbstractLoadGeneratorStarter.evaluateScript( reader );
            String path = AbstractLoadGeneratorStarter.writeAsJsonTmp( resource );
            Resource fromJson = AbstractLoadGeneratorStarter.evaluateJson( Paths.get( path ) );
            Assert.assertEquals( resource.descendantCount(), fromJson.descendantCount() );
        }
    }

    static class TestHandler
        extends HttpServlet
    {

        AtomicInteger getNumber = new AtomicInteger( 0 ), postNumber = new AtomicInteger( 0 );

        Server server;

        @Override
        protected void service( HttpServletRequest request, HttpServletResponse response )
            throws ServletException, IOException
        {

            String method = request.getMethod().toUpperCase( Locale.ENGLISH );

            HttpSession httpSession = request.getSession();

            switch ( method )
            {
                case "GET":
                {
                    String fail = request.getParameter( "fail" );
                    if (fail != null)
                    {
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
                    }
                    response.getOutputStream().write( "Jetty rocks!!".getBytes() );
                    response.flushBuffer();
                    getNumber.addAndGet( 1 );
                    break;
                }
                case "POST":
                {
                    IO.copy( request.getInputStream(), response.getOutputStream() );
                    postNumber.addAndGet( 1 );
                    break;
                }
            }

        }
    }


}
