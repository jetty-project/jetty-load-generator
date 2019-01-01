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

package org.mortbay.jetty.load.generator.listeners.report;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Contains all response time values!
 */
public class DetailledTimeValuesReport
{

    private List<Entry> entries = new CopyOnWriteArrayList<>();

    public DetailledTimeValuesReport()
    {
        // no op
    }

    public List<Entry> getEntries()
    {
        return entries;
    }

    public void setEntries( List<Entry> entries )
    {
        this.entries = entries;
    }

    public void addEntry( Entry entry )
    {
        this.entries.add( entry );
    }

    public static class Entry
    {
        // in  millis
        private long timeStamp;

        private String path;

        private int httpStatus;

        // in nano s
        private long time;

        public Entry( long timeStamp, String path, int httpStatus, long time )
        {
            this.timeStamp = timeStamp;
            this.path = path;
            this.httpStatus = httpStatus;
            this.time = time;
        }

        public String getPath()
        {
            return path;
        }

        public int getHttpStatus()
        {
            return httpStatus;
        }

        public long getTime()
        {
            return time;
        }

        public void setPath( String path )
        {
            this.path = path;
        }

        public void setHttpStatus( int httpStatus )
        {
            this.httpStatus = httpStatus;
        }

        public void setTime( long time )
        {
            this.time = time;
        }

        public long getTimeStamp()
        {
            return timeStamp;
        }

        public void setTimeStamp( long timeStamp )
        {
            this.timeStamp = timeStamp;
        }
    }

}
