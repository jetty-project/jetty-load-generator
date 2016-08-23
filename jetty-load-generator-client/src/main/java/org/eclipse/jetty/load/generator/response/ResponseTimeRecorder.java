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

package org.eclipse.jetty.load.generator.response;

import org.HdrHistogram.Recorder;
import org.eclipse.jetty.load.generator.CollectorInformations;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ResponseTimeRecorder
    implements ResponseTimeListener
{

    private static final Logger LOGGER = Log.getLogger( ResponseTimeRecorder.class );

    private final Map<String, Recorder> recorderPerPath;

    private final List<ResponseTimeValueListener> responseTimeValueListeners;

    private ScheduledExecutorService scheduledExecutorService;

    private ValueListenerRunnable runnable;

    public ResponseTimeRecorder( Map<String, Recorder> recorderPerPath,
                                 List<ResponseTimeValueListener> responseTimeValueListeners )
    {
        this.recorderPerPath = recorderPerPath;
        this.responseTimeValueListeners = responseTimeValueListeners;
        this.runnable = new ValueListenerRunnable( responseTimeValueListeners, recorderPerPath );
        scheduledExecutorService = Executors.newScheduledThreadPool( 1 );
        // FIXME configurable!!!
        scheduledExecutorService.scheduleWithFixedDelay( runnable, 0, 1, TimeUnit.SECONDS );
    }

    private static class ValueListenerRunnable
        implements Runnable
    {
        private final List<ResponseTimeValueListener> responseTimeValueListeners;

        private final Map<String, Recorder> recorderPerPath;

        private ValueListenerRunnable( List<ResponseTimeValueListener> responseTimeValueListeners,
                                       Map<String, Recorder> recorderPerPath )
        {
            this.responseTimeValueListeners = responseTimeValueListeners == null ? //
                Collections.emptyList() : responseTimeValueListeners;
            this.recorderPerPath = recorderPerPath;
        }

        @Override
        public void run()
        {
            for ( Map.Entry<String, Recorder> entry : recorderPerPath.entrySet())
            {
                for ( ResponseTimeValueListener responseTimeValueListener : responseTimeValueListeners )
                {
                    responseTimeValueListener.onValue( entry.getKey(), //
                        new CollectorInformations( entry.getValue().getIntervalHistogram(), //
                                                   CollectorInformations.InformationType.REQUEST ) );
                }
            }
        }
    }


    @Override
    public void onResponseTimeValue( String path, long responseTime )
    {
        Recorder recorder = recorderPerPath.get( path );
        if ( recorder == null )
        {
            LOGGER.warn( "cannot find Histogram to record response time for path: {}", path );
        }
        else
        {
            recorder.recordValue( responseTime );
        }
    }

    @Override
    public void onLoadGeneratorStop()
    {
        scheduledExecutorService.shutdown();
        // last run
        runnable.run();
    }
}
