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


import org.eclipse.jetty.load.generator.responsetime.ResponseTimeDisplayListener;
import org.eclipse.jetty.load.generator.responsetime.ResponseTimePerPathListener;
import org.eclipse.jetty.load.generator.profile.Resource;
import org.eclipse.jetty.load.generator.profile.ResourceProfile;
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

        ResourceProfile resourceProfile = //
            new ResourceProfile( new Resource( "/index.html" ).size( 1024 ));

        Scheduler scheduler = new ScheduledExecutorScheduler( getClass().getName() + "-scheduler", false );

        new LoadGenerator.Builder() //
            .host( "localhost" ) //
            .port( connector.getLocalPort() ) //
            .users( this.usersNumber ) //
            .scheduler( scheduler ) //
            .transactionRate( 1 ) //
            .transport( this.transport ) //
            .httpClientTransport( this.httpClientTransport() ) //
            .loadProfile( resourceProfile ) //
            .responseTimeListeners( new ResponseTimeDisplayListener(), new ResponseTimePerPathListener() ) //
            .httpVersion( httpVersion() ) //
            .build() //
            .run( 5, TimeUnit.SECONDS );

        scheduler.stop();
    }




}
