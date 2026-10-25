#!/usr/bin/env bash
# Converts the phone app's TFLite embedding model to ONNX for the desktop app.
# Needs Python 3.11 with tensorflow-cpu and tf2onnx, e.g.:
#   uv venv -p 3.11 /tmp/tf && VIRTUAL_ENV=/tmp/tf uv pip install tensorflow-cpu tf2onnx
#   PYTHON=/tmp/tf/bin/python tools/convert_embedder.sh
set -euo pipefail
cd "$(dirname "$0")/.."
"${PYTHON:-python3}" -m tf2onnx.convert \
    --tflite app/src/main/assets/image_embedder.tflite \
    --output desktop-app/src/main/resources/models/image_embedder.onnx \
    --opset 17
