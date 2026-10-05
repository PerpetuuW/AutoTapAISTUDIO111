# -*- coding: utf-8 -*-
"""
1AAutomation/clipboard_sync.py
Нативный модуль взаимодействия с системным буфером обмена Windows через 64-битный Win32 API.
Гарантирует 100% обратную совместимость со всеми историческими вызовами.
"""

import sys
import ctypes
from ctypes import wintypes
import subprocess

def copy_to_clipboard_win32_64(text: str) -> bool:
    if not isinstance(text, str):
        text = str(text)
    if sys.platform == "win32":
        try:
            user32 = ctypes.windll.user32
            kernel32 = ctypes.windll.kernel32

            GMEM_MOVEABLE = 0x0002
            CF_UNICODETEXT = 13

            kernel32.GlobalAlloc.restype = ctypes.c_void_p
            kernel32.GlobalAlloc.argtypes = [wintypes.UINT, ctypes.c_size_t]
            kernel32.GlobalLock.restype = ctypes.c_void_p
            kernel32.GlobalLock.argtypes = [ctypes.c_void_p]
            kernel32.GlobalUnlock.argtypes = [ctypes.c_void_p]
            user32.SetClipboardData.restype = ctypes.c_void_p
            user32.SetClipboardData.argtypes = [wintypes.UINT, ctypes.c_void_p]

            if user32.OpenClipboard(None):
                user32.EmptyClipboard()
                data = text.encode("utf-16le") + b"\x00\x00"
                h_mem = kernel32.GlobalAlloc(GMEM_MOVEABLE, len(data))
                if h_mem:
                    p_mem = kernel32.GlobalLock(h_mem)
                    if p_mem:
                        ctypes.memmove(p_mem, data, len(data))
                        kernel32.GlobalUnlock(h_mem)
                        user32.SetClipboardData(CF_UNICODETEXT, h_mem)
                user32.CloseClipboard()
                return True
        except Exception:
            pass

    try:
        p = subprocess.Popen(["clip"], stdin=subprocess.PIPE)
        p.communicate(input=text.encode("utf-16le"))
        return True
    except Exception:
        return False

def get_clipboard_text() -> str:
    if sys.platform == "win32":
        try:
            user32 = ctypes.windll.user32
            kernel32 = ctypes.windll.kernel32
            CF_UNICODETEXT = 13

            kernel32.GlobalLock.restype = ctypes.c_void_p
            kernel32.GlobalLock.argtypes = [ctypes.c_void_p]
            kernel32.GlobalUnlock.argtypes = [ctypes.c_void_p]
            user32.GetClipboardData.restype = ctypes.c_void_p
            user32.GetClipboardData.argtypes = [wintypes.UINT]

            if user32.OpenClipboard(None):
                h_mem = user32.GetClipboardData(CF_UNICODETEXT)
                if h_mem:
                    p_mem = kernel32.GlobalLock(h_mem)
                    if p_mem:
                        text = ctypes.wstring_at(p_mem)
                        kernel32.GlobalUnlock(h_mem)
                        user32.CloseClipboard()
                        return text
                user32.CloseClipboard()
        except Exception:
            pass
    return ""

# Цепочка алиасов для 100% обратной совместимости
copy_to_clipboard = copy_to_clipboard_win32_64
copy_text = copy_to_clipboard_win32_64
copy_to_clipboard_safe = copy_to_clipboard_win32_64
