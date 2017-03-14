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
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;

/**
 * Helper builder to provide an http(s) {@link HttpClientTransport}
 */
public class HTTP1ClientTransportBuilder implements HTTPClientTransportBuilder {
    private int selectors = 1;

    public HTTP1ClientTransportBuilder selectors(int selectors) {
        this.selectors = selectors;
        return this;
    }

    public int getSelectors() {
        return selectors;
    }

    @Override
    public HttpClientTransport build() {
        return new HttpClientTransportOverHTTP(getSelectors());
    }
}
