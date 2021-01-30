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

package org.mortbay.jetty.load.generator.listeners;

import java.lang.management.ManagementFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

/**
 * <p>A load generator listener that reports information about a load run.</p>
 * <p>Usage:</p>
 * <pre>
 * // Create the report listener.
 * ReportListener listener = new ReportListener();
 *
 * // Create the LoadGenerator, passing the listener to relevant builder methods.
 * LoadGenerator generator = LoadGenerator.builder()
 *     ...
 *     .listener(listener)
 *     .requestListener(listener)
 *     .resourceListener(listener)
 *     .build();
 *
 * // Add the listener as a bean of the generator.
 * generator.addBean(listener);
 *
 * // Start the load generation and wait to finish.
 * generator.begin().join();
 *
 * // Now you can access the data in the listener.
 * System.err.printf("max response time: %d", listener.getResponseTimeHistogram().getMaxValue());
 * </pre>
 */
public class ReportListener extends ContainerLifeCycle implements LoadGenerator.BeginListener, LoadGenerator.EndListener, LoadGenerator.CompleteListener, Request.Listener, Resource.NodeListener, Connection.Listener {
    private final ConnectionStatistics connectionStats = new ConnectionStatistics();
    private final Recorder recorder;
    private final AtomicReference<Histogram> histogram = new AtomicReference<>();
    private final LongAdder requestCount = new LongAdder();
    private final LongAdder responses1xx = new LongAdder();
    private final LongAdder responses2xx = new LongAdder();
    private final LongAdder responses3xx = new LongAdder();
    private final LongAdder responses4xx = new LongAdder();
    private final LongAdder responses5xx = new LongAdder();
    private final LongAdder responseContent = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private volatile long beginTime;
    private volatile long endTime;
    private volatile long completeTime;
    private volatile long beginCPUTime;
    private volatile long completeCPUTime;

    /**
     * <p>Creates a report listener that records values between 1 microsecond and 1 minute with 3 digit precision.</p>
     */
    public ReportListener() {
        this(TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MINUTES.toNanos(1), 3);
    }

    /**
     * <p>Creates a report listener that record values between the {@code lowestDiscernibleValue}
     * and {@code highestTrackableValue} with {@code numberOfSignificantValueDigits} precision.
     *
     * @param lowestDiscernibleValue the minimum value recorded
     * @param highestTrackableValue the maximum value recorded
     * @param numberOfSignificantValueDigits the number of significant digits.
     * @see Histogram
     */
    public ReportListener(long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
        recorder = new Recorder(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
        addBean(connectionStats);
    }

    @Override
    public void onBegin(LoadGenerator generator) {
        beginTime = System.nanoTime();
        beginCPUTime = getProcessCPUTime();
    }

    @Override
    public void onEnd(LoadGenerator generator) {
        endTime = System.nanoTime();
    }

    @Override
    public void onComplete(LoadGenerator generator) {
        completeTime = System.nanoTime();
        completeCPUTime = getProcessCPUTime();
    }

    @Override
    public void onBegin(Request request) {
        requestCount.increment();
    }

    @Override
    public void onResourceNode(Resource.Info info) {
        if (info.getFailure() == null) {
            recordResponseGroup(info);
            long responseTime = info.getResponseTime() - info.getRequestTime();
            recorder.recordValue(responseTime);
            responseContent.add(info.getContentLength());
        } else {
            failures.increment();
        }
    }

    @Override
    public void onOpened(Connection connection) {
        connectionStats.onOpened(connection);
    }

    @Override
    public void onClosed(Connection connection) {
        connectionStats.onClosed(connection);
    }

    private void recordResponseGroup(Resource.Info info) {
        switch (info.getStatus() / 100) {
            case 1:
                responses1xx.increment();
                break;
            case 2:
                responses2xx.increment();
                break;
            case 3:
                responses3xx.increment();
                break;
            case 4:
                responses4xx.increment();
                break;
            case 5:
                responses5xx.increment();
                break;
            default:
                break;
        }
    }

    public Histogram getResponseTimeHistogram() {
        // The histogram is reset every time getIntervalHistogram() is called.
        return histogram.updateAndGet(h -> h != null ? h : recorder.getIntervalHistogram());
    }

    public double getRequestRate() {
        return nanoRate(requestCount.doubleValue(), endTime - beginTime);
    }

    public double getResponseRate() {
        return nanoRate(getResponseTimeHistogram().getTotalCount(), getElapsedTime());
    }

    public double getSentBytesRate() {
        return nanoRate(connectionStats.getSentBytes(), getElapsedTime());
    }

    public double getReceivedBytesRate() {
        return nanoRate(connectionStats.getReceivedBytes(), getElapsedTime());
    }

    public long getResponses1xx() {
        return responses1xx.longValue();
    }

    public long getResponses2xx() {
        return responses2xx.longValue();
    }

    public long getResponses3xx() {
        return responses3xx.longValue();
    }

    public long getResponses4xx() {
        return responses4xx.longValue();
    }

    public long getResponses5xx() {
        return responses5xx.longValue();
    }

    public long getFailures() {
        return failures.longValue();
    }

    public double getAverageCPUPercent() {
        long elapsedTime = getElapsedTime();
        return elapsedTime == 0 ? 0 : 100D * (completeCPUTime - beginCPUTime) / elapsedTime;
    }

    private double nanoRate(double dividend, long divisor) {
        return divisor == 0 ? 0 : (dividend * TimeUnit.SECONDS.toNanos(1)) / divisor;
    }

    private long getProcessCPUTime() {
        try {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName osObjectName = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
            return (Long)mbeanServer.getAttribute(osObjectName, "ProcessCpuTime");
        } catch (Throwable x) {
            return 0;
        }
    }

    private long getElapsedTime() {
        return completeTime - beginTime;
    }
}
