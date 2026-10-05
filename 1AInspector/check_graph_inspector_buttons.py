import os

def run_check(root_dir: str) -> tuple[bool, str]:
    editor_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphEditorOverlay.kt")
    if not os.path.exists(editor_path):
        return True, "[INFO] GraphEditorOverlay.kt не найден"

    with open(editor_path, "r", encoding="utf-8") as f:
        content = f.read()

    if "btnRowBottom.addView(btnDeleteNode, LinearLayout.LayoutParams(0, dp(34)" not in content:
        return False, "[FAIL] Кнопки нижнего ряда инспектора узла должны иметь безопасную высоту dp(34)."

    if "includeFontPadding = false" not in content:
        return False, "[FAIL] Кнопки инспектора должны отключать includeFontPadding для предотвращения вертикального среза текста."

    return True, "[OK] Эргономика и типографика кнопок инспектора узла графа верифицированы."
