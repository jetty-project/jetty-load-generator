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

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;

import org.eclipse.jetty.http.HttpMethod;

public class Resource {
    private final List<Resource> resources = new ArrayList<>();
    private String method = HttpMethod.GET.asString();
    private String path = "/";
    private int requestLength;
    private int responseLength;

    public Resource() {
        this((String)null);
    }

    public Resource(Resource... resources) {
        this(null, resources);
    }

    public Resource(String path) {
        this(path, new Resource[0]);
    }

    public Resource(String path, Resource... resources) {
        this.path = path;
        if (resources != null) {
            Collections.addAll(this.resources, resources);
        }
    }

    public Resource method(String method) {
        this.method = method;
        return this;
    }

    public String getMethod() {
        return method;
    }

    public Resource path(String path) {
        this.path = path.startsWith("/") ? path : "/" + path;
        return this;
    }

    public String getPath() {
        return path;
    }

    public Resource requestLength(int requestLength) {
        this.requestLength = requestLength;
        return this;
    }

    public int getRequestLength() {
        return requestLength;
    }

    public Resource responseLength(int responseLength) {
        this.responseLength = responseLength;
        return this;
    }

    public int getResponseLength() {
        return responseLength;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public Resource findDescendant(URI uri) {
        String pathQuery = uri.getRawPath();
        String query = uri.getRawQuery();
        if (query != null) {
            pathQuery += "?" + query;
        }
        for (Resource child : getResources()) {
            if (pathQuery.equals(child.getPath())) {
                return child;
            } else {
                Resource result = child.findDescendant(uri);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    public int descendantCount() {
        return descendantCount(this);
    }

    private int descendantCount(Resource resource) {
        int result = 1;
        for (Resource child : resource.getResources()) {
            result += descendantCount(child);
        }
        return result;
    }

    public Info newInfo() {
        return new Info(this);
    }

    @Override
    public String toString() {
        return String.format("%s@%h{%s %s - %d/%d}",
                getClass().getSimpleName(),
                hashCode(),
                getMethod(),
                getPath(),
                getRequestLength(),
                getResponseLength());
    }

    public static class Info {
        private final Resource resource;
        private long requestTime;
        private long latencyTime;
        private long responseTime;
        private long treeTime;
        private long contentLength;
        private boolean pushed;
        private int status;

        private Info(Resource resource) {
            this.resource = resource;
        }

        public Resource getResource() {
            return resource;
        }

        public long getRequestTime() {
            return requestTime;
        }

        public void setRequestTime(long requestTime) {
            this.requestTime = requestTime;
        }

        public long getLatencyTime() {
            return latencyTime;
        }

        public void setLatencyTime(long latencyTime) {
            this.latencyTime = latencyTime;
        }

        public long getResponseTime() {
            return responseTime;
        }

        public void setResponseTime(long responseTime) {
            this.responseTime = responseTime;
        }

        public long getTreeTime() {
            return treeTime;
        }

        public void setTreeTime(long treeTime) {
            this.treeTime = treeTime;
        }

        public void addContent(int bytes) {
            contentLength += bytes;
        }

        public long getContentLength() {
            return contentLength;
        }

        public boolean isPushed() {
            return pushed;
        }

        public void setPushed( boolean pushed) {
            this.pushed = pushed;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus( int status ) {
            this.status = status;
        }


    }

    public interface Listener extends EventListener {
    }

    public interface NodeListener extends Listener {
        public void onResourceNode(Info info);
    }

    public interface TreeListener extends Listener {
        public void onResourceTree(Info info);
    }
}
