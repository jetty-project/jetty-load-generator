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
import com.jayway.jsonpath.Configuration;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.Option;
import com.jayway.jsonpath.TypeRef;
import com.jayway.jsonpath.spi.json.JacksonJsonProvider;
import com.jayway.jsonpath.spi.json.JsonProvider;
import com.jayway.jsonpath.spi.mapper.JacksonMappingProvider;
import com.jayway.jsonpath.spi.mapper.MappingProvider;
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

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    static
    {

        Configuration.setDefaults( new Configuration.Defaults()
        {

            private final JsonProvider jsonProvider = new JacksonJsonProvider();

            private final MappingProvider mappingProvider = new JacksonMappingProvider();

            @Override
            public JsonProvider jsonProvider()
            {
                return jsonProvider;
            }

            @Override
            public MappingProvider mappingProvider()
            {
                return mappingProvider;
            }

            @Override
            public Set<Option> options()
            {
                return EnumSet.noneOf( Option.class );
            }
        } );
    }


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
    public void save( ExtendedLoadResult extendedLoadResult )
    {
        try
        {
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
        try
        {
            ContentResponse contentResponse = httpClient.newRequest( host, port ).scheme( scheme ) //
                .path( "/loadresult/result/" + loadResult.getUuid() ) //
                .method( HttpMethod.DELETE ) //
                .send();
            if ( contentResponse.getStatus() != HttpStatus.OK_200 )
            {
                LOGGER.info( "Cannot delete load result: {}", contentResponse.getContentAsString() );
            }
            else
            {
                LOGGER.info( "Load result deleted" );
            }
        }
        catch ( Exception e )
        {
            LOGGER.warn( e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }
    }

    @Override
    public ExtendedLoadResult get( String loadResultId )
    {
        try
        {
            ContentResponse contentResponse = httpClient.newRequest( host, port ).scheme( scheme ) //
                .path( "/loadresult/result/_search/" + loadResultId ) //
                .method( HttpMethod.GET ) //
                .send();
            if ( contentResponse.getStatus() != HttpStatus.OK_200 )
            {
                LOGGER.info( "Cannot get load result: {}", contentResponse.getContentAsString() );
                return null;
            }

            List<ExtendedLoadResult> loadResults = map( contentResponse );

            LOGGER.debug( "result {}", loadResults );
            return loadResults == null || loadResults.isEmpty() ? null : loadResults.get( 0 );


        }
        catch ( Exception e )
        {
            LOGGER.warn( e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }
    }

    @Override
    public List<ExtendedLoadResult> find( QueryFiler queryFiler )
    {
        return null;
    }

    @Override
    public List<ExtendedLoadResult> findAll()
    {
        try
        {
            ContentResponse contentResponse = httpClient.newRequest( host, port ).scheme( scheme ) //
                .path( "/loadresult/result/_search" ) //
                .method( HttpMethod.GET ) //
                .send();
            if ( contentResponse.getStatus() != HttpStatus.OK_200 )
            {
                LOGGER.info( "Cannot get load result: {}", contentResponse.getContentAsString() );
                return Collections.emptyList();
            }

            List<ExtendedLoadResult> loadResults = map( contentResponse );

            LOGGER.debug( "result {}", loadResults );
            return loadResults;


        }
        catch ( Exception e )
        {
            LOGGER.warn( e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }
    }

    @Override
    public void close()
        throws IOException
    {
        try
        {
            this.httpClient.stop();
        }
        catch ( Exception e )
        {
            throw new IOException( e.getMessage(), e );
        }
    }

    public static List<ExtendedLoadResult> map( ContentResponse contentResponse )
    {
        return map( Collections.singletonList( contentResponse ) );
    }

    public static List<ExtendedLoadResult> map( List<ContentResponse> contentResponses )
    {
        List<ExtendedLoadResult> results = new ArrayList<>();
        contentResponses.stream().forEach(
            contentResponse -> results.addAll( JsonPath.parse( contentResponse.getContentAsString() ) //
                                                   .read( "$.hits.hits[*]._source",
                                                          new TypeRef<List<ExtendedLoadResult>>()
                                                          {
                                                          } ) ) );
        return results;
    }

    public HttpClient getHttpClient()
    {
        return httpClient;
    }

    @Override
    public String getProviderId()
    {
        return ID;
    }
}
