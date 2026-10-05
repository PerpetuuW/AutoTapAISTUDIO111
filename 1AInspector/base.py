# -*- coding: utf-8 -*-
class BaseCheck:
    def __init__(self, root_dir: str):
        self.root_dir = root_dir

    def run(self) -> tuple[bool, str]:
        raise NotImplementedError
