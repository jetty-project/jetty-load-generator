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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * <p>Use {@link Recorder} to tracker response time per path</p>
 * <p>
 *     Print out general statistics when stopping.
 *     To prevent that and only get the values simply use the constructor with <code>false</code>
 * </p>
 */
public class ResponseTimePerPathListener
    implements ResponseTimeListener
{

    private Map<String, Recorder> recorderPerPath;

    private boolean printOnEnd = true;

    private long lowestDiscernibleValue = TimeUnit.MICROSECONDS.toNanos( 1 );
    private long highestTrackableValue = TimeUnit.MINUTES.toNanos( 1 );
    private int numberOfSignificantValueDigits = 3;


    public ResponseTimePerPathListener( Map<String, Recorder> recorderPerPath, boolean printOnEnd,
                                        long lowestDiscernibleValue, long highestTrackableValue,
                                        int numberOfSignificantValueDigits )
    {
        this.recorderPerPath = recorderPerPath;
        this.printOnEnd = printOnEnd;
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
    }

    public ResponseTimePerPathListener( boolean printOnEnd )
    {
        this.printOnEnd = printOnEnd;
        this.recorderPerPath = new ConcurrentHashMap<>();
    }



    public ResponseTimePerPathListener()
    {
        this( true );
    }

    @Override
    public void onResponseTimeValue( Values values )
    {
        String path = values.getPath();
        long responseTime = values.getTime();
        Recorder recorder = recorderPerPath.get( path );
        if ( recorder == null )
        {
            recorder = new Recorder( TimeUnit.MICROSECONDS.toNanos( 1 ), //
                                     TimeUnit.MINUTES.toNanos( 1 ), //
                                     3 );
            recorderPerPath.put( path, recorder );
        }
        recorder.recordValue( responseTime );
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
