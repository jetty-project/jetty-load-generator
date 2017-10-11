package org.mortbay.jetty.load.generator.listeners;

import org.mortbay.jetty.load.generator.listeners.CollectorInformations;
import org.mortbay.jetty.load.generator.listeners.ServerInfo;

public class LoadResult
{

    private final ServerInfo serverInfo;

    private final CollectorInformations collectorInformations;

    public LoadResult( ServerInfo serverInfo, CollectorInformations collectorInformations )
    {
        this.serverInfo = serverInfo;
        this.collectorInformations = collectorInformations;
    }

    public ServerInfo getServerInfo()
    {
        return serverInfo;
    }

    public CollectorInformations getCollectorInformations()
    {
        return collectorInformations;
    }

    @Override
    public String toString()
    {
        return "LoadResult{" + "serverInfo=" + serverInfo + ", collectorInformations=" + collectorInformations + '}';
    }
}
