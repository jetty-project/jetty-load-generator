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

package org.webtide.jetty.load.generator;

import org.junit.Test;
import org.webtide.jetty.load.generator.LoadGenerator;

/**
 * Unit test for the builder validation
 */
public class LoadGeneratorBuilderTest
{
    @Test( expected = IllegalArgumentException.class )
    public void users_validation()
    {
        new LoadGenerator.Builder().users( 0 ).build();
    }

    @Test( expected = IllegalArgumentException.class )
    public void raterequest_validation()
    {
        new LoadGenerator.Builder().transactionRate( 0 ).build();
    }

    @Test( expected = IllegalArgumentException.class )
    public void host_null_validation()
    {
        new LoadGenerator.Builder().host( null ).build();
    }

    @Test( expected = IllegalArgumentException.class )
    public void host_blank_validation()
    {
        new LoadGenerator.Builder().host( " " ).build();
    }

    @Test( expected = IllegalArgumentException.class )
    public void port_zero_validation()
    {
        new LoadGenerator.Builder().port( 0 ).build();
    }

    @Test( expected = IllegalArgumentException.class )
    public void port_non_positive_validation()
    {
        new LoadGenerator.Builder().port( -99 ).build();
    }

}
