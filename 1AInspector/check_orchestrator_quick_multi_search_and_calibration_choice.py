# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    p_orch = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "orchestrator", "AutoTapOrchestrator.kt")
    p_calib = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "capture", "CalibrationOverlay.kt")
    p_repo = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "storage", "TemplateRepositoryImpl.kt")
    p_neural = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "vision", "NeuralVisualMatcher.kt")

    for path, name in [(p_orch, "AutoTapOrchestrator.kt"), (p_calib, "CalibrationOverlay.kt"), (p_repo, "TemplateRepositoryImpl.kt"), (p_neural, "NeuralVisualMatcher.kt")]:
        if not os.path.exists(path):
            return False, f"[CRITICAL] Файл {name} не найден"

    with open(p_orch, "r", encoding="utf-8") as f:
        orch_content = f.read()

    with open(p_calib, "r", encoding="utf-8") as f:
        calib_content = f.read()

    with open(p_repo, "r", encoding="utf-8") as f:
        repo_content = f.read()

    with open(p_neural, "r", encoding="utf-8") as f:
        neural_content = f.read()

    # 1. Захват нового шаблона для мультипоиска в быстром меню
    if "startAddTemplateForStep" not in orch_content:
        return False, "[FAIL] В AutoTapOrchestrator.kt отсутствует функция startAddTemplateForStep"

    if "existingTemplatePath = null" not in orch_content:
        return False, "[FAIL] В startAddTemplateForStep должен передаваться existingTemplatePath = null"

    # 2. Выбор экрана калибровки (живой/сохраненный)
    if "showCalibrationChoiceModal" not in orch_content:
        return False, "[FAIL] В AutoTapOrchestrator.kt отсутствует диалог выбора экрана калибровки showCalibrationChoiceModal"

    if "НА ТЕКУЩЕМ ЭКРАНЕ" not in orch_content or "НА СОХРАНЕННОМ СКРИНШОТЕ" not in orch_content:
        return False, "[FAIL] В диалоге выбора источника калибровки отсутствуют варианты ТЕКУЩИЙ ЭКРАН / СОХРАНЕННЫЙ СКРИНШОТ"

    # 3. Принудительное расширение .png в репозитории
    if 'if (name.endsWith(".png")) name else "$name.png"' not in repo_content and 'endsWith(".png")' not in repo_content:
        return False, "[FAIL] В TemplateRepositoryImpl.kt отсутствует принудительное расширение .png для шаблонов"

    # 4. Отдельный порог сходимости AI (98%)
    if "coerceIn(98, 99)" not in neural_content and "98" not in neural_content:
        return False, "[FAIL] В NeuralVisualMatcher.kt должен использоваться независимый порог 98%"

    # 5. Сворачивание калибровки и отсутствие кнопки Теста
    if "calibrationContentLayout" not in calib_content:
        return False, "[FAIL] В CalibrationOverlay.kt отсутствует контейнер calibrationContentLayout для безопасного сворачивания"

    if "btnTestClick" in calib_content or 'text = "ТЕСТ КЛИКА"' in calib_content:
        return False, "[FAIL] Из CalibrationOverlay.kt должна быть удалена лишняя кнопка ТЕСТ КЛИКА"

    return True, "[PASS] Инварианты мультипоиска, выбора экрана калибровки, расширения .png и AI порога 98% соблюдены."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
