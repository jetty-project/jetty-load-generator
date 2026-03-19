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

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

/**
 * <p>A load generator listener that reports information about the status codes.</p>
 * <p>Usage:</p>
 * <pre>
 * // Create the report listener.
 * ResponseStatusReportListener listener = new ResponseStatusReportListener("http-client-statuses.log");
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
 * Until the load generation completes, the {@code http-client-statuses.log} file is written with one report per second that
 * has the following format:
 * <pre>
 * [0]
 * 5901=200
 * 98=500
 * </pre>
 * where {@code [0]} is the second count since the recording started, {@code 6001=200} is the number of responses with status
 * code 200 and {@code 98=500} is the number of responses with status code 500.
 */
public class ResponseStatusReportListener implements Resource.NodeListener, LoadGenerator.CompleteListener
{
    private final Timer timer = new Timer();
    private final AtomicReference<ConcurrentMap<String, LongAdder>> statuses = new AtomicReference<>(new ConcurrentHashMap<>());
    private final PrintWriter printWriter;
    private final boolean fullStackTrace;
    private int writeCounter;
    private boolean record;

    public ResponseStatusReportListener(String statusFilename) throws IOException
    {
        this(statusFilename, true, 0L);
    }

    public ResponseStatusReportListener(String statusFilename, long delayBeforeRecordingMs) throws IOException
    {
        this(statusFilename, true, delayBeforeRecordingMs);
    }

    public ResponseStatusReportListener(String statusFilename, boolean fullStackTrace, long delayBeforeRecordingMs) throws IOException
    {
        this.printWriter = new PrintWriter(statusFilename, StandardCharsets.UTF_8);
        this.fullStackTrace = fullStackTrace;
        if (delayBeforeRecordingMs > 0L)
        {
            this.timer.schedule(new TimerTask()
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
        this.record = true;
        this.timer.schedule(new TimerTask()
        {
            @Override
            public void run()
            {
                writeStatuses();
            }
        }, 1000L, 1000L);
    }

    public void stopRecording()
    {
        record = false;
        timer.cancel();
        printWriter.close();
        writeCounter = 0;
    }

    private void writeStatuses()
    {
        ConcurrentMap<String, LongAdder> toWrite = statuses.getAndSet(new ConcurrentHashMap<>());
        printWriter.println("[" + (writeCounter++) + "]");
        for (Map.Entry<String, LongAdder> entry : toWrite.entrySet())
        {
            String key = entry.getKey();
            String value = entry.getValue().toString();
            printWriter.print(value);
            printWriter.print('=');
            printWriter.println(key);
        }
        printWriter.println();
        printWriter.flush();
    }

    @Override
    public void onResourceNode(Resource.Info info)
    {
        if (!record)
            return;

        String key;

        Throwable failure = info.getFailure();
        if (failure != null)
        {
            if (fullStackTrace)
            {
                StringWriter sw = new StringWriter();
                try (PrintWriter pw = new PrintWriter(sw))
                {
                    failure.printStackTrace(pw);
                }
                key = sw.toString();
            }
            else
            {
                key = failure.getClass().getName();
            }
        }
        else
        {
            int status = info.getStatus();
            key = Integer.toString(status);
        }

        LongAdder longAdder = statuses.get().get(key);
        if (longAdder == null)
        {
            statuses.get().compute(key, (k, v) ->
            {
                if (v == null)
                    v = new LongAdder();
                v.increment();
                return v;
            });
        }
        else
        {
            longAdder.increment();
        }
    }

    @Override
    public void onComplete(LoadGenerator loadGenerator)
    {
        stopRecording();
    }
}
