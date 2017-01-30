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

package org.webtide.jetty.load.generator.responsetime;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.webtide.jetty.load.generator.CollectorInformations;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ResponseTimeDisplayListener
    implements ResponseTimeListener
{

    private static final Logger LOGGER = Log.getLogger( ResponseTimeDisplayListener.class );

    private final Map<String, Recorder> recorderPerPath;

    private ScheduledExecutorService scheduledExecutorService;

    private ValueListenerRunnable runnable;

    private final long lowestDiscernibleValue;

    private final long highestTrackableValue;

    private final int numberOfSignificantValueDigits;

    private List<ValueListener> valueListeners = new ArrayList<>();

    public ResponseTimeDisplayListener( long initial, long delay, TimeUnit timeUnit )
    {
        this( RecorderConstants.LOWEST_DISCERNIBLE_VALUE, //
              RecorderConstants.HIGHEST_TRACKABLE_VALUE,  //
              RecorderConstants.NUMBER_OF_SIHNIFICANT_VALUE_DIGITS,  //
              initial, //
              delay,  //
              timeUnit, //
              Arrays.asList( new PrintValueListener() ) );
    }

    public ResponseTimeDisplayListener( long lowestDiscernibleValue, long highestTrackableValue,
                                        int numberOfSignificantValueDigits, long initial, long delay, TimeUnit timeUnit,
                                        List<ValueListener> valueListeners )
    {
        this.valueListeners.addAll( valueListeners );
        this.recorderPerPath = new ConcurrentHashMap<>();
        this.runnable = new ValueListenerRunnable( recorderPerPath, this.valueListeners );
        // FIXME configurable or using a shared one
        scheduledExecutorService = Executors.newScheduledThreadPool( 1 );
        scheduledExecutorService.scheduleWithFixedDelay( runnable, initial, delay, timeUnit );
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
    }

    public ResponseTimeDisplayListener()
    {
        this( 0, 5, TimeUnit.SECONDS );
    }

    private static class ValueListenerRunnable
        implements Runnable
    {
        private final Map<String, Recorder> recorderPerPath;

        private final List<ValueListener> valueListeners;

        private ValueListenerRunnable( Map<String, Recorder> recorderPerPath, List<ValueListener> valueListeners )
        {
            this.recorderPerPath = recorderPerPath;
            this.valueListeners = valueListeners;
        }

        @Override
        public void run()
        {
            for ( Map.Entry<String, Recorder> entry : recorderPerPath.entrySet() )
            {
                String path = entry.getKey();
                Histogram histogram = entry.getValue().getIntervalHistogram();
                for ( ValueListener valueListener : valueListeners )
                {
                    valueListener.onValue( path, histogram );
                }
            }
        }
    }


    @Override
    public void onResponseTimeValue( Values values )
    {
        String path = values.getPath();

        Recorder recorder = recorderPerPath.get( path );
        if ( recorder == null )
        {
            recorder = new Recorder( lowestDiscernibleValue, //
                                     highestTrackableValue, //
                                     numberOfSignificantValueDigits );
            recorderPerPath.put( path, recorder );
        }

        long time = values.getTime();
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
    public void onLoadGeneratorStop()
    {
        scheduledExecutorService.shutdown();
        // last run
        runnable.run();
    }


    interface ValueListener
    {
        void onValue( String path, Histogram histogram );
    }

    public static class PrintValueListener
        implements ValueListener
    {
        @Override
        public void onValue( String path, Histogram histogram )
        {
            StringBuilder message = new StringBuilder( "Path:" ).append( path ).append( System.lineSeparator() );
            message.append( new CollectorInformations( histogram, //
                                                       CollectorInformations.InformationType.REQUEST ) //
                                .toString( true ) ) //
                .append( System.lineSeparator() );
            LOGGER.info( message.toString() );
        }
    }
}
