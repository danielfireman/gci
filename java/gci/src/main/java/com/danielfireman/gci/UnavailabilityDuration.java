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
    private static final int HISTORY_SIZE = 5;
    private Clock clock;
    private long[] past = new long[HISTORY_SIZE];
    private long gcCount;
    private long gcStartTime;
    private double requestDurationOldMean, requestDurationNewMean, requestDurationOldVar, requestDurationNewVar;
    private long requestCount;
    private long gcEstimation, requestDurationMean, requestDurationStdDev;


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
     * @param queueSize number of requests in flight at the moment of estimation.
     * @return next unavailability duration estimate.
     */
    synchronized Duration estimate(long queueSize) {
        // Using 68–95–99.7 rule to have a good coverage on the request size.
        // https://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
        long requestDurationIncrement = 0;
        if (queueSize > 0) {
            requestDurationIncrement = queueSize * requestDurationMean + 3 * requestDurationStdDev;
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
        past[(int) (gcCount % HISTORY_SIZE)] = durationMillis;
        long max = past[0];
        for (int i = 1; i < past.length; i++) if (past[i] > max) max = past[i];
        gcEstimation = max;
        requestDurationMean = (requestCount > 0) ? (long) requestDurationNewMean : 0L;
        requestDurationStdDev = (long) Math.sqrt((requestCount > 1) ? requestDurationNewMean/(double)(requestCount - 1) : 0.0);
        requestCount = 0;
        gcCount++;
    }

    /**
     * Flags that a request has been finished.
     *
     * @param startTimeMillis Request start time in milliseconds since epoch.
     */
    synchronized void requestFinished(long startTimeMillis) {
        // Fast and more accurate (compared to the naive approach) way of computing variance. Proposed by
        // B. P. Welford and presented in Donald Knuth’s Art of Computer Programming, Vol 2, page 232, 3rd
        // edition.
        // Mean and standard deviation calculation based on: https://www.johndcook.com/blog/standard_deviation/
        requestCount++;
        double value = clock.millis() - startTimeMillis;
        if (requestCount == 1) {
            requestDurationOldMean = requestDurationNewMean = value;
            requestDurationOldVar = 0.0;
        } else {
            requestDurationNewMean = requestDurationOldMean + (value - requestDurationOldMean) / (double) requestCount;
            requestDurationNewVar = requestDurationNewVar + (value - requestDurationOldMean) * (value - requestDurationNewMean);
            requestDurationOldMean = requestDurationNewMean;
            requestDurationOldVar = requestDurationNewVar;
        }
    }
}
