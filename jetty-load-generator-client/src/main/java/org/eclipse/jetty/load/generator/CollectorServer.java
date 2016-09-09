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
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.load.generator.latency.LatencyListener;
import org.eclipse.jetty.load.generator.response.ResponseTimeListener;
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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class CollectorServer
    implements LatencyListener, ResponseTimeListener
{

    private static final Logger LOGGER = Log.getLogger( CollectorServer.class );

    private int port;

    private Server server;

    private ServerConnector connector;

    private final Map<String, Recorder> recorderPerPath = new ConcurrentHashMap<>(  );

    private final Recorder latencyRecorder = new Recorder( TimeUnit.MICROSECONDS.toNanos( 1 ), //
                                                           TimeUnit.MINUTES.toNanos( 1 ), //
                                                           3 );


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

        CollectorServlet collectorServlet = new CollectorServlet( latencyRecorder, recorderPerPath );

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

        private Recorder latencyRecorder;

        private Map<String, Recorder> recorderPerPath;

        public CollectorServlet( Recorder latencyRecorder, Map<String, Recorder> recorderPerPath )
        {
            this.latencyRecorder = latencyRecorder;
            this.recorderPerPath = recorderPerPath;
        }

        @Override
        protected void doGet( HttpServletRequest req, HttpServletResponse resp )
            throws ServletException, IOException
        {
            String pathInfo = req.getPathInfo();
            LOGGER.debug( "doGet: {}", pathInfo );

            ObjectMapper mapper = new ObjectMapper();

            // FIXME expose it!!

            if ( StringUtil.endsWithIgnoreCase( pathInfo, "client-latency" ) )
            {

                mapper.writeValue( resp.getOutputStream(),  //
                                   new CollectorInformations( latencyRecorder.getIntervalHistogram(), //
                                                              CollectorInformations.InformationType.LATENCY ) );
                return;
            }

            if ( StringUtil.endsWithIgnoreCase( pathInfo, "response-times" ) )
            {
                Map<String, CollectorInformations> infos = new HashMap<>( recorderPerPath.size() );
                for ( Map.Entry<String, Recorder> entry : recorderPerPath.entrySet() )
                {
                    infos.put( entry.getKey(), new CollectorInformations( entry.getValue().getIntervalHistogram(), //
                                                                          CollectorInformations.InformationType.REQUEST ) );
                }
                mapper.writeValue( resp.getOutputStream(), infos );
                return;
            }

        }
    }

    @Override
    public void onLatencyValue( LatencyListener.Values latencyValue )
    {
        latencyRecorder.recordValue( latencyValue.getLatencyValue() );
    }

    @Override
    public void onResponse( ResponseTimeListener.Values values )
    {
        String path = values.getPath();
        long responseTime = values.getResponseTime();
        Recorder recorder = recorderPerPath.get( path );
        if ( recorder == null )
        {
            recorder = new Recorder( TimeUnit.MICROSECONDS.toNanos( 1 ), //
                                     TimeUnit.MINUTES.toNanos( 1 ), //
                                     3 );
            recorderPerPath.put( path, recorder );
        }
        recorder.recordValue( responseTime );
    }

    @Override
    public void onLoadGeneratorStop()
    {
        // no op
    }
}
