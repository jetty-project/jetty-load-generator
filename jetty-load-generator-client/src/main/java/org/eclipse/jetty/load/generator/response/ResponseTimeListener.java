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

package org.eclipse.jetty.load.generator.response;

/**
 *
 */
public interface ResponseTimeListener
{
    /**
     * triggered with an response time value
     * @param path the http path
     * @param responseTime the response time value in nano seconds
     */
    // FIXME add HTTP method, size (a bean with values)
    void onResponse( String path, long responseTime );

    /**
     * triggered when the load generator is stopped
     */
    void onLoadGeneratorStop();

}
