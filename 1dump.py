import os

def bundle_kotlin_project(root_dir, output_file):
    # Папки, которые нужно пропустить (кэш, сборка и т.д.)
    exclude_dirs = {'build', '.gradle', '.idea', '.git', 'gradle', 'captures', 'cxx'}
    
    # Расширения и имена важных файлов
    include_extensions = {'.kt', '.kts', '.xml'}
    include_filenames = {'build.gradle', 'settings.gradle', 'gradle.properties'}

    root_dir = os.path.abspath(root_dir)
    files_count = 0

    with open(output_file, 'w', encoding='utf-8') as outfile:
        for dirpath, dirnames, filenames in os.walk(root_dir):
            # Исключаем ненужные папки из обхода
            dirnames[:] = [d for d in dirnames if d not in exclude_dirs]
            
            for filename in filenames:
                # Пропускаем сам выходной файл и скрипт
                if filename in {output_file, os.path.basename(__file__)}:
                    continue
                    
                ext = os.path.splitext(filename)[1].lower()
                if ext in include_extensions or filename in include_filenames:
                    file_path = os.path.join(dirpath, filename)
                    rel_path = os.path.relpath(file_path, root_dir)
                    
                    outfile.write(f"// ==========================================\n")
                    outfile.write(f"// FILE: {rel_path}\n")
                    outfile.write(f"// ==========================================\n\n")
                    
                    try:
                        with open(file_path, 'r', encoding='utf-8') as infile:
                            outfile.write(infile.read())
                        outfile.write("\n\n")
                        files_count += 1
                        print(f"Добавлен файл: {rel_path}")
                    except Exception as e:
                        outfile.write(f"// [ОШИБКА ЧТЕНИЯ ФАЙЛА]: {e}\n\n")
                        print(f"Ошибка при чтении {rel_path}: {e}")

    print(f"\nГотово! Успешно собрано файлов: {files_count}")
    print(f"Результат сохранен в файл: {os.path.join(root_dir, output_file)}")

if __name__ == "__main__":
    # Точка "." означает текущую папку, в которой лежит скрипт 1dump.py
    PROJECT_ROOT = "." 
    OUTPUT_TXT = "kotlin_project_bundle.txt"
    
    bundle_kotlin_project(PROJECT_ROOT, OUTPUT_TXT)