package org.eclipse.jetty.load.generator.responsetime;

import java.util.concurrent.TimeUnit;

/**
 *
 */
public interface RecorderConstants
{

    long LOWEST_DISCERNIBLE_VALUE = TimeUnit.MICROSECONDS.toNanos( 1 );

    long HIGHEST_TRACKABLE_VALUE = TimeUnit.MINUTES.toNanos( 1 );

    int NUMBER_OF_SIHNIFICANT_VALUE_DIGITS = 3;

}

