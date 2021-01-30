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

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.eclipse.jetty.http.HttpField;
import org.eclipse.jetty.http.HttpFields;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.ajax.JSON;

/**
 * <p>A resource node to be fetched by the load generator.</p>
 * <p>Resources are organized in a tree, and the load generator
 * fetches parent resources before children resources, while sibling
 * resources are fetched in parallel.</p>
 * <p>A Resource without a path is a <em>group</em> resource,
 * only meant to group resources together (for example to fetch all
 * JavaScript resources as a group before fetching the image resources).</p>
 */
public class Resource implements JSON.Convertible {
    public static final String RESPONSE_LENGTH = "JLG-Response-Length";

    private final List<Resource> resources = new ArrayList<>();
    private final HttpFields requestHeaders = new HttpFields();
    private String method = HttpMethod.GET.asString();
    private String path;
    private long requestLength;
    private long responseLength;

    public Resource() {
        this((String)null);
    }

    public Resource(String path) {
        this(path, new Resource[0]);
    }

    public Resource(Resource... resources) {
        this(null, resources);
    }

    public Resource(String path, Resource... resources) {
        this.path = path;
        if (resources != null) {
            Collections.addAll(this.resources, resources);
        }
    }

    /**
     * @param method the HTTP method to use to fetch the resource
     * @return this Resource
     */
    public Resource method(String method) {
        this.method = method;
        return this;
    }

    public String getMethod() {
        return method;
    }

    /**
     * @param path the resource path
     * @return this Resource
     */
    public Resource path(String path) {
        this.path = path.startsWith("/") ? path : "/" + path;
        return this;
    }

    public String getPath() {
        return path;
    }

    /**
     * @param requestLength the request content length
     * @return this Resource
     */
    public Resource requestLength(long requestLength) {
        this.requestLength = requestLength;
        return this;
    }

    public long getRequestLength() {
        return requestLength;
    }

    /**
     * <p>Adds a request header.</p>
     *
     * @param name  the header name
     * @param value the header value
     * @return this Resource
     */
    public Resource requestHeader(String name, String value) {
        this.requestHeaders.add(name, value);
        return this;
    }

    /**
     * <p>Adds request headers.</p>
     *
     * @param headers the request headers
     * @return this Resource
     */
    public Resource requestHeaders(HttpFields headers) {
        this.requestHeaders.addAll(headers);
        return this;
    }

    public HttpFields getRequestHeaders() {
        return requestHeaders;
    }

    /**
     * <p>Sets the response content length.</p>
     * <p>The response content length is conveyed as the request header
     * specified by {@link #RESPONSE_LENGTH}. Servers may ignore it
     * or honor it, responding with the desired response content length.</p>
     *
     * @param responseLength the response content length
     * @return this Resource
     */
    public Resource responseLength(long responseLength) {
        this.responseLength = responseLength;
        return this;
    }

    public long getResponseLength() {
        return responseLength;
    }

    /**
     * <p>Adds children resources.</p>
     *
     * @param resources the children resources to add
     * @return this Resource
     */
    public Resource resources(Resource... resources) {
        this.resources.addAll(List.of(resources));
        return this;
    }

    /**
     * @return the children resources
     */
    public List<Resource> getResources() {
        return resources;
    }

    /**
     * Finds a descendant resource by path and query with the given URI.
     *
     * @param uri the URI with the path and query to find
     * @return a matching descendant resource, or null if there is no match
     */
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

    /**
     * @return the number of descendant resource nodes
     */
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

    /**
     * @return a new Info object
     */
    public Info newInfo() {
        return new Info(this);
    }

    @Override
    public void toJSON(JSON.Output out) {
        out.add("method", getMethod());
        out.add("path", getPath());
        out.add("requestLength", getRequestLength());
        out.add("requestHeaders", toMap(getRequestHeaders()));
        out.add("resources", getResources());
        out.add("responseLength", getResponseLength());
    }

    @Override
    public void fromJSON(Map map) {
        method((String)map.get("method"));
        path((String)map.get("path"));
        requestLength(((Number)map.get("requestLength")).longValue());
        @SuppressWarnings("unchecked")
        Map<String, Object> requestHeaders = (Map<String, Object>)map.get("requestHeaders");
        requestHeaders(toHttpFields(requestHeaders));
        resources(toResources(map.get("resources")));
        responseLength(((Number)map.get("responseLength")).longValue());
    }

    private static Map<String, Object> toMap(HttpFields fields) {
        return fields.stream()
                .map(field -> new Object() {
                    final String name = field.getName();
                    final String[] values = field.getValues();
                })
                .collect(Collectors.toMap(tuple -> tuple.name, tuple -> tuple.values));
    }

    private static HttpFields toHttpFields(Map<String, Object> map) {
        HttpFields fields = new HttpFields();
        map.entrySet().stream()
                .map(entry -> new HttpField(entry.getKey(), Arrays.stream((Object[])entry.getValue()).map(String::valueOf).collect(Collectors.joining(","))))
                .forEach(fields::put);
        return fields;
    }

    private static Resource[] toResources(Object objects) {
        if (objects != null) {
            Object[] array = null;
            if (objects.getClass().isArray()) {
                array = (Object[])objects;
            } else if (objects instanceof Collection) {
                array = ((Collection<?>)objects).toArray();
            }
            if (array != null) {
                return Arrays.stream(array)
                        .map(object -> (Map<?, ?>)object)
                        .map(map -> {
                            Resource child = new Resource();
                            child.fromJSON(map);
                            return child;
                        })
                        .toArray(Resource[]::new);
            }
        }
        return new Resource[0];
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

    /**
     * <p>Value class containing information per-resource and per-request.</p>
     */
    public static class Info {
        private final Resource resource;
        private long requestTime;
        private long latencyTime;
        private long responseTime;
        private long treeTime;
        private long contentLength;
        private boolean pushed;
        private int status;
        private Throwable failure;

        private Info(Resource resource) {
            this.resource = resource;
        }

        /**
         * @return the corresponding Resource
         */
        public Resource getResource() {
            return resource;
        }

        /**
         * @return the time, in ns, the request is being sent
         */
        public long getRequestTime() {
            return requestTime;
        }

        void setRequestTime(long requestTime) {
            this.requestTime = requestTime;
        }

        /**
         * @return the time, in ns, the response first byte arrived
         */
        public long getLatencyTime() {
            return latencyTime;
        }

        void setLatencyTime(long latencyTime) {
            this.latencyTime = latencyTime;
        }

        /**
         * @return the time, in ns, the response last byte arrived
         */
        public long getResponseTime() {
            return responseTime;
        }

        void setResponseTime(long responseTime) {
            this.responseTime = responseTime;
        }

        /**
         * @return the time, in ns, the last byte of the whole resource tree arrived
         */
        public long getTreeTime() {
            return treeTime;
        }

        void setTreeTime(long treeTime) {
            this.treeTime = treeTime;
        }

        /**
         * @param bytes the number of bytes to add to the response content length
         */
        void addContent(int bytes) {
            contentLength += bytes;
        }

        /**
         * @return the response content length in bytes
         */
        public long getContentLength() {
            return contentLength;
        }

        /**
         * @return whether the resource has been pushed by the server
         */
        public boolean isPushed() {
            return pushed;
        }

        void setPushed(boolean pushed) {
            this.pushed = pushed;
        }

        /**
         * @return the response HTTP status code
         */
        public int getStatus() {
            return status;
        }

        void setStatus(int status) {
            this.status = status;
        }

        /**
         * @return the request/response failure, if any
         */
        public Throwable getFailure() {
            return failure;
        }

        void setFailure(Throwable failure) {
            this.failure = failure;
        }
    }

    /**
     * <p>Generic listener for resource events.</p>
     *
     * @see NodeListener
     * @see TreeListener
     */
    public interface Listener extends EventListener {
    }

    /**
     * <p>Listener for resource node events.</p>
     * <p>Resource node events are emitted for non-warmup resource requests that completed successfully.</p>
     */
    public interface NodeListener extends Listener {
        public void onResourceNode(Info info);
    }

    /**
     * <p>Listener for resource tree events.</p>
     * <p>Resource tree events are emitted for the non-warmup root resource.</p>
     */
    public interface TreeListener extends Listener {
        public void onResourceTree(Info info);
    }
}
