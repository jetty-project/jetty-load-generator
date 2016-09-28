package org.eclipse.jetty.load.generator.report;

import org.eclipse.jetty.load.generator.CollectorInformations;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 *
 */
public class SummaryReport
{

    private Map<String, CollectorInformations> collectorInformationsPerPath = new ConcurrentHashMap<>();

    public SummaryReport()
    {
        // no op
    }

    public Map<String, CollectorInformations> getCollectorInformationsPerPath()
    {
        return collectorInformationsPerPath;
    }

    public void setCollectorInformationsPerPath( Map<String, CollectorInformations> collectorInformationsPerPath )
    {
        this.collectorInformationsPerPath = collectorInformationsPerPath;
    }

    public void addCollectorInformations( String path, CollectorInformations collectorInformations )
    {
        this.collectorInformationsPerPath.put( path, collectorInformations );
    }
}
