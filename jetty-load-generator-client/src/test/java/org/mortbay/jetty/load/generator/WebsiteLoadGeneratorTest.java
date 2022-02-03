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

import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.CustomRequestLog;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.RequestLogWriter;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.StatisticsHandler;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.junit.After;

public abstract class WebsiteLoadGeneratorTest {
    protected Resource resource;
    protected Server server;
    protected ServerConnector connector;
    protected Scheduler scheduler;
    protected StatisticsHandler serverStats;

    public WebsiteLoadGeneratorTest() {
        // A dump of the resources needed by the webtide.com website.
        HttpFields headers = new HttpFields();
        headers.put("User-Agent", "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:52.0) Gecko/20100101 Firefox/52.0");
        headers.put("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
        headers.put("Accept-Language", "en-US,en;q=0.5");
        headers.put("Cookie", "__utma=124097164.2025215041.1465995519.1483973120.1485461487.58; __utmz=124097164.1480932641.29.9.utmcsr=localhost:8080|utmccn=(referral)|utmcmd=referral|utmcct=/; wp-settings-3=editor%3Dhtml%26wplink%3D1%26post_dfw%3Doff%26posts_list_mode%3Dlist; wp-settings-time-3=1483536385; wp-settings-time-4=1485794804; wp-settings-4=editor%3Dhtml; _ga=GA1.2.2025215041.1465995519; wordpress_google_apps_login=30a7b62f9ae5db1653367cafa3accacd; PHPSESSID=r8rr7hnl7kttpq40q7bkbcn5c2; ckon1703=sject1703_bfc34a0618c85; JCS_INENREF=; JCS_INENTIM=1489507850637; _gat=1");
        resource = new Resource("/",
                new Resource("/styles.css").requestHeaders(headers).responseLength(1600),
                new Resource("/pagenavi-css.css").requestHeaders(headers).responseLength(426),
                new Resource("/style.css").requestHeaders(headers).responseLength(74900),
                new Resource("/genericicons.css").requestHeaders(headers).responseLength(27700),
                new Resource("/font-awesome.min.css").requestHeaders(headers).responseLength(28400),
                new Resource("/jquery.js").requestHeaders(headers).responseLength(95000),
                new Resource("/jquery-migrate.min.js").requestHeaders(headers).responseLength(9900),
                new Resource("/picturefill.min.js").requestHeaders(headers).responseLength(11600),
                new Resource("/jscripts.php").requestHeaders(headers).responseLength(842),
                new Resource("/cropped-WTLogo-2.png").requestHeaders(headers).responseLength(3900),
                new Resource("/pexels-photo-40120-1.jpeg").requestHeaders(headers).responseLength(143000),
                new Resource("/Keyboard.jpg").requestHeaders(headers).responseLength(90000),
                new Resource("/Jetty-Code-2x.jpg").requestHeaders(headers).responseLength(697000),
                new Resource("/rocket.png").requestHeaders(headers).responseLength(3700),
                new Resource("/aperture2.png").requestHeaders(headers).responseLength(2900),
                new Resource("/dev.png").requestHeaders(headers).responseLength(3500),
                new Resource("/jetty-avatar.png").requestHeaders(headers).responseLength(10000),
                new Resource("/megaphone.png").requestHeaders(headers).responseLength(2400),
                new Resource("/jquery.form.min.js").requestHeaders(headers).responseLength(14900),
                new Resource("/scripts.js").requestHeaders(headers).responseLength(11900),
                new Resource("/jquery.circle2.min.js").requestHeaders(headers).responseLength(22500),
                new Resource("/jquery.circle2.swipe.min.js").requestHeaders(headers).responseLength(9900),
                new Resource("/waypoints.min.js").requestHeaders(headers).responseLength(7400),
                new Resource("/jquery.counterup.min.js").requestHeaders(headers).responseLength(1000),
                new Resource("/navigation.min.js").requestHeaders(headers).responseLength(582),
                new Resource("/spacious-custom.min.js").requestHeaders(headers).responseLength(1300),
                new Resource("/jscripts-ftr-min.js").requestHeaders(headers).responseLength(998),
                new Resource("/wp-embed.min.js").requestHeaders(headers).responseLength(1400),
                new Resource("/wp-emoji-release.min.js").requestHeaders(headers).responseLength(11200),
                new Resource("/fontawesome-webfont.woff2").requestHeaders(headers).responseLength(70300)
        ).requestHeaders(headers).responseLength(30700);
    }

    protected void prepareServer(ConnectionFactory connectionFactory, Handler handler) throws Exception {
        server = new Server();
        connector = new ServerConnector(server, connectionFactory);
        server.addConnector(connector);

        // The request log ensures that the request
        // is inspected how an application would do.
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        CustomRequestLog requestLog = new CustomRequestLog(new RequestLogWriter() {
            @Override
            public void write(String log) {
                // Do not write the log.
            }
        }, CustomRequestLog.NCSA_FORMAT);
        requestLogHandler.setRequestLog(requestLog);
        serverStats = new StatisticsHandler();

        server.setHandler(requestLogHandler);
        requestLogHandler.setHandler(serverStats);
        serverStats.setHandler(handler);

        scheduler = new ScheduledExecutorScheduler();
        server.addBean(scheduler, true);

        server.start();
    }

    protected LoadGenerator.Builder prepareLoadGenerator(HTTPClientTransportBuilder clientTransportBuilder) {
        return LoadGenerator.builder()
                .threads(1)
                .port(connector.getLocalPort())
                .httpClientTransportBuilder(clientTransportBuilder)
                .resource(resource)
                .scheduler(scheduler);
    }

    @After
    public void dispose() throws Exception {
        if (server != null) {
            server.stop();
        }
    }
}
