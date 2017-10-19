//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
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
