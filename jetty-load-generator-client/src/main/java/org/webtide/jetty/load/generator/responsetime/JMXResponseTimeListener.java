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

package org.webtide.jetty.load.generator.responsetime;

import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.annotation.ManagedAttribute;
import org.eclipse.jetty.util.annotation.ManagedObject;
import org.eclipse.jetty.util.component.ContainerLifeCycle;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

import java.lang.management.ManagementFactory;

/**
 *
 */
public class JMXResponseTimeListener
    implements ResponseTimeListener
{

    private static final Logger LOGGER = Log.getLogger( JMXResponseTimeListener.class );

    private JMXValues _jmxJmxValues;

    //private ConnectorServer connectorServer;

    public JMXResponseTimeListener( Server server )
    {
        MBeanContainer mbeanContainer = new MBeanContainer( ManagementFactory.getPlatformMBeanServer() );

        this._jmxJmxValues = new JMXValues();

        this._jmxJmxValues.addBean( mbeanContainer );
        try
        {
            this._jmxJmxValues.start();
        }
        catch ( Exception e )
        {
            LOGGER.warn( "skip error starting mbean: " + e.getMessage(), e );
        }

        server.addEventListener(mbeanContainer);
        server.addBean(mbeanContainer);

        server.addBean(Log.getLog());

        /*
        try
        {
            JMXServiceURL jmxServiceURL = new JMXServiceURL( "rmi", null, 1099, "/jndi/rmi://localhost:1099/jmxrmi" );

            connectorServer = new ConnectorServer( jmxServiceURL, "org.eclipse.jetty.jmx:name=rmiconnectorserver" );

            connectorServer.start();
        }
        catch ( Exception e )
        {
            LOGGER.warn( "skip error starting mbean server: " + e.getMessage(), e );
        }*/

    }

    @Override
    public void onResponseTimeValue( Values values )
    {
        this._jmxJmxValues.setLastResponseTimeValue( values.getTime() );
        this._jmxJmxValues.setPath( values.getPath() );
    }


    @Override
    public void onLoadGeneratorStop()
    {
        try
        {
            this._jmxJmxValues.stop();
        }
        catch ( Exception e )
        {
            LOGGER.warn( "skip error stopping mbean: " + e.getMessage(), e );
        }

        /*
        try
        {
            this.connectorServer.stop();
        }
        catch ( Exception e )
        {
            LOGGER.warn( "skip error stopping connectorserver: " + e.getMessage(), e );
        }*/
    }

    @ManagedObject( "this is some doco" )
    public static class JMXValues
        extends ContainerLifeCycle
    {
        private long _lastResponseTimeValue;

        private String _path;

        @ManagedAttribute( "last response time value" )
        public long getLastResponseTimeValue()
        {
            return _lastResponseTimeValue;
        }

        public void setLastResponseTimeValue( long lastResponseTimeValue )
        {
            this._lastResponseTimeValue = lastResponseTimeValue;
        }

        @ManagedAttribute( "Some value that can be set and got from" )
        public String getPath()
        {
            return _path;
        }

        public void setPath( String path )
        {
            this._path = path;
        }
    }


}
