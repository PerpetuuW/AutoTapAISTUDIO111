@echo off
chcp 65001 >nul
:: AutoTap - Script for reading GITHUB_TOKEN and GITHUB_USERNAME from .env

echo ====================================================
echo AutoTap: GitHub Token Setup from .env
echo ====================================================
echo.

set "TOKEN=%~1"
set "USERNAME=%~2"

if "%TOKEN%"=="" (
    if exist ".env" (
        echo [INFO] Чтение параметров из файла .env...
        for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
            if "%%A"=="GITHUB_TOKEN" set "TOKEN=%%B"
            if "%%A"=="GITHUB_USERNAME" set "USERNAME=%%B"
        )
    )
)

if "%TOKEN%"=="" (
    echo [ОШИБКА] GITHUB_TOKEN не найден ни в аргументах, ни в файле .env!
    echo Добавьте в файл .env строку: GITHUB_TOKEN=ghp_...
    pause
    exit /b 1
)

echo [1/3] Установка переменной среды GITHUB_TOKEN...
setx GITHUB_TOKEN "%TOKEN%" >nul
set "GITHUB_TOKEN=%TOKEN%"

if not "%USERNAME%"=="" (
    echo [2/3] Настройка пользователя Git: %USERNAME%...
    git config --global user.name "%USERNAME%"
    git config --global user.email "%USERNAME%@users.noreply.github.com"
    echo [3/3] Настройка remote URL для https://github.com/%USERNAME%/autotap.git ...
    git remote set-url origin "https://%USERNAME%:%TOKEN%@github.com/%USERNAME%/autotap.git"
) else (
    echo [2/3] Настройка пользователя Git по умолчанию...
    git config --global user.name "AutoTap Developer"
    git config --global user.email "developer@autotap.local"
)

echo.
echo [УСПЕХ] GITHUB_TOKEN и GITHUB_USERNAME успешно подгружены из .env!
echo Теперь вы можете выполнять git push без ввода паролей.
echo.
pause
