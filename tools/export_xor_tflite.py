#!/usr/bin/env python3
"""Train a tiny XOR MLP and write TFLite for the Android app (your export, not a download)."""
from __future__ import annotations

import os
import warnings

# TensorFlow logs warnings on macOS ARM; keep CI quiet.
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "2")
warnings.filterwarnings("ignore")

import numpy as np  # type: ignore

def main() -> None:
    import tensorflow as tf  # type: ignore

    out_dir = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "models")
    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, "xor_mlp.tflite")

    np.random.seed(42)
    tf.random.set_seed(42)

    model = tf.keras.Sequential(
        [
            tf.keras.layers.Dense(16, activation="sigmoid", input_shape=(2,)),
            tf.keras.layers.Dense(1, activation="sigmoid"),
        ]
    )
    x = np.array([[0, 0], [0, 1], [1, 0], [1, 1]], dtype=np.float32)
    y = np.array([[0], [1], [1], [0]], dtype=np.float32)
    model.compile(optimizer=tf.keras.optimizers.SGD(0.6), loss="mse")
    model.fit(x, y, epochs=8000, verbose=0)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()
    with open(out_path, "wb") as f:
        f.write(tflite_model)

    # Smoke check
    interp = tf.lite.Interpreter(model_content=tflite_model)
    interp.allocate_tensors()
    inf = interp.get_input_details()[0]
    ouf = interp.get_output_details()[0]
    print("Wrote", out_path, "size", len(tflite_model))
    print("input", inf["shape"], inf["dtype"], "output", ouf["shape"])


if __name__ == "__main__":
    main()
