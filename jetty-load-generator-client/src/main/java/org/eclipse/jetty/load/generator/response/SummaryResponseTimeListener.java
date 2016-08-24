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
package org.eclipse.jetty.load.generator.response;

import org.HdrHistogram.Recorder;
import org.eclipse.jetty.load.generator.CollectorInformations;

import java.util.Map;

/**
 *
 */
public class SummaryResponseTimeListener
    implements ResponseTimeListener
{

    //private static final Logger LOGGER = Log.getLogger( SummaryLatencyListener.class );

    private Map<String, Recorder> recorderPerPath;

    public SummaryResponseTimeListener( Map<String, Recorder> recorderPerPath )
    {
        this.recorderPerPath = recorderPerPath;
    }

    @Override
    public void onResponseTimeValue( String path, long responseTime )
    {
        Recorder recorder = recorderPerPath.get( path );
        if ( recorder != null )
        {
            recorder.recordValue( responseTime );
        }
    }

    @Override
    public void onLoadGeneratorStop()
    {
        StringBuilder message =  //
            new StringBuilder( System.lineSeparator()) //
                .append( "--------------------------------------" ).append( System.lineSeparator() ) //
                .append( "   Response Time Summary    " ).append( System.lineSeparator() ) //
                .append( "--------------------------------------" ).append( System.lineSeparator() ); //

        for ( Map.Entry<String, Recorder> entry : recorderPerPath.entrySet() )
        {
            message.append( "Path:" ).append( entry.getKey() ).append( System.lineSeparator() );
            message.append( new CollectorInformations( entry.getValue().getIntervalHistogram(), //
                                                       CollectorInformations.InformationType.REQUEST ) //
                                .toString( true ) ) //
                .append( System.lineSeparator() );

        }
        System.out.println( message.toString() );
    }
}
