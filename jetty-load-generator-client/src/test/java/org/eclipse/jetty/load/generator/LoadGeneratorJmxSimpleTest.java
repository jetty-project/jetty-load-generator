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

package org.eclipse.jetty.load.generator;


import org.eclipse.jetty.jmx.MBeanContainer;
import org.eclipse.jetty.load.generator.responsetime.JMXResponseTimeListener;
import org.eclipse.jetty.load.generator.responsetime.ResponseTimeListener;
import org.eclipse.jetty.load.generator.profile.Resource;
import org.eclipse.jetty.load.generator.profile.ResourceProfile;
import org.eclipse.jetty.util.log.Log;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

@RunWith( Parameterized.class )
public class LoadGeneratorJmxSimpleTest
    extends AbstractLoadGeneratorTest
{

    public LoadGeneratorJmxSimpleTest( LoadGenerator.Transport transport, int usersNumber )
    {
        super( transport, usersNumber );
    }

    @Override
    protected List<ResponseTimeListener> getLatencyListeners()
    {
        List<ResponseTimeListener> listeners = new ArrayList<>( super.getLatencyListeners() );
        listeners.add( new JMXResponseTimeListener(server) );
        return listeners;
    }

    @Override
    protected void enhanceLoadGenerator( LoadGenerator loadGenerator ) throws Exception
    {
        MBeanContainer mbeanContainer = new MBeanContainer( ManagementFactory.getPlatformMBeanServer() );
        loadGenerator.addBean( mbeanContainer );
        loadGenerator.start();

        server.addBean(mbeanContainer);

        server.addBean( Log.getLog());
    }

    @Test
    public void simple_test()
        throws Exception
    {

        ResourceProfile resourceProfile = new ResourceProfile( new Resource( "/index.html" ) );

        runProfile( resourceProfile );

    }


}
