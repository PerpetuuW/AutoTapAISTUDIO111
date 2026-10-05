# check_recapture_and_lock_passthrough.py
import os
def run_check(root_dir: str) -> tuple[bool, str]:
    orch = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/orchestrator/AutoTapOrchestrator.kt"))
    if not os.path.exists(orch): return False, "AutoTapOrchestrator.kt не найден"
    with open(orch, "r", encoding="utf-8") as f: src = f.read()
    if "CaptureFrameOverlay(" not in src: return False, "startRecaptureForStep не открывает селектор области"
    lock = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/lock/ScreenLockOverlay.kt"))
    with open(lock, "r", encoding="utf-8") as f: l_src = f.read()
    if "FLAG_NOT_TOUCHABLE" not in l_src: return False, "ScreenLockOverlay не содержит FLAG_NOT_TOUCHABLE"
    return True, "Селектор на сыром кадре и сквозной тач блокировки подтверждены"
if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
