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

import org.HdrHistogram.Histogram;
import org.HdrHistogram.Recorder;
import org.mortbay.jetty.load.generator.resource.Resource;

public class RunInfo implements LoadGenerator.BeginListener, LoadGenerator.EndListener, Resource.NodeListener {
    private final AtomicLong resources = new AtomicLong();
    private final Recorder recorder;
    private long beginTime;
    private long endTime;

    public RunInfo() {
        this(TimeUnit.MICROSECONDS.toNanos(1), TimeUnit.MINUTES.toNanos(1), 3);
    }

    public RunInfo(long lowestDiscernibleValue, long highestTrackableValue, int numberOfSignificantValueDigits) {
        recorder = new Recorder(lowestDiscernibleValue, highestTrackableValue, numberOfSignificantValueDigits);
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
    public void onResourceNode(Resource.Info info) {
        resources.incrementAndGet();
        recorder.recordValue(info.getResponseTime() - info.getRequestTime());
    }

    public long getResources() {
        return resources.get();
    }

    public long getBeginTime() {
        return beginTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public Histogram getHistogram() {
        return recorder.getIntervalHistogram();
    }
}
