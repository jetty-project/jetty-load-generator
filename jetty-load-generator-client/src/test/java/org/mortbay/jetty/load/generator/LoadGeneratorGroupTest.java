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


import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mortbay.jetty.load.generator.profile.Resource;
import org.mortbay.jetty.load.generator.profile.ResourceProfile;

import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@RunWith( Parameterized.class )
public class LoadGeneratorGroupTest
    extends AbstractLoadGeneratorTest
{

    public LoadGeneratorGroupTest( LoadGenerator.Transport transport, int usersNumber )
    {
        super( transport, usersNumber );
    }


    @Test
    public void simple_with_group()
        throws Exception
    {
        ResourceProfile resourceProfile =
            new ResourceProfile(
                                new Resource( "/index.html",
                                              new Resource( "/foo.html" ), //
                                              new Resource( "/cider.html" )),
                                new Resource( "/wine.html" , //
                                    new Resource( "/beer.html" )
                                ));

        runProfile( resourceProfile );

        Map<String,AtomicLong> response = responsePerPath.getRecorderPerPath();

        for ( Map.Entry<String,AtomicLong> entry : response.entrySet())
        {
            logger.info( "responsePerPath: {}", entry );
        }
    }

    @Test
    public void website_like() throws Exception {
        ResourceProfile sample = //
            new ResourceProfile( //
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
                    )
            );


        runProfile( sample );

        Map<String,AtomicLong> response = responsePerPath.getRecorderPerPath();

        logger.info( "responsePerPath: {}", response );

    }

}
