import os

def run_check(root_dir: str) -> tuple[bool, str]:
    license_p = os.path.join(root_dir, "app/src/main/java/com/example/autotap/core/license/LicenseManager.kt")
    xml_p = os.path.join(root_dir, "app/src/main/res/layout/activity_main.xml")

    if not os.path.exists(license_p) or not os.path.exists(xml_p):
        return False, "[FAIL] Файлы лицензирования или разметки не найдены"

    with open(license_p, "r", encoding="utf-8") as f:
        lic_code = f.read()

    with open(xml_p, "r", encoding="utf-8") as f:
        xml_code = f.read()

    if "vwobfqlrsc8tbyu" not in lic_code:
        return False, "[FAIL] В LicenseManager отсутствует вайтлист устройства vwobfqlrsc8tbyu."

    if "btn_main_pro" not in xml_code:
        return False, "[FAIL] В activity_main.xml отсутствует видимая кнопка btn_main_pro."

    return True, "[OK] Вайтлист разработчика и видимая кнопка тарифов верифицированы."
