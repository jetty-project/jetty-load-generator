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

import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.http.HttpMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 *
 */
public class LoadGeneratorWorkflow
{

    private List<Step> steps = new ArrayList<>();


    public LoadGeneratorWorkflow( List<Step> steps )
    {
        this.steps = steps;
    }

    public List<Step> getSteps()
    {
        return steps;
    }

    //--------------------------------------------------------------
    //  Builder
    //--------------------------------------------------------------

    public static class Builder
    {

        /*

        resource("/index.html").size(1024)
            .then()
                .resourceGroup()
                    .resource("/styles.css").size(512)
                    .resource("/script.js").size(2048)
            .then()
                .resource("/foo.html").size(1024)
            .then()
                .resource("/beer.html)
            .then()



        with the idea that you can specify whether to wait for a resource
        (using then()), or to ask for them in parallel (via resourceGroup above).

        */

        private Stack<Step> steps = new Stack<>();

        private boolean resourceGroup = false;

        public static Builder builder()
        {
            return new Builder();
        }

        private Builder()
        {
            // no op
        }

        public LoadGeneratorWorkflow build()
        {
            return new LoadGeneratorWorkflow( steps );
        }

        public Builder resource( String path )
        {
            Resource resource = new Resource( path );
            if ( resourceGroup )
            {
                steps.peek().getResources().add( resource );
            }
            else
            {
                Step current = new Step( resource );
                steps.push( current );
            }
            return this;
        }

        private Resource getCurrent()
        {
            Step current = steps.peek();
            if ( current == null )
            {
                return null;
            }
            Resource resource = current.getResources().get( current.getResources().size() - 1 );
            return resource;
        }

        public Builder size( int size )
        {
            Resource resource = getCurrent();
            if ( resource == null )
            {
                throw new IllegalArgumentException( "not resource defined" );
            }
            resource.size( size );
            return this;
        }

        public Builder method( String method )
        {
            Resource resource = getCurrent();
            if ( resource == null )
            {
                throw new IllegalArgumentException( "not resource defined" );
            }
            resource.method( method );
            return this;
        }

        public Builder then()
        {
            Step step = steps.peek();
            if ( step == null )
            {
                throw new IllegalArgumentException( "not step defined" );
            }
            step.wait = true;
            resourceGroup = false;
            return this;
        }

        public Builder resourceGroup()
        {
            resourceGroup = true;
            this.steps.add( new Step() );
            return this;
        }

    }

    //--------------------------------------------------------------
    //  DSL Beans
    //--------------------------------------------------------------

    public static class Step
    {

        private List<Resource> resources;

        /**
         * wait the response before requesting the next step
         */
        private boolean wait;

        private Step()
        {
            this.resources = new ArrayList<>();
        }

        private Step( Resource resource )
        {
            this();
            this.resources.add( resource );
        }


        private Step( List<Resource> resources )
        {
            this.resources = resources;
        }

        public List<Resource> getResources()
        {
            return resources;
        }

        public boolean isWait()
        {
            return wait;
        }

        protected Step clone()
        {
            Step clone = new Step();
            clone.wait = this.wait;
            for ( Resource resource : this.resources )
            {
                clone.resources.add( resource.clone() );
            }

            return clone;
        }
    }

    public static class Resource
    {

        private int responseSize;

        private String path;

        private int size;

        private String method = HttpMethod.GET.asString();

        /**
         * the actual http request
         */
        Request request;

        public Resource( String path )
        {
            this.path = path;
        }

        public Resource path( String path )
        {
            this.path = path;
            return this;
        }

        public Resource size( int size )
        {
            this.size = size;
            return this;
        }

        public Resource method( String method )
        {
            this.method = method;
            return this;
        }

        public String getPath()
        {
            return path;
        }

        public int getSize()
        {
            return size;
        }

        public String getMethod()
        {
            return method;
        }

        public Resource responseSize( int responseSize )
        {
            this.responseSize = responseSize;
            return this;
        }

        public int getResponseSize()
        {
            return responseSize;
        }

        protected Resource clone()
        {
            Resource resource = new Resource( this.path );
            resource.size = this.size;
            resource.responseSize = this.responseSize;
            return resource;
        }
    }
}
