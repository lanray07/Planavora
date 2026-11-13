"""Inventory packaged deliverables with SHA-256 checksums."""
import csv
import hashlib
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1] / "deliverables"
TARGET = ROOT / "DELIVERABLES_MANIFEST.csv"

with TARGET.open("w", newline="", encoding="utf-8") as manifest:
    out = csv.writer(manifest, lineterminator="\n")
    out.writerow(["file", "bytes", "sha256", "git_tracked"])
    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or path == TARGET or path.name == "fire-screen.png":
            continue
        relative = path.relative_to(ROOT)
        digest = hashlib.sha256(path.read_bytes()).hexdigest()
        out.writerow([relative.as_posix(), path.stat().st_size, digest, "no" if relative.parts[0] == "android" else "yes"])
