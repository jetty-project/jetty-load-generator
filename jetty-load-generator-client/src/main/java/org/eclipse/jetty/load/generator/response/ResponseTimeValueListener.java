package org.eclipse.jetty.load.generator.response;

import org.eclipse.jetty.load.generator.CollectorInformations;

/**
 *
 */
public interface ResponseTimeValueListener
{
    void onValue( String path, CollectorInformations collectorInformations );
}
