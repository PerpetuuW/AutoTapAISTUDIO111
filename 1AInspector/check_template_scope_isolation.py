# -*- coding: utf-8 -*-
"""
1AInspector/check_template_scope_isolation.py
Инспектор иммунитета: валидация изоляции пула масок и защиты метаданных шаблонов.
Предотвращает:
1. Передачу глобального пула всех шаблонов приложения в поиск одного шага (лаг 10 сек).
2. Перетирание индивидуального порога калибровки (features.individualThreshold).
3. Ложное принудительное отключение цвета через features.analysis.isShapeOnlyRecommended.
"""
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    engine_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "engine", "MacroExecutionEngine.kt")
    tme_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "vision", "TemplateMatchingEngine.kt")

    if not os.path.exists(engine_file):
        return False, f"Файл не найден: {engine_file}"
    if not os.path.exists(tme_file):
        return False, f"Файл не найден: {tme_file}"

    with open(engine_file, "r", encoding="utf-8") as f:
        engine_content = f.read()

    with open(tme_file, "r", encoding="utf-8") as f:
        tme_content = f.read()

    # 1. Проверка изоляции масок шага в MacroExecutionEngine
    if "stepTemplates" not in engine_content:
        return False, "В MacroExecutionEngine.kt отсутствует изоляция масок шага (stepTemplates). Угроза таймаута мультипоиска!"

    # 2. Проверка использования индивидуального порога калибровки в TemplateMatchingEngine
    if "features.individualThreshold" not in tme_content:
        return False, "В TemplateMatchingEngine.kt не используется features.individualThreshold шаблона!"

    # 3. Проверка отсутствия ложного принудительного контура через эвристику
    if "features.analysis.isShapeOnlyRecommended" in tme_content:
        # Проверяем, что эвристика не участвует в логическом ИЛИ для isShapeDriven
        for line in tme_content.splitlines():
            if "isShapeDriven" in line and "isShapeOnlyRecommended" in line:
                return False, "Эвристика isShapeOnlyRecommended ломает цветовую калибровку в isShapeDriven!"

    return True, "Изоляция масок шага и защита метаданных шаблонов верифицированы (Zero-Regression)"
