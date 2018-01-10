//
//  ========================================================================
//  Copyright (c) 1995-2018 Mort Bay Consulting Pty. Ltd.
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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class HTTP2LoadGeneratorTest {
    private Server server;
    private ServerConnector connector;

    private void prepare(Handler handler) throws Exception {
        server = new Server();
        connector = new ServerConnector(server, new HTTP2CServerConnectionFactory(new HttpConfiguration()));
        server.addConnector(connector);
        server.setHandler(handler);
        server.start();
    }

    @After
    public void dispose() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void testPush() throws Exception {
        prepare(new TestHandler() {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request jettyRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if ("/".equals(target)) {
                    jettyRequest.getPushBuilder()
                            .path("/1")
                            .setHeader(Resource.RESPONSE_LENGTH, String.valueOf(10 * 1024))
                            .push();
                    jettyRequest.getPushBuilder()
                            .path("/2")
                            .setHeader(Resource.RESPONSE_LENGTH, String.valueOf(32 * 1024))
                            .push();
                }
                super.handle(target, jettyRequest, request, response);
            }
        });

        AtomicLong requests = new AtomicLong();
        AtomicLong sent = new AtomicLong();
        AtomicLong pushed = new AtomicLong();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .httpClientTransportBuilder(new HTTP2ClientTransportBuilder())
                .port(connector.getLocalPort())
                .resource(new Resource("/", new Resource("/1"), new Resource("/2")).responseLength(128 * 1024))
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        requests.incrementAndGet();
                    }
                })
                .resourceListener((Resource.NodeListener)info -> {
                    if (info.isPushed()) {
                        pushed.incrementAndGet();
                    } else {
                        sent.incrementAndGet();
                    }
                })
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(1, requests.get());
        Assert.assertEquals(1, sent.get());
        Assert.assertEquals(2, pushed.get());
    }
}
