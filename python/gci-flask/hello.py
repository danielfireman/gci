import os
import sys
from flask import Flask

# Importing gci relative to gci-flask
sys.path.append(os.path.abspath(os.path.join(os.getcwd(), '../gci')))
from gci import enable_gci

app = Flask(__name__)


@app.route("/")
@enable_gci
def hello():
    return "Hello World!"


if __name__ == "__main__":
    app.run()
