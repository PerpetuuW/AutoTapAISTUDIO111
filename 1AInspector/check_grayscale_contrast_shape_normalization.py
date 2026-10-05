# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    p_tme = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "vision", "TemplateMatchingEngine.kt")
    p_sme = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "vision", "SmartMaskEngine.kt")

    if not os.path.exists(p_tme) or not os.path.exists(p_sme):
        return False, "[CRITICAL] Файлы vision-модулей не найдены"

    with open(p_tme, "r", encoding="utf-8") as f: content_tme = f.read()
    with open(p_sme, "r", encoding="utf-8") as f: content_sme = f.read()

    if "Adaptive Grayscale Contrast Normalization" not in content_tme:
        return False, "[FAIL] В TemplateMatchingEngine отсутствует нормализация диапазона яркости"

    if "Adaptive Grayscale Contrast Normalization" not in content_sme:
        return False, "[FAIL] В SmartMaskEngine отсутствует ч/б контрастирование переднего плана маски"

    return True, "[PASS] Контракт адаптивной ч/б нормализации масок и шаблонов соблюден."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
