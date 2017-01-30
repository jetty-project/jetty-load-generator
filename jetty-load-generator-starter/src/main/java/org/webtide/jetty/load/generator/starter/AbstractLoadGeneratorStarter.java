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

package org.webtide.jetty.load.generator.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.jetty.client.HttpClientTransport;
import org.webtide.jetty.load.generator.Http2TransportBuilder;
import org.webtide.jetty.load.generator.HttpFCGITransportBuilder;
import org.webtide.jetty.load.generator.HttpTransportBuilder;
import org.webtide.jetty.load.generator.LoadGenerator;
import org.webtide.jetty.load.generator.latency.LatencyTimeListener;
import org.webtide.jetty.load.generator.latency.LatencyTimePerPathListener;
import org.webtide.jetty.load.generator.profile.ResourceProfile;
import org.webtide.jetty.load.generator.responsetime.ResponseTimeListener;
import org.webtide.jetty.load.generator.responsetime.TimePerPathListener;
import org.eclipse.jetty.util.StringUtil;
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

    private Executor executor;

    public AbstractLoadGeneratorStarter( LoadGeneratorStarterArgs runnerArgs )
    {
        this.starterArgs = runnerArgs;
    }

    public void run()
        throws Exception
    {
        LoadGenerator loadGenerator = getLoadGenerator();

        loadGenerator.run( starterArgs.getRunningTime(), starterArgs.getRunningTimeUnit() );

        loadGenerator.interrupt();

        writeStats( loadGenerator );

    }

    public void run( int iteration )
        throws Exception
    {
        this.run( iteration, false );
    }

    public void run( int iteration, boolean interrupt )
        throws Exception
    {
        LoadGenerator loadGenerator = getLoadGenerator();

        loadGenerator.run( iteration );

        if ( interrupt )
        {
            loadGenerator.interrupt();
            writeStats( loadGenerator );
        }


    }


    protected void writeStats( LoadGenerator loadGenerator )
        throws Exception
    {
        if ( starterArgs.getStatsFile() != null //
            && StringUtil.isNotBlank( loadGenerator.getEndStatsResponse()) )
        {
            Path path = Paths.get( starterArgs.getStatsFile() );
            if ( Files.notExists( path ) )
            {
                Files.createFile( path );
            }

            Files.write( path, loadGenerator.getEndStatsResponse().getBytes() );

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
            .executor( getExecutor() != null ? getExecutor() : null ).build();

        return loadGenerator;
    }

    public Executor getExecutor()
    {
        return executor;
    }

    public void setExecutor( Executor executor )
    {
        this.executor = executor;
    }

    public AbstractLoadGeneratorStarter executor( Executor executor )
    {
        this.executor = executor;
        return this;
    }

    public ResponseTimeListener[] getResponseTimeListeners()
    {
        return new ResponseTimeListener[]{ new TimePerPathListener() };
    }

    public LatencyTimeListener[] getLatencyTimeListeners()
    {
        return new LatencyTimeListener[]{ new LatencyTimePerPathListener() };
    }


    public ResourceProfile getResourceProfile()
        throws Exception
    {

        if ( starterArgs.getProfileJsonPath() != null )
        {
            Path profilePath = Paths.get( starterArgs.getProfileJsonPath() );
            if ( Files.exists( profilePath ) )
            {
                ObjectMapper objectMapper = new ObjectMapper();
                return objectMapper.readValue( profilePath.toFile(), ResourceProfile.class );
            }
        }
        if ( starterArgs.getProfileXmlPath() != null )
        {
            Path profilePath = Paths.get( starterArgs.getProfileXmlPath() );
            try (InputStream inputStream = Files.newInputStream( profilePath ))
            {
                return (ResourceProfile) new XmlConfiguration( inputStream ).configure();
            }
        }
        throw new IllegalArgumentException( "not resource profile file defined" );
    }


    public HttpClientTransport httpClientTransport()
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

    public SslContextFactory sslContextFactory()
    {
        // FIXME make this more configurable
        SslContextFactory sslContextFactory = new SslContextFactory( true );
        return sslContextFactory;
    }

}
