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

import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.StatisticsServlet;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 */
public class LoadGeneratorStarterTest
{

    Server server;

    ServerConnector connector;

    StatisticsHandler statisticsHandler = new StatisticsHandler();

    TestHandler testHandler;

    @Before
    public void startJetty() throws Exception {
      QueuedThreadPool serverThreads = new QueuedThreadPool();
      serverThreads.setName( "server" );
      server = new Server( serverThreads );
      server.setSessionIdManager( new HashSessionIdManager() );
      connector = new ServerConnector( server, new HttpConnectionFactory( new HttpConfiguration() ) );
      server.addConnector( connector );

      server.setHandler( statisticsHandler );

      ServletContextHandler statsContext = new ServletContextHandler( statisticsHandler, "/" );

      statsContext.addServlet( new ServletHolder( new StatisticsServlet() ), "/stats" );

      testHandler = new TestHandler();

      statsContext.addServlet( new ServletHolder( testHandler ), "/" );

      statsContext.setSessionHandler( new SessionHandler() );

      server.start();
    }

    @After
    public void stopJetty() throws Exception {
        server.stop();
    }

    @Test
    public void simpletest() throws Exception {

        List<String> args = new ArrayList<>(  );
        args.add( "--warmup-number" );
        args.add( "10" );
        args.add( "-h" );
        args.add( "localhost" );
        args.add( "--port" );
        args.add( Integer.toString( connector.getLocalPort()) );
        args.add( "--running-time");
        args.add( "10");
        args.add( "--running-time-unit");
        args.add( "s" );
        args.add( "--transaction-rate" );
        args.add( "3" );
        args.add( "--transport");
        args.add( "http");
        args.add( "--users");
        args.add( "3");
        args.add( "--profile-groovy-path");
        args.add( "src/test/resources/loadgenerator_profile.groovy");

        LoadGeneratorStarter.main( args.toArray(new String[args.size()]) );

        int getNumber = testHandler.getNumber.get();

        Assert.assertTrue("getNumber return: " + getNumber, getNumber > 10);
    }


    static class TestHandler extends HttpServlet {

      AtomicInteger getNumber = new AtomicInteger( 0 ), postNumber = new AtomicInteger( 0 );

      @Override
      protected void service( HttpServletRequest request, HttpServletResponse response ) throws ServletException, IOException {

        String method = request.getMethod().toUpperCase( Locale.ENGLISH );

        HttpSession httpSession = request.getSession();

        switch ( method ) {
          case "GET":
                {
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
