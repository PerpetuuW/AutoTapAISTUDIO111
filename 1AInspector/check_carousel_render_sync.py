# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    dialog_file = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap", "infrastructure", "overlay", "dialog", "EditActionDialog.kt")
    if not os.path.exists(dialog_file):
        return True, "EditActionDialog.kt не найден"

    with open(dialog_file, "r", encoding="utf-8") as f:
        content = f.read()

    idx_all = content.find("btnSelectAll =")
    if idx_all == -1:
        return False, "btnSelectAll не найден в EditActionDialog.kt"

    scope_all = content[idx_all:idx_all + 500]
    if "renderCarouselItems()" not in scope_all:
        return False, "btnSelectAll не вызывает renderCarouselItems()! Рассинхрон UI карусели."

    return True, "Реактивная перерисовка карусели шаблонов при пакетном выборе подтверждена"
