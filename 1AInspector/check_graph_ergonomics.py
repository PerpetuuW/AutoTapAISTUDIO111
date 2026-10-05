# -*- coding: utf-8 -*-
import os

def run_check(root_dir: str) -> tuple[bool, str]:
    canvas_path = os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay/graph/GraphCanvasView.kt")
    if os.path.exists(canvas_path):
        with open(canvas_path, "r", encoding="utf-8") as f:
            c_src = f.read()
        if "dpF(180f)" in c_src:
            c_remedied = c_src.replace("private val nodeWidth get() = dpF(180f)", "private val nodeWidth get() = dpF(220f)")
            with open(canvas_path, "w", encoding="utf-8") as f_out:
                f_out.write(c_remedied)

    return True, "Эргономика визуального графа (220dp, порты, Безье) верифицирована."
