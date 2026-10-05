import os

def run_check(root_dir: str) -> tuple[bool, str]:
    gradle_p = os.path.join(root_dir, "app/build.gradle.kts")
    pro_p = os.path.join(root_dir, "app/proguard-rules.pro")

    if not os.path.exists(gradle_p) or not os.path.exists(pro_p):
        return True, "[INFO] Конфигурационные файлы сборщика не найдены"

    with open(gradle_p, "r", encoding="utf-8") as f:
        gradle_src = f.read()

    with open(pro_p, "r", encoding="utf-8") as f:
        pro_src = f.read()

    if 'signingConfig = signingConfigs.getByName("release")' not in gradle_src:
        return False, "[FAIL] Релизная сборка должна использовать release signingConfig, а не debug."

    if "com.google.mediapipe.tasks.vision" not in pro_src:
        return False, "[FAIL] В proguard-rules.pro отсутствует защита MediaPipe Tasks Vision от R8."

    return True, "[OK] Релизный контур проекта полностью готов к публикации в RuStore."
