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

package org.mortbay.jetty.load.generator.starter;

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

import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.QpsListenerDisplay;
import org.mortbay.jetty.load.generator.listeners.RequestQueuedListenerDisplay;

/**
 * Class to start remote process for Jenkins
 */
public class JenkinsRemoteStarter {
    private static List<Resource.NodeListener> nodeListeners;
    private static List<LoadGenerator.Listener> loadGeneratorListeners;

    public static List<Resource.NodeListener> getNodeListeners() {
        return nodeListeners;
    }

    public static void setNodeListeners(List<Resource.NodeListener> nodeListeners) {
        JenkinsRemoteStarter.nodeListeners = nodeListeners;
    }

    public static List<LoadGenerator.Listener> getLoadGeneratorListeners() {
        return loadGeneratorListeners;
    }

    public static void setLoadGeneratorListeners(List<LoadGenerator.Listener> loadGeneratorListeners) {
        JenkinsRemoteStarter.loadGeneratorListeners = loadGeneratorListeners;
    }

    public static void main(String... args) throws Exception {
        String slaveAgentSocket = args[0];
        int i = slaveAgentSocket.indexOf(':');
        if (i > 0) {
            main(slaveAgentSocket.substring(0, i), Integer.parseInt(slaveAgentSocket.substring(i + 1)));
        } else {
            main(null, Integer.parseInt(slaveAgentSocket));
        }
    }

    public static void main(String agentIp, int tcpPort) throws Exception {
        final Socket s = new Socket(agentIp, tcpPort);

        ClassLoader classLoader = JenkinsRemoteStarter.class.getClassLoader();

        Class<?> remotingLauncher = classLoader.loadClass("hudson.remoting.Launcher");

        remotingLauncher.getMethod("main", 
                new Class[]{InputStream.class, OutputStream.class}).invoke( 
                null,
                // do partial close, since socket.getInputStream and
                // getOutputStream doesn't do it by
                new BufferedInputStream(
                        new FilterInputStream(s.getInputStream()) {
                            public void close() throws IOException {
                                s.shutdownInput();
                            }
                        }),
                new BufferedOutputStream(
                        new RealFilterOutputStream(s.getOutputStream()) {
                            public void close() throws IOException {
                                s.shutdownOutput();
                            }
                        })
        );
        System.exit(0);
    }

    static class RealFilterOutputStream extends FilterOutputStream {
        public RealFilterOutputStream(OutputStream core) {
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
        LoadGeneratorStarterArgs starterArgs = LoadGeneratorStarter.parse(args);
        LoadGenerator.Builder builder = LoadGeneratorStarter.prepare(starterArgs);
        if (nodeListeners != null) {
            nodeListeners.forEach(builder::resourceListener);
        }
        if (loadGeneratorListeners != null) {
            loadGeneratorListeners.forEach(builder::listener);
        }
        QpsListenerDisplay qpsListenerDisplay = new QpsListenerDisplay(10, 30, TimeUnit.SECONDS);
        RequestQueuedListenerDisplay requestQueuedListenerDisplay = new RequestQueuedListenerDisplay(10, 30, TimeUnit.SECONDS);
        builder.listener(qpsListenerDisplay).listener(requestQueuedListenerDisplay)
                .requestListener(qpsListenerDisplay).requestListener(requestQueuedListenerDisplay);
        LoadGeneratorStarter.run(builder);
    }
}
