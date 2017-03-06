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

import org.HdrHistogram.Recorder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.ValueListener;
import org.mortbay.jetty.load.generator.latency.LatencyTimeListener;
import org.mortbay.jetty.load.generator.responsetime.HistogramConstants;
import org.mortbay.jetty.load.generator.responsetime.ResponseTimeListener;

import java.io.Serializable;

/**
 * This will collect a global histogram for all response and latency times
 */
public class GlobalSummaryListener
    implements ResponseTimeListener, LatencyTimeListener, Serializable
{

    private static final Logger LOGGER = Log.getLogger( GlobalSummaryListener.class );

    private Recorder responseHistogram, latencyHistogram;

    private final long lowestDiscernibleValue, highestTrackableValue;

    private int numberOfSignificantValueDigits;

    public GlobalSummaryListener( long lowestDiscernibleValue, long highestTrackableValue,
                                  int numberOfSignificantValueDigits )
    {
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
        this.responseHistogram =
            new Recorder( lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits );
        this.latencyHistogram =
            new Recorder( lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits );
    }

    public GlobalSummaryListener()
    {
        this( HistogramConstants.LOWEST_DISCERNIBLE_VALUE, //
              HistogramConstants.HIGHEST_TRACKABLE_VALUE, //
              HistogramConstants.NUMBER_OF_SIGNIFICANT_VALUE_DIGITS );
    }

    @Override
    public void reset( LoadGenerator loadGenerator )
    {
        synchronized ( this )
        {
            this.responseHistogram.reset();
            this.latencyHistogram.reset();
        }
    }

    @Override
    public void onResponseTimeValue( ValueListener.Values values )
    {
        long time = values.getTime();
        try
        {
            responseHistogram.recordValue( time );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "skip error recording response time {}, {}", time, e.getMessage() );
        }

    }

    @Override
    public void onLatencyTimeValue( ValueListener.Values values )
    {
        long time = values.getTime();
        try
        {
            latencyHistogram.recordValue( time );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "skip error recording latency time {}, {}", time, e.getMessage() );
        }
    }

    @Override
    public void onLoadGeneratorStop()
    {
        // no op
    }


    public Recorder getResponseTimeHistogram()
    {
        return responseHistogram;
    }

    public Recorder getLatencyTimeHistogram()
    {
        return latencyHistogram;
    }

}
