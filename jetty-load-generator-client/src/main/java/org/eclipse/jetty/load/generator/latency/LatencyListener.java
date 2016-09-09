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

/**
 *
 */
public interface LatencyListener
{
    /**
     * triggered with an http client latency value value
     * @param values the latency values
     */
    void onLatencyValue( Values values );

    /**
     * triggered when the load generator is stopped
     */
    void onLoadGeneratorStop();

    class Values
    {
        private String path;

        /**
         * the latency value in nano seconds
         */
        private long latencyValue;

        private String method;

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

        public long getLatencyValue()
        {
            return latencyValue;
        }

        public void setLatencyValue( long latencyValue )
        {
            this.latencyValue = latencyValue;
        }

        public Values latencyValue( long latencyValue )
        {
            this.latencyValue = latencyValue;
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

        @Override
        public String toString()
        {
            return "Values{" + "path='" + path + '\'' + ", latencyValue=" + latencyValue + ", method='" + method + '\''
                + '}';
        }
    }

}
