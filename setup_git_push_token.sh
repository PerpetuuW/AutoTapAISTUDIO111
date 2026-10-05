#!/usr/bin/env bash
# AutoTap - Script for reading GITHUB_TOKEN & GITHUB_USERNAME from .env

if [ -f .env ]; then
    echo "[INFO] Loading configuration from .env..."
    export $(grep -v '^#' .env | xargs)
fi

TOKEN="${1:-$GITHUB_TOKEN}"
USERNAME="${2:-$GITHUB_USERNAME}"

if [ -z "$TOKEN" ]; then
    echo "[ERROR] GITHUB_TOKEN not found in arguments or .env"
    exit 1
fi

export GITHUB_TOKEN="$TOKEN"

if [ -n "$USERNAME" ]; then
    git config --global user.name "$USERNAME"
    git config --global user.email "$USERNAME@users.noreply.github.com"
    git remote set-url origin "https://$USERNAME:$TOKEN@github.com/$USERNAME/autotap.git"
fi

echo "[SUCCESS] GITHUB_TOKEN and GITHUB_USERNAME loaded from .env!"
