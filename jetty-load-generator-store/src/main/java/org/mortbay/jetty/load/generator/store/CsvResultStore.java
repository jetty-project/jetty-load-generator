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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import org.apache.commons.lang3.StringUtils;
import org.mortbay.jetty.load.generator.listeners.CollectorInformations;
import org.mortbay.jetty.load.generator.listeners.LoadResult;
import org.mortbay.jetty.load.generator.listeners.ServerInfo;

public class CsvResultStore
    extends AbstractResultStore
    implements ResultStore
{

    public static final String STORE_FILE_KEY = "csvStoreFile";

    private final static Logger LOGGER = Logger.getLogger( CsvResultStore.class.getName() );

    private final ReentrantLock lock = new ReentrantLock();

    private String fileName = "load_result.csv";

    public static final String ID = "csv";

    private File csvFile;

    public CsvResultStore()
    {
        //no op
    }

    @Override
    public void initialize( Map<String, String> setupData )
    {
        this.fileName = setupData.get( STORE_FILE_KEY );
        if ( StringUtils.isEmpty( this.fileName ) )
        {
            throw new IllegalArgumentException( STORE_FILE_KEY + " cannot be empty" );
        }
    }

    protected void initStoreFile( String fileName )
    {
        // create the file if not exists
        this.csvFile = new File( fileName );
        if ( !Files.exists( this.csvFile.toPath() ) )
        {
            try
            {
                Files.createFile( this.csvFile.toPath() );
                // write csv headers
                writeStrings( new String[]{ "uuid", "processors", "jettyVersion", "memory", //
                    "minValue", "meanValue", "maxValue", "total", "start", "end", "value50", //
                    "value90", "stdDeviation", "comment" } );
            }
            catch ( IOException e )
            {
                String msg = "Cannot create file:" + this.csvFile;
                LOGGER.log( Level.SEVERE, msg, e );
                throw new RuntimeException( e.getMessage(), e );
            }
        }
    }

    @Override
    public String getProviderId()
    {
        return ID;
    }

    @Override
    public void save( LoadResult loadResult )
    {
        lock.lock();
        try
        {
            writeStrings( toCsv( loadResult ) );
        }
        catch ( IOException e )
        {
            String msg = "Cannot write entry:" + loadResult;
            LOGGER.log( Level.SEVERE, msg, e );
            throw new RuntimeException( e.getMessage(), e );
        }
        finally
        {
            lock.unlock();
        }
    }

    private void writeStrings( String[] values )
        throws IOException
    {
        if ( this.csvFile == null )
        {
            initStoreFile( this.fileName );
        }
        try (CSVWriter writer = new CSVWriter( new FileWriter( this.csvFile, true ), ';',
                                               CSVWriter.DEFAULT_QUOTE_CHARACTER, CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                                               CSVWriter.DEFAULT_LINE_END ))
        {

            writer.writeNext( values );
            writer.flushQuietly();
        }

    }


    protected String[] toCsv( LoadResult loadResult )
    {
        ServerInfo serverInfo = loadResult.getServerInfo();
        CollectorInformations collectorInformations = loadResult.getCollectorInformations();

        String uuid = loadResult.getUuid();
        int processors = serverInfo.getAvailableProcessors();
        String jettyVersion = serverInfo.getJettyVersion();
        long memory = serverInfo.getTotalMemory();
        long minValue = collectorInformations.getMinValue();
        double meanValue = collectorInformations.getMean();
        long maxValue = collectorInformations.getMaxValue();
        long total = collectorInformations.getTotalCount();
        long start = collectorInformations.getStartTimeStamp();
        long end = collectorInformations.getEndTimeStamp();
        long value50 = collectorInformations.getValue50();
        long value90 = collectorInformations.getValue90();
        double stdDeviation = collectorInformations.getStdDeviation();

        return new String[]{ uuid, String.valueOf( processors ), jettyVersion, String.valueOf( memory ), //
            String.valueOf( minValue ), String.valueOf( meanValue ), String.valueOf( maxValue ), //
            String.valueOf( total ), String.valueOf( start ), String.valueOf( end ), String.valueOf( value50 ), //
            String.valueOf( value90 ), String.valueOf( stdDeviation ), loadResult.getComment() };


    }


    protected LoadResult fromCsv( String[] values )
    {

        ServerInfo serverInfo = new ServerInfo();
        serverInfo.setAvailableProcessors( Integer.valueOf( values[1] ) );
        serverInfo.setJettyVersion( values[2] );
        serverInfo.setTotalMemory( Long.valueOf( values[3] ) );

        CollectorInformations collectorInformations = new CollectorInformations();
        collectorInformations.setMinValue( Long.valueOf( values[4] ) );
        collectorInformations.setMean( Double.valueOf( values[5] ) );
        collectorInformations.setMaxValue( Long.valueOf( values[6] ) );
        collectorInformations.setTotalCount( Long.valueOf( values[7] ) );
        collectorInformations.setStartTimeStamp( Long.valueOf( values[8] ) );
        collectorInformations.setEndTimeStamp( Long.valueOf( values[9] ) );
        collectorInformations.setValue50( Long.valueOf( values[10] ) );
        collectorInformations.setValue90( Long.valueOf( values[11] ) );
        collectorInformations.setStdDeviation( Double.valueOf( values[12] ) );

        LoadResult loadResult =
            new LoadResult( serverInfo, collectorInformations, null );

        return loadResult.comment( values[13] ).uuid( values[0] );
    }

    @Override
    public void remove( LoadResult loadResult )
    {
        // not supported
    }

    @Override
    public List<LoadResult> find( QueryFilter queryFilter )
    {
        // TODO filter on result
        return findAll();
    }

    @Override
    public LoadResult get( String loadResultId )
    {
        return null;
    }

    @Override
    public List<LoadResult> get( List<String> loadResultId )
    {
        return null;
    }

    @Override
    public List<LoadResult> findAll()
    {
        return findWithFilters( null );
    }

    public List<LoadResult> findWithFilters( List<Predicate<String[]>> predicates )
    {
        lock.lock();
        try (CSVReader reader = new CSVReader( new FileReader( "yourfile.csv" ) ))
        {

            Stream<String[]> stream = Stream.generate( reader.iterator()::next );

            if ( predicates != null )
            {
                for ( Predicate predicate : predicates )
                {
                    stream = stream.filter( predicate );
                }
            }
            return stream.map( strings -> fromCsv( strings ) ) //
                .collect( Collectors.toList() );

        }
        catch ( IOException e )
        {
            LOGGER.log( Level.SEVERE, e.getMessage(), e );
            throw new RuntimeException( e.getMessage(), e );
        }
        finally
        {
            lock.unlock();
        }
    }

    @Override
    public void close()
        throws IOException
    {
        // no op
    }
}
