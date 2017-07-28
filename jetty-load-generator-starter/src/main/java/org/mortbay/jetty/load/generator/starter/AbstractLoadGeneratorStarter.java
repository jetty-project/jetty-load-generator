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

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.mortbay.jetty.load.generator.HTTP1ClientTransportBuilder;
import org.mortbay.jetty.load.generator.HTTP2ClientTransportBuilder;
import org.mortbay.jetty.load.generator.HTTPClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

public abstract class AbstractLoadGeneratorStarter {
    private Logger logger = Log.getLogger(getClass());
    private LoadGeneratorStarterArgs starterArgs;
    private ExecutorService executorService;
    private Resource resource;
    private Request.Listener[] listeners;

    public AbstractLoadGeneratorStarter(LoadGeneratorStarterArgs runnerArgs) {
        this.starterArgs = runnerArgs;
    }

    public void run() throws Exception {
        LoadGenerator.Builder builder = new LoadGenerator.Builder();
        Resource resource = getResource(builder);
        builder.threads(starterArgs.getThreads())
                .warmupIterationsPerThread(starterArgs.getWarmupIterations())
                .iterationsPerThread(starterArgs.getIterations())
                .usersPerThread(starterArgs.getUsers())
                .channelsPerUser(starterArgs.getChannelsPerUser())
                .resource(resource)
                .resourceRate(starterArgs.getResourceRate())
                .httpClientTransportBuilder(httpClientTransportBuilder())
                .sslContextFactory(sslContextFactory())
                .scheme(starterArgs.getScheme())
                .host(starterArgs.getHost())
                .port(starterArgs.getPort())
                .maxRequestsQueued(starterArgs.getMaxRequestsQueued());

        long runFor = starterArgs.getRunningTime();
        if (runFor > 0) {
            builder = builder.runFor(runFor, starterArgs.getRunningTimeUnit());
        }

        ExecutorService executor = getExecutorService();
        if (executor != null) {
            builder = builder.executor(executor);
        }

        for (Resource.Listener listener : getResourceListeners()) {
            builder = builder.resourceListener(listener);
        }

        for (Request.Listener listener : getRequestListeners()) {
            builder = builder.requestListener(listener);
        }

        for (LoadGenerator.Listener listener : getLoadGeneratorListeners()) {
            builder = builder.listener(listener);
        }

        LoadGenerator loadGenerator = builder.build();
        logger.info("load generator config: {}", loadGenerator.getConfig());
        logger.info("load generation begin");
        CompletableFuture<Void> cf = loadGenerator.begin();
        cf.whenComplete((x, f) -> {
            if (f == null) {
                logger.info("load generation complete");
            } else {
                logger.info("load generation failure", f);
            }
        }).join();
    }

    protected LoadGenerator.Listener[] getLoadGeneratorListeners() {
        return new LoadGenerator.Listener[0];
    }

    protected Resource.Listener[] getResourceListeners() {
        return new Resource.Listener[0];
    }

    protected Request.Listener[] getRequestListeners() {
        return listeners == null ? new Request.Listener[0] : this.listeners;
    }

    protected void setRequestListeners(Request.Listener[] listeners) {
        this.listeners = listeners;
    }

    public ExecutorService getExecutorService() {
        return executorService;
    }

    public void setExecutorService(ExecutorService executor) {
        this.executorService = executor;
    }

    public Resource getResource(LoadGenerator.Builder builder) throws Exception {
        if (resource != null) {
            return resource;
        }

        String jsonPath = starterArgs.getResourceJSONPath();
        if (jsonPath != null) {
            Path path = Paths.get(jsonPath);
            if (Files.exists(path)) {
                return resource = evaluateJSON(path);
            }
        }
        String xmlPath = starterArgs.getResourceXMLPath();
        if (xmlPath != null) {
            Path path = Paths.get(xmlPath);
            try (InputStream inputStream = Files.newInputStream(path)) {
                return resource = (Resource)new XmlConfiguration(inputStream).configure();
            }
        }
        String groovyPath = starterArgs.getResourceGroovyPath();
        if (groovyPath != null) {
            Path path = Paths.get(groovyPath);
            try (Reader reader = Files.newBufferedReader(path)) {
                Map<String, Object> context = new HashMap<>();
                context.put("loadGeneratorBuilder", builder);
                return resource = (Resource)evaluateGroovy(reader, context);
            }
        }

        throw new IllegalArgumentException("resource not defined");
    }

    protected static Resource evaluateJSON(Path profilePath) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        return objectMapper.readValue(profilePath.toFile(), Resource.class);
    }

    protected static String writeAsJsonTmp(Resource resource) throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
        Path tmpPath = Files.createTempFile("profile", ".tmp");
        objectMapper.writeValue(tmpPath.toFile(), resource);
        return tmpPath.toString();
    }

    protected static Object evaluateGroovy(Reader script, Map<String, Object> context) throws Exception {
        CompilerConfiguration config = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
        config.setDebug(true);
        config.setVerbose(true);
        Binding binding = new Binding(context);
        GroovyShell interpreter = new GroovyShell(binding, config);
        return interpreter.evaluate(script);
    }

    public HTTPClientTransportBuilder httpClientTransportBuilder() {
        LoadGeneratorStarterArgs.Transport transport = getStarterArgs().getTransport();
        switch (transport) {
            case HTTP:
            case HTTPS: {
                return new HTTP1ClientTransportBuilder().selectors(getStarterArgs().getSelectors());
            }
            case H2C:
            case H2: {
                return new HTTP2ClientTransportBuilder().selectors(getStarterArgs().getSelectors());
            }
            default: {
                throw new IllegalArgumentException("unsupported transport " + transport);
            }
        }
    }

    public SslContextFactory sslContextFactory() {
        // FIXME make this more configurable
        SslContextFactory sslContextFactory = new SslContextFactory(true);
        return sslContextFactory;
    }

    public LoadGeneratorStarterArgs getStarterArgs() {
        return starterArgs;
    }
}
