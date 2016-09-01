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

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.load.generator.latency.LatencyListener;
import org.eclipse.jetty.load.generator.response.ResponseTimeListener;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.util.Collections;
import java.util.List;

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

    private List<LatencyListener> latencyListeners;

    private List<ResponseTimeListener> responseTimeListeners;

    public LoadGeneratorResultHandler( List<ResponseTimeListener> responseTimeListeners, //
                                       List<LatencyListener> latencyListeners )
    {
        this.responseTimeListeners = responseTimeListeners == null ? Collections.emptyList() : responseTimeListeners;
        this.latencyListeners = latencyListeners == null ? Collections.emptyList() : latencyListeners;
    }

    @Override
    public void onBegin( Request request )
    {
        // latency since queued
        String sendCallTime = request.getHeaders().get( AFTER_SEND_TIME_HEADER );
        if ( sendCallTime != null )
        {
            long latencyValue = System.nanoTime() - Long.parseLong( sendCallTime );
            for ( LatencyListener latencyListener : latencyListeners )
            {
                latencyListener.onLatencyValue( latencyValue );
            }
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

        String path = response.getRequest().getPath();

        String startTime = response.getRequest().getHeaders().get( START_SEND_TIME_HEADER );
        if ( !StringUtil.isBlank( startTime ) )
        {
            long time = end - Long.parseLong( startTime );
            for (ResponseTimeListener responseTimeListener : responseTimeListeners) {
                responseTimeListener.onResponse( path, time);
            }
        }
    }
}
