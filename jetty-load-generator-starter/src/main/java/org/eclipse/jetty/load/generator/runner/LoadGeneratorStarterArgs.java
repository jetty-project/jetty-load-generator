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

package org.eclipse.jetty.load.generator.runner;

import com.beust.jcommander.Parameter;
import org.eclipse.jetty.load.generator.LoadGenerator;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public class LoadGeneratorStarterArgs
{

    @Parameter( names = { "--profile-xml-path", "-pxp" }, description = "Path to profile xml file" )
    private String profileXmlPath;

    @Parameter( names = { "--profile-json-path", "-pjp" }, description = "Path to profile json file" )
    private String profileJsonPath;

    @Parameter( names = { "--host", "-h" }, description = "Target host" )
    private String host = "localhost";

    @Parameter( names = { "--port", "-p" }, description = "Target port" )
    private int port = 8080;

    @Parameter( names = { "--users", "-u" }, description = "Simulated users number" )
    private int users = 1;

    @Parameter( names = { "--transaction-rate", "-tr" }, description = "Transaction rate / second" )
    private int transactionRate = 1;

    @Parameter( names = { "--transport", "-t" }, description = "Transport (http, https, h2, h2c, fcgi)" )
    private String transport = "http";

    @Parameter( names = { "--selectors", "-s" }, description = "HttpClientTransport selectors" )
    private int selectors = 1;

    @Parameter( names = { "--running-time", "-rt" }, description = "Running Time" )
    private long runningTime = 1;

    @Parameter( names = { "--running-time-unit", "-rtu" }, description = "Running Time Unit (h/m/s/ms)" )
    private String runningTimeUnit = "s";

    @Parameter( names = { "--help"}, description = "Display help" )
    private boolean help;

    public LoadGeneratorStarterArgs()
    {
        // no op
    }

    public String getProfileXmlPath()
    {
        return profileXmlPath;
    }

    public void setProfileXmlPath( String profileXmlPath )
    {
        this.profileXmlPath = profileXmlPath;
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

    public int getUsers()
    {
        return users;
    }

    public void setUsers( int users )
    {
        this.users = users;
    }

    public int getTransactionRate()
    {
        return transactionRate;
    }

    public void setTransactionRate( int transactionRate )
    {
        this.transactionRate = transactionRate;
    }

    public LoadGenerator.Transport getTransport()
    {
        switch ( this.transport )
        {
            case "http":
                return LoadGenerator.Transport.HTTP;
            case "https":
                return LoadGenerator.Transport.HTTPS;
            case "h2":
                return LoadGenerator.Transport.H2;
            case "h2c":
                return LoadGenerator.Transport.H2C;
            case "fcgi":
                return LoadGenerator.Transport.FCGI;
            default:
                throw new IllegalArgumentException( transport + " is not recognized" );
        }
    }

    public void setTransport( String transport )
    {
        this.transport = transport != null ? transport.toLowerCase() : "";
    }

    public int getSelectors()
    {
        return selectors;
    }

    public void setSelectors( int selectors )
    {
        this.selectors = selectors;
    }

    public boolean isHelp()
    {
        return help;
    }

    public void setHelp( boolean help )
    {
        this.help = help;
    }

    public long getRunningTime()
    {
        return runningTime;
    }

    public void setRunningTime( long runningTime )
    {
        this.runningTime = runningTime;
    }

    public TimeUnit getRunningTimeUnit()
    {
        switch ( this.runningTimeUnit ) {
            case "h":
                return TimeUnit.HOURS;
            case "m":
                return TimeUnit.MINUTES;
            case "s":
                return TimeUnit.SECONDS;
            case "ms":
                return TimeUnit.MILLISECONDS;
            default:
                throw new IllegalArgumentException( runningTimeUnit + " is not recognized" );
        }
    }

    public void setRunningTimeUnit( String runningTimeUnit )
    {
        this.runningTimeUnit = runningTimeUnit;
    }
}
