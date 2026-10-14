# -*- coding: utf-8 -*-
import os, sys, re

def generate_graph(root_dir: str):
    target_dir = os.path.join(root_dir, "app", "src", "main", "java", "com", "example", "autotap")
    if not os.path.exists(target_dir):
        print(f"[ERROR] Не найдена директория {target_dir}")
        return

    nodes = set()
    edges = set()

    package_pattern = re.compile(r'^package\s+([\w\.]+)')
    class_pattern = re.compile(r'(?:class|object|interface)\s+([A-Z][\w]+)')
    import_pattern = re.compile(r'^import\s+com\.example\.autotap\.([\w\.]+)\.([A-Z][\w]+)')

    for current_root, _, files in os.walk(target_dir):
        for file in files:
            if file.endswith(".kt"):
                full_path = os.path.join(current_root, file)

                with open(full_path, "r", encoding="utf-8", errors="replace") as f:
                    content = f.read()

                classes_in_file = class_pattern.findall(content)
                if not classes_in_file: continue

                main_class = classes_in_file[0]
                nodes.add(main_class)

                imports = import_pattern.findall(content)
                for pkg, imported_class in imports:
                    if imported_class != main_class:
                        nodes.add(imported_class)
                        edges.add((main_class, imported_class))

    artifact_dir = os.path.join(root_dir, ".artifacts", "8d2ad352-9492-4261-aacd-804734d8a4f9")
    os.makedirs(artifact_dir, exist_ok=True)
    out_path = os.path.join(artifact_dir, "architecture_graph.artifact.md")

    with open(out_path, "w", encoding="utf-8") as f:
        f.write("# Граф Зависимостей Архитектуры (AutoTap)\n\n")
        f.write("Данный Mermaid-граф автоматически сгенерирован 1AInspector для визуализации взаимосвязей классов внутри проекта.\n\n")
        f.write("```mermaid\n")
        f.write("graph TD\n")

        # Группировка или стилизация
        f.write("    classDef orchestrator fill:#312E81,stroke:#818CF8,stroke-width:2px,color:#fff;\n")
        f.write("    classDef engine fill:#3B2606,stroke:#F59E0B,stroke-width:2px,color:#fff;\n")
        f.write("    classDef overlay fill:#0F172A,stroke:#38BDF8,stroke-width:2px,color:#fff;\n")
        f.write("    classDef domain fill:#14241B,stroke:#34D399,stroke-width:2px,color:#fff;\n\n")

        for n in sorted(nodes):
            f.write(f"    {n}\n")
            if "Orchestrator" in n: f.write(f"    class {n} orchestrator\n")
            elif "Engine" in n or "Matcher" in n: f.write(f"    class {n} engine\n")
            elif "Overlay" in n or "Dialog" in n: f.write(f"    class {n} overlay\n")
            elif "Action" in n or "Scenario" in n or "Template" in n: f.write(f"    class {n} domain\n")

        f.write("\n")
        for fro, to in sorted(edges):
            f.write(f"    {fro} --> {to}\n")

        f.write("```\n")

    print(f"[PASS] Граф архитектуры сгенерирован: {out_path}")

if __name__ == "__main__":
    generate_graph(os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))
