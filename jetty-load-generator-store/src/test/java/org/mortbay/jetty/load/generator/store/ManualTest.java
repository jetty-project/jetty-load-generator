package org.mortbay.jetty.load.generator.store;

import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.TypeRef;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.mortbay.jetty.load.generator.listeners.LoadResult;

import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class ManualTest
{
    public static void main(String[] args) throws Exception
    {
        ElasticResultStore elasticResultStore = new ElasticResultStore();
        Map<String, String> setupData = new HashMap<>();
        setupData.put( ElasticResultStore.HOST_KEY, "localhost" );
        setupData.put( ElasticResultStore.PORT_KEY, "9200" );
        setupData.put( ElasticResultStore.SCHEME_KEY, "http" );
        elasticResultStore.initialize( setupData );

        LoadResult loadResult = new LoadResult() //
            .uuid( UUID.randomUUID().toString() ) //
            .comment( "comment foo" ) //
            .timestamp( Instant.now().toString() );
        loadResult.getServerInfo().setJettyVersion( "9.1" );
        elasticResultStore.save( loadResult );

        List<LoadResult> results = elasticResultStore.findAll();
        System.out.println( results );


        results =  elasticResultStore.searchResultsByExternalId( "98" );
        System.out.println( results );


        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream( "distinctJettyVersion.json" ))
        {
            String distinctSearchQuery = IOUtils.toString( inputStream, Charset.defaultCharset() );

            String distinctResult = elasticResultStore.search( distinctSearchQuery );

            System.out.println( distinctResult );

            List<Map<String,String>> versionsListMap = JsonPath.parse( distinctResult ).read( "$.aggregations.version.buckets");

            Map<String, String> versions = versionsListMap.stream().collect( Collectors.toMap( m -> m.get( "key" ),
                                                                                                m -> String.valueOf( m.get("doc_count") ) ) );
            System.out.println( versions );
        }
    }

}
