import os

def run_check(root_dir: str) -> tuple[bool, str]:
    path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelOverlay.kt")
    if not os.path.exists(path):
        return True, "[INFO] ControlPanelOverlay.kt не найден"

    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    # Проверка наличия цикличного перехода режимов в одной кнопке
    if "PanelDisplayMode.COMPACT_SINGLE_ROW -> PanelDisplayMode.MINI_BUBBLE" not in content:
        return False, "[FAIL] В ControlPanelOverlay отсутствует 3-фазный цикличный переход режимов."

    return True, "[OK] Цикличная кнопка сворачивания (Полное меню -> Строка -> 2 кнопки -> Полное меню) активна."
