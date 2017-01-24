import os
import sys

# Relative import
sys.path.append(os.path.abspath(os.path.join(os.getcwd(), "../gci.py")))

from flask_gci_decorator import enable_gci
from flask import Flask

app = Flask(__name__)
app.config.update(
    DEBUG=True,
)


@app.route("/")
@enable_gci
def hello():
    return "Hello World!"


if __name__ == "__main__":
    app.run()
