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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class LoadGeneratorProfile
{

    private List<Resource> resources = new ArrayList<>();

    public LoadGeneratorProfile()
    {
        // no op
    }

    public LoadGeneratorProfile( List<Resource> resources )
    {
        this.resources = resources;
    }

    public LoadGeneratorProfile( Resource... resources )
    {
        this.resources = resources == null ? new ArrayList<>() : Arrays.asList( resources );
    }

    public List<Resource> getResources()
    {
        return resources;
    }

    public void setResources( List<Resource> resources )
    {
        this.resources = resources;
    }

    public void addResource( Resource resource )
    {
        if ( this.resources == null )
        {
            this.resources = new ArrayList<>();
        }
        this.resources.add( resource );
    }
}