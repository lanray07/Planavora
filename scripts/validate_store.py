"""Check screenshot and composition coverage, dimensions and file integrity."""
import argparse
import csv
from collections import Counter

from PIL import Image

from capture import DESTINATION, LISTINGS, SCENES, SIZES

def validate(root, variants, expected_size=None):
    with (root / "manifest.csv").open(encoding="utf-8", newline="") as file:
        rows = list(csv.DictReader(file))
    selected = [row for row in rows if row["device_variant" if expected_size else "variant"] in variants]
    identities = Counter((row["device_variant"], row["locale"], row["scene"]) if expected_size else (
        row["variant"], row["listing_locale"], row["scene"]) for row in selected)
    expected = Counter((variant, locale, scene) for variant in variants for locale in LISTINGS for scene in SCENES)
    extra = identities - expected
    allowed_qa = Counter((variant, "ar", scene) for variant in variants for scene in SCENES) if not expected_size else Counter()
    if (expected - identities) or (extra - allowed_qa):
        raise ValueError(f"{root.name}: missing {list(expected - identities)}, extra {list(identities - expected)}")
    for row in selected:
        path = root.parent / row["file"] if expected_size else DESTINATION.parents[2] / row["file"]
        with Image.open(path) as image:
            image.load()
            size = (int(row["width"]), int(row["height"]))
            if image.size != size or image.mode != "RGB" or tuple(sorted(size)) not in SIZES:
                raise ValueError(f"{path}: unexpected format {image.mode} {image.size}")
            if expected_size and (image.format != "JPEG" or size != expected_size):
                raise ValueError(f"{path}: wrong composition format")
            if not expected_size and image.format != "PNG":
                raise ValueError(f"{path}: raw capture must be PNG")
    print(f"{root.name}: {len(selected)} verified {', '.join(variants)} images")

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variant", nargs="+", choices=["tablet", "phone"], default=["tablet"])
    args = parser.parse_args()
    validate(DESTINATION, args.variant)
    validate(DESTINATION.parent / "compositions", args.variant, (2560, 1600))
