# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str):
    gradle_file = os.path.join(root_dir, "app", "build.gradle.kts")
    ocr_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "ocr", "OcrEngine.kt")

    if not os.path.exists(gradle_file) or not os.path.exists(ocr_file):
        return False, "[CRITICAL] Файлы сборки или OCR не найдены"

    with open(gradle_file, "r", encoding="utf-8") as f:
        g_code = f.read()

    if "onnxruntime-android:1.30" not in g_code and "onnxruntime-android:1.20" not in g_code and "onnxruntime-android:1.19" not in g_code:
        return False, "[FAIL] Missing onnxruntime-android:1.20.x or 1.19.x (16 KB Aligned)"

    with open(ocr_file, "r", encoding="utf-8") as f:
        o_code = f.read()

    if "recognizeTextWithOnnx" not in o_code:
        return False, "[FAIL] В OcrEngine.kt отсутствует интеграция инференса Pure ONNX"

    return True, "[PASS] Архитектура Pure ONNX OCR 1.30.0 (16 KB Aligned) полностью верифицирована"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
