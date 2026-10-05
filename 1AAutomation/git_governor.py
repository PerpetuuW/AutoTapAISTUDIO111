# -*- coding: utf-8 -*-
import subprocess
import os

class GitGovernor:
    @staticmethod
    def rollback_files(files: list[str], root_dir: str):
        if not files:
            return
        try:
            cmd = ["git", "restore"] + files
            subprocess.run(cmd, cwd=root_dir, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
        except Exception:
            try:
                cmd_old = ["git", "checkout", "--"] + files
                subprocess.run(cmd_old, cwd=root_dir, check=False, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
            except Exception:
                pass

    @staticmethod
    def get_modified_files(root_dir: str) -> list[str]:
        try:
            res = subprocess.run(["git", "status", "--porcelain"], cwd=root_dir, capture_output=True, text=True, check=True)
            files = []
            for line in res.stdout.splitlines():
                parts = line.strip().split()
                if len(parts) >= 2:
                    files.append(parts[-1])
            return files
        except Exception:
            return []
