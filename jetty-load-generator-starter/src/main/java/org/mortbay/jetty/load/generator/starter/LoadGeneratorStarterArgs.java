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

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.beust.jcommander.DynamicParameter;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.internal.Maps;

/**
 *
 */
public class LoadGeneratorStarterArgs
{

    @Parameter( names = { "--profile-xml-path", "-pxp" }, description = "Path to profile xml file" )
    private String profileXmlPath;

    @Parameter( names = { "--profile-json-path", "-pjp" }, description = "Path to profile json file" )
    private String profileJsonPath;

    @Parameter( names = { "--profile-groovy-path", "-pgp" }, description = "Path to profile groovy file" )
    private String profileGroovyPath;

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

    @Parameter( names = { "--running-iteration", "-ri" }, description = "Iteration number to run" )
    private int runIteration;

    @Parameter( names = { "--report-host", "-rh" }, description = "Report host" )
    private String reportHost = "localhost";

    @Parameter( names = { "--scheme" }, description = "Scheme (http/https)" )
    private String scheme = "http";

    @Parameter( names = { "--report-port", "-rp" }, description = "Report port" )
    private int reportPort;

    @Parameter( names = { "--no-interrupt", "-notint"}, description = "Not Interrupt Loadgenerator after run" )
    private boolean notInterrupt = false;

    @Parameter( names = { "--stats-to-file", "-stf" }, description = "Write stats to this file" )
    private String statsFile;

    @DynamicParameter(names = "-D", description = "Dynamic parameters go here")
    public Map<String, String> params = Maps.newHashMap();

    @Parameter( names = { "--help"}, description = "Display help" )
    private boolean help;

    @Parameter( names = { "--display-stats-end", "-dse"}, description = "Display stats at the end" )
    private boolean displayStatsAtEnd;

    @Parameter( names = { "--collect-server-stats", "-css"}, description = "Collect server stats on remote StatisticsServlet" )
    private boolean collectServerStats;

    @Parameter( names = { "--warmup-number", "-wn" }, description = "Warm up number to run" )
    private int warmupNumber;


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

    public Transport getTransport()
    {
        switch ( this.transport )
        {
            case "http":
                return Transport.HTTP;
            case "https":
                return Transport.HTTPS;
            case "h2":
                return Transport.H2;
            case "h2c":
                return Transport.H2C;
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

    public int getRunIteration()
    {
        return runIteration;
    }

    public void setRunIteration( int runIteration )
    {
        this.runIteration = runIteration;
    }

    public void setRunningTimeUnit( String runningTimeUnit )
    {
        this.runningTimeUnit = runningTimeUnit;
    }

    public String getProfileJsonPath()
    {
        return profileJsonPath;
    }

    public void setProfileJsonPath( String profileJsonPath )
    {
        this.profileJsonPath = profileJsonPath;
    }

    public String getReportHost()
    {
        return reportHost;
    }

    public void setReportHost( String reportHost )
    {
        this.reportHost = reportHost;
    }

    public int getReportPort()
    {
        return reportPort;
    }

    public void setReportPort( int reportPort )
    {
        this.reportPort = reportPort;
    }

    public boolean isNotInterrupt()
    {
        return notInterrupt;
    }

    public void setNotInterrupt( boolean notInterrupt )
    {
        this.notInterrupt = notInterrupt;
    }

    public String getStatsFile()
    {
        return statsFile;
    }

    public void setStatsFile( String statsFile )
    {
        this.statsFile = statsFile;
    }

    public Map<String, String> getParams()
    {
        return params;
    }

    public String getProfileGroovyPath()
    {
        return profileGroovyPath;
    }

    public void setProfileGroovyPath( String profileGroovyPath )
    {
        this.profileGroovyPath = profileGroovyPath;
    }

    public void setParams( Map<String, String> params )
    {
        this.params = params;
    }

    public boolean isDisplayStatsAtEnd()
    {
        return displayStatsAtEnd;
    }

    public void setDisplayStatsAtEnd( boolean displayStatsAtEnd )
    {
        this.displayStatsAtEnd = displayStatsAtEnd;
    }

    public boolean isCollectServerStats()
    {
        return collectServerStats;
    }

    public void setCollectServerStats( boolean collectServerStats )
    {
        this.collectServerStats = collectServerStats;
    }

    public int getWarmupNumber()
    {
        return warmupNumber;
    }

    public void setWarmupNumber( int warmupNumber )
    {
        this.warmupNumber = warmupNumber;
    }

    public String getScheme()
    {
        return scheme;
    }

    public void setScheme( String scheme )
    {
        this.scheme = scheme;
    }

    @Override
    public String toString()
    {
        return "LoadGeneratorStarterArgs{" + "profileXmlPath='" + profileXmlPath + '\'' + ", profileJsonPath='"
            + profileJsonPath + '\'' + ", profileGroovyPath='" + profileGroovyPath + '\'' + ", host='" + host + '\''
            + ", port=" + port + ", users=" + users + ", transactionRate=" + transactionRate + ", transport='"
            + transport + '\'' + ", selectors=" + selectors + ", runningTime=" + runningTime + ", runningTimeUnit='"
            + runningTimeUnit + '\'' + ", runIteration=" + runIteration + ", reportHost='" + reportHost + '\''
            + ", scheme='" + scheme + '\'' + ", reportPort=" + reportPort + ", notInterrupt=" + notInterrupt
            + ", statsFile='" + statsFile + '\'' + ", params=" + params + ", help=" + help + ", displayStatsAtEnd="
            + displayStatsAtEnd + ", collectServerStats=" + collectServerStats + ", warmupNumber=" + warmupNumber + '}';
    }

    public enum Transport {
        HTTP,
        HTTPS,
        H2C,
        H2
    }
}
