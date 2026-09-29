#!/usr/bin/env python3
from pathlib import Path
from typing import NamedTuple
import json
import os
import shlex
import subprocess
import sys

SCRIPT_DIR = Path(__file__).resolve().parent
if str(SCRIPT_DIR) not in sys.path:
    sys.path.insert(0, str(SCRIPT_DIR))

from detect_project import detect_project


class VerificationConfig(NamedTuple):
    commands: dict[str, list[list[str]]]
    forbidden_real_effect_markers: list[str]
    allow_real_external_effects: bool
    source: str


DEFAULT_COMMANDS = {
    "maven": {
        "low": [["mvn", "--batch-mode", "--no-transfer-progress", "-DskipTests", "compile"]],
        "medium": [["mvn", "--batch-mode", "--no-transfer-progress", "test"]],
        "high": [],
    },
    "gradle": {
        "low": [["./gradlew", "classes"]],
        "medium": [["./gradlew", "test"]],
        "high": [],
    },
    "node": {
        "low": [["npm", "run", "lint", "--if-present"]],
        "medium": [["npm", "test"]],
        "high": [],
    },
    "python": {
        "low": [["python3", "-m", "compileall", "-q", "."]],
        "medium": [["python3", "-m", "unittest", "discover"]],
        "high": [],
    },
    "go": {
        "low": [["go", "vet", "./..."]],
        "medium": [["go", "test", "./..."]],
        "high": [],
    },
    "rust": {
        "low": [["cargo", "check"]],
        "medium": [["cargo", "test"]],
        "high": [],
    },
}

RISK_ORDER = ("LOW", "MEDIUM", "HIGH")


def _normalize_commands(raw: dict) -> dict[str, list[list[str]]]:
    normalized = {"low": [], "medium": [], "high": []}
    for level in normalized:
        for command in raw.get(level, []):
            if isinstance(command, str):
                normalized[level].append(shlex.split(command))
            elif isinstance(command, list) and all(isinstance(part, str) for part in command):
                normalized[level].append(command)
            else:
                raise ValueError(f"Invalid command entry for {level}: {command!r}")
    return normalized


def load_verification_config(root: Path) -> VerificationConfig:
    root = Path(root)
    config_path = root / ".software-factory.json"
    if config_path.exists():
        data = json.loads(config_path.read_text(encoding="utf-8"))
        return VerificationConfig(
            commands=_normalize_commands(data.get("commands", {})),
            forbidden_real_effect_markers=list(data.get("forbidden_real_effect_markers", [])),
            allow_real_external_effects=bool(data.get("allow_real_external_effects", False)),
            source="repository",
        )

    detection = detect_project(root)
    if detection.requires_config:
        detected = ", ".join(detection.stacks) if detection.stacks else "unknown"
        raise ValueError(
            f"Project stack is {detected}; add local verification configuration in .software-factory.json "
            "instead of relying on guessed commands."
        )

    stack = detection.stacks[0]
    return VerificationConfig(
        commands=DEFAULT_COMMANDS[stack],
        forbidden_real_effect_markers=[],
        allow_real_external_effects=False,
        source=f"default:{stack}",
    )


def verification_commands(config: VerificationConfig, risk: str) -> list[list[str]]:
    normalized_risk = risk.upper()
    if normalized_risk not in RISK_ORDER:
        raise ValueError(f"Unknown risk level {risk!r}; expected LOW, MEDIUM, or HIGH.")

    selected = []
    max_index = RISK_ORDER.index(normalized_risk)
    for level in RISK_ORDER[: max_index + 1]:
        selected.extend(config.commands.get(level.lower(), []))

    if not config.allow_real_external_effects:
        for command in selected:
            rendered = " ".join(command)
            for marker in config.forbidden_real_effect_markers:
                if marker and marker in rendered:
                    raise ValueError(
                        f"Command contains forbidden real-effect marker {marker!r}: {rendered}"
                    )
    return selected


def main() -> int:
    root = Path(os.getenv("SOFTWARE_FACTORY_ROOT", ".")).resolve()
    risk = os.getenv("FIRST_PASS_RISK", "MEDIUM")
    try:
        config = load_verification_config(root)
        commands = verification_commands(config, risk)
    except (ValueError, json.JSONDecodeError) as exc:
        print(f"Software Factory configuration error: {exc}", file=sys.stderr)
        return 2

    print(f"Software Factory risk={risk.upper()} source={config.source}")
    if not commands:
        print("No verification commands selected.")
        return 0

    for command in commands:
        print("+ " + shlex.join(command), flush=True)
        completed = subprocess.run(command, cwd=root)
        if completed.returncode != 0:
            return completed.returncode
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
