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
import org.eclipse.jetty.load.generator.LoadGenerator;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public class HistogramLatencyRecorder
    implements LatencyListener
{

    private static final Logger LOGGER = Log.getLogger( LoadGenerator.class );

    private final Recorder latencyRecorder = new Recorder( TimeUnit.MICROSECONDS.toNanos( 1 ), //
                                                   TimeUnit.MINUTES.toNanos( 1 ), //
                                                   3 );


    public HistogramLatencyRecorder()
    {
        // no op
    }

    @Override
    public void onLatencyValue( long latencyValue )
    {
        this.latencyRecorder.recordValue( latencyValue );
    }

    // FIXME schedule fixed delay print
    /*
    LOGGER.info( "latency informations: {}", //
                            new CollectorInformations( latencyRecorder.getIntervalHistogram(),
                                CollectorInformations.InformationType.LATENCY ) );
    */

    public CollectorInformations getCollectorInformations()
    {
        return new CollectorInformations( latencyRecorder.getIntervalHistogram(),
                                          CollectorInformations.InformationType.LATENCY );
    }

    @Override
    public void onLoadGeneratorStop()
    {
        LOGGER.info( "latency informations: {}", //
                     new CollectorInformations( latencyRecorder.getIntervalHistogram(),
                                                CollectorInformations.InformationType.LATENCY ) );
    }
}
