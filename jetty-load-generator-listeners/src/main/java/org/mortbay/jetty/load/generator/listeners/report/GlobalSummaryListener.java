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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.HdrHistogram.Recorder;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.HistogramConstants;

/**
 * This will collect a global histogram for all response and latency times
 */
public class GlobalSummaryListener
    extends Request.Listener.Adapter
    implements Resource.NodeListener
{

    private static final Logger LOGGER = Log.getLogger( GlobalSummaryListener.class );

    private Recorder responseHistogram, latencyHistogram;

    private List<Integer> excludeHttpStatusFamily = new ArrayList<>();

    private final LongAdder responses1xx = new LongAdder();

    private final LongAdder responses2xx = new LongAdder();

    private final LongAdder responses3xx = new LongAdder();

    private final LongAdder responses4xx = new LongAdder();

    private final LongAdder responses5xx = new LongAdder();

    private final LongAdder requestCommitTotal = new LongAdder();

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

    /**
     * @param httpStatusFamilies if you want to exclude 1xx or 5xx, add 100 or 500
     * @return
     */
    public GlobalSummaryListener addExcludeHttpStatusFamily( int... httpStatusFamilies )
    {
        if ( httpStatusFamilies == null )
        {
            return this;
        }
        for ( int status : httpStatusFamilies )
        {
            this.excludeHttpStatusFamily.add( status / 100 );
        }
        return this;
    }

    @Override
    public void onResourceNode( Resource.Info info )
    {
        switch ( info.getStatus() / 100 )
        {
            case 1:
                responses1xx.increment();
                break;
            case 2:
                responses2xx.increment();
                break;
            case 3:
                responses3xx.increment();
                break;
            case 4:
                responses4xx.increment();
                break;
            case 5:
                responses5xx.increment();
                break;
            default:
                break;
        }

        if ( this.excludeHttpStatusFamily.contains( info.getStatus() / 100 ) )
        {
            if ( LOGGER.isDebugEnabled() )
            {
                LOGGER.debug( "exclude http status: {}", info.getStatus() );
            }
            return;
        }
        try
        {
            long latencyTime = info.getLatencyTime() - info.getRequestTime();
            if ( LOGGER.isDebugEnabled() )
            {
                LOGGER.debug( "latencyTime: {} resource: {}, status: {}", //
                              TimeUnit.MILLISECONDS.convert( latencyTime, TimeUnit.NANOSECONDS ), //
                              info.getResource().getPath(), //
                              info.getStatus() );
            }
            latencyHistogram.recordValue( latencyTime );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "fail to record latency value: {}", info.getLatencyTime() );
        }
        try
        {
            long responseTime = info.getResponseTime() - info.getRequestTime();
            LOGGER.debug( "responseTime: {} resource: {}, status: {}", //
                          TimeUnit.MILLISECONDS.convert( responseTime, TimeUnit.NANOSECONDS ), //
                          info.getResource().getPath(), //
                          info.getStatus() );
            responseHistogram.recordValue( responseTime );
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

    public LongAdder getResponses1xx()
    {
        return responses1xx;
    }

    public LongAdder getResponses2xx()
    {
        return responses2xx;
    }

    public LongAdder getResponses3xx()
    {
        return responses3xx;
    }

    public LongAdder getResponses4xx()
    {
        return responses4xx;
    }

    public LongAdder getResponses5xx()
    {
        return responses5xx;
    }

    public long getTotalResponse()
    {
        return responses1xx.longValue() //
            + responses2xx.longValue() //
            + responses3xx.longValue() //
            + responses4xx.longValue() //
            + responses5xx.longValue();
    }

    @Override
    public void onCommit( Request request )
    {
        requestCommitTotal.increment();
    }

    public long getRequestCommitTotal()
    {
        return requestCommitTotal.longValue();
    }
}
