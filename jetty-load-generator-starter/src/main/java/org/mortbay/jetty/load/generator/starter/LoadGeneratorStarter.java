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

package org.mortbay.jetty.load.generator.starter;

import java.io.OutputStream;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import com.beust.jcommander.JCommander;
import org.HdrHistogram.Histogram;
import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.toolchain.perf.HistogramSnapshot;
import org.eclipse.jetty.util.ajax.JSON;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.listeners.ReportListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * <p>A convenience class to run the load generator from the command line.</p>
 * <pre>
 * java -jar jetty-load-generator-starter.jar --help
 * </pre>
 */
public class LoadGeneratorStarter {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadGeneratorStarter.class);

    public static void main(String[] args) throws Exception {
        LoadGeneratorStarterArgs starterArgs = parse(args);
        if (starterArgs == null) {
            return;
        }
        LoadGenerator.Builder builder = configure(starterArgs);
        ReportListener listener = new ReportListener();
        LoadGenerator generator = builder
                .listener(listener)
                .resourceListener(listener)
                .build();
        generator.addBean(listener);
        if (starterArgs.isJMX()) {
            MBeanContainer mbeanContainer = new MBeanContainer(ManagementFactory.getPlatformMBeanServer());
            generator.addBean(mbeanContainer);
        }
        run(generator);
        ReportListener.Report report = listener.whenComplete().join();
        if (starterArgs.isDisplayStats()) {
            displayReport(generator.getConfig(), report);
        }
        String statsFile = starterArgs.getStatsFile();
        if (statsFile != null) {
            try (OutputStream output = Files.newOutputStream(Path.of(statsFile))) {
                Map<String, Object> map = new HashMap<>();
                map.put("config", generator.getConfig());
                map.put("report", report);
                JSON json = new JSON();
                output.write(json.toJSON(map).getBytes(StandardCharsets.UTF_8));
                LOGGER.info("load generator report saved to: {}", statsFile);
            }
        }
    }

    /**
     * <p>Parses the program arguments, returning the default arguments holder.</p>
     *
     * @param args the program arguments to parse
     * @return the default arguments holder
     * @see #parse(String[], Supplier)
     */
    public static LoadGeneratorStarterArgs parse(String[] args) {
        return parse(args, LoadGeneratorStarterArgs::new);
    }

    /**
     * <p>Parses the program arguments, returning a custom arguments holder.</p>
     *
     * @param args         the program arguments to parse
     * @param argsSupplier the supplier for the custom arguments holder
     * @param <T>          the custom argument holder type
     * @return the custom arguments holder
     */
    public static <T extends LoadGeneratorStarterArgs> T parse(String[] args, Supplier<T> argsSupplier) {
        T starterArgs = argsSupplier.get();
        JCommander jCommander = new JCommander(starterArgs);
        jCommander.setAcceptUnknownOptions(true);
        jCommander.parse(args);
        if (starterArgs.isHelp()) {
            jCommander.usage();
            return null;
        }
        return starterArgs;
    }

    /**
     * <p>Creates a new LoadGenerator.Builder, configuring it from the given arguments holder.</p>
     *
     * @param starterArgs the arguments holder
     * @return a new LoadGenerator.Builder
     */
    public static LoadGenerator.Builder configure(LoadGeneratorStarterArgs starterArgs) {
        try {
            LoadGenerator.Builder builder = LoadGenerator.builder();
            return builder
                    .threads(starterArgs.getThreads())
                    .warmupIterationsPerThread(starterArgs.getWarmupIterations())
                    .iterationsPerThread(starterArgs.getIterations())
                    .runFor(starterArgs.getRunningTime(), starterArgs.getRunningTimeUnit())
                    .usersPerThread(starterArgs.getUsersPerThread())
                    .channelsPerUser(starterArgs.getChannelsPerUser())
                    .resource(starterArgs.getResource(builder))
                    .resourceRate(starterArgs.getResourceRate())
                    .rateRampUpPeriod(starterArgs.getRateRampUpPeriod())
                    .scheme(starterArgs.getScheme())
                    .host(starterArgs.getHost())
                    .port(starterArgs.getPort())
                    .httpClientTransportBuilder(starterArgs.getHttpClientTransportBuilder())
                    .sslContextFactory(starterArgs.getSslContextFactory())
                    .maxRequestsQueued(starterArgs.getMaxRequestsQueued())
                    .connectBlocking(starterArgs.isConnectBlocking())
                    .connectTimeout(starterArgs.getConnectTimeout())
                    .idleTimeout(starterArgs.getIdleTimeout())
                    .executor(starterArgs.getExecutor())
                    .scheduler(starterArgs.getScheduler());
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    /**
     * <p>Runs a load generation, waiting indefinitely for completion.</p>
     *
     * @param loadGenerator the load generator to run
     */
    public static void run(LoadGenerator loadGenerator) {
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

    private static void displayReport(LoadGenerator.Config config, ReportListener.Report report) {
        Histogram responseTimes = report.getResponseTimeHistogram();
        HistogramSnapshot snapshot = new HistogramSnapshot(responseTimes, 20, "response times", "ms", TimeUnit.NANOSECONDS::toMillis);
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z").withZone(ZoneId.systemDefault());
        LOGGER.info("");
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("-------------  Load Generator Report  --------------");
        LOGGER.info("----------------------------------------------------");
        LOGGER.info("{}://{}:{} over {}", config.getScheme(), config.getHost(), config.getPort(), config.getHttpClientTransportBuilder().getType());
        int resourceCount = config.getResource().descendantCount();
        LOGGER.info("resource tree     : {} resource(s)", resourceCount);
        Instant beginInstant = report.getBeginInstant();
        LOGGER.info("begin date time   : {}", dateTimeFormatter.format(beginInstant));
        Instant completeInstant = report.getCompleteInstant();
        LOGGER.info("complete date time: {}", dateTimeFormatter.format(completeInstant));
        LOGGER.info("recording time    : {} s", String.format("%.3f", (double)report.getRecordingDuration().toMillis() / 1000));
        LOGGER.info("average cpu load  : {}/{}", String.format("%.3f", report.getAverageCPUPercent()), Runtime.getRuntime().availableProcessors() * 100);
        LOGGER.info("");
        if (responseTimes.getTotalCount() > 0) {
            LOGGER.info("histogram:");
            Arrays.stream(snapshot.toString().split(System.lineSeparator())).forEach(line -> LOGGER.info("{}", line));
            LOGGER.info("");
        }
        double resourceRate = config.getResourceRate();
        LOGGER.info("nominal resource rate (resources/s): {}", String.format("%.3f", resourceRate));
        LOGGER.info("nominal request rate (requests/s)  : {}", String.format("%.3f", resourceRate * resourceCount));
        LOGGER.info("request rate (requests/s)          : {}", String.format("%.3f", report.getRequestRate()));
        LOGGER.info("response rate (responses/s)        : {}", String.format("%.3f", report.getResponseRate()));
        LOGGER.info("send rate (bytes/s)                : {}", String.format("%.3f", report.getSentBytesRate()));
        LOGGER.info("receive rate (bytes/s)             : {}", String.format("%.3f", report.getReceivedBytesRate()));
        LOGGER.info("failures          : {}", report.getFailures());
        LOGGER.info("response 1xx group: {}", report.getResponses1xx());
        LOGGER.info("response 2xx group: {}", report.getResponses2xx());
        LOGGER.info("response 3xx group: {}", report.getResponses3xx());
        LOGGER.info("response 4xx group: {}", report.getResponses4xx());
        LOGGER.info("response 5xx group: {}", report.getResponses5xx());
        LOGGER.info("----------------------------------------------------");
    }
}
