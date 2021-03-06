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
import org.eclipse.jetty.util.ajax.JSON;

/**
 * <p>A builder for {@link HttpClientTransport}.</p>
 */
public interface HTTPClientTransportBuilder extends JSON.Convertible {
    /**
     * @return the transport type, such as "http/1.1" or "http/2"
     */
    public String getType();

    /**
     * @return a new HttpClientTransport instance
     */
    public HttpClientTransport build();
}
