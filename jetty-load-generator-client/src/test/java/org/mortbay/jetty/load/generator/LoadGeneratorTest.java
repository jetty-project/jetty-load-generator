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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import javax.management.ObjectName;

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LoadGeneratorTest {
    @Parameterized.Parameters(name = "{0}")
    public static Object[] parameters() {
        return TransportType.values();
    }

    private final ConnectionFactory connectionFactory;
    private final HTTPClientTransportBuilder clientTransportBuilder;
    private Server server;
    private ServerConnector connector;

    public LoadGeneratorTest(TransportType transportType) {
        switch (transportType) {
            case H1C:
                connectionFactory = new HttpConnectionFactory();
                clientTransportBuilder = new HTTP1ClientTransportBuilder();
                break;
            case H2C:
                connectionFactory = new HTTP2CServerConnectionFactory(new HttpConfiguration());
                clientTransportBuilder = new HTTP2ClientTransportBuilder();
                break;
            default:
                throw new IllegalArgumentException();
        }
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
        List<Resource.Info> infos = new ArrayList<>();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resource(new Resource("/",
                        new Resource("/1",
                                new Resource("/11").responseLength(1024))
                                .responseLength(10 * 1024))
                        .responseLength(16 * 1024))
                .resourceListener((Resource.NodeListener)info -> {
                    resources.offer(info.getResource().getPath());
                    infos.add(info);
                })
                .resourceListener((Resource.TreeListener)info -> resources.offer(info.getResource().getPath()))
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals("/,/1,/11,/", resources.stream().collect(Collectors.joining(",")));
        Assert.assertTrue(infos.stream().allMatch(info -> info.getStatus() == 200));
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

    @Test
    public void testWarmupDoesNotNotifyResourceListeners() throws Exception {
        prepare(new TestHandler());

        AtomicLong requests = new AtomicLong();
        AtomicLong resources = new AtomicLong();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .warmupIterationsPerThread(2)
                .iterationsPerThread(3)
                .resourceRate(5)
                .resource(new Resource("/").method("POST").responseLength(1024))
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        requests.incrementAndGet();
                    }
                })
                .resourceListener((Resource.NodeListener)info -> resources.incrementAndGet())
                .build();

        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(5, requests.get());
        Assert.assertEquals(3, resources.get());
    }

    @Test
    public void testTwoRuns() throws Exception {
        prepare(new TestHandler());

        AtomicLong requests = new AtomicLong();
        AtomicLong resources = new AtomicLong();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .iterationsPerThread(3)
                .resourceRate(5)
                .resource(new Resource("/").responseLength(1024))
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        requests.incrementAndGet();
                    }
                })
                .resourceListener((Resource.NodeListener)info -> resources.incrementAndGet())
                .build();

        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(3, requests.get());
        Assert.assertEquals(3, resources.get());

        requests.set(0);
        resources.set(0);
        loadGenerator.begin().get(5, TimeUnit.SECONDS);

        Assert.assertEquals(3, requests.get());
        Assert.assertEquals(3, resources.get());
    }

    @Test
    public void testJMX() throws Exception {
        prepare(new TestHandler());

        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                // Iterate forever.
                .iterationsPerThread(0)
                .resourceRate(5)
                .build();

        MBeanContainer mbeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
        loadGenerator.addBean(mbeanContainer);

        ObjectName pattern = new ObjectName(LoadGenerator.class.getPackage().getName() + ":*");
        Set<ObjectName> objectNames = mbeanContainer.getMBeanServer().queryNames(pattern, null);
        Assert.assertTrue(objectNames.size() > 0);
        Optional<ObjectName> objectNameOpt = objectNames.stream()
                .filter(o -> o.getKeyProperty("type").equalsIgnoreCase(LoadGenerator.class.getSimpleName()))
                .findAny();
        Assert.assertTrue(objectNameOpt.isPresent());
        ObjectName objectName = objectNameOpt.get();

        CompletableFuture<Void> cf = loadGenerator.begin();

        Thread.sleep(1000);

        mbeanContainer.getMBeanServer().invoke(objectName, "interrupt", null, null);

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
    public void testRateIsRespected() throws Exception {
        // Use a large resource rate to test that
        // sleep compensation works correctly.
        int rate = 2000;
        if (connectionFactory instanceof HTTP2CServerConnectionFactory) {
            ((HTTP2CServerConnectionFactory)connectionFactory).setMaxConcurrentStreams(rate);
        }
        prepare(new TestHandler());

        int iterations = 5 * rate;
        AtomicLong requests = new AtomicLong();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .iterationsPerThread(iterations)
                .resourceRate(rate)
                .socketAddressResolver(new SocketAddressResolver.Sync())
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        requests.incrementAndGet();
                    }
                })
                .build();

        long start = System.nanoTime();
        loadGenerator.begin().get(10 * rate, TimeUnit.MILLISECONDS);
        long elapsed = System.nanoTime() - start;
        long expected = TimeUnit.SECONDS.toNanos(iterations / rate);

        Assert.assertTrue(Math.abs(elapsed - expected) < expected / 10);
        Assert.assertEquals(iterations, requests.intValue());
    }

    @Test
    public void testRateRampUp() throws Exception {
        prepare(new TestHandler());

        int rate = 10;
        long ramp = 5;
        AtomicLong requests = new AtomicLong();
        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resourceRate(rate)
                .rateRampUpPeriod(ramp)
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        requests.incrementAndGet();
                    }
                })
                .runFor(ramp, TimeUnit.SECONDS)
                .build();

        loadGenerator.begin().get();

        // The number of unsent requests during ramp up is
        // half of the requests that would have been sent.
        long expected = rate * ramp / 2;
        Assert.assertTrue(expected - 1 <= requests.get());
        Assert.assertTrue(requests.get() <= expected + 1);
    }

    private enum TransportType {
        H1C, H2C
    }
}
