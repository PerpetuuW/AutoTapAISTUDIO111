# -*- coding: utf-8 -*-
"""check_search_message_conciseness_and_cache_hygiene.py — Проверка лаконичности сообщений поиска и сброса кэша совпадений перед запуском макроса"""

import os

def run_check(root_dir: str) -> tuple[bool, str]:
    engine_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "engine", "MacroExecutionEngine.kt"
    )
    badge_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "overlay", "badge", "RunningBadgeOverlay.kt"
    )
    orch_file = os.path.join(
        root_dir, "app", "src", "main", "java", "com", "example", "autotap",
        "infrastructure", "orchestrator", "AutoTapOrchestrator.kt"
    )

    for p in [engine_file, badge_file, orch_file]:
        if not os.path.exists(p):
            return False, f"Файл не найден: {p}"

    with open(engine_file, "r", encoding="utf-8") as f:
        engine_src = f.read()

    with open(badge_file, "r", encoding="utf-8") as f:
        badge_src = f.read()

    with open(orch_file, "r", encoding="utf-8") as f:
        orch_src = f.read()

    # 1. Запрет перегруженного префикса '[Поиск] Мультипоиск #' для одиночных шаблонов
    if "[Поиск] Мультипоиск #" in engine_src:
        return False, "В MacroExecutionEngine.kt обнаружен громоздкий заголовок '[Поиск] Мультипоиск #', он должен быть лаконичным"

    # 2. Проверка расширенной ширины бейджа для предотвращения обрезки статуса
    if "contentWidthDp = 78" in badge_src:
        return False, "RunningBadgeOverlay.kt: contentWidthDp = 78 слишком узкий для отображения статуса"

    # 3. Проверка очистки кэша позиций поиска перед стартом выполнения
    if "TemplateMatchingEngine.clearTemplateCache()" not in orch_src:
        return False, "AutoTapOrchestrator.kt обязан вызывать TemplateMatchingEngine.clearTemplateCache() при onPlayClicked"

    return True, "[PASS] Лаконичность сообщений поиска и гигиена кэша подтверждены."

if __name__ == "__main__":
    ok, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(f"[{'PASS' if ok else 'FAIL'}] {msg}")
