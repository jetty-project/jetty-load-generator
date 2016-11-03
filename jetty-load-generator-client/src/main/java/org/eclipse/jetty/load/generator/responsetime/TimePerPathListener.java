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

package org.eclipse.jetty.load.generator.responsetime;

import org.HdrHistogram.Recorder;
import org.eclipse.jetty.load.generator.CollectorInformations;
import org.eclipse.jetty.load.generator.latency.LatencyTimeListener;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Use {@link Recorder} to tracker response/latency time per path</p>
 * <p>
 * Print out general statistics when stopping.
 * To prevent that and only get the values simply use the constructor with <code>false</code>
 * </p>
 */
public class TimePerPathListener
    implements ResponseTimeListener, LatencyTimeListener, Serializable
{

    private static final Logger LOGGER = Log.getLogger( TimePerPathListener.class );

    private Map<String, Recorder> responseTimePerPath = new ConcurrentHashMap<>();

    private Map<String, Recorder> latencyTimePerPath = new ConcurrentHashMap<>();

    private boolean printOnEnd = true;

    private long lowestDiscernibleValue = RecorderConstants.LOWEST_DISCERNIBLE_VALUE;

    private long highestTrackableValue = RecorderConstants.HIGHEST_TRACKABLE_VALUE;

    private int numberOfSignificantValueDigits = RecorderConstants.NUMBER_OF_SIHNIFICANT_VALUE_DIGITS;


    public TimePerPathListener( boolean printOnEnd, long lowestDiscernibleValue, long highestTrackableValue,
                                int numberOfSignificantValueDigits )
    {
        this( printOnEnd );
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
    }

    public TimePerPathListener( boolean printOnEnd )
    {
        this.printOnEnd = printOnEnd;
    }


    public TimePerPathListener()
    {
        this( true );
    }

    @Override
    public void onResponseTimeValue( Values values )
    {
        String path = values.getPath();
        long responseTime = values.getTime();
        Recorder recorder = responseTimePerPath.get( path );
        if ( recorder == null )
        {
            recorder = new Recorder( lowestDiscernibleValue, //
                                     highestTrackableValue, //
                                     numberOfSignificantValueDigits );
            responseTimePerPath.put( path, recorder );
        }
        try
        {
            recorder.recordValue( responseTime );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "skip error recording time {}, {}", responseTime, e.getMessage() );
        }
    }

    @Override
    public void onLatencyTimeValue( Values values )
    {
        String path = values.getPath();
        long time = values.getTime();
        Recorder recorder = latencyTimePerPath.get( path );
        if ( recorder == null )
        {
            recorder = new Recorder( lowestDiscernibleValue, //
                                     highestTrackableValue, //
                                     numberOfSignificantValueDigits );
            latencyTimePerPath.put( path, recorder );
        }
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
        if ( printOnEnd )
        {
            StringBuilder responseTimeMessage =  //
                new StringBuilder( System.lineSeparator() ) //
                    .append( "--------------------------------------" ).append( System.lineSeparator() ) //
                    .append( "   Response Time Summary              " ).append( System.lineSeparator() ) //
                    .append( "--------------------------------------" ).append( System.lineSeparator() ); //

            for ( Map.Entry<String, Recorder> entry : responseTimePerPath.entrySet() )
            {
                responseTimeMessage.append( "Path:" ).append( entry.getKey() ).append( System.lineSeparator() );
                responseTimeMessage.append( new CollectorInformations( entry.getValue().getIntervalHistogram(), //
                                                                       CollectorInformations.InformationType.REQUEST ) //
                                                .toString( true ) ) //
                    .append( System.lineSeparator() );

            }

            responseTimeMessage.append( System.lineSeparator() );

            StringBuilder latencyTimeMessage =  //
                new StringBuilder( System.lineSeparator() ) //
                    .append( "--------------------------------------" ).append( System.lineSeparator() ) //
                    .append( "   Latency Time Summary               " ).append( System.lineSeparator() ) //
                    .append( "--------------------------------------" ).append( System.lineSeparator() ); //

            for ( Map.Entry<String, Recorder> entry : responseTimePerPath.entrySet() )
            {
                latencyTimeMessage.append( "Path:" ).append( entry.getKey() ).append( System.lineSeparator() );
                latencyTimeMessage.append( new CollectorInformations( entry.getValue().getIntervalHistogram(), //
                                                                      CollectorInformations.InformationType.REQUEST ) //
                                               .toString( true ) ) //
                    .append( System.lineSeparator() );

            }

            latencyTimeMessage.append( System.lineSeparator() );

            System.out.println( responseTimeMessage.append( latencyTimeMessage ) );
        }
    }

    public Map<String, Recorder> getResponseTimePerPath()
    {
        return responseTimePerPath;
    }


    public Map<String, Recorder> getLatencyTimePerPath()
    {
        return latencyTimePerPath;
    }
}
