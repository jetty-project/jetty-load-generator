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
import java.io.InterruptedIOException;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.ObjectName;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.http2.server.HTTP2CServerConnectionFactory;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
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

    private void startServer(Handler handler) throws Exception {
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
        startServer(new TestHandler());

        LoadGenerator loadGenerator = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .build();
        loadGenerator.begin().get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testSlowServer() throws Exception {
        int iterations = 1;
        CountDownLatch serverLatch = new CountDownLatch(iterations);
        long delay = 500;
        startServer(new AbstractHandler() {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request jettyRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
                try {
                    jettyRequest.setHandled(true);
                    Thread.sleep(delay);
                    serverLatch.countDown();
                } catch (InterruptedException x) {
                    throw new InterruptedIOException();
                }
            }
        });

        long start = System.nanoTime();
        AtomicLong beginTimeStamp = new AtomicLong();
        AtomicLong endTimeStamp = new AtomicLong();
        AtomicLong completeTimeStamp = new AtomicLong();
        LoadGenerator loadGenerator = LoadGenerator.builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .warmupIterationsPerThread(0)
                .iterationsPerThread(iterations)
                .listener((LoadGenerator.BeginListener)g -> beginTimeStamp.set(System.nanoTime()))
                .listener((LoadGenerator.EndListener)g -> endTimeStamp.set(System.nanoTime()))
                .listener((LoadGenerator.CompleteListener)g -> completeTimeStamp.set(System.nanoTime()))
                .build();
        CompletableFuture<Void> complete = loadGenerator.begin();

        complete.get(5, TimeUnit.SECONDS);
        // Make sure the completion happened after the server completed.
        Assert.assertTrue(serverLatch.await(5, TimeUnit.SECONDS));
        // Verify timings.
        Assert.assertTrue(start < beginTimeStamp.get());
        Assert.assertTrue(beginTimeStamp.get() < endTimeStamp.get());
        Assert.assertTrue(endTimeStamp.get() + delay / 2 < completeTimeStamp.get());
    }

    @Test
    public void testMultipleThreads() throws Exception {
        startServer(new TestHandler());

        Set<String> threads = Collections.newSetFromMap(new ConcurrentHashMap<>());
        LoadGenerator loadGenerator = LoadGenerator.builder()
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
        startServer(new TestHandler());

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
    public void testInterruptAfterResourceComplete() throws Exception {
        startServer(new AbstractHandler() {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request jettyRequest, HttpServletRequest request, HttpServletResponse response) {
                jettyRequest.setHandled(true);
            }
        });

        LoadGenerator.Builder config = new LoadGenerator.Builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .iterationsPerThread(0)
                .resourceListener((Resource.NodeListener)info -> {
                    info.getLoadGenerator().interrupt();
                });
        LoadGenerator loadGenerator = new LoadGenerator(config) {
            @Override
            boolean isInterrupted() {
                try {
                    Thread.sleep(1000);
                    return super.isInterrupted();
                } catch (InterruptedException x) {
                    return true;
                }
            }
        };

        CompletableFuture<Void> cf = loadGenerator.begin();

        cf.handle((r, x) -> {
            Assert.assertNotNull(x);
            return r;
        }).get(5, TimeUnit.SECONDS);
    }

    @Test
    public void testRunFor() throws Exception {
        startServer(new TestHandler());

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
        startServer(new TestHandler());

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

        Assert.assertEquals("/,/1,/11,/", String.join(",", resources));
        Assert.assertTrue(infos.stream().allMatch(info -> info.getStatus() == 200));
    }

    @Test
    public void testResourceGroup() throws Exception {
        startServer(new TestHandler());

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

        Assert.assertEquals("/1,<group>", String.join(",", resources));
    }

    @Test
    public void testWarmupDoesNotNotifyResourceListeners() throws Exception {
        startServer(new TestHandler());

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
        startServer(new TestHandler());

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
        startServer(new TestHandler());

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

        // Needs empty arrays otherwise Java 11 throws NPE.
        mbeanContainer.getMBeanServer().invoke(objectName, "interrupt", new Object[0], new String[0]);

        cf.handle((r, x) -> {
            // Load generation was interrupted.
            Assert.assertNotNull(x);
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
        startServer(new TestHandler());

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
        startServer(new TestHandler());

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

    @Test
    public void testSomeRequestFailure() throws Exception {
        startServer(new AbstractHandler() {
            private final AtomicInteger requests = new AtomicInteger();

            @Override
            public void handle(String target, org.eclipse.jetty.server.Request jettyRequest, HttpServletRequest request, HttpServletResponse response) {
                jettyRequest.setHandled(true);
                if (requests.incrementAndGet() == 2) {
                    // Fail only the second request.
                    jettyRequest.getHttpChannel().abort(new IOException());
                }
            }
        });

        int count = 3;
        AtomicInteger resources = new AtomicInteger();
        AtomicInteger failures = new AtomicInteger();
        LoadGenerator loadGenerator = LoadGenerator.builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .threads(1)
                .iterationsPerThread(count)
                // Allow enough time for requests to be sequential.
                .resourceRate(2)
                .resourceListener((Resource.NodeListener)info -> {
                    resources.incrementAndGet();
                    if (info.getFailure() != null) {
                        failures.incrementAndGet();
                    }
                })
                .build();

        loadGenerator.begin().get();

        Assert.assertEquals(count, resources.get());
        Assert.assertEquals(1, failures.get());
    }

    @Test
    public void testReadyEvent() throws Exception {
        startServer(new TestHandler());

        int threads = 2;
        int count = 30;
        AtomicInteger resources = new AtomicInteger();
        CountDownLatch readyLatch = new CountDownLatch(1);
        LoadGenerator loadGenerator = LoadGenerator.builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .threads(threads)
                .warmupIterationsPerThread(20)
                .iterationsPerThread(count)
                .resourceRate(1000)
                .listener((LoadGenerator.ReadyListener)g -> {
                    try {
                        Thread.sleep(1000);
                        // Make sure no request has been sent.
                        if (resources.get() > 0) {
                            g.interrupt();
                        }
                        readyLatch.countDown();
                    } catch (InterruptedException x) {
                        x.printStackTrace();
                    }
                })
                .resourceListener((Resource.NodeListener)info -> {
                    if (readyLatch.getCount() > 0) {
                        info.getLoadGenerator().interrupt();
                    }
                    resources.incrementAndGet();
                })
                .build();

        loadGenerator.begin().get();

        Assert.assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        Assert.assertEquals(threads * count, resources.get());
    }

    @Test
    public void testReadyEventWithoutWarmup() throws Exception {
        startServer(new TestHandler());

        int threads = 2;
        int count = 30;
        CountDownLatch readyLatch = new CountDownLatch(1);
        LoadGenerator loadGenerator = LoadGenerator.builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .threads(threads)
                .iterationsPerThread(count)
                .resourceRate(0)
                .listener((LoadGenerator.ReadyListener)g -> readyLatch.countDown())
                .build();

        loadGenerator.begin().get();

        Assert.assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testResourceFromRequestAttribute() throws Exception {
        Resource resource = new Resource("/original");
        String extraPath = "/" + Integer.toHexString(resource.hashCode());
        startServer(new AbstractHandler() {
            @Override
            public void handle(String target, org.eclipse.jetty.server.Request jettyRequest, HttpServletRequest request, HttpServletResponse response) {
                jettyRequest.setHandled(true);
                response.setStatus(target.endsWith(extraPath) ? HttpStatus.OK_200 : HttpStatus.INTERNAL_SERVER_ERROR_500);
            }
        });

        CountDownLatch latch = new CountDownLatch(1);
        LoadGenerator loadGenerator = LoadGenerator.builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resource(resource)
                .requestListener(new Request.Listener() {
                    @Override
                    public void onBegin(Request request) {
                        Resource resource = (Resource)request.getAttributes().get(Resource.class.getName());
                        request.path(resource.getPath() + extraPath);
                    }
                })
                .resourceListener((Resource.NodeListener)info -> {
                    if (info.getStatus() == HttpStatus.OK_200) {
                        latch.countDown();
                    }
                })
                .build();

        loadGenerator.begin().get();

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    @Test
    public void testServerSlowOnFirstIterationFastOnLastIteration() throws Exception {
        int resourceRate = 3;
        startServer(new AbstractHandler() {
            private final AtomicInteger requests = new AtomicInteger();

            @Override
            public void handle(String target, org.eclipse.jetty.server.Request jettyRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
                jettyRequest.setHandled(true);
                if (requests.incrementAndGet() == 1) {
                    try {
                        Thread.sleep(4 * 1000 / resourceRate);
                    } catch (InterruptedException x) {
                        throw new InterruptedIOException();
                    }
                }
            }
        });

        int requestCount = 2;
        CountDownLatch latch = new CountDownLatch(requestCount);
        LoadGenerator loadGenerator = LoadGenerator.builder()
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resourceRate(resourceRate)
                .threads(1)
                .iterationsPerThread(requestCount)
                .resourceListener((Resource.NodeListener)info -> {
                    if (info.getStatus() == HttpStatus.OK_200) {
                        latch.countDown();
                    }
                })
                .build();

        loadGenerator.begin().get();

        Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

    private enum TransportType {
        H1C, H2C
    }
}
