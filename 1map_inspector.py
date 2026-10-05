#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
CODE INSPECTOR PRO (v35.0 ARCHITECTURE & PATCHING DIAGNOSTIC)
=============================================================
Инструмент для:
 1. Картирования архитектуры монолитов (методы, оверлеи, потоки, структуры).
 2. Точного определения точек вставки и целевой глубины [D:depth].
 3. Автоматической генерации верхних и нижних якорей для дифф-патчей.
 4. Валидации баланса фигурных скобок и целостности границ.
"""

import os
import re
import sys
import json
from dataclasses import dataclass, field, asdict
from typing import List, Optional, Dict

@dataclass
class CodeSymbol:
    kind: str           # "CLASS", "ENUM", "FUNCTION", "COMPANION", "PROPERTY"
    name: str           # Имя функции / класса / структуры
    start_line: int     # Номер строки начала объявления
    end_line: int       # Номер закрывающей скобки
    depth: int          # Целевая глубина блока [D:depth]
    signature: str      # Сигнатура объявления
    category: str       # Функциональная категория
    parent: Optional[str] = None

@dataclass
class AnchorContext:
    target_symbol: str
    target_depth: int
    insert_after_line: int
    top_anchor: List[str]
    bottom_anchor: List[str]

class KotlinInspector:
    def __init__(self, file_path: str):
        self.file_path = file_path
        self.file_name = os.path.basename(file_path)
        self.lines: List[str] = []
        self.depth_map: List[int] = []
        self.symbols: List[CodeSymbol] = []
        self.total_lines = 0

    def load_and_index(self) -> bool:
        if not os.path.exists(self.file_path):
            print(f"[!] Ошибка: Файл '{self.file_path}' не найден.")
            return False

        with open(self.file_path, "r", encoding="utf-8", errors="replace") as f:
            self.lines = [line.rstrip("\r\n") for line in f.readlines()]

        self.total_lines = len(self.lines)
        if self.total_lines == 0:
            print("[!] Ошибка: Файл пуст.")
            return False

        # Расчет сквозной глубины [D:depth]
        current_depth = 0
        self.depth_map = []
        for line in self.lines:
            opens = line.count("{")
            closes = line.count("}")
            stripped = line.lstrip()
            
            display_depth = current_depth
            if stripped.startswith("}"):
                display_depth = max(0, current_depth - 1)
            
            self.depth_map.append(display_depth)
            current_depth += (opens - closes)
            if current_depth < 0:
                current_depth = 0

        return True

    def _categorize_symbol(self, name: str, sig: str) -> str:
        sig_lower = (name + " " + sig).lower()
        if any(w in sig_lower for w in ["cv", "template", "cascade", "heatmap", "mask", "ocr", "scan"]):
            return "Computer Vision & AI"
        if any(w in sig_lower for w in ["gesture", "click", "swipe", "tap", "human", "inject"]):
            return "Gestures & Input Simulation"
        if any(w in sig_lower for w in ["overlay", "panel", "dialog", "view", "hud", "bar", "touch", "joystick"]):
            return "UI Overlays & Interaction"
        if any(w in sig_lower for w in ["script", "json", "export", "load", "save", "trash"]):
            return "Storage & Script Engine"
        if any(w in sig_lower for w in ["log", "watchdog", "vibrat", "wakelock", "diagnostic"]):
            return "System, Diagnostics & Lifecycle"
        return "Core / Service Engine"

    def parse_symbols(self):
        """Парсинг методов, классов и ключевых блоков с определением границ."""
        self.symbols.clear()
        
        fun_pattern = re.compile(r'^\s*(?:override\s+|private\s+|public\s+|protected\s+|internal\s+)*fun\s+([a-zA-Z0-9_]+)\s*(\(.*?\))?')
        class_pattern = re.compile(r'^\s*(?:data\s+|enum\s+|sealed\s+|open\s+|abstract\s+)*class\s+([a-zA-Z0-9_]+)')
        companion_pattern = re.compile(r'^\s*companion\s+object')
        enum_pattern = re.compile(r'^\s*enum\s+class\s+([a-zA-Z0-9_]+)')

        i = 0
        while i < self.total_lines:
            line = self.lines[i]
            depth = self.depth_map[i]
            
            # Пропуск пустых строк и однострочных комментариев
            if not line.strip() or line.strip().startswith("//"):
                i += 1
                continue

            # 1. Поиск классов / enum
            class_match = class_pattern.search(line)
            if class_match:
                name = class_match.group(1)
                kind = "CLASS"
                if enum_pattern.search(line):
                    kind = "ENUM"
                end_line = self._find_block_end(i)
                cat = self._categorize_symbol(name, line)
                self.symbols.append(CodeSymbol(kind, name, i + 1, end_line + 1, depth, line.strip(), cat))
                i += 1
                continue

            # 2. Поиск Companion Object
            if companion_pattern.search(line):
                end_line = self._find_block_end(i)
                self.symbols.append(CodeSymbol("COMPANION", "companion object", i + 1, end_line + 1, depth, line.strip(), "Core / Static Singleton"))
                i += 1
                continue

            # 3. Поиск функций / методов
            fun_match = fun_pattern.search(line)
            if fun_match:
                name = fun_match.group(1)
                end_line = self._find_block_end(i)
                cat = self._categorize_symbol(name, line)
                self.symbols.append(CodeSymbol(kind="FUNCTION", name=name, start_line=i + 1, end_line=end_line + 1, depth=depth, signature=line.strip(), category=cat))
                i += 1
                continue

            i += 1

    def _find_block_end(self, start_idx: int) -> int:
        """Находит строку с парной закрывающей скобкой для блока."""
        balance = 0
        found_first_brace = False
        for idx in range(start_idx, self.total_lines):
            line = self.lines[idx]
            opens = line.count("{")
            closes = line.count("}")
            if opens > 0:
                found_first_brace = True
            balance += (opens - closes)
            if found_first_brace and balance <= 0:
                return idx
        return self.total_lines - 1

    def generate_anchor_sandwich(self, target_symbol_name: str, anchor_size: int = 10) -> Optional[AnchorContext]:
        """Генерирует готовый контекстный сэндвич ДО и ПОСЛЕ целевого метода."""
        sym_idx = next((i for i, s in enumerate(self.symbols) if s.name == target_symbol_name), None)
        if sym_idx is None:
            print(f"[!] Символ '{target_symbol_name}' не найден в карте.")
            return None

        target_sym = self.symbols[sym_idx]
        cut_line = target_sym.end_line  # Строка закрывающей скобки

        # Верхний якорь (строки до точки вставки)
        top_start = max(0, cut_line - anchor_size)
        top_lines = [
            f"{idx + 1:4d} | [D:{self.depth_map[idx]}] | {self.lines[idx]}"
            for idx in range(top_start, cut_line)
        ]

        # Нижний якорь (строки после точки вставки)
        bottom_end = min(self.total_lines, cut_line + anchor_size)
        bottom_lines = [
            f"{idx + 1:4d} | [D:{self.depth_map[idx]}] | {self.lines[idx]}"
            for idx in range(cut_line, bottom_end)
        ]

        return AnchorContext(
            target_symbol=target_sym.name,
            target_depth=target_sym.depth,
            insert_after_line=cut_line,
            top_anchor=top_lines,
            bottom_anchor=bottom_lines
        )

    def print_functional_map(self):
        """Вывод карты методов и функционала приложения."""
        print(f"\n{'='*90}")
        print(f" КАРТА ФУНКЦИОНАЛА: {self.file_name} (Всего строк: {self.total_lines})")
        print(f"{'='*90}")
        print(f"{'ТИП':<10} | {'ГЛУБИНА':<7} | {'СТРОКИ':<14} | {'ИМЯ СИМВОЛА':<32} | {'КАТЕГОРИЯ'}")
        print(f"{'-'*90}")

        for s in self.symbols:
            lines_range = f"{s.start_line}..{s.end_line}"
            depth_str = f"[D:{s.depth}]"
            print(f"{s.kind:<10} | {depth_str:<7} | {lines_range:<14} | {s.name:<32} | {s.category}")

        print(f"{'='*90}\n")

    def print_patch_recipe(self, symbol_name: str):
        """Формирует шаблон дифф-патча по стандарту DPP v35.0."""
        anchor = self.generate_anchor_sandwich(symbol_name)
        if not anchor:
            return

        print("\n" + "="*80)
        print(f" ГОТОВЫЙ ДИФФ-ШАБЛОН ДЛЯ ВСТАВКИ ПОСЛЕ '{symbol_name}'")
        print("="*80)
        print(f"// ============================================================================")
        print(f"// PATCH: [Новый метод / функционал]")
        print(f"// FILE: {self.file_path}")
        print(f"// INSERTION POINT: После строки {anchor.insert_after_line}")
        print(f"// TARGET DEPTH: [D:{anchor.target_depth}]")
        print(f"// ============================================================================\n")
        
        print("// --- [ВЕРХНИЙ ЯКОРЬ / ДО ТОЧКИ ВСТАВКИ] ---")
        for line in anchor.top_anchor:
            print(f"// {line}")

        print("\n// --- [НАЧАЛО ИЗМЕНЕНИЙ] ---")
        print("<<<<<<< SEARCH")
        print(f"    // Вставка нового функционала на глубине [D:{anchor.target_depth}]")
        print("=======")
        print(f"    fun yourNewMethodHere() {{")
        print(f"        // В теле метода глубина будет [D:{anchor.target_depth + 1}]")
        print(f"    }}")
        print(">>>>>>>")
        print("// --- [КОНЕЦ ИЗМЕНЕНИЙ] ---\n")

        print("// --- [НИЖНИЙ ЯКОРЬ / ПОСЛЕ ТОЧКИ ВСТАВКИ] ---")
        for line in anchor.bottom_anchor:
            print(f"// {line}")
        print("\n" + "="*80 + "\n")

    def export_blueprint(self, output_path: str = "app_blueprint.json"):
        """Экспорт полной карты в JSON для авто-скриптов и валидаторов."""
        data = {
            "file": self.file_path,
            "total_lines": self.total_lines,
            "symbols_count": len(self.symbols),
            "symbols": [asdict(s) for s in self.symbols]
        }
        with open(output_path, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2)
        print(f"[✓] Архитектурная карта сохранена в '{output_path}'.")

def main():
    target_file = sys.argv[1] if len(sys.argv) > 1 else "app/src/main/java/com/example/autotap/MyAutoClickService.kt"
    
    inspector = KotlinInspector(target_file)
    if not inspector.load_and_index():
        return

    inspector.parse_symbols()
    
    if len(sys.argv) > 2:
        cmd = sys.argv[2]
        if cmd == "--map":
            inspector.print_functional_map()
        elif cmd == "--patch-after" and len(sys.argv) > 3:
            target_symbol = sys.argv[3]
            inspector.print_patch_recipe(target_symbol)
        elif cmd == "--export":
            inspector.export_blueprint()
    else:
        inspector.print_functional_map()
        inspector.export_blueprint()
        print("[ℹ] Подсказка по вызовам:")
        print("  python 1map_inspector.py <файл> --map                  # Вывести карту методов")
        print("  python 1map_inspector.py <файл> --patch-after <метод>  # Сгенерировать якоря для вставки")
        print("  python 1map_inspector.py <файл> --export               # Экспорт структуры в JSON")

if __name__ == "__main__":
    main()