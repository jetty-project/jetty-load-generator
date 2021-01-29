![GitHub CI](https://github.com/jetty-project/jetty-load-generator/workflows/GitHub%20CI/badge.svg)

# Jetty Load Generator Project

Jetty's `LoadGenerator` is an API to load-test HTTP servers, based on Jetty's `HttpClient`, that supports both HTTP/1.1 and HTTP/2.

Modules:
* `jetty-load-generator-client` - Java API
* `jetty-load-generator-listeners` - useful listeners for events emitted during load-test
* `jetty-load-generator-starter` - command-line load test uber jar

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
In the example above, `/index.html` will be requested and awaited; when its response arrives `LoadGenerator` will send its children (in parallel if possible), siblings `/styles.css` and `/application.js`.  

Resources can be defined in Java, Groovy files, Jetty XML files, or JSON files.

### `LoadGenerator` APIs

`LoadGenerator` offers a builder-style API:

```java
LoadGenerator generator = LoadGenerator.builder()
        .scheme(scheme)
        .host(serverHost)
        .port(serverPort)
        .httpClientTransportBuilder(transportBuilder)
        .resource(resource)
        .usersPerThread(10)
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

### Listener APIs

`LoadGenerator` emits a variety of events that you can listen to.

`LoadGenerator` emits events at:
* load generation begin, emitted when the load generation begins to `LoadGenerator.BeginListener`
* load generation end, emitted when the load generation ends (that is, the last request has been sent) to `LoadGenerator.EndListener`
* load generation complete, emitted when the load generation completes (that is, the last response has been received) to `LoadGenerator.CompleteListener`

Most interesting are events related to resources.

`Resource.NodeListener` is notified every time a resource is received by `LoadGenerator`.
`Resource.TreeListener` is notified every time a whole resource tree is received by `LoadGenerator` - this is useful to gather "page load" times.

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

Artifact `jetty-load-generator-starter-<version>-uber.jar` allows you to generate load using the command-line and therefore scripts. 

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
        --transport h2 # secure HTTP/2
        --resource-json-path /tmp/resource.json
        --users-per-thread 10
        --warmup-iterations 10
        --iterations 100
        --display-stats 
```
