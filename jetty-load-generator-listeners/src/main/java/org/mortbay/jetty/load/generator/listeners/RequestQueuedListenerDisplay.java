//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jetty.load.generator.listeners;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.mortbay.jetty.load.generator.LoadGenerator;

/**
 *
 */
public class RequestQueuedListenerDisplay
    extends Request.Listener.Adapter
    implements LoadGenerator.EndListener
{

    private static final Logger LOGGER = Log.getLogger( RequestQueuedListenerDisplay.class );

    private AtomicLong requestsQueued = new AtomicLong( 0 );

    private ScheduledExecutorService scheduledExecutorService;

    public RequestQueuedListenerDisplay()
    {
        this( 10, 30, TimeUnit.SECONDS );
    }

    public RequestQueuedListenerDisplay( long initial, long delay, TimeUnit timeUnit )
    {
        scheduledExecutorService = Executors.newScheduledThreadPool( 1 );
        scheduledExecutorService.scheduleWithFixedDelay( () ->
            {
                LOGGER.info( "----------------------------------------" );
                LOGGER.info( "  Requests in queue: " + requestsQueued.get() );
                LOGGER.info( "----------------------------------------" );
            },//
            initial, delay, timeUnit );
    }

    @Override
    public void onQueued( Request request )
    {
        requestsQueued.incrementAndGet();
    }

    @Override
    public void onBegin( Request request )
    {
        requestsQueued.decrementAndGet();
    }

    @Override
    public void onEnd( LoadGenerator generator )
    {
        this.scheduledExecutorService.shutdownNow();
    }

}
