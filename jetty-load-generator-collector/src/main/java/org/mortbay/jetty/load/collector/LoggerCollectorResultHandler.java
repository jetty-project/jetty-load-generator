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

package org.mortbay.jetty.load.collector;


import org.mortbay.jetty.load.generator.CollectorInformations;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.util.Map;

/**
 *
 */
public class LoggerCollectorResultHandler
    implements CollectorResultHandler
{
    private static final Logger LOGGER = Log.getLogger( LoggerCollectorResultHandler.class );


    @Override
    public void handleResponseTime( Map<String, CollectorInformations> responseTimePerPath )
    {
        for ( Map.Entry<String, CollectorInformations> entry : responseTimePerPath.entrySet() )
        {
            LOGGER.info( "path: {}, responseTime: {}", entry.getKey(), entry.getValue() );
        }
    }
}
