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

package org.eclipse.jetty.load.generator.latency;

import org.HdrHistogram.Recorder;
import org.eclipse.jetty.load.generator.CollectorInformations;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public class SummaryLatencyListener
    implements LatencyListener
{

    //private static final Logger LOGGER = Log.getLogger( SummaryLatencyListener.class );

    private final Recorder latencyRecorder = new Recorder( TimeUnit.MICROSECONDS.toNanos( 1 ), //
                                                           TimeUnit.MINUTES.toNanos( 1 ), //
                                                           3 );

    @Override
    public void onLatencyValue( long latencyValue )
    {
        latencyRecorder.recordValue( latencyValue );
    }

    @Override
    public void onLoadGeneratorStop()
    {
        StringBuilder message =  //
            new StringBuilder( "--------------------------------------" ).append( System.lineSeparator() ) //
                .append( "   Latency Summary    " ).append( System.lineSeparator() ) //
                .append( "--------------------------------------" ).append( System.lineSeparator() ) //
                .append( new CollectorInformations( latencyRecorder.getIntervalHistogram(), //
                                                    CollectorInformations.InformationType.LATENCY ).toString(true) );

        //LOGGER.info( message.toString(), );
        System.out.print( message );
    }
}
