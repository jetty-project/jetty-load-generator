package org.eclipse.jetty.load.generator.starter;

import com.beust.jcommander.JCommander;
import org.eclipse.jetty.load.generator.responsetime.ResponseTimeListener;

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

    public static List<ResponseTimeListener> responseTimeListeners;

    public static List<ResponseTimeListener> getResponseTimeListeners()
    {
        return responseTimeListeners;
    }

    public static void setResponseTimeListeners( List<ResponseTimeListener> responseTimeListeners )
    {
        JenkinsRemoteStarter.responseTimeListeners = responseTimeListeners;
    }

    public static void main( String... args)  throws Exception {
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
                protected ResponseTimeListener[] getResponseTimeListeners()
                {
                    return responseTimeListeners.toArray(new ResponseTimeListener[responseTimeListeners.size()]);
                }
            };

            if (runnerArgs.getRunIteration() > 0)
            {
                runner.run(runnerArgs.getRunIteration());
            } else
            {
                runner.run();
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            new JCommander( runnerArgs ).usage();
            System.exit( 1 );
        }

    }


}
