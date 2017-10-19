package org.mortbay.jetty.load.generator.store;

import java.util.Map;

public abstract class AbstractResultStore
    implements ResultStore
{

    /**
     * default implementation checking if fqcn is a sys prop with true.
     *
     * @return
     */
    public boolean isActive( Map<String, String> setupData )
    {
        return Boolean.getBoolean( getClass().getName() ) || Boolean.parseBoolean(
            setupData.get( getClass().getName() ) );
    }

    protected String getSetupValue( Map<String, String> setupData, String key, String defaultValue )
    {
        String value = setupData.get( key );
        return value != null ? value : System.getProperty( key, defaultValue );
    }

    protected Integer getSetupValue( Map<String, String> setupData, String key, int defaultValue )
    {
        String value = setupData.get( key );
        return value != null ? Integer.valueOf( value ) : Integer.getInteger( key, defaultValue );
    }

}
