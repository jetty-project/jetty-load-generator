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

import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;

/**
 * Helper builder to provide an http2 {@link HttpClientTransport}
 */
public class HTTP2ClientTransportBuilder implements HTTPClientTransportBuilder {
    private int selectors = 1;
    private int sessionRecvWindow = 16 * 1024 * 1024;
    private int streamRecvWindow = 16 * 1024 * 1024;

    public HTTP2ClientTransportBuilder selectors(int selectors) {
        this.selectors = selectors;
        return this;
    }

    public int getSelectors() {
        return selectors;
    }

    public HTTP2ClientTransportBuilder sessionRecvWindow(int sessionRecvWindow) {
        this.sessionRecvWindow = sessionRecvWindow;
        return this;
    }

    public int getSessionRecvWindow() {
        return sessionRecvWindow;
    }

    public HTTP2ClientTransportBuilder streamRecvWindow(int streamRecvWindow) {
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
        return new HttpClientTransportOverHTTP2(http2Client);
    }
}
