"""Run on a QA device with Planavora debug and instrumentation APKs installed.

The screenshot test replaces Planavora's local QA data with fictional records.
"""
import argparse
import csv
from pathlib import Path
import subprocess

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "deliverables/store-assets/screenshots"
SCENES = ("01-command-centre", "02-budget", "03-rsvps", "04-planning-assistant", "05-voice",
          "06-vendors", "07-timeline", "08-reports")
LISTINGS = {
    "en-US": "en-US", "en-GB": "en-GB", "en-AU": "en-AU", "en-IN": "en-GB",
    "fr-FR": "fr-FR", "es-ES": "es-ES", "es-MX": "es-419", "de-DE": "de-DE",
    "it-IT": "it-IT", "ja-JP": "ja-JP", "nl-NL": "nl-NL", "pl-PL": "pl-PL",
    "pt-BR": "pt-BR", "pt-PT": "pt-PT", "sv-SE": "sv-SE",
}
SIZES = {(480, 800), (600, 1024), (720, 1280), (800, 1280), (1080, 1920), (1200, 1920), (1600, 2560)}

def run(adb, serial, listing, variant):
    locale = LISTINGS.get(listing, listing)
    command = [adb] + (["-s", serial] if serial else [])
    result = subprocess.run(command + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
        "com.planavora.app.FireJourneyTest#capturesEightRealScreens", "-e", "captureLocale", locale,
        "com.planavora.app.test/androidx.test.runner.AndroidJUnitRunner"], capture_output=True, check=True, timeout=160)
    log = result.stdout.decode(errors="replace")
    print(listing, log, flush=True)
    if "OK (1 test)" not in log:
        return False
    folder = DESTINATION / variant / listing
    folder.mkdir(parents=True, exist_ok=True)
    for scene in SCENES:
        path = folder / (scene + ".png")
        path.write_bytes(subprocess.check_output(command + ["exec-out", "run-as", "com.planavora.app",
            "cat", f"files/capture/{locale}/{scene}.png"]))
        with Image.open(path) as image:
            rgb = image.convert("RGB")
        if tuple(sorted(rgb.size)) not in SIZES:
            raise ValueError(f"{path} has unsupported size {rgb.size}")
        rgb.save(path, optimize=True)
    return True

def manifest():
    DESTINATION.mkdir(parents=True, exist_ok=True)
    with (DESTINATION / "manifest.csv").open("w", newline="", encoding="utf-8") as file:
        out = csv.writer(file)
        out.writerow(["variant", "listing_locale", "app_locale", "scene", "width", "height", "file"])
        for path in sorted(DESTINATION.glob("*/*/*.png")):
            variant, listing, name = path.relative_to(DESTINATION).parts
            with Image.open(path) as image:
                out.writerow([variant, listing, LISTINGS.get(listing, listing), name.removesuffix(".png"),
                              *image.size, path.relative_to(ROOT).as_posix()])

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial")
    parser.add_argument("--variant", choices=["tablet", "phone"], default="tablet")
    parser.add_argument("--locale", nargs="+", default=list(LISTINGS))
    args = parser.parse_args()
    failures = []
    for listing in args.locale:
        if not run(args.adb, args.serial, listing, args.variant):
            failures.append(listing)
    manifest()
    if failures:
        raise SystemExit("Failed captures: " + ", ".join(failures))
