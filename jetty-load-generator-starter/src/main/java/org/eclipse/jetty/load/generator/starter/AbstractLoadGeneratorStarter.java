//
//  ========================================================================
//  Copyright (c) 1995-2016 Mort Bay Consulting Pty. Ltd.
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

package org.eclipse.jetty.load.generator.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.load.generator.Http2TransportBuilder;
import org.eclipse.jetty.load.generator.HttpFCGITransportBuilder;
import org.eclipse.jetty.load.generator.HttpTransportBuilder;
import org.eclipse.jetty.load.generator.LoadGenerator;
import org.eclipse.jetty.load.generator.latency.LatencyTimeListener;
import org.eclipse.jetty.load.generator.latency.LatencyTimePerPathListener;
import org.eclipse.jetty.load.generator.profile.ResourceProfile;
import org.eclipse.jetty.load.generator.responsetime.ResponseTimeListener;
import org.eclipse.jetty.load.generator.responsetime.TimePerPathListener;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;

/**
 *
 */
public abstract class AbstractLoadGeneratorStarter
{

    private LoadGeneratorStarterArgs starterArgs;

    public AbstractLoadGeneratorStarter( LoadGeneratorStarterArgs runnerArgs )
    {
        this.starterArgs = runnerArgs;
    }

    protected void run()
        throws Exception
    {
        LoadGenerator loadGenerator = getLoadGenerator();

        loadGenerator.run( starterArgs.getRunningTime(), starterArgs.getRunningTimeUnit() );

        loadGenerator.interrupt();
    }

    protected void run(int iteration)
        throws Exception
    {
        this.run(iteration, false);
    }

    protected void run(int iteration, boolean interrupt)
        throws Exception
    {
        LoadGenerator loadGenerator = getLoadGenerator();

        loadGenerator.run( iteration );

        if (interrupt)
        {
            loadGenerator.interrupt();
        }
    }

    protected LoadGenerator getLoadGenerator()
        throws Exception
    {
        ResourceProfile resourceProfile = getResourceProfile();

        LoadGenerator loadGenerator = new LoadGenerator.Builder() //
            .host( starterArgs.getHost() ) //
            .port( starterArgs.getPort() ) //
            .users( starterArgs.getUsers() ) //
            .transactionRate( starterArgs.getTransactionRate() ) //
            .transport( starterArgs.getTransport() ) //
            .httpClientTransport( httpClientTransport() ) //
            .sslContextFactory( sslContextFactory() ) //
            .loadProfile( resourceProfile ) //
            .responseTimeListeners( getResponseTimeListeners() ) //
            .latencyTimeListeners( getLatencyTimeListeners() ) //
            //.requestListeners( testRequestListener ) //
            .executor( getExecutor() != null ? getExecutor() : null )
            .build();

        return loadGenerator;
    }

    protected Executor getExecutor() {
      return null;
    }

    protected ResponseTimeListener[] getResponseTimeListeners() {
        return new ResponseTimeListener[]{new TimePerPathListener()};
    }

    protected LatencyTimeListener[] getLatencyTimeListeners() {
        return new LatencyTimeListener[] {new LatencyTimePerPathListener()};
    }


    protected ResourceProfile getResourceProfile()
        throws Exception
    {

        if (starterArgs.getProfileJsonPath()!= null)
        {
            Path profilePath = Paths.get( starterArgs.getProfileJsonPath() );
            if ( Files.exists( profilePath ) )
            {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue( profilePath.toFile(), ResourceProfile.class );
            }
        }
        if (starterArgs.getProfileXmlPath() != null)
        {
            Path profilePath = Paths.get( starterArgs.getProfileXmlPath() );
            try (InputStream inputStream = Files.newInputStream( profilePath ))
            {
                return (ResourceProfile) new XmlConfiguration( inputStream ).configure();
            }
        }
        throw new IllegalArgumentException( "not resource profile file defined" );
    }


    protected HttpClientTransport httpClientTransport()
    {
        switch ( starterArgs.getTransport() )
        {
            case HTTP:
            case HTTPS:
            {
                return new HttpTransportBuilder().selectors( starterArgs.getSelectors() ).build();
            }
            case H2C:
            case H2:
            {
                return new Http2TransportBuilder().selectors( starterArgs.getSelectors() ).build();
            }
            case FCGI:
            {
                return new HttpFCGITransportBuilder().build();
            }

            default:
            {
                // nothing this weird case already handled by #provideClientTransport
            }

        }
        throw new IllegalArgumentException( "unknown httpClientTransport" );
    }

    protected SslContextFactory sslContextFactory()
    {
        // FIXME make this more configurable
        SslContextFactory sslContextFactory = new SslContextFactory( true );
        return sslContextFactory;
    }

}
