//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import groovy.lang.GroovyShell;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;
import org.mortbay.jetty.load.generator.HTTP1ClientTransportBuilder;
import org.mortbay.jetty.load.generator.HTTP2ClientTransportBuilder;
import org.mortbay.jetty.load.generator.HTTPClientTransportBuilder;
import org.mortbay.jetty.load.generator.LoadGenerator;
import org.mortbay.jetty.load.generator.Resource;
import org.mortbay.jetty.load.generator.listeners.responsetime.TimePerPathListener;

import java.io.InputStream;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 */
public abstract class AbstractLoadGeneratorStarter
{

    private Logger logger = Log.getLogger( getClass() );

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

        Resource resourceProfile = getResource();

        LoadGenerator.Builder loadGeneratorBuilder = new LoadGenerator.Builder() //
            .host( starterArgs.getHost() ) //
            .port( starterArgs.getPort() ) //
            .iterationsPerThread( starterArgs.getRunIteration() ) //
            .usersPerThread( starterArgs.getUsers() ) //
            .resourceRate( starterArgs.getTransactionRate() ) //
            .httpClientTransportBuilder( httpClientTransportBuilder() ) //
            .sslContextFactory( sslContextFactory() ) //
            .resource( resourceProfile ) //
            .warmupIterationsPerThread( starterArgs.getWarmupNumber() ) //
            .scheme( starterArgs.getScheme() ); //

        if ( starterArgs.getMaxRequestsQueued() > 0 )
        {
            loadGeneratorBuilder.maxRequestsQueued( starterArgs.getMaxRequestsQueued() );
        }

        if ( getExecutorService() != null )
        {
            loadGeneratorBuilder.executor( getExecutorService() );
        }

        boolean runFor = false;

        if ( starterArgs.getRunningTime() > 0 )
        {
            loadGeneratorBuilder.runFor( starterArgs.getRunningTime(), starterArgs.getRunningTimeUnit() );
        }

        for ( Resource.Listener listener : getResourceListeners() )
        {
            loadGeneratorBuilder.resourceListener( listener );
        }

        for ( Request.Listener listener : getListeners() )
        {
            loadGeneratorBuilder.requestListener( listener );
        }

        for ( LoadGenerator.Listener listener : getLoadGeneratorListeners() )
        {
            loadGeneratorBuilder.listener( listener );
        }

        LoadGenerator loadGenerator = loadGeneratorBuilder.build();
        logger.info( "loadgenerator.config: {}", loadGenerator.getConfig().toString() );
        CompletableFuture<Void> cf = loadGenerator.begin();
        cf.join();
        logger.info( "load test done" );
    }

    public void displayStats( LoadGenerator loadGenerator )
        throws Exception
    {
        writeStats( loadGenerator );
    }


    protected void writeStats( LoadGenerator loadGenerator )
        throws Exception
    {
        if ( starterArgs.getStatsFile() != null //
//            && StringUtil.isNotBlank( loadGenerator.getEndStatsResponse() ) )
            )
        {
            Path path = Paths.get( starterArgs.getStatsFile() );
            if ( Files.notExists( path ) )
            {
                Files.createFile( path );
            }

//            Files.write( path, loadGenerator.getEndStatsResponse().getBytes() );

        }
    }

    protected LoadGenerator.Listener[] getLoadGeneratorListeners()
    {
        return new LoadGenerator.Listener[0];
    }

    protected Resource.Listener[] getResourceListeners()
    {
        return new Resource.Listener[0];
    }

    protected Request.Listener[] getListeners()
    {
        return new Request.Listener[0];
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
                return resource = evaluateJson( profilePath );
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

    protected static Resource evaluateJson( Path profilePath )
        throws Exception
    {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES );
        return objectMapper.readValue( profilePath.toFile(), Resource.class );

    }

    protected static String writeAsJsonTmp( Resource resource )
        throws Exception
    {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.disable( SerializationFeature.FAIL_ON_EMPTY_BEANS );
        Path tmpPath = Files.createTempFile( "profile", ".tmp" );
        objectMapper.writeValue( tmpPath.toFile(), resource );
        return tmpPath.toString();
    }

    protected static Object evaluateScript( Reader script )
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

    public HTTPClientTransportBuilder httpClientTransportBuilder()
    {
        switch ( starterArgs.getTransport() )
        {
            case HTTP:
            case HTTPS:
            {
                return new HTTP1ClientTransportBuilder().selectors( starterArgs.getSelectors() );
            }
            case H2C:
            case H2:
            {
                return new HTTP2ClientTransportBuilder().selectors( starterArgs.getSelectors() );
            }
            default:
            {
                // nothing this weird case already handled by #provideClientTransport
            }

        }
        throw new IllegalArgumentException( "unknown httpClientTransportBuilder" );
    }

    public SslContextFactory sslContextFactory()
    {
        // FIXME make this more configurable
        SslContextFactory sslContextFactory = new SslContextFactory( true );
        return sslContextFactory;
    }

}
