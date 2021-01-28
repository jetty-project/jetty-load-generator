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

package org.mortbay.jetty.load.generator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.Objects;
import java.util.stream.Collectors;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.junit.Assert;
import org.junit.Test;

public class ResourceBuildTest {
    @Test
    public void testSimpleBuild() {
        Resource resourceProfile = new Resource(new Resource("/index.html").requestLength(1024));

        Assert.assertEquals(1, resourceProfile.getResources().size());
        Assert.assertEquals("/index.html", resourceProfile.getResources().get(0).getPath());
        Assert.assertEquals(1024, resourceProfile.getResources().get(0).getRequestLength());
        Assert.assertEquals("GET", resourceProfile.getResources().get(0).getMethod());
    }

    @Test
    public void testSimpleTwoResources() {
        Resource resourceProfile = new Resource(
                new Resource("/index.html").requestLength(1024),
                new Resource("/beer.html").requestLength(2048).method(HttpMethod.POST.asString())
        );

        Assert.assertEquals(2, resourceProfile.getResources().size());
        Assert.assertEquals("/index.html", resourceProfile.getResources().get(0).getPath());
        Assert.assertEquals(1024, resourceProfile.getResources().get(0).getRequestLength());
        Assert.assertEquals("GET", resourceProfile.getResources().get(0).getMethod());
        Assert.assertEquals("/beer.html", resourceProfile.getResources().get(1).getPath());
        Assert.assertEquals(2048, resourceProfile.getResources().get(1).getRequestLength());
        Assert.assertEquals("POST", resourceProfile.getResources().get(1).getMethod());
    }

    @Test
    public void testWebsiteTree() {
        Resource sample = new Resource(
                new Resource("index.html",
                        new Resource("/style.css",
                                new Resource("/logo.gif"),
                                new Resource("/spacer.png")
                        ),
                        new Resource("/fancy.css"),
                        new Resource("/script.js",
                                new Resource("/library.js"),
                                new Resource("/morestuff.js")
                        ),
                        new Resource("/anotherScript.js"),
                        new Resource("/iframeContents.html"),
                        new Resource("/moreIframeContents.html"),
                        new Resource("/favicon.ico")
                ));

        assertWebsiteTree(sample);
    }

    @Test
    public void testWebsiteTreeWithXML() throws Exception {
        URL xml = Thread.currentThread().getContextClassLoader().getResource("website_profile.xml");
        Resource sample = (Resource)new XmlConfiguration(Objects.requireNonNull(org.eclipse.jetty.util.resource.Resource.newResource(xml))).configure();
        assertWebsiteTree(sample);
    }

    @Test
    public void testWebsiteTreeWithGroovy() throws Exception {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("website_profile.groovy")) {
            Resource sample = (Resource)evaluateScript(read(inputStream));
            assertWebsiteTree(sample);
        }
    }

    private static String read(InputStream input) throws IOException {
        try (BufferedReader buffer = new BufferedReader(new InputStreamReader(input))) {
            return buffer.lines().collect(Collectors.joining(System.lineSeparator()));
        }
    }

    public Object evaluateScript(String script) {
        CompilerConfiguration config = new CompilerConfiguration(CompilerConfiguration.DEFAULT);
        config.setDebug(true);
        config.setVerbose(true);
        GroovyShell interpreter = new GroovyShell(config);
        return interpreter.evaluate(script);
    }

    protected void assertWebsiteTree(Resource sample) {
        /*
        GET index.html
                style.css
                    logo.gif
                    spacer.png
                fancy.css
                script.js
                    library.js
                    morestuff.js
                anotherScript.js
                iframeContents.html
                moreIframeContents.html
                favicon.ico
        */

        Assert.assertEquals(1, sample.getResources().size());
        Assert.assertEquals(7, sample.getResources().get(0).getResources().size());
        Assert.assertEquals("/style.css", sample.getResources().get(0).getResources().get(0).getPath());
        Assert.assertEquals("/logo.gif", sample.getResources().get(0)
                .getResources().get(0).getResources().get(0).getPath());
        Assert.assertEquals("/spacer.png", sample.getResources().get(0)
                .getResources().get(0).getResources().get(1).getPath());
        Assert.assertEquals(2, sample.getResources().get(0)
                .getResources().get(0).getResources().size());
        Assert.assertEquals(2, sample.getResources().get(0)
                .getResources().get(2).getResources().size());
        Assert.assertEquals("/library.js", sample.getResources().get(0)
                .getResources().get(2).getResources().get(0).getPath());
        Assert.assertEquals("/morestuff.js", sample.getResources().get(0)
                .getResources().get(2).getResources().get(1).getPath());
        Assert.assertEquals("/anotherScript.js", sample.getResources().get(0)
                .getResources().get(3).getPath());
        Assert.assertEquals("/moreIframeContents.html", sample.getResources().get(0)
                .getResources().get(5).getPath());
        Assert.assertEquals("/favicon.ico", sample.getResources().get(0)
                .getResources().get(6).getPath());
    }
}
