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

package org.mortbay.jetty.load.generator.store;

import org.apache.commons.lang3.StringUtils;

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
        return !StringUtils.isEmpty( value ) ? value : System.getProperty( key, defaultValue );
    }

    protected Integer getSetupValue( Map<String, String> setupData, String key, int defaultValue )
    {
        String value = setupData.get( key );
        return !StringUtils.isEmpty( value ) ? Integer.valueOf( value ) : Integer.getInteger( key, defaultValue );
    }

}
