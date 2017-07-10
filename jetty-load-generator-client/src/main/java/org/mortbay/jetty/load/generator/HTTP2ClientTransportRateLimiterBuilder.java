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
import org.eclipse.jetty.http2.api.Session;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpChannelOverHTTP2;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.http2.client.http.HttpConnectionOverHTTP2;

/**
 * Helper builder to provide an http2 {@link HttpClientTransport}
 */
public class HTTP2ClientTransportRateLimiterBuilder
    implements HTTPClientTransportBuilder {
    private int selectors = 1;
    private int sessionRecvWindow = 16 * 1024 * 1024;
    private int streamRecvWindow = 16 * 1024 * 1024;

    private int resourceRate;

    public HTTP2ClientTransportRateLimiterBuilder( int resourceRate )
    {
        this.resourceRate = resourceRate;
    }

    public HTTP2ClientTransportRateLimiterBuilder selectors( int selectors) {
        this.selectors = selectors;
        return this;
    }

    public int getSelectors() {
        return selectors;
    }

    public HTTP2ClientTransportRateLimiterBuilder sessionRecvWindow( int sessionRecvWindow) {
        this.sessionRecvWindow = sessionRecvWindow;
        return this;
    }

    public int getSessionRecvWindow() {
        return sessionRecvWindow;
    }

    public HTTP2ClientTransportRateLimiterBuilder streamRecvWindow( int streamRecvWindow) {
        this.streamRecvWindow = streamRecvWindow;
        return this;
    }

    public int getStreamRecvWindow() {
        return streamRecvWindow;
    }

    @Override
    public HttpClientTransport build() {
        HTTP2Client http2Client = new HTTP2Client();
        // Chrome uses 15 MiB session and 6 MiB stream windows.
        // Firefox uses 12 MiB session and stream windows.
        http2Client.setInitialSessionRecvWindow(getSessionRecvWindow());
        http2Client.setInitialStreamRecvWindow(getStreamRecvWindow());
        http2Client.setSelectors(getSelectors());
        final RateLimiter rateLimiter = RateLimiter.create( resourceRate );
        return new HttpClientTransportOverHTTP2RateLimiter(http2Client, rateLimiter);
    }

    private static class HttpClientTransportOverHTTP2RateLimiter extends HttpClientTransportOverHTTP2
    {
        private final RateLimiter rateLimiter;

        public HttpClientTransportOverHTTP2RateLimiter( HTTP2Client client, RateLimiter rateLimiter )
        {
            super( client );
            this.rateLimiter = rateLimiter;
        }

        @Override
        protected HttpConnectionOverHTTP2 newHttpConnection( HttpDestination destination, Session session )
        {
            return super.newHttpConnection( destination, session );
        }
    }


    private static class HttpConnectionOverHTTP2RateLimiter extends HttpConnectionOverHTTP2
    {
        private final RateLimiter rateLimiter;

        public HttpConnectionOverHTTP2RateLimiter( HttpDestination destination, Session session,
                                                   RateLimiter rateLimiter )
        {
            super( destination, session );
            this.rateLimiter = rateLimiter;
        }

        @Override
        protected HttpChannelOverHTTP2RateLimiter newHttpChannel( boolean push )
        {
            return new HttpChannelOverHTTP2RateLimiter(  getHttpDestination(), this,
                                                    getSession(),  push, rateLimiter );
        }
    }

    private static class HttpChannelOverHTTP2RateLimiter extends HttpChannelOverHTTP2
    {
        private final RateLimiter rateLimiter;

        public HttpChannelOverHTTP2RateLimiter( HttpDestination destination, HttpConnectionOverHTTP2 connection,
                                                Session session, boolean push, RateLimiter rateLimiter )
        {
            super( destination, connection, session, push );
            this.rateLimiter = rateLimiter;
        }

        @Override
        public void send()
        {
            rateLimiter.acquire();
            super.send();
        }
    }
}
