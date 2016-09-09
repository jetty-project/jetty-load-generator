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

import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.load.generator.latency.LatencyListener;
import org.eclipse.jetty.load.generator.response.ResponseTimeListener;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public class LoadGeneratorResultHandler
    extends Request.Listener.Adapter
    implements Response.CompleteListener, Request.BeginListener, Response.AsyncContentListener, Response.BeginListener
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
                latencyListener.onLatencyValue( new LatencyListener.Values() //
                                                    .latencyValue( latencyValue ) //
                                                    .path( request.getPath() ) //
                                                    .method( request.getMethod() )
                );
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
        int size = 0;
        // we need to consume content!
        if (response instanceof ContentResponse ) {
            try
            {
                byte[] thecontent = ( (ContentResponse) response ).getContent();
                if ( LOGGER.isDebugEnabled() )
                {
                    LOGGER.debug( "response content: {}", thecontent );
                }
                size = thecontent.length;
                onContentSize( size );
            }
            catch ( Throwable e )
            {
                LOGGER.warn( "skip fail to get content size", e );
            }
        }

        long end = System.nanoTime();

        String path = response.getRequest().getPath();

        String startTime = response.getRequest().getHeaders().get( START_SEND_TIME_HEADER );
        if ( !StringUtil.isBlank( startTime ) )
        {
            long time = end - Long.parseLong( startTime );
            for (ResponseTimeListener responseTimeListener : responseTimeListeners) {
                responseTimeListener.onResponse(
                    new ResponseTimeListener.Values() //
                        .path( path ) //
                        .responseTime( time ) //
                        .method( response.getRequest().getMethod() ) //
                        .status( response.getStatus() ) //
                        .size( size )
                );
            }
        }
    }

    @Override
    public void onFailure( Request request, Throwable failure )
    {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug( "request failure" + request, failure );
        }
    }



    @Override
    public void onContent( Response response, ByteBuffer buffer, Callback callback )
    {
        try
        {
            byte[] bytes;
            if ( buffer.hasArray() )
            {
                bytes = buffer.array();
            }
            else
            {
                bytes = new byte[buffer.remaining()];
                buffer.get( bytes );
            }

            int size = bytes.length;
            LOGGER.debug( "size: {}", size );
            onContentSize( size );
        }
        catch ( Throwable e )
        {
            LOGGER.warn( "skip fail to get content size", e );
        }
        callback.succeeded();
    }


    protected void onContentSize(int size) {
        // TODO store this bandwith approx
    }

    @Override
    public void onBegin( Response response )
    {
        //here add header with nano timestamp
        response.getHeaders().add( "foo", "beer" );
    }
}
