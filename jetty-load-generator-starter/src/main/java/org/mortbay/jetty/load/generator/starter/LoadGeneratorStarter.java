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

package org.mortbay.jetty.load.generator.starter;

import com.beust.jcommander.JCommander;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;

/**
 *
 */
public class LoadGeneratorStarter
    extends AbstractLoadGeneratorStarter
{

    private static final Logger LOGGER = Log.getLogger( LoadGeneratorStarter.class);

    public LoadGeneratorStarter( LoadGeneratorStarterArgs runnerArgs )
    {
        super( runnerArgs );
    }

    public static void main( String[] args )
        throws Exception
    {

        LoadGeneratorStarterArgs runnerArgs = new LoadGeneratorStarterArgs();

        try
        {
            JCommander jCommander = new JCommander( runnerArgs, args );
            if ( runnerArgs.isHelp() )
            {
                jCommander.usage();
                return;
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            new JCommander( runnerArgs ).usage();
            return;
        }

        try
        {
            LoadGeneratorStarter runner = new LoadGeneratorStarter( runnerArgs );
            runner.run();

            if (runnerArgs.isDisplayStatsAtEnd())
            {

            }

        }
        catch ( Exception e )
        {
            LOGGER.info( "error happened", e);
            new JCommander( runnerArgs ).usage();
        }
    }


}
