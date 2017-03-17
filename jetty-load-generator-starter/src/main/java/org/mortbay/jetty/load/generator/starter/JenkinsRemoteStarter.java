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

package org.mortbay.jetty.load.generator.starter;

import com.beust.jcommander.JCommander;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.QpsListenerDisplay;
import org.mortbay.jetty.load.generator.listeners.RequestQueuedListenerDisplay;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Class to start remote process for Jenkins
 */
public class JenkinsRemoteStarter
{

    private static final Logger LOGGER = Log.getLogger( JenkinsRemoteStarter.class );

    public static List<Resource.NodeListener> nodeListeners;

    public static List<Resource.NodeListener> getNodeListeners()
    {
        return nodeListeners;
    }

    public static void setNodeListeners( List<Resource.NodeListener> nodeListeners )
    {
        JenkinsRemoteStarter.nodeListeners = nodeListeners;
    }

    public static List<LoadGenerator.Listener> loadGeneratorListeners;

    public static List<LoadGenerator.Listener> getLoadGeneratorListeners()
    {
        return loadGeneratorListeners;
    }

    public static void setLoadGeneratorListeners( List<LoadGenerator.Listener> loadGeneratorListeners )
    {
        JenkinsRemoteStarter.loadGeneratorListeners = loadGeneratorListeners;
    }

    public static void main( String... args)  throws Exception {
        String slaveAgentSocket = args[0];
        int i = slaveAgentSocket.indexOf(':');
        if (i > 0)
        {
            main( slaveAgentSocket.substring( 0, i ), Integer.parseInt( slaveAgentSocket.substring( i + 1 ) ) );
        } else {
            main( null, Integer.parseInt( slaveAgentSocket ) );
        }
    }

    public static void main( String agentIp, int tcpPort) throws Exception {


        final Socket s = new Socket( agentIp, tcpPort);

        ClassLoader classLoader = JenkinsRemoteStarter.class.getClassLoader();

        Class remotingLauncher = classLoader.loadClass("hudson.remoting.Launcher");

        remotingLauncher.getMethod("main", //
                                   new Class[] { InputStream.class, OutputStream.class }).invoke( //
            null, //
            new Object[] { //
                // do partial close, since socket.getInputStream and
                // getOutputStream doesn't do it by
                new BufferedInputStream( //
                    new FilterInputStream( s.getInputStream()) { //
                        public void close() throws IOException { //
                            s.shutdownInput(); //
                        } //
                    }), //
                    new BufferedOutputStream( //
                        new RealFilterOutputStream( s.getOutputStream()) { //
                    public void close() throws IOException { //
                        s.shutdownOutput(); //
                    } //
                }) }); //
        System.exit(0);
    }


    static class RealFilterOutputStream
        extends FilterOutputStream
    {
        public RealFilterOutputStream( OutputStream core ) {
            super(core);
        }

        public void write(byte[] b) throws IOException {
            out.write(b);
        }

        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        public void close() throws IOException {
            out.close();
        }
    }


    public static void launch( List<String> argsList )
        throws Exception
    {

        final String[] args = argsList.toArray( new String[argsList.size()] );

        LoadGeneratorStarterArgs runnerArgs = new LoadGeneratorStarterArgs();

        try
        {
            JCommander jCommander = new JCommander( runnerArgs, args );
            if ( runnerArgs.isHelp() )
            {
                jCommander.usage();
                System.exit( 0 );
            }
        }
        catch ( Exception e )
        {
            new JCommander( runnerArgs ).usage();
        }

        try
        {
            LoadGeneratorStarter runner = new LoadGeneratorStarter( runnerArgs )
            {

                QpsListenerDisplay qpsListenerDisplay =
                    new QpsListenerDisplay( 10, 30, TimeUnit.SECONDS );

                private RequestQueuedListenerDisplay requestQueuedListenerDisplay = //
                    // FIXME those values need to be configurable!! //
                    new RequestQueuedListenerDisplay(10, 30, TimeUnit.SECONDS);

                @Override
                protected Resource.Listener[] getResourceListeners()
                {
                    return nodeListeners.toArray(new Resource.Listener[nodeListeners.size()]);
                }

                @Override
                protected LoadGenerator.Listener[] getLoadGeneratorListeners()
                {
                    loadGeneratorListeners.add( qpsListenerDisplay );
                    loadGeneratorListeners.add( requestQueuedListenerDisplay );
                    return loadGeneratorListeners.toArray( new LoadGenerator.Listener[loadGeneratorListeners.size()] );
                }

                @Override
                protected Request.Listener[] getListeners()
                {
                    return new Request.Listener[]{qpsListenerDisplay, requestQueuedListenerDisplay};
                }
            };

            LOGGER.info( "start LoadGenerator to " + runnerArgs.getHost() + " for " + runnerArgs.getRunningTime() + " "
                             + runnerArgs.getRunningTimeUnit() );
            runner.run();

            LOGGER.info( "LoadGenerator stopped" );

        }
        catch ( Exception e )
        {
            e.printStackTrace();
            new JCommander( runnerArgs ).usage();
            System.exit( 1 );
        }

    }

}
