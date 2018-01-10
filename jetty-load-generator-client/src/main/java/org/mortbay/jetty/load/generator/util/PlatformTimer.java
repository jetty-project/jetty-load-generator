package org.mortbay.jetty.load.generator.util;
//
//  ========================================================================
//  Copyright (c) 1995-2014 Mort Bay Consulting Pty. Ltd.
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

import java.util.concurrent.TimeUnit;

/**
 * Detects and reports the timer resolution of the current running platform.
 * <p />
 * Unfortunately, {@link Thread#sleep(long)} on many platforms has a resolution of 1 ms
 * or even of 10 ms, so calling {@code Thread.sleep(2)} often results in a 10 ms sleep.
 * <p />
 * The same applies for {@link Thread#sleep(long, int)} and {@link Object#wait(long, int)}:
 * they are not accurate, especially on virtualized platforms (like Amazon EC2, where the
 * resolution can be as high as 64 ms).
 * <p />
 * {@link System#nanoTime()} is precise enough, but we would need to loop continuously
 * checking the nano time until the sleep period is elapsed; to avoid busy looping pegging
 * the CPUs, {@link Thread#yield()} is called to attempt to reduce the CPU load.
 * <p />
 * Typical usage to impose a precise throughput to requests:
 * <pre>
 * PlatformTimer timer = PlatformTimer.detect();
 * for (int i = 0; i < 100; ++i)
 * {
 *     performRequest();
 *     timer.sleep(microseconds);
 * }
 * </pre>
 */
public class PlatformTimer
{
    private final long nativeResolution;
    private final long emulatedResolution;

    private PlatformTimer(long nativeResolution, long emulatedResolution)
    {
        this.nativeResolution = nativeResolution;
        this.emulatedResolution = emulatedResolution;
    }

    public long getNativeResolution()
    {
        return nativeResolution;
    }

    public long getEmulatedResolution()
    {
        return emulatedResolution;
    }

    public void sleep(long micros)
    {
        if (micros > nativeResolution)
            sleepNative(micros);
        else
            sleepEmulated(micros);
    }

    @Override
    public String toString()
    {
        return String.format("%s[native=%d,emulated=%d]", getClass().getName(), getNativeResolution(), getEmulatedResolution());
    }

    public static PlatformTimer detect()
    {
        detectNative();
        long nativeAccuracy = detectNative();
        detectEmulated();
        long emulatedAccuracy = detectEmulated();
        while (emulatedAccuracy > nativeAccuracy)
            emulatedAccuracy = detectEmulated();
        return new PlatformTimer( nativeAccuracy, emulatedAccuracy);
    }

    private static long detectNative()
    {
        return detect(true);
    }

    private static long detectEmulated()
    {
        return detect(false);
    }

    private static long detect(boolean useNative)
    {
        // Avoid stop-the-world pauses from the GC
        System.gc();

        long min = 0;
        long max = 100000;
        long value = max;

        while (max > min + 1)
        {
            long begin = System.nanoTime();
            if (useNative)
                sleepNative(value);
            else
                sleepEmulated(value);
            long end = System.nanoTime();

            long elapsedMicros = TimeUnit.NANOSECONDS.toMicros(end - begin);
            if (elapsedMicros > value + (value / 10))
                min = value;
            else
                max = value;
            value = (min + max) / 2;
        }
        return value;
    }

    private static void sleepNative(long micros)
    {
        try
        {
            TimeUnit.MICROSECONDS.sleep(micros);
        }
        catch (InterruptedException x)
        {
            Thread.currentThread().interrupt();
            throw new RuntimeException(x);
        }
    }

    private static void sleepEmulated(long micros)
    {
        long end = System.nanoTime() + TimeUnit.MICROSECONDS.toNanos(micros);
        while (System.nanoTime() < end)
            java.lang.Thread.onSpinWait();
    }
}

