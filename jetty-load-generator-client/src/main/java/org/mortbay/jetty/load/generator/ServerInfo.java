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
import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.eclipse.jetty.client.BufferingResponseListener;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Result;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * <p>A value class representing server-side information.</p>
 * <p>A server would expose a well known path such as {@code /.well-known/serverInfo}
 * that can be invoked via:</p>
 * <pre>
 * ServerInfo serverInfo = ServerInfo.retrieveServerInfo(httpClient, URI.create("http://localhost:8080/.well-known/serverInfo"));
 * </pre>
 * <p>The server should respond with JSON content with the following format:</p>
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
public class ServerInfo implements JSON.Convertible {
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
    public void toJSON(JSON.Output out) {
        out.add("serverVersion", getServerVersion());
        out.add("processorCount", getProcessorCount());
        out.add("totalMemory", getTotalMemory());
        out.add("gitHash", getGitHash());
        out.add("javaVersion", getJavaVersion());
    }

    @Override
    public void fromJSON(Map<String, Object> map) {
        setServerVersion((String)map.get("serverVersion"));
        Number processorCount = (Number)map.get("processorCount");
        if (processorCount != null) {
            setProcessorCount(processorCount.intValue());
        }
        Number totalMemory = (Number)map.get("totalMemory");
        if (totalMemory != null) {
            setTotalMemory(totalMemory.longValue());
        }
        setGitHash((String)map.get("gitHash"));
        setJavaVersion((String)map.get("javaVersion"));
    }

    @Override
    public String toString() {
        return String.format("%s{jettyVersion=%s, availableProcessors=%d, totalMemory=%d, gitHash=%s, javaVersion=%s}",
                getClass().getSimpleName(), getServerVersion(), getProcessorCount(), getTotalMemory(), getGitHash(), getJavaVersion());
    }

    public static CompletableFuture<ServerInfo> retrieveServerInfo(HttpClient httpClient, URI uri) {
        CompletableFuture<ServerInfo> complete = new CompletableFuture<>();
        httpClient.newRequest(uri).send(new BufferingResponseListener() {
            @Override
            public void onComplete(Result result) {
                if (result.isSucceeded()) {
                    int status = result.getResponse().getStatus();
                    if (status == HttpStatus.OK_200) {
                        ServerInfo serverInfo = new ServerInfo();
                        @SuppressWarnings("unchecked")
                        Map<String, Object> map = (Map<String, Object>)new JSON().parse(new JSON.StringSource(getContentAsString()));
                        serverInfo.fromJSON(map);
                        complete.complete(serverInfo);
                    } else {
                        complete.completeExceptionally(new IOException("could not retrieve server info: HTTP " + status));
                    }
                } else {
                    complete.completeExceptionally(result.getFailure());
                }
            }
        });
        return complete;
    }
}
