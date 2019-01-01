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


package org.mortbay.jetty.load.generator.listeners;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.HdrHistogram.Recorder;
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
import org.mortbay.jetty.load.generator.Resource;

/**
 *
 */
public class CollectorServer
    implements Resource.NodeListener
{

    private static final Logger LOGGER = Log.getLogger( CollectorServer.class );

    private int port;

    private Server server;

    private ServerConnector connector;

    private final Map<String, Recorder> recorderPerPath = new ConcurrentHashMap<>(  );

    public CollectorServer( int port )
    {
        this.port = port;
    }

    public int getPort()
    {
        return port;
    }

    public CollectorServer start()
        throws Exception
    {

        QueuedThreadPool serverThreads = new QueuedThreadPool();
        serverThreads.setName( "server" );
        server = new Server( serverThreads );

        connector = newServerConnector( server );
        server.addConnector( connector );

        ServletContextHandler context = new ServletContextHandler( ServletContextHandler.SESSIONS );

        server.setHandler( context );

        CollectorServlet collectorServlet = new CollectorServlet( recorderPerPath );

        // TODO path configurable?
        context.addServlet( new ServletHolder( collectorServlet ), "/collector/*" );

        server.start();

        this.port = connector.getLocalPort();

        LOGGER.info( "CollectorServer started on port {}", this.port );

        return this;

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

        private Map<String, Recorder> recorderPerPath;

        public CollectorServlet(  Map<String, Recorder> recorderPerPath )
        {
            this.recorderPerPath = recorderPerPath;
        }

        @Override
        protected void doGet( HttpServletRequest req, HttpServletResponse resp )
            throws ServletException, IOException
        {
            String pathInfo = req.getPathInfo();
            LOGGER.debug( "doGet: {}", pathInfo );

            ObjectMapper mapper = new ObjectMapper();

            if ( StringUtil.endsWithIgnoreCase( pathInfo, "response-times" ) )
            {
                Map<String, CollectorInformations> infos = new HashMap<>( recorderPerPath.size() );
                for ( Map.Entry<String, Recorder> entry : recorderPerPath.entrySet() )
                {
                    infos.put( entry.getKey(), new CollectorInformations( entry.getValue().getIntervalHistogram()) );
                }
                mapper.writeValue( resp.getOutputStream(), infos );
                return;
            }

        }
    }

    @Override
    public void onResourceNode( Resource.Info info )
    {
        String path = info.getResource().getPath();

        Recorder recorder = recorderPerPath.get( path );
        if ( recorder == null )
        {
            recorder = new Recorder( TimeUnit.MICROSECONDS.toNanos( 1 ), //
                                     TimeUnit.MINUTES.toNanos( 1 ), //
                                     3 );
            recorderPerPath.put( path, recorder );
        }


        long time = info.getResponseTime() - info.getRequestTime();
        try
        {
            recorder.recordValue( time );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "skip error recording time {}, {}", time, e.getMessage() );
        }


    }
}
