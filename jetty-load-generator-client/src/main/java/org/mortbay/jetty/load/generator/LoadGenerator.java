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
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.CountingCallback;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.annotation.ManagedOperation;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.component.LifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.Scheduler;

/**
 * <p>An HTTP Load Generator that sends {@link Resource resources} to the server as HTTP requests.</p>
 * <p>Use a {@link Builder} to configure the parameters that control the load generation.</p>
 * <p>Typical usage:</p>
 *
 * <pre>
 * LoadGenerator loadGenerator = LoadGenerator.builder()
 *     .host("localhost")                          // The server host
 *     .port(8080)                                 // The server port
 *     .resource(new Resource("/"))                // The resource(s) to request
 *     .resourceRate(5)                            // The send rate in resource tree per second
 *     .usersPerThread(10)                         // Simulate 10 different users/connections
 *     .warmupIterationsPerThread(100)             // How many warmup iterations (not recorded)
 *     .iterationsPerThread(200)                   // How many recorded iterations
 *     .resourceListener(this::recordResponseTime) // The listener for resource events to record
 *     .build();
 *
 * // Start the load generation.
 * CompletableFuture&lt;?&gt; load = loadGenerator.begin();
 *
 * // Wait for the load generator to finish.
 * load.get();
 * </pre>
 *
 * <p>In the example above, a single resource is configured, with a total request rate of 5 requests/s,
 * which means a nominal pause between requests of 200 ms;
 * 10 users per thread means that at least 10 connections will be opened to the server;
 * 200 iterations means that that the load generator will perform requests with this pattern:</p>
 * <pre>
 * 200 ms pause
 * client1.send
 * 200 ms pause
 * client2.send
 * ...
 * 200 ms pause
 * client10.send
 * 200 ms pause
 * client1.send
 * ...
 * </pre>
 * <p>The rate is across all users; with 200 iterations and 10 users,
 * each user sends 20 resources to the server.</p>
 *
 * <p>Rather than counting iterations, the load generator can run for a specified time using
 * {@link Builder#runFor(long, TimeUnit)} instead of {@link Builder#iterationsPerThread(int)}.</p>
 */
@ManagedObject("LoadGenerator")
public class LoadGenerator extends ContainerLifeCycle {
    private static final Logger LOGGER = Log.getLogger(LoadGenerator.class);

    /**
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    private final Config config;
    private final CyclicBarrier barrier;
    private ExecutorService executorService;
    private volatile boolean interrupt;

    private LoadGenerator(Config config) {
        this.config = config;
        this.barrier = new CyclicBarrier(config.threads);
        addBean(config);
        addBean(config.getExecutor());
        addBean(config.getScheduler());
    }

    private CompletableFuture<Void> spawn() {
        CompletableFuture<Void> result = new CompletableFuture<>();
        try {
            start();
            result.complete(null);
        } catch (Throwable x) {
            result.completeExceptionally(x);
        }
        return result;
    }

    @Override
    protected void doStart() throws Exception {
        executorService = Executors.newCachedThreadPool();
        interrupt = false;
        super.doStart();
    }

    private void halt() {
        LifeCycle.stop(this);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        interrupt();
        executorService.shutdown();
    }

    /**
     * @return the configuration of this LoadGenerator
     */
    public Config getConfig() {
        return config;
    }

    /**
     * <p>Begins the load generation, as configured with the Builder.</p>
     *
     * @return a CompletableFuture that is completed when the load generation completes.
     */
    public CompletableFuture<Void> begin() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("generating load, {}", config);
        }
        return spawn()
                .thenRun(() -> fireBeginEvent(this))
                .thenCompose(x -> {
                    // These CompletableFutures will be completed when process()
                    // returns, i.e. when requests have been scheduled for send.
                    CompletableFuture<?>[] requests = new CompletableFuture<?>[config.getThreads()];
                    // These CompletableFutures will be completed when responses are completed.
                    CompletableFuture<?>[] responses = new CompletableFuture<?>[config.getThreads()];
                    for (int i = 0; i < requests.length; ++i) {
                        int index = i;
                        Supplier<CompletableFuture<Void>> sender = () -> {
                            CompletableFuture<Void> complete = process();
                            responses[index] = complete;
                            return complete;
                        };
                        requests[index] = CompletableFuture.supplyAsync(sender, executorService);
                    }
                    return CompletableFuture.allOf(requests)
                            .thenRun(() -> fireEndEvent(this))
                            .thenCompose(v -> CompletableFuture.allOf(responses));
                })
                .thenRun(() -> fireCompleteEvent(this))
                // Call halt() even if previous stages failed.
                .whenCompleteAsync((r, x) -> halt(), executorService);
    }

    /**
     * <p>Interrupts gracefully the load generation.</p>
     * <p>The CompletableFuture returned by {@link #begin()} is completed
     * exceptionally with an {@link InterruptedException}.</p>
     */
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
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("sender thread {} running", threadName);
            }

            HttpClient[] clients = new HttpClient[config.getUsersPerThread()];
            // HttpClient cannot be stopped from one of its own threads.
            result = process.whenCompleteAsync((r, x) -> {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("stopping http clients");
                }
                Arrays.stream(clients).forEach(this::stopHttpClient);
            }, executorService);
            for (int i = 0; i < clients.length; ++i) {
                HttpClient client = clients[i] = newHttpClient(getConfig());
                addManaged(client);
            }

            Callback processCallback = new Callback() {
                @Override
                public void succeeded() {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("sender thread {} completed", threadName);
                    }
                    process.complete(null);
                }

                @Override
                public void failed(Throwable x) {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("sender thread " + threadName + " failed", x);
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
            long rateRampUpPeriod = TimeUnit.SECONDS.toNanos(config.getRateRampUpPeriod());

            long runFor = config.getRunFor();
            int warmupIterations = config.getWarmupIterationsPerThread();
            int iterations = runFor > 0 ? 0 : config.getIterationsPerThread();

            long begin = System.nanoTime();
            long total = 0;
            long unsent = 0;
            int clientIndex = 0;

            send:
            while (true) {
                long batch = 1;
                if (period > 0) {
                    TimeUnit.NANOSECONDS.sleep(period);
                    // We need to compensate for oversleeping.
                    long elapsed = System.nanoTime() - begin;
                    long expected = Math.round((double)elapsed / period);
                    if (rateRampUpPeriod > 0 && elapsed < rateRampUpPeriod) {
                        long send = Math.round(0.5D * elapsed * elapsed / rateRampUpPeriod / period);
                        unsent = expected - send;
                        expected = send;
                    } else {
                        expected -= unsent;
                    }
                    batch = expected - total;
                    total = expected;
                }

                while (batch > 0) {
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
                    --batch;

                    if (lastIteration || ranEnough || process.isCompletedExceptionally()) {
                        break send;
                    }
                    if (interrupt) {
                        callback.failed(new InterruptedException());
                        break send;
                    }

                    if (++clientIndex == clients.length) {
                        clientIndex = 0;
                    }
                }
            }
        } catch (Throwable x) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(x);
            }
            process.completeExceptionally(x);
        }
        return result;
    }

    protected HttpClient newHttpClient(Config config) {
        HttpClient httpClient = new HttpClient(config.getHttpClientTransportBuilder().build(), config.getSslContextFactory());
        httpClient.setExecutor(config.getExecutor());
        httpClient.setScheduler(config.getScheduler());
        httpClient.setMaxConnectionsPerDestination(config.getChannelsPerUser());
        httpClient.setMaxRequestsQueuedPerDestination(config.getMaxRequestsQueued());
        httpClient.setSocketAddressResolver(config.getSocketAddressResolver());
        httpClient.setConnectBlocking(config.isConnectBlocking());
        httpClient.setConnectTimeout(config.getConnectTimeout());
        httpClient.setIdleTimeout(config.getIdleTimeout());
        return httpClient;
    }

    private void stopHttpClient(HttpClient client) {
        try {
            if (client != null) {
                client.stop();
                removeBean(client);
            }
        } catch (Throwable x) {
            LOGGER.ignore(x);
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
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("completed tree for {}", resource);
                }
                info.setTreeTime(System.nanoTime());
                if (!warmup) {
                    fireResourceTreeEvent(info);
                }
                callback.succeeded();
            }

            @Override
            public void failed(Throwable x) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("failed tree for {}", resource);
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

    private void fireCompleteEvent(LoadGenerator generator) {
        config.getListeners().stream()
                .filter(l -> l instanceof CompleteListener)
                .map(l -> (CompleteListener)l)
                .forEach(l -> l.onComplete(generator));
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
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("skip sending pushed {}", resource);
                        }
                    } else {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("sending {}{}", warmup ? "warmup " : "", resource);
                        }

                        httpRequest.pushListener((request, pushed) -> {
                            URI pushedURI = pushed.getURI();
                            Resource child = resource.findDescendant(pushedURI);
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("pushed {}", child);
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
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("completed {}: {}", resource, result);
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
     * <p>Read-only configuration for the load generator.</p>
     *
     * @see Builder
     */
    @ManagedObject("LoadGenerator Configuration")
    public static class Config {
        protected int threads = 1;
        protected int warmupIterationsPerThread = 0;
        protected int iterationsPerThread = 1;
        protected long runFor = 0;
        protected int usersPerThread = 1;
        protected int channelsPerUser = 1024;
        protected int resourceRate = 1;
        protected long rateRampUpPeriod = 0;
        protected String scheme = "http";
        protected String host = "localhost";
        protected int port = 8080;
        protected HTTPClientTransportBuilder httpClientTransportBuilder;
        protected SslContextFactory.Client sslContextFactory;
        protected Scheduler scheduler;
        protected Executor executor;
        protected SocketAddressResolver socketAddressResolver = new SocketAddressResolver.Sync();
        protected Resource resource = new Resource("/");
        protected final List<Listener> listeners = new ArrayList<>();
        protected final List<Request.Listener> requestListeners = new ArrayList<>();
        protected final List<Resource.Listener> resourceListeners = new ArrayList<>();
        protected int maxRequestsQueued = 128 * 1024;
        protected boolean connectBlocking = true;
        protected long connectTimeout = 5000;
        protected long idleTimeout = 15000;

        @ManagedAttribute("Number of sender threads")
        public int getThreads() {
            return threads;
        }

        @ManagedAttribute("Number of warmup iterations per sender thread")
        public int getWarmupIterationsPerThread() {
            return warmupIterationsPerThread;
        }

        @ManagedAttribute("Number of iterations per sender thread")
        public int getIterationsPerThread() {
            return iterationsPerThread;
        }

        @ManagedAttribute("Time in seconds for how long to run")
        public long getRunFor() {
            return runFor;
        }

        @ManagedAttribute("Number of users per sender thread")
        public int getUsersPerThread() {
            return usersPerThread;
        }

        @ManagedAttribute("Number of concurrent request channels per user")
        public int getChannelsPerUser() {
            return channelsPerUser;
        }

        @ManagedAttribute("Send rate in resource trees per second")
        public int getResourceRate() {
            return resourceRate;
        }

        @ManagedAttribute("Rate ramp up period in seconds")
        public long getRateRampUpPeriod() {
            return rateRampUpPeriod;
        }

        @ManagedAttribute("Scheme for the request URI")
        public String getScheme() {
            return scheme;
        }

        @ManagedAttribute("Host for the request URI")
        public String getHost() {
            return host;
        }

        @ManagedAttribute("Port for the request URI")
        public int getPort() {
            return port;
        }

        public HTTPClientTransportBuilder getHttpClientTransportBuilder() {
            return httpClientTransportBuilder;
        }

        public SslContextFactory.Client getSslContextFactory() {
            return sslContextFactory;
        }

        public Scheduler getScheduler() {
            return scheduler;
        }

        public Executor getExecutor() {
            return executor;
        }

        public SocketAddressResolver getSocketAddressResolver() {
            return socketAddressResolver;
        }

        public Resource getResource() {
            return resource;
        }

        @ManagedAttribute("Maximum number of queued requests")
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

        @ManagedAttribute("Whether the connect operation is blocking")
        public boolean isConnectBlocking() {
            return connectBlocking;
        }

        @ManagedAttribute("Connect timeout in milliseconds")
        public long getConnectTimeout() {
            return connectTimeout;
        }

        @ManagedAttribute("Idle timeout in milliseconds")
        public long getIdleTimeout() {
            return idleTimeout;
        }

        @Override
        public String toString() {
            return String.format("%s[t=%d,i=%d,u=%d,c=%d,r=%d,rf=%ds,%s://%s:%d]",
                    Config.class.getSimpleName(),
                    threads,
                    runFor > 0 ? -1 : iterationsPerThread,
                    usersPerThread,
                    channelsPerUser,
                    resourceRate,
                    runFor > 0 ? runFor : -1,
                    scheme,
                    host,
                    port);
        }
    }

    /**
     * <p>A builder for LoadGenerator.</p>
     */
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
            if (time > 0) {
                this.runFor = unit.toSeconds(time);
            }
            return this;
        }

        /**
         * <p>Configures the number of "users" per sender thread, where a "user" is the
         * entity that opens TCP connections to the server.</p>
         * <p>A "user" maps to an {@code HttpClient} instance.</p>
         * <p>This value is an indication of the minimum number of TCP connections
         * opened by the LoadGenerator, since the precise number depends on the
         * protocol (HTTP/1.1 vs HTTP/2) and the request rate.</p>
         * <p>Consider using {@link #channelsPerUser(int)} to have more control on the
         * maximum number of TCP connections opened to the server.</p>
         * <p>Consider using a {@link #executor(Executor) shared executor} and a
         * {@link #scheduler(Scheduler) shared scheduler} to avoid that each
         * {@code HttpClient} instance allocates its own.</p>
         *
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
         * <p>Configures the number of "channels" a user can use to send requests in parallel.</p>
         * <p>When using the HTTP/1.1 protocol, this value can be used to simulate browsers,
         * that only open a limited number of TCP connections (typically 6-8) to a single server.</p>
         * <p>When using the HTTP/2 protocol, this value is the maximum number of concurrent
         * streams sent by the LoadGenerator.</p>
         *
         * @param channelsPerUser the number of connections/streams per user
         * @return this Builder
         * @see #usersPerThread(int)
         */
        public Builder channelsPerUser(int channelsPerUser) {
            if (channelsPerUser < 0) {
                throw new IllegalArgumentException();
            }
            this.channelsPerUser = channelsPerUser;
            return this;
        }

        /**
         * <p>The total request rate of the resource tree generated by the LoadGenerator.</p>
         * <p>For a resource tree made of just one resource, this value is effectively the HTTP request rate.</p>
         * <p>For a resource tree made of 3 sibling resources, the HTTP request rate is this value times 3 (as
         * sibling resources are sent in parallel).</p>
         * <p>When using more than 1 {@link #threads(int) sender thread}, the resource rate is split among
         * sender threads (so it is best that this value is a multiple of the number of sender threads).</p>
         *
         * @param resourceRate number of resource trees requested per second, or zero for maximum request rate
         * @return this Builder
         */
        public Builder resourceRate(int resourceRate) {
            this.resourceRate = resourceRate;
            return this;
        }

        /**
         * <p>The resource rate ramp-up period, to avoid that high resource rates
         * cause a load spike when the load generation begins.</p>
         *
         * @param rateRampUpPeriod the rate ramp-up period in seconds, or zero for no ramp-up
         * @return this Builder
         */
        public Builder rateRampUpPeriod(long rateRampUpPeriod) {
            this.rateRampUpPeriod = rateRampUpPeriod;
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
        public Builder sslContextFactory(SslContextFactory.Client sslContextFactory) {
            this.sslContextFactory = sslContextFactory;
            return this;
        }

        /**
         * @param scheduler the shared scheduler among all HttpClient instances
         *                  if {@code null} each HttpClient will use its own
         * @return this Builder
         */
        public Builder scheduler(Scheduler scheduler) {
            this.scheduler = Objects.requireNonNull(scheduler);
            return this;
        }

        /**
         * @param executor the shared executor among all HttpClient instances
         *                 if {@code null} each HttpClient will use its own
         * @return this Builder
         */
        public Builder executor(Executor executor) {
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

        /**
         * @param maxRequestsQueued same as {@link HttpClient#setMaxRequestsQueuedPerDestination(int)}
         * @return this Builder
         */
        public Builder maxRequestsQueued(int maxRequestsQueued) {
            this.maxRequestsQueued = maxRequestsQueued;
            return this;
        }

        /**
         * @param listener the {@link Listener} to add
         * @return this Builder
         */
        public Builder listener(Listener listener) {
            listeners.add(listener);
            return this;
        }

        /**
         * @param listener the {@link Request.Listener} to add
         * @return this Builder
         */
        public Builder requestListener(Request.Listener listener) {
            requestListeners.add(listener);
            return this;
        }

        /**
         * @param listener the {@link Resource.Listener} to add
         * @return this Builder
         */
        public Builder resourceListener(Resource.Listener listener) {
            resourceListeners.add(listener);
            return this;
        }

        /**
         * @param connectBlocking same as {@link HttpClient#setConnectBlocking(boolean)}
         * @return this Builder
         */
        public Builder connectBlocking(boolean connectBlocking) {
            this.connectBlocking = connectBlocking;
            return this;
        }

        /**
         * @param connectTimeout same as {@link HttpClient#setConnectTimeout(long)}
         * @return this Builder
         */
        public Builder connectTimeout(long connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        /**
         * @param idleTimeout same as {@link HttpClient#setIdleTimeout(long)}
         * @return this Builder
         */
        public Builder idleTimeout(long idleTimeout) {
            this.idleTimeout = idleTimeout;
            return this;
        }

        /**
         * @return a new LoadGenerator instance
         */
        public LoadGenerator build() {
            if (httpClientTransportBuilder == null) {
                httpClientTransportBuilder = new HTTP1ClientTransportBuilder();
            }
            return new LoadGenerator(this);
        }
    }

    /**
     * <p>A generic listener for LoadGenerator events.</p>
     */
    public interface Listener extends EventListener {
    }

    /**
     * <p>A listener for the LoadGenerator "begin" event.</p>
     * <p>The "begin" event is emitted when the load generation begins.</p>
     */
    public interface BeginListener extends Listener {
        /**
         * <p>Callback method invoked when the "begin" event is emitted.</p>
         *
         * @param generator the load generator
         */
        public void onBegin(LoadGenerator generator);
    }

    /**
     * <p>A listener for the LoadGenerator "end" event.</p>
     * <p>The "end" event is emitted when the load generation ends,
     * that is when the last request has been sent.</p>
     *
     * @see CompleteListener
     */
    public interface EndListener extends Listener {
        /**
         * <p>Callback method invoked when the "end" event is emitted.</p>
         *
         * @param generator the load generator
         */
        void onEnd(LoadGenerator generator);
    }

    /**
     * <p>A listener for the LoadGenerator "complete" event.</p>
     * <p>The "complete" event is emitted when the load generation completes,
     * that is when the last response has been received.</p>
     *
     * @see EndListener
     */
    public interface CompleteListener extends Listener {
        /**
         * <p>Callback method invoked when the "complete" event is emitted.</p>
         *
         * @param generator the load generator
         */
        void onComplete(LoadGenerator generator);
    }
}
