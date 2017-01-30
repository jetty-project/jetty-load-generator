package org.webtide.jetty.load.generator.report;

import org.webtide.jetty.load.generator.CollectorInformations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
