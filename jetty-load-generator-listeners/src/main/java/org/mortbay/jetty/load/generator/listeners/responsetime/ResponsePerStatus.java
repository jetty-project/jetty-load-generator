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

package org.mortbay.jetty.load.generator.listeners.responsetime;

import java.util.concurrent.atomic.LongAdder;

import org.mortbay.jetty.load.generator.Resource;

/**
 *
 */
public class ResponsePerStatus
    implements Resource.NodeListener
{

    private final LongAdder _responses1xx = new LongAdder();

    private final LongAdder _responses2xx = new LongAdder();

    private final LongAdder _responses3xx = new LongAdder();

    private final LongAdder _responses4xx = new LongAdder();

    private final LongAdder _responses5xx = new LongAdder();

    @Override
    public void onResourceNode( Resource.Info info )
    {
        switch ( info.getStatus() / 100 )
        {
            case 1:
                _responses1xx.increment();
                break;
            case 2:
                _responses2xx.increment();
                break;
            case 3:
                _responses3xx.increment();
                break;
            case 4:
                _responses4xx.increment();
                break;
            case 5:
                _responses5xx.increment();
                break;
            default:
                break;
        }
    }

    public long getResponses1xx()
    {
        return _responses1xx.longValue();
    }

    public long getResponses2xx()
    {
        return _responses2xx.longValue();
    }

    public long getResponses3xx()
    {
        return _responses3xx.longValue();
    }

    public long getResponses4xx()
    {
        return _responses4xx.longValue();
    }

    public long getResponses5xx()
    {
        return _responses5xx.longValue();
    }
}
