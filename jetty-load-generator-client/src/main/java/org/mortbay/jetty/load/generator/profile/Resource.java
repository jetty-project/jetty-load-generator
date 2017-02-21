//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
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

import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.StringUtil;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public class Resource
    implements Serializable
{

    private int responseSize;

    private String path;

    private int size;

    private String method = HttpMethod.GET.asString();


    /**
     * wait the responses for the provided {{@link #resources} before going to next resource
     * default to <code>false</code>
     */
    private boolean wait = false;

    /**
     * timeout in ms to wait to load all children {@link Resource} if any
     */
    private long childrenTimeout = 30000;

    private List<Resource> resources;

    public Resource()
    {
        // no op
    }

    public Resource(Resource... resources)
    {
        this(null, resources);
    }

    public Resource(List<Resource> resources)
    {
        this.resources = resources;
    }

    public Resource( String path )
    {
        this.path( path );
    }

    public Resource( int responseSize, String path, int size, String method )
    {
        this( path );
        this.responseSize = responseSize;
        this.size = size;
        this.method = method;
    }


    public Resource( String path, Resource... then )
    {
        this( path );
        this.resources = then == null ? new ArrayList<>() : Arrays.asList( then );
    }

    public Resource( String path, List<Resource> then )
    {
        this( path );
        this.resources = then;
    }

    public Resource path( String path )
    {
        this.path = StringUtil.startsWithIgnoreCase( path, "/" ) ? path : "/" + path;
        return this;
    }

    public void setPath( String path )
    {
        this.path( path );
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

    /**
     * cannot be used to add Resource, you must use {@link #addResource(Resource)}
     *
     * @return the children {@link Resource} or an empty list
     */
    public List<Resource> getResources()
    {
        return resources == null ? Collections.emptyList() : resources;
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

    public boolean isWait()
    {
        return wait;
    }

    public void setWait( boolean wait )
    {
        this.wait = wait;
    }

    public Resource wait( boolean wait )
    {
        this.wait = wait;
        return this;
    }

    public long getChildrenTimeout()
    {
        return childrenTimeout;
    }

    @Override
    public String toString()
    {
        return "Resource{" + "responseSize=" + responseSize + ", path='" + path + '\'' + ", size=" + size + ", method='"
            + method + '\'' + '}';
    }


}
