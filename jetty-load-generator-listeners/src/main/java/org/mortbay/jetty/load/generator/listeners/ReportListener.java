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

package org.mortbay.jetty.load.generator.listeners;

import java.io.ByteArrayOutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.io.ConnectionStatistics;
import org.eclipse.jetty.util.ajax.JSON;
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
 * // Start the load generation.
 * generator.begin();
 *
 * // Wait for the load generation to complete to get the report.
 * ReportListener.Report report = listener.whenComplete().join();
 *
 * System.err.printf("max response time: %d", report.getResponseTimeHistogram().getMaxValue());
 * </pre>
 */
public class ReportListener extends ContainerLifeCycle implements LoadGenerator.BeginListener, LoadGenerator.ReadyListener, LoadGenerator.EndListener, LoadGenerator.CompleteListener, Resource.NodeListener, Connection.Listener {
    private final Report report = new Report();
    private final CompletableFuture<Report> reportPromise = new CompletableFuture<>();
    private final ConnectionStatistics connectionStats = new ConnectionStatistics();
    private final Recorder recorder;

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
     * @return a CompletableFuture that is completed when the load generation is complete
     */
    public CompletableFuture<Report> whenComplete() {
        return reportPromise;
    }

    /**
     * @return the Instant of the load generation {@link LoadGenerator.BeginListener begin event}
     * @deprecated use {@link Report#getBeginInstant()} instead
     */
    @Deprecated
    public Instant getBeginInstant() {
        return report.getBeginInstant();
    }

    /**
     * @return the Instant of the load generation {@link LoadGenerator.CompleteListener complete event}
     * @deprecated use {@link Report#getCompleteInstant()} instead
     */
    @Deprecated
    public Instant getCompleteInstant() {
        return report.getCompleteInstant();
    }

    /**
     * <p>Returns the duration of the load generation recording.</p>
     * <p>The recording starts at the {@link LoadGenerator.ReadyListener ready event}
     * and therefore excludes warmup.</p>
     *
     * @return the Duration of the load generation recording
     * @deprecated use {@link Report#getRecordingDuration()} instead
     */
    @Deprecated
    public Duration getRecordingDuration() {
        return report.getRecordingDuration();
    }

    /**
     * <p>Returns the response time histogram.</p>
     * <p>The response time is the time between a request is queued to be sent,
     * to the time the response is fully received, in nanoseconds.</p>
     * <p>Warmup requests are not recorded.</p>
     *
     * @return the response time histogram
     * @deprecated use {@link Report#getResponseTimeHistogram()} instead
     */
    @Deprecated
    public Histogram getResponseTimeHistogram() {
        return report.getResponseTimeHistogram();
    }

    /**
     * @return the request rate, in requests/s
     * @deprecated use {@link Report#getRequestRate()} instead
     */
    @Deprecated
    public double getRequestRate() {
        return report.getRequestRate();
    }

    /**
     * @return the response rate in responses/s
     * @deprecated use {@link Report#getResponseRate()} instead
     */
    @Deprecated
    public double getResponseRate() {
        return report.getResponseRate();
    }

    /**
     * @return the rate of bytes sent, in bytes/s
     * @deprecated use {@link Report#getSentBytesRate()} instead
     */
    @Deprecated
    public double getSentBytesRate() {
        return report.getSentBytesRate();
    }

    /**
     * @return the rate of bytes received, in bytes/s
     * @deprecated use {@link Report#getReceivedBytesRate()} instead
     */
    @Deprecated
    public double getReceivedBytesRate() {
        return report.getReceivedBytesRate();
    }

    /**
     * @return the number of HTTP 1xx responses
     * @deprecated use {@link Report#getResponses1xx()} instead
     */
    @Deprecated
    public long getResponses1xx() {
        return report.getResponses1xx();
    }

    /**
     * @return the number of HTTP 2xx responses
     * @deprecated use {@link Report#getResponses2xx()} instead
     */
    @Deprecated
    public long getResponses2xx() {
        return report.getResponses2xx();
    }

    /**
     * @return the number of HTTP 3xx responses
     * @deprecated use {@link Report#getResponses3xx()} instead
     */
    @Deprecated
    public long getResponses3xx() {
        return report.getResponses3xx();
    }

    /**
     * @return the number of HTTP 4xx responses
     * @deprecated use {@link Report#getResponses4xx()} instead
     */
    @Deprecated
    public long getResponses4xx() {
        return report.getResponses4xx();
    }

    /**
     * @return the number of HTTP 5xx responses
     * @deprecated use {@link Report#getResponses5xx()} instead
     */
    @Deprecated
    public long getResponses5xx() {
        return report.getResponses5xx();
    }

    /**
     * @return the number of failures
     * @deprecated use {@link Report#getFailures()} instead
     */
    @Deprecated
    public long getFailures() {
        return report.getFailures();
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
     * @deprecated use {@link Report#getAverageCPUPercent()} instead
     */
    @Deprecated
    public double getAverageCPUPercent() {
        return report.getAverageCPUPercent();
    }

    @Override
    public void onBegin(LoadGenerator generator) {
        report.beginInstant = Instant.now();
        report.beginTime = System.nanoTime();
    }

    @Override
    public void onReady(LoadGenerator generator) {
        report.readyTime = System.nanoTime();
        report.readyCPUTime = getProcessCPUTime();
    }

    @Override
    public void onEnd(LoadGenerator generator) {
        report.endTime = System.nanoTime();
    }

    @Override
    public void onComplete(LoadGenerator generator) {
        report.completeTime = System.nanoTime();
        report.completeCPUTime = getProcessCPUTime();
        // The histogram is reset every time getIntervalHistogram() is called.
        report.histogram = recorder.getIntervalHistogram();
        report.sentBytes = connectionStats.getSentBytes();
        report.recvBytes = connectionStats.getReceivedBytes();
        reportPromise.complete(report);
    }

    @Override
    public void onResourceNode(Resource.Info info) {
        if (info.getFailure() == null) {
            recordResponseGroup(info);
            long responseTime = info.getResponseTime() - info.getRequestTime();
            recorder.recordValue(responseTime);
            report.responseContent.add(info.getContentLength());
        } else {
            report.failures.increment();
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
                report.responses1xx.increment();
                break;
            case 2:
                report.responses2xx.increment();
                break;
            case 3:
                report.responses3xx.increment();
                break;
            case 4:
                report.responses4xx.increment();
                break;
            case 5:
                report.responses5xx.increment();
                break;
            default:
                break;
        }
    }

    private static long getProcessCPUTime() {
        try {
            MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
            ObjectName osObjectName = new ObjectName(ManagementFactory.OPERATING_SYSTEM_MXBEAN_NAME);
            return (Long)mbeanServer.getAttribute(osObjectName, "ProcessCpuTime");
        } catch (Throwable x) {
            return 0;
        }
    }

    public static class Report implements JSON.Convertible {
        private final LongAdder responses1xx = new LongAdder();
        private final LongAdder responses2xx = new LongAdder();
        private final LongAdder responses3xx = new LongAdder();
        private final LongAdder responses4xx = new LongAdder();
        private final LongAdder responses5xx = new LongAdder();
        private final LongAdder responseContent = new LongAdder();
        private final LongAdder failures = new LongAdder();
        private volatile Histogram histogram;
        private volatile Instant beginInstant;
        private volatile long beginTime;
        private volatile long readyTime;
        private volatile long endTime;
        private volatile long completeTime;
        private volatile long readyCPUTime;
        private volatile long completeCPUTime;
        private volatile long sentBytes;
        private volatile long recvBytes;

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
            return histogram;
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
            return nanoRate(sentBytes, getRecordingNanos());
        }

        /**
         * @return the rate of bytes received, in bytes/s
         */
        public double getReceivedBytesRate() {
            return nanoRate(recvBytes, getRecordingNanos());
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

        private long getRecordingNanos() {
            return completeTime - readyTime;
        }

        private static double nanoRate(double dividend, long divisor) {
            return divisor == 0 ? 0 : (dividend * TimeUnit.SECONDS.toNanos(1)) / divisor;
        }

        @Override
        public void toJSON(JSON.Output out) {
            out.add("beginInstant", getBeginInstant().atZone(ZoneOffset.UTC).toString());
            out.add("completeInstant", getCompleteInstant().atZone(ZoneOffset.UTC).toString());
            out.add("recordingDuration", getRecordingDuration().toMillis());
            out.add("availableProcessors", Runtime.getRuntime().availableProcessors());
            out.add("averageCPUPercent", getAverageCPUPercent());
            out.add("requestRate", getRequestRate());
            out.add("responseRate", getResponseRate());
            out.add("sentBytesRate", getSentBytesRate());
            out.add("receivedBytesRate", getReceivedBytesRate());
            out.add("failures", getFailures());
            out.add("1xx", getResponses1xx());
            out.add("2xx", getResponses2xx());
            out.add("3xx", getResponses3xx());
            out.add("4xx", getResponses4xx());
            out.add("5xx", getResponses5xx());
            ByteArrayOutputStream histogramOutput = new ByteArrayOutputStream();
            HistogramLogWriter hw = new HistogramLogWriter(histogramOutput);
            hw.outputIntervalHistogram(getResponseTimeHistogram());
            hw.close();
            out.add("histogram", new String(histogramOutput.toByteArray(), StandardCharsets.UTF_8));
        }

        @Override
        public void fromJSON(Map map) {
            throw new UnsupportedOperationException();
        }
    }
}
