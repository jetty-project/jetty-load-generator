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

import java.io.FileNotFoundException;
import java.util.Timer;
import java.util.TimerTask;

import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

/**
 * <p>A load generator listener that reports response time histograms.</p>
 * <p>Usage:</p>
 * <pre>
 * // Create the report listener.
 * ResponseTimeReportListener listener = new ResponseTimeReportListener("perf.hlog");
 *
 * // Create the LoadGenerator, passing the listener to relevant builder methods.
 * LoadGenerator generator = LoadGenerator.builder()
 *     ...
 *     .listener(listener)
 *     .resourceListener(listener)
 *     .build();
 *
 * // Start the load generation.
 * generator.begin();
 * </pre>
 * Until the load generation completes, the {@code perf.hlog} file is written in HdrHistogram's
 * {@code HistogramLogReader} format with one histogram per second.
 */
public class ResponseTimeReportListener implements Resource.NodeListener, LoadGenerator.CompleteListener
{
    private final LatencyRecorder recorder;

    public ResponseTimeReportListener(String histogramFilename) throws FileNotFoundException
    {
        this(histogramFilename, 0, 0L);
    }

    public ResponseTimeReportListener(String histogramFilename, long delayBeforeRecordingMs) throws FileNotFoundException
    {
        this(histogramFilename, 0, delayBeforeRecordingMs);
    }

    public ResponseTimeReportListener(String histogramFilename, int minBufferSize, long delayBeforeRecordingMs) throws FileNotFoundException
    {
        this.recorder = new LatencyRecorder(histogramFilename, minBufferSize);
        if (delayBeforeRecordingMs > 0L)
        {
            Timer timer = new Timer(true);
            timer.schedule(new TimerTask()
            {
                @Override
                public void run()
                {
                    startRecording();
                }
            }, delayBeforeRecordingMs);
        }
        else
        {
            startRecording();
        }
    }

    public void startRecording()
    {
        recorder.startRecording();
    }

    public void stopRecording()
    {
        recorder.stopRecording();
    }

    @Override
    public void onResourceNode(Resource.Info info)
    {
        long responseTime = info.getResponseTime() - info.getRequestTime();
        recorder.recordValue(responseTime);
    }

    @Override
    public void onComplete(LoadGenerator generator)
    {
        stopRecording();
    }
}

