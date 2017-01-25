"""Module that exports the GarbageCollectorControlInterceptor.

The current implementation of this module has few assumptions:
* Linux: decided so the first version does not add new dependencies (i.e. github.com/giampaolo/psutil).
* CPython: decided so the first version does not needed to deal with mutexes and so on.

These assumptions simplify a lot the request processing flow. As there is no concurrent processing within
a single python runtime, no requests need to be shed. The synchronous flow could be:

1) Pre-request: based on a sampling window, check if it is time to GC
2) Process request
3) After-response: perform gc if needed
"""

import collections
import gc
import math
import os
import resource
import time
from datetime import timedelta

_PAGE_SIZE = resource.getpagesize()
_TOTAL_MEM = _PAGE_SIZE * os.sysconf('SC_PHYS_PAGES')
_STATM_FILENAME = '/proc/%d/statm' % os.getpid()
_SHEDDING_THRESHOLD = 0.2
_DEFAULT_UNAVAILABILITY_DURATION_SECS = 1
_MAX_SAMPLE_WINDOW_SIZE = float(400)
_MIN_SAMPLE_WINDOW_SIZE = float(40)
_LAST_UNAVAILABLE_HISTORY_SIZE = 5


def _std(s):
    ss = len(s)
    if ss < 2:
        return float(0)
    avg = float(sum(s)) / ss
    var = sum(map(lambda x: (x - avg) ** 2, s)) / (ss - 1)
    return math.sqrt(var)


class HeapMonitor:
    def __init__(self, filename=None):
        self._rss_after_last_gc = 0.0

    def get_usage(self, filename=None):
        """Heap usage percentage.

         This value is calculated as the increase of Resident Set Size (RSS) since last garbage
         collection divided by the total amount of memory available to the runtime."""
        # From: https://utcc.utoronto.ca/~cks/space/blog/linux/LinuxMemoryStats
        # "Your process's RSS increases every time it looks at a new piece of memory (and
        # thereby establishes a  page table entry for it). It decreases as the kernel removes
        # PTEs that haven't been used sufficiently recently; how fast this happens depends on how
        # much memory pressure the overall system is under. The more memory pressure, the more the kernel
        # tries to steal pages from processes and decrease their RSS."
        # So, we are keeping track of the RSS after last GC and used it as a baseline (in
        # which case is more likely to have a decrease). Also updating to zero things up in case of
        # decrease.
        current_rss = self._get_meminfo(filename)
        if current_rss < self._rss_after_last_gc:
            self._rss_after_last_gc = current_rss
        return (current_rss - self._rss_after_last_gc) / _TOTAL_MEM

    def update_meminfo(self, filename=None):
        self._rss_after_last_gc = self._get_meminfo(filename)

    def _get_meminfo(self, filename=None):
        # For Linux we can use information from the proc filesystem. We use
        # '/proc/statm' as it easier to parse than '/proc/status' file.
        filename = filename or _STATM_FILENAME
        with open(filename, 'r') as fp:
            statm = fp.read().split()
            return float(statm[1]) * _PAGE_SIZE


class GarbageCollectorInterceptor:
    """Garbage Collector Control Interceptor (GCI)."""

    def __init__(self):
        gc.disable()
        gc.set_debug(gc.DEBUG_STATS)
        self._monitor = HeapMonitor()
        self._sample_rate = _MIN_SAMPLE_WINDOW_SIZE
        self._processed = 0
        self._unavailability_duration = collections.deque([_DEFAULT_UNAVAILABILITY_DURATION_SECS],
                                                          _LAST_UNAVAILABLE_HISTORY_SIZE)
        print "GCI activated and automatic garbage collection disabled. "

    def before_request(self, filename=None):
        """Method that should be called before request processing.

        Returns a datetime.timedelta if representing the unavailability delay of the service after attending to this
        request. Or None if the execution will proceed without interruption."""
        if self._processed > 0 and self._processed % self._sample_rate == 0:
            if self._monitor.get_usage() > _SHEDDING_THRESHOLD:
                std = _std(self._unavailability_duration)
                return timedelta(seconds=self._unavailability_duration[0] + std)

        self._processed += 1
        return None

    def after_response(self, should_shed=False):
        if not should_shed:
            return
        start_time = time.time()
        gc.collect()
        self._unavailability_duration.appendleft(time.time() - start_time)

        # Calculating next sample rate.
        # The main idea is to get 20% of the requests that arrived since last GC and bound
        # this number to [MIN_SAMPLE_WINDOW_SIZE, MAX_SAMPLE_WINDOW_SIZE]. We don't want to take
        # too long that a load peak could happen and we don't want it to be too often that
        # would lead to a performance damage.
        self._sample_rate = min(_MAX_SAMPLE_WINDOW_SIZE, max(_MIN_SAMPLE_WINDOW_SIZE, self._processed / 5))
        self._processed = 0
        self._monitor.update_meminfo()
