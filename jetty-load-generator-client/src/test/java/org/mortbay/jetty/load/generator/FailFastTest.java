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

package org.mortbay.jetty.load.generator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.StatisticsServlet;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FailFastTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(FailFastTest.class);

    private Server server;
    private ServerConnector connector;

    @Before
    public void startJetty() throws Exception {
        StatisticsHandler statisticsHandler = new StatisticsHandler();
        server = new Server(new ExecutorThreadPool(5120));
        connector = new ServerConnector(server, new HttpConnectionFactory(new HttpConfiguration()));
        server.addConnector(connector);
        server.setHandler(statisticsHandler);
        ServletContextHandler statsContext = new ServletContextHandler(statisticsHandler, "/");
        statsContext.addServlet(new ServletHolder(new StatisticsServlet()), "/stats");
        ServerStopServlet servlet = new ServerStopServlet(server);
        statsContext.addServlet(new ServletHolder(servlet), "/");
        statsContext.setSessionHandler(new SessionHandler());
        server.start();
    }

    @After
    public void stopJetty() throws Exception {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    // Other load test tools continue to send load even if the server is down.
    @Ignore
    public void testFailFastOnServerStop() {
        AtomicInteger onFailure = new AtomicInteger(0);
        AtomicInteger onCommit = new AtomicInteger(0);
        LoadGenerator.Builder builder = LoadGenerator.builder()
                .host("localhost")
                .port(connector.getLocalPort())
                .resource(new Resource("/index.html?fail=5"))
                .warmupIterationsPerThread(1)
                .usersPerThread(1)
                .threads(1)
                .resourceRate(5)
                .iterationsPerThread(25)
                .requestListener(new Request.Listener.Adapter() {
                    @Override
                    public void onFailure(Request request, Throwable failure) {
                        LOGGER.info("fail: {}", onFailure.incrementAndGet());
                    }

                    @Override
                    public void onCommit(Request request) {
                        LOGGER.info("onCommit: {}", onCommit.incrementAndGet());
                    }
                });

        try {
            builder.build().begin().get(15, TimeUnit.SECONDS);
            Assert.fail();
        } catch (Exception ignored) {
        }

        LOGGER.info("onFailure: {}, onCommit: {}", onFailure, onCommit);
        int onFailureCall = onFailure.get();
        // The value is really dependant on machine...
        Assert.assertTrue("onFailureCall is " + onFailureCall, onFailureCall < 10);
    }

    private static class ServerStopServlet extends HttpServlet {
        private final AtomicInteger requests = new AtomicInteger();
        private final Server server;

        private ServerStopServlet(Server server) {
            this.server = server;
        }

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (requests.incrementAndGet() > Integer.parseInt(request.getParameter("fail"))) {
                new Thread(() -> LifeCycle.stop(server)).start();
            }
            response.getOutputStream().write("Jetty rocks!!".getBytes(StandardCharsets.UTF_8));
            response.flushBuffer();
        }
    }
}
