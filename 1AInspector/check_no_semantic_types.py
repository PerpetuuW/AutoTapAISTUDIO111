# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    src_dir = os.path.join(root_dir, "app", "src", "main", "java")
    forbidden = ["UiSemanticType", "MediaPipeUiElementDetector", "AI_SEMANTIC"]
    violations = []

    for r, _, files in os.walk(src_dir):
        for f in files:
            if f.endswith(".kt"):
                p = os.path.join(r, f)
                with open(p, "r", encoding="utf-8") as kf:
                    content = kf.read()
                    for word in forbidden:
                        if word in content:
                            violations.append(f"{f}: найден устаревший символ '{word}'")

    if violations:
        return False, "\n".join(violations)

    return True, "Кодовая база полностью очищена от рудиментов UiSemanticType и псевдо-детекторов"
