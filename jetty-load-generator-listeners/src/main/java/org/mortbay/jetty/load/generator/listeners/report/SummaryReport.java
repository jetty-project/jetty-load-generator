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

package org.mortbay.jetty.load.generator.listeners.report;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.mortbay.jetty.load.generator.listeners.CollectorInformations;

/**
 *
 */
public class SummaryReport
{

    private Map<String, CollectorInformations> responseTimeInformationsPerPath = new ConcurrentHashMap<>();

    private Map<String, CollectorInformations> latencyTimeInformationsPerPath = new ConcurrentHashMap<>();

    private String buildId;

    public SummaryReport(String buildId)
    {
        this.buildId = buildId;
    }

    public Map<String, CollectorInformations> getResponseTimeInformationsPerPath()
    {
        return responseTimeInformationsPerPath;
    }

    public void setResponseTimeInformationsPerPath( Map<String, CollectorInformations> responseTimeInformationsPerPath )
    {
        this.responseTimeInformationsPerPath = responseTimeInformationsPerPath;
    }

    public void addResponseTimeInformations( String path, CollectorInformations collectorInformations )
    {
        this.responseTimeInformationsPerPath.put( path, collectorInformations );
    }

    public Map<String, CollectorInformations> getLatencyTimeInformationsPerPath()
    {
        return latencyTimeInformationsPerPath;
    }

    public void setLatencyTimeInformationsPerPath( Map<String, CollectorInformations> latencyTimeInformationsPerPath )
    {
        this.latencyTimeInformationsPerPath = latencyTimeInformationsPerPath;
    }

    public void addLatencyTimeInformations( String path, CollectorInformations collectorInformations )
    {
        this.latencyTimeInformationsPerPath.put( path, collectorInformations );
    }
}
