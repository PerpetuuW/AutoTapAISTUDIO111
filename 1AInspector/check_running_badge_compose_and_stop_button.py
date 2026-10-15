# -*- coding: utf-8 -*-
import os, sys

def run_check(root_dir: str) -> tuple[bool, str]:
    badge_path = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "badge", "RunningBadgeComposeOverlay.kt")

    if not os.path.exists(badge_path):
        return False, f"[CRITICAL] RunningBadgeComposeOverlay.kt не найден: {badge_path}"

    with open(badge_path, "r", encoding="utf-8") as f:
        content = f.read()

    if ".clickable { onStopClick() }" not in content:
        return False, "[FAIL] В RunningBadgeComposeOverlay.kt кнопка СТОП должна быть привязана через .clickable { onStopClick() }"

    if ".clickable { onLockClick() }" not in content:
        return False, "[FAIL] В RunningBadgeComposeOverlay.kt кнопка ЗАМОК должна быть привязана через .clickable { onLockClick() }"

    if "posX = (posX + dragAmount.x.toInt())" not in content or "posY = (posY + dragAmount.y.toInt())" not in content:
        return False, "[FAIL] В RunningBadgeComposeOverlay.kt перетаскивание оверлея должно обновлять переменные posX и posY"

    return True, "[PASS] Кнопка СТОП, кликабельность замка и динамические координаты перетаскивания бейджа верифицированы."

if __name__ == "__main__":
    passed, msg = run_check(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
    print(msg)
    sys.exit(0 if passed else 1)
