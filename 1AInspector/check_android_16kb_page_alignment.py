# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str):
    target = os.path.join(root_dir, "app", "build.gradle.kts")
    if not os.path.exists(target):
        return False, "[CRITICAL] build.gradle.kts не найден"

    with open(target, "r", encoding="utf-8") as f:
        content = f.read()

    if "onnxruntime-android:1.30." not in content:
        return False, "[FAIL] Версия ONNX Runtime должна быть 1.30.0+ для нативной 16 KB Page Alignment совместимости"

    if "useLegacyPackaging = true" in content:
        return False, "[FAIL] useLegacyPackaging должен быть false для нативного uncompressed 16 KB mmap в Android 15+"

    return True, "[PASS] Сборка нативно поддерживает 16 KB Page Alignment без предупреждений линтера"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
