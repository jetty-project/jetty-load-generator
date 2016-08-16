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

    public static final String START_TIME_HEADER = "X-Jetty-LoadGenerator-Start";

    private final LoadGeneratorResult loadGeneratorResult;

    private final Map<String, AtomicHistogram> histogramPerPath;

    public LoadGeneratorResultHandler( LoadGeneratorResult loadGeneratorResult,
                                       Map<String, AtomicHistogram> histogramPerPath )
    {
        this.loadGeneratorResult = loadGeneratorResult;
        this.histogramPerPath = histogramPerPath;
    }

    @Override
    public void onBegin( Request request )
    {
        request.getHeaders().add( START_TIME_HEADER, Long.toString( System.nanoTime() ) );
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

        AtomicHistogram atomicHistogram = this.histogramPerPath.get( path );
        if ( atomicHistogram == null )
        {
            LOGGER.warn( "cannot find AtomicHistogram for path: {}", path );
        }
        else
        {
            String startTime = response.getRequest().getHeaders().get( START_TIME_HEADER );
            if ( !StringUtil.isBlank( startTime ) )
            {
                long time = end - Long.parseLong( startTime );

                atomicHistogram.recordValue( time );
            }
        }
    }


    public LoadGeneratorResult getLoadGeneratorResult()
    {
        return loadGeneratorResult;
    }
}
