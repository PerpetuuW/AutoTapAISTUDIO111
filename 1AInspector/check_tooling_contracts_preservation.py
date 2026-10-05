# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    auto_dir = os.path.join(root_dir, "1AAutomation")
    if not os.path.exists(auto_dir):
        return True, "1AAutomation не найден"

    clip_path = os.path.join(auto_dir, "clipboard_sync.py")
    if os.path.exists(clip_path):
        with open(clip_path, "r", encoding="utf-8") as f:
            code = f.read()
        if "copy_to_clipboard =" not in code or "copy_text =" not in code:
            return False, "Нарушен контракт clipboard_sync.py: отсутствуют алиасы обратной совместимости!"

    patch_path = os.path.join(auto_dir, "patch_engine.py")
    if os.path.exists(patch_path):
        with open(patch_path, "r", encoding="utf-8") as f:
            code = f.read()
        if "SmartPatcher" not in code or "TransactionalPatcher" not in code:
            return False, "Нарушен контракт patch_engine.py: отсутствуют классы SmartPatcher/TransactionalPatcher!"

    return True, "Контракты локальных утилит (Tooling API) надежно защищены."
