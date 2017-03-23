package com.danielfireman.gci;

import java.time.Clock;
import java.time.Duration;

/**
 * Estimates the unavailability duration of a given service based
 * on the last unavailability periods.
 *
 * @author danielfireman
 */
public class UnavailabilityDuration {
    private static final int HISTORY_SIZE = 20;
    private static final float SAFETY_FACTOR = 2f;
    private Clock clock;
    private long startTime;
    private Duration duration = Duration.ofMillis(100);
    private long[] past = new long[HISTORY_SIZE];
    private long count;

    /**
     * Creates a new {@link UnavailabilityDuration} instance.
     *
     * @param clock System clock.
     */
    public UnavailabilityDuration(Clock clock) {
        this.clock = clock;
    }

    /**
     * Creates a new {@link UnavailabilityDuration} instance with default parameters.
     *
     * @see Clock#systemDefaultZone()
     */
    UnavailabilityDuration() {
        this(Clock.systemDefaultZone());
    }

    /**
     * Estimates the amount of taken the service will be unavailable given the
     * last periods of unavailability.
     *
     * @return next unavailability duration estimate.
     */
    synchronized Duration estimate() {
        return duration;
    }

    /**
     * Flags that an unavailability period has begun.
     */
    synchronized void begin() {
        startTime = clock.millis();
    }

    /**
     * Flags that the last unavailability period has ended.
     */
    synchronized void end() {
        long durationMillis = clock.millis() - startTime;
        past[(int)(count%HISTORY_SIZE)] = durationMillis;
        long max = past[0];
        for (int i=1; i < past.length; i++) if (past[i] > max) max = past[i];
        duration = Duration.ofMillis((long) (max * SAFETY_FACTOR));
        count++;
    }
}

