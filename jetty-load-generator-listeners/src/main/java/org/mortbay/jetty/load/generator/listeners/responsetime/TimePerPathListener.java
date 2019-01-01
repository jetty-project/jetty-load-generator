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

package org.mortbay.jetty.load.generator.listeners.responsetime;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.HdrHistogram.AtomicHistogram;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.CollectorInformations;
import org.mortbay.jetty.load.generator.listeners.HistogramConstants;

/**
 * <p>Use {@link AtomicHistogram} to tracker response/latency time per path</p>
 * <p>
 * Print out general statistics when stopping.
 * To prevent that and only get the values simply use the constructor with <code>false</code>
 * </p>
 */
public class TimePerPathListener
    implements Resource.NodeListener, LoadGenerator.EndListener, LoadGenerator.BeginListener, Serializable
{

    private static final Logger LOGGER = Log.getLogger( TimePerPathListener.class );

    private Map<String, AtomicHistogram> responseTimePerPath = new ConcurrentHashMap<>();

    private Map<String, AtomicHistogram> latencyTimePerPath = new ConcurrentHashMap<>();

    private boolean printOnEnd = true;

    private long lowestDiscernibleValue = HistogramConstants.LOWEST_DISCERNIBLE_VALUE;

    private long highestTrackableValue = HistogramConstants.HIGHEST_TRACKABLE_VALUE;

    private int numberOfSignificantValueDigits = HistogramConstants.NUMBER_OF_SIGNIFICANT_VALUE_DIGITS;

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
    public void onBegin( LoadGenerator loadGenerator )
    {
        // we initialize Maps to avoid concurrent issues
        responseTimePerPath = new ConcurrentHashMap<>();
        initializeMap( responseTimePerPath, loadGenerator.getConfig().getResource().getResources() );
        latencyTimePerPath = new ConcurrentHashMap<>();
        initializeMap( latencyTimePerPath, loadGenerator.getConfig().getResource().getResources() );
    }

    private void initializeMap( Map<String, AtomicHistogram> histogramMap, List<Resource> resources )
    {
        for ( Resource resource : resources )
        {
            AtomicHistogram atomicHistogram = histogramMap.get( resource.getPath() );
            if ( atomicHistogram == null )
            {
                atomicHistogram = new AtomicHistogram( lowestDiscernibleValue, //
                                         highestTrackableValue, //
                                         numberOfSignificantValueDigits );
                histogramMap.put( resource.getPath(), atomicHistogram );
            }
            initializeMap( histogramMap, resource.getResources() );
        }
    }

    @Override
    public void onResourceNode( Resource.Info info )
    {
        String path = info.getResource().getPath();
        long responseTime = info.getResponseTime() - info.getRequestTime();
        AtomicHistogram atomicHistogram = responseTimePerPath.get( path );
        if ( atomicHistogram == null )
        {
            atomicHistogram = new AtomicHistogram( lowestDiscernibleValue, //
                                     highestTrackableValue, //
                                     numberOfSignificantValueDigits );
            responseTimePerPath.put( path, atomicHistogram );
        }
        try
        {
            atomicHistogram.recordValue( responseTime );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "skip error recording time {}, {}", responseTime, e.getMessage() );
        }

        long time = info.getLatencyTime() - info.getRequestTime();
        atomicHistogram = latencyTimePerPath.get( path );
        if ( atomicHistogram == null )
        {
            atomicHistogram = new AtomicHistogram( lowestDiscernibleValue, //
                                     highestTrackableValue, //
                                     numberOfSignificantValueDigits );
            latencyTimePerPath.put( path, atomicHistogram );
        }
        try
        {
            atomicHistogram.recordValue( time );
        }
        catch ( ArrayIndexOutOfBoundsException e )
        {
            LOGGER.warn( "skip error recording time {}, {}", time, e.getMessage() );
        }
    }

    @Override
    public void onEnd( LoadGenerator generator )
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

                for ( Map.Entry<String, AtomicHistogram> entry : latencyTimePerPath.entrySet() )
                {
                    latencyTimeMessage.append( "Path:" ).append( entry.getKey() ).append( System.lineSeparator() );
                    CollectorInformations collectorInformations =
                        new CollectorInformations( entry.getValue() );
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

                for ( Map.Entry<String, AtomicHistogram> entry : responseTimePerPath.entrySet() )
                {
                    responseTimeMessage.append( "Path:" ).append( entry.getKey() ).append( System.lineSeparator() );
                    CollectorInformations collectorInformations =
                        new CollectorInformations( entry.getValue() );

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

    public Map<String, AtomicHistogram> getResponseTimePerPath()
    {
        return responseTimePerPath;
    }


    public Map<String, AtomicHistogram> getLatencyTimePerPath()
    {
        return latencyTimePerPath;
    }
}
