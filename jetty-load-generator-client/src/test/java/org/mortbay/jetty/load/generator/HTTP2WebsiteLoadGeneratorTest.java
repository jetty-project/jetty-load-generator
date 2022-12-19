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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.HdrHistogram.AtomicHistogram;
import org.HdrHistogram.Histogram;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpURI;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.http.MetaData;
import org.eclipse.jetty.http2.server.HTTP2ServerConnectionFactory;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.toolchain.perf.HistogramSnapshot;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.thread.MonitoredQueuedThreadPool;
import org.junit.Assert;
import org.junit.Test;

public class HTTP2WebsiteLoadGeneratorTest extends WebsiteLoadGeneratorTest {
    @Test
    public void testHTTP2WithoutPush() throws Exception {
        prepareServer(new HTTP2ServerConnectionFactory(new HttpConfiguration()), new TestHandler());
        testHTTP2();
    }

    @Test
    public void testHTTP2WithPush() throws Exception {
        prepareServer(new HTTP2ServerConnectionFactory(new HttpConfiguration()), new PushingHandler());
        testHTTP2();
    }

    private void testHTTP2() throws Exception {
        MonitoredQueuedThreadPool executor = new MonitoredQueuedThreadPool(1024);
        executor.start();

        AtomicLong requests = new AtomicLong();
        Histogram treeHistogram = new AtomicHistogram(TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(10), 3);
        Histogram rootHistogram = new AtomicHistogram(TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(10), 3);
        LoadGenerator loadGenerator = prepareLoadGenerator(new HTTP2ClientTransportBuilder())
                .warmupIterationsPerThread(10)
                .iterationsPerThread(100)
//                .warmupIterationsPerThread(1000)
//                .runFor(2, TimeUnit.MINUTES)
                .usersPerThread(100)
                .channelsPerUser(1000)
                .resourceRate(20)
                .executor(executor)
                .resourceListener((Resource.TreeListener)info -> {
                    rootHistogram.recordValue(info.getResponseTime() - info.getRequestTime());
                    treeHistogram.recordValue(info.getTreeTime() - info.getRequestTime());
                })
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onQueued(Request request) {
                        requests.incrementAndGet();
                    }
                })
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onBegin(Request request) {
                        requests.decrementAndGet();
                    }
                })
                .build();

        long begin = System.nanoTime();
        loadGenerator.begin().join();
        long elapsed = System.nanoTime() - begin;

        Assert.assertEquals(0, requests.get());

        int serverRequests = serverStats.getRequests();
        System.err.printf("%nserver - requests: %d, rate: %.3f, max_request_time: %d%n%n",
                serverRequests,
                elapsed > 0 ? serverRequests * 1_000_000_000F / elapsed : 0F,
                TimeUnit.NANOSECONDS.toMillis(serverStats.getRequestTimeMax()));

        HistogramSnapshot treeSnapshot = new HistogramSnapshot(treeHistogram, 20, "tree response time", "us", TimeUnit.NANOSECONDS::toMicros);
        System.err.println(treeSnapshot);
        HistogramSnapshot rootSnapshot = new HistogramSnapshot(rootHistogram, 20, "root response time", "us", TimeUnit.NANOSECONDS::toMicros);
        System.err.println(rootSnapshot);

        System.err.printf("client thread pool - max_threads: %d, max_queue_size: %d, max_queue_latency: %dms%n%n",
                executor.getMaxBusyThreads(),
                executor.getMaxQueueSize(),
                TimeUnit.NANOSECONDS.toMillis(executor.getMaxQueueLatency())
        );

        executor.stop();
    }

    private class PushingHandler extends TestHandler {
        @Override
        public boolean process(org.eclipse.jetty.server.Request request, Response response, Callback callback) {
            if ("/".equals(org.eclipse.jetty.server.Request.getPathInContext(request))) {
                for (Resource resource : resource.getResources()) {
                    MetaData.Request push = new MetaData.Request(
                            HttpMethod.GET.asString(),
                            HttpURI.build(request.getHttpURI()).path(resource.getPath()),
                            HttpVersion.HTTP_2,
                            HttpFields.build().put(Resource.RESPONSE_LENGTH, Long.toString(resource.getResponseLength()))
                    );
                    request.push(push);
                }
            }
            return super.process(request, response, callback);
        }
    }
}
