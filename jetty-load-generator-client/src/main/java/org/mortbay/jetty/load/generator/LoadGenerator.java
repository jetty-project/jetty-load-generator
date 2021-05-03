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
import java.util.Collection;
import java.util.Collections;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.HttpRequest;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;
import org.eclipse.jetty.client.api.Result;
import org.eclipse.jetty.client.util.BytesContentProvider;
import org.eclipse.jetty.io.Connection;
import org.eclipse.jetty.util.Callback;
import org.eclipse.jetty.util.CountingCallback;
import org.eclipse.jetty.util.SocketAddressResolver;
import org.eclipse.jetty.util.ajax.JSON;
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
    private volatile boolean interrupted;

    LoadGenerator(Config config) {
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
        interrupted = false;
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
                .thenRun(this::fireBeginEvent)
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
                            .thenRun(this::fireEndEvent)
                            .thenCompose(v -> CompletableFuture.allOf(responses));
                })
                .thenRun(this::fireCompleteEvent)
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
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("interrupting {}", this);
        }
        interrupted = true;
    }

    boolean isInterrupted() {
        return interrupted;
    }

    private CompletableFuture<Void> process() {
        // The implementation of this method may look unnecessary complicated.
        // The reason is that Callbacks propagate completion inwards,
        // while CompletableFutures propagate completion outwards.
        // The method returns a CompletableFuture, but the implementation
        // uses Callbacks that need to reference the innermost CompletableFuture.

        HttpClient[] clients = new HttpClient[config.getUsersPerThread()];

        Callback.Completable anyFailure = new Callback.Completable();

        // This is the callback to use for warmup iterations.
        int warmupIterations = config.getWarmupIterationsPerThread();
        WarmupCallback warmupCallback = new WarmupCallback(anyFailure, warmupIterations);

        // This is the callback to use for run iterations.
        RunCallback runCallback = new RunCallback();
        // Fail fast in case of run failures.
        runCallback.exceptionally(x -> {
            anyFailure.completeExceptionally(x);
            return null;
        });
        // Fail the run iterations if there is any failure.
        anyFailure.exceptionally(x -> {
            runCallback.completeExceptionally(x);
            return null;
        });

        try {
            // Wait for all the sender threads to arrive here.
            awaitBarrier();

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("sender thread running");
            }

            Collection<Connection.Listener> connectionListeners = getBeans(Connection.Listener.class);
            for (int i = 0; i < clients.length; ++i) {
                HttpClient client = clients[i] = newHttpClient(getConfig());
                connectionListeners.forEach(client::addBean);
                addManaged(client);
            }

            int rate = config.getResourceRate();
            long period = rate > 0 ? TimeUnit.SECONDS.toNanos(config.getThreads()) / rate : 0;
            long rateRampUpPeriod = TimeUnit.SECONDS.toNanos(config.getRateRampUpPeriod());

            long runFor = config.getRunFor();
            int iterations = runFor > 0 ? 0 : config.getIterationsPerThread();

            long alreadySent = 0;
            long rampUpUnsent = 0;
            int clientIndex = 0;
            boolean warmup = true;
            long begin = System.nanoTime();
            long warmupWait = 0;

            send:
            while (true) {
                // Typically only one batch is sent.
                // However, for high rates the period may be smaller than the
                // timer resolution so the sleep may last more than expected.
                // Also in case of GC pauses time may be lost.
                // To compensate for oversleeping, the batch is adjusted.
                long batchToSend = 1;
                if (period > 0) {
                    TimeUnit.NANOSECONDS.sleep(period);
                    long elapsedNanos = System.nanoTime() - begin - warmupWait;
                    long expectedSent = Math.round((double)elapsedNanos / period);
                    if (rateRampUpPeriod > 0 && elapsedNanos < rateRampUpPeriod) {
                        // The rate ramp-up is linear: it will bring the rate up in the
                        // given time, so that the rate over time graph is a right triangle.
                        double rampUpRate = ((double)elapsedNanos / rateRampUpPeriod) / period;
                        // The accumulated number of requests is the area of the triangle.
                        long rampUpExpectedSent = Math.round(elapsedNanos * rampUpRate / 2);
                        rampUpUnsent = expectedSent - rampUpExpectedSent;
                        expectedSent = rampUpExpectedSent;
                    } else {
                        // Adjust for those requests that could
                        // not be sent in the last ramp-up step.
                        expectedSent -= rampUpUnsent;
                    }
                    batchToSend = expectedSent - alreadySent;
                    alreadySent = expectedSent;
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("sending batch: {} resources", batchToSend);
                }

                while (batchToSend > 0) {
                    Callback callback;
                    boolean lastIteration = false;
                    if (warmup) {
                        if (warmupIterations == 0) {
                            warmup = false;
                            long start = System.nanoTime();
                            warmupCallback.join();
                            warmupWait = System.nanoTime() - start;
                            continue;
                        } else {
                            --warmupIterations;
                            callback = warmupCallback;
                        }
                    } else {
                        if (iterations > 0) {
                            lastIteration = --iterations == 0;
                        } else {
                            lastIteration = runFor > 0 && TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - begin) >= runFor;
                        }
                        runCallback.increment(lastIteration);
                        callback = runCallback;
                    }

                    HttpClient client = clients[clientIndex];
                    sendResourceTree(client, config.getResource(), warmup, callback);
                    --batchToSend;

                    if (lastIteration || anyFailure.isCompletedExceptionally()) {
                        break send;
                    }

                    if (isInterrupted()) {
                        throw new InterruptedException("sender thread interrupted");
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
            anyFailure.completeExceptionally(x);
        }

        return runCallback
                .whenComplete((r, x) -> {
                    // When the resource trees are complete, try to
                    // succeed anyFailure, if was not already failed.
                    anyFailure.complete(null);
                })
                // FlatMap anyFailure so that even if all the promises have succeeded,
                // the failure is reported anyway (for example, manual interruption).
                .thenCompose(y -> anyFailure)
                .whenComplete((r, x) -> {
                    if (LOGGER.isDebugEnabled()) {
                        if (x == null) {
                            LOGGER.debug("sender thread completed");
                        } else {
                            LOGGER.debug("sender thread failed", x);
                        }
                    }
                })
                // HttpClient cannot be stopped from one of its own threads.
                .whenCompleteAsync((r, x) -> {
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("stopping http clients");
                    }
                    Arrays.stream(clients).forEach(this::stopHttpClient);
                }, executorService);
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
                .attribute(Resource.class.getName(), resource)
                .method(resource.getMethod())
                .path(resource.getPath());
        request.getHeaders().addAll(resource.getRequestHeaders());
        request.header(Resource.RESPONSE_LENGTH, Long.toString(resource.getResponseLength()));
        long requestLength = resource.getRequestLength();
        if (requestLength > 0) {
            request.content(new BytesContentProvider(new byte[Math.toIntExact(requestLength)]));
        }
        return request;
    }

    private void sendResourceTree(HttpClient client, Resource resource, boolean warmup, Callback callback) {
        int nodes = resource.descendantCount();
        Resource.Info info = resource.newInfo(this);
        CountingCallback treeCallback = new CountingCallback(new Callback() {
            @Override
            public void succeeded() {
                info.setTreeTime(System.nanoTime());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("completed {}tree for {}", warmup ? "warmup " : "", resource);
                }
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
        sender.offer(List.of(info));
        sender.send();
    }

    private int awaitBarrier() {
        try {
            return barrier.await();
        } catch (Throwable x) {
            throw new RuntimeException(x);
        }
    }

    private void fireBeginEvent() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("emitting begin event");
        }
        config.getListeners().stream()
                .filter(l -> l instanceof BeginListener)
                .map(l -> (BeginListener)l)
                .forEach(this::invokeBeginListener);
    }

    private void invokeBeginListener(BeginListener listener) {
        try {
            listener.onBegin(this);
        } catch (Throwable x) {
            LOGGER.info("ignored failure while invoking listener {}", listener, x);
        }
    }

    private void fireReadyEvent() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("emitting ready event");
        }
        config.getListeners().stream()
                .filter(l -> l instanceof ReadyListener)
                .map(l -> (ReadyListener)l)
                .forEach(this::invokeReadyListener);
    }

    private void invokeReadyListener(ReadyListener listener) {
        try {
            listener.onReady(this);
        } catch (Throwable x) {
            LOGGER.info("ignored failure while invoking listener {}", listener, x);
        }
    }

    private void fireEndEvent() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("emitting end event");
        }
        config.getListeners().stream()
                .filter(l -> l instanceof EndListener)
                .map(l -> (EndListener)l)
                .forEach(this::invokeEndListener);
    }

    private void invokeEndListener(EndListener listener) {
        try {
            listener.onEnd(this);
        } catch (Throwable x) {
            LOGGER.info("ignored failure while invoking listener {}", listener, x);
        }
    }

    private void fireCompleteEvent() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("emitting complete event");
        }
        config.getListeners().stream()
                .filter(l -> l instanceof CompleteListener)
                .map(l -> (CompleteListener)l)
                .forEach(this::invokeCompleteListener);
    }

    private void invokeCompleteListener(CompleteListener listener) {
        try {
            listener.onComplete(this);
        } catch (Throwable x) {
            LOGGER.info("ignored failure while invoking listener {}", listener, x);
        }
    }

    private void fireResourceNodeEvent(Resource.Info info) {
        config.getResourceListeners().stream()
                .filter(l -> l instanceof Resource.NodeListener)
                .map(l -> (Resource.NodeListener)l)
                .forEach(l -> invokeResourceNodeListener(l, info));
    }

    private void invokeResourceNodeListener(Resource.NodeListener listener, Resource.Info info) {
        try {
            listener.onResourceNode(info);
        } catch (Throwable x) {
            LOGGER.info("ignored failure while invoking listener {}", listener, x);
        }
    }

    private void fireResourceTreeEvent(Resource.Info info) {
        config.getResourceListeners().stream()
                .filter(l -> l instanceof Resource.TreeListener)
                .map(l -> (Resource.TreeListener)l)
                .forEach(l -> invokeResourceTreeListener(l, info));
    }

    private void invokeResourceTreeListener(Resource.TreeListener listener, Resource.Info info) {
        try {
            listener.onResourceTree(info);
        } catch (Throwable x) {
            LOGGER.info("ignored failure while invoking listener {}", listener, x);
        }
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
            try {
                for (Resource.Info info : resources) {
                    Resource resource = info.getResource();
                    if (resource.getPath() != null) {
                        HttpRequest httpRequest = (HttpRequest)newRequest(client, config, resource);

                        if (pushCache.contains(httpRequest.getURI())) {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("skip sending pushed {}", info);
                            }
                        } else {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("sending {}{}", warmup ? "warmup " : "", info);
                            }

                            httpRequest.pushListener((request, pushed) -> {
                                URI pushedURI = pushed.getURI();
                                Resource child = resource.findDescendant(pushedURI);
                                if (child != null && pushCache.add(pushedURI)) {
                                    Resource.Info pushedInfo = child.newInfo(LoadGenerator.this);
                                    if (LOGGER.isDebugEnabled()) {
                                        LOGGER.debug("pushed {}", pushedInfo);
                                    }
                                    pushedInfo.setRequestTime(System.nanoTime());
                                    pushedInfo.setPushed(true);
                                    return new ResponseHandler(pushedInfo);
                                } else {
                                    return null;
                                }
                            });

                            Request request = config.getRequestListeners().stream()
                                    .reduce(httpRequest, Request::listener, (r1, r2) -> r1);
                            info.setRequestTime(System.nanoTime());
                            request.send(new ResponseHandler(info));
                        }
                    } else {
                        // Don't fire the resource event for "group" resources.
                        callback.succeeded();
                        sendChildren(resource);
                    }
                }
            } catch (Throwable x) {
                callback.failed(x);
            }
        }

        private void sendChildren(Resource resource) {
            List<Resource> children = resource.getResources();
            if (!children.isEmpty()) {
                offer(children.stream()
                        .map(child -> child.newInfo(LoadGenerator.this))
                        .collect(Collectors.toList()));
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
                info.setResponseTime(System.nanoTime());
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("completed {}{}: {}", warmup ? "warmup " : "", info, result);
                }
                if (result.isSucceeded()) {
                    info.setStatus(result.getResponse().getStatus());
                } else {
                    Throwable failure = result.getFailure();
                    info.setFailure(failure);
                }
                if (!warmup) {
                    fireResourceNodeEvent(info);
                }
                // Succeed the callback even in case of
                // failures to continue the load generation.
                callback.succeeded();
                sendChildren(info.getResource());
            }
        }
    }

    /**
     * <p>Read-only configuration for the load generator.</p>
     *
     * @see Builder
     */
    @ManagedObject("LoadGenerator Configuration")
    public static class Config implements JSON.Convertible {
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
        protected HTTPClientTransportBuilder httpClientTransportBuilder = new HTTP1ClientTransportBuilder();
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
        public void toJSON(JSON.Output out) {
            out.add("threads", getThreads());
            out.add("warmupIterationsPerThread", getWarmupIterationsPerThread());
            out.add("iterationsPerThread", getIterationsPerThread());
            out.add("runFor", getRunFor());
            out.add("usersPerThread", getUsersPerThread());
            out.add("channelsPerUser", getChannelsPerUser());
            out.add("resourceRate", getResourceRate());
            out.add("rateRampUpPeriod", getRateRampUpPeriod());
            out.add("scheme", getScheme());
            out.add("host", getHost());
            out.add("port", getPort());
            out.add("transport", getHttpClientTransportBuilder());
            out.add("resource", getResource());
            out.add("maxRequestsQueued", getMaxRequestsQueued());
            out.add("connectBlocking", isConnectBlocking());
            out.add("connectTimeout", getConnectTimeout());
            out.add("idleTimeout", getIdleTimeout());
        }

        @Override
        public void fromJSON(Map map) {
            threads = asInt(map, "threads");
            warmupIterationsPerThread = asInt(map, "warmupIterationsPerThread");
            iterationsPerThread = asInt(map, "iterationsPerThread");
            runFor = asLong(map, "runFor");
            usersPerThread = asInt(map, "usersPerThread");
            channelsPerUser = asInt(map, "channelsPerUser");
            resourceRate = asInt(map, "resourceRate");
            rateRampUpPeriod = asLong(map, "rateRampUpPeriod");
            scheme = asString(map, "scheme", "http");
            host = asString(map, "host", "localhost");
            port = asInt(map, "port");
            httpClientTransportBuilder = asTransport(map);
            resource = asResource(map);
            maxRequestsQueued = asInt(map, "maxRequestsQueued");
            connectBlocking = map.get("connectBlocking") == Boolean.TRUE;
            connectTimeout = asInt(map, "connectTimeout");
            idleTimeout = asInt(map, "idleTimeout");
        }

        static int asInt(Map<?, ?> map, String name) {
            Object obj = map.get(name);
            if (obj instanceof Number) {
                return ((Number)obj).intValue();
            }
            return 0;
        }

        private long asLong(Map<?, ?> map, String name) {
            Object obj = map.get(name);
            if (obj instanceof Number) {
                return ((Number)obj).longValue();
            }
            return 0;
        }

        private String asString(Map<?, ?> map, String name, String dftValue) {
            Object obj = map.get(name);
            if (obj == null) {
                return dftValue;
            }
            return obj.toString();
        }

        private HTTPClientTransportBuilder asTransport(Map<?, ?> map) {
            Object obj = map.get("transport");
            if (obj == null) {
                return new HTTP1ClientTransportBuilder();
            }
            Map<?, ?> transport = (Map<?, ?>)obj;
            String type = (String)transport.get("type");
            if (type == null) {
                return new HTTP1ClientTransportBuilder();
            }
            HTTPClientTransportBuilder result;
            switch (type) {
                case HTTP1ClientTransportBuilder.TYPE:
                    result = new HTTP1ClientTransportBuilder();
                    break;
                case HTTP2ClientTransportBuilder.TYPE:
                    result = new HTTP2ClientTransportBuilder();
                    break;
                default:
                    throw new IllegalArgumentException("unknown transport type: " + type);
            }
            result.fromJSON(transport);
            return result;
        }

        private Resource asResource(Map<?, ?> map) {
            Object obj = map.get("resource");
            if (obj == null) {
                return new Resource("/");
            }
            Resource result = new Resource();
            result.fromJSON((Map<?, ?>)obj);
            return result;
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
     * <p>A listener for the LoadGenerator "ready" event.</p>
     * <p>The "ready" event is emitted when the load generation warmup is finished.</p>
     */
    public interface ReadyListener extends Listener {
        /**
         * <p>Callback method invoked when the "ready" event is emitted.</p>
         *
         * @param generator the load generator
         */
        public void onReady(LoadGenerator generator);
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

    private class WarmupCallback extends Callback.Nested {
        private final CountDownLatch latch;
        private final Callback counter;

        public WarmupCallback(Callback callback, int warmupIterations) {
            super(callback);
            latch = new CountDownLatch(warmupIterations == 0 ? 0 : 1);
            // If there are no warmup iterations, the callback will never be invoked.
            counter = warmupIterations == 0 ? NOOP : new CountingCallback(Callback.from(this::success, this::failure), warmupIterations);
        }

        @Override
        public void succeeded() {
            counter.succeeded();
        }

        @Override
        public void failed(Throwable x) {
            counter.failed(x);
        }

        public void join() {
            try {
                if (counter == NOOP) {
                    fireReadyEvent();
                }
                latch.await();
            } catch (InterruptedException x) {
                throw new RuntimeException(x);
            }
        }

        private void success() {
            try {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("awaiting barrier for ready event");
                }
                if (awaitBarrier() == 0) {
                    fireReadyEvent();
                }
                // Wait for the "ready" listener to complete.
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("awaiting barrier for ready listener");
                }
                awaitBarrier();
                // Do not forward success the nested callback,
                // as these are just warmup iterations.
            } finally {
                latch.countDown();
            }
        }

        private void failure(Throwable failure) {
            latch.countDown();
            // Only failures are forwarded to the nested callback.
            super.failed(failure);
        }
    }

    private static class RunCallback extends Callback.Completable {
        private final AtomicLong counter = new AtomicLong();
        private boolean last;

        @Override
        public void succeeded() {
            if (counter.decrementAndGet() == 0 && last) {
                super.succeeded();
            }
        }

        public void increment(boolean last) {
            this.last = last;
            counter.incrementAndGet();
        }


    }

}
