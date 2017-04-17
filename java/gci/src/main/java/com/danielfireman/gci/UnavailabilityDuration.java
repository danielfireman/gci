package com.danielfireman.gci;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

/**
 * Estimates the unavailability duration of a given service based
 * on the last unavailability periods.
 *
 * @author danielfireman
 */
public class UnavailabilityDuration {
    private static final int HISTORY_SIZE = 5;
    private Clock clock;
    private long[] past = new long[HISTORY_SIZE];
    private long[] pastRequestDurations = new long[HISTORY_SIZE];
    private int gcCount;
    private long gcStartTime;
    private double requestDurationOldMean, requestDurationNewMean, requestDurationNewVar;
    private long requestCount;
    private long gcEstimation, requestDurationEstimation;


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
        this(Clock.systemUTC());
    }

    /**
     * Estimates the amount of taken the service will be unavailable given the
     * last periods of unavailability.
     *
     * @param queueSize number of requests in flight at the moment of estimation.
     * @return next unavailability duration estimate.
     */
    synchronized Duration estimate(long queueSize) {
        long requestDurationIncrement = 0;
        if (queueSize > 0) {
            requestDurationIncrement = queueSize * requestDurationEstimation;
        }
        return Duration.ofMillis(gcEstimation + requestDurationIncrement);
    }

    /**
     * Flags that an unavailability period has begun.
     */
    synchronized void begin() {
        gcStartTime = clock.millis();
    }

    /**
     * Flags that the last unavailability period has ended.
     */
    synchronized void end() {
        long durationMillis = clock.millis() - gcStartTime;
        past[gcCount] = durationMillis;
        long max = past[0];
        for (int i = 1; i < past.length; i++) if (past[i] > max) max = past[i];
        gcEstimation = max;

        // Using 68–95–99.7 rule to have a good coverage on the request size.
        // https://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
        long mean = (requestCount > 0) ? (long) requestDurationNewMean : 0L;
        long stdDev = (long) Math.sqrt((requestCount > 1) ? requestDurationNewVar / (double) (requestCount - 1) : 0.0);
        pastRequestDurations[gcCount] = mean + 3 * stdDev;

        max = pastRequestDurations[0];
        for (int i = 1; i < pastRequestDurations.length; i++)
            if (pastRequestDurations[i] > max) max = pastRequestDurations[i];
        requestDurationEstimation = max;

        requestDurationOldMean = requestDurationNewMean = requestDurationNewVar = 0.0;
        requestCount = 0;
        gcCount = ++gcCount % HISTORY_SIZE;
    }

    /**
     * Flags that a request has been finished.
     *
     * @param duration Request processing duration in milliseconds..
     */
    synchronized void requestFinished(long duration) {
        // Fast and more accurate (compared to the naive approach) way of computing variance. Proposed by
        // B. P. Welford and presented in Donald Knuth’s Art of Computer Programming, Vol 2, page 232, 3rd
        // edition.
        // Mean and standard deviation calculation based on: https://www.johndcook.com/blog/standard_deviation/
        requestCount++;
        if (requestCount == 1) {
            requestDurationOldMean = requestDurationNewMean = duration;
        } else {
            requestDurationNewMean = requestDurationOldMean + (duration - requestDurationOldMean) / (double) requestCount;
            requestDurationNewVar = requestDurationNewVar + (duration - requestDurationOldMean) * (duration - requestDurationNewMean);
            requestDurationOldMean = requestDurationNewMean;
        }
    }
}
