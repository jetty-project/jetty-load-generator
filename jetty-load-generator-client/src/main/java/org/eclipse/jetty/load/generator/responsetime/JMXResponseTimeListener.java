package org.eclipse.jetty.load.generator.responsetime;

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
        this._jmxJmxValues.setLastLatencyValue( values.getLatencyTime() );
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
        private long _lastLatencyValue;

        private String _path;

        @ManagedAttribute( "last since latency value" )
        public long getLastLatencyValue()
        {
            return _lastLatencyValue;
        }

        public void setLastLatencyValue( long _lastLatencyValue )
        {
            this._lastLatencyValue = _lastLatencyValue;
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
