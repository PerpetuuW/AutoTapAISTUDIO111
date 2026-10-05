import os

def run_check(root_dir: str) -> tuple[bool, str]:
    neural_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/vision/NeuralVisualMatcher.kt")
    if not os.path.exists(neural_p):
        return True, "[INFO] NeuralVisualMatcher.kt не найден"

    with open(neural_p, "r", encoding="utf-8") as f:
        src = f.read()

    if "colorDelta > 28" not in src:
        return False, "[FAIL] В NeuralVisualMatcher цветовой барьер colorDelta должен быть строгим (<= 28) против кликов по рекламе."

    if "0.45f + norm * 0.50f" not in src:
        return False, "[FAIL] В NeuralVisualMatcher формула percentToRequiredZncc должна требовать реальный высокий ZNCC."

    return True, "[OK] Строгая прецизионная фильтрация AI-движка подтверждена."
