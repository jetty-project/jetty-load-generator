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

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.Parameter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.mortbay.jetty.load.generator.HTTP1ClientTransportBuilder;
import org.mortbay.jetty.load.generator.HTTP2ClientTransportBuilder;
import org.mortbay.jetty.load.generator.HTTPClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

public class LoadGeneratorStarterArgs {
    @Parameter(names = {"--threads", "-t"}, description = "LoadGenerator threads")
    private int threads = 1;

    @Parameter(names = {"--warmup-iterations", "-wi"}, description = "Warmup iterations per thread")
    private int warmupIterations;

    @Parameter(names = {"--iterations", "-i"}, description = "Iterations per thread")
    private int iterations = 1;

    @Parameter(names = {"--running-time", "-rt"}, description = "LoadGenerator Running Time")
    private long runningTime;

    @Parameter(names = {"--running-time-unit", "-rtu"}, description = "LoadGenerator Running Time Unit (h/m/s/ms)")
    private String runningTimeUnit = "s";

    @Parameter(names = {"--users", "-u"}, description = "Users per thread")
    private int users = 1;

    @Parameter(names = {"--channels-per-user", "-cpu"}, description = "Channels/Connections per user")
    private int channelsPerUser = 128;

    @Parameter(names = {"--resource-xml-path", "-rxp"}, description = "Path to resource XML file")
    private String resourceXMLPath;

    @Parameter(names = {"--resource-json-path", "-rjp"}, description = "Path to resource JSON file")
    private String resourceJSONPath;

    @Parameter(names = {"--resource-groovy-path", "-rgp"}, description = "Path to resource Groovy file")
    private String resourceGroovyPath;

    @Parameter(names = {"--resource-rate", "-rr"}, description = "Resource rate / second")
    private int resourceRate = 1;

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

    @Parameter(names = {"--max-requests-queued", "-mrq"}, description = "Max Requests Queued")
    private int maxRequestsQueued = 1024;

    @Parameter(names = {"--stats-file", "-sf"}, description = "Statistics output file")
    private String statsFile;

    @Parameter(names = {"--display-stats", "-ds"}, description = "Display statistics")
    private boolean displayStats;

    @Parameter(names = {"--help"}, description = "Displays usage")
    private boolean help;

    public String getResourceXMLPath() {
        return resourceXMLPath;
    }

    public void setResourceXMLPath(String resourceXMLPath) {
        this.resourceXMLPath = resourceXMLPath;
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

    public int getUsers() {
        return users;
    }

    public void setUsers(int users) {
        this.users = users;
    }

    public int getResourceRate() {
        return resourceRate;
    }

    public void setResourceRate(int resourceRate) {
        this.resourceRate = resourceRate;
    }

    public Transport getTransport() {
        switch (this.transport) {
            case "http":
                return Transport.HTTP;
            case "https":
                return Transport.HTTPS;
            case "h2":
                return Transport.H2;
            case "h2c":
                return Transport.H2C;
            default:
                throw new IllegalArgumentException("unsupported transport " + transport);
        }
    }

    public void setTransport(String transport) {
        this.transport = transport != null ? transport.toLowerCase() : "";
    }

    public int getSelectors() {
        return selectors;
    }

    public void setSelectors(int selectors) {
        this.selectors = selectors;
    }

    public boolean isHelp() {
        return help;
    }

    public void setHelp(boolean help) {
        this.help = help;
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

    public int getIterations() {
        return iterations;
    }

    public void setIterations(int iterations) {
        this.iterations = iterations;
    }

    public String getResourceJSONPath() {
        return resourceJSONPath;
    }

    public void setResourceJSONPath(String resourceJSONPath) {
        this.resourceJSONPath = resourceJSONPath;
    }

    public String getStatsFile() {
        return statsFile;
    }

    public void setStatsFile(String statsFile) {
        this.statsFile = statsFile;
    }

    public String getResourceGroovyPath() {
        return resourceGroovyPath;
    }

    public void setResourceGroovyPath(String resourceGroovyPath) {
        this.resourceGroovyPath = resourceGroovyPath;
    }

    public boolean isDisplayStats() {
        return displayStats;
    }

    public void setDisplayStats(boolean displayStats) {
        this.displayStats = displayStats;
    }

    public int getWarmupIterations() {
        return warmupIterations;
    }

    public void setWarmupIterations(int warmupIterations) {
        this.warmupIterations = warmupIterations;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public int getMaxRequestsQueued() {
        return maxRequestsQueued;
    }

    public void setMaxRequestsQueued(int maxRequestsQueued) {
        this.maxRequestsQueued = maxRequestsQueued;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getChannelsPerUser() {
        return channelsPerUser;
    }

    public void setChannelsPerUser(int channelsPerUser) {
        this.channelsPerUser = channelsPerUser;
    }

    public Resource getResource(LoadGenerator.Builder builder) throws Exception {
        String jsonPath = getResourceJSONPath();
        if (jsonPath != null) {
            Path path = Paths.get(jsonPath);
            return evaluateJSON(path);
        }
        String xmlPath = getResourceXMLPath();
        if (xmlPath != null) {
            Path path = Paths.get(xmlPath);
            try (InputStream inputStream = Files.newInputStream(path)) {
                return (Resource)new XmlConfiguration(inputStream).configure();
            }
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
        throw new IllegalArgumentException("resource not defined");
    }

    public static Resource evaluateJSON(Path profilePath) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return objectMapper.readValue(profilePath.toFile(), Resource.class);
    }

    public static Resource evaluateGroovy(Reader script, Map<String, Object> context) {
        CompilerConfiguration config = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
        config.setDebug(true);
        config.setVerbose(true);
        Binding binding = new Binding(context);
        GroovyShell interpreter = new GroovyShell(binding, config);
        return (Resource)interpreter.evaluate(script);
    }

    public HTTPClientTransportBuilder getHttpClientTransportBuilder() {
        Transport transport = getTransport();
        switch (transport) {
            case HTTP:
            case HTTPS: {
                return new HTTP1ClientTransportBuilder().selectors(getSelectors());
            }
            case H2C:
            case H2: {
                return new HTTP2ClientTransportBuilder().selectors(getSelectors());
            }
            default: {
                throw new IllegalArgumentException("unsupported transport " + transport);
            }
        }
    }

    public enum Transport {
        HTTP,
        HTTPS,
        H2C,
        H2
    }
}
