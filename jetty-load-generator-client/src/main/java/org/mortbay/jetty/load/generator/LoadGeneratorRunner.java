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

import org.apache.commons.lang.RandomStringUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.toolchain.perf.PlatformTimer;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.profile.Resource;

import java.net.HttpCookie;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private int transactionNumber = -1;

    // maintain a session/cookie per httpClient
    // FIXME olamy: not sure we really need that??
    private final HttpCookie httpCookie = new HttpCookie( "XXX-Jetty-LoadGenerator", //
                                                          Long.toString( System.nanoTime() ) );

    public LoadGeneratorRunner( HttpClient httpClient, LoadGenerator loadGenerator,
                                LoadGeneratorResultHandler loadGeneratorResultHandler, int transactionNumber )
    {
        this.httpClient = httpClient;
        this.loadGenerator = loadGenerator;
        this.loadGeneratorResultHandler = loadGeneratorResultHandler;
        this.transactionNumber = transactionNumber;
    }

    @Override
    public void run()
    {
        LOGGER.debug( "loadGenerator#run" );
        try
        {
            do
            {
                if ( this.loadGenerator.getStop().get() || httpClient.isStopped() )
                {
                    break;
                }

                List<Resource> resources = loadGenerator.getProfile().getResources();

                for ( Resource resource : resources )
                {
                    handleResource( resource );
                }

                int transactionRate = loadGenerator.getTransactionRate();
                if ( transactionRate > 0 )
                {
                    long waitTime = 1000 / transactionRate;
                    PLATFORM_TIMER.sleep( TimeUnit.MILLISECONDS.toMicros( waitTime ) );
                }

                if ( transactionNumber > -1 )
                {
                    transactionNumber--;
                }

            }
            while ( true && transactionNumber != 0 );

            HttpDestination destination = (HttpDestination) httpClient.getDestination( loadGenerator.getScheme(), //
                                                                                       loadGenerator.getHost(), //
                                                                                       loadGenerator.getPort() );

            //wait until the end of all requests
            while ( !destination.getHttpExchanges().isEmpty() )
            {
                Thread.sleep( 1 );
            }
        }
        catch ( Throwable e )
        {
            LOGGER.warn( "ignoring exception:" + e.getMessage(), e );
            // TODO record error in generator report
        }
    }

    private void handleResource( Resource resource )
        throws Exception
    {

        // so we have sync call if we have children or resource marked as wait
        if ( !resource.getResources().isEmpty() || resource.isWait() )
        {
            loadGeneratorResultHandler.onComplete( buildRequest( resource ).send() );
        }
        else
        {
            buildRequest( resource ).send( loadGeneratorResultHandler );
        }

        if ( !resource.getResources().isEmpty() )
        {
            // it's a group so we can request in parallel but wait all responses before next step
            ExecutorService executorService = Executors.newWorkStealingPool();

            for ( Resource children : resource.getResources() )
            {
                executorService.execute( () ->
                                         {
                                             try
                                             {
                                                 handleResource( children );
                                             }
                                             catch ( Exception e )
                                             {
                                                 LOGGER.debug( e.getMessage(), e );
                                             }
                                         } );
            }

            executorService.shutdown();

            // TODO make this configurable??
            boolean finished = executorService.awaitTermination( resource.getChildrenTimeout(), TimeUnit.MILLISECONDS );
            if ( !finished )
            {
                LOGGER.warn( "resourceGroup request not all completed for timeout " + resource.getChildrenTimeout() );
            }
        }
    }


    private Request buildRequest( Resource resource )
    {
        final String url = //
            loadGenerator.getScheme() + "://" //
                + loadGenerator.getHost() + ":" //
                + loadGenerator.getPort() + //
                ( resource.getPath() == null ? "" : resource.getPath() );

        Request request = httpClient.newRequest( url );

        request.version( loadGenerator.getHttpVersion() );

        request.method( resource.getMethod() ).cookie( httpCookie );

        if ( resource.getResponseSize() > 0 )
        {
            request.header( "X-Download", Integer.toString( resource.getResponseSize() ) );
        }

        if ( resource.getSize() > 0 )
        {
            request.content( new BytesContentProvider( RandomStringUtils.random( resource.getSize() ).getBytes() ) );
        }

        //request.onResponseContentAsync( loadGeneratorResultHandler );

        request.onRequestBegin( beginRequest ->
                                {
                                    beginRequest.header( LoadGeneratorResultHandler.START_LATENCY_TIME_HEADER, //
                                                         Long.toString( System.nanoTime() ) );
                                } );

        if (resource.isWait())
        {
            request.onResponseBegin( loadGeneratorResultHandler );
        }
        //request.onComplete( loadGeneratorResultHandler );

        request.header( LoadGeneratorResultHandler.START_RESPONSE_TIME_HEADER, //
                        Long.toString( System.nanoTime() ) );

        return request;
    }

}
