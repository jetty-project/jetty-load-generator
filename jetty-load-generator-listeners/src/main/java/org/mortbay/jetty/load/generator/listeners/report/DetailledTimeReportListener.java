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

import java.io.Serializable;

import org.mortbay.jetty.load.generator.Resource;

/**
 * Use this one to collect all values
 */
public class DetailledTimeReportListener
    implements  Serializable, Resource.NodeListener
{
    private DetailledTimeValuesReport detailledResponseTimeValuesReport = new DetailledTimeValuesReport();

    private DetailledTimeValuesReport detailledLatencyTimeValuesReport = new DetailledTimeValuesReport();

    @Override
    public void onResourceNode( Resource.Info info )
    {
        this.detailledLatencyTimeValuesReport.addEntry(
            new DetailledTimeValuesReport.Entry( info.getRequestTime(), //
                                                 info.getResource().getPath(), //
                                                 info.getStatus(), //
                                                 info.getLatencyTime() - info.getRequestTime() ) );

        this.detailledResponseTimeValuesReport.addEntry(
            new DetailledTimeValuesReport.Entry( info.getRequestTime(), //
                                                 info.getResource().getPath(), //
                                                 info.getStatus(), //
                                                 info.getResponseTime() - info.getRequestTime()) );
    }

    public DetailledTimeValuesReport getDetailledResponseTimeValuesReport()
    {
        return detailledResponseTimeValuesReport;
    }

    public DetailledTimeValuesReport getDetailledLatencyTimeValuesReport()
    {
        return detailledLatencyTimeValuesReport;
    }
}
