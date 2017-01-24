import os
import sys

from flask import Flask

app = Flask(__name__)
app.config.update(
    DEBUG=True,
)

# Relative import for GCI.
# TODO(danielfireman): Improve this importing hack. Most likely the best option is to upload to PIP.
sys.path.append(os.path.abspath(os.path.join(os.getcwd(), "../flask_gci.py")))
import flask_gci

# Adding GCI interceptor to the app.
app.before_request(flask_gci.before_request)
app.teardown_request(flask_gci.after_response)


@app.route("/")
def hello():
    return "Hello World!"


if __name__ == "__main__":
    app.run()
