package org.eclipse.jetty.load.generator.report;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.load.generator.responsetime.RecorderConstants;
import org.eclipse.jetty.load.generator.responsetime.ResponseTimeListener;

/**
 * Created by olamy on 30/9/16.
 */
public class GlobalSummaryReportListener
    implements ResponseTimeListener
{


    private Recorder recorder;

    public GlobalSummaryReportListener( long lowestDiscernibleValue, long highestTrackableValue,
                                        int numberOfSignificantValueDigits )
    {
        this.recorder = new Recorder( lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits );
    }

    public GlobalSummaryReportListener()
    {
        this.recorder = new Recorder( RecorderConstants.LOWEST_DISCERNIBLE_VALUE, //
                                      RecorderConstants.HIGHEST_TRACKABLE_VALUE, //
                                      RecorderConstants.NUMBER_OF_SIHNIFICANT_VALUE_DIGITS );
    }

    @Override
    public void onResponseTimeValue( Values values )
    {
        recorder.recordValue( values.getTime() );

    }

    @Override
    public void onLoadGeneratorStop()
    {
        // no op
    }


    public Histogram getHistogram()
    {
        return recorder.getIntervalHistogram();
    }

}
