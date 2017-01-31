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
import org.mortbay.jetty.load.generator.latency.LatencyTimeListener;
import org.mortbay.jetty.load.generator.responsetime.ResponseTimeListener;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;

/**
 * Class to start remote process for Jenkins
 */
public class JenkinsRemoteStarter
{

    private static final Logger LOGGER = Log.getLogger( JenkinsRemoteStarter.class );

    public static List<ResponseTimeListener> responseTimeListeners;

    public static List<ResponseTimeListener> getResponseTimeListeners()
    {
        return responseTimeListeners;
    }

    public static void setResponseTimeListeners( List<ResponseTimeListener> responseTimeListeners )
    {
        JenkinsRemoteStarter.responseTimeListeners = responseTimeListeners;
    }

    public static List<LatencyTimeListener> latencyTimeListeners;

    public static List<LatencyTimeListener> getLatencyTimeListeners()
    {
        return latencyTimeListeners;
    }

    public static void setLatencyTimeListeners( List<LatencyTimeListener> latencyTimeListeners )
    {
        JenkinsRemoteStarter.latencyTimeListeners = latencyTimeListeners;
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


    public static void launch(List<String> argsList) throws Exception {

        final String[] args = argsList.toArray(new String[argsList.size()]);

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
            LoadGeneratorStarter runner = new LoadGeneratorStarter( runnerArgs ){
                @Override
                public ResponseTimeListener[] getResponseTimeListeners()
                {
                    return responseTimeListeners.toArray(new ResponseTimeListener[responseTimeListeners.size()]);
                }

                @Override
                public LatencyTimeListener[] getLatencyTimeListeners()
                {
                    return latencyTimeListeners.toArray(new LatencyTimeListener[latencyTimeListeners.size()]);
                }
            };


            if (runnerArgs.getRunIteration() > 0)
            {
                LOGGER.info( "start LoadGenerator to " + runnerArgs.getHost()  + " for " + runnerArgs.getRunIteration() + " iterations" );
                runner.run(runnerArgs.getRunIteration(), !runnerArgs.isNotInterrupt());
            } else
            {
                LOGGER.info( "start LoadGenerator to " + runnerArgs.getHost() + " for " + runnerArgs.getRunningTime() + " " + runnerArgs.getRunningTimeUnit() );
                runner.run();
            }

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
