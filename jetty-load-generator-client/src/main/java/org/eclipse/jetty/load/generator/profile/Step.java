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
import java.util.List;

/**
 *
 */
public class Step
{

    private List<Resource> resources;

    /**
     * wait the response before requesting the next step
     */
    protected boolean wait;

    public Step()
    {
        this.resources = new ArrayList<>();
    }

    public Step( Resource resource )
    {
        this();
        this.resources.add( resource );
    }


    private Step( List<Resource> resources )
    {
        this.resources = resources;
    }

    public List<Resource> getResources()
    {
        return resources;
    }

    public boolean isWait()
    {
        return wait;
    }

    protected Step clone()
    {
        Step clone = new Step();
        clone.wait = this.wait;
        for ( Resource resource : this.resources )
        {
            clone.resources.add( resource.clone() );
        }

        return clone;
    }

}
