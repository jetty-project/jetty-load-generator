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

package org.eclipse.jetty.load.generator;

import org.HdrHistogram.AtomicHistogram;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.util.Map;

/**
 *
 */
public class LoadGeneratorResultHandler
    extends Request.Listener.Adapter
    implements Response.CompleteListener, Request.BeginListener
{

    private static final Logger LOGGER = Log.getLogger( LoadGeneratorResultHandler.class );

    /**
     * time of the send method call
     */
    public static final String AFTER_SEND_TIME_HEADER = "X-Jetty-LoadGenerator-After-Send-Time";

    /**
     * time of the start sending datas
     */
    public static final String START_SEND_TIME_HEADER = "X-Jetty-LoadGenerator-Start-Send-Time";

    private final LoadGeneratorResult loadGeneratorResult;

    private final Map<String, Recorder> recorderPerPath;

    private Recorder latencyRecorder;

    public LoadGeneratorResultHandler( LoadGeneratorResult loadGeneratorResult, //
                                       Map<String, Recorder> recorderPerPath, //
                                       Recorder latencyHistogram )
    {
        this.loadGeneratorResult = loadGeneratorResult;
        this.recorderPerPath = recorderPerPath;
        this.latencyRecorder = latencyHistogram;
    }

    @Override
    public void onBegin( Request request )
    {
        // latency since queued
        String sendCallTime = request.getHeaders().get( AFTER_SEND_TIME_HEADER );
        if (StringUtil.isNotBlank( sendCallTime ))
        {
            this.latencyRecorder.recordValue( System.nanoTime() - Long.parseLong( sendCallTime ) );
        }
        request.header( START_SEND_TIME_HEADER, Long.toString( System.nanoTime() ) );
    }



    @Override
    public void onComplete( Result result )
    {
        onComplete( result.getResponse() );
    }

    public void onComplete( Response response )
    {
        long end = System.nanoTime();
        this.loadGeneratorResult.getTotalResponse().incrementAndGet();

        if ( ( response.getStatus() / 100 ) == 2 )
        {
            this.loadGeneratorResult.getTotalSuccess().incrementAndGet();
        }
        else
        {
            this.loadGeneratorResult.getTotalFailure().incrementAndGet();
        }
        String path = response.getRequest().getPath();

        Recorder recorder = this.recorderPerPath.get( path );
        if ( recorder == null )
        {
            LOGGER.warn( "cannot find Recorder for path: {}", path );
        }
        else
        {
            String startTime = response.getRequest().getHeaders().get( START_SEND_TIME_HEADER );
            if ( !StringUtil.isBlank( startTime ) )
            {
                long time = end - Long.parseLong( startTime );

                recorder.recordValue( time );
            }
        }
    }


    public LoadGeneratorResult getLoadGeneratorResult()
    {
        return loadGeneratorResult;
    }
}
