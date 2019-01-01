//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jetty.load.generator.listeners.responsetime;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.mortbay.jetty.load.generator.Resource;

/**
 *
 */
public class ResponseNumberPerPath
    implements Resource.NodeListener, Serializable
{

    private final Map<String, AtomicInteger> responseNumberPerPath = new ConcurrentHashMap<>();

    @Override
    public void onResourceNode( Resource.Info info )
    {
        String path = info.getResource().getPath();

        // response number record
        {

            AtomicInteger number = responseNumberPerPath.get( path );
            if ( number == null )
            {
                number = new AtomicInteger( 1 );
                responseNumberPerPath.put( path, number );
            }
            else
            {
                number.incrementAndGet();
            }

        }
    }

    public Map<String, AtomicInteger> getResponseNumberPerPath()
    {
        return responseNumberPerPath;
    }

}
