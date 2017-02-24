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

package org.mortbay.jetty.load.generator.starter;

import com.fasterxml.jackson.databind.ObjectMapper;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.util.StringUtil;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.mortbay.jetty.load.generator.Http2TransportBuilder;
import org.mortbay.jetty.load.generator.HttpFCGITransportBuilder;
import org.mortbay.jetty.load.generator.HttpTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.latency.LatencyTimeListener;
import org.mortbay.jetty.load.generator.profile.Resource;
import org.mortbay.jetty.load.generator.responsetime.ResponseTimeListener;
import org.mortbay.jetty.load.generator.responsetime.TimePerPathListener;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public abstract class AbstractLoadGeneratorStarter
{

    private LoadGeneratorStarterArgs starterArgs;

    private ExecutorService executorService;

    private Resource resource;

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
        this.run( iteration, true );
    }

    public void run( long time, TimeUnit timeUnit, boolean interrupt )
        throws Exception
    {

        LoadGenerator loadGenerator = getLoadGenerator();

        loadGenerator.run( time, timeUnit );

        if ( interrupt )
        {
            loadGenerator.interrupt();
            writeStats( loadGenerator );
        }

    }

    public void displayStats()
        throws Exception
    {
        writeStats( getLoadGenerator() );
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
            && StringUtil.isNotBlank( loadGenerator.getEndStatsResponse() ) )
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
        Resource resourceProfile = getResource();

        LoadGenerator loadGenerator = new LoadGenerator.Builder() //
            .host( starterArgs.getHost() ) //
            .port( starterArgs.getPort() ) //
            .users( starterArgs.getUsers() ) //
            .transactionRate( starterArgs.getTransactionRate() ) //
            .transport( starterArgs.getTransport() ) //
            .httpClientTransport( httpClientTransport() ) //
            .sslContextFactory( sslContextFactory() ) //
            .resource( resourceProfile ) //
            .responseTimeListeners( getResponseTimeListeners() ) //
            .latencyTimeListeners( getLatencyTimeListeners() ) //
            .executorService( getExecutorService() != null ? getExecutorService() : null ).build();

        return loadGenerator;
    }

    public ExecutorService getExecutorService()
    {
        return executorService;
    }

    public void setExecutorService( ExecutorService executor )
    {
        this.executorService = executor;
    }

    public AbstractLoadGeneratorStarter executorService( ExecutorService executor )
    {
        this.executorService = executor;
        return this;
    }

    public ResponseTimeListener[] getResponseTimeListeners()
    {
        return new ResponseTimeListener[]{ new TimePerPathListener() };
    }

    public LatencyTimeListener[] getLatencyTimeListeners()
    {
        return new LatencyTimeListener[]{ new TimePerPathListener() };
    }


    public Resource getResource()
        throws Exception
    {
        if ( resource != null )
        {
            return resource;
        }

        if ( starterArgs.getProfileJsonPath() != null )
        {
            Path profilePath = Paths.get( starterArgs.getProfileJsonPath() );
            if ( Files.exists( profilePath ) )
            {
                ObjectMapper objectMapper = new ObjectMapper();
                return resource = objectMapper.readValue( profilePath.toFile(), Resource.class );
            }
        }
        if ( starterArgs.getProfileXmlPath() != null )
        {
            Path profilePath = Paths.get( starterArgs.getProfileXmlPath() );
            try (InputStream inputStream = Files.newInputStream( profilePath ))
            {
                return resource = (Resource) new XmlConfiguration( inputStream ).configure();
            }
        }
        if ( starterArgs.getProfileGroovyPath() != null )
        {
            Path profilePath = Paths.get( starterArgs.getProfileGroovyPath() );

            try (Reader reader = Files.newBufferedReader( profilePath ))
            {
                return resource = (Resource) evaluateScript( reader );
            }
        }

        throw new IllegalArgumentException( "not resource profile file defined" );
    }

    private Object evaluateScript( Reader script )
        throws Exception
    {

        CompilerConfiguration config = new CompilerConfiguration( CompilerConfiguration.DEFAULT );
        config.setDebug( true );
        config.setVerbose( true );

        GroovyShell interpreter = new GroovyShell( config );

        return interpreter.evaluate( script );
    }

    public void setResource( Resource resource )
    {
        this.resource = resource;
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
