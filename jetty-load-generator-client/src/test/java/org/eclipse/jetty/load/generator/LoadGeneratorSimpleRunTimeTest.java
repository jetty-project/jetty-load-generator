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


import org.eclipse.jetty.load.generator.latency.LatencyDisplayListener;
import org.eclipse.jetty.load.generator.latency.SummaryLatencyListener;
import org.eclipse.jetty.load.generator.response.ResponseTimeDisplayListener;
import org.eclipse.jetty.load.generator.response.SummaryResponseTimeListener;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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

        LoadGeneratorProfile loadGeneratorProfile = new LoadGeneratorProfile.Builder() //
            .resource( "/index.html" ).size( 1024 ) //
            //.resource( "" ).size( 1024 ) //
            .build();

        startServer( new LoadHandler() );

        Scheduler scheduler = new ScheduledExecutorScheduler( getClass().getName() + "-scheduler", false );

        new LoadGenerator.Builder() //
            .host( "localhost" ) //
            .port( connector.getLocalPort() ) //
            .users( this.usersNumber ) //
            .scheduler( scheduler ) //
            .requestRate( 1 ) //
            .transport( this.transport ) //
            .httpClientTransport( this.transport() ) //
            .selectors( this.usersNumber ) //
            .loadProfile( loadGeneratorProfile ) //
            .latencyListeners( new LatencyDisplayListener(), new SummaryLatencyListener() ) //
            .responseTimeListeners( new ResponseTimeDisplayListener(), new SummaryResponseTimeListener() ) //
            .build() //
            .run( 5, TimeUnit.SECONDS );

        scheduler.stop();
    }


}
