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
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.toolchain.perf.PlatformTimer;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.net.HttpCookie;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class LoadGeneratorRunner
    implements Runnable
{

    private static final Logger LOGGER = Log.getLogger(LoadGeneratorRunner.class);

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

        // FIXME populate loadGeneratorResult with statistics values
        try
        {
            while ( true )
            {
                if ( this.loadGenerator.getStop().get() || httpClient.isStopped() )
                {
                    break;
                }

                List<LoadGeneratorProfile.Step> steps = buildSteps();

                for (LoadGeneratorProfile.Step step : steps) {
                    for( LoadGeneratorProfile.Resource resource : step.getResources())
                    {
                        resource.request.send( loadGeneratorResultHandler );

                        loadGeneratorResultHandler.getLoadGeneratorResult().getTotalRequest().incrementAndGet();
                    }
                }

                long waitTime = 1000 / loadGenerator.getRequestRate();

                PLATFORM_TIMER.sleep( TimeUnit.MILLISECONDS.toMicros( waitTime ) );

            }
        } catch ( RejectedExecutionException e )
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

    private List<LoadGeneratorProfile.Step> buildSteps()
    {
        LoadGeneratorProfile workflow = loadGenerator.getLoadGeneratorProfile();

        List<LoadGeneratorProfile.Step> steps = new ArrayList<>( workflow.getSteps().size() );

        for( LoadGeneratorProfile.Step step : workflow.getSteps()) {

            LoadGeneratorProfile.Step clone = step.clone();

            for ( LoadGeneratorProfile.Resource resource : clone.getResources()) {
                final String url = //
                    loadGenerator.getScheme() + "://" //
                    + loadGenerator.getHost() + ":" //
                    + loadGenerator.getPort() + //
                    ( resource.getPath() == null ? "" : resource.getPath() );

                Request request = httpClient.newRequest( url ).method( resource.getMethod() ).cookie( httpCookie );

                if (resource.getResponseSize() > 0)
                {
                    request.header( "X-Download", Integer.toString( resource.getResponseSize() ) );
                }

                if (resource.getSize() > 0) {
                    request.content( new BytesContentProvider( new byte[resource.getSize()]) );
                }

                resource.request = request;
            }

            steps.add( clone );
        }

        return steps;
    }

    private static class DelayedSend implements Delayed
    {
        private long requestRate;

        private DelayedSend( long requestRate) {
            this.requestRate = requestRate;
        }

        @Override
        public long getDelay( TimeUnit unit )
        {
            return unit.convert( requestRate, TimeUnit.SECONDS );
        }

        @Override
        public int compareTo( Delayed o )
        {
            // we don't mind
            return 0;
        }
    }


}
