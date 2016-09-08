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


import org.eclipse.jetty.load.generator.profile.LoadGeneratorProfile;
import org.eclipse.jetty.load.generator.profile.Resource;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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

        // TODO size only for upload PUT / POST

        LoadGeneratorProfile loadGeneratorProfile = new LoadGeneratorProfile( //
            new Resource( "/index.html",
                              new Resource( "/foo.html" ), //
                              new Resource( "/beer.html" ) //

            ).size( 1024 ), //
            new Resource( "/wine.html" ), //
            new Resource( "/beer.html" )
            );

        runProfile( loadGeneratorProfile );

    }

}
