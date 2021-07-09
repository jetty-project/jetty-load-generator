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
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.HdrHistogram.EncodableHistogram;
import org.HdrHistogram.HistogramLogReader;
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
import org.eclipse.jetty.util.ajax.JSON;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoadGeneratorStarterTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(LoadGeneratorStarterTest.class);

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
    public void testSimple() throws Exception {
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
    @Ignore("see FailFastTest")
    public void testFailFast() {
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
    public void testFromGroovyToJSON() throws Exception {
        try (Reader reader = Files.newBufferedReader(Paths.get("src/test/resources/tree_resources.groovy"))) {
            Resource resource = LoadGeneratorStarterArgs.evaluateGroovy(reader, Map.of());
            Path tmpPath = Files.createTempFile("resources_", ".tmp");
            tmpPath.toFile().deleteOnExit();
            try (BufferedWriter writer = Files.newBufferedWriter(tmpPath, StandardCharsets.UTF_8)) {
                JSON json = new JSON();
                writer.write(json.toJSON(resource));
            }
            Resource fromJson = LoadGeneratorStarterArgs.evaluateJSON(tmpPath);
            Assert.assertEquals(resource.descendantCount(), fromJson.descendantCount());
        }
    }

    @Test
    public void testCalculateDescendantCount() throws Exception {
        try (Reader reader = Files.newBufferedReader(Paths.get("src/test/resources/tree_resources.groovy"))) {
            Resource resource = LoadGeneratorStarterArgs.evaluateGroovy(reader, Map.of());
            Assert.assertEquals(17, resource.descendantCount());
        }
    }

    @Test
    public void testSimplestJSON() {
        String path = "/index.html";
        try (StringReader reader = new StringReader("{\"path\":\"" + path + "\"}")) {
            Resource resource = LoadGeneratorStarterArgs.evaluateJSON(reader);
            Assert.assertEquals(path, resource.getPath());
        }
    }

    @Test
    public void testFullJSON() {
        try (StringReader reader = new StringReader("" +
                "{" +
                "\"method\":\"POST\"," +
                "\"path\":\"/index.html\"," +
                "\"requestLength\":1," +
                "\"responseLength\":2," +
                "\"requestHeaders\":{\"Foo\":[\"Bar\"]}," +
                "\"resources\":[{\"path\":\"/styles.css\"}]" +
                "}")) {
            Resource resource = LoadGeneratorStarterArgs.evaluateJSON(reader);
            Assert.assertEquals("POST", resource.getMethod());
            Assert.assertEquals("/index.html", resource.getPath());
            Assert.assertEquals(1, resource.getRequestLength());
            Assert.assertEquals(2, resource.getResponseLength());
            Assert.assertEquals("Bar", resource.getRequestHeaders().get("Foo"));
            List<Resource> children = resource.getResources();
            Assert.assertEquals(1, children.size());
            Assert.assertEquals("/styles.css", children.get(0).getPath());
        }
    }

    @Test
    public void testStatsFile() throws Exception {
        Path statsPath = Files.createTempFile(Path.of("target"), "jlg-stats-", ".json");
        statsPath.toFile().deleteOnExit();
        String[] args = new String[]{
                "--port",
                Integer.toString(connector.getLocalPort()),
                "--iterations",
                "10",
                "--resource-rate",
                "10",
                "--stats-file",
                statsPath.toString()
        };
        LoadGeneratorStarter.main(args);

        try (BufferedReader reader = Files.newBufferedReader(statsPath, StandardCharsets.UTF_8)) {
            JSON json = new JSON();
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>)json.parse(new JSON.ReaderSource(reader));

            @SuppressWarnings("unchecked")
            Map<String, Object> configMap = (Map<String, Object>)map.get("config");
            LoadGenerator.Config config = new LoadGenerator.Config();
            config.fromJSON(configMap);
            Assert.assertEquals(connector.getLocalPort(), config.getPort());

            @SuppressWarnings("unchecked")
            Map<String, Object> reportMap = (Map<String, Object>)map.get("report");
            try (InputStream inputStream = new ByteArrayInputStream(((String)reportMap.get("histogram")).getBytes(StandardCharsets.UTF_8))) {
                HistogramLogReader histogramReader = new HistogramLogReader(inputStream);
                EncodableHistogram histogram = histogramReader.nextIntervalHistogram();
                Assert.assertNotNull(histogram);
            }
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
