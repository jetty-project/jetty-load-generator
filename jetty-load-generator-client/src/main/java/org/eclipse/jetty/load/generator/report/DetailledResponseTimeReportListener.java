package org.eclipse.jetty.load.generator.report;

import org.eclipse.jetty.load.generator.responsetime.ResponseTimeListener;

/**
 * Created by olamy on 28/9/16.
 */
public class DetailledResponseTimeReportListener
    implements ResponseTimeListener
{
    private DetailledResponseTimeReport detailledResponseTimeReport = new DetailledResponseTimeReport();

    @Override
    public void onLoadGeneratorStop()
    {
        // no op
    }

    @Override
    public void onResponseTimeValue( Values values )
    {
        this.detailledResponseTimeReport.addEntry(
            new DetailledResponseTimeReport.Entry( System.nanoTime(), //
                                                   values.getPath(), //
                                                   values.getStatus(), //
                                                   values.getTime() ) );
    }

    public DetailledResponseTimeReport getDetailledResponseTimeReport()
    {
        return detailledResponseTimeReport;
    }
}
