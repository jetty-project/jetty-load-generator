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

package org.mortbay.jetty.load.generator.listeners;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.LoadGenerator;

import java.net.InetAddress;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * This listener will record time between start of send and onCommit event
 * Then display the number of request per second
 */
public class QpsListenerDisplay
    extends Request.Listener.Adapter
    implements Request.Listener, LoadGenerator.EndListener
{

    private static final Logger LOGGER = Log.getLogger( QpsListenerDisplay.class );

    private ScheduledExecutorService scheduledExecutorService;

    private volatile Recorder recorder;

    private String hostname;

    public QpsListenerDisplay( long initial, long delay, TimeUnit timeUnit )
    {
        this( HistogramConstants.LOWEST_DISCERNIBLE_VALUE, //
              HistogramConstants.HIGHEST_TRACKABLE_VALUE,  //
              HistogramConstants.NUMBER_OF_SIGNIFICANT_VALUE_DIGITS,  //
              initial, //
              delay,  //
              timeUnit );
    }

    public QpsListenerDisplay( long lowestDiscernibleValue, long highestTrackableValue,
                               int numberOfSignificantValueDigits, long initial, long delay, TimeUnit timeUnit )
    {
        this.recorder = new Recorder( lowestDiscernibleValue, //
                                      highestTrackableValue, //
                                      numberOfSignificantValueDigits );

        try
        {
            hostname = InetAddress.getLocalHost().getHostName();
        }
        catch ( Exception e )
        {
            LOGGER.info( "ignore cannot get hostname:" + e.getMessage() );
        }
        scheduledExecutorService = Executors.newScheduledThreadPool( 1 );
        scheduledExecutorService.scheduleWithFixedDelay( new ValueDisplayRunnable( recorder, hostname ), //
                                                         initial, delay, timeUnit );
    }


    @Override
    public void onCommit( Request request )
    {
        // we only care about total count and start/end so just record 1 :-)
        this.recorder.recordValue( 1 );
    }

    @Override
    public void onFailure( Request request, Throwable failure )
    {
        LOGGER.warn( "fail to send request", failure );
    }

    private static class ValueDisplayRunnable
        implements Runnable
    {
        private volatile Recorder recorder;

        private String hostname;

        public ValueDisplayRunnable( Recorder recorder, String hostname )
        {
            this.hostname = hostname;
            this.recorder = recorder;
        }

        @Override
        public void run()
        {
            Histogram histogram = this.recorder.getIntervalHistogram();
            long totalRequestCommitted = histogram.getTotalCount();
            long start = histogram.getStartTimeStamp();
            long end = histogram.getEndTimeStamp();

            CollectorInformations collectorInformations = new CollectorInformations( histogram );

            LOGGER.info( "----------------------------------------" );
            LOGGER.info( "--------    QPS estimation    ----------" );
            LOGGER.info( "----------------------------------------" );
            long timeInSeconds = TimeUnit.SECONDS.convert( end - start, TimeUnit.MILLISECONDS );
            long qps = totalRequestCommitted / timeInSeconds;
            LOGGER.info( "host '" + hostname + "' estimated QPS : " + qps );
            LOGGER.info( "----------------------------------------" );
            LOGGER.info( "--------  Request commit time  ----------" );
            LOGGER.info( "-----------------------------------------" );
            LOGGER.info( collectorInformations.toString( true ) );

        }
    }

    @Override
    public void onEnd( LoadGenerator generator )
    {
        this.scheduledExecutorService.shutdownNow();
    }
}
