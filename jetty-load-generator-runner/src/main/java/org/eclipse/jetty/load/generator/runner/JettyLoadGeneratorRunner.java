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

package org.eclipse.jetty.load.generator.runner;

import com.beust.jcommander.JCommander;
import org.eclipse.jetty.client.HttpClientTransport;
import org.eclipse.jetty.load.generator.Http2TransportBuilder;
import org.eclipse.jetty.load.generator.HttpFCGITransportBuilder;
import org.eclipse.jetty.load.generator.HttpTransportBuilder;
import org.eclipse.jetty.load.generator.LoadGenerator;
import org.eclipse.jetty.load.generator.profile.ResourceProfile;
import org.eclipse.jetty.load.generator.responsetime.SummaryResponseTimeListener;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.xml.XmlConfiguration;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 */
public class JettyLoadGeneratorRunner
{

    private JettyLoadGeneratorRunnerArgs runnerArgs;

    public JettyLoadGeneratorRunner( JettyLoadGeneratorRunnerArgs runnerArgs )
    {
        this.runnerArgs = runnerArgs;
    }

    public static void main( String[] args )
        throws Exception
    {

        JettyLoadGeneratorRunnerArgs runnerArgs = new JettyLoadGeneratorRunnerArgs();

        try
        {
            JCommander jCommander = new JCommander( runnerArgs, args );
            if ( runnerArgs.isHelp() )
            {
                jCommander.usage();
                System.exit( 0 );
            }
        }
        catch ( Exception e )
        {
            new JCommander( runnerArgs ).usage();
        }

        try
        {
            JettyLoadGeneratorRunner runner = new JettyLoadGeneratorRunner( runnerArgs );
            runner.run();
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            new JCommander( runnerArgs ).usage();
        }

    }


    public void run()
        throws Exception
    {
        Path profilePath = Paths.get( runnerArgs.getProfileXmlPath() );
        ResourceProfile resourceProfile;
        try (InputStream inputStream = Files.newInputStream( profilePath ))
        {
            resourceProfile = (ResourceProfile) new XmlConfiguration( inputStream ).configure();
        }

        LoadGenerator loadGenerator = new LoadGenerator.Builder() //
            .host( runnerArgs.getHost() ) //
            .port( runnerArgs.getPort() ) //
            .users( runnerArgs.getUsers() ) //
            .transactionRate( runnerArgs.getTransactionRate() ) //
            .transport( runnerArgs.getTransport() ) //
            .httpClientTransport( httpClientTransport() ) //
            .sslContextFactory( sslContextFactory() ) //
            .loadProfile( resourceProfile ) //
            .responseTimeListeners( new SummaryResponseTimeListener() ) //
            //.requestListeners( testRequestListener ) //
            //.executor( new QueuedThreadPool() )
            .build();

        loadGenerator.run( runnerArgs.getRunningTime(), runnerArgs.getRunningTimeUnit() );

    }

    protected HttpClientTransport httpClientTransport()
    {
        switch ( runnerArgs.getTransport() )
        {
            case HTTP:
            case HTTPS:
            {
                return new HttpTransportBuilder().selectors( runnerArgs.getSelectors() ).build();
            }
            case H2C:
            case H2:
            {
                return new Http2TransportBuilder().selectors( runnerArgs.getSelectors() ).build();
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
        throw new IllegalArgumentException( "unknow httpClientTransport" );
    }

    protected SslContextFactory sslContextFactory()
    {
        // FIXME make this more configurable
        SslContextFactory sslContextFactory = new SslContextFactory( true );
        return sslContextFactory;
    }

}
