package com.danielfireman.gci;

import java.time.Clock;
import java.time.Duration;

/**
 * Estimates the unavailability duration of a given service based
 * on the last unavailability periods.
 *
 * @author danielfireman
 */
class UnavailabilityDuration {
    private Clock clock;
    private double count, startTime, oldMean, newMean, oldVar, newVar;

    /**
     * Creates a new {@link UnavailabilityDuration} instance.
     *
     * @param clock System clock.
     */
    UnavailabilityDuration(Clock clock) {
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
        double delta = clock.millis() - startTime;
        double stddev = Math.sqrt((count > 1) ? newVar / (count - 1) : 0.0);
        double mean = (count > 0) ? newMean : 0.0;
        return Duration.ofMillis((long) Math.max(0, mean + stddev - delta));
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
        // Fast and more accurate (compared to the naive approach) way of computing variance. Proposed by
        // B. P. Welford and presented in Donald Knuth’s Art of Computer Programming, Vol 2, page 232, 3rd
        // edition.
        // Another explanation: https://www.embeddedrelated.com/showarticle/785.php
        count++;
        double value = clock.millis() - startTime;
        if (count == 1) {
            oldMean = newMean = value;
            oldVar = 0.0;
        } else {
            newMean = oldMean + (value - oldMean) / count;
            newVar = oldVar + (value - oldMean) * (value - newMean);
            oldMean = newMean;
            oldVar = newVar;
        }
    }
}
