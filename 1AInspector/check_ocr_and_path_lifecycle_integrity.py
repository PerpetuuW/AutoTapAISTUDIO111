# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str):
    p_ocr = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "ocr", "OcrEngine.kt")
    if not os.path.exists(p_ocr):
        return False, f"[CRITICAL] Файл не найден: {p_ocr}"

    with open(p_ocr, "r", encoding="utf-8") as f:
        ocr_src = f.read()
    if "stripH = 48" not in ocr_src:
        return False, "[FAIL] OcrEngine: отсутствует полосовое сканирование текста"

    return True, "[PASS] Контракт OCR полосового сканирования соблюден"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
