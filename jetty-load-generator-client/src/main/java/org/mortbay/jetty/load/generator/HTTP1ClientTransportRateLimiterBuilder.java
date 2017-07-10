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

package org.mortbay.jetty.load.generator;

import com.google.common.util.concurrent.RateLimiter;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.HttpDestination;
import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.client.SendFailure;
import org.eclipse.jetty.client.api.Connection;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.client.http.HttpConnectionOverHTTP;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.util.Promise;

/**
 * Helper builder to provide an http(s) {@link HttpClientTransport}
 */
public class HTTP1ClientTransportRateLimiterBuilder
    implements HTTPClientTransportBuilder
{
    private int selectors = 1;

    private int resourceRate;

    public HTTP1ClientTransportRateLimiterBuilder( int resourceRate )
    {
        this.resourceRate = resourceRate;
    }

    public HTTP1ClientTransportRateLimiterBuilder selectors( int selectors )
    {
        this.selectors = selectors;
        return this;
    }

    public int getSelectors()
    {
        return selectors;
    }

    @Override
    public HttpClientTransport build()
    {
        RateLimiter rateLimiter = RateLimiter.create( resourceRate );
        return new HttpClientTransportOverHTTPRateLimiter( getSelectors(), rateLimiter );
    }


    private static class HttpClientTransportOverHTTPRateLimiter
        extends HttpClientTransportOverHTTP
    {
        private final RateLimiter rateLimiter;

        public HttpClientTransportOverHTTPRateLimiter( int selectors, RateLimiter rateLimiter )
        {
            super( selectors );
            this.rateLimiter = rateLimiter;
        }

        @Override
        protected HttpConnectionOverHTTP newHttpConnection( EndPoint endPoint, HttpDestination destination,
                                                            Promise<Connection> promise )
        {
            return new HttpConnectionOverHTTPRateLimiter( endPoint, destination, promise, rateLimiter );
        }
    }


    private static class HttpConnectionOverHTTPRateLimiter extends HttpConnectionOverHTTP
    {
        private final RateLimiter rateLimiter;

        public HttpConnectionOverHTTPRateLimiter( EndPoint endPoint, HttpDestination destination,
                                                  Promise<Connection> promise, RateLimiter rateLimiter )
        {
            super( endPoint, destination, promise );
            this.rateLimiter = rateLimiter;
        }

        @Override
        public void send( Request request, Response.CompleteListener listener )
        {
            rateLimiter.acquire();
            super.send( request, listener );
        }

        @Override
        protected SendFailure send( HttpExchange exchange )
        {
            double time = rateLimiter.acquire();
            return super.send( exchange );
        }
    }
}
