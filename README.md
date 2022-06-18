![GitHub CI](https://github.com/jetty-project/jetty-load-generator/workflows/GitHub%20CI/badge.svg)

# Jetty Load Generator Project

Jetty's `LoadGenerator` is an API to load-test HTTP servers, based on Jetty's `HttpClient`.

| Jetty Load Generator Version | Jetty Version | Java Version |    Status    |
|:----------------------------:|:-------------:|:------------:|:------------:|
|            4.0.x             |    12.0.x     |   Java 17+   | Experimental |
|            3.1.x             |    11.0.x     |   Java 11+   |    Stable    |
|            2.1.x             |    10.0.x     |   Java 11+   |    Stable    |
|            1.1.x             |     9.4.x     |   Java 8+    |    Stable    |
|            2.0.x             |    11.0.x     |   Java 11+   | End-Of-Life  |
|            1.0.7             |     9.4.x     |   Java 8+    | End-Of-Life  |
|         1.0.0-1.0.6          |     9.4.x     |   Java 11+   | End-Of-Life  |

The design of the `LoadGenerator` is based around these concepts:

* Generate requests asynchronously at a constant rate, independently of responses.
* Model [requests as web pages](#resource-apis), simulating what browsers do to download a web page.
* Support both HTTP/1.1 and HTTP/2 (and future versions of the HTTP protocol).
* Emit response events asynchronously, so they can be recorded, for example, in a response time histogram.

You can embed Jetty's `LoadGenerator` in Java applications -- this will give you full flexibility, or you can use it as a command-line tool -- and therefore use it in scripts.

The project artifacts are:

* `jetty-load-generator-client` -- Java APIs, see [this section](#load-generator-apis)
* `jetty-load-generator-listeners` -- useful listeners for events emitted during load-test
* `jetty-load-generator-starter` -- command-line load test uber-jar, see [this section](#command-line-load-generation)

## Recommended Load Generation Setup

1. Assume that the load generator is the bottleneck. You may need several load generators on different machines to make the server even break a sweat.
1. Establish a baseline to verify that all the parties involved in the load runs behave correctly, including network, load generators, server(s), load balancer(s), etc.
1. Use one or more _loaders_ to generate load on the server. A loader is a load generator that imposes a load on the server, but does not record response times.
1. Use a _probe_ to record response times. A probe is a load generator that imposes a light load on the server and records response times.
1. Use Brendan Gregg's [USE method](http://www.brendangregg.com/usemethod.html) to analyze the results.

For example, let's say you want to plot how the server responds to increasing load.  
You setup, say, 4 _loaders_ and one _probe_.  
Configure each loader with, say, `threads=2`, `usersPerThread=50` and `requestRate=20`.  
Configure the probe with, say, `threads=1`, `usersPerThread=1` and `requestRate=1`.  
The total load on the server is therefore 81 requests/s from 401 users, from all the loaders and the probe.
Perform a run with this configuration and record the results from the probe.  
Then change the configuration of the loaders to increase the load (but don't change the probe configuration), say to `usersPerThread=75` and `requestRate=30`, so now the total load on the server is 121 requests/s from 601 users.  
Perform another runs and record the results from the probe.  
Increment again to `usersPerThread=100` and `requestRate=40`, that is 161 requests/s from 801 users.  

Loaders should not affect each other, so ideally each loader should be on a separate machine with a separate network link to the server. 
For non-critical loads, loaders may share the same machine/link, but they will obviously steal CPU and bandwidth from each other.
Loaders should not affect the probe, so ideally the probe should run on a separate machine with a separate network link to the server, to avoid that loaders steal CPUs and bandwidth from the probe that will therefore record bogus results.

Monitor continuously each loader request rate and compare it with its response rate.  
The effective request rate should be close to the nominal request rate you want to impose.  
The response rate should be as close as possible to the request rate.  
If these conditions are not met, it means that the loader is over capacity, and you must reduce the load and possibly spawn a new loader.

## Load Generator APIs

### `Resource` APIs

You can use the `Resource` APIs to define resources that `LoadGenerator` requests to the server.

A simple resource:

```java
Resource resource = new Resource("/index.html");
```

A web-page like `Resource` tree:

```java
Resource resource = new Resource("/index.html",
        new Resource("/styles.css"),
        new Resource("/application.js")
);
```

`Resource` trees are requested to the server similarly to how a browser would do.
In the example above, `/index.html` will be requested and awaited; when its response arrives, `LoadGenerator` will send its children (in parallel if possible): `/styles.css` and `/application.js`.  

Resources can be defined in Java, Groovy files, Jetty XML files, or JSON files.

### `LoadGenerator` APIs

`LoadGenerator` offers a builder-style API:

```java
LoadGenerator generator = LoadGenerator.builder()
        .scheme(scheme)
        .host(serverHost)
        .port(serverPort)
        .resource(resource)
        .httpClientTransportBuilder(transportBuilder)
        .threads(1)
        .usersPerThread(10)
        .channelsPerUser(6)
        .warmupIterationsPerThread(10)
        .iterationsPerThread(100)
        .runFor(2, TimeUnit.MINUTES) // Overrides iterationsPerThread()
        .resourceListener(resourceListener)
        .build();                

// Start the load generation.
CompletableFuture<Void> complete = generator.begin();
        
// Now the load generator is running.
        
// You can wait for the CompletableFuture to complete.
// Or you can interrupt the load generation:
generator.interrupt();
```

`LoadGenerator` uses _sender_ threads to request resources to the server.

Each sender thread can be configured with a number of _users_; each user is a separate `HttpClient` instance that simulates a browser, and has its own connection pool.
Each user opens at least one TCP connection to the server -- the exact number of connections opened depends on the protocol used (HTTP/1.1 vs HTTP/2), the user channels (see below), and the resource rate.

Each user may send requests in parallel through _channels_.
A channel is either a new connection in HTTP/1.1, or a new HTTP/2 stream.

Each sender thread runs an optional number of _warmup_ iterations, that are not recorded -- no events will be emitted for these warmup requests.

After the warmup iterations, each sender thread runs the configured number of _iterations_ or, alternatively, runs for the configured time.
These requests will emit events that may be recorded by listeners, see below.


### Listener APIs

`LoadGenerator` emits a variety of events that you can listen to.

`LoadGenerator` emits events at:
* load generation begin, emitted when the load generation begins, to `LoadGenerator.BeginListener`
* load generation ready, emitted when the load generation has finished the warmup, to `LoadGenerator.ReadyListener`
* load generation end, emitted when the load generation ends (that is, the last request has been sent), to `LoadGenerator.EndListener`
* load generation complete, emitted when the load generation completes (that is, the last response has been received), to `LoadGenerator.CompleteListener`

Most interesting are events related to resources.

`Resource.NodeListener` is notified every time a resource is received by `LoadGenerator`.
`Resource.TreeListener` is notified every time a whole resource tree is received by `LoadGenerator` -- this is useful to gather "page load" times.

For both resource listeners, the information is carried by `Resource.Info`, that provides the timestamps (in nanoseconds) for resource send, resource received, resource content bytes, HTTP status, etc.

You can use histograms to record the response times:

```java
class ResponseTimeListener implements Resource.NodeListener, LoadGenerator.CompleteListener {
    private final org.HdrHistogram.Recorder recorder;
    private org.HdrHistogram.Histogram histogram;
    
    // Invoked every time a resource is received.
    @Override
    public void onResourceNode(Resource.Info info) {
        long responseTime = info.getResponseTime() - info.getRequestTime();
        recorder.recordValue(responseTime);
    }
    
    // Invoked at the end of the load generation.
    @Override
    public void onEnd(LoadGenerator generator) {
        // Retrieve the histogram, resetting the recorder.
        this.histogram = recorder.getIntervalHistogram();
    }
}
```

The `Histogram` APIs provides count, percentiles, average, minimum and maximum values.

## Command-Line Load Generation

Artifact `jetty-load-generator-starter-<version>-uber.jar` allows you to generate load using the command-line.
The uber-jar already contains all the required dependencies.

To display usage:

```
java -jar jetty-load-generator-starter-<version>-uber.jar --help
```

Example:

```shell
java -jar jetty-load-generator-starter-<version>-uber.jar 
        --scheme https 
        --host serverHost 
        --port serverPort
        --resource-json-path /tmp/resource.json
        --transport h2 # secure HTTP/2
        --threads 1
        --users-per-thread 10
        --channels-per-user 6
        --warmup-iterations 10
        --iterations 100
        --display-stats
```

The `/tmp/resource.json` can be as simple as:

```json
{
  "path": "/index.html"
}
```
