# check_viewgroup_parent_collisions.py
import os, re
def run_check(root_dir: str) -> tuple[bool, str]:
    ov_dir = os.path.normpath(os.path.join(root_dir, "app/src/main/java/com/example/autotap/infrastructure/overlay"))
    if not os.path.exists(ov_dir): return False, "overlay dir не найдена"
    collisions = []
    for root, _, files in os.walk(ov_dir):
        for file in files:
            if not file.endswith(".kt"): continue
            p = os.path.join(root, file)
            with open(p, "r", encoding="utf-8", errors="replace") as f: lines = f.readlines()
            locals_set = set(); added = {}
            for l_idx, line in enumerate(lines):
                s = line.strip()
                if re.match(r'(override\s+)?(fun|private\s+fun)\s+\w+', s): locals_set.clear(); added.clear()
                vm = re.search(r'\b(val|var)\s+([a-zA-Z0-9_]+)\s*[:=]', s)
                if vm: locals_set.add(vm.group(2))
                am = re.search(r'([a-zA-Z0-9_]+)\.addView\(\s*([a-zA-Z0-9_]+)\s*[,)]', s)
                if am:
                    parent, child = am.group(1), am.group(2)
                    if child in locals_set and not child.startswith("create"):
                        k = f"{parent}.{child}"
                        if k in added: collisions.append(f"{file}:{l_idx+1} повторный addView({child})")
                        else: added[k] = l_idx + 1
    if collisions: return False, "\n".join(collisions)
    return True, "Иерархии ViewGroup защищены от коллизий"
if __name__ == "__main__":
    ok, msg = run_check(os.getcwd()); print(f"Result: {ok} -> {msg}")
