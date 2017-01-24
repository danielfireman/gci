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

import gc
import os
import resource
import time
from datetime import timedelta

_PAGE_SIZE = resource.getpagesize()
_STATM_FILENAME = '/proc/%d/statm' % os.getpid()
_SHEDDING_THRESHOLD = 0.2
_DEFAULT_UNAVAILABILITY_DURATION_SECS = 1


class GarbageCollectorInterceptor:
    """Garbage Collector Control Interceptor (GCI)."""

    def __init__(self):
        gc.disable()
        gc.set_debug(gc.DEBUG_STATS)
        self._virt = None
        self._last_unavailability_duration_secs = _DEFAULT_UNAVAILABILITY_DURATION_SECS
        print "GCI activated and automatic garbage collection disabled. "

    def before_request(self, filename=None):
        """Method that should be called before request processing.

        Returns a ShedResponse whether the current request should be evicted"""
        virt, rss = self._get_meminfo(filename)

        # CPython runtime's virtual address space increases automatically as needed, that makes
        # difficult to predictably collect garbage based on memory consumption. To mitigate that,
        # GCI is only going to update virtual memory consumption first time it is called
        # after GC collection.
        if not self._virt:
            self._virt = virt

        # TODO(danielfireman): Use sampling window
        # TODO(danielfireman): Disable GCI and enable automatic GC if there is a memory leak.
        # The rationale here is to be no worse than automatic GC in extreme cases (like
        # coding bugs)
        if rss / self._virt > _SHEDDING_THRESHOLD:
            # TODO(danielfireman): Keep standard deviation of unavailability durations and consider it.
            return timedelta(seconds=self._last_unavailability_duration_secs)

        return None

    def after_response(self, shed_response):
        # NOTE: teardown callbacks are always executed, even if before-request
        # callbacks were not executed yet but an exception happened.
        # More info at: http://flask.pocoo.org/docs/0.12/reqcontext/#teardown-callbacks
        if shed_response and shed_response.should_shed:
            start_time = time.time()
            gc.collect()
            self._last_unavailability_duration_secs = time.time() - start_time

    def _get_meminfo(self, filename=None):
        # For Linux we can use information from the proc filesystem. We use
        # '/proc/statm' as it easier to parse than '/proc/status' file.
        filename = filename or _STATM_FILENAME
        with open(filename, 'r') as fp:
            statm = fp.read().split()
            return float(statm[0]) * _PAGE_SIZE, float(statm[1]) * _PAGE_SIZE
