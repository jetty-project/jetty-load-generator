package org.eclipse.jetty.load.generator.starter;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Class to start remote process for Jenkins
 */
public class JenkinsRemoteStarter
{

    public static void main(String... args)  throws Exception {
        String slaveAgentSocket = args[0];
        int i = slaveAgentSocket.indexOf(':');
        main( slaveAgentSocket.substring(0, i), Integer.parseInt(slaveAgentSocket.substring(i+1) ));
    }

    public static void main( String agentIp, int tcpPort) throws Exception {


        final Socket s = new Socket( agentIp, tcpPort);

        ClassLoader classLoader = JenkinsRemoteStarter.class.getClassLoader();

        Class remotingLauncher = classLoader.loadClass("hudson.remoting.Launcher");

        remotingLauncher.getMethod("main",
                                   new Class[] { InputStream.class, OutputStream.class }).invoke(
            null,
            new Object[] {
                // do partial close, since socket.getInputStream and
                // getOutputStream doesn't do it by
                new BufferedInputStream(
                    new FilterInputStream( s.getInputStream()) {
                        public void close() throws IOException {
                            s.shutdownInput();
                        }
                    }),
                    new BufferedOutputStream(
                        new RealFilterOutputStream( s.getOutputStream()) {
                    public void close() throws IOException {
                        s.shutdownOutput();
                    }
                }) });
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
}
