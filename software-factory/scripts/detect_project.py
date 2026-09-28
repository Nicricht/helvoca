#!/usr/bin/env python3
from pathlib import Path
from typing import NamedTuple
import json
import sys


class ProjectDetection(NamedTuple):
    stacks: tuple[str, ...]
    requires_config: bool


MARKERS = (
    ("maven", ("pom.xml",)),
    ("gradle", ("build.gradle", "build.gradle.kts")),
    ("node", ("package.json",)),
    ("python", ("pyproject.toml",)),
    ("go", ("go.mod",)),
    ("rust", ("Cargo.toml",)),
)


def detect_project(root: Path) -> ProjectDetection:
    root = Path(root)
    stacks = []
    for stack, filenames in MARKERS:
        if any((root / filename).exists() for filename in filenames):
            stacks.append(stack)
    unique = tuple(dict.fromkeys(stacks))
    return ProjectDetection(unique, len(unique) != 1)


def main() -> int:
    root = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
    result = detect_project(root)
    print(json.dumps({
        "root": str(root),
        "stacks": list(result.stacks),
        "requires_config": result.requires_config,
    }))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
