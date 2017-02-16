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


import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mortbay.jetty.load.generator.profile.Resource;
import org.mortbay.jetty.load.generator.profile.ResourceProfile;
import org.mortbay.jetty.load.generator.responsetime.ResponseTimeDisplayListener;
import org.mortbay.jetty.load.generator.responsetime.TimePerPathListener;

import java.util.concurrent.TimeUnit;

@RunWith( Parameterized.class )
public class LoadGeneratorSimpleRunTimeTest
    extends AbstractLoadGeneratorTest
{

    public LoadGeneratorSimpleRunTimeTest( LoadGenerator.Transport transport, int usersNumber )
    {
        super( transport, usersNumber );
    }


    @Test
    public void simple_test_limited_time_run()
        throws Exception
    {

        ResourceProfile resourceProfile = //
            new ResourceProfile( new Resource( "/index.html" ).size( 1024 ) );

        Scheduler scheduler = new ScheduledExecutorScheduler( getClass().getName() + "-scheduler", false );

        TimePerPathListener latency = new TimePerPathListener();

        new LoadGenerator.Builder() //
            .host( "localhost" ) //
            .port( connector.getLocalPort() ) //
            .users( this.usersNumber ) //
            .scheduler( scheduler ) //
            .transactionRate( 1 ) //
            .transport( this.transport ) //
            .httpClientTransport( this.httpClientTransport() ) //
            .loadProfile( resourceProfile ) //
            .latencyTimeListeners( latency ) //
            .responseTimeListeners( new ResponseTimeDisplayListener(), new TimePerPathListener() ) //
            .httpVersion( httpVersion() ) //
            .build() //
            .run( 5, TimeUnit.SECONDS );

        scheduler.stop();
    }


    @Test
    public void simple_test_limited_number_run()
        throws Exception
    {

        int requestNumber = 2;

        ResourceProfile resourceProfile = //
            new ResourceProfile( new Resource( "/index.html" ).size( 1024 ) );

        Scheduler scheduler = new ScheduledExecutorScheduler( getClass().getName() + "-scheduler", false );

        TimePerPathListener result = new TimePerPathListener();

        new LoadGenerator.Builder() //
            .host( "localhost" ) //
            .port( connector.getLocalPort() ) //
            .users( this.usersNumber ) //
            .scheduler( scheduler ) //
            .transactionRate( 1 ) //
            .transport( this.transport ) //
            .httpClientTransport( this.httpClientTransport() ) //
            .loadProfile( resourceProfile ) //
            .latencyTimeListeners( result ) //
            .responseTimeListeners( result ) //
            .httpVersion( httpVersion() ) //
            .build() //
            .run( requestNumber );

        Assert.assertEquals( requestNumber,
                             result.getResponseTimePerPath().values().iterator().next().getIntervalHistogram().getTotalCount() );

        Assert.assertEquals( requestNumber,
                             result.getLatencyTimePerPath().values().iterator().next().getIntervalHistogram().getTotalCount() );

        scheduler.stop();
    }

    @Test
    public void simple_test_limited_number_run_sync_call()
        throws Exception
    {

        int requestNumber = 2;
        ResourceProfile resourceProfile = //
            new ResourceProfile( new Resource( "/index.html" ).size( 1024 ).wait( true ) );

        Scheduler scheduler = new ScheduledExecutorScheduler( getClass().getName() + "-scheduler", false );

        TimePerPathListener result = new TimePerPathListener();

        new LoadGenerator.Builder() //
            .host( "localhost" ) //
            .port( connector.getLocalPort() ) //
            .users( this.usersNumber ) //
            .scheduler( scheduler ) //
            .transactionRate( 1 ) //
            .transport( this.transport ) //
            .httpClientTransport( this.httpClientTransport() ) //
            .loadProfile( resourceProfile ) //
            .latencyTimeListeners( result ) //
            .responseTimeListeners( result ) //
            .httpVersion( httpVersion() ) //
            .build() //
            .run( requestNumber );

        Assert.assertEquals( requestNumber,
                             result.getResponseTimePerPath().values().iterator().next().getIntervalHistogram().getTotalCount() );

        Assert.assertEquals( requestNumber,
                             result.getLatencyTimePerPath().values().iterator().next().getIntervalHistogram().getTotalCount() );

        scheduler.stop();
    }

}
