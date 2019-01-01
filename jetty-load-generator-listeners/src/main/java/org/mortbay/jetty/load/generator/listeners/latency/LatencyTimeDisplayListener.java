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

package org.mortbay.jetty.load.generator.listeners.latency;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.CollectorInformations;
import org.mortbay.jetty.load.generator.listeners.HistogramConstants;

/**
 *
 */
public class LatencyTimeDisplayListener
    extends Request.Listener.Adapter
    implements Resource.NodeListener, LoadGenerator.EndListener
{

    private static final Logger LOGGER = Log.getLogger( LatencyTimeDisplayListener.class );

    private ScheduledExecutorService scheduledExecutorService;

    private ValueListenerRunnable runnable;

    private volatile Recorder latencyRecorder, totalCommittedRecorder;

    private List<Integer> excludeHttpStatusFamily = new ArrayList<>();

    private List<ValueListener> valueListeners = new ArrayList<>();

    public LatencyTimeDisplayListener( long initial, long delay, TimeUnit timeUnit )
    {
        this( HistogramConstants.LOWEST_DISCERNIBLE_VALUE, //
              HistogramConstants.HIGHEST_TRACKABLE_VALUE,  //
              HistogramConstants.NUMBER_OF_SIGNIFICANT_VALUE_DIGITS,  //
              initial, //
              delay,  //
              timeUnit, //
              Collections.emptyList() );
    }

    public LatencyTimeDisplayListener( long lowestDiscernibleValue, long highestTrackableValue,
                                       int numberOfSignificantValueDigits, long initial, long delay, TimeUnit timeUnit,
                                       List<ValueListener> valueListeners )
    {
        this.valueListeners.addAll( valueListeners );
        latencyRecorder = new Recorder( lowestDiscernibleValue, //
                                        highestTrackableValue, //
                                        numberOfSignificantValueDigits );
        totalCommittedRecorder = new Recorder( lowestDiscernibleValue, //
                                               highestTrackableValue, //
                                               numberOfSignificantValueDigits );
        this.runnable = new ValueListenerRunnable( latencyRecorder, totalCommittedRecorder, //
                                                   this.valueListeners );
        // FIXME configurable or using a shared one
        scheduledExecutorService = Executors.newScheduledThreadPool( 1 );
        scheduledExecutorService.scheduleWithFixedDelay( runnable, initial, delay, timeUnit );
    }

    public LatencyTimeDisplayListener addValueListener( ValueListener valueListener )
    {
        this.valueListeners.add( valueListener );
        return this;
    }

    /**
     * @param httpStatusFamilies if you want to exclude 1xx or 5xx, add 100 or 500
     * @return
     */
    public LatencyTimeDisplayListener addExcludeHttpStatusFamily( int... httpStatusFamilies )
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

    public LatencyTimeDisplayListener()
    {
        this( 0, 5, TimeUnit.SECONDS );
    }

    private static class ValueListenerRunnable
        implements Runnable
    {
        private final Recorder latencyRecorder, totalCommittedRecorder;

        private final List<ValueListener> valueListeners;

        private ValueListenerRunnable( Recorder latencyRecorder, Recorder totalCommittedRecorder,
                                       List<ValueListener> valueListeners )
        {
            this.latencyRecorder = latencyRecorder;
            this.valueListeners = valueListeners;
            this.totalCommittedRecorder = totalCommittedRecorder;
        }

        @Override
        public void run()
        {
            Histogram latencyHistogram = latencyRecorder.getIntervalHistogram(), //
                totalHistogram = totalCommittedRecorder.getIntervalHistogram();

            for ( ValueListener valueListener : valueListeners )
            {
                valueListener.onValue( latencyHistogram, totalHistogram );
            }

        }
    }

    @Override
    public void onResourceNode( Resource.Info info )
    {
        if ( this.excludeHttpStatusFamily.contains( info.getStatus() / 100 ) )
        {
            if ( LOGGER.isDebugEnabled() )
            {
                LOGGER.debug( "exclude http status: {}", info.getStatus() );
            }
            return;
        }
        long time = info.getLatencyTime() - info.getRequestTime();
        try
        {
            latencyRecorder.recordValue( time );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "skip error recording time {}, {}", time, e.getMessage() );
        }
    }

    @Override
    public void onEnd( LoadGenerator generator )
    {
        if ( !scheduledExecutorService.isShutdown() )
        {
            scheduledExecutorService.shutdownNow();
        }
    }

    public interface ValueListener
    {
        /**
         * @param latencyHistogram histogram without the excluded http response
         * @param totalHistogram   histogram with all http response
         */
        void onValue( Histogram latencyHistogram, Histogram totalHistogram );
    }

    public static class PrintValueListener
        implements ValueListener
    {
        @Override
        public void onValue( Histogram latencyHistogram, Histogram totalHistogram )
        {
            StringBuilder message = new StringBuilder().append( System.lineSeparator() );
            message.append( new CollectorInformations( latencyHistogram ) //
                                .toString( true ) ) //
                .append( System.lineSeparator() );
            LOGGER.info( message.toString() );
        }
    }

    @Override
    public void onCommit( Request request )
    {
        totalCommittedRecorder.recordValue( 1 );
    }
}
