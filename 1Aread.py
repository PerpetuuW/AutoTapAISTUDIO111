import os

def scan_compose_and_overlays(root_dir=".", output_file="compose_audit.txt"):
    exclude_dirs = {'build', '.gradle', '.idea', '.git', 'gradle'}
    target_keywords = ('androidx.compose', '@Composable', 'ComposeView', 'ComposeOverlay')
    must_include_files = {
        'AutoTapOrchestrator.kt',
        'ControlPanelListener.kt',
        'OverlayWindowManager.kt',
        'RunningBadgeComposeOverlay.kt',
        'ControlPanelComposeOverlay.kt'
    }

    root_dir = os.path.abspath(root_dir)
    found_files = []

    for dirpath, dirnames, filenames in os.walk(root_dir):
        dirnames[:] = [d for d in dirnames if d not in exclude_dirs]
        for filename in filenames:
            if not filename.endswith('.kt'):
                continue
            file_path = os.path.join(dirpath, filename)
            rel_path = os.path.relpath(file_path, root_dir)

            try:
                with open(file_path, 'r', encoding='utf-8') as f:
                    content = f.read()
                
                has_compose = any(kw in content for kw in target_keywords)
                is_must_include = filename in must_include_files

                if has_compose or is_must_include:
                    found_files.append((rel_path, filename, content, has_compose))
            except Exception as e:
                print(f"Ошибка чтения {rel_path}: {e}")

    with open(output_file, 'w', encoding='utf-8') as out:
        out.write("// === СПИСОК НАЙДЕННЫХ ФАЙЛОВ ===\n")
        for rel_path, _, _, has_compose in found_files:
            tag = "[COMPOSE]" if has_compose else "[CORE/OVERLAY]"
            out.write(f"// {tag} {rel_path}\n")
        out.write("\n")

        for rel_path, filename, content, has_compose in found_files:
            out.write(f"// ==========================================\n")
            out.write(f"// FILE: {rel_path}\n")
            out.write(f"// ==========================================\n\n")
            
            # Для AutoTapOrchestrator берем вторую половину (с 500-й строки), т.к. начало у нас уже есть
            if filename == 'AutoTapOrchestrator.kt':
                lines = content.splitlines()
                out.write("// [Строки 1..500 пропущены для экономии места]\n")
                out.write("\n".join(lines[500:]))
            else:
                out.write(content)
            out.write("\n\n")

    print(f"Сканирование завершено! Найдено файлов: {len(found_files)}")
    print(f"Отчет сохранен в: {os.path.join(root_dir, output_file)}")

if __name__ == "__main__":
    scan_compose_and_overlays()