Project description
============

LoadGenerator will generate some load on an http server using the Jetty HttpClient.

Documentation
============

You can use the API to define a running profile (steps with url to hit)

```java
        LoadGeneratorProfile loadGeneratorProfile = LoadGeneratorProfile.Builder.builder() //
            .resource( "/index.html" ).size( 1024 ) //
            //.resource( "" ).size( 1024 ) //
            .build();  
```

Then you run the load generator with this profile

```java
        LoadGenerator loadGenerator = LoadGenerator.Builder.builder() //
            .host( "localhost" ) //
            .port( port ) //
            .users( parallel users ) //
            .requestRate( 1 ) // request/rate per second
            .scheme( scheme() ) //
            .requestListeners( ) // some listeners you maybe want to use
            .transport( transport ) // the transport HTTP, HTTPS, H2 etcc
            .httpClientScheduler( scheduler ) //
            .loadGeneratorWorkflow( profile ) //
            .build() //
            .start();

        LoadGeneratorResult result = loadGenerator.run();
        
        Now you generator is running
        
        you can now modify the request rate
        
```

Exposed results
The LoadGenerator start a collector server you can query to get some informations as: 

    * totalCount;
    * minValue;
    * maxValue;
    * mean;
    * stdDeviation;
    * startTimeStamp;
    * endTimeStamp;




Building
========

To build, use:
```shell
  mvn clean install
```

It is possible to bypass tests by building with `mvn -DskipTests install`

Professional Services
============

Expert advice and production support are available through [Webtide.com](http://webtide.com).
