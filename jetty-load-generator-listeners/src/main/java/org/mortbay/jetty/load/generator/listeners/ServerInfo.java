//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
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

package org.mortbay.jetty.load.generator.listeners;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.log.Log;
import org.mortbay.jetty.load.generator.HTTPClientTransportBuilder;

public class ServerInfo
{
    private String jettyVersion;

    private int availableProcessors;

    private long totalMemory;

    private String gitHash;

    private String javaVersion;

    public String getJettyVersion()
    {
        return jettyVersion;
    }

    public int getAvailableProcessors()
    {
        return availableProcessors;
    }

    public long getTotalMemory()
    {
        return totalMemory;
    }

    public void setJettyVersion(String jettyVersion)
    {
        this.jettyVersion = jettyVersion;
    }

    public void setAvailableProcessors(int availableProcessors)
    {
        this.availableProcessors = availableProcessors;
    }

    public void setTotalMemory(long totalMemory)
    {
        this.totalMemory = totalMemory;
    }

    public String getGitHash()
    {
        return gitHash;
    }

    public void setGitHash(String gitHash)
    {
        this.gitHash = gitHash;
    }

    public String getJavaVersion()
    {
        return javaVersion;
    }

    public void setJavaVersion(String javaVersion)
    {
        this.javaVersion = javaVersion;
    }

    @Override
    public String toString()
    {
        return "ServerInfo{" + "jettyVersion='" + jettyVersion + '\'' + ", availableProcessors=" + availableProcessors +
                ", totalMemory=" + totalMemory + ", gitHash='" + gitHash + '\'' + ", javaVersion='" + javaVersion + '\'' +
                '}';
    }

    public static ServerInfo retrieveServerInfo(Request request, HttpClient httpClient)
        throws Exception
    {
            ContentResponse contentResponse = httpClient //
                .newRequest(request.host, request.port) //
                .scheme(request.scheme) //
                .path(request.path) //
                .send();
            if (contentResponse.getStatus() != HttpStatus.OK_200)
            {
                Log.getLogger(ServerInfo.class).info("fail to retrieve server info " +
                        contentResponse.getStatus() + ", content: " + contentResponse.getContentAsString());
            }
            return new ObjectMapper() //
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false) //
                .readValue(contentResponse.getContent(), ServerInfo.class);


    }

    public static class Request
    {
        public String scheme,host, path;
        public int port;

        public Request(String scheme, String host, String path, int port)
        {
            this.scheme = scheme;
            this.host = host;
            this.path = path;
            this.port = port;
        }
    }

}
