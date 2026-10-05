# -*- coding: utf-8 -*-
"""
1AAutomation/adb_deploy.py
Чистый архитектурный модуль деплоя APK через ADB (AutoTap v6.0)

Особенности:
- Полное разделение логики и вывода.
- Единый протокол ошибок DeployResult.
- Единый слой ADB-вызовов.
- Стратегии установки (DefaultInstallStrategy, XiaomiInstallStrategy).
- Fail-fast архитектура.
"""

import os
import subprocess
from dataclasses import dataclass

# ================================
# Константы
# ================================

SERIAL_CONFIG_FILE = ".target_serial"
TARGET_PACKAGE = "com.example.autotap"

PUSH_TIMEOUT = 120
INSTALL_TIMEOUT = 120
PING_TIMEOUT = 3
SCAN_TIMEOUT = 5

REMOTE_TMP_APK = "/data/local/tmp/autotap_debug.apk"


# ================================
# Результат операции
# ================================

@dataclass
class DeployResult:
    success: bool
    code: str
    message: str


# ================================
# Универсальный ADB-вызов
# ================================

def adb(adb_exec: str, args: list[str], timeout: int = 10):
    try:
        res = subprocess.run(
            [adb_exec] + args,
            capture_output=True,
            text=True,
            timeout=timeout
        )
        return res.returncode, res.stdout.strip(), res.stderr.strip()
    except subprocess.TimeoutExpired:
        return -1, "", "TIMEOUT"
    except Exception as e:
        return -1, "", str(e)


# ================================
# Поиск ADB
# ================================

def get_adb_path() -> str | None:
    try:
        subprocess.run(["adb", "--version"], stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)
        return "adb"
    except Exception:
        pass

    if os.name == "nt":
        local_appdata = os.environ.get("LOCALAPPDATA")
        if local_appdata:
            adb_win = os.path.join(local_appdata, "Android", "Sdk", "platform-tools", "adb.exe")
            if os.path.exists(adb_win):
                return adb_win
    else:
        paths = [
            "~/Library/Android/sdk/platform-tools/adb",
            "~/Android/Sdk/platform-tools/adb"
        ]
        for p in paths:
            full = os.path.expanduser(p)
            if os.path.exists(full):
                return full

    return None


# ================================
# Утилиты
# ================================

def get_prop(adb_exec: str, transport_id: str, prop_name: str) -> str:
    code, out, _ = adb(adb_exec, ["-s", transport_id, "shell", "getprop", prop_name], timeout=3)
    return out if code == 0 else ""


def scan_connected_devices(adb_exec: str) -> list[dict]:
    code, out, err = adb(adb_exec, ["devices", "-l"], timeout=SCAN_TIMEOUT)
    if code != 0:
        return []

    lines = out.strip().splitlines()[1:]
    devices = []

    for line in lines:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            dev_id = parts[0]
            model = get_prop(adb_exec, dev_id, "ro.product.model") or "Unknown Model"
            serial = (
                get_prop(adb_exec, dev_id, "ro.serialno")
                or get_prop(adb_exec, dev_id, "ro.boot.serialno")
                or dev_id
            )
            android_v = get_prop(adb_exec, dev_id, "ro.build.version.release") or "?"
            devices.append({
                "transport_id": dev_id,
                "model": model,
                "serial": serial,
                "android_version": android_v
            })

    return devices


def get_saved_target_serial(auto_folder: str) -> str:
    cfg_path = os.path.join(auto_folder, SERIAL_CONFIG_FILE)
    if os.path.exists(cfg_path):
        try:
            with open(cfg_path, "r", encoding="utf-8") as f:
                return f.read().strip()
        except Exception:
            return ""
    return ""


def save_target_serial(auto_folder: str, serial: str):
    if not serial:
        return
    cfg_path = os.path.join(auto_folder, SERIAL_CONFIG_FILE)
    try:
        with open(cfg_path, "w", encoding="utf-8") as f:
            f.write(serial.strip() + "\n")
    except Exception:
        pass


def select_target_device(adb_exec: str, auto_folder: str) -> dict | None:
    devices = scan_connected_devices(adb_exec)
    if not devices:
        return None

    saved_serial = get_saved_target_serial(auto_folder)
    if saved_serial:
        for d in devices:
            if d["serial"] == saved_serial or d["transport_id"] == saved_serial:
                return d

    chosen = devices[0]
    save_target_serial(auto_folder, chosen["serial"])
    return chosen


def is_connection_alive(adb_exec: str, transport_id: str) -> bool:
    code, out, _ = adb(adb_exec, ["-s", transport_id, "shell", "echo", "PING_ALIVE"], timeout=PING_TIMEOUT)
    return code == 0 and "PING_ALIVE" in out


# ================================
# Стратегии установки
# ================================

class DefaultInstallStrategy:
    def install(self, adb_exec: str, tid: str, remote_tmp_apk: str):
        return adb(
            adb_exec,
            ["-s", tid, "shell", "pm", "install", "-r", "-t", "-d", "-g", remote_tmp_apk],
            timeout=INSTALL_TIMEOUT
        )


class XiaomiInstallStrategy(DefaultInstallStrategy):
    """
    Xiaomi bypass: повторная установка после uninstall.
    """
    def install(self, adb_exec: str, tid: str, remote_tmp_apk: str):
        code, out, err = super().install(adb_exec, tid, remote_tmp_apk)
        output = (out + "\n" + err).strip()

        if "INSTALL_FAILED" in output or "Failure" in output:
            adb(adb_exec, ["-s", tid, "shell", "pm", "uninstall", TARGET_PACKAGE], timeout=15)
            return super().install(adb_exec, tid, remote_tmp_apk)

        return code, out, err


# ================================
# Главный процесс деплоя
# ================================

def try_adb_install(root_dir: str) -> DeployResult:
    adb_exec = get_adb_path()
    if not adb_exec:
        return DeployResult(True, "ADB_NOT_FOUND", "ADB не найден в системе")

    apk_path = os.path.join(root_dir, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
    if not os.path.exists(apk_path):
        return DeployResult(False, "APK_NOT_FOUND", f"Файл APK не найден: {apk_path}")

    auto_folder = os.path.join(root_dir, "1AAutomation")
    target = select_target_device(adb_exec, auto_folder)
    if not target:
        return DeployResult(True, "NO_DEVICE_CONNECTED", "Активное устройство не обнаружено")

    tid = target["transport_id"]

    if not is_connection_alive(adb_exec, tid):
        return DeployResult(False, "STALE_CONNECTION", "Устройство не отвечает на пинг")

    # Шаг 1: push
    code, out, err = adb(adb_exec, ["-s", tid, "push", apk_path, REMOTE_TMP_APK], timeout=PUSH_TIMEOUT)
    if code != 0:
        return DeployResult(False, "PUSH_FAILED", err or out)

    # Выбор стратегии
    is_xiaomi = "xiaomi" in target["model"].lower()
    strategy = XiaomiInstallStrategy() if is_xiaomi else DefaultInstallStrategy()

    # Шаг 2: install
    code, out, err = strategy.install(adb_exec, tid, REMOTE_TMP_APK)
    output = (out + "\n" + err).strip()

    if "Success" in output or code == 0:
        # Очистка временного файла
        adb(adb_exec, ["-s", tid, "shell", "rm", REMOTE_TMP_APK], timeout=10)

        # Запуск приложения
        activity = f"{TARGET_PACKAGE}.presentation.main.MainActivity"
        adb(adb_exec, ["-s", tid, "shell", "am", "force-stop", TARGET_PACKAGE], timeout=10)
        adb(adb_exec, ["-s", tid, "shell", "am", "start", "-n", f"{TARGET_PACKAGE}/{activity}"], timeout=10)

        return DeployResult(True, "INSTALLED_AND_LAUNCHED", "Успех")

    return DeployResult(False, "INSTALLATION_FAILED", output)
