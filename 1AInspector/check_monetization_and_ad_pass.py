import os

def run_check(root_dir: str) -> tuple[bool, str]:
    license_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/license/LicenseManager.kt")
    main_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/presentation/main/MainActivity.kt")
    orch_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    xml_p = os.path.join(root_dir, "app/src/main/res/layout/activity_main.xml")

    if not os.path.exists(license_p) or not os.path.exists(main_p) or not os.path.exists(orch_p) or not os.path.exists(xml_p):
        return False, "[FAIL] Файлы подсистемы монетизации не найдены"

    with open(xml_p, "r", encoding="utf-8") as f:
        xml_src = f.read()

    with open(orch_p, "r", encoding="utf-8") as f:
        orch_src = f.read()

    # Проверка текстового контракта кнопки
    if "PRO Без рекламы / Доступ за рекламу" not in xml_src:
        return False, "[FAIL] В activity_main.xml кнопка btn_main_pro должна иметь текст 'PRO Без рекламы / Доступ за рекламу'."

    # Проверка, что вырезка шаблонов не заблокирована платным доступом
    if "Feature.TEMPLATES" in orch_src:
        return False, "[FAIL] Создание шаблонов должно быть доступно бесплатно по умолчанию без блокировки."

    return True, "[OK] Стилистика кнопки и бесплатный доступ к шаблонам подтверждены."
