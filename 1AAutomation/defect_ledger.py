# -*- coding: utf-8 -*-
"""
1AAutomation/defect_ledger.py
Реестр дефектов и контроль непрерывного иммунитета (v5.6):
- Хранит базу всех произошедших и устраненных дефектов в .defect_ledger.json.
- Проверяет наличие неснимаемых модулей проверки в 1AInspector/ для каждого дефекта.
"""

import os
import json
from datetime import datetime

LEDGER_FILE = ".defect_ledger.json"

def get_ledger_path(root_dir: str) -> str:
    return os.path.join(root_dir, "1AAutomation", LEDGER_FILE)

def load_ledger(root_dir: str) -> dict:
    path = get_ledger_path(root_dir)
    if os.path.exists(path):
        try:
            with open(path, "r", encoding="utf-8") as f:
                return json.load(f)
        except Exception:
            pass
    return {"version": 1, "defects": []}

def save_ledger(root_dir: str, data: dict):
    path = get_ledger_path(root_dir)
    try:
        with open(path, "w", encoding="utf-8", newline="\n") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
    except Exception as e:
        print(f"[DefectLedger Warning]: Не удалось сохранить реестр: {e}")

def record_defect(root_dir: str, defect_id: str, description: str, target_file: str, checker_module: str, status: str = "FIXED"):
    ledger = load_ledger(root_dir)
    defects = ledger.setdefault("defects", [])
    
    # Обновляем или добавляем дефект
    found = False
    for d in defects:
        if d.get("id") == defect_id:
            d["description"] = description
            d["target_file"] = target_file
            d["checker_module"] = checker_module
            d["status"] = status
            d["updated_at"] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
            found = True
            break
            
    if not found:
        defects.append({
            "id": defect_id,
            "description": description,
            "target_file": target_file,
            "checker_module": checker_module,
            "status": status,
            "created_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
            "updated_at": datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        })
        
    save_ledger(root_dir, ledger)

def verify_immunity_coverage(root_dir: str) -> tuple[bool, list[str]]:
    """
    Проверяет, что для каждого устраненного дефекта существует действующий чекер в 1AInspector/.
    """
    ledger = load_ledger(root_dir)
    defects = ledger.get("defects", [])
    ins_dir = os.path.join(root_dir, "1AInspector")
    
    missing_checkers = []
    
    for d in defects:
        if d.get("status") == "FIXED":
            checker_name = d.get("checker_module")
            if not checker_name:
                missing_checkers.append(f"Дефект '{d.get('id')}' закрыт, но чекер иммунитета не указан!")
                continue
                
            checker_path = os.path.join(ins_dir, checker_name)
            if not os.path.exists(checker_path):
                missing_checkers.append(f"Дефект '{d.get('id')}' закрыт, но чекер '{checker_name}' ОТСУТСТВУЕТ в 1AInspector/!")
                
    if missing_checkers:
        return False, missing_checkers
        
    return True, []
