package com.danielfireman.gci;

import com.codahale.metrics.Histogram;
import com.codahale.metrics.SlidingWindowReservoir;
import com.codahale.metrics.Snapshot;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Estimates the unavailability duration of a given service based
 * on the last unavailability periods.
 *
 * @author danielfireman
 */
class UnavailabilityDuration {

    private Clock clock;
    private Histogram unavailabilityHistogram = new Histogram(new SlidingWindowReservoir(10));
    private AtomicLong unavailabilityStartTime = new AtomicLong(0);

    /**
     * Creates a new {@link UnavailabilityDuration} instance.
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
    Duration estimate() {
        double delta = clock.millis() - unavailabilityStartTime.get();
        Snapshot s = unavailabilityHistogram.getSnapshot();
        return Duration.ofMillis((long) Math.max(0, s.getMedian() + s.getStdDev() - delta));
    }

    /**
     * Flags that an unavailability period has begun.
     */
    synchronized void begin() {
        unavailabilityStartTime.set(clock.millis());
    }

    /**
     * Flags that the last unavailability period has ended.
     */
    synchronized void end() {
        unavailabilityHistogram.update(clock.millis() - unavailabilityStartTime.get());
    }
}
