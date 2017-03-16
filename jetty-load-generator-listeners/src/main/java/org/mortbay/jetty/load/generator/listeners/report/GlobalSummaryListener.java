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

package org.mortbay.jetty.load.generator.listeners.report;

import org.HdrHistogram.Recorder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.HistogramConstants;

/**
 * This will collect a global histogram for all response and latency times
 */
public class GlobalSummaryListener
    implements Resource.NodeListener
{

    private static final Logger LOGGER = Log.getLogger( GlobalSummaryListener.class );

    private Recorder responseHistogram, latencyHistogram;


    public GlobalSummaryListener( long lowestDiscernibleValue, long highestTrackableValue,
                                  int numberOfSignificantValueDigits )
    {
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
    public void onResourceNode( Resource.Info info )
    {
        try
        {
            latencyHistogram.recordValue( info.getLatencyTime() - info.getRequestTime() );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "fail to record latency value: {}", info.getLatencyTime() );
        }
        try
        {
            responseHistogram.recordValue( info.getResponseTime() - info.getRequestTime() );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "fail to record response time value: {}", info.getLatencyTime() );
        }
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
