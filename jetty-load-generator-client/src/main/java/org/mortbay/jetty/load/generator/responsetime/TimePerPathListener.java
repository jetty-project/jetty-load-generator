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

package org.mortbay.jetty.load.generator.responsetime;

import org.HdrHistogram.Recorder;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.CollectorInformations;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.latency.LatencyTimeListener;
import org.mortbay.jetty.load.generator.profile.Resource;

import java.io.Serializable;
import java.util.List;
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

    private int numberOfSignificantValueDigits = RecorderConstants.NUMBER_OF_SIGNIFICANT_VALUE_DIGITS;

    private boolean nanoDisplay = true;

    public TimePerPathListener( boolean printOnEnd, long lowestDiscernibleValue, long highestTrackableValue,
                                int numberOfSignificantValueDigits )
    {
        this( printOnEnd );
        this.lowestDiscernibleValue = lowestDiscernibleValue;
        this.highestTrackableValue = highestTrackableValue;
        this.numberOfSignificantValueDigits = numberOfSignificantValueDigits;
    }


    public TimePerPathListener( boolean printOnEnd, boolean nanoDisplay )
    {
        this.printOnEnd = printOnEnd;
        this.nanoDisplay = nanoDisplay;
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
    public void onLoadGeneratorStart( LoadGenerator loadGenerator )
    {
        // we initialize Maps to avoid concurrent issues
        responseTimePerPath = new ConcurrentHashMap<>();
        initializeMap( responseTimePerPath, loadGenerator.getResource().getResources() );
        latencyTimePerPath = new ConcurrentHashMap<>();
        initializeMap( latencyTimePerPath, loadGenerator.getResource().getResources() );
    }

    private void initializeMap( Map<String, Recorder> recorderMap, List<Resource> resources )
    {
        for ( Resource resource : resources )
        {
            Recorder recorder = recorderMap.get( resource.getPath() );
            if ( recorder == null )
            {
                recorder = new Recorder( lowestDiscernibleValue, //
                                         highestTrackableValue, //
                                         numberOfSignificantValueDigits );
                recorderMap.put( resource.getPath(), recorder );
            }
            initializeMap( recorderMap, resource.getResources() );
        }
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
        if ( LOGGER.isDebugEnabled() )
        {
            LOGGER.debug( "onLatencyTimeValue:" + values.toString() );
        }
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
            StringBuilder reportMessage = new StringBuilder();
            if ( !latencyTimePerPath.isEmpty() )
            {
                StringBuilder latencyTimeMessage = new StringBuilder( "--------------------------------------" ) //
                    .append( System.lineSeparator() ) //
                    .append( "   Latency Time Summary               " ).append( System.lineSeparator() ) //
                    .append( "--------------------------------------" ).append( System.lineSeparator() ); //

                for ( Map.Entry<String, Recorder> entry : latencyTimePerPath.entrySet() )
                {
                    latencyTimeMessage.append( "Path:" ).append( entry.getKey() ).append( System.lineSeparator() );
                    CollectorInformations collectorInformations =
                        new CollectorInformations( entry.getValue().getIntervalHistogram(), //
                                                   CollectorInformations.InformationType.REQUEST );
                    latencyTimeMessage.append( nanoDisplay
                                                   ? collectorInformations.toStringInNanos( true )
                                                   : collectorInformations.toString( true ) ) //
                        .append( System.lineSeparator() );

                }

                latencyTimeMessage.append( System.lineSeparator() );

                reportMessage.append( latencyTimeMessage );
            }

            if ( !responseTimePerPath.isEmpty() )
            {

                StringBuilder responseTimeMessage =  //
                    new StringBuilder( System.lineSeparator() ) //
                        .append( "--------------------------------------" ).append( System.lineSeparator() ) //
                        .append( "   Response Time Summary              " ).append( System.lineSeparator() ) //
                        .append( "--------------------------------------" ).append( System.lineSeparator() ); //

                for ( Map.Entry<String, Recorder> entry : responseTimePerPath.entrySet() )
                {
                    responseTimeMessage.append( "Path:" ).append( entry.getKey() ).append( System.lineSeparator() );
                    CollectorInformations collectorInformations =
                        new CollectorInformations( entry.getValue().getIntervalHistogram(), //
                                                   CollectorInformations.InformationType.REQUEST );

                    responseTimeMessage.append( nanoDisplay
                                                    ? collectorInformations.toStringInNanos( true )
                                                    : collectorInformations.toString( true ) ) //
                        .append( System.lineSeparator() );

                }

                responseTimeMessage.append( System.lineSeparator() );
                reportMessage.append( responseTimeMessage );
            }
            System.out.println( reportMessage );
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
