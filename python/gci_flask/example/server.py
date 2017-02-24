from ..functions import before_request
from ..functions import after_response
from flask import Flask

app = Flask(__name__)
app.config.update(
    DEBUG=True,
)

# Adding GCI interceptor to the app.
app.before_request(before_request)
app.teardown_request(after_response)


@app.route("/")
def hello():
    return "Hello World!"


if __name__ == "__main__":
    app.run()
