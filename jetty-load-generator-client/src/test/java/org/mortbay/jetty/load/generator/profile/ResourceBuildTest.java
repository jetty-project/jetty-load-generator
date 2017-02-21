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

package org.mortbay.jetty.load.generator.profile;

import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 *
 */
public class ResourceBuildTest
{

    @Test
    public void simple_build()
        throws Exception
    {
        Resource resourceProfile = //
            new Resource( //
                                 new Resource( "/index.html" ).size( 1024 ) //
            );

        Assert.assertEquals( 1, resourceProfile.getResources().size() );

        Assert.assertEquals( "/index.html", resourceProfile.getResources().get( 0 ).getPath() );
        Assert.assertEquals( 1024, resourceProfile.getResources().get( 0 ).getSize() );
        Assert.assertEquals( "GET", resourceProfile.getResources().get( 0 ).getMethod() );
    }

    @Test
    public void simple_two_resources()
        throws Exception
    {
        Resource resourceProfile = //
            new Resource( //
                                 new Resource( "/index.html" ).size( 1024 ), //
                                 new Resource( "/beer.html" ).size( 2048 ).method( HttpMethod.POST.asString() )  //
            );

        Assert.assertEquals( 2, resourceProfile.getResources().size() );

        Assert.assertEquals( "/index.html", resourceProfile.getResources().get( 0 ).getPath() );
        Assert.assertEquals( 1024, resourceProfile.getResources().get( 0 ).getSize() );
        Assert.assertEquals( "GET", resourceProfile.getResources().get( 0 ).getMethod() );

        Assert.assertEquals( "/beer.html", resourceProfile.getResources().get( 1 ).getPath() );
        Assert.assertEquals( 2048, resourceProfile.getResources().get( 1 ).getSize() );
        Assert.assertEquals( "POST", resourceProfile.getResources().get( 1 ).getMethod() );
    }

    @Test
    public void website_profile()
        throws Exception
    {

        Resource sample = //
            new Resource( //
                                 new Resource( "index.html", //
                                               new Resource( "/style.css", //
                                                             new Resource( "/logo.gif" ), //
                                                             new Resource( "/spacer.png" ) //
                                               ), //
                                               new Resource( "/fancy.css" ), //
                                               new Resource( "/script.js", //
                                                             new Resource( "/library.js" ), //
                                                             new Resource( "/morestuff.js" ) //
                                               ), //
                                               new Resource( "/anotherScript.js" ), //
                                               new Resource( "/iframeContents.html" ), //
                                               new Resource( "/moreIframeContents.html" ), //
                                               new Resource( "/favicon.ico" ) //
                                 ) );

        web_profile_assert( sample );
    }


    @Test
    public void website_profile_with_xml()
        throws Exception
    {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream( "website_profile.xml" ))
        {
            Resource sample = (Resource) new XmlConfiguration( inputStream ).configure();
            web_profile_assert( sample );
        }
    }

    public static String read(InputStream input) throws IOException
    {
        try (BufferedReader buffer = new BufferedReader( new InputStreamReader( input))) {
            return buffer.lines().collect( Collectors.joining( System.lineSeparator() ));
        }
    }

    @Test
    public void website_profile_with_groovy()
        throws Exception
    {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream( "website_profile.groovy" ))
        {
            Resource sample = (Resource) evaluateScript( read( inputStream ) );
            web_profile_assert( sample );
        }
    }


    public Object evaluateScript( String script )
        throws Exception
    {

        CompilerConfiguration config = new CompilerConfiguration( CompilerConfiguration.DEFAULT );
        config.setDebug( true );
        config.setVerbose( true );

        GroovyShell interpreter = new GroovyShell( config );

        return interpreter.evaluate( script );
    }


    protected void web_profile_assert(Resource sample) {


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

        Assert.assertEquals( 1, sample.getResources().size() );

        Assert.assertEquals( 7, sample.getResources().get( 0 ).getResources().size() );

        Assert.assertEquals( "/style.css", sample.getResources().get( 0 ).getResources().get( 0 ).getPath() );

        Assert.assertEquals( "/logo.gif", sample.getResources().get( 0 ) //
            .getResources().get( 0 ).getResources().get( 0 ).getPath() );

        Assert.assertEquals( "/spacer.png", sample.getResources().get( 0 ) //
            .getResources().get( 0 ).getResources().get( 1 ).getPath() );

        Assert.assertEquals( 2, sample.getResources().get( 0 ) //
            .getResources().get( 0 ).getResources().size() );

        Assert.assertEquals( 2, sample.getResources().get( 0 ) //
            .getResources().get( 2 ).getResources().size() );

        Assert.assertEquals( "/library.js", sample.getResources().get( 0 ) //
            .getResources().get( 2 ).getResources().get(0).getPath() );

        Assert.assertEquals( "/morestuff.js", sample.getResources().get( 0 ) //
            .getResources().get( 2 ).getResources().get(1).getPath() );

        Assert.assertEquals( "/anotherScript.js", sample.getResources().get( 0 ) //
            .getResources().get( 3 ).getPath() );

        Assert.assertEquals( "/moreIframeContents.html", sample.getResources().get( 0 ) //
            .getResources().get( 5 ).getPath() );

        Assert.assertEquals( "/favicon.ico", sample.getResources().get( 0 ) //
            .getResources().get( 6 ).getPath() );

    }
}
