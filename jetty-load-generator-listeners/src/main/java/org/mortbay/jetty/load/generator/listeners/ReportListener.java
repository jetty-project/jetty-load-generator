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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;
import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.eclipse.jetty.client.api.Request;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

public class ReportListener implements LoadGenerator.BeginListener, LoadGenerator.EndListener, LoadGenerator.CompleteListener, Request.Listener, Resource.NodeListener {
    private final Recorder recorder;
    private final AtomicReference<Histogram> histogram = new AtomicReference<>();
    private final LongAdder requestCount = new LongAdder();
    private final LongAdder responses1xx = new LongAdder();
    private final LongAdder responses2xx = new LongAdder();
    private final LongAdder responses3xx = new LongAdder();
    private final LongAdder responses4xx = new LongAdder();
    private final LongAdder responses5xx = new LongAdder();
    private final LongAdder responseContent = new LongAdder();
    private volatile long beginTime;
    private volatile long endTime;
    private volatile long completeTime;

    public ReportListener() {
        this(TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MINUTES.toNanos(1), 3);
    }

    public ReportListener(long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
        this.recorder = new Recorder(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
    }

    @Override
    public void onBegin(LoadGenerator generator) {
        beginTime = System.nanoTime();
    }

    @Override
    public void onEnd(LoadGenerator generator) {
        endTime = System.nanoTime();
    }

    @Override
    public void onComplete(LoadGenerator generator) {
        completeTime = System.nanoTime();
    }

    @Override
    public void onBegin(Request request) {
        requestCount.increment();
    }

    @Override
    public void onResourceNode(Resource.Info info) {
        recordResponseGroup(info);
        long responseTime = info.getResponseTime() - info.getRequestTime();
        recorder.recordValue(responseTime);
        responseContent.add(info.getContentLength());
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
        return nanoRate(getResponseTimeHistogram().getTotalCount(), completeTime - beginTime);
    }

    public double getDownloadRate() {
        return nanoRate(responseContent.doubleValue(), completeTime - beginTime);
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

    private double nanoRate(double dividend, long divisor) {
        return divisor == 0 ? 0 : (dividend * TimeUnit.SECONDS.toNanos(1)) / divisor;
    }
}
