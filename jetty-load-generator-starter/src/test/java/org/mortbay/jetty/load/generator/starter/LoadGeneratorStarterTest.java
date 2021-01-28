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

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.StatisticsServlet;
import org.eclipse.jetty.util.IO;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;

public class LoadGeneratorStarterTest {
    private static final Logger LOGGER = Log.getLogger(LoadGeneratorStarterTest.class);

    private Server server;
    private ServerConnector connector;
    private TestServlet testServlet;

    @Before
    public void startJetty() throws Exception {
        QueuedThreadPool serverThreads = new QueuedThreadPool();
        serverThreads.setName("server");
        server = new Server(serverThreads);
        connector = new ServerConnector(server, new HttpConnectionFactory(new HttpConfiguration()));
        server.addConnector(connector);
        StatisticsHandler statisticsHandler = new StatisticsHandler();
        server.setHandler(statisticsHandler);
        ServletContextHandler statsContext = new ServletContextHandler(statisticsHandler, "/");
        statsContext.addServlet(new ServletHolder(new StatisticsServlet()), "/stats");
        testServlet = new TestServlet(connector);
        statsContext.addServlet(new ServletHolder(testServlet), "/");
        server.start();
    }

    @After
    public void stopJetty() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    public void simpleTest() {
        String[] args = new String[]{
                "--warmup-iterations",
                "10",
                "-h",
                "localhost",
                "--port",
                Integer.toString(connector.getLocalPort()),
                "--running-time",
                "10",
                "--running-time-unit",
                "s",
                "--resource-rate",
                "3",
                "--transport",
                "http",
                "--users",
                "3",
                "--resource-groovy-path",
                "src/test/resources/tree_resources.groovy"
        };
        LoadGeneratorStarter.main(args);
        int getNumber = testServlet.getNumber.get();
        LOGGER.debug("received get: {}", getNumber);
        Assert.assertTrue("getNumber return: " + getNumber, getNumber > 10);
    }

    @Test
    public void failFast() {
        String[] args = new String[]{
                "--warmup-iterations",
                "10",
                "-h",
                "localhost",
                "--port",
                Integer.toString(connector.getLocalPort()),
                "--running-time",
                "10",
                "--running-time-unit",
                "s",
                "--resource-rate",
                "3",
                "--transport",
                "http",
                "--users",
                "1",
                "--resource-groovy-path",
                "src/test/resources/single_fail_resource.groovy"
        };
        LoadGeneratorStarterArgs starterArgs = LoadGeneratorStarter.parse(args);
        LoadGenerator.Builder builder = LoadGeneratorStarter.configure(starterArgs);

        AtomicInteger onFailure = new AtomicInteger(0);
        AtomicInteger onCommit = new AtomicInteger(0);
        Request.Listener.Adapter requestListener = new Request.Listener.Adapter() {
            @Override
            public void onFailure(Request request, Throwable failure) {
                LOGGER.info("fail: {}", onFailure.incrementAndGet());
            }

            @Override
            public void onCommit(Request request) {
                LOGGER.info("onCommit: {}", onCommit.incrementAndGet());
            }
        };
        builder.requestListener(requestListener);

        try {
            LoadGeneratorStarter.run(builder.build());
            Assert.fail();
        } catch (Exception x) {
            // Expected.
        }

        int getNumber = testServlet.getNumber.get();
        Assert.assertEquals(5, getNumber);
        Assert.assertTrue(onFailure.get() < 10);
    }

    @Test
    public void fromGroovyToJSON() throws Exception {
        try (Reader reader = Files.newBufferedReader(Paths.get("src/test/resources/tree_resources.groovy"))) {
            Resource resource = LoadGeneratorStarterArgs.evaluateGroovy(reader, Collections.emptyMap());
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);
            Path tmpPath = Files.createTempFile("profile", ".tmp");
            objectMapper.writeValue(tmpPath.toFile(), resource);
            Resource fromJson = LoadGeneratorStarterArgs.evaluateJSON(tmpPath);
            Assert.assertEquals(resource.descendantCount(), fromJson.descendantCount());
        }
    }

    @Test
    public void calculate_descendant_number() throws Exception {
        try (Reader reader = Files.newBufferedReader(Paths.get("src/test/resources/tree_resources.groovy"))) {
            Resource resource = LoadGeneratorStarterArgs.evaluateGroovy(reader, Collections.emptyMap());
            Assert.assertEquals(17, resource.descendantCount());
        }
    }

    private static class TestServlet extends HttpServlet {
        private final AtomicInteger getNumber = new AtomicInteger(0);
        private final AtomicInteger postNumber = new AtomicInteger(0);
        private final ServerConnector connector;

        private TestServlet(ServerConnector connector) {
            this.connector = connector;
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            String method = request.getMethod().toUpperCase(Locale.ENGLISH);
            switch (method) {
                case "GET": {
                    String fail = request.getParameter("fail");
                    if (fail != null) {
                        if (getNumber.get() >= Integer.parseInt(fail)) {
                            try {
                                connector.stop();
                            } catch (Exception e) {
                                throw new RuntimeException(e.getMessage(), e);
                            }
                        }
                    }
                    response.getOutputStream().write("Jetty rocks!!".getBytes());
                    response.flushBuffer();
                    getNumber.addAndGet(1);
                    break;
                }
                case "POST": {
                    IO.copy(request.getInputStream(), response.getOutputStream());
                    postNumber.addAndGet(1);
                    break;
                }
            }
        }
    }
}
