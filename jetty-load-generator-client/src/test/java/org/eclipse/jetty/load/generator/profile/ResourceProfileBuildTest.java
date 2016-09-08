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

package org.eclipse.jetty.load.generator.profile;

import org.eclipse.jetty.http.HttpMethod;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 */
public class ResourceProfileBuildTest
{

    @Test
    public void simple_build()
        throws Exception
    {
        ResourceProfile resourceProfile = new ResourceProfile( //
                                                               new Resource( "/index.html" ).size( 1024 ) //
            );

        Assert.assertEquals( 1, resourceProfile.getResources().size() );

        Assert.assertEquals( "/index.html",
                             resourceProfile.getResources().get( 0 ).getPath() );
        Assert.assertEquals( 1024, resourceProfile.getResources().get( 0 ).getSize() );
        Assert.assertEquals( "GET", resourceProfile.getResources().get( 0 ).getMethod() );
    }

    @Test
    public void simple_two_resources()
        throws Exception
    {
        ResourceProfile resourceProfile = //
            new ResourceProfile( //
                                 new Resource( "/index.html" ).size( 1024 ), //
                                 new Resource( "/beer.html" ).size( 2048 ).method( HttpMethod.POST.asString())  //
        );

        Assert.assertEquals( 2, resourceProfile.getResources().size() );

        Assert.assertEquals( "/index.html",
                             resourceProfile.getResources().get( 0 ).getPath() );
        Assert.assertEquals( 1024, resourceProfile.getResources().get( 0 ).getSize() );
        Assert.assertEquals( "GET", resourceProfile.getResources().get( 0 ).getMethod() );

        Assert.assertEquals( "/beer.html", resourceProfile.getResources().get( 1 ).getPath() );
        Assert.assertEquals( 2048, resourceProfile.getResources().get( 1 ).getSize() );
        Assert.assertEquals( "POST", resourceProfile.getResources().get( 1 ).getMethod() );
    }

    @Test
    public void website_profile() throws Exception
    {

        /*
        GET index.html
                style.css
                    logo.gif
                    spacer.png
                fancy.css
                script.js
                    library.js
                    morestuff.js
                anotherScript.js
                iframeContents.html
                moreIframeContents.html
                favicon.ico
        */

        ResourceProfile sample = //
            new ResourceProfile( //
                                 new Resource( "index.html" , //
                   new Resource( "/style.css", //
                        new Resource( "/logo.gif"), //
                        new Resource( "/spacer.png") //
                   ), //
                   new Resource( "/fancy.css" ), //
                   new Resource( "/script.js", //
                        new Resource( "/library.js"), //
                        new Resource( "/morestuff.js") //
                   ), //
                   new Resource( "/anotherScript.js" ), //
                   new Resource( "/iframeContents.html" ), //
                   new Resource( "/moreIframeContents.html" ), //
                   new Resource( "/favicon.ico" ) //
               )
             );

        Assert.assertEquals( 1, sample.getResources().size() );

        Assert.assertEquals( 7, sample.getResources().get( 0 ).getResources().size() );

        // FIXME more testing here!!!


    }

}
