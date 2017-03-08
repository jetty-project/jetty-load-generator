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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.toolchain.perf.PlatformTimer;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.CountingCallback;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.annotation.ManagedOperation;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;
import org.mortbay.jetty.load.generator.latency.LatencyTimeListener;
import org.mortbay.jetty.load.generator.resource.Resource;
import org.mortbay.jetty.load.generator.responsetime.ResponseTimeListener;

@ManagedObject("Jetty LoadGenerator")
public class LoadGenerator {
    private static final Logger logger = Log.getLogger(LoadGenerator.class);

    private final PlatformTimer timer = PlatformTimer.detect();
    private final Config config;
    private final CyclicBarrier barrier;
    private ExecutorService threads;
    private volatile boolean interrupt;

    private LoadGenerator(Config config) {
        this.config = config;
        this.barrier = new CyclicBarrier(config.threads);
    }

    public Config getConfig() {
        return config;
    }

    public CompletableFuture<Void> begin() {
        if (logger.isDebugEnabled()) {
            logger.debug("generating load, {}", config);
        }
        fireBeginEvent(this);
        threads = Executors.newCachedThreadPool();

        CompletableFuture[] futures = new CompletableFuture[config.getThreads()];
        for (int i = 0; i < futures.length; ++i) {
            futures[i] = CompletableFuture.supplyAsync(this::process, threads).thenCompose(Function.identity());
        }
        return CompletableFuture.allOf(futures).whenCompleteAsync((r, x) -> {
            fireEndEvent(this);
            interrupt();
            threads.shutdown();
        }, threads);
    }

    @ManagedOperation(value = "Interrupts this LoadGenerator", impact = "ACTION")
    public void interrupt() {
        interrupt = true;
    }

    private CompletableFuture<Void> process() {
        CompletableFuture<Void> process = new CompletableFuture<>();
        CompletableFuture<Void> result = process;
        try {
            barrier.await();

            String threadName = Thread.currentThread().getName();
            int iterations = config.getIterationsPerThread();
            if (logger.isDebugEnabled()) {
                logger.debug("sender thread {} running {} iterations", threadName, iterations);
            }

            CountingCallback callback = new CountingCallback(new Callback() {
                @Override
                public void succeeded() {
                    if (logger.isDebugEnabled()) {
                        logger.debug("sender thread {} completed {} iterations", threadName, iterations);
                    }
                    process.complete(null);
                }

                @Override
                public void failed(Throwable x) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("sender thread {} failed {} iterations", threadName, iterations);
                    }
                    process.completeExceptionally(x);
                }
            }, iterations);
            int iteration = 0;

            HttpClient[] clients = new HttpClient[config.getUsersPerThread()];
            // HttpClient cannot be stopped from one of its own threads.
            result = process.whenCompleteAsync((r, x) -> {
                if (logger.isDebugEnabled()) {
                    logger.debug("stopping http clients");
                }
                Arrays.stream(clients).forEach(this::stopHttpClient);
            }, threads);
            PushCache[] pushCaches = new PushCache[clients.length];
            for (int i = 0; i < clients.length; ++i) {
                clients[i] = newHttpClient(getConfig());
                clients[i].start();
                pushCaches[i] = new PushCache();
            }

            int rate = config.getResourceRate();
            long pause = rate > 0 ? TimeUnit.SECONDS.toMicros(config.getThreads()) / rate : 0;

            int clientIndex = 0;
            while (true) {
                HttpClient client = clients[clientIndex];
                PushCache pushCache = pushCaches[clientIndex];
                sendResourceTree(client, pushCache, config.getResource(), callback);
                if (++iteration == iterations) {
                    break;
                }
                if (++clientIndex == clients.length) {
                    clientIndex = 0;
                }
                if (pause > 0) {
                    timer.sleep(pause);
                }
                if (interrupt) {
                    process.completeExceptionally(new InterruptedException());
                    break;
                }
            }

            return result;
        } catch (Throwable x) {
            if (logger.isDebugEnabled()) {
                logger.debug(x);
            }
            process.completeExceptionally(x);
            return result;
        }
    }

    protected HttpClient newHttpClient(Config config) {
        HttpClient result = new HttpClient(config.getHttpClientTransportBuilder().build(), config.getSslContextFactory());
        result.setExecutor(config.getExecutor());
        result.setScheduler(config.getScheduler());
        result.setMaxConnectionsPerDestination(config.getChannelsPerUser());
        result.setMaxRequestsQueuedPerDestination(2048);
        result.setSocketAddressResolver(config.getSocketAddressResolver());
        return result;
    }

    private void stopHttpClient(HttpClient client) {
        try {
            if (client != null) {
                client.stop();
            }
        } catch (Throwable x) {
            logger.ignore(x);
        }
    }

    protected Request newRequest(HttpClient client, Config config, Resource resource) {
        Request request = client.newRequest(config.getHost(), config.getPort())
                .scheme(config.getScheme())
                .method(resource.getMethod())
                .path(resource.getPath())
                .header("X-Download", Integer.toString(resource.getResponseLength()));
        int requestLength = resource.getRequestLength();
        if (requestLength > 0) {
            request.content(new BytesContentProvider(new byte[requestLength]));
        }
        return request;
    }

    private void sendResourceTree(HttpClient client, PushCache pushCache, Resource resource, Callback callback) {
        int nodes = resource.descendantCount();
        Resource.Info info = new Resource.Info(resource);
        CountingCallback treeCallback = new CountingCallback(new Callback() {
            @Override
            public void succeeded() {
                if (logger.isDebugEnabled()) {
                    logger.debug("completed tree for {}", resource);
                }
                info.setTotalTime(System.nanoTime());
                fireResourceTreeEvent(info);
                callback.succeeded();
            }

            @Override
            public void failed(Throwable x) {
                if (logger.isDebugEnabled()) {
                    logger.debug("failed tree for {}", resource);
                }
                callback.failed(x);
            }
        }, nodes);
        Sender sender = new Sender(client, pushCache, treeCallback);
        sender.offer(Collections.singletonList(info));
        sender.send();
    }

    private void fireBeginEvent(LoadGenerator generator) {
        config.getListeners().stream()
                .filter(l -> l instanceof BeginListener)
                .map(l -> (BeginListener)l)
                .forEach(l -> l.onBegin(generator));
    }

    private void fireEndEvent(LoadGenerator generator) {
        config.getListeners().stream()
                .filter(l -> l instanceof EndListener)
                .map(l -> (EndListener)l)
                .forEach(l -> l.onEnd(generator));
    }

    private void fireResourceNodeEvent(Resource.Info info) {
        config.getResourceListeners().stream()
                .filter(l -> l instanceof Resource.NodeListener)
                .map(l -> (Resource.NodeListener)l)
                .forEach(l -> l.onResourceNode(info));
    }

    private void fireResourceTreeEvent(Resource.Info info) {
        config.getResourceListeners().stream()
                .filter(l -> l instanceof Resource.TreeListener)
                .map(l -> (Resource.TreeListener)l)
                .forEach(l -> l.onResourceTree(info));
    }

    private static class PushCache {
        private final ConcurrentMap<URI, Boolean> cache = new ConcurrentHashMap<>();

        public boolean add(URI uri) {
            return cache.putIfAbsent(uri, true) == null;
        }

        public boolean contains(URI uri) {
            return cache.containsKey(uri);
        }
    }

    private class Sender {
        private final Queue<Resource.Info> queue = new ArrayDeque<>();
        private final HttpClient client;
        private final PushCache pushCache;
        private final CountingCallback callback;
        private boolean active;

        private Sender(HttpClient client, PushCache pushCache, CountingCallback callback) {
            this.client = client;
            this.pushCache = pushCache;
            this.callback = callback;
        }

        private void offer(List<Resource.Info> resources) {
            synchronized (this) {
                queue.addAll(resources);
            }
        }

        private void send() {
            synchronized (this) {
                if (active) {
                    return;
                }
                active = true;
            }

            List<Resource.Info> resources = new ArrayList<>();
            while (true) {
                synchronized (this) {
                    if (queue.isEmpty()) {
                        active = false;
                        return;
                    }
                    resources.addAll(queue);
                    queue.clear();
                }

                send(resources);
                resources.clear();
            }
        }

        private void send(List<Resource.Info> resources) {
            for (Resource.Info info : resources) {
                Resource resource = info.getResource();
                if (logger.isDebugEnabled()) {
                    logger.debug("sending {}", resource);
                }
                // Record send time.
                info.setRequestTime(System.nanoTime());
                if (resource.getPath() != null) {
                    HttpRequest httpRequest = (HttpRequest)newRequest(client, config, resource);

                    httpRequest.pushListener((req, push) -> {
                        URI pushURI = push.getURI();
                        if (pushCache.add(pushURI)) {
                            return result -> {
                            };
                        } else {
                            return null;
                        }
                    });

                    if (pushCache.contains(httpRequest.getURI())) {
                        info.setLatencyTime(System.nanoTime());
                        info.setResponseTime(System.nanoTime());
                        info.setPushed(true);
                        fireResourceNodeEvent(info);
                        callback.succeeded();
                        sendChildren(resource);
                    } else {
                        Request request = httpRequest
                                // Record time to first byte.
                                .onResponseBegin(r -> info.setLatencyTime(System.nanoTime()))
                                // Record content length.
                                .onResponseContent((r, b) -> info.addContent(b.remaining()));
                        request = config.getRequestListeners().stream()
                                .reduce(request, Request::listener, (r1, r2) -> r1);
                        request.send(result -> {
                            if (logger.isDebugEnabled()) {
                                logger.debug("completed {}: {}", resource, result);
                            }
                            if (result.isSucceeded()) {
                                info.setResponseTime(System.nanoTime());
                                fireResourceNodeEvent(info);
                                callback.succeeded();
                            } else {
                                callback.failed(result.getFailure());
                            }
                            sendChildren(resource);
                        });
                    }
                } else {
                    info.setResponseTime(System.nanoTime());
                    // Don't fire the resource event for "group" resources.
                    callback.succeeded();
                    sendChildren(resource);
                }
            }
        }

        private void sendChildren(Resource resource) {
            List<Resource> children = resource.getResources();
            if (!children.isEmpty()) {
                offer(children.stream().map(Resource.Info::new).collect(Collectors.toList()));
                send();
            }
        }
    }

    /**
     * Read-only configuration for the load generator.
     */
    public static class Config {
        protected int threads = 1;
        protected int iterationsPerThread = 1;
        protected int usersPerThread = 1;
        protected int channelsPerUser = 1024;
        protected int resourceRate = 1;
        protected String scheme = "http";
        protected String host = "localhost";
        protected int port = 8080;
        protected HttpClientTransportBuilder httpClientTransportBuilder;
        protected SslContextFactory sslContextFactory;
        protected Scheduler scheduler;
        protected ExecutorService executor;
        protected SocketAddressResolver socketAddressResolver = new SocketAddressResolver.Sync();
        protected Resource resource = new Resource("/");
        protected final List<Listener> listeners = new ArrayList<>();
        protected final List<Request.Listener> requestListeners = new ArrayList<>();
        protected final List<Resource.Listener> resourceListeners = new ArrayList<>();

        public int getThreads() {
            return threads;
        }

        public int getIterationsPerThread() {
            return iterationsPerThread;
        }

        public int getUsersPerThread() {
            return usersPerThread;
        }

        public int getChannelsPerUser() {
            return channelsPerUser;
        }

        public int getResourceRate() {
            return resourceRate;
        }

        public String getScheme() {
            return scheme;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public HttpClientTransportBuilder getHttpClientTransportBuilder() {
            return httpClientTransportBuilder;
        }

        public SslContextFactory getSslContextFactory() {
            return sslContextFactory;
        }

        public Scheduler getScheduler() {
            return scheduler;
        }

        public ExecutorService getExecutor() {
            return executor;
        }

        public SocketAddressResolver getSocketAddressResolver() {
            return socketAddressResolver;
        }

        public Resource getResource() {
            return resource;
        }

        public List<Listener> getListeners() {
            return listeners;
        }

        public List<Request.Listener> getRequestListeners() {
            return requestListeners;
        }

        public List<Resource.Listener> getResourceListeners() {
            return resourceListeners;
        }

        @Override
        public String toString() {
            return String.format("%s[t=%d,i=%d,u=%d,c=%d,r=%d,%s://%s:%d]",
                    Config.class.getSimpleName(),
                    threads,
                    iterationsPerThread,
                    usersPerThread,
                    channelsPerUser,
                    resourceRate,
                    scheme,
                    host,
                    port);
        }
    }

    public static class Builder extends Config {
        /**
         * @param threads the number of sender threads
         * @return this Builder
         */
        public Builder threads(int threads) {
            if (threads < 1) {
                throw new IllegalArgumentException();
            }
            this.threads = threads;
            return this;
        }

        /**
         * @param iterationsPerThread the number of iterations that each sender thread perform, or zero to run forever
         * @return this Builder
         */
        public Builder iterationsPerThread(int iterationsPerThread) {
            this.iterationsPerThread = iterationsPerThread;
            return this;
        }

        /**
         * @param usersPerThread the number of users/browsers for each sender thread
         * @return this Builder
         */
        public Builder usersPerThread(int usersPerThread) {
            if (usersPerThread < 0) {
                throw new IllegalArgumentException();
            }
            this.usersPerThread = usersPerThread;
            return this;
        }

        /**
         * @param channelsPerUser the number of connections/streams per user
         * @return this Builder
         */
        public Builder channelsPerUser(int channelsPerUser) {
            if (channelsPerUser < 0) {
                throw new IllegalArgumentException();
            }
            this.channelsPerUser = channelsPerUser;
            return this;
        }

        /**
         * @param resourceRate number of resource trees requested per second, or zero for maximum request rate
         * @return this Builder
         */
        public Builder resourceRate(int resourceRate) {
            this.resourceRate = resourceRate;
            return this;
        }

        /**
         * @param scheme the default scheme
         * @return this Builder
         */
        public Builder scheme(String scheme) {
            this.scheme = Objects.requireNonNull(scheme);
            return this;
        }

        /**
         * @param host the default host
         * @return this Builder
         */
        public Builder host(String host) {
            this.host = Objects.requireNonNull(host);
            return this;
        }

        /**
         * @param port the default port
         * @return this Builder
         */
        public Builder port(int port) {
            if (port <= 0) {
                throw new IllegalArgumentException();
            }
            this.port = port;
            return this;
        }

        /**
         * @param httpClientTransportBuilder the HttpClient transport builder
         * @return this Builder
         */
        public Builder httpClientTransportBuilder(HttpClientTransportBuilder httpClientTransportBuilder) {
            this.httpClientTransportBuilder = Objects.requireNonNull(httpClientTransportBuilder);
            return this;
        }

        /**
         * @param sslContextFactory the SslContextFactory to use for https requests
         * @return this Builder
         */
        public Builder sslContextFactory(SslContextFactory sslContextFactory) {
            this.sslContextFactory = sslContextFactory;
            return this;
        }

        /**
         * @param scheduler the shared scheduler
         * @return this Builder
         */
        public Builder scheduler(Scheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler);
            return this;
        }

        /**
         * @param executor the shared executor
         * @return this Builder
         */
        public Builder executor(ExecutorService executor) {
            this.executor = Objects.requireNonNull(executor);
            return this;
        }

        /**
         * @param socketAddressResolver the shared SocketAddressResolver
         * @return this Builder
         */
        public Builder socketAddressResolver(SocketAddressResolver socketAddressResolver) {
            this.socketAddressResolver = Objects.requireNonNull(socketAddressResolver);
            return this;
        }

        /**
         * @param resource the root Resource
         * @return this Builder
         */
        public Builder resource(Resource resource) {
            this.resource = resource;
            return this;
        }

        public Builder listener(Listener listener) {
            listeners.add(listener);
            return this;
        }

        public Builder requestListener(Request.Listener listener) {
            requestListeners.add(listener);
            return this;
        }

        public Builder resourceListener(Resource.Listener listener) {
            resourceListeners.add(listener);
            return this;
        }

        public LoadGenerator build() {
            if (httpClientTransportBuilder == null) {
                httpClientTransportBuilder = new Http1ClientTransportBuilder();
            }
            return new LoadGenerator(this);
        }

        // TODO: verify how these listeners are actually used.

        public Builder requestListeners(Request.Listener... requestListeners) {
            return this;
        }

        public Builder responseTimeListeners(ResponseTimeListener... responseTimeListeners) {
            return this;
        }

        public Builder latencyTimeListeners(LatencyTimeListener... latencyTimeListeners) {
            return this;
        }
    }

    public interface Listener extends EventListener {
    }

    public interface BeginListener extends Listener {
        public void onBegin(LoadGenerator generator);
    }

    public interface EndListener extends Listener {
        public void onEnd(LoadGenerator generator);
    }

    // TODO: methods to remove once implementation is stable.

    public Resource getResource() {
        return config.getResource();
    }

    public AtomicBoolean getStop() {
        return new AtomicBoolean();
    }

    public int getTransactionRate() {
        return config.getResourceRate();
    }

    public String getScheme() {
        return config.getScheme();
    }

    public String getHost() {
        return config.getHost();
    }

    public int getPort() {
        return config.getPort();
    }

    public HttpVersion getHttpVersion() {
        return HttpVersion.HTTP_1_1;
    }

    public enum Transport {
        HTTP,
        HTTPS,
        FCGI,
        H2C,
        H2
    }
}
