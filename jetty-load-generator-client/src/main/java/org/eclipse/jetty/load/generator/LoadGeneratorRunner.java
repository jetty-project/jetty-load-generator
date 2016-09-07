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

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.load.generator.profile.Resource;
import org.eclipse.jetty.load.generator.profile.Step;
import org.eclipse.jetty.toolchain.perf.PlatformTimer;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.net.HttpCookie;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class LoadGeneratorRunner
    implements Runnable
{

    private static final Logger LOGGER = Log.getLogger( LoadGeneratorRunner.class );

    private final HttpClient httpClient;

    private final LoadGenerator loadGenerator;

    private final LoadGeneratorResultHandler loadGeneratorResultHandler;

    private static final PlatformTimer PLATFORM_TIMER = PlatformTimer.detect();

    // maintain a session/cookie per httpClient
    // FIXME olamy: not sure we really need that??
    private final HttpCookie httpCookie = new HttpCookie( "XXX-Jetty-LoadGenerator", //
                                                          Long.toString( System.nanoTime() ) );

    public LoadGeneratorRunner( HttpClient httpClient, LoadGenerator loadGenerator,
                                LoadGeneratorResultHandler loadGeneratorResultHandler )
    {
        this.httpClient = httpClient;
        this.loadGenerator = loadGenerator;
        this.loadGeneratorResultHandler = loadGeneratorResultHandler;
    }

    @Override
    public void run()
    {

        LOGGER.debug( "loadGenerator#run" );
        // FIXME populate loadGeneratorResult with statistics values
        try
        {
            while ( true )
            {
                if ( this.loadGenerator.getStop().get() || httpClient.isStopped() )
                {
                    break;
                }

                List<Step> steps = loadGenerator.getProfile().getSteps();

                for ( Step step : steps )
                {
                    if (!step.isWait()) {
                        for ( Resource resource : step.getResources() )
                        {
                            Request request = buildRequest( resource );
                            request.header( LoadGeneratorResultHandler.AFTER_SEND_TIME_HEADER, //
                                            Long.toString( System.nanoTime() ) );
                            request.send( loadGeneratorResultHandler );
                        }
                    } else {
                        // it's a group so we can request in parallel but wait all responses before next step
                        ExecutorService executorService = Executors.newWorkStealingPool();

                        for ( Resource resource : step.getResources() )
                        {
                            executorService.execute( () -> {
                                try
                                {
                                    Request request = buildRequest( resource );
                                    request.header( LoadGeneratorResultHandler.AFTER_SEND_TIME_HEADER, //
                                                    Long.toString( System.nanoTime() ) );

                                    ContentResponse contentResponse = request.send();
                                    loadGeneratorResultHandler.onComplete( contentResponse );
                                }
                                catch ( Exception e )
                                {
                                    LOGGER.debug( e.getMessage(), e );
                                }
                            } );
                        }

                        executorService.shutdown();

                        // TODO make this configurable??
                        boolean finished = executorService.awaitTermination( step.getTimeout(), TimeUnit.MILLISECONDS );
                        if (!finished) {
                            LOGGER.warn( "resourceGroup request not all completed" );
                        }
                    }

                }

                long waitTime = 1000 / loadGenerator.getRequestRate();

                PLATFORM_TIMER.sleep( TimeUnit.MILLISECONDS.toMicros( waitTime ) );

            }
        }
        catch ( RejectedExecutionException e )
        {
            // can happen if the client has been stopped
            LOGGER.debug( "ignore RejectedExecutionException", e );
        }
        catch ( Throwable e )
        {
            LOGGER.warn( "ignoring exception", e );
            // TODO record error in generator report
        }
    }


    private Request buildRequest( Resource resource )
    {
        final String url = //
            loadGenerator.getScheme() + "://" //
                + loadGenerator.getHost() + ":" //
                + loadGenerator.getPort() + //
                ( resource.getPath() == null ? "" : resource.getPath() );

        Request request = httpClient.newRequest( url ).method( resource.getMethod() ).cookie( httpCookie );

        if ( resource.getResponseSize() > 0 )
        {
            request.header( "X-Download", Integer.toString( resource.getResponseSize() ) );
        }

        if ( resource.getSize() > 0 )
        {
            request.content( new BytesContentProvider( new byte[resource.getSize()] ) );
        }

        request.onResponseContentAsync( loadGeneratorResultHandler );

        return request;
    }

}
