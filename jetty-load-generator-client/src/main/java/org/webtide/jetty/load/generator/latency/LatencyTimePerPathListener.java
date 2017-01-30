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

package org.webtide.jetty.load.generator.latency;

import org.HdrHistogram.Recorder;
import org.webtide.jetty.load.generator.CollectorInformations;
import org.webtide.jetty.load.generator.responsetime.RecorderConstants;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Use {@link Recorder} to tracker latency time per path</p>
 * <p>
 *     Print out general statistics when stopping.
 *     To prevent that and only get the values simply use the constructor with <code>false</code>
 * </p>
 */
public class LatencyTimePerPathListener
    implements LatencyTimeListener, Serializable
{

    private static final Logger LOGGER = Log.getLogger( LatencyTimePerPathListener.class );

    private Map<String, Recorder> recorderPerPath;

    private boolean printOnEnd = true;

    private long lowestDiscernibleValue = RecorderConstants.LOWEST_DISCERNIBLE_VALUE;
    private long highestTrackableValue = RecorderConstants.HIGHEST_TRACKABLE_VALUE;
    private int numberOfSignificantValueDigits = RecorderConstants.NUMBER_OF_SIHNIFICANT_VALUE_DIGITS;


    public LatencyTimePerPathListener( Map<String, Recorder> recorderPerPath, boolean printOnEnd,
                                       long lowestDiscernibleValue, long highestTrackableValue,
                                       int numberOfSignificantValueDigits )
    {
        this.recorderPerPath = recorderPerPath;
        this.printOnEnd = printOnEnd;
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
    }

    public LatencyTimePerPathListener( boolean printOnEnd )
    {
        this.printOnEnd = printOnEnd;
        this.recorderPerPath = new ConcurrentHashMap<>();
    }



    public LatencyTimePerPathListener()
    {
        this( true );
    }

    @Override
    public void onLatencyTimeValue( Values values )
    {
        String path = values.getPath();
        long time = values.getTime();
        Recorder recorder = recorderPerPath.get( path );
        if ( recorder == null )
        {
            recorder = new Recorder( lowestDiscernibleValue, //
                                     highestTrackableValue, //
                                     numberOfSignificantValueDigits );
            recorderPerPath.put( path, recorder );
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
            StringBuilder message =  //
                new StringBuilder( System.lineSeparator() ) //
                    .append( "--------------------------------------" ).append( System.lineSeparator() ) //
                    .append( "   Response Time Summary    " ).append( System.lineSeparator() ) //
                    .append( "--------------------------------------" ).append( System.lineSeparator() ); //

            for ( Map.Entry<String, Recorder> entry : recorderPerPath.entrySet() )
            {
                message.append( "Path:" ).append( entry.getKey() ).append( System.lineSeparator() );
                message.append( new CollectorInformations( entry.getValue().getIntervalHistogram(), //
                                                           CollectorInformations.InformationType.REQUEST ) //
                                    .toString( true ) ) //
                    .append( System.lineSeparator() );

            }
            System.out.println( message.toString() );
        }
    }

    public Map<String, Recorder> getRecorderPerPath()
    {
        return recorderPerPath;
    }
}
