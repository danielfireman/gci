package com.danielfireman.gci;

import java.time.Duration;

/**
 * Holds the response of processing a single request from {@code GarbageCollectorControlInterceptor}.
 * @author danielfireman
 */
public class ShedResponse {
    /**
     * The estimated duration of server unavailability due to GC activity.
     * This is used to the Retry-After response header, as per
     * <a href="https://tools.ietf.org/html/rfc7231#section-6.6.4">RFC 7231</a>.
     */
    public Duration unavailabilityDuration;

    /**
     * Whether the request should be shed.
     */
    public boolean shouldShed;

    public ShedResponse(boolean shouldShed, Duration unavailabilityDuration) {
        this.unavailabilityDuration = unavailabilityDuration;
        this.shouldShed = shouldShed;
    }
}
