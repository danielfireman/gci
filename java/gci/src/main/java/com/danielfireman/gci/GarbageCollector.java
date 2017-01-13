package com.danielfireman.gci;

/**
 * Abstract garbage the runtime's garbage collector. Meant to be used by tests.
 *
 * @author danielfireman
 */
@FunctionalInterface
interface GarbageCollector {
    void collect();
}
