package com.danielfireman.gci;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;

/**
 * Monitors heap usage.
 *
 * @author danielfireman
 */
class HeapMonitor {
    private MemoryPoolMXBean youngPool;
    private MemoryPoolMXBean tenuredPool;

    HeapMonitor() {
        for (final MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            // TODO(danielfireman): Generalize this to other JVM versions.
            if (pool.getName().contains("Eden")) {
                youngPool = pool;
                continue;
            }
            if (pool.getName().contains("Old")) {
                tenuredPool = pool;
                continue;
            }
        }
    }

    /**
     * @return Heap used.
     * @see Usage
     */
    Usage getUsage() {
        MemoryUsage youngUsage = this.youngPool.getUsage();
        MemoryUsage tenuredUsage = this.tenuredPool.getUsage();

        Usage usage = new Usage();
        usage.young = (double) youngUsage.getUsed() / (double) youngUsage.getCommitted();
        usage.tenured = (double) tenuredUsage.getUsed() / (double) tenuredUsage.getCommitted();
        return usage;
    }

    /**
     * Main response from memory monitor, it represents the usage of young and tenured heap pools.
     * All usage values are percentage related to the amount of memory committed.
     */
    class Usage {
        double young;
        double tenured;
    }
}
