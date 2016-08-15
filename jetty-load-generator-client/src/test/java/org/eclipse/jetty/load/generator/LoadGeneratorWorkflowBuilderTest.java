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

import org.eclipse.jetty.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;

/**
 *  FIXME more assert on values!!
 */
public class LoadGeneratorWorkflowBuilderTest
{

    @Test
    public void simple_build() throws Exception
    {
        LoadGeneratorWorkflow loadGeneratorWorkflow =
            LoadGeneratorWorkflow.Builder.builder() //
                .resource( "/index.html" ).size( 1024 ) //
                .build();

        Assert.assertEquals(1, loadGeneratorWorkflow.getSteps().size());
        Assert.assertEquals(1, loadGeneratorWorkflow.getSteps().get(0).getResources().size());

        Assert.assertEquals( "/index.html", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getPath() );
        Assert.assertEquals( 1024, loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getSize() );
        Assert.assertEquals( "GET", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getMethod() );
    }

    @Test
    public void simple_two_resources() throws Exception
    {
        LoadGeneratorWorkflow loadGeneratorWorkflow = //
            LoadGeneratorWorkflow.Builder.builder() //
                .resource( "/index.html" ).size( 1024 ) //
                .resource( "/beer.html" ).size( 2048 ).method( HttpMethod.POST.asString() ) //
                .build();

        Assert.assertEquals(2, loadGeneratorWorkflow.getSteps().size());
        Assert.assertEquals(1, loadGeneratorWorkflow.getSteps().get(0).getResources().size());
        Assert.assertEquals(1, loadGeneratorWorkflow.getSteps().get(1).getResources().size());

        Assert.assertEquals( "/index.html", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getPath() );
        Assert.assertEquals( 1024, loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getSize() );
        Assert.assertEquals( "GET", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getMethod() );

        Assert.assertEquals( "/beer.html", loadGeneratorWorkflow.getSteps().get(1).getResources().get( 0 ).getPath() );
        Assert.assertEquals( 2048, loadGeneratorWorkflow.getSteps().get(1).getResources().get( 0 ).getSize() );
        Assert.assertEquals( "POST", loadGeneratorWorkflow.getSteps().get(1).getResources().get( 0 ).getMethod() );
    }

    @Test
    public void simple_resource_group() throws Exception
    {
        LoadGeneratorWorkflow loadGeneratorWorkflow = //
            LoadGeneratorWorkflow.Builder.builder() //
                .resourceGroup() //
                .resource( "/index.html" ).size( 1024 ) //
                .resource( "/beer.html" ).size( 2048 ).method( "POST" ) //
                .resource( "/wine.html" ).size( 4096 ) //
                .build();

        Assert.assertEquals(1, loadGeneratorWorkflow.getSteps().size());
        Assert.assertEquals(3, loadGeneratorWorkflow.getSteps().get(0).getResources().size());


        Assert.assertEquals( "/index.html", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getPath() );
        Assert.assertEquals( 1024, loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getSize() );
        Assert.assertEquals( "GET", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getMethod() );

        Assert.assertEquals( "/beer.html", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 1 ).getPath() );
        Assert.assertEquals( 2048, loadGeneratorWorkflow.getSteps().get(0).getResources().get( 1 ).getSize() );
        Assert.assertEquals( "POST", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 1 ).getMethod() );

        Assert.assertEquals( "/wine.html", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 2 ).getPath() );
        Assert.assertEquals( 4096, loadGeneratorWorkflow.getSteps().get(0).getResources().get( 2 ).getSize() );
        Assert.assertEquals( "GET", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 2 ).getMethod() );

    }

    @Test
    public void mix_resource_with_resource_group() throws Exception
    {
        LoadGeneratorWorkflow loadGeneratorWorkflow = //
            LoadGeneratorWorkflow.Builder.builder() //
                .resource( "/cheese.html" ).size( 8192 ) //
                .then() //
                .resourceGroup() //
                .resource( "/index.html" ).size( 1024 ) //
                .resource( "/beer.html" ).size( 2048 ).method( "POST" ) //
                .resource( "/wine.html" ).size( 4096 ) //
                .then()
                .resource( "/coffee.html" ).size( 35292 ) //
                .build();

        Assert.assertEquals(3, loadGeneratorWorkflow.getSteps().size());
        Assert.assertEquals(1, loadGeneratorWorkflow.getSteps().get(0).getResources().size());
        Assert.assertEquals(3, loadGeneratorWorkflow.getSteps().get(1).getResources().size());
        Assert.assertEquals(1, loadGeneratorWorkflow.getSteps().get(2).getResources().size());

        Assert.assertTrue( loadGeneratorWorkflow.getSteps().get(0).isWait() );
        Assert.assertTrue( loadGeneratorWorkflow.getSteps().get(1).isWait() );
        Assert.assertFalse( loadGeneratorWorkflow.getSteps().get(2).isWait() );

        Assert.assertEquals( "/cheese.html", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getPath() );
        Assert.assertEquals( 8192, loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getSize() );
        Assert.assertEquals( "GET", loadGeneratorWorkflow.getSteps().get(0).getResources().get( 0 ).getMethod() );

        Assert.assertEquals( "/index.html", loadGeneratorWorkflow.getSteps().get(1).getResources().get( 0 ).getPath() );
        Assert.assertEquals( 1024, loadGeneratorWorkflow.getSteps().get(1).getResources().get( 0 ).getSize() );
        Assert.assertEquals( "GET", loadGeneratorWorkflow.getSteps().get(1).getResources().get( 0 ).getMethod() );

        Assert.assertEquals( "/beer.html", loadGeneratorWorkflow.getSteps().get(1).getResources().get( 1 ).getPath() );
        Assert.assertEquals( 2048, loadGeneratorWorkflow.getSteps().get(1).getResources().get( 1 ).getSize() );
        Assert.assertEquals( "POST", loadGeneratorWorkflow.getSteps().get(1).getResources().get( 1 ).getMethod() );

        Assert.assertEquals( "/wine.html", loadGeneratorWorkflow.getSteps().get(1).getResources().get( 2 ).getPath() );
        Assert.assertEquals( 4096, loadGeneratorWorkflow.getSteps().get(1).getResources().get( 2 ).getSize() );
        Assert.assertEquals( "GET", loadGeneratorWorkflow.getSteps().get(1).getResources().get( 2 ).getMethod() );

        Assert.assertEquals( "/coffee.html", loadGeneratorWorkflow.getSteps().get(2).getResources().get( 0 ).getPath() );
        Assert.assertEquals( 35292, loadGeneratorWorkflow.getSteps().get(2).getResources().get( 0 ).getSize() );
        Assert.assertEquals( "GET", loadGeneratorWorkflow.getSteps().get(2).getResources().get( 0 ).getMethod() );

    }



}
