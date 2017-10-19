package org.mortbay.jetty.load.generator.store;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.util.EntityUtils;
import org.eclipse.jetty.http.HttpMethod;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.mortbay.jetty.load.generator.listeners.LoadResult;

import java.io.StringWriter;
import java.util.Collections;
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

    private RestClient restClient;

    @Override
    public void initialize( Map<String, String> setupData )
    {
        this.restClient = RestClient //
            .builder( new HttpHost( getSetupValue( setupData, HOST_KEY, "localhost" ), //
                                    getSetupValue( setupData, PORT_KEY, 9200 ), //
                                    getSetupValue( setupData, SCHEME_KEY, "http" ) ) ) //
            .build();
    }

    @Override
    public ExtendedLoadResult save( LoadResult loadResult )
    {
        try
        {
            ExtendedLoadResult extendedLoadResult = new ExtendedLoadResult( UUID.randomUUID().toString(), loadResult );

            StringWriter stringWriter = new StringWriter();
            new ObjectMapper().writeValue( stringWriter, extendedLoadResult );

            HttpEntity entity = new NStringEntity( stringWriter.toString(), ContentType.APPLICATION_JSON );

            Response indexResponse = restClient.performRequest( HttpMethod.PUT.asString(), //
                                                                "/loadresult/result/" + extendedLoadResult.getUuid(), //
                                                                Collections.emptyMap(), //
                                                                entity );
            LOGGER.info( EntityUtils.toString( indexResponse.getEntity() ) );

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
