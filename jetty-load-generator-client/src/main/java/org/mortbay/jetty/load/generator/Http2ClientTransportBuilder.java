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
public class Http2ClientTransportBuilder implements HttpClientTransportBuilder {
    private int selectors = 1;

    public Http2ClientTransportBuilder selectors(int selectors) {
        this.selectors = selectors;
        return this;
    }

    @Override
    public HttpClientTransport build() {
        HTTP2Client http2Client = new HTTP2Client();
        http2Client.setSelectors(selectors);
        return new HttpClientTransportOverHTTP2(http2Client);
    }
}
