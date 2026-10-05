# -*- coding: utf-8 -*-
import os
import sys

def run_check(root_dir: str) -> tuple[bool, str]:
    deploy_file = os.path.join(root_dir, "1AAutomation", "adb_deploy.py")
    if not os.path.exists(deploy_file):
        return False, "Модуль 1AAutomation/adb_deploy.py отсутствует"

    with open(deploy_file, "r", encoding="utf-8") as f:
        content = f.read()

    if "NO_DEVICE_CONNECTED" in content and "def try_adb_install" in content:
        return True, "Контракт терминальной обработки ADB соблюден"

    return False, "Модуль adb_deploy.py не поддерживает статус NO_DEVICE_CONNECTED"
