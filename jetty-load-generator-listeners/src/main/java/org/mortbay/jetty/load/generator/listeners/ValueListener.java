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
package org.mortbay.jetty.load.generator.listeners;

import java.io.Serializable;

import org.mortbay.jetty.load.generator.LoadGenerator;

/**
 *
 */
public interface ValueListener
{

    /**
     * triggered when building {@link LoadGenerator} instance
     * @since 0.3
     */
    default void onLoadGeneratorStart( LoadGenerator loadGenerator )
    {
        //no op
    }

    /**
     * triggered before starting a {@link LoadGenerator} run
     * @since 0.3
     */
    default void beforeRun( LoadGenerator loadGenerator )
    {
        // no op
    }

    /**
     * triggered after finishing a {@link LoadGenerator} run
     * @since 0.3
     */
    default void afterRun( LoadGenerator loadGenerator )
    {
        // no op
    }

    /**
     * can be called
     * @param loadGenerator can be <code>null</code>
     * @since 0.3
     */
    default void reset( LoadGenerator loadGenerator )
    {
        // no op
    }

    /**
     * triggered when the load generator is stopped
     */
    void onLoadGeneratorStop();


    class Values
        implements Serializable
    {

        /**
         * the timestamp in millis seconds
         */
        private long eventTimestamp;

        private String path;

        /**
         * the value in nano seconds
         */
        private long time;

        private String method;

        private long size;

        private int status;

        public Values()
        {
            // no op
        }

        public String getPath()
        {
            return path;
        }

        public void setPath( String path )
        {
            this.path = path;
        }

        public Values path( String path )
        {
            this.path = path;
            return this;
        }

        public long getTime()
        {
            return time;
        }

        public void setTime( long time )
        {
            this.time = time;
        }

        public Values time( long time )
        {
            this.time = time;
            return this;
        }

        public String getMethod()
        {
            return method;
        }

        public void setMethod( String method )
        {
            this.method = method;
        }

        public Values method( String method )
        {
            this.method = method;
            return this;
        }

        public long getSize()
        {
            return size;
        }

        public void setSize( long size )
        {
            this.size = size;
        }

        public Values size( long size )
        {
            this.size = size;
            return this;
        }

        public int getStatus()
        {
            return status;
        }

        public void setStatus( int status )
        {
            this.status = status;
        }

        public Values status( int status )
        {
            this.status = status;
            return this;
        }

        public long getEventTimestamp()
        {
            return eventTimestamp;
        }

        public void setEventTimestamp( long eventTimestamp )
        {
            this.eventTimestamp = eventTimestamp;
        }

        public Values eventTimestamp( long eventTimestamp )
        {
            this.eventTimestamp = eventTimestamp;
            return this;
        }

        @Override
        public String toString()
        {
            return "Values{" + "eventTimestamp=" + eventTimestamp + ", path='" + path + '\'' + ", time=" + time
                + ", method='" + method + '\'' + ", size=" + size + ", status=" + status + '}';
        }
    }

}
