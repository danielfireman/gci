""""Module that holds Flask decorator that enables Garbage Collector Control Interceptor (GCI).

Example:
    @app.route("/")
    @enable_gci
    def hello():
        return "Hello World!"
"""
import os
import sys
from functools import wraps

# TODO(danielfireman): Improve this importing hack. Most likely the best option is to upload to PIP.
# Importing gci relative to gci-flask
sys.path.append(os.path.abspath(os.path.join(os.getcwd(), '../gci')))

from gci import GarbageCollectorInterceptor


def enable_gci(f):
    gci = GarbageCollectorInterceptor()

    @wraps(f)
    def decorated(*args, **kwargs):
        should_shed = gci.before()
        ret = f(*args, **kwargs)
        gci.after(should_shed)
        return ret

    return decorated
