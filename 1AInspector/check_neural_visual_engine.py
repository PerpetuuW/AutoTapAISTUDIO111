# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    nv_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "vision", "NeuralVisualMatcher.kt")
    gv_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "vision", "GoogleVisionTemplateMatcher.kt")

    if os.path.exists(gv_path):
        return False, "GoogleVisionTemplateMatcher.kt должен быть удален (ликвидация OCR для графики)"

    if not os.path.exists(nv_path):
        return False, "NeuralVisualMatcher.kt не найден"

    with open(nv_path, "r", encoding="utf-8") as f:
        content = f.read()

    # Робастная проверка формулы ZNCC (Zero-mean Normalized Cross-Correlation)
    has_zncc = "val zncc = (cov / (tStdDev * sStdDev))" in content or "val zncc = cov / (tStdDev * sStdDev)" in content or "cov / (tStdDev * sStdDev)" in content
    if not has_zncc:
        return False, "NeuralVisualMatcher: отсутствует реализация нормализованной кросс-корреляции (ZNCC)"

    return True, "Визуальный нейросетевой пайплайн с ZNCC-фолбэком успешно верифицирован"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath("."))
    print(f"Status: {passed}, Msg: {msg}")
