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

import java.util.Map;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * <p>A builder for {@link HttpClientTransport}.</p>
 */
public abstract class HTTPClientTransportBuilder implements JSON.Convertible {
    protected int selectors;
    protected ClientConnector connector;

    /**
     * @param selectors the number of NIO selectors
     * @return this builder instance
     */
    public HTTPClientTransportBuilder selectors(int selectors) {
        this.selectors = selectors;
        return this;
    }

    public int getSelectors() {
        return selectors;
    }

    public HTTPClientTransportBuilder connector(ClientConnector connector) {
        this.connector = connector;
        return this;
    }

    public ClientConnector getConnector() {
        return connector;
    }

    /**
     * @return the transport type, such as "http/1.1" or "http/2"
     */
    public abstract String getType();

    /**
     * @return a new HttpClientTransport instance
     */
    public HttpClientTransport build() {
        ClientConnector connector = getConnector();
        if (connector == null) {
            connector = new ClientConnector();
        }
        int selectors = getSelectors();
        if (selectors > 0) {
            connector.setSelectors(selectors);
        }
        return newHttpClientTransport(connector);
    }

    protected abstract HttpClientTransport newHttpClientTransport(ClientConnector connector);

    @Override
    public void toJSON(JSON.Output out) {
        out.add("type", getType());
        out.add("selectors", getSelectors());
    }

    @Override
    public void fromJSON(Map<String, Object> map) {
        selectors = LoadGenerator.Config.asInt(map, "selectors");
    }
}
