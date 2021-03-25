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

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.alpn.server.ALPNServerConnectionFactory;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;

public class HTTP2LoadGeneratorTest {
    private Server server;
    private ServerConnector connector;
    private ServerConnector tlsConnector;

    private void startServer(Handler handler) throws Exception {
        server = new Server();

        HttpConfiguration httpConfig = new HttpConfiguration();
        connector = new ServerConnector(server, 1, 1, new HTTP2CServerConnectionFactory(httpConfig));
        server.addConnector(connector);

        HttpConfiguration httpsConfig = new HttpConfiguration(httpConfig);
        httpsConfig.addCustomizer(new SecureRequestCustomizer());
        HTTP2ServerConnectionFactory h2 = new HTTP2ServerConnectionFactory(httpConfig);
        ALPNServerConnectionFactory alpn = new ALPNServerConnectionFactory();
        alpn.setDefaultProtocol(h2.getProtocol());
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        sslContextFactory.setKeyStorePath("src/test/resources/keystore.p12");
        sslContextFactory.setKeyStoreType("pkcs12");
        sslContextFactory.setKeyStorePassword("storepwd");
        SslConnectionFactory tls = new SslConnectionFactory(sslContextFactory, alpn.getProtocol());
        tlsConnector = new ServerConnector(server, 1, 1, tls, alpn, h2);
        server.addConnector(tlsConnector);

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
    public void testHTTP2() throws Exception {
        startServer(new TestHandler());

        AtomicLong responses = new AtomicLong();
        LoadGenerator loadGenerator = LoadGenerator.builder()
                .scheme("https")
                .port(tlsConnector.getLocalPort())
                .sslContextFactory(new SslContextFactory.Client(true))
                .httpClientTransportBuilder(new HTTP2ClientTransportBuilder())
                .resource(new Resource("/", new Resource("/1"), new Resource("/2")).responseLength(128 * 1024))
                .resourceListener((Resource.NodeListener)info -> {
                    if (info.getStatus() == HttpStatus.OK_200) {
                        responses.incrementAndGet();
                    }
                })
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(3, responses.get());
    }

    @Test
    public void testPush() throws Exception {
        startServer(new TestHandler() {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request jettyRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
                if ("/".equals(target)) {
                    request.newPushBuilder()
                            .path("/1")
                            .setHeader(Resource.RESPONSE_LENGTH, String.valueOf(10 * 1024))
                            .push();
                    request.newPushBuilder()
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
