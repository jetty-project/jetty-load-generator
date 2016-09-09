## Project description


LoadGenerator will generate some load on an http server using the Jetty HttpClient.

## Documentation

### Profile
You can use the API to define a running profile (steps with url to hit)

```java
        ResourceProfile resourceProfile =
            new ResourceProfile( //
                new Resource( "/index.html" ) //
            ); 
```

### Load Generator 
Then you run the load generator with this profile

```java
        LoadGenerator loadGenerator = new LoadGenerator.Builder() //
            .host( your host ) //
            .port( the port ) //
            .users( a users number ) //
            .transactionRate( 1 ) // number of transaction per second. Transaction means all the request from the ResourceProfile
            .transport( transport ) // the type of transport.
            .httpClientTransport( HttpClientTransport instance have a look at the various builder ) //
            .scheduler( scheduler ) //
            .sslContextFactory( sslContextFactory ) //
            .loadProfile( profile ) //
            .latencyListeners( some listeners you can build your own or use existing one ) // some listeners you can build your own
            .requestListeners( some listeners you can build your own or use existing one ) //
            .build();

        LoadGeneratorResult result = loadGenerator.run();
        
        Now you generator is running
        
        you can now modify the transaction rate
        
        loadGenerator.interrupt();
        
```

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

* /collector/client-latency will return the httpclient latency informations
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


## Building

To build, use:
```shell
  mvn clean install
```

It is possible to bypass tests by building with `mvn -DskipTests install`

## Professional Services

Expert advice and production support are available through [Webtide.com](http://webtide.com).
