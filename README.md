## HTTP/1.1 and HTTP/2 Load Testing

LoadGenerator will generate some load on an http server using the Jetty HttpClient.

You can generate load for both HTTP/1.1 and HTTP/2

More documentation https://jetty-project.github.io/jetty-load-generator/

Snapshots available here: http://oss.sonatype.org/content/repositories/jetty-snapshots

Current snapshot version: 1.0.0-SNAPSHOT

## Documentation

### Profile
You can use the API to define a running resource (steps with url to hit). This can be a single urls or a tree.

```java
Resource resource = new Resource("/",
            new Resource("/styles.css"),
            new Resource("/pagenavi-css.css"),
            
    );
```

### Load Generator 
Then you simply run the load generator with this resource

```java
     
        LoadGenerator.Builder builder = new LoadGenerator.Builder()
                .host( your host )
                .port( the port )
                .httpClientTransportBuilder(clientTransportBuilder)
                .resource(resource)
                .warmupIterationsPerThread(10)
                .iterationsPerThread(100)
                .runFor(2, TimeUnit.MINUTES) // if you want to run for 2 minutes (this wil override iterationsPerThread)
                .usersPerThread(100)
                .build();                

        loadGenerator.begin();
        
        Now you generator is running
        
        
        loadGenerator.interrupt();
        
```

### Listeners
#### Resource Listeners
To count or do some statistics on load you can create your own listeners.
The inferface to implement is ``` org.mortbay.jetty.load.generator.Resource.NodeListener ```.

You will have some time available via ``` org.mortbay.jetty.load.generator.Resource.Info ```

They can be added to the ``` LoadGenerator using Builder method```
```
loadGeneratorBuilder.resourceListener( listener )
```

The latencyTime and responseTime (in nano seconds) are available using the dedicated methods.

**Note** This is not calculation of the data but the timestamp of the event happened. So you need to use the requestTime
 to calculate the values.

#### Tree listener
You can have a listener for resource tree sent.
The interface to implement is ``` org.mortbay.jetty.load.generator.Resource.TreeListener ```.

You will have some time available via ``` org.mortbay.jetty.load.generator.Resource.Info ```

They can be added to the ``` LoadGenerator using Builder method```
```
loadGeneratorBuilder.resourceListener( listener )
```
#### Load Generator Listener
There are 2 listeners for the LoadGenerator lifecycle (start and end).
The interfaces to implement are:
* ``` org.mortbay.jetty.load.generator.LoadGenerator.BeginListener ```
* ``` org.mortbay.jetty.load.generator.LoadGenerator.EndListener ```

They can be added to the ``` LoadGenerator using Builder method```
```
loadGeneratorBuilder.listener( listener )
```
#### Http Request Listener
You can use Request.Listener from the HttpClient API.

They can be added to the ``` LoadGenerator using Builder method```
```
loadGeneratorBuilder.requestListener(Request.Listener listener)
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

#### Latency Time

The latencyTime is the time taken just before 2. and 6. (time to get the first byte of the response)

#### Response Time

The responseTime is the time taken just before 1. and 9. (time to get the last byte of the response) 

### Using uber jar

```
java -jar jetty-load-generator-starter-1.0.0-SNAPSHOT-uber.jar -h localhost -p 8080 -pgp ./simple_profile.groovy -t http -rt 10 -rtu s -tr 40 -u 100
```
See --help for usage

### Groovy profile file

```
import org.mortbay.jetty.load.generator.Resource

return new Resource( "index.html",
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
                     );
```

## Building

To build, use:
```shell
  mvn clean install
```

It is possible to bypass tests by building with `mvn -DskipTests install`


### WIP DOC TO UPDATE
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

## Professional Services

Expert advice and production support are available through [Webtide.com](http://webtide.com).
