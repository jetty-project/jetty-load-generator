package org.eclipse.jetty.load.generator;

import org.eclipse.jetty.load.generator.latency.LatencyDisplayListener;
import org.eclipse.jetty.load.generator.latency.SummaryLatencyListener;
import org.eclipse.jetty.load.generator.response.ResponseTimeDisplayListener;
import org.eclipse.jetty.load.generator.response.SummaryResponseTimeListener;
import org.junit.Test;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ManualTest
{

    @Test
    public void manual_testing() throws Exception
    {
        LoadGeneratorProfile profile = LoadGeneratorProfile.Builder.builder() //
            .resource( "/" ) //
            .build();

        LoadGenerator load = LoadGenerator.Builder.builder() //
            .host( "beer.org" ) //
            .port( 80 ) //
            .requestRate( 2 ) //
            .transport( LoadGenerator.Transport.HTTP ) //
            .users(10) //
            .loadProfile( profile ) //
            .responseTimeListeners( Arrays.asList( new ResponseTimeDisplayListener(2, 10, TimeUnit.SECONDS), //
                                                   new SummaryResponseTimeListener() ) ) //
            .build();

        load.start().run();

        //In machine N+1:

        LoadGenerator loadLatency = LoadGenerator.Builder.builder() //
            .host( "beer.org" ) //
            .port( 80 ) //
            .requestRate( 2 ) //
            .transport( LoadGenerator.Transport.HTTP ) //
            .users(10) //
            .loadProfile( profile ) //
            .latencyListeners( Arrays.asList( new SummaryLatencyListener(), //
                                              new LatencyDisplayListener( 2, 10, TimeUnit.SECONDS ) ) ) //
            .build();

        loadLatency.start().run( 1, TimeUnit.MINUTES );


        load.stop();

    }

}
