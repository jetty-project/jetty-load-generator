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

package org.mortbay.jetty.load.generator.listeners.latency;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.CollectorInformations;
import org.mortbay.jetty.load.generator.listeners.HistogramConstants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class LatencyTimeDisplayListener
    implements Resource.NodeListener, LoadGenerator.EndListener
{

    private static final Logger LOGGER = Log.getLogger( LatencyTimeDisplayListener.class );

    private ScheduledExecutorService scheduledExecutorService;

    private ValueListenerRunnable runnable;

    private Recorder recorder;

    private final long lowestDiscernibleValue;
    private final long highestTrackableValue;
    private final int numberOfSignificantValueDigits;

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
                                       int numberOfSignificantValueDigits, long initial, long delay,
                                       TimeUnit timeUnit, List<ValueListener> valueListeners )
    {
        this.valueListeners.addAll( valueListeners );
        recorder  = new Recorder( lowestDiscernibleValue, //
                                  highestTrackableValue, //
                                  numberOfSignificantValueDigits );
        this.runnable = new ValueListenerRunnable( recorder, this.valueListeners );
        // FIXME configurable or using a shared one
        scheduledExecutorService = Executors.newScheduledThreadPool( 1 );
        scheduledExecutorService.scheduleWithFixedDelay( runnable, initial, delay, timeUnit );
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
    }

    public LatencyTimeDisplayListener addValueListener( ValueListener valueListener)
    {
        this.valueListeners.add( valueListener );
        return this;
    }

    public LatencyTimeDisplayListener()
    {
        this( 0, 5, TimeUnit.SECONDS );
    }

    private static class ValueListenerRunnable
        implements Runnable
    {
        private final Recorder recorder;

        private final List<ValueListener> valueListeners;

        private ValueListenerRunnable(Recorder recorder, List<ValueListener> valueListeners )
        {
            this.recorder = recorder;
            this.valueListeners = valueListeners;
        }

        @Override
        public void run()
        {
            Histogram histogram = recorder.getIntervalHistogram();

            for ( ValueListener valueListener : valueListeners )
            {
                valueListener.onValue( histogram );
            }

        }
    }

    @Override
    public void onResourceNode( Resource.Info info )
    {
        long time = info.getLatencyTime() - info.getRequestTime();
        try
        {
            recorder.recordValue( time );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "skip error recording time {}, {}", time, e.getMessage() );
        }
    }

    @Override
    public void onEnd( LoadGenerator generator )
    {
        if (!scheduledExecutorService.isShutdown())
        {
            scheduledExecutorService.shutdownNow();
        }
    }

    public interface ValueListener
    {
        void onValue( Histogram histogram );
    }

    public static class PrintValueListener
        implements ValueListener
    {
        @Override
        public void onValue(Histogram histogram )
        {
            StringBuilder message = new StringBuilder( ).append( System.lineSeparator() );
            message.append( new CollectorInformations( histogram ) //
                                .toString( true ) ) //
                .append( System.lineSeparator() );
            LOGGER.info( message.toString() );
        }
    }
}
