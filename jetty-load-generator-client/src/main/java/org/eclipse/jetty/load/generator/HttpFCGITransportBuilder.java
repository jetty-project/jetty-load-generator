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

import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.fcgi.client.http.HttpClientTransportOverFCGI;

/**
 * Helper builder to provide an http2 {@link HttpClientTransport}
 */
public class HttpFCGITransportBuilder
{

    private int selectors = 1;

    private long stopTimeout;

    private boolean multiplexed;

    private String scriptRoot;

    public HttpFCGITransportBuilder()
    {
        // no op
    }

    public HttpFCGITransportBuilder selectors( int selectors )
    {
        this.selectors = selectors;
        return this;
    }

    public HttpFCGITransportBuilder stopTimeout( long stopTimeout )
    {
        this.stopTimeout = stopTimeout;
        return this;
    }

    public HttpFCGITransportBuilder multiplexed( boolean multiplexed )
    {
        this.multiplexed = multiplexed;
        return this;
    }

    public HttpFCGITransportBuilder scriptRoot( String scriptRoot )
    {
        this.scriptRoot = scriptRoot;
        return this;
    }

    public HttpClientTransport build()
    {
        HttpClientTransportOverFCGI httpClientTransport = //
            new HttpClientTransportOverFCGI( selectors, multiplexed, scriptRoot );

        httpClientTransport.setStopTimeout( stopTimeout );

        return httpClientTransport;
    }

}
