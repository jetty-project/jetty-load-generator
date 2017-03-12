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

package org.mortbay.jetty.load.generator.report;

import org.mortbay.jetty.load.generator.ValueListener;
import org.mortbay.jetty.load.generator.latency.LatencyTimeListener;
import org.mortbay.jetty.load.generator.resource.Resource;
import org.mortbay.jetty.load.generator.responsetime.ResponseTimeListener;

import java.io.Serializable;

/**
 * Use this one to collect all values
 */
public class DetailledTimeReportListener
    implements ResponseTimeListener, LatencyTimeListener, Serializable, Resource.NodeListener
{
    private DetailledTimeValuesReport detailledTimeValuesReport = new DetailledTimeValuesReport();

    private DetailledTimeValuesReport detailledLatencyTimeValuesReport = new DetailledTimeValuesReport();

    @Override
    public void onLoadGeneratorStop()
    {
        // no op
    }

    @Override
    public void onResponseTimeValue( ValueListener.Values values )
    {
        this.detailledTimeValuesReport.addEntry(
            new DetailledTimeValuesReport.Entry( values.getEventTimestamp(), //
                                                 values.getPath(), //
                                                 values.getStatus(), //
                                                 values.getTime() ) );
    }

    @Override
    public void onLatencyTimeValue( ValueListener.Values values )
    {
        this.detailledLatencyTimeValuesReport.addEntry(
            new DetailledTimeValuesReport.Entry( values.getEventTimestamp(), //
                                                 values.getPath(), //
                                                 values.getStatus(), //
                                                 values.getTime() ) );
    }

    @Override
    public void onResourceNode( Resource.Info info )
    {
        //info.
    }

    public DetailledTimeValuesReport getDetailledResponseTimeValuesReport()
    {
        return detailledTimeValuesReport;
    }

    public DetailledTimeValuesReport getDetailledLatencyTimeValuesReport()
    {
        return detailledLatencyTimeValuesReport;
    }
}
