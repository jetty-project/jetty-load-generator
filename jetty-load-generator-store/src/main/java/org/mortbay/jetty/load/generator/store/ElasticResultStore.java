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

package org.mortbay.jetty.load.generator.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.AuthenticationStore;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.util.BasicAuthentication;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.mortbay.jetty.load.generator.listeners.LoadResult;

import java.io.StringWriter;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ElasticResultStore
    extends AbstractResultStore
    implements ResultStore
{

    private final static Logger LOGGER = Log.getLogger( ElasticResultStore.class );

    public static final String ID = "elastic";

    public static final String HOST_KEY = "elastic.host";

    public static final String PORT_KEY = "elastic.port";

    public static final String SCHEME_KEY = "elastic.scheme";

    public static final String USER_KEY = "elastic.user";

    public static final String PWD_KEY = "elastic.password";

    private HttpClient httpClient;

    private String host, scheme, username, password;

    private int port;


    @Override
    public void initialize( Map<String, String> setupData )
    {
        host = getSetupValue( setupData, HOST_KEY, "localhost" );
        port = getSetupValue( setupData, PORT_KEY, 9200 );
        scheme = getSetupValue( setupData, SCHEME_KEY, "http" );
        username = getSetupValue( setupData, USER_KEY, null );
        password = getSetupValue( setupData, PWD_KEY, null );

        this.httpClient = new HttpClient( new SslContextFactory( true ) );
        try
        {
            if ( StringUtils.isNotEmpty( username ) )
            {
                URI uri = new URI( scheme + "://" + host + ":" + port );
                AuthenticationStore auth = httpClient.getAuthenticationStore();
                auth.addAuthenticationResult( new BasicAuthentication.BasicResult( uri, username, password ) );
            }
            this.httpClient.start();
        }
        catch ( Exception e )
        {
            LOGGER.warn( e );
            throw new RuntimeException( "Cannot start http client: " + e.getMessage(), e );
        }
    }

    @Override
    public ExtendedLoadResult save( LoadResult loadResult )
    {
        try
        {
            ExtendedLoadResult extendedLoadResult = new ExtendedLoadResult( UUID.randomUUID().toString(), loadResult );

            StringWriter stringWriter = new StringWriter();
            new ObjectMapper().writeValue( stringWriter, extendedLoadResult );

            ContentResponse contentResponse = httpClient.newRequest( host, port ).scheme( scheme ) //
                .path( "/loadresult/result/" + extendedLoadResult.getUuid() ) //
                .content( new StringContentProvider( stringWriter.toString() ) ) //
                .method( HttpMethod.PUT ) //
                .send();

            if ( contentResponse.getStatus() != HttpStatus.CREATED_201 )
            {
                LOGGER.info( "Cannot record load result: {}", contentResponse.getContentAsString() );
            }
            else
            {
                LOGGER.info( "Load result recorded" );
            }

            return extendedLoadResult;
        }
        catch ( Exception e )
        {
            LOGGER.warn( e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }
    }

    @Override
    public void remove( ExtendedLoadResult loadResult )
    {

    }

    @Override
    public List<ExtendedLoadResult> find( QueryFiler queryFiler )
    {
        return null;
    }

    @Override
    public List<ExtendedLoadResult> findAll()
    {
        return null;
    }

    @Override
    public String getProviderId()
    {
        return ID;
    }
}
