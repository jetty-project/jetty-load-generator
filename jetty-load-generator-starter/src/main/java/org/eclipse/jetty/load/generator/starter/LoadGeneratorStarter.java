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

package org.eclipse.jetty.load.generator.starter;

import com.beust.jcommander.JCommander;

/**
 *
 */
public class LoadGeneratorStarter
    extends AbstractLoadGeneratorStarter
{

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
                System.exit( 0 );
            }
        }
        catch ( Exception e )
        {
            new JCommander( runnerArgs ).usage();
        }

        try
        {
            LoadGeneratorStarter runner = new LoadGeneratorStarter( runnerArgs );
            if (runnerArgs.getRunIteration() > 0)
            {
                runner.run(runnerArgs.getRunIteration());
            } else
            {
                runner.run();
            }
        }
        catch ( Exception e )
        {
            e.printStackTrace();
            new JCommander( runnerArgs ).usage();
        }
    }


}
