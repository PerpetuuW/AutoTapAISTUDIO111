#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
anchor_inspector.py — Детерминированный генератор контекстных якорей (делегат 1map_inspector).
"""
import importlib.util
import os
import sys

def main():
    root = os.path.abspath(os.path.dirname(__file__))
    map_path = os.path.join(root, "1map_inspector.py")
    if os.path.exists(map_path):
        spec = importlib.util.spec_from_file_location("map_inspector", map_path)
        if spec and spec.loader:
            mod = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(mod)
            mod.main()

if __name__ == "__main__":
    main()
