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

package org.eclipse.jetty.load.generator;

import org.HdrHistogram.Recorder;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

public class LoadGeneratorResult
{
    private AtomicLong totalRequest = new AtomicLong( 0 );

    private AtomicLong totalResponse = new AtomicLong( 0 );

    private AtomicLong totalSuccess = new AtomicLong( 0 );

    private AtomicLong totalFailure = new AtomicLong( 0 );

    private final Map<String, Recorder> recorderPerPath;

    public LoadGeneratorResult( Map<String, Recorder> recorderPerPath )
    {
        this.recorderPerPath = recorderPerPath;
    }

    public AtomicLong getTotalRequest()
    {
        return totalRequest;
    }

    public AtomicLong getTotalResponse()
    {
        return totalResponse;
    }

    public AtomicLong getTotalSuccess()
    {
        return totalSuccess;
    }

    public AtomicLong getTotalFailure()
    {
        return totalFailure;
    }

    public Map<String, CollectorInformations> getCollectorInformationsPerPath() {
        Map<String,CollectorInformations> map = new HashMap<>( this.recorderPerPath.size() );

        for(Map.Entry<String, Recorder> entry : this.recorderPerPath.entrySet())
        {
            map.put( entry.getKey(), new CollectorInformations( entry.getValue().getIntervalHistogram(), //
                                                                CollectorInformations.InformationType.REQUEST ) );
        }

        return map;
    }

}
