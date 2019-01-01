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

package org.mortbay.jetty.load.generator.listeners;

import org.mortbay.jetty.load.generator.LoadGenerator;

public class LoadConfig
{

    private int threads;

    private int warmupIterationsPerThread;

    private int iterationsPerThread;

    private long runFor;

    private int usersPerThread;

    private int channelsPerUser;

    private int resourceRate;

    private String scheme;

    private String host;

    private int port;

    private int maxRequestsQueued;

    /**
     * loader or probe or something else
     */
    private Type type;

    private int resourceNumber;

    private int instanceNumber;

    private String transport;


    public enum Type {
        LOADER,PROBE;
    }

    public LoadConfig()
    {
        //
    }

    public LoadConfig( LoadGenerator.Config config )
    {
        this.threads = config.getThreads();
        this.warmupIterationsPerThread = config.getWarmupIterationsPerThread();
        this.iterationsPerThread = config.getIterationsPerThread();
        this.runFor = config.getRunFor();
        this.usersPerThread = config.getUsersPerThread();
        this.channelsPerUser = config.getChannelsPerUser();
        this.resourceRate = config.getResourceRate();
        this.scheme = config.getScheme();
        this.host = config.getHost();
        this.port = config.getPort();
        this.maxRequestsQueued = config.getMaxRequestsQueued();
    }

    public LoadConfig( int threads, int warmupIterationsPerThread, int iterationsPerThread, long runFor,
                       int usersPerThread, int channelsPerUser, int resourceRate, String scheme, String host, int port,
                       int maxRequestsQueued )
    {
        this.threads = threads;
        this.warmupIterationsPerThread = warmupIterationsPerThread;
        this.iterationsPerThread = iterationsPerThread;
        this.runFor = runFor;
        this.usersPerThread = usersPerThread;
        this.channelsPerUser = channelsPerUser;
        this.resourceRate = resourceRate;
        this.scheme = scheme;
        this.host = host;
        this.port = port;
        this.maxRequestsQueued = maxRequestsQueued;
    }

    public int getThreads()
    {
        return threads;
    }

    public void setThreads( int threads )
    {
        this.threads = threads;
    }

    public int getWarmupIterationsPerThread()
    {
        return warmupIterationsPerThread;
    }

    public void setWarmupIterationsPerThread( int warmupIterationsPerThread )
    {
        this.warmupIterationsPerThread = warmupIterationsPerThread;
    }

    public int getIterationsPerThread()
    {
        return iterationsPerThread;
    }

    public void setIterationsPerThread( int iterationsPerThread )
    {
        this.iterationsPerThread = iterationsPerThread;
    }

    public long getRunFor()
    {
        return runFor;
    }

    public void setRunFor( long runFor )
    {
        this.runFor = runFor;
    }

    public int getUsersPerThread()
    {
        return usersPerThread;
    }

    public void setUsersPerThread( int usersPerThread )
    {
        this.usersPerThread = usersPerThread;
    }

    public int getChannelsPerUser()
    {
        return channelsPerUser;
    }

    public void setChannelsPerUser( int channelsPerUser )
    {
        this.channelsPerUser = channelsPerUser;
    }

    public int getResourceRate()
    {
        return resourceRate;
    }

    public void setResourceRate( int resourceRate )
    {
        this.resourceRate = resourceRate;
    }

    public String getScheme()
    {
        return scheme;
    }

    public void setScheme( String scheme )
    {
        this.scheme = scheme;
    }

    public String getHost()
    {
        return host;
    }

    public void setHost( String host )
    {
        this.host = host;
    }

    public int getPort()
    {
        return port;
    }

    public void setPort( int port )
    {
        this.port = port;
    }

    public int getMaxRequestsQueued()
    {
        return maxRequestsQueued;
    }

    public void setMaxRequestsQueued( int maxRequestsQueued )
    {
        this.maxRequestsQueued = maxRequestsQueued;
    }

    public Type getType()
    {
        return type;
    }

    public void setType( Type type )
    {
        this.type = type;
    }

    public LoadConfig type( Type type )
    {
        this.type = type;
        return this;
    }

    public int getResourceNumber()
    {
        return resourceNumber;
    }

    public void setResourceNumber( int resourceNumber )
    {
        this.resourceNumber = resourceNumber;
    }

    public LoadConfig resourceNumber( int resourceNumber )
    {
        this.resourceNumber = resourceNumber;
        return this;
    }

    public int getInstanceNumber()
    {
        return instanceNumber;
    }

    public void setInstanceNumber( int instanceNumber )
    {
        this.instanceNumber = instanceNumber;
    }

    public LoadConfig instanceNumber( int instanceNumber )
    {
        this.instanceNumber = instanceNumber;
        return this;
    }

    public String getTransport()
    {
        return transport;
    }

    public void setTransport( String transport )
    {
        this.transport = transport;
    }

    public LoadConfig transport( String transport )
    {
        this.transport = transport;
        return this;
    }

    @Override
    public String toString()
    {
        return "LoadConfig{" + "threads=" + threads + ", warmupIterationsPerThread=" + warmupIterationsPerThread
            + ", iterationsPerThread=" + iterationsPerThread + ", runFor=" + runFor + ", usersPerThread="
            + usersPerThread + ", channelsPerUser=" + channelsPerUser + ", resourceRate=" + resourceRate + ", scheme='"
            + scheme + '\'' + ", host='" + host + '\'' + ", port=" + port + ", maxRequestsQueued=" + maxRequestsQueued
            + ", type=" + type + ", resourceNumber=" + resourceNumber + ", instanceNumber=" + instanceNumber
            + ", transport='" + transport + '\'' + '}';
    }
}
