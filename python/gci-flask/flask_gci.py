""""Module that holds Flask decorator that enables Garbage Collector Control Interceptor (GCI).

Example:
    @app.route("/")
    @enable_gci
    def hello():
        return "Hello World!"
"""
import os
import sys
from flask import Response
from flask import g

# TODO(danielfireman): Improve this importing hack. Most likely the best option is to upload to PIP.
# Importing gci relative to gci-flask
sys.path.append(os.path.abspath(os.path.join(os.getcwd(), '../gci')))

from gci import GarbageCollectorInterceptor

_gci = GarbageCollectorInterceptor()


def before_request():
    unavailability_duration = _gci.before_request()
    if unavailability_duration:
        g._unavailability_duration = unavailability_duration
        return Response(status=503, headers={"Retry-After": str(unavailability_duration.total_seconds())})
    return None


def after_response(exception=None):
    _gci.after_response(hasattr(g, "_unavailability_duration"))
