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
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
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
public class ReportListener extends ContainerLifeCycle implements LoadGenerator.BeginListener, LoadGenerator.ReadyListener, LoadGenerator.EndListener, LoadGenerator.CompleteListener, Resource.NodeListener, Connection.Listener {
    private final ConnectionStatistics connectionStats = new ConnectionStatistics();
    private final Recorder recorder;
    private final AtomicReference<Histogram> histogram = new AtomicReference<>();
    private final LongAdder responses1xx = new LongAdder();
    private final LongAdder responses2xx = new LongAdder();
    private final LongAdder responses3xx = new LongAdder();
    private final LongAdder responses4xx = new LongAdder();
    private final LongAdder responses5xx = new LongAdder();
    private final LongAdder responseContent = new LongAdder();
    private final LongAdder failures = new LongAdder();
    private volatile Instant beginInstant;
    private volatile long beginTime;
    private volatile long readyTime;
    private volatile long endTime;
    private volatile long completeTime;
    private volatile long readyCPUTime;
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

    /**
     * @return the Instant of the load generation {@link LoadGenerator.BeginListener begin event}
     */
    public Instant getBeginInstant() {
        return beginInstant;
    }

    /**
     * @return the Instant of the load generation {@link LoadGenerator.CompleteListener complete event}
     */
    public Instant getCompleteInstant() {
        return beginInstant.plusNanos(completeTime - beginTime);
    }

    /**
     * <p>Returns the duration of the load generation recording.</p>
     * <p>The recording starts at the {@link LoadGenerator.ReadyListener ready event}
     * and therefore excludes warmup.</p>
     *
     * @return the Duration of the load generation recording
     */
    public Duration getRecordingDuration() {
        return Duration.ofNanos(getRecordingNanos());
    }

    /**
     * <p>Returns the response time histogram.</p>
     * <p>The response time is the time between a request is queued to be sent,
     * to the time the response is fully received, in nanoseconds.</p>
     * <p>Warmup requests are not recorded.</p>
     *
     * @return the response time histogram
     */
    public Histogram getResponseTimeHistogram() {
        // The histogram is reset every time getIntervalHistogram() is called.
        return histogram.updateAndGet(h -> h != null ? h : recorder.getIntervalHistogram());
    }

    /**
     * @return the request rate, in requests/s
     */
    public double getRequestRate() {
        return nanoRate(getResponseTimeHistogram().getTotalCount(), endTime - readyTime);
    }

    /**
     * @return the response rate in responses/s
     */
    public double getResponseRate() {
        return nanoRate(getResponseTimeHistogram().getTotalCount(), getRecordingNanos());
    }

    /**
     * @return the rate of bytes sent, in bytes/s
     */
    public double getSentBytesRate() {
        return nanoRate(connectionStats.getSentBytes(), getRecordingNanos());
    }

    /**
     * @return the rate of bytes received, in bytes/s
     */
    public double getReceivedBytesRate() {
        return nanoRate(connectionStats.getReceivedBytes(), getRecordingNanos());
    }

    /**
     * @return the number of HTTP 1xx responses
     */
    public long getResponses1xx() {
        return responses1xx.longValue();
    }

    /**
     * @return the number of HTTP 2xx responses
     */
    public long getResponses2xx() {
        return responses2xx.longValue();
    }

    /**
     * @return the number of HTTP 3xx responses
     */
    public long getResponses3xx() {
        return responses3xx.longValue();
    }

    /**
     * @return the number of HTTP 4xx responses
     */
    public long getResponses4xx() {
        return responses4xx.longValue();
    }

    /**
     * @return the number of HTTP 5xx responses
     */
    public long getResponses5xx() {
        return responses5xx.longValue();
    }

    /**
     * @return the number of failures
     */
    public long getFailures() {
        return failures.longValue();
    }

    /**
     * <p>Returns the average CPU load during recording.</p>
     * <p>This is the CPU time for the load generator JVM, across all cores, divided by the recording duration.</p>
     * <p>This number is typically greater than 100 because it takes into account all cores.</p>
     * <p>For example, a value of {@code 456.789} on a 12 core machine means that during the recording
     * about 4.5 cores out of 12 were at 100% utilization.
     * Equivalently, it means that each core was at {@code 456.789/12}, about 38%, utilization.</p>
     *
     * @return the average CPU load during recording
     */
    public double getAverageCPUPercent() {
        long elapsedTime = getRecordingNanos();
        return elapsedTime == 0 ? 0 : 100D * (completeCPUTime - readyCPUTime) / elapsedTime;
    }

    @Override
    public void onBegin(LoadGenerator generator) {
        beginInstant = Instant.now();
        beginTime = System.nanoTime();
    }

    @Override
    public void onReady(LoadGenerator generator) {
        readyTime = System.nanoTime();
        readyCPUTime = getProcessCPUTime();
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

    private long getRecordingNanos() {
        return completeTime - readyTime;
    }
}
