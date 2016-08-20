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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 */
public class CollectorServer
{

    private static final Logger LOGGER = Log.getLogger( CollectorServer.class );

    private int port;

    private Server server;

    private ServerConnector connector;

    private final LoadGenerator loadGenerator;

    private final LoadGeneratorResult loadGeneratorResult;

    public CollectorServer( LoadGenerator loadGenerator, LoadGeneratorResult loadGeneratorResult )
    {
        this.port = loadGenerator.getCollectorPort();
        this.loadGenerator = loadGenerator;
        this.loadGeneratorResult = loadGeneratorResult;
    }

    public int getPort()
    {
        return port;
    }

    public void start()
        throws Exception
    {

        QueuedThreadPool serverThreads = new QueuedThreadPool();
        serverThreads.setName( "server" );
        server = new Server( serverThreads );

        connector = newServerConnector( server );
        server.addConnector( connector );

        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );

        server.setHandler( context );

        CollectorServlet collectorServlet = new CollectorServlet( loadGeneratorResult );

        // TODO path configurable?
        context.addServlet( new ServletHolder( collectorServlet ), "/collector/*" );

        server.start();

        this.port = connector.getLocalPort();

        LOGGER.info( "CollectorServer started on port {}", this.port );

    }

    protected ServerConnector newServerConnector( Server server )
    {
        // FIXME support more protcols!!
        ConnectionFactory connectionFactory = new HttpConnectionFactory( new HttpConfiguration() );

        return new ServerConnector( server, connectionFactory );
    }

    public void stop()
        throws Exception
    {
        server.stop();
    }


    public static class CollectorServlet
        extends HttpServlet
    {

        private static final Logger LOGGER = Log.getLogger( CollectorServlet.class );

        private LoadGeneratorResult loadGeneratorResult;

        public CollectorServlet( LoadGeneratorResult loadGeneratorResult )
        {
            this.loadGeneratorResult = loadGeneratorResult;
        }

        @Override
        protected void doGet( HttpServletRequest req, HttpServletResponse resp )
            throws ServletException, IOException
        {
            String pathInfo = req.getPathInfo();
            LOGGER.debug( "doGet: {}", pathInfo );

            ObjectMapper mapper = new ObjectMapper();

            if ( StringUtil.endsWithIgnoreCase( pathInfo, "client-latency" ) )
            {
                mapper.writeValue( resp.getOutputStream(), loadGeneratorResult.getLatencyInformations() );
                return;
            }

            if ( StringUtil.endsWithIgnoreCase( pathInfo, "response-times" ) )
            {
                mapper.writeValue( resp.getOutputStream(), loadGeneratorResult.getCollectorInformationsPerPath() );
                return;
            }

        }
    }

}
