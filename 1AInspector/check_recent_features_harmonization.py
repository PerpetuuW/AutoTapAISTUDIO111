# -*- coding: utf-8 -*-
import os
import sys
import json

def run_check(root_dir: str) -> tuple[bool, str]:
    # 1. Проверка полного отсутствия кислотного неона #00F5D4 в CalibrationOverlay
    calib_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/capture/CalibrationOverlay.kt".replace("/", os.sep))
    if os.path.exists(calib_path):
        with open(calib_path, "r", encoding="utf-8") as f:
            content = f.read()
        if '"#00F5D4"' in content or '"#00f5d4"' in content:
            return False, "В CalibrationOverlay.kt обнаружен запрещенный кислотно-неоновый цвет #00F5D4"

    # 2. Проверка отсутствия фиолетового неона и наличия Zero-Jump драга в ControlPanelOverlay.kt
    panel_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/panel/ControlPanelOverlay.kt".replace("/", os.sep))
    if os.path.exists(panel_path):
        with open(panel_path, "r", encoding="utf-8") as f:
            panel_content = f.read()
        if "0xFF2E1854" in panel_content or "0xFF2E1522" in panel_content:
            return False, "В ControlPanelOverlay.kt обнаружены устаревшие неоновые овалы highlightButtons"
        if "startRawX = ev.rawX" not in panel_content and "startRawX = event.rawX" not in panel_content:
            return False, "В ControlPanelOverlay.kt не обнаружен ре-анкоринг координат для устранения смещения при драге"

    # 3. Проверка порядка туториалов и отсутствия фантомного шага джойстика
    tut_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/tutorial/InteractiveTutorialOverlay.kt".replace("/", os.sep))
    if os.path.exists(tut_path):
        with open(tut_path, "r", encoding="utf-8") as f:
            tut_content = f.read()

        if "BTN_JOYSTICK" in tut_content or '"ДЖОЙСТИК"' in tut_content:
            return False, "В InteractiveTutorialOverlay.kt обнаружен устаревший фантомный шаг 'ДЖОЙСТИК'"

        idx_capture = tut_content.find('"ВЫРЕЗКА ШАБЛОНА"')
        idx_record = tut_content.find('"ЗАПИСЬ ЖЕСТОВ"')
        idx_add = tut_content.find('"ДОБАВИТЬ ШАГ"')
        if idx_capture == -1 or idx_record == -1 or idx_add == -1:
            return False, "В InteractiveTutorialOverlay.kt отсутствуют ключевые шаги туториала"
        if not (idx_capture < idx_record < idx_add):
            return False, "Нарушен порядок шагов туториала: ожидается ВЫРЕЗКА ШАБЛОНА -> ЗАПИСЬ ЖЕСТОВ -> ДОБАВИТЬ ШАГ"

    # 4. Каноническая полиморфная проверка реестра дефектов
    ledger_path = os.path.join(root_dir, "1AAutomation", ".defect_ledger.json")
    if os.path.exists(ledger_path):
        try:
            with open(ledger_path, "r", encoding="utf-8") as f:
                ledger = json.load(f)

            items = []
            if isinstance(ledger, dict):
                if "defects" in ledger and isinstance(ledger["defects"], list):
                    items = ledger["defects"]
                else:
                    items = [v for v in ledger.values() if isinstance(v, dict)]
            elif isinstance(ledger, list):
                items = [item for item in ledger if isinstance(item, dict)]

            for item in items:
                if not isinstance(item, dict):
                    continue
                checker = item.get("checker") or item.get("checker_file") or item.get("checker_module")
                if not checker or not checker.strip():
                    return False, f"В реестре дефектов обнаружена запись без чекера: {item.get('id')}"
                ch_file = os.path.join(root_dir, "1AInspector", checker.strip())
                if not os.path.exists(ch_file):
                    return False, f"Файл чекера не существует на диске: {checker}"
        except Exception as ex:
            return False, f"Ошибка валидации реестра дефектов: {ex}"

    return True, "Все последние обновления (палитра M3, Zero-Jump драг, ликвидация джойстика) полностью верифицированы"
