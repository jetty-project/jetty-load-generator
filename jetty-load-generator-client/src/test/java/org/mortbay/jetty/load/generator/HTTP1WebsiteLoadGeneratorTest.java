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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.HdrHistogram.AtomicHistogram;
import org.HdrHistogram.Histogram;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.toolchain.perf.HistogramSnapshot;
import org.junit.Before;
import org.junit.Test;
import org.mortbay.jetty.load.generator.util.MonitoringThreadPoolExecutor;

public class HTTP1WebsiteLoadGeneratorTest extends WebsiteLoadGeneratorTest {
    @Before
    public void prepare() throws Exception {
        prepareServer(new HttpConnectionFactory(), new TestHandler());
    }

    @Test
    public void testHTTP1() throws Exception {
        MonitoringThreadPoolExecutor executor = new MonitoringThreadPoolExecutor( 1024, 60, TimeUnit.SECONDS);

        AtomicLong requests = new AtomicLong();
        Histogram treeHistogram = new AtomicHistogram(TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(10), 3);
        Histogram rootHistogram = new AtomicHistogram(TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.SECONDS.toNanos(10), 3);
        LoadGenerator loadGenerator = prepareLoadGenerator(new HTTP1ClientTransportBuilder())
                .warmupIterationsPerThread(10)
                .iterationsPerThread(100)
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

        loadGenerator.begin().join();

        HistogramSnapshot treeSnapshot = new HistogramSnapshot(treeHistogram, 20, "tree response time", "us", TimeUnit.NANOSECONDS::toMicros);
        System.err.println(treeSnapshot);
        HistogramSnapshot rootSnapshot = new HistogramSnapshot(rootHistogram, 20, "root response time", "us", TimeUnit.NANOSECONDS::toMicros);
        System.err.println(rootSnapshot);
        System.err.printf("queued requests: %d%n", requests.get());

        executor.shutdown();
    }
}
