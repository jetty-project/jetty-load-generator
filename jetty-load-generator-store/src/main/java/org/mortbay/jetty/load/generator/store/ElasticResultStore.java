//
//  ========================================================================
//  Copyright (c) 1995-2019 Mort Bay Consulting Pty. Ltd.
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

import com.fasterxml.jackson.databind.DeserializationFeature;
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
import org.mortbay.jetty.load.generator.listeners.LoadResult;

import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
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

            private final JsonProvider jsonProvider = new JacksonJsonProvider(
                new ObjectMapper().configure( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false ) );

            private final MappingProvider mappingProvider = new JacksonMappingProvider(
                new ObjectMapper().configure( DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false ) );

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
            LOGGER.debug( "elastic http client initialize to {}:{}", host, port );
        }
        catch ( Exception e )
        {
            LOGGER.warn( e );
            throw new RuntimeException( "Cannot start http client: " + e.getMessage(), e );
        }
    }

    @Override
    public void save( LoadResult loadResult )
    {
        try
        {
            StringWriter stringWriter = new StringWriter();
            new ObjectMapper().writeValue( stringWriter, loadResult );
            LOGGER.debug( "save loadResult with UUID {}", loadResult.getUuid() );

            ContentResponse contentResponse = httpClient.newRequest( host, port ).scheme( scheme ) //
                .path( "/loadresult/result/" + loadResult.getUuid() ) //
                .content( new StringContentProvider( stringWriter.toString() ) ) //
                .method( HttpMethod.PUT ) //
                .header( "Content-Type", "application/json" ) //
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
            LOGGER.warn( "Cannot save result:" + e.getMessage(), e );
            //throw new RuntimeException( e.getMessage(), e );
        }
    }

    @Override
    public void remove( LoadResult loadResult )
    {
        try
        {
            ContentResponse contentResponse = httpClient.newRequest( host, port ) //
                .scheme( scheme ) //
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
    public LoadResult get( String loadResultId )
    {
        try
        {
            ContentResponse contentResponse = httpClient.newRequest( host, port ) //
                .scheme( scheme ) //
                .path( "/loadresult/result/_search/" + loadResultId ) //
                .method( HttpMethod.GET ) //
                .send();
            if ( contentResponse.getStatus() != HttpStatus.OK_200 )
            {
                LOGGER.info( "Cannot get load result: {}", contentResponse.getContentAsString() );
                return null;
            }

            List<LoadResult> loadResults = map( contentResponse );

            LOGGER.debug( "result {}", loadResults );
            return loadResults == null || loadResults.isEmpty() ? null : loadResults.get( 0 );


        }
        catch ( Exception e )
        {
            LOGGER.warn( e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }
    }

    public List<LoadResult> searchResultsByExternalId(String anExternalId) {
        try
        {


            Map<String, Object> json = new HashMap<>();

            Map<String, Object> externalId = new HashMap<>();
            externalId.put( "externalId", anExternalId );
            Map<String, Object> term = new HashMap<>();
            term.put( "term", externalId );

            Map<String, Object> filter = new HashMap<>();
            filter.put( "filter", term );

            Map<String, Object> constant_score = new HashMap<>();
            constant_score.put( "constant_score", filter );

            json.put( "query", constant_score );

            Map<String, Object> order = new HashMap<>();
            order.put( "order", "desc" );
            Map<String, Object> timestamp = new HashMap<>();
            timestamp.put( "timestamp", order );

            json.put( "sort", timestamp );

            StringWriter stringWriter = new StringWriter();
            new ObjectMapper().writeValue( stringWriter, json );



            ContentResponse contentResponse = getHttpClient() //
                .newRequest( host, port ) //
                .scheme( scheme ) //
                .header( "Content-Type","application/json" ) //
                .method( HttpMethod.GET ) //
                .path( "/loadresult/result/_search?sort=timestamp" ) //
                .content( new StringContentProvider( stringWriter.toString() ) ) //
                .send();
            List<LoadResult> loadResults = map( contentResponse );
            return loadResults;
        }
        catch ( Exception e )
        {
            LOGGER.warn( e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }
    }

    @Override
    public List<LoadResult> get( List<String> loadResultIds )
    {
        try
        {
            //     we need this type of Json
            //        {
            //            "query": {
            //            "ids" : {
            //                "values" : ["192267e6-7f74-4806-867a-c13ef777d6eb", "80a2dc5b-4a92-48ba-8f5b-f2de1588318a"]
            //            }
            //        }
            //        }
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> values = new HashMap<>();
            values.put( "values", loadResultIds );
            Map<String, Object> ids = new HashMap<>();
            ids.put( "ids", values );
            Map<String, Object> query = new HashMap<>();
            query.put( "query", ids );
            StringWriter stringWriter = new StringWriter();
            objectMapper.writeValue( stringWriter, query );

            ContentResponse contentResponse = getHttpClient() //
                .newRequest( host, port ) //
                .scheme( scheme ) //
                .method( HttpMethod.GET ) //
                .header( "Content-Type", "application/json" ) //
                .path( "/loadresult/result/_search?sort=timestamp" ) //
                .content( new StringContentProvider( stringWriter.toString() ) ) //
                .send();
            List<LoadResult> loadResults = map( contentResponse );
            return loadResults;
        }
        catch ( Exception e )
        {
            LOGGER.warn( e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }
    }

    public String search( String searchPost )
    {
        try
        {
            ContentResponse contentResponse = getHttpClient() //
                .newRequest( host, port ) //
                .scheme( scheme ) //
                .method( HttpMethod.GET ) //
                .header( "Content-Type", "application/json" ) //
                .path( "/loadresult/result/_search?pretty" ) //
                .content( new StringContentProvider( searchPost ) ) //
                .send();
            return contentResponse.getContentAsString();
        }
        catch ( Exception e )
        {
            LOGGER.warn( e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }
    }

    @Override
    public List<LoadResult> find( QueryFilter queryFilter )
    {
        return null;
    }

    @Override
    public List<LoadResult> findAll()
    {
        try
        {
            ContentResponse contentResponse = httpClient.newRequest( host, port ) //
                .scheme( scheme ) //
                .path( "/loadresult/result/_search?pretty" ) //
                .method( HttpMethod.GET ) //
                .send();
            if ( contentResponse.getStatus() != HttpStatus.OK_200 )
            {
                LOGGER.info( "Cannot get load result: {}", contentResponse.getContentAsString() );
                return Collections.emptyList();
            }

            List<LoadResult> loadResults = map( contentResponse );

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

    public static List<LoadResult> map( ContentResponse contentResponse )
    {
        return map( Collections.singletonList( contentResponse ) );
    }

    public static List<LoadResult> map( List<ContentResponse> contentResponses )
    {
        List<LoadResult> results = new ArrayList<>();
        contentResponses.stream().forEach(
            contentResponse -> results.addAll( JsonPath.parse( contentResponse.getContentAsString() ) //
                                                   .read( "$.hits.hits[*]._source", new TypeRef<List<LoadResult>>()
                                                   {
                                                   } ) ) );
        return results;
    }

    private HttpClient getHttpClient()
    {
        return httpClient;
    }

    @Override
    public String getProviderId()
    {
        return ID;
    }
}
