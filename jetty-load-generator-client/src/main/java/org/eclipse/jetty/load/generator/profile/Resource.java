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

/**
 *
 */
public class Resource
{

    private int responseSize;

    private String path;

    private int size;

    private String method = HttpMethod.GET.asString();

    public Resource( String path )
    {
        this.path = path;
    }

    public Resource path( String path )
    {
        this.path = path;
        return this;
    }

    public Resource size( int size )
    {
        this.size = size;
        return this;
    }

    public Resource method( String method )
    {
        this.method = method;
        return this;
    }

    public String getPath()
    {
        return path;
    }

    public int getSize()
    {
        return size;
    }

    public String getMethod()
    {
        return method;
    }

    public Resource responseSize( int responseSize )
    {
        this.responseSize = responseSize;
        return this;
    }

    public int getResponseSize()
    {
        return responseSize;
    }

    protected Resource clone()
    {
        Resource resource = new Resource( this.path );
        resource.size = this.size;
        resource.responseSize = this.responseSize;
        return resource;
    }

}
