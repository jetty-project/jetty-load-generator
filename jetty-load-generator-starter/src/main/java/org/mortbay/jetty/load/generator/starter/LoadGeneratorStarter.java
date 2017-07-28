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

package org.mortbay.jetty.load.generator.starter;

import java.text.SimpleDateFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.JCommander;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.listeners.CollectorInformations;
import org.mortbay.jetty.load.generator.listeners.report.GlobalSummaryListener;

public class LoadGeneratorStarter {
    private static final Logger LOGGER = Log.getLogger(LoadGeneratorStarter.class);

    public static void main(String[] args) {
        LoadGeneratorStarterArgs starterArgs = parse(args);
        if (starterArgs == null) {
            return;
        }
        LoadGenerator.Builder builder = prepare(starterArgs);
        GlobalSummaryListener globalSummaryListener = new GlobalSummaryListener();
        builder = builder.resourceListener(globalSummaryListener).requestListener(globalSummaryListener);
        run(builder);
        if (starterArgs.isDisplayStats()) {
            displayGlobalSummaryListener(globalSummaryListener);
        }
    }

    public static LoadGeneratorStarterArgs parse(String[] args) {
        LoadGeneratorStarterArgs starterArgs = new LoadGeneratorStarterArgs();
        JCommander jCommander = new JCommander(starterArgs);
        jCommander.setAcceptUnknownOptions(true);
        jCommander.parse(args);
        if (starterArgs.isHelp()) {
            jCommander.usage();
            return null;
        }
        return starterArgs;
    }

    public static LoadGenerator.Builder prepare(LoadGeneratorStarterArgs starterArgs) {
        try {
            LoadGenerator.Builder builder = new LoadGenerator.Builder();
            return builder.threads(starterArgs.getThreads())
                    .warmupIterationsPerThread(starterArgs.getWarmupIterations())
                    .iterationsPerThread(starterArgs.getIterations())
                    .usersPerThread(starterArgs.getUsers())
                    .channelsPerUser(starterArgs.getChannelsPerUser())
                    .resource(starterArgs.getResource(builder))
                    .resourceRate(starterArgs.getResourceRate())
                    .httpClientTransportBuilder(starterArgs.getHttpClientTransportBuilder())
                    .sslContextFactory(new SslContextFactory())
                    .scheme(starterArgs.getScheme())
                    .host(starterArgs.getHost())
                    .port(starterArgs.getPort())
                    .maxRequestsQueued(starterArgs.getMaxRequestsQueued());
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    public static void run(LoadGenerator.Builder builder) {
        LoadGenerator loadGenerator = builder.build();
        LOGGER.info("load generator config: {}", loadGenerator.getConfig());
        LOGGER.info("load generation begin");
        CompletableFuture<Void> cf = loadGenerator.begin();
        cf.whenComplete((x, f) -> {
            if (f == null) {
                LOGGER.info("load generation complete");
            } else {
                LOGGER.info("load generation failure", f);
            }
        }).join();
    }

    private static void displayGlobalSummaryListener(GlobalSummaryListener globalSummaryListener) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss z");
        CollectorInformations latencyTimeSummary =
                new CollectorInformations(globalSummaryListener.getLatencyTimeHistogram() //
                        .getIntervalHistogram());

        long totalRequestCommitted = globalSummaryListener.getRequestCommitTotal();
        long start = latencyTimeSummary.getStartTimeStamp();
        long end = latencyTimeSummary.getEndTimeStamp();

        LOGGER.info("");
        LOGGER.info("");
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("--------    Latency Time Summary     ---------------");
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("total count:" + latencyTimeSummary.getTotalCount());
        LOGGER.info("maxLatency:" //
                + nanosToMillis(latencyTimeSummary.getMaxValue()));
        LOGGER.info("minLatency:" //
                + nanosToMillis(latencyTimeSummary.getMinValue()));
        LOGGER.info("aveLatency:" //
                + nanosToMillis(Math.round(latencyTimeSummary.getMean())));
        LOGGER.info("50Latency:" //
                + nanosToMillis(latencyTimeSummary.getValue50()));
        LOGGER.info("90Latency:" //
                + nanosToMillis(latencyTimeSummary.getValue90()));
        LOGGER.info("stdDeviation:" //
                + nanosToMillis(Math.round(latencyTimeSummary.getStdDeviation())));
        LOGGER.info("start: {}, end: {}", //
                simpleDateFormat.format(latencyTimeSummary.getStartTimeStamp()), //
                simpleDateFormat.format(latencyTimeSummary.getEndTimeStamp()));
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("-----------     Estimated QPS     ------------------");
        LOGGER.info("----------------------------------------------------");
        long timeInSeconds = TimeUnit.SECONDS.convert(end - start, TimeUnit.MILLISECONDS);
        long qps = totalRequestCommitted / timeInSeconds;
        LOGGER.info("estimated QPS : " + qps);
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("response 1xx family: " + globalSummaryListener.getResponses1xx().longValue());
        LOGGER.info("response 2xx family: " + globalSummaryListener.getResponses2xx().longValue());
        LOGGER.info("response 3xx family: " + globalSummaryListener.getResponses3xx().longValue());
        LOGGER.info("response 4xx family: " + globalSummaryListener.getResponses4xx().longValue());
        LOGGER.info("response 5xx family: " + globalSummaryListener.getResponses5xx().longValue());
        LOGGER.info("");
    }

    private static long nanosToMillis(long nanosValue) {
        return TimeUnit.NANOSECONDS.toMillis(nanosValue);
    }
}
