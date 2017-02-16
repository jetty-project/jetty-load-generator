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

package org.mortbay.jetty.load.generator;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.mortbay.jetty.load.generator.latency.LatencyTimeListener;
import org.mortbay.jetty.load.generator.responsetime.ResponseTimeListener;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.util.Collections;
import java.util.List;

/**
 *
 */
public class LoadGeneratorResultHandler
    implements Response.CompleteListener, Response.BeginListener
{

    private static final Logger LOGGER = Log.getLogger( LoadGeneratorResultHandler.class );

    /**
     * time of the start response time calculation
     */
    public static final String START_RESPONSE_TIME_HEADER = "X-Jetty-LoadGenerator-Start-Response-Time";

    public static final String START_LATENCY_TIME_HEADER = "X-Jetty-LoadGenerator-Start-Latency-Time";

    private final List<ResponseTimeListener> responseTimeListeners;

    private final List<LatencyTimeListener> latencyTimeListeners;


    public LoadGeneratorResultHandler( List<ResponseTimeListener> responseTimeListeners,
                                       List<LatencyTimeListener> latencyTimeListeners )
    {
        this.responseTimeListeners = responseTimeListeners == null ? Collections.emptyList() : responseTimeListeners;
        this.latencyTimeListeners = latencyTimeListeners == null ? Collections.emptyList() : latencyTimeListeners;
    }

    @Override
    public void onComplete( Result result )
    {
        onComplete( result.getResponse() );
    }

    public void onComplete( Response response )
    {
        if ( responseTimeListeners.isEmpty() )
        {
            return;
        }
        // TODO olamy: call to listeners in an async way?
        long end = System.nanoTime();

        String startTime = response.getRequest().getHeaders().get( START_RESPONSE_TIME_HEADER );
        if ( !StringUtil.isBlank( startTime ) )
        {
            long time = end - Long.parseLong( startTime );

            ValueListener.Values values = new ResponseTimeListener.Values() //
                .time( time ) //
                .path( response.getRequest().getPath() ) //
                .method( response.getRequest().getMethod() ) //
                .status( response.getStatus() ) //
                .eventTimestamp( System.currentTimeMillis() );

            for ( ResponseTimeListener responseTimeListener : responseTimeListeners )
            {
                responseTimeListener.onResponseTimeValue( values );
            }
        }

        /*

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
        */
    }

    @Override
    public void onBegin( Response response )
    {
        if ( latencyTimeListeners.isEmpty() )
        {
            return;
        }

        long end = System.nanoTime();

        String startTime = response.getRequest().getHeaders().get( START_LATENCY_TIME_HEADER);

        if ( !StringUtil.isBlank( startTime ) )
        {
            long time = end - Long.parseLong( startTime );

            ValueListener.Values values = new ValueListener.Values() //
                .time( time ) //
                .path( response.getRequest().getPath() ) //
                .method( response.getRequest().getMethod() ) //
                .status( response.getStatus() ) //
                .eventTimestamp( System.currentTimeMillis() );

            System.out.println( "onBegin:" + response.hashCode() );

            for ( LatencyTimeListener latencyTimeListener : latencyTimeListeners )
            {
                latencyTimeListener.onLatencyTimeValue( values );
            }
        }
    }

    //@Override
    public void onFailure( Request request, Throwable failure )
    {
        if ( LOGGER.isDebugEnabled() )
        {
            LOGGER.debug( "request failure" + request, failure );
        }
    }

    /*
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
    */


    protected void onContentSize( int size )
    {
        // TODO store this bandwith approx
        LOGGER.debug( "onContentSize: {}", size );
    }

    public List<ResponseTimeListener> getResponseTimeListeners()
    {
        return responseTimeListeners;
    }

    public List<LatencyTimeListener> getLatencyTimeListeners()
    {
        return latencyTimeListeners;
    }

}
