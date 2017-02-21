## HTTP/1.1 and HTTP/2 Load Testing

LoadGenerator will generate some load on an http server using the Jetty HttpClient.

You can generate load for both HTTP/1.1 and HTTP/2

More documentation https://jetty-project.github.io/jetty-load-generator/

## Documentation

### Profile
You can use the API to define a running resource (steps with url to hit)

```java
        Resource resource =
            new Resource( //
                new Resource( "/index.html" ) //
            ); 
```

### Load Generator 
Then you run the load generator with this resource

```java
        LoadGenerator loadGenerator = new LoadGenerator.Builder() //
            .host( your host ) //
            .port( the port ) //
            .users( a users number ) //
            .transactionRate( 1 ) // number of transaction per second. Transaction means all the request from the Resource
            .transport( transport ) // the type of transport.
            .httpClientTransport( HttpClientTransport instance have a look at the various builder ) //
            .scheduler( scheduler ) //
            .sslContextFactory( sslContextFactory ) //
            .resource( resource ) //
            .responseTimeListeners( some listeners you can build your own or use existing one ) // some listeners you can build your own
            .requestListeners( some listeners you can build your own or use existing one ) //
            .build();

        LoadGeneratorResult result = loadGenerator.run();
        
        Now you generator is running
        
        you can now modify the transaction rate
        
        loadGenerator.interrupt();
        
```

### Time Measure

With all listeners we have, we can take measure in different places

1. request start  (request#send( ) ) 
2. request de queued ready to be sent ( BeginListener#onBegin() )
3. request headers finished sent  ( CommitListener#onCommit() )
4. request body start ( ContentListener#onContent() )
5. request body finished ( SuccessListener#onSuccess()
6. response headers start receiving  ( BeginListener#onBegin() )
7. response headers received 
8. response body start received ( ContentListener#onContent() ) 
9. response body completed ( CompleteListener#onComplete() )

#### Response Time

The responseTime exposed using ResponseTimeListener is the time taken just before 1. and 9.

#### Latency Time

The latencyTime exposed using LatencyTimeListener is the time taken just before 2. and 6.

### Exposed results
The LoadGenerator start a collector server you can query to get some informations as: 

* totalCount: number of request part of the result
* minValue: the minimum value
* maxValue: the maximum value
* value50: the value at 50 percentile.
* value90: the value at 90 percentile.
* mean: the mean value
* stdDeviation: the computed standard deviation
* startTimeStamp: the start time for the values
* endTimeStamp: the end time for the values

You can get those information trough Json result via GET request

* /collector/response-times will return per http path the response times informations

### Results Collector
The Collector client will help to collect statistics on collector server.
To use it

```java

            CollectorClient collectorClient = CollectorClient.Builder.builder() //
                .addAddress( "localhost:187" ) //
                .addAddress( "beer.org:80" ) //
                .scheduleDelayInMillis( 1000 ) //
                .build();

            collectorClient.start();
        
```

### Using uber jar

```
java -jar jetty-load-generator-starter-0.3-SNAPSHOT-uber.jar -h localhost -p 8080 -pgp ./simple_profile.groovy -t http -rt 10 -rtu s -tr 40 -u 100
```
See --help for usage

### Groovy profile file

```
import org.mortbay.jetty.load.generator.profile.Resource

return new Resource(new Resource( "index.html",
                                         new Resource( "/css/bootstrap.css",
                                                       new Resource( "/css/bootstrap-theme.css" ),
                                                       new Resource( "/js/jquery-3.1.1.min.js"),
                                                       new Resource( "/js/jquery-3.1.1.min.js"),
                                                       new Resource( "/js/jquery-3.1.1.min.js"),
                                                       new Resource( "/js/jquery-3.1.1.min.js")
                                         ),
                                         new Resource( "/js/bootstrap.js" ,
                                                       new Resource( "/js/bootstrap.js" ),
                                                       new Resource( "/js/bootstrap.js" ),
                                                       new Resource( "/js/bootstrap.js" )
                                         ),
                                         new Resource( "/hello" ),
                                         new Resource( "/dump.jsp?wine=foo&foo=bar" ),
                                         new Resource( "/not_here.html" ),
                                         new Resource( "/hello?name=foo" ),
                                         new Resource( "/hello?name=foo" ),
                                         new Resource( "/upload" ).method("PUT").size(8192),
                                         )
);
```

## Building

To build, use:
```shell
  mvn clean install
```

It is possible to bypass tests by building with `mvn -DskipTests install`

## Professional Services

Expert advice and production support are available through [Webtide.com](http://webtide.com).
