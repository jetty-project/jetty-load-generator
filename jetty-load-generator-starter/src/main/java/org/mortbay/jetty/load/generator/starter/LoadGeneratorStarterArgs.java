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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import com.beust.jcommander.Parameter;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.mortbay.jetty.load.generator.HTTP1ClientTransportBuilder;
import org.mortbay.jetty.load.generator.HTTP2ClientTransportBuilder;
import org.mortbay.jetty.load.generator.HTTPClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

public class LoadGeneratorStarterArgs {
    @Parameter(names = {"--threads", "-t"}, description = "Number of sender threads")
    private int threads = 1;

    @Parameter(names = {"--warmup-iterations", "-wi"}, description = "Number of warmup iterations per sender thread")
    private int warmupIterations;

    @Parameter(names = {"--iterations", "-i"}, description = "Number of iterations per sender thread")
    private int iterations = 1;

    @Parameter(names = {"--running-time", "-rt"}, description = "Load generation running time")
    private long runningTime;

    @Parameter(names = {"--running-time-unit", "-rtu"}, description = "Load generation running time unit (h/m/s/ms)")
    private String runningTimeUnit = "s";

    @Parameter(names = {"--users-per-thread", "-upt"}, description = "Number of users/connections per sender thread")
    private int usersPerThread = 1;

    @Parameter(names = {"--channels-per-user", "-cpu"}, description = "Number of concurrent connections/streams per user")
    private int channelsPerUser = 128;

    @Parameter(names = {"--resource-xml-path", "-rxp"}, description = "Path to resource XML file")
    private String resourceXMLPath;

    @Parameter(names = {"--resource-json-path", "-rjp"}, description = "Path to resource JSON file")
    private String resourceJSONPath;

    @Parameter(names = {"--resource-groovy-path", "-rgp"}, description = "Path to resource Groovy file")
    private String resourceGroovyPath;

    @Parameter(names = {"--resource-rate", "-rr"}, description = "Total resource tree rate, per second; use 0 for max request rate")
    private int resourceRate = 1;

    @Parameter(names = {"--rate-ramp-up", "-rru"}, description = "Rate ramp-up period, in seconds")
    private long rateRampUpPeriod = 0;

    @Parameter(names = {"--scheme", "-s"}, description = "Target scheme (http/https)")
    private String scheme = "http";

    @Parameter(names = {"--host", "-h"}, description = "Target host")
    private String host = "localhost";

    @Parameter(names = {"--port", "-p"}, description = "Target port")
    private int port = 8080;

    @Parameter(names = {"--transport", "-tr"}, description = "Transport (http, https, h2, h2c)")
    private String transport = "http";

    @Parameter(names = {"--selectors"}, description = "Number of NIO selectors")
    private int selectors = 1;

    @Parameter(names = {"--max-requests-queued", "-mrq"}, description = "Maximum number of queued requests")
    private int maxRequestsQueued = 1024;

    @Parameter(names = {"--connect-blocking", "-cb"}, description = "Whether TCP connect is blocking")
    private boolean connectBlocking = true;

    @Parameter(names = {"--connect-timeout", "-ct"}, description = "TCP connect timeout, in milliseconds")
    private long connectTimeout = 5000;

    @Parameter(names = {"--idle-timeout", "-it"}, description = "TCP connection idle timeout, in milliseconds")
    private long idleTimeout = 15000;

    @Parameter(names = {"--stats-file", "-sf"}, description = "Statistics output file path in JSON format")
    private String statsFile;

    @Parameter(names = {"--display-stats", "-ds"}, description = "Whether to display statistics in the terminal")
    private boolean displayStats;

    @Parameter(names = {"--jmx"}, description = "Exports load generator components to the JVM platform MBeanServer as MBeans")
    private boolean jmx;

    @Parameter(names = {"--executor-max-threads"})
    private int executorMaxThreads = 256;

    @Parameter(names = {"--scheduler-max-threads"})
    private int schedulerMaxThreads = 1;

    @Parameter(names = {"--help"}, description = "Displays usage")
    private boolean help;

    // Getters and setters are needed by JCommander.

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getWarmupIterations() {
        return warmupIterations;
    }

    public void setWarmupIterations(int warmupIterations) {
        this.warmupIterations = warmupIterations;
    }

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public long getRunningTime() {
        return runningTime;
    }

    public void setRunningTime(long runningTime) {
        this.runningTime = runningTime;
    }

    public TimeUnit getRunningTimeUnit() {
        switch (this.runningTimeUnit) {
            case "m":
            case "minutes":
            case "MINUTES":
                return TimeUnit.MINUTES;
            case "h":
            case "hours":
            case "HOURS":
                return TimeUnit.HOURS;
            case "s":
            case "seconds":
            case "SECONDS":
                return TimeUnit.SECONDS;
            case "ms":
            case "milliseconds":
            case "MILLISECONDS":
                return TimeUnit.MILLISECONDS;
            default:
                throw new IllegalArgumentException(runningTimeUnit + " is not recognized");
        }
    }

    public void setRunningTimeUnit(String runningTimeUnit) {
        this.runningTimeUnit = runningTimeUnit;
    }

    public int getUsersPerThread() {
        return usersPerThread;
    }

    public void setUsersPerThread(int usersPerThread) {
        this.usersPerThread = usersPerThread;
    }

    public int getChannelsPerUser() {
        return channelsPerUser;
    }

    public void setChannelsPerUser(int channelsPerUser) {
        this.channelsPerUser = channelsPerUser;
    }

    public String getResourceXMLPath() {
        return resourceXMLPath;
    }

    public void setResourceXMLPath(String resourceXMLPath) {
        this.resourceXMLPath = resourceXMLPath;
    }

    public String getResourceJSONPath() {
        return resourceJSONPath;
    }

    public void setResourceJSONPath(String resourceJSONPath) {
        this.resourceJSONPath = resourceJSONPath;
    }

    public String getResourceGroovyPath() {
        return resourceGroovyPath;
    }

    public void setResourceGroovyPath(String resourceGroovyPath) {
        this.resourceGroovyPath = resourceGroovyPath;
    }

    public int getResourceRate() {
        return resourceRate;
    }

    public void setResourceRate(int resourceRate) {
        this.resourceRate = resourceRate;
    }

    public long getRateRampUpPeriod() {
        return rateRampUpPeriod;
    }

    public void setRateRampUpPeriod(long rateRampUpPeriod) {
        this.rateRampUpPeriod = rateRampUpPeriod;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public String getTransport() {
        return transport;
    }

    public void setTransport(String transport) {
        transport = transport.toLowerCase(Locale.ENGLISH);
        switch (transport) {
            case "http":
            case "https":
            case "h2c":
            case "h2":
                this.transport = transport;
                break;
            default:
                throw new IllegalArgumentException("unsupported transport " + transport);
        }
    }

    public int getSelectors() {
        return selectors;
    }

    public void setSelectors(int selectors) {
        this.selectors = selectors;
    }

    public int getMaxRequestsQueued() {
        return maxRequestsQueued;
    }

    public void setMaxRequestsQueued(int maxRequestsQueued) {
        this.maxRequestsQueued = maxRequestsQueued;
    }

    public boolean isConnectBlocking() {
        return connectBlocking;
    }

    public void setConnectBlocking(boolean connectBlocking) {
        this.connectBlocking = connectBlocking;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public long getIdleTimeout() {
        return idleTimeout;
    }

    public void setIdleTimeout(long idleTimeout) {
        this.idleTimeout = idleTimeout;
    }

    public String getStatsFile() {
        return statsFile;
    }

    public void setStatsFile(String statsFile) {
        this.statsFile = statsFile;
    }

    public boolean isDisplayStats() {
        return displayStats;
    }

    public void setDisplayStats(boolean displayStats) {
        this.displayStats = displayStats;
    }

    public boolean isJMX() {
        return jmx;
    }

    public void setJMX(boolean jmx) {
        this.jmx = jmx;
    }

    public int getExecutorMaxThreads() {
        return executorMaxThreads;
    }

    public void setExecutorMaxThreads(int executorMaxThreads) {
        this.executorMaxThreads = executorMaxThreads;
    }

    public int getSchedulerMaxThreads() {
        return schedulerMaxThreads;
    }

    public void setSchedulerMaxThreads(int schedulerMaxThreads) {
        this.schedulerMaxThreads = schedulerMaxThreads;
    }

    public boolean isHelp() {
        return help;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    // APIs used by LoadGeneratorStarter.

    public SslContextFactory.Client getSslContextFactory() {
        return new SslContextFactory.Client(true);
    }

    public HTTPClientTransportBuilder getHttpClientTransportBuilder() {
        String transport = getTransport();
        switch (transport) {
            case "http":
            case "https": {
                return new HTTP1ClientTransportBuilder().selectors(getSelectors());
            }
            case "h2c":
            case "h2": {
                return new HTTP2ClientTransportBuilder().selectors(getSelectors());
            }
            default: {
                throw new IllegalArgumentException("unsupported transport " + transport);
            }
        }
    }

    Resource getResource(LoadGenerator.Builder builder) throws Exception {
        String jsonPath = getResourceJSONPath();
        if (jsonPath != null) {
            Path path = Paths.get(jsonPath);
            return evaluateJSON(path);
        }
        String xmlPath = getResourceXMLPath();
        if (xmlPath != null) {
            Path path = Paths.get(xmlPath);
            return (Resource)new XmlConfiguration(org.eclipse.jetty.util.resource.Resource.newResource(path)).configure();
        }
        String groovyPath = getResourceGroovyPath();
        if (groovyPath != null) {
            Path path = Paths.get(groovyPath);
            try (Reader reader = Files.newBufferedReader(path)) {
                Map<String, Object> context = new HashMap<>();
                context.put("loadGeneratorBuilder", builder);
                return evaluateGroovy(reader, context);
            }
        }
        return new Resource("/");
    }

    static Resource evaluateJSON(Path profilePath) throws IOException {
        try (BufferedReader reader = Files.newBufferedReader(profilePath, StandardCharsets.UTF_8)) {
            return evaluateJSON(reader);
        }
    }

    static Resource evaluateJSON(Reader reader) {
        JSON json = new JSON();
        Resource resource = new Resource();
        resource.fromJSON((Map<?, ?>)json.parse(new JSON.ReaderSource(reader)));
        return resource;
    }

    static Resource evaluateGroovy(Reader script, Map<String, Object> context) {
        CompilerConfiguration config = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
        config.setDebug(true);
        config.setVerbose(true);
        Binding binding = new Binding(context);
        GroovyShell interpreter = new GroovyShell(binding, config);
        return (Resource)interpreter.evaluate(script);
    }

    Executor getExecutor() {
        QueuedThreadPool executor = new QueuedThreadPool(getExecutorMaxThreads());
        executor.setName("load-generator-executor");
        return executor;
    }
    
    Scheduler getScheduler() {
        return new ScheduledExecutorScheduler("load-generator-scheduler", false, getSchedulerMaxThreads());
    }
}
