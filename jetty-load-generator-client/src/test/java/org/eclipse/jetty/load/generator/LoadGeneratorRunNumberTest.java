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
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.load.generator.profile.Resource;
import org.eclipse.jetty.load.generator.profile.ResourceProfile;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RunWith( Parameterized.class )
public class LoadGeneratorRunNumberTest
    extends AbstractLoadGeneratorTest
{

    public LoadGeneratorRunNumberTest( LoadGenerator.Transport transport, int usersNumber )
    {
        super( transport, usersNumber );
    }

    @Parameterized.Parameters( name = "httpClientTransport/users: {0},{1}" )
    public static Collection<Object[]> data()
    {

        List<LoadGenerator.Transport> transports = new ArrayList<>();

        transports.add( LoadGenerator.Transport.HTTP );
        transports.add( LoadGenerator.Transport.HTTPS );
        transports.add( LoadGenerator.Transport.H2 );
        transports.add( LoadGenerator.Transport.H2C );
        transports.add( LoadGenerator.Transport.FCGI );


        // number of users
        List<Integer> users = Arrays.asList( 1 );//, 2, 4 );

        List<Object[]> parameters = new ArrayList<>();

        for ( LoadGenerator.Transport transport : transports )
        {
            for ( Integer userNumber : users )
            {
                parameters.add( new Object[]{ transport, userNumber } );
            }

        }
        return parameters;
    }

    @Test
    public void simpleTestLimitedRunTwo()
        throws Exception
    {
        int number = 2, expected =  number * usersNumber;

        responsePerPath = new ResponsePerPath();

        ResourceProfile resourceProfile = //
            new ResourceProfile( new Resource( "/index.html" ) );//, new Resource( "/foo.html" ).wait( true ) );

        Scheduler scheduler = new ScheduledExecutorScheduler( getClass().getName() + "-scheduler", false );

        LoadGenerator loadGenerator = new LoadGenerator.Builder() //
            .host( "localhost" ) //
            .port( connector.getLocalPort() ) //
            .users( this.usersNumber ) //
            .scheduler( scheduler ) //
            .transactionRate( 1 ) //
            .transport( this.transport ) //
            .httpClientTransport( this.httpClientTransport() ) //
            .loadProfile( resourceProfile ) //
            .sslContextFactory( sslContextFactory ) //
            .responseTimeListeners( responsePerPath ) //
            .httpVersion( httpVersion() ) //
            .build();
        loadGenerator.run( number );

        scheduler.stop();

        //Assert.assertEquals( 2, responsePerPath.getResponseTimePerPath().size() );

        for ( Map.Entry<String, AtomicLong> entry : responsePerPath.getRecorderPerPath().entrySet() )
        {
            Assert.assertEquals( transport +  " not " + expected + " response for path " + entry.getKey(), //
                                 expected, //
                                 entry.getValue().get() );
        }

    }

    @Test
    public void groupTestLimitedRunTwo()
        throws Exception
    {

        int number = 2, expected =  number * usersNumber;

        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(
            "website_profile.xml" ))
        {
            ResourceProfile resourceProfile = (ResourceProfile) new XmlConfiguration( inputStream ).configure();

            responsePerPath = new ResponsePerPath();

            Scheduler scheduler = new ScheduledExecutorScheduler( getClass().getName() + "-scheduler", false );

            LoadGenerator loadGenerator = new LoadGenerator.Builder() //
                .host( "localhost" ) //
                .port( connector.getLocalPort() ) //
                .users( this.usersNumber ) //
                .scheduler( scheduler ) //
                .transactionRate( 1 ) //
                .sslContextFactory( sslContextFactory ) //
                .transport( this.transport ) //
                .httpClientTransport( this.httpClientTransport() ) //
                .loadProfile( resourceProfile ) //
                .responseTimeListeners( responsePerPath ) //
                .httpVersion( httpVersion() ) //
                .build();
            loadGenerator.run( number );

            scheduler.stop();

            for ( Map.Entry<String, AtomicLong> entry : responsePerPath.getRecorderPerPath().entrySet() )
            {
                Assert.assertEquals( transport + " not " + expected + " response for path " + entry.getKey(), //
                                     expected, //
                                     entry.getValue().get() );
            }
        }


    }


}
