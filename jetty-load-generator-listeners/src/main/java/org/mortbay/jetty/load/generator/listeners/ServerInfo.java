package org.mortbay.jetty.load.generator.listeners;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;

public class ServerInfo
{
    private String jettyVersion;

    private int availableProcessors;

    private long totalMemory;

    public String getJettyVersion()
    {
        return jettyVersion;
    }

    public int getAvailableProcessors()
    {
        return availableProcessors;
    }

    public long getTotalMemory()
    {
        return totalMemory;
    }

    public void setJettyVersion( String jettyVersion )
    {
        this.jettyVersion = jettyVersion;
    }

    public void setAvailableProcessors( int availableProcessors )
    {
        this.availableProcessors = availableProcessors;
    }

    public void setTotalMemory( long totalMemory )
    {
        this.totalMemory = totalMemory;
    }

    @Override
    public String toString()
    {
        return "ServerInfo{" + "jettyVersion='" + jettyVersion + '\'' + ", availableProcessors=" + availableProcessors
            + ", totalMemory=" + totalMemory + '}';
    }

    public static ServerInfo retrieveServerInfo( String scheme, String host, int port, String path )
        throws Exception
    {
        HttpClient httpClient = new HttpClient();

        try
        {
            httpClient.start();
            ContentResponse contentResponse = httpClient.newRequest( host, port ).scheme( scheme ).path( path ).send();

            return new ObjectMapper().readValue( contentResponse.getContent(), ServerInfo.class );
        }
        finally
        {
            httpClient.stop();
        }

    }

}
