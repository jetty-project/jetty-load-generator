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
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.toolchain.perf.HistogramSnapshot;
import org.eclipse.jetty.util.thread.MonitoredQueuedThreadPool;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class HTTP1WebsiteLoadGeneratorTest extends WebsiteLoadGeneratorTest {
    @Before
    public void prepare() throws Exception {
        prepareServer(new HttpConnectionFactory(), new TestHandler());
    }

    @Test
    public void testHTTP1() throws Exception {
        MonitoredQueuedThreadPool executor = new MonitoredQueuedThreadPool(1024);
        executor.start();

        AtomicLong requests = new AtomicLong();
        Histogram treeHistogram = new AtomicHistogram(TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(10), 3);
        Histogram rootHistogram = new AtomicHistogram(TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(10), 3);
        LoadGenerator loadGenerator = prepareLoadGenerator(new HTTP1ClientTransportBuilder())
                .warmupIterationsPerThread(10)
                .iterationsPerThread(100)
//                .warmupIterationsPerThread(1000)
//                .runFor(2, TimeUnit.MINUTES)
                .usersPerThread(100)
                .channelsPerUser(6)
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

        serverStats.statsReset();
        loadGenerator.begin().join();
        long elapsed = serverStats.getStatsOnMs();

        Assert.assertEquals(0, requests.get());

        int serverRequests = serverStats.getRequests();
        System.err.printf("%nserver - requests: %d, rate: %.3f, max_request_time: %d%n%n",
                serverRequests,
                elapsed > 0 ? serverRequests * 1000F / elapsed : 0F,
                serverStats.getRequestTimeMax());

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
}
