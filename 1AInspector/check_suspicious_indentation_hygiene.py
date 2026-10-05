# check_suspicious_indentation_hygiene.py
# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    tme_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "vision", "TemplateMatchingEngine.kt")
    gcv_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "graph", "GraphCanvasView.kt")

    if not os.path.exists(tme_path) or not os.path.exists(gcv_path):
        return False, "Файлы не найдены"

    with open(tme_path, "r", encoding="utf-8") as f:
        tme = f.read()
    with open(gcv_path, "r", encoding="utf-8") as f:
        gcv = f.read()

    # Проверка отсутствия избыточных отступов (16..24 пробела)
    if "                var templateSim = defaultSim" in tme:
        return False, "TemplateMatchingEngine: обнаружен избыточный отступ var templateSim (16 sp)"
    if "                        statL1Sectors" in tme:
        return False, "TemplateMatchingEngine: обнаружен избыточный отступ statL1Sectors (24 sp)"
    if "                val idxTrig = node.triggers" in gcv:
        return False, "GraphCanvasView: обнаружен избыточный отступ val idxTrig (16 sp)"

    return True, "Все подозрительные отступы нормализованы, предупреждения компилятора устранены"

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath("."))
    print(f"Status: {passed}, Msg: {msg}")
