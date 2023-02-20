//
// ========================================================================
// Copyright (c) 2016-2022 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under the
// terms of the Eclipse Public License v. 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
// which is available at https://www.apache.org/licenses/LICENSE-2.0.
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.mortbay.jetty.load.generator;

import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.io.ClientConnector;

/**
 * <p>Helper builder to provide an http(s) {@link HttpClientTransport}.</p>
 */
public class HTTP1ClientTransportBuilder extends HTTPClientTransportBuilder {
    public static final String TYPE = "http/1.1";

    @Override
    public HTTP1ClientTransportBuilder selectors(int selectors) {
        super.selectors(selectors);
        return this;
    }

    @Override
    public HTTP1ClientTransportBuilder connector(ClientConnector connector) {
        super.connector(connector);
        return this;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    protected HttpClientTransport newHttpClientTransport(ClientConnector connector) {
        return new HttpClientTransportOverHTTP(connector);
    }
}
