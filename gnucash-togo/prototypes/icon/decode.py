"""Turns a saved evaluate_script result (a PNG data URL in JSON) into a .png file.

Usage: python -I decode.py <in.json> <out.png>
"""
import base64
import json
import sys

data = json.load(open(sys.argv[1], encoding="utf-8"))
if not isinstance(data, str):
    data = data.get("result", data)
open(sys.argv[2], "wb").write(base64.b64decode(data.split(",", 1)[1]))
print("ok")
