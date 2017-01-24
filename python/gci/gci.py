from datetime import timedelta


class ShedResponse:
    """"Holds the response of processing a single request from GarbageCollectorControlInterceptor."""

    def __init__(self, should_shed=False, unavailability_duration=None):
        self.should_shed = should_shed
        self.unavailability_duration = unavailability_duration


class GarbageCollectorInterceptor:
    """Garbage Collector Control Interceptor (GCI).
    This class is thread-safe. It is meant to be used as singleton in highly concurrent environment.
    """

    def before(self):
        # TODO(danielfireman): Implement this method
        return ShedResponse(False, timedelta(seconds=1))

    def after(self, shed_response):
        # TODO(danielfireman): Implement this method
        pass
