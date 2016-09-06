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
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class Step
{

    private List<Resource> resources;

    /**
     * wait the response before requesting the next step
     */
    private boolean wait;

    /**
     * timeout in ms
     */
    private long timeout = 30000;


    public Step()
    {
        this.resources = new ArrayList<>();
    }

    public Step( Resource resource )
    {
        this();
        this.resources.add( resource );
    }

    public Step( List<Resource> resources )
    {
        this.resources = resources;
    }

    public List<Resource> getResources()
    {
        return resources;
    }

    public void addResource( Resource resource )
    {
        if ( this.resources == null )
        {
            this.resources = new ArrayList<>();
        }
        this.resources.add( resource );
    }

    public boolean isWait()
    {
        return wait;
    }

    public void setWait( boolean wait )
    {
        this.wait = wait;
    }

    public void setTimeout( long timeout )
    {
        this.timeout = timeout;
    }

    public long getTimeout()
    {
        return timeout;
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
