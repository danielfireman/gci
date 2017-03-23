package com.danielfireman.gci;

import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Garbage Collector Control Interceptor (GCI).
 * This class is thread-safe. It is meant to be used as singleton in highly
 * concurrent environment.
 *
 * @author danielfireman
 */
public class GarbageCollectorControlInterceptor {
    private static final float SHEDDING_THRESHOLD = 0.50f;
    private static final long SAMPLE_RATE = 3L;
    private static final Duration WAIT_FOR_TRAILERS_SLEEP_MILLIS = Duration.ofMillis(10);
    private static final ShedResponse PROCESS_REQUEST = new ShedResponse(false, null);
    AtomicBoolean doingGC = new AtomicBoolean(false);  // Package private to make testing easier.
    private AtomicLong incoming = new AtomicLong();
    private AtomicLong finished = new AtomicLong();
    private HeapMonitor monitor;
    private GarbageCollector collector;
    private Executor executor;
    private UnavailabilityDuration unavailabilityDuration;

    /**
     * Creates a new instance of {@code GarbageCollectorControlInterceptor}
     *
     * @param monitor                {@code HeapMonitor} used to monitoring JVM heap pools.
     * @param executor               thread pool used to trigger/control garbage collection.
     * @param collector              Garbage collector to be triggered by the control interceptor.
     * @param unavailabilityDuration Keeps track and estimates unavailability periods.
     */
    public GarbageCollectorControlInterceptor(
            HeapMonitor monitor,
            GarbageCollector collector,
            Executor executor,
            UnavailabilityDuration unavailabilityDuration) {
        this.monitor = monitor;
        this.collector = collector;
        this.executor = executor;
        this.unavailabilityDuration = unavailabilityDuration;
    }

    /**
     * Creates a new instance of {@code GarbageCollectorControlInterceptor} using defaults.
     *
     * @see HeapMonitor
     * @see System#gc()
     * @see Executors#newSingleThreadExecutor()
     * @see UnavailabilityDuration
     */
    public GarbageCollectorControlInterceptor() {
        this(new HeapMonitor(), () -> System.gc(), Executors.newSingleThreadExecutor(), new UnavailabilityDuration());
    }

    private static ShedResponse shedRequest(Duration unavailabilityDuration) {
        return new ShedResponse(true, unavailabilityDuration);
    }

    public ShedResponse before() {
        incoming.incrementAndGet();
        if (doingGC.get()) {
            return shedRequest(unavailabilityDuration.estimate());
        }
        if (incoming.get() % SAMPLE_RATE == 0) {
            HeapMonitor.Usage usage = monitor.getUsage();
            if (usage.tenured > SHEDDING_THRESHOLD || usage.young > SHEDDING_THRESHOLD) {
                synchronized (this) {
                    if (doingGC.get()) {
                        return shedRequest(unavailabilityDuration.estimate());
                    }
                    doingGC.set(true);
                }
                executor.execute(() -> {
                    // Deliberately counting the time waiting for trailer requests
                    // as unavailability time.
                    unavailabilityDuration.begin();
                    // Loop waiting for the queue to get empty.
                    while (finished.get() < incoming.get()) {
                        try {
                            Thread.sleep(WAIT_FOR_TRAILERS_SLEEP_MILLIS.toMillis());
                        } catch (InterruptedException ie) {
                            throw new RuntimeException(ie);
                        }
                    }
                    // Finally, collect the garbage.
                    collector.collect();
                    unavailabilityDuration.end();
                    doingGC.set(false);
                });
                return shedRequest(unavailabilityDuration.estimate());
            }
        }
        return PROCESS_REQUEST;
    }

    public void after(ShedResponse response) {
        finished.incrementAndGet();
    }
}
