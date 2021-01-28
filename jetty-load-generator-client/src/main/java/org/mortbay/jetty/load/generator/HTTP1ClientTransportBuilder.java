//
// ========================================================================
// Copyright (c) 2016-2021 Mort Bay Consulting Pty Ltd and others.
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
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;

/**
 * <p>Helper builder to provide an http(s) {@link HttpClientTransport}.</p>
 */
public class HTTP1ClientTransportBuilder implements HTTPClientTransportBuilder {
    private int selectors = 1;

    /**
     * @param selectors the number of NIO selectors
     * @return this builder instance
     */
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
