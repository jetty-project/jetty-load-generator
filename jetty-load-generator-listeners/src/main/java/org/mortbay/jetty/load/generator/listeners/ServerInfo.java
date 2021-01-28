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

package org.mortbay.jetty.load.generator.listeners;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;

/**
 * <p>A value class representing server-side information.</p>
 * <p>A server would expose a well known path such as {@code /.well-known/serverInfo}
 * that can be invoked via:</p>
 * <pre>
 * ServerInfo serverInfo = ServerInfo.retrieveServerInfo(httpClient, URI.create("http://localhost:8080/.well-known/serverInfo"));
 * </pre>
 * The server should respond with JSON content with the following format:</p>
 * <pre>
 * {
 *     "serverVersion": "jetty-9.4.x",
 *     "processorCount": 12,
 *     "totalMemory": 34359738368,
 *     "gitHash": "0123456789abcdef",
 *     "javaVersion": "11.0.10+9"
 * }
 * </pre>
 */
public class ServerInfo {
    private String serverVersion;
    private int processorCount;
    private long totalMemory;
    private String gitHash;
    private String javaVersion;

    public String getServerVersion() {
        return serverVersion;
    }

    public void setServerVersion(String serverVersion) {
        this.serverVersion = serverVersion;
    }

    public int getProcessorCount() {
        return processorCount;
    }

    public void setProcessorCount(int processorCount) {
        this.processorCount = processorCount;
    }

    public long getTotalMemory() {
        return totalMemory;
    }

    public void setTotalMemory(long totalMemory) {
        this.totalMemory = totalMemory;
    }

    public String getGitHash() {
        return gitHash;
    }

    public void setGitHash(String gitHash) {
        this.gitHash = gitHash;
    }

    public String getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion) {
        this.javaVersion = javaVersion;
    }

    @Override
    public String toString() {
        return String.format("%s{jettyVersion='%s', availableProcessors=%d, totalMemory=%d, gitHash='%s', javaVersion='%s'}",
                getClass().getSimpleName(), getServerVersion(), getProcessorCount(), getTotalMemory(), getGitHash(), getJavaVersion());
    }

    public static ServerInfo retrieveServerInfo(HttpClient httpClient, URI uri) throws Exception {
        ContentResponse contentResponse = httpClient.newRequest(uri)
                .timeout(5, TimeUnit.SECONDS)
                .send();
        if (contentResponse.getStatus() != HttpStatus.OK_200) {
            throw new IOException("could not retrieve server info: HTTP " + contentResponse.getStatus());
        }
        return new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(contentResponse.getContent(), ServerInfo.class);
    }
}
