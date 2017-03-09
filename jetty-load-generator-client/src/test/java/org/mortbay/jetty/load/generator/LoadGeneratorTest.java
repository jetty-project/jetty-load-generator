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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mortbay.jetty.load.generator.resource.Resource;

@RunWith(Parameterized.class)
public class LoadGeneratorTest {
    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> parameters() {
        List<Object[]> result = new ArrayList<>();
        result.add(new Object[]{new HttpConnectionFactory(), new Http1ClientTransportBuilder()});
        result.add(new Object[]{new HTTP2CServerConnectionFactory(new HttpConfiguration()), new Http2ClientTransportBuilder()});
        return result;
    }

    private final ConnectionFactory connectionFactory;
    private final HttpClientTransportBuilder clientTransportBuilder;
    private Server server;
    private ServerConnector connector;

    public LoadGeneratorTest(ConnectionFactory connectionFactory, HttpClientTransportBuilder clientTransportBuilder) {
        this.connectionFactory = connectionFactory;
        this.clientTransportBuilder = clientTransportBuilder;
    }

    private void prepare(Handler handler) throws Exception {
        server = new Server();
        connector = new ServerConnector(server, connectionFactory);
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
    public void testDefaultConfiguration() throws Exception {
        prepare(new TestHandler());

        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testMultipleThreads() throws Exception {
        prepare(new TestHandler());

        Set<String> threads = Collections.newSetFromMap(new ConcurrentHashMap<>());
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .threads(2)
                .iterationsPerThread(1)
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        threads.add(Thread.currentThread().getName());
                    }
                })
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);
        Assert.assertEquals(2, threads.size());
    }

    @Test
    public void testInterrupt() throws Exception {
        prepare(new TestHandler());

        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                // Iterate forever.
                .iterationsPerThread(0)
                .resourceRate(5)
                .build();
        CompletableFuture<Void> cf = loadGenerator.begin();

        Thread.sleep(1000);

        loadGenerator.interrupt();

        cf.handle((r, x) -> {
            Throwable cause = x.getCause();
            if (cause instanceof InterruptedException) {
                return null;
            } else {
                throw new CompletionException(cause);
            }
        }).get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testRunFor() throws Exception {
        prepare(new TestHandler());

        long time = 2;
        TimeUnit unit = TimeUnit.SECONDS;
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .runFor(time, unit)
                .resourceRate(5)
                .build();
        loadGenerator.begin().get(2 * time, unit);
    }

    @Test
    public void testResourceTree() throws Exception {
        prepare(new TestHandler());

        Queue<String> resources = new ConcurrentLinkedDeque<>();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resource(new Resource("/",
                        new Resource("/1",
                                new Resource("/11").responseLength(1024))
                                .responseLength(10 * 1024))
                        .responseLength(16 * 1024))
                .resourceListener((Resource.NodeListener)info -> resources.offer(info.getResource().getPath()))
                .resourceListener((Resource.TreeListener)info -> resources.offer(info.getResource().getPath()))
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals("/,/1,/11,/", resources.stream().collect(Collectors.joining(",")));
    }

    @Test
    public void testResourceGroup() throws Exception {
        prepare(new TestHandler());

        Queue<String> resources = new ConcurrentLinkedDeque<>();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resource(new Resource(new Resource("/1").responseLength(10 * 1024)))
                .resourceListener((Resource.NodeListener)info -> resources.offer(info.getResource().getPath()))
                .resourceListener((Resource.TreeListener)info -> {
                    if (info.getResource().getPath() == null) {
                        if (resources.size() == 1) {
                            resources.offer("<group>");
                        }
                    }
                })
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals("/1,<group>", resources.stream().collect(Collectors.joining(",")));
    }
}
