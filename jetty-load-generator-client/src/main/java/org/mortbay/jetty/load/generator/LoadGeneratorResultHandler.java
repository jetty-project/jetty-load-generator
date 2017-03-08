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

package org.mortbay.jetty.load.generator;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.ExecutionStrategy;
import org.mortbay.jetty.load.generator.latency.LatencyTimeListener;
import org.mortbay.jetty.load.generator.responsetime.ResponseTimeListener;

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

    private /*final */List<ResponseTimeListener> responseTimeListeners;

    private /*final */List<LatencyTimeListener> latencyTimeListeners;

    private Executor executor;

    private final LoadGenerator loadGenerator;

    public LoadGeneratorResultHandler( LoadGenerator loadGenerator, //
                                       Executor executor)
    {
//        this.responseTimeListeners = loadGenerator.getResponseTimeListeners() == null ? Collections.emptyList() //
//            : new CopyOnWriteArrayList<>( loadGenerator.getResponseTimeListeners() );
//        this.latencyTimeListeners = loadGenerator.getLatencyTimeListeners() == null ? Collections.emptyList() //
//            : new CopyOnWriteArrayList<>( loadGenerator.getLatencyTimeListeners() );
        this.executor = executor;
        if (executor == null)
        {
            this.executor = Executors.newCachedThreadPool();
        }
        this.loadGenerator = loadGenerator;
    }

    private static class LatencyValueProducer implements ExecutionStrategy.Producer
    {
        private ValueListener.Values values;

        public LatencyValueProducer( ValueListener.Values values )
        {
            this.values = values;
        }

        @Override
        public Runnable produce()
        {
            return null;
        }
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

        String startTime = response.getRequest().getHeaders().get( START_RESPONSE_TIME_HEADER );
        if ( !StringUtil.isBlank( startTime ) && !loadGenerator.getStop().get() )
        {
            // we calculate now time outside of the Runnable as we don't when it will happen
            long end = System.nanoTime();
            this.executor.execute( () ->
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

                                   } );

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

        String startTime = response.getRequest().getHeaders().get( START_LATENCY_TIME_HEADER );
        if ( !StringUtil.isBlank( startTime ) && !loadGenerator.getStop().get() )
        {
            // we calculate now time outside of the Runnable as we don't when it will happen
            long end = System.nanoTime();
            this.executor.execute( () ->
                                   {


                                       long time = end - Long.parseLong( startTime );

                                       ValueListener.Values values = new ValueListener.Values() //
                                           .time( time ) //
                                           .path( response.getRequest().getPath() ) //
                                           .method( response.getRequest().getMethod() ) //
                                           .status( response.getStatus() ) //
                                           .eventTimestamp( System.currentTimeMillis() );

                                       if ( LOGGER.isDebugEnabled() )
                                       {
                                           LOGGER.debug( "onBegin:" + response.hashCode() );
                                       }
                                       for ( LatencyTimeListener latencyTimeListener : latencyTimeListeners )
                                       {
                                           latencyTimeListener.onLatencyTimeValue( values );
                                       }

                                   } );
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
