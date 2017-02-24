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
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

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

    private final CyclicBarrier _cyclicBarrier;

    // maintain a session/cookie per httpClient
    // FIXME olamy: not sure we really need that??
    private final HttpCookie httpCookie = new HttpCookie( "XXX-Jetty-LoadGenerator", //
                                                          Long.toString( System.nanoTime() ) );

    private Executor executor;

    private AtomicBoolean done = new AtomicBoolean(false);

    public LoadGeneratorRunner( HttpClient httpClient, LoadGenerator loadGenerator,
                                LoadGeneratorResultHandler loadGeneratorResultHandler, int transactionNumber,
                                CyclicBarrier cyclicBarrier, Executor executor )
    {
        this.httpClient = httpClient;
        this.loadGenerator = loadGenerator;
        this.loadGeneratorResultHandler = loadGeneratorResultHandler;
        this.transactionNumber = transactionNumber;
        this._cyclicBarrier = cyclicBarrier;
        this.executor = executor == null ? Executors.newCachedThreadPool() : executor;
    }

    // TODO implements Future ?
    public boolean isDone()
    {
        return this.done.get();
    }

    @Override
    public void run()
    {
        LOGGER.debug( "loadGenerator#run" );
        try
        {
            if ( _cyclicBarrier != null )
            {
                _cyclicBarrier.await();
            }
            do
            {
                if ( this.loadGenerator.getStop().get() || httpClient.isStopped() )
                {
                    break;
                }

                Resource resource = loadGenerator.getResource();
                handleResource( resource );

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
            while ( transactionNumber != 0 );

            HttpDestination destination = (HttpDestination) httpClient.getDestination( loadGenerator.getScheme(), //
                                                                                       loadGenerator.getHost(), //
                                                                                       loadGenerator.getPort() );

            //wait until the end of all requests
            while ( !destination.getHttpExchanges().isEmpty() )
            {
                Thread.sleep( 1 );
            }
            done.set( true );
            LOGGER.debug( "run finish" );
        }
        catch ( InterruptedException e )
        {
            Thread.currentThread().interrupt();
            // this exception can happen when interrupting the generator so ignore
            if ( !this.loadGenerator.getStop().get() )
            {
                LOGGER.warn( "ignoring InterruptedException:" + e.getMessage(), e );
            }
            return;
        }
        catch ( Throwable e )
        {
            LOGGER.warn( "ignoring Throwable:" + e.getMessage(), e );
            // TODO record error in generator report
        }
    }

    private void handleResource( Resource resource )
        throws Exception
    {
        if (resource.getPath() != null)
        {
            if ( !resource.getResources().isEmpty() )
            {

                Request request = buildRequest( resource ).onResponseBegin(

                    response -> {

                        CyclicBarrier cyclicBarrier = new CyclicBarrier( resource.getResources().size() );
                        for ( Resource children : resource.getResources() )
                        {
                            executor.execute( () ->
                                                     {
                                                         try
                                                         {
                                                             cyclicBarrier.await();
                                                             handleResource( children );
                                                         } catch ( InterruptedException e )
                                                         {
                                                             Thread.currentThread().interrupt();
                                                             LOGGER.warn( e.getMessage(), e );
                                                         }
                                                         catch ( Exception e )
                                                         {
                                                             LOGGER.debug( e.getMessage(), e );
                                                         }
                                                     } );
                        }

                    }
                );

                request.send( loadGeneratorResultHandler );
                //loadGeneratorResultHandler.onComplete( request.send() );

            }
            else
            {
                buildRequest( resource ).send( loadGeneratorResultHandler );
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

        if (!resource.getResources().isEmpty())
        {
            request.onResponseBegin( loadGeneratorResultHandler );
        }

        request.header( LoadGeneratorResultHandler.START_RESPONSE_TIME_HEADER, //
                        Long.toString( System.nanoTime() ) );

        return request;
    }

}
