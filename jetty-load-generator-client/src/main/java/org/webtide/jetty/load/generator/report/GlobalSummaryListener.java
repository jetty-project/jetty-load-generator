//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
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

package org.webtide.jetty.load.generator.report;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.webtide.jetty.load.generator.latency.LatencyTimeListener;
import org.webtide.jetty.load.generator.responsetime.RecorderConstants;
import org.webtide.jetty.load.generator.responsetime.ResponseTimeListener;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.io.Serializable;

/**
 * This will collect a global histogram for all response and latency times
 */
public class GlobalSummaryListener
    implements ResponseTimeListener, LatencyTimeListener, Serializable
{

    private static final Logger LOGGER = Log.getLogger( GlobalSummaryListener.class );

    private Recorder responseTimeRecorder, latencyTimeRecorder;

    public GlobalSummaryListener( long lowestDiscernibleValue, long highestTrackableValue,
                                  int numberOfSignificantValueDigits )
    {
        this.responseTimeRecorder =
            new Recorder( lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits );
        this.latencyTimeRecorder =
            new Recorder( lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits );
    }

    public GlobalSummaryListener()
    {
        this.responseTimeRecorder = new Recorder( RecorderConstants.LOWEST_DISCERNIBLE_VALUE, //
                                                  RecorderConstants.HIGHEST_TRACKABLE_VALUE, //
                                                  RecorderConstants.NUMBER_OF_SIHNIFICANT_VALUE_DIGITS );

        this.latencyTimeRecorder = new Recorder( RecorderConstants.LOWEST_DISCERNIBLE_VALUE, //
                                                 RecorderConstants.HIGHEST_TRACKABLE_VALUE, //
                                                 RecorderConstants.NUMBER_OF_SIHNIFICANT_VALUE_DIGITS );
    }

    @Override
    public void onResponseTimeValue( Values values )
    {
        long time = values.getTime();
        try
        {
            responseTimeRecorder.recordValue( time );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "skip error recording time {}, {}", time, e.getMessage() );
        }

    }

    @Override
    public void onLatencyTimeValue( Values values )
    {
        long time = values.getTime();
        try
        {
            latencyTimeRecorder.recordValue( time );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "skip error recording time {}, {}", time, e.getMessage() );
        }
    }

    @Override
    public void onLoadGeneratorStop()
    {
        // no op
    }


    public Histogram getResponseTimeHistogram()
    {
        return responseTimeRecorder.getIntervalHistogram();
    }

    public Histogram getLatencyTimeHistogram()
    {
        return latencyTimeRecorder.getIntervalHistogram();
    }

}
