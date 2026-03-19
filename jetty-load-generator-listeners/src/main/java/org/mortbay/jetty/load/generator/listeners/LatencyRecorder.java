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

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArrayList;

import org.HdrHistogram.Histogram;
import org.HdrHistogram.HistogramLogWriter;
import org.HdrHistogram.SingleWriterRecorder;

/**
 * A latency recorder that writes to a file one histogram per second in HdrHistogram's
 * {@code HistogramLogReader} format.
 */
public class LatencyRecorder
{
    private final HistogramLogRecorder recorder;
    private final String histogramFilename;

    /**
     * Creates a new instance.
     * @param histogramFilename the histograms file name.
     * @throws FileNotFoundException if the file cannot be created.
     */
    public LatencyRecorder(String histogramFilename) throws FileNotFoundException
    {
        this(histogramFilename, 0);
    }
    /**
     * Creates a new instance
     * @param histogramFilename the histograms file name.
     * @param minBufferSize the minimum buffer size that is used to collect one histogram, 0 for a default value.
     * @throws FileNotFoundException if the file cannot be created.
     */
    public LatencyRecorder(String histogramFilename, int minBufferSize) throws FileNotFoundException
    {
        this.recorder = new HistogramLogRecorder(histogramFilename, 3, 1000, minBufferSize);
        this.histogramFilename = histogramFilename;
    }

    /**
     * @return the histograms file name.
     */
    public String getFilename()
    {
        return histogramFilename;
    }

    /**
     * Starts the recording of the histograms into the file.
     */
    public void startRecording()
    {
        recorder.startRecording();
    }

    /**
     * Stops the recording of the histograms into the file.
     */
    public void stopRecording()
    {
        recorder.close();
    }

    /**
     * Add a latency value to the current histogram.
     * @param value the latency in ns.
     */
    public void recordValue(long value)
    {
        recorder.recordValue(value);
    }

    private static class HistogramLogRecorder implements Closeable
    {
        private enum State
        {
            NOT_RECORDING, RECORDING, CLOSED
        }

        private final ThreadLocal<SingleWriterRecorder> recorderTl;
        private final List<SingleWriterRecorder> recorders = new CopyOnWriteArrayList<>();
        private final Timer timer = new Timer();
        private final HistogramLogWriter writer;
        private volatile HistogramLogRecorder.State state = HistogramLogRecorder.State.NOT_RECORDING;

        public HistogramLogRecorder(String histogramFilename, int numberOfSignificantValueDigits, int intervalInMs, int minBufferSize) throws FileNotFoundException
        {
            recorderTl = ThreadLocal.withInitial(() ->
            {
                SingleWriterRecorder singleWriterRecorder = new SingleWriterRecorder(numberOfSignificantValueDigits);
                recorders.add(singleWriterRecorder);
                return singleWriterRecorder;
            });
            writer = new HistogramLogWriter(histogramFilename);
            timer.schedule(new TimerTask()
            {
                private final Histogram collectiveHistogram = new Histogram(numberOfSignificantValueDigits)
                {
                    public int getNeededByteBufferCapacity()
                    {
                        int superNeededByteBufferCapacity = super.getNeededByteBufferCapacity();
                        if (minBufferSize > 0)
                            return Math.max(superNeededByteBufferCapacity, minBufferSize);
                        else
                            return superNeededByteBufferCapacity;
                    }
                };
                private Histogram intervalHistogram;

                @Override
                public void run()
                {
                    for (SingleWriterRecorder recorder : recorders)
                    {
                        intervalHistogram = recorder.getIntervalHistogram(intervalHistogram, false);
                        collectiveHistogram.add(intervalHistogram);
                    }
                    if (state == HistogramLogRecorder.State.RECORDING)
                        writer.outputIntervalHistogram(collectiveHistogram);
                    collectiveHistogram.reset();
                }
            }, intervalInMs, intervalInMs);
        }

        public void startRecording()
        {
            if (state != HistogramLogRecorder.State.NOT_RECORDING)
                throw new IllegalStateException("current state: " + state);

            long now = System.currentTimeMillis();
            writer.setBaseTime(now);
            writer.outputBaseTime(now);
            writer.outputStartTime(now);
            state = HistogramLogRecorder.State.RECORDING;
        }

        @Override
        public void close()
        {
            if (state == HistogramLogRecorder.State.CLOSED)
                return;
            state = HistogramLogRecorder.State.CLOSED;

            timer.cancel();
            writer.close();
        }

        public void recordValue(long value)
        {
            // Always record values even if state != State.RECORDING, the timer won't write the
            // histogram data on disk, but the histogram code will be jit'ed.
            recorderTl.get().recordValue(value);
        }
    }
}
