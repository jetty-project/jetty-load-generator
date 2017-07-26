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

package org.mortbay.jetty.load.generator.starter;

import com.beust.jcommander.JCommander;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.CollectorInformations;
import org.mortbay.jetty.load.generator.listeners.report.GlobalSummaryListener;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public class LoadGeneratorStarter
    extends AbstractLoadGeneratorStarter
{

    private static final Logger LOGGER = Log.getLogger( LoadGeneratorStarter.class );

    public LoadGeneratorStarter( LoadGeneratorStarterArgs runnerArgs )
    {
        super( runnerArgs );
    }

    public static void main( String[] args )
        throws Exception
    {

        LoadGeneratorStarterArgs runnerArgs = new LoadGeneratorStarterArgs();

        try
        {
            JCommander jCommander = new JCommander( runnerArgs, args );
            if ( runnerArgs.isHelp() )
            {
                jCommander.usage();
                return;
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            new JCommander( runnerArgs ).usage();
            return;
        }

        try
        {
            GlobalSummaryListener globalSummaryListener = new GlobalSummaryListener();
            LoadGeneratorStarter runner = new LoadGeneratorStarter( runnerArgs )
            {
                @Override
                protected Resource.Listener[] getResourceListeners()
                {
                    return new Resource.Listener[]{ globalSummaryListener };
                }

                @Override
                protected Request.Listener[] getListeners()
                {
                    return new Request.Listener[]{ globalSummaryListener };
                }
            };

            runner.run();

            if ( runnerArgs.isDisplayStatsAtEnd() )
            {
                runner.displayGlobalSummaryListener( globalSummaryListener );
            }

        }
        catch ( Exception e )
        {
            LOGGER.info( "error happened", e );
            new JCommander( runnerArgs ).usage();
        }
    }


    public void displayGlobalSummaryListener( GlobalSummaryListener globalSummaryListener )
    {

        CollectorInformations latencyTimeSummary =
            new CollectorInformations( globalSummaryListener.getLatencyTimeHistogram() //
                                           .getIntervalHistogram() );

        long totalRequestCommitted = globalSummaryListener.getRequestCommitTotal();
        long start = latencyTimeSummary.getStartTimeStamp();
        long end = latencyTimeSummary.getEndTimeStamp();

        LOGGER.info( "" );
        LOGGER.info( "" );
        LOGGER.info( "----------------------------------------------------" );
        LOGGER.info( "--------    Latency Time Summary     ---------------" );
        LOGGER.info( "----------------------------------------------------" );
        LOGGER.info( "total count:" + latencyTimeSummary.getTotalCount() );
        LOGGER.info( "maxLatency:" //
                         + fromNanostoMillis( latencyTimeSummary.getMaxValue() ) );
        LOGGER.info( "minLatency:" //
                         + fromNanostoMillis( latencyTimeSummary.getMinValue() ) );
        LOGGER.info( "aveLatency:" //
                         + fromNanostoMillis( Math.round( latencyTimeSummary.getMean() ) ) );
        LOGGER.info( "50Latency:" //
                         + fromNanostoMillis( latencyTimeSummary.getValue50() ) );
        LOGGER.info( "90Latency:" //
                         + fromNanostoMillis( latencyTimeSummary.getValue90() ) );
        LOGGER.info( "stdDeviation:" //
                         + fromNanostoMillis( Math.round( latencyTimeSummary.getStdDeviation() ) ) );
        LOGGER.info( "----------------------------------------------------" );
        LOGGER.info( "-----------     Estimated QPS     ------------------" );
        LOGGER.info( "----------------------------------------------------" );
        long timeInSeconds = TimeUnit.SECONDS.convert( end - start, TimeUnit.MILLISECONDS );
        long qps = totalRequestCommitted / timeInSeconds;
        LOGGER.info( "estimated QPS : " + qps );
        LOGGER.info( "----------------------------------------------------" );
        LOGGER.info( "response 1xx family: " + globalSummaryListener.getResponses1xx().longValue() );
        LOGGER.info( "response 2xx family: " + globalSummaryListener.getResponses2xx().longValue() );
        LOGGER.info( "response 3xx family: " + globalSummaryListener.getResponses3xx().longValue() );
        LOGGER.info( "response 4xx family: " + globalSummaryListener.getResponses4xx().longValue() );
        LOGGER.info( "response 5xx family: " + globalSummaryListener.getResponses5xx().longValue() );
        LOGGER.info( "" );

    }

    static long fromNanostoMillis( long nanosValue )
    {
        return TimeUnit.MILLISECONDS.convert( nanosValue, TimeUnit.NANOSECONDS );
    }

}
