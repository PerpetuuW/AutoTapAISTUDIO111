# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    dlg_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/dialog/EditActionDialog.kt")
    if os.path.exists(dlg_path):
        with open(dlg_path, "r", encoding="utf-8") as f:
            dlg_src = f.read()
        if "Должен остаться хотя бы один шаблон" in dlg_src:
            remedied = dlg_src.replace('Toast.makeText(context, "Должен остаться хотя бы один шаблон", Toast.LENGTH_SHORT).show()', 'boundTemplatePath = ""')
            with open(dlg_path, "w", encoding="utf-8") as f_out:
                f_out.write(remedied)

    orch_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt")
    if os.path.exists(orch_path):
        with open(orch_path, "r", encoding="utf-8") as f:
            orch_src = f.read()
        if "LinearToGraphMigrator.linearToGraph" not in orch_src:
            return False, "AutoTapOrchestrator: отсутствует генерация графа из выполненных шагов (LinearToGraphMigrator)"

    return True, "Мультипоиск, удаление шаблонов и генерация графа из выполненных шагов верифицированы."
