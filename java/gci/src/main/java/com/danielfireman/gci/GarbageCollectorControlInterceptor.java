package com.danielfireman.gci;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Garbage Collector Control Interceptor (GCI).
 * This class is thread-safe. It is meant to be used as singleton in highly
 * concurrent environment.
 *
 * @author danielfireman
 */
public class GarbageCollectorControlInterceptor {
    private static final float SHEDDING_THRESHOLD = 0.90f;
    private static final Duration WAIT_FOR_TRAILERS_SLEEP_MILLIS = Duration.ofMillis(10);
    private final Clock clock;
    AtomicBoolean doingGC = new AtomicBoolean(false);  // Package private to make testing easier.
    private AtomicLong incoming = new AtomicLong();
    private AtomicLong finished = new AtomicLong();
    private HeapMonitor monitor;
    private GarbageCollector collector;
    private Executor executor;
    private UnavailabilityDuration unavailabilityDuration;
    private int sampleCount = 0;
    private long lastFinished = 0;
    private AtomicInteger sampleRate = new AtomicInteger(DEFAULT_SAMPLE_RATE);
    private int[] pastSampleRates = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE};
    // Default sample rate should be fairly small, so big requests get checked up quickly.
    private static final int DEFAULT_SAMPLE_RATE = 10;
    // Max sample rate can not be very big because of peaks.
    // The algorithm is fairly conservative, but we never know.
    private static final int MAX_SAMPLE_RATE = 50;

    /**
     * Creates a new instance of {@code GarbageCollectorControlInterceptor}
     *
     * @param monitor                {@code HeapMonitor} used to monitoring JVM heap pools.
     * @param executor               thread pool used to trigger/control garbage collection.
     * @param collector              Garbage collector to be triggered by the control interceptor.
     * @param unavailabilityDuration Keeps track and estimates unavailability periods.
     * @param clock                  System clock used to find the time.
     */
    public GarbageCollectorControlInterceptor(
            HeapMonitor monitor,
            GarbageCollector collector,
            Executor executor,
            UnavailabilityDuration unavailabilityDuration,
            Clock clock) {
        this.monitor = monitor;
        this.collector = collector;
        this.executor = executor;
        this.unavailabilityDuration = unavailabilityDuration;
        this.clock = clock;
    }

    /**
     * Creates a new instance of {@code GarbageCollectorControlInterceptor} using defaults.
     *
     * @see HeapMonitor
     * @see System#gc()
     * @see Executors#newSingleThreadExecutor()
     * @see UnavailabilityDuration
     * @see Clock#systemDefaultZone()
     */
    public GarbageCollectorControlInterceptor() {
        this(new HeapMonitor(),
                () -> System.gc(),
                Executors.newSingleThreadExecutor(),
                new UnavailabilityDuration(),
                Clock.systemUTC());
    }

    private ShedResponse shedRequest(Duration unavailabilityDuration) {
        return new ShedResponse(true, unavailabilityDuration, clock.millis());
    }

    public ShedResponse before() {
        incoming.incrementAndGet();
        if (doingGC.get()) {
            return shedRequest(unavailabilityDuration.estimate(finished.get() - incoming.get()));
        }
        if (incoming.get() % sampleRate.get() == 0) {
            HeapMonitor.Usage usage = monitor.getUsage();
            if (usage.tenured > SHEDDING_THRESHOLD || usage.young > SHEDDING_THRESHOLD) {
		// Note: Please, be kind and keep the following synchronized block small.
                synchronized (this) {
                    if (doingGC.get()) {
                        return shedRequest(unavailabilityDuration.estimate(finished.get() - incoming.get()));
                    }
                    doingGC.set(true);
                    lastFinished = (int)finished.get();
                }
                executor.execute(() -> {
		    // Being conservative here. Picking up the minimum value among last sample rates.
                    if (sampleCount > 0) {
			    int lastWindow = (int)(finished.get() - lastFinished);
			    pastSampleRates[sampleCount % pastSampleRates.length] = lastWindow;
			    long min = pastSampleRates[0];
			    for (int i = 1; i < pastSampleRates.length; i++) {
				if (pastSampleRates[i] < min) min = pastSampleRates[i];
			    }
		    	    sampleCount++;
			    sampleRate.set((int)Math.min(min, MAX_SAMPLE_RATE));  
		    }

                    // Loop waiting for the queue to get empty.
                    while (finished.get() < incoming.get()) {
                        try {
                            Thread.sleep(WAIT_FOR_TRAILERS_SLEEP_MILLIS.toMillis());
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);
                        }
                    }
                    // Finally, collect the garbage.
                    unavailabilityDuration.begin();
                    collector.collect();
                    unavailabilityDuration.end();
                    doingGC.set(false);
                });
                return shedRequest(unavailabilityDuration.estimate(finished.get() - incoming.get()));
            }
        }
        return new ShedResponse(false, null, clock.millis());
    }

    public void after(ShedResponse response) {
        finished.incrementAndGet();
        if (!response.shouldShed) {
            unavailabilityDuration.requestFinished(clock.millis()-response.startTimeMillis);
        }
    }
}
