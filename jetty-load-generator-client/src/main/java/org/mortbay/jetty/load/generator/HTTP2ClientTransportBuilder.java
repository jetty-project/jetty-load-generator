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

import java.util.Map;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.http2.client.HTTP2Client;
import org.eclipse.jetty.http2.client.http.HttpClientTransportOverHTTP2;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * <p>Helper builder to provide an HTTP/2 {@link HttpClientTransport}.</p>
 */
public class HTTP2ClientTransportBuilder implements HTTPClientTransportBuilder {
    public static final String TYPE = "http/2";

    private int selectors = 1;
    private int sessionRecvWindow = 16 * 1024 * 1024;
    private int streamRecvWindow = 16 * 1024 * 1024;

    /**
     * @param selectors the number of NIO selectors
     * @return this builder instance
     */
    public HTTP2ClientTransportBuilder selectors(int selectors) {
        this.selectors = selectors;
        return this;
    }

    public int getSelectors() {
        return selectors;
    }

    /**
     * @param sessionRecvWindow the HTTP/2 session flow control receive window
     * @return this builder instance
     */
    public HTTP2ClientTransportBuilder sessionRecvWindow(int sessionRecvWindow) {
        this.sessionRecvWindow = sessionRecvWindow;
        return this;
    }

    public int getSessionRecvWindow() {
        return sessionRecvWindow;
    }

    /**
     * @param streamRecvWindow the HTTP/2 stream flow control receive window
     * @return this builder instance
     */
    public HTTP2ClientTransportBuilder streamRecvWindow(int streamRecvWindow) {
        this.streamRecvWindow = streamRecvWindow;
        return this;
    }

    public int getStreamRecvWindow() {
        return streamRecvWindow;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    @Override
    public HttpClientTransport build() {
        HTTP2Client http2Client = new HTTP2Client();
        http2Client.setInitialSessionRecvWindow(getSessionRecvWindow());
        http2Client.setInitialStreamRecvWindow(getStreamRecvWindow());
        http2Client.setSelectors(getSelectors());
        return new HttpClientTransportOverHTTP2(http2Client);
    }

    @Override
    public void toJSON(JSON.Output out) {
        out.add("type", getType());
        out.add("selectors", getSelectors());
        out.add("sessionRecvWindow", getSessionRecvWindow());
        out.add("streamRecvWindow", getStreamRecvWindow());
    }

    @Override
    public void fromJSON(Map map) {
        selectors = LoadGenerator.Config.asInt(map, "selectors");
        sessionRecvWindow = LoadGenerator.Config.asInt(map, "sessionRecvWindow");
        streamRecvWindow = LoadGenerator.Config.asInt(map, "streamRecvWindow");
    }
}
