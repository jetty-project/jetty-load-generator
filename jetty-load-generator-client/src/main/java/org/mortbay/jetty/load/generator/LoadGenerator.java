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
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.toolchain.perf.PlatformTimer;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.CountingCallback;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.annotation.ManagedOperation;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

@ManagedObject("Jetty LoadGenerator")
public class LoadGenerator extends ContainerLifeCycle {
    private static final Logger logger = Log.getLogger(LoadGenerator.class);

    private final PlatformTimer timer = PlatformTimer.detect();
    private final Config config;
    private final CyclicBarrier barrier;
    private ExecutorService executorService;
    private volatile boolean interrupt;

    private LoadGenerator(Config config) {
        this.config = config;
        this.barrier = new CyclicBarrier(config.threads);
    }

    private void go() {
        try {
            start();
        } catch (RuntimeException x) {
            throw x;
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    protected void doStart() throws Exception {
        executorService = config.getExecutor() == null ? Executors.newCachedThreadPool() : config.getExecutor();
        interrupt = false;
        super.doStart();
        fireBeginEvent(this);
    }

    private void halt() {
        try {
            stop();
        } catch (RuntimeException x) {
            throw x;
        } catch (Exception x) {
            throw new RuntimeException(x);
        }
    }

    @Override
    protected void doStop() throws Exception {
        fireEndEvent(this);
        super.doStop();
        interrupt();
        executorService.shutdown();
    }

    public Config getConfig() {
        return config;
    }

    public CompletableFuture<Void> begin() {
        if (logger.isDebugEnabled()) {
            logger.debug("generating load, {}", config);
        }

        go();

        CompletableFuture[] futures = new CompletableFuture[config.getThreads()];
        for (int i = 0; i < futures.length; ++i) {
            futures[i] = CompletableFuture.supplyAsync(this::process, executorService).thenCompose(Function.identity());
        }

        return CompletableFuture.allOf(futures).whenCompleteAsync((r, x) -> halt(), executorService);
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
            if (logger.isDebugEnabled()) {
                logger.debug("sender thread {} running", threadName);
            }

            HttpClient[] clients = new HttpClient[config.getUsersPerThread()];
            // HttpClient cannot be stopped from one of its own threads.
            result = process.whenCompleteAsync((r, x) -> {
                if (logger.isDebugEnabled()) {
                    logger.debug("stopping http clients");
                }
                Arrays.stream(clients).forEach(this::stopHttpClient);
            }, executorService);
            for (int i = 0; i < clients.length; ++i) {
                clients[i] = newHttpClient(getConfig());
                clients[i].start();
            }

            Callback processCallback = new Callback() {
                @Override
                public void succeeded() {
                    if (logger.isDebugEnabled()) {
                        logger.debug("sender thread {} completed", threadName);
                    }
                    process.complete(null);
                }

                @Override
                public void failed(Throwable x) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("sender thread {} failed", threadName);
                    }
                    process.completeExceptionally(x);
                }
            };

            // This callback only forwards failure, success is notified explicitly.
            Callback callback = new Callback.Nested(processCallback) {
                @Override
                public void succeeded() {
                }
            };

            int rate = config.getResourceRate();
            long period = rate > 0 ? TimeUnit.SECONDS.toNanos(config.getThreads()) / rate : 0;

            long runFor = config.getRunFor();
            int warmupIterations = config.getWarmupIterationsPerThread();
            int iterations = runFor > 0 ? 0 : config.getIterationsPerThread();

            long begin = System.nanoTime();
            long next = begin + period;
            int clientIndex = 0;
            while (true) {
                HttpClient client = clients[clientIndex];

                boolean warmup = false;
                boolean lastIteration = false;
                if (warmupIterations > 0) {
                    warmup = --warmupIterations >= 0;
                } else if (iterations > 0) {
                    lastIteration = --iterations == 0;
                }
                // Sends the resource one more time after the time expired,
                // but guarantees that the callback is notified correctly.
                boolean ranEnough = runFor > 0 && TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - begin) >= runFor;
                Callback c = lastIteration || ranEnough ? processCallback : callback;

                sendResourceTree(client, config.getResource(), warmup, c);

                if (lastIteration || ranEnough || process.isCompletedExceptionally()) {
                    break;
                }
                if (interrupt) {
                    callback.failed(new InterruptedException());
                    break;
                }

                if (++clientIndex == clients.length) {
                    clientIndex = 0;
                }

                if (period > 0) {
                    long pause = TimeUnit.NANOSECONDS.toMicros(next - System.nanoTime());
                    next += period;
                    if (pause > 0) {
                        timer.sleep(pause);
                    }
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
        result.setMaxRequestsQueuedPerDestination(config.getMaxRequestsQueued());
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
                .path(resource.getPath());
        request.getHeaders().addAll(resource.getRequestHeaders());
        request.header(Resource.RESPONSE_LENGTH, Integer.toString(resource.getResponseLength()));
        int requestLength = resource.getRequestLength();
        if (requestLength > 0) {
            request.content(new BytesContentProvider(new byte[requestLength]));
        }
        return request;
    }

    private void sendResourceTree(HttpClient client, Resource resource, boolean warmup, Callback callback) {
        int nodes = resource.descendantCount();
        Resource.Info info = resource.newInfo();
        CountingCallback treeCallback = new CountingCallback(new Callback() {
            @Override
            public void succeeded() {
                if (logger.isDebugEnabled()) {
                    logger.debug("completed tree for {}", resource);
                }
                info.setTreeTime(System.nanoTime());
                if (!warmup) {
                    fireResourceTreeEvent(info);
                }
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
        Sender sender = new Sender(client, warmup, treeCallback);
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

    private class Sender {
        private final Queue<Resource.Info> queue = new ArrayDeque<>();
        private final Set<URI> pushCache = Collections.newSetFromMap(new ConcurrentHashMap<>());
        private final HttpClient client;
        private final boolean warmup;
        private final CountingCallback callback;
        private boolean active;

        private Sender(HttpClient client, boolean warmup, CountingCallback callback) {
            this.client = client;
            this.warmup = warmup;
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
                info.setRequestTime(System.nanoTime());
                if (resource.getPath() != null) {
                    HttpRequest httpRequest = (HttpRequest)newRequest(client, config, resource);

                    if (pushCache.contains(httpRequest.getURI())) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("skip sending pushed {}", resource);
                        }
                    } else {
                        if (logger.isDebugEnabled()) {
                            logger.debug("sending {}{}", warmup ? "warmup " : "", resource);
                        }

                        httpRequest.pushListener((request, pushed) -> {
                            URI pushedURI = pushed.getURI();
                            Resource child = resource.findDescendant(pushedURI);
                            if (logger.isDebugEnabled()) {
                                logger.debug("pushed {}", child);
                            }
                            if (child != null && pushCache.add(pushedURI)) {
                                Resource.Info pushedInfo = child.newInfo();
                                pushedInfo.setRequestTime(System.nanoTime());
                                pushedInfo.setPushed(true);
                                return new ResponseHandler(pushedInfo);
                            } else {
                                return null;
                            }
                        });

                        Request request = config.getRequestListeners().stream()
                                .reduce(httpRequest, Request::listener, (r1, r2) -> r1);
                        request.send(new ResponseHandler(info));
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
                offer(children.stream().map(Resource::newInfo).collect(Collectors.toList()));
                send();
            }
        }

        private class ResponseHandler extends Response.Listener.Adapter {
            private final Resource.Info info;

            private ResponseHandler(Resource.Info info) {
                this.info = info;
            }

            @Override
            public void onBegin(Response response) {
                // Record time to first byte.
                info.setLatencyTime(System.nanoTime());
            }

            @Override
            public void onContent(Response response, ByteBuffer buffer) {
                // Record content length.
                info.addContent(buffer.remaining());
            }

            @Override
            public void onComplete(Result result) {
                Resource resource = info.getResource();
                if (logger.isDebugEnabled()) {
                    logger.debug("completed {}: {}", resource, result);
                }
                if (result.isSucceeded()) {
                    info.setResponseTime(System.nanoTime());
                    info.setStatus(result.getResponse().getStatus());
                    if (!warmup) {
                        fireResourceNodeEvent(info);
                    }
                    callback.succeeded();
                } else {
                    callback.failed(result.getFailure());
                }
                sendChildren(resource);
            }
        }
    }

    /**
     * Read-only configuration for the load generator.
     */
    public static class Config {
        protected int threads = 1;
        protected int warmupIterationsPerThread = 0;
        protected int iterationsPerThread = 1;
        protected long runFor = 0;
        protected int usersPerThread = 1;
        protected int channelsPerUser = 1024;
        protected int resourceRate = 1;
        protected String scheme = "http";
        protected String host = "localhost";
        protected int port = 8080;
        protected HTTPClientTransportBuilder httpClientTransportBuilder;
        protected SslContextFactory sslContextFactory;
        protected Scheduler scheduler;
        protected ExecutorService executor;
        protected SocketAddressResolver socketAddressResolver = new SocketAddressResolver.Sync();
        protected Resource resource = new Resource("/");
        protected final List<Listener> listeners = new ArrayList<>();
        protected final List<Request.Listener> requestListeners = new ArrayList<>();
        protected final List<Resource.Listener> resourceListeners = new ArrayList<>();
        protected int maxRequestsQueued = 128 * 1024;

        public int getThreads() {
            return threads;
        }

        public int getWarmupIterationsPerThread() {
            return warmupIterationsPerThread;
        }

        public int getIterationsPerThread() {
            return iterationsPerThread;
        }

        public long getRunFor() {
            return runFor;
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

        public HTTPClientTransportBuilder getHttpClientTransportBuilder() {
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

        public int getMaxRequestsQueued() {
            return maxRequestsQueued;
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
         * @param warmupIterationsPerThread the number of warmup iterations that each sender thread performs
         * @return this Builder
         */
        public Builder warmupIterationsPerThread(int warmupIterationsPerThread) {
            this.warmupIterationsPerThread = warmupIterationsPerThread;
            return this;
        }

        /**
         * @param iterationsPerThread the number of iterations that each sender thread performs, or zero to run forever
         * @return this Builder
         */
        public Builder iterationsPerThread(int iterationsPerThread) {
            this.iterationsPerThread = iterationsPerThread;
            return this;
        }

        /**
         * <p>Configures the amount of time that the load generator should run.</p>
         * <p>This setting always takes precedence over {@link #iterationsPerThread}.</p>
         *
         * @param time the time the load generator runs
         * @param unit the unit of time
         * @return this Builder
         */
        public Builder runFor(long time, TimeUnit unit) {
            this.runFor = unit.toSeconds(time);
            if (runFor <= 0) {
                throw new IllegalArgumentException();
            }
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
        public Builder httpClientTransportBuilder(HTTPClientTransportBuilder httpClientTransportBuilder) {
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

        public Builder maxRequestsQueued(int maxRequestsQueued) {
            this.maxRequestsQueued = maxRequestsQueued;
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
                httpClientTransportBuilder = new HTTP1ClientTransportBuilder();
            }
            return new LoadGenerator(this);
        }
    }

    public interface Listener extends EventListener {
    }

    public interface BeginListener extends Listener {
        void onBegin(LoadGenerator generator);
    }

    public interface EndListener extends Listener {
        void onEnd(LoadGenerator generator);
    }
}
