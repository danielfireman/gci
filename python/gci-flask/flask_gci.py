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


def before():
    shed_response = _gci.before()
    g._shed_response = shed_response
    if shed_response.should_shed:
        return Response(status=503, headers={"Retry-After": str(shed_response.unavailability_duration.total_seconds())})
    return None


def after(response):
    _gci.after(getattr(g, "_shed_response", None))
    return response
