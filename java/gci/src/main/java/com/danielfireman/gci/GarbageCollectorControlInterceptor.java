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
    private static final float SHEDDING_THRESHOLD = 0.5f;
    private static final long MAX_SAMPLE_WINDOW_SIZE = 400L;
    private static final long MIN_SAMPLE_WINDOW_SIZE = 40L;
    private static final long WAIT_FOR_TRAILERS_SLEEP_MILLIS = 50;
    private static final ShedResponse PROCESS_REQUEST = new ShedResponse(false, null);
    AtomicBoolean doingGC = new AtomicBoolean(false);  // Package private to make testing easier.
    private AtomicLong incoming = new AtomicLong();
    private AtomicLong finished = new AtomicLong();
    private AtomicLong sampleRate = new AtomicLong(10);
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
        if (doingGC.get()) {
            return shedRequest(unavailabilityDuration.estimate());
        }
        if (incoming.get() % sampleRate.get() == 0) {
            synchronized (this) {
                if (doingGC.get()) {
                    return shedRequest(unavailabilityDuration.estimate());
                }
                HeapMonitor.Usage usage = monitor.getUsage();
                if (usage.tenured > SHEDDING_THRESHOLD || usage.young > SHEDDING_THRESHOLD) {
                    doingGC.set(true);
                    unavailabilityDuration.begin();

                    // Making sure the request is not interrupted by GC.
                    incoming.incrementAndGet();

                    // This should return immediately. We don't want to block inside the synchronized block.
                    executor.execute(() -> {
                        // Calculating next sample rate.
                        // The main idea is to get 20% of the requests that arrived since last GC and bound
                        // this number to [MIN_SAMPLE_WINDOW_SIZE, MAX_SAMPLE_WINDOW_SIZE]. We don't want to take
                        // too long that a load peak could happen and we don't want it to be too often that
                        // would lead to a performance damage.
                        sampleRate.set(Math.min(
                                MAX_SAMPLE_WINDOW_SIZE,
                                Math.max(MIN_SAMPLE_WINDOW_SIZE, (long) ((double) incoming.get() / 5d))));

                        // Loop waiting for the queue to get empty.
                        while (finished.get() < incoming.get()) {
                            try {
                                Thread.sleep(WAIT_FOR_TRAILERS_SLEEP_MILLIS);
                            } catch (InterruptedException ie) {
                                throw new RuntimeException(ie);
                            }
                        }

                        // Finally, collect the garbage.
                        collector.collect();

                        // Wrap things up and update the unavailability duration histogram.
                        unavailabilityDuration.end();
                        incoming.set(0);
                        finished.set(0);
                        doingGC.set(false);
                    });
                    return PROCESS_REQUEST;
                }
            }
        }
        incoming.incrementAndGet();
        return PROCESS_REQUEST;
    }

    public void after(ShedResponse response) {
        if (!response.shouldShed) {
            finished.incrementAndGet();
        }
    }
}
