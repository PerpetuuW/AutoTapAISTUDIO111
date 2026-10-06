#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
AutoTap Secret Vault
Утилита для удобного шифрования/расшифровки файла .env с помощью вашего личного пароля.
Поддерживает:
  1. 'age' (если установлен)
  2. Встроенный 'openssl' (системный или из Conda/Git на Windows)
  3. Модуль 'cryptography' (чистый Python, если установлен в Conda)

Использование на Windows:
  python tools/secret_vault.py encrypt   (или python secret_vault.py encrypt)
  python tools/secret_vault.py decrypt   (или python secret_vault.py decrypt)

Использование на Linux/macOS:
  python3 tools/secret_vault.py encrypt
  python3 tools/secret_vault.py decrypt
"""

import sys
import os
import shutil
import subprocess
import getpass

def find_root_dir() -> str:
    # 1. Проверяем текущую директорию скрипта
    script_dir = os.path.dirname(os.path.abspath(__file__))
    if os.path.exists(os.path.join(script_dir, ".env.example")) or os.path.exists(os.path.join(script_dir, "settings.gradle.kts")):
        return script_dir
    # 2. Проверяем родительскую директорию (если скрипт в tools/)
    parent_dir = os.path.dirname(script_dir)
    if os.path.exists(os.path.join(parent_dir, ".env.example")) or os.path.exists(os.path.join(parent_dir, "settings.gradle.kts")):
        return parent_dir
    # 3. Проверяем текущую рабочую директорию терминала
    cwd = os.getcwd()
    if os.path.exists(os.path.join(cwd, ".env.example")) or os.path.exists(os.path.join(cwd, "settings.gradle.kts")):
        return cwd
    return parent_dir

ROOT_DIR = find_root_dir()
ENV_FILE = os.path.join(ROOT_DIR, ".env")
ENC_FILE = os.path.join(ROOT_DIR, ".env.enc")

def find_openssl_bin() -> str | None:
    found = shutil.which("openssl")
    if found:
        return found
    if sys.platform == "win32":
        # Проверяем conda Library/bin
        conda_openssl = os.path.join(sys.prefix, "Library", "bin", "openssl.exe")
        if os.path.exists(conda_openssl):
            return conda_openssl
        # Проверяем Git for Windows
        for git_path in [
            r"C:\Program Files\Git\usr\bin\openssl.exe",
            r"C:\Program Files\Git\bin\openssl.exe",
            r"C:\Program Files (x86)\Git\usr\bin\openssl.exe"
        ]:
            if os.path.exists(git_path):
                return git_path
    return None

def has_crypto_lib() -> bool:
    try:
        import cryptography  # noqa: F401
        return True
    except ImportError:
        return False

def encrypt_with_cryptography(pwd: str):
    import base64
    from cryptography.fernet import Fernet
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

    with open(ENV_FILE, "rb") as f:
        data = f.read()

    salt = os.urandom(16)
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=100000,
    )
    key = base64.urlsafe_b64encode(kdf.derive(pwd.encode("utf-8")))
    fernet = Fernet(key)
    token = fernet.encrypt(data)

    # Записываем MAGIC + salt + encrypted token
    with open(ENC_FILE, "wb") as f:
        f.write(b"AUTOTAP_VAULT_V1\n" + salt + b"\n" + token)

def decrypt_with_cryptography(pwd: str) -> bool:
    import base64
    from cryptography.fernet import Fernet, InvalidToken
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC

    with open(ENC_FILE, "rb") as f:
        content = f.read()

    parts = content.split(b"\n", 2)
    if len(parts) < 3 or parts[0] != b"AUTOTAP_VAULT_V1":
        return False

    salt = parts[1]
    token = parts[2]

    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=32,
        salt=salt,
        iterations=100000,
    )
    key = base64.urlsafe_b64encode(kdf.derive(pwd.encode("utf-8")))
    fernet = Fernet(key)
    try:
        decrypted = fernet.decrypt(token)
        with open(ENV_FILE, "wb") as f:
            f.write(decrypted)
        return True
    except InvalidToken:
        return False

def encrypt():
    if not os.path.exists(ENV_FILE):
        print(f"[ОШИБКА] Файл .env не найден в корне проекта: {ENV_FILE}")
        print("Создайте файл .env и добавьте ваши токены перед шифрованием.")
        sys.exit(1)

    print("=== ШИФРОВАНИЕ СЕКРЕТОВ (.env -> .env.enc) ===")
    
    if shutil.which("age"):
        print("[ИНФО] Используется утилита 'age' (scrypt + ChaCha20-Poly1305)")
        cmd = ["age", "-p", "-o", ENC_FILE, ENV_FILE]
        res = subprocess.run(cmd)
        if res.returncode == 0:
            print(f"[УСПЕХ] Зашифрованный файл создан: {ENC_FILE}")
            print("Теперь можно выполнить: git add .env.enc && git commit -m 'Update secrets'")
            return
        else:
            print("[ОШИБКА] Не удалось зашифровать файл с помощью age.")
            sys.exit(res.returncode)

    openssl_bin = find_openssl_bin()
    if openssl_bin:
        print(f"[ИНФО] Используется OpenSSL ({openssl_bin})")
        pwd = getpass.getpass("Введите проверенный пароль для шифрования: ")
        pwd_confirm = getpass.getpass("Повторите пароль: ")
        if pwd != pwd_confirm:
            print("[ОШИБКА] Пароли не совпадают!")
            sys.exit(1)
        if not pwd:
            print("[ОШИБКА] Пароль не может быть пустым!")
            sys.exit(1)
            
        cmd = [
            openssl_bin, "enc", "-aes-256-cbc", "-pbkdf2", "-iter", "100000",
            "-salt", "-in", ENV_FILE, "-out", ENC_FILE, "-pass", f"pass:{pwd}"
        ]
        res = subprocess.run(cmd)
        if res.returncode == 0:
            print(f"[УСПЕХ] Зашифрованный файл создан: {ENC_FILE}")
            print("Теперь можно выполнить: git add .env.enc && git commit -m 'Update secrets'")
            return
        else:
            print("[ОШИБКА] Сбой при шифровании через openssl.")
            sys.exit(res.returncode)

    if has_crypto_lib():
        print("[ИНФО] Используется встроенный модуль Python 'cryptography' (AES-128-CBC + HMAC-SHA256)")
        pwd = getpass.getpass("Введите проверенный пароль для шифрования: ")
        pwd_confirm = getpass.getpass("Повторите пароль: ")
        if pwd != pwd_confirm:
            print("[ОШИБКА] Пароли не совпадают!")
            sys.exit(1)
        encrypt_with_cryptography(pwd)
        print(f"[УСПЕХ] Зашифрованный файл создан: {ENC_FILE}")
        print("Теперь можно выполнить: git add .env.enc && git commit -m 'Update secrets'")
        return

    print("[ОШИБКА] Не найдено средств шифрования. Установите 'age' (winget install FiloSottile.age) или OpenSSL.")
    sys.exit(1)

def decrypt():
    if not os.path.exists(ENC_FILE):
        print(f"[ОШИБКА] Зашифрованный файл .env.enc не найден: {ENC_FILE}")
        sys.exit(1)

    print("=== РАСШИФРОВКА СЕКРЕТОВ (.env.enc -> .env) ===")

    # Проверяем, зашифрован ли файл через модуль cryptography
    with open(ENC_FILE, "rb") as f:
        header = f.read(17)
    if header == b"AUTOTAP_VAULT_V1\n":
        if has_crypto_lib():
            pwd = getpass.getpass("Введите ваш проверенный пароль: ")
            if decrypt_with_cryptography(pwd):
                print(f"[УСПЕХ] Файл .env успешно расшифрован в: {ENV_FILE}")
                return
            else:
                print("[ОШИБКА] Неверный пароль или поврежденный файл.")
                sys.exit(1)

    if shutil.which("age"):
        print("[ИНФО] Попытка расшифровки через 'age'...")
        cmd = ["age", "-d", "-o", ENV_FILE, ENC_FILE]
        res = subprocess.run(cmd)
        if res.returncode == 0:
            print(f"[УСПЕХ] Файл .env успешно расшифрован в: {ENV_FILE}")
            return

    openssl_bin = find_openssl_bin()
    if openssl_bin:
        print(f"[ИНФО] Попытка расшифровки через OpenSSL ({openssl_bin})...")
        pwd = getpass.getpass("Введите ваш проверенный пароль: ")
        cmd = [
            openssl_bin, "enc", "-d", "-aes-256-cbc", "-pbkdf2", "-iter", "100000",
            "-in", ENC_FILE, "-out", ENV_FILE, "-pass", f"pass:{pwd}"
        ]
        res = subprocess.run(cmd)
        if res.returncode == 0:
            print(f"[УСПЕХ] Файл .env успешно расшифрован в: {ENV_FILE}")
            return
        else:
            print("[ОШИБКА] Неверный пароль или поврежденный файл.")
            sys.exit(1)

    print("[ОШИБКА] Не удалось расшифровать файл. Проверьте пароль или установите 'age'.")
    sys.exit(1)

if __name__ == "__main__":
    if len(sys.argv) < 2 or sys.argv[1] not in ("encrypt", "decrypt"):
        print("Использование:")
        print("  python tools/secret_vault.py encrypt")
        print("  python tools/secret_vault.py decrypt")
        sys.exit(1)
        
    action = sys.argv[1]
    if action == "encrypt":
        encrypt()
    elif action == "decrypt":
        decrypt()
