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

package org.mortbay.jetty.load.generator.listeners;

public class LoadResult
{

    private ServerInfo serverInfo;

    private CollectorInformations collectorInformations;

    private LoadConfig loadConfig;

    public LoadResult()
    {
        // no op
    }

    public LoadResult( ServerInfo serverInfo, CollectorInformations collectorInformations, LoadConfig loadConfig )
    {
        this.serverInfo = serverInfo;
        this.collectorInformations = collectorInformations;
        this.loadConfig = loadConfig;
    }

    public ServerInfo getServerInfo()
    {
        return serverInfo;
    }

    public CollectorInformations getCollectorInformations()
    {
        return collectorInformations;
    }

    public void setServerInfo( ServerInfo serverInfo )
    {
        this.serverInfo = serverInfo;
    }

    public void setCollectorInformations( CollectorInformations collectorInformations )
    {
        this.collectorInformations = collectorInformations;
    }

    public LoadConfig getLoadConfig()
    {
        return loadConfig;
    }

    public void setLoadConfig( LoadConfig loadConfig )
    {
        this.loadConfig = loadConfig;
    }

    @Override
    public String toString()
    {
        return "LoadResult{" + "serverInfo=" + serverInfo + ", collectorInformations=" + collectorInformations
            + ", loadConfig=" + loadConfig + '}';
    }
}
