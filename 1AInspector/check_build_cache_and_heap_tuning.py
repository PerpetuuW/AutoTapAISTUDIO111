import os

def run_check(root_dir: str) -> tuple[bool, str]:
    prop_path = os.path.join(root_dir, "gradle.properties")
    if not os.path.exists(prop_path):
        return False, "[FAIL] gradle.properties не найден"
    with open(prop_path, "r", encoding="utf-8") as f:
        content = f.read()

    if "org.gradle.caching=true" not in content:
        return False, "[FAIL] Деградация скорости: флаг org.gradle.caching=true отключен."
    if "-Xmx4096m" not in content and "-Xmx6144m" not in content:
        return False, "[FAIL] Деградация скорости: объем кучи Gradle JVM меньше 4096m."
    if "org.gradle.vfs.watch=true" not in content:
        return False, "[FAIL] Деградация скорости: наблюдение VFS отключено."

    return True, "[OK] Высокоскоростные параметры сборки Gradle активны."
