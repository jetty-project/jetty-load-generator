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

package org.eclipse.jetty.load.generator.profile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 */
public class LoadGeneratorProfile
{

    private List<Step> steps = new ArrayList<>();

    public LoadGeneratorProfile()
    {
        // no op
    }

    public LoadGeneratorProfile( List<Step> steps )
    {
        this.steps = steps;
    }

    public LoadGeneratorProfile( Step... steps )
    {
        this.steps = steps == null ? new ArrayList<>() : Arrays.asList( steps );
    }

    public List<Step> getSteps()
    {
        return steps;
    }

    public void setSteps( List<Step> steps )
    {
        this.steps = steps;
    }

    public void addStep( Step step )
    {
        if ( this.steps == null )
        {
            this.steps = new ArrayList<>();
        }
        this.steps.add( step );
    }
}
