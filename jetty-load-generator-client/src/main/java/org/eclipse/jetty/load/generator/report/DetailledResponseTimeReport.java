package org.eclipse.jetty.load.generator.report;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Contains all response time values!
 */
public class DetailledResponseTimeReport
{

    private List<Entry> entries = new CopyOnWriteArrayList<>();

    public DetailledResponseTimeReport()
    {
        // no op
    }

    public List<Entry> getEntries()
    {
        return entries;
    }

    public void setEntries( List<Entry> entries )
    {
        this.entries = entries;
    }

    public void addEntry( Entry entry )
    {
        this.entries.add( entry );
    }

    public static class Entry
    {
        // in nano s
        private long timeStamp;

        private String path;

        private int httpStatus;

        private long time;

        public Entry( long timeStamp, String path, int httpStatus, long time )
        {
            this.timeStamp = timeStamp;
            this.path = path;
            this.httpStatus = httpStatus;
            this.time = time;
        }

        public String getPath()
        {
            return path;
        }

        public int getHttpStatus()
        {
            return httpStatus;
        }

        public long getTime()
        {
            return time;
        }

        public void setPath( String path )
        {
            this.path = path;
        }

        public void setHttpStatus( int httpStatus )
        {
            this.httpStatus = httpStatus;
        }

        public void setTime( long time )
        {
            this.time = time;
        }

        public long getTimeStamp()
        {
            return timeStamp;
        }

        public void setTimeStamp( long timeStamp )
        {
            this.timeStamp = timeStamp;
        }
    }

}
