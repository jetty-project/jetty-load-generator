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

package org.mortbay.jetty.load.generator;

import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import org.junit.After;
import org.mortbay.jetty.load.generator.resource.Resource;

public abstract class WebsiteLoadGeneratorTest {
    // A dump of the resources needed by the webtide.com website.
    private Resource resource = new Resource("/",
            new Resource("/styles.css").responseLength(1600),
            new Resource("/pagenavi-css.css").responseLength(426),
            new Resource("/style.css").responseLength(74900),
            new Resource("/genericicons.css").responseLength(27700),
            new Resource("/font-awesome.min.css").responseLength(28400),
            new Resource("/jquery.js").responseLength(95000),
            new Resource("/jquery-migrate.min.js").responseLength(9900),
            new Resource("/picturefill.min.js").responseLength(11600),
            new Resource("/jscripts.php").responseLength(842),
            new Resource("/cropped-WTLogo-2.png").responseLength(3900),
            new Resource("/pexels-photo-40120-1.jpeg").responseLength(143000),
            new Resource("/Keyboard.jpg").responseLength(90000),
            new Resource("/Jetty-Code-2x.jpg").responseLength(697000),
            new Resource("/rocket.png").responseLength(3700),
            new Resource("/aperture2.png").responseLength(2900),
            new Resource("/dev.png").responseLength(3500),
            new Resource("/jetty-avatar.png").responseLength(10000),
            new Resource("/megaphone.png").responseLength(2400),
            new Resource("/jquery.form.min.js").responseLength(14900),
            new Resource("/scripts.js").responseLength(11900),
            new Resource("/jquery.circle2.min.js").responseLength(22500),
            new Resource("/jquery.circle2.swipe.min.js").responseLength(9900),
            new Resource("/waypoints.min.js").responseLength(7400),
            new Resource("/jquery.counterup.min.js").responseLength(1000),
            new Resource("/navigation.min.js").responseLength(582),
            new Resource("/spacious-custom.min.js").responseLength(1300),
            new Resource("/jscripts-ftr-min.js").responseLength(998),
            new Resource("/wp-embed.min.js").responseLength(1400),
            new Resource("/wp-emoji-release.min.js").responseLength(11200),
            new Resource("/fontawesome-webfont.woff2").responseLength(70300)
    ).responseLength(30700);
    private Server server;
    private ServerConnector connector;
    private Scheduler scheduler;

    protected void prepareServer(ConnectionFactory connectionFactory, Handler handler)  throws Exception {
        QueuedThreadPool serverThreads = new QueuedThreadPool(5120);
        serverThreads.setName("server");
        server = new Server(serverThreads);
        connector = new ServerConnector(server, connectionFactory);
        server.addConnector(connector);
        server.setHandler(handler);

        scheduler = new ScheduledExecutorScheduler();
        server.addBean(scheduler, true);

        server.start();
    }

    protected LoadGenerator.Builder prepareLoadGenerator(HTTPClientTransportBuilder clientTransportBuilder) {
        return new LoadGenerator.Builder()
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

    protected Resource getResource() {
        return resource;
    }
}
