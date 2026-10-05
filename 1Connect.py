# -*- coding: utf-8 -*-
"""
Менеджер устройств ADB (v2.0):
- Автоматически находит adb.exe в Android SDK (%LOCALAPPDATA%), если его нет в PATH.
- Распознает mDNS TLS подключения Android Studio (adb-***._adb-tls-connect._tcp).
- Сохраняет историю и сопоставляет динамические имена с физическим девайсом.
"""

import os
import sys
import json
import shutil
import subprocess
from datetime import datetime

REGISTRY_FILE = "devices_registry.json"
HELPER_PS1 = "pin_active_device.ps1"

def get_adb_executable() -> str:
    """Гарантированно находит adb.exe в SDK или PATH."""
    local_appdata = os.environ.get("LOCALAPPDATA", "")
    candidates = [
        os.path.join(local_appdata, "Android", "Sdk", "platform-tools", "adb.exe"),
        r"C:\Android\sdk\platform-tools\adb.exe",
        shutil.which("adb")
    ]
    for cand in candidates:
        if cand and os.path.exists(cand):
            return cand
    return "adb"

ADB_BIN = get_adb_executable()

def run_adb(cmd: list[str], timeout: int = 5) -> str:
    try:
        res = subprocess.run([ADB_BIN] + cmd, capture_output=True, text=True, timeout=timeout)
        return res.stdout.strip()
    except Exception as e:
        return ""

def load_registry() -> dict:
    if os.path.exists(REGISTRY_FILE):
        try:
            with open(REGISTRY_FILE, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"default_hardware_serial": "", "devices": {}}

def save_registry(data: dict):
    with open(REGISTRY_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)

def get_prop(adb_id: str, prop_name: str) -> str:
    return run_adb(["-s", adb_id, "shell", "getprop", prop_name])

def scan_and_update_devices() -> dict:
    registry = load_registry()
    known_devices = registry.get("devices", {})

    print(f"\n🔍 Сканирование через: {ADB_BIN}")
    raw_devices = run_adb(["devices", "-l"])
    lines = [l.strip() for l in raw_devices.splitlines() if l.strip() and not l.startswith("List of")]

    active_count = 0

    for line in lines:
        parts = line.split()
        if len(parts) < 2:
            continue
        
        adb_id, status = parts[0], parts[1]
        if status != "device":
            print(f"⚠️  Устройство {adb_id} не готово (статус: {status})")
            continue

        active_count += 1
        
        # Парсим модель из строки adb devices -l если доступно
        model_from_line = ""
        for p in parts[2:]:
            if p.startswith("model:"):
                model_from_line = p.split("model:")[-1]

        hw_serial = get_prop(adb_id, "ro.serialno") or get_prop(adb_id, "ro.boot.serialno") or adb_id
        model = get_prop(adb_id, "ro.product.model") or model_from_line or "Android Device"
        brand = get_prop(adb_id, "ro.product.manufacturer") or "Device"
        android_ver = get_prop(adb_id, "ro.build.version.release") or "?"

        # Сохранение в базу
        if hw_serial in known_devices:
            entry = known_devices[hw_serial]
            entry["current_adb_id"] = adb_id
            entry["status"] = "online"
            entry["last_seen"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        else:
            known_devices[hw_serial] = {
                "alias": f"{brand} {model}".strip(),
                "hardware_serial": hw_serial,
                "current_adb_id": adb_id,
                "model": model,
                "brand": brand,
                "android_version": android_ver,
                "status": "online",
                "registered_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                "last_seen": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            }

    active_adb_ids = [l.split()[0] for l in lines if len(l.split()) >= 2 and l.split()[1] == "device"]
    for hw_id, dev in known_devices.items():
        if dev.get("current_adb_id") not in active_adb_ids:
            dev["status"] = "offline"

    registry["devices"] = known_devices
    save_registry(registry)
    print(f"✅ Найдено активных устройств: {active_count}")
    return registry

def pin_device(adb_id: str, alias: str):
    os.environ["ANDROID_SERIAL"] = adb_id
    with open(HELPER_PS1, "w", encoding="utf-8") as f:
        f.write(f'$env:ANDROID_SERIAL = "{adb_id}"\n')
        f.write(f'Write-Host "Закреплен девайс: {alias} ({adb_id})" -ForegroundColor Green\n')

    print(f"\n📌 Устройство закреплено: \033[1;32m{alias}\033[0m")
    print(f"   ADB ID: \033[1;33m{adb_id}\033[0m")
    print(f"👉 Выполните в консоли PowerShell: \033[1;36m. .\\{HELPER_PS1}\033[0m\n")

def list_devices():
    registry = scan_and_update_devices()
    devices = registry.get("devices", {})
    default_hw = registry.get("default_hardware_serial", "")

    if not devices:
        print("❌ Нет сохраненных или активных устройств.")
        return []

    print("\n" + "=" * 85)
    print(f"{'№':<3} | {'Имя / Алиас':<22} | {'Статус':<8} | {'Аппаратный SN':<18} | {'Текущий ADB ID'}")
    print("=" * 85)

    keys = list(devices.keys())
    for idx, hw in enumerate(keys):
        d = devices[hw]
        status_color = "\033[32mON\033[0m" if d.get("status") == "online" else "\033[31mOFF\033[0m"
        is_def = "★ " if hw == default_hw else "  "
        alias = is_def + d.get("alias", "No Name")[:19]
        curr_id = d.get('current_adb_id', '-')
        print(f"{idx + 1:<3} | {alias:<22} | {status_color:<17} | {hw:<18} | {curr_id}")
    print("=" * 85 + "\n")
    return keys

def select_and_pin():
    keys = list_devices()
    if not keys:
        return

    choice = input("Выберите номер устройства для закрепления: ").strip()
    if not choice.isdigit() or not (1 <= int(choice) <= len(keys)):
        print("Неверный номер.")
        return

    hw = keys[int(choice) - 1]
    registry = load_registry()
    dev = registry["devices"][hw]
    
    adb_id = dev.get("current_adb_id")
    if dev.get("status") != "online" or not adb_id:
        print("⚠️  Внимание: устройство сейчас не в сети ADB!")
        return

    pin_device(adb_id, dev.get("alias"))

def edit_device_interactive():
    keys = list_devices()
    if not keys:
        return

    choice = input("Введите номер устройства для редактирования: ").strip()
    if not choice.isdigit() or not (1 <= int(choice) <= len(keys)):
        print("Неверный номер.")
        return

    hw = keys[int(choice) - 1]
    registry = load_registry()
    dev = registry["devices"][hw]

    print(f"\nРедактирование: {dev.get('alias')} (SN: {hw})")
    new_alias = input(f"Новое имя [{dev.get('alias')}]: ").strip()
    if new_alias:
        dev["alias"] = new_alias

    make_default = input("Сделать устройством по умолчанию? (y/n): ").strip().lower()
    if make_default == 'y':
        registry["default_hardware_serial"] = hw

    save_registry(registry)
    print("✅ Данные сохранены.")

def auto_pin_default():
    registry = scan_and_update_devices()
    default_hw = registry.get("default_hardware_serial")
    devices = registry.get("devices", {})

    if not default_hw or default_hw not in devices:
        print("Девайс по умолчанию не задан. Настройте его через пункт 3.")
        return

    dev = devices[default_hw]
    if dev.get("status") == "online" and dev.get("current_adb_id"):
        pin_device(dev["current_adb_id"], dev.get("alias"))
    else:
        print(f"❌ Девайс по умолчанию '{dev.get('alias')}' сейчас отключен.")

def interactive_menu():
    while True:
        print("\n--- [Менеджер устройств ADB] ---")
        print("1. Показать список устройств и обновить статус")
        print("2. Выбрать и закрепить устройство (Pin)")
        print("3. Изменить имя (алиас) или девайс по умолчанию")
        print("4. Автоматически закрепить девайс по умолчанию")
        print("0. Выход")
        
        cmd = input("\nВыберите действие: ").strip()
        if cmd == "1":
            list_devices()
        elif cmd == "2":
            select_and_pin()
        elif cmd == "3":
            edit_device_interactive()
        elif cmd == "4":
            auto_pin_default()
        elif cmd == "0":
            break

if __name__ == "__main__":
    if len(sys.argv) > 1:
        arg = sys.argv[1].lower()
        if arg in ["--auto", "-a"]:
            auto_pin_default()
        elif arg in ["--list", "-l"]:
            list_devices()
    else:
        interactive_menu()