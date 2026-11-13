"""Compose editable store layouts from real captures and locale-specific copy."""
import argparse
import csv
import json
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1] / "deliverables/store-assets"
TEMPLATE = Path(__file__).with_name("store_layout.json")

def compose(source, target, copy, layout, font_dir, locale):
    colors = layout["colors"]
    width, height = layout["size"]
    art = Image.new("RGB", (width, height), colors["background"])
    draw = ImageDraw.Draw(art)
    font_set = layout["fonts"]["ja" if locale.startswith("ja") else "default"]

    def fit(text, font_name, size, minimum):
        for point in range(size, minimum - 1, -2):
            font = ImageFont.truetype(str(font_dir / font_name), point)
            if draw.textlength(text, font) <= width - 240:
                return font
        raise ValueError(f"Text does not fit: {text!r}")

    for key, y, size, minimum, color, font_name in [
        ("headline", 63, 83, 42, colors["headline"], font_set[0]),
        ("caption", 165, 43, 30, colors["caption"], font_set[1]),
    ]:
        content = copy[key]
        draw.text((width // 2, y), content, fill=color, font=fit(content, font_name, size, minimum), anchor="mt")
    with Image.open(source) as raw:
        screen = raw.convert("RGB")
    screen.thumbnail(tuple(layout["screenshot_size"]), Image.Resampling.LANCZOS)
    left = (width - screen.width) // 2
    top = layout["screenshot_top"]
    draw.rounded_rectangle((left - 10, top - 10, left + screen.width + 10, top + screen.height + 10), radius=14,
                           fill=colors["outline"])
    art.paste(screen, (left, top))
    target.parent.mkdir(parents=True, exist_ok=True)
    art.save(target, quality=91, optimize=True, subsampling=0)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--fonts", type=Path, default=Path("C:/Windows/Fonts"))
    args = parser.parse_args()
    layout = json.loads(TEMPLATE.read_text(encoding="utf-8"))
    manifest = ROOT / "compositions"
    manifest.mkdir(parents=True, exist_ok=True)
    with (manifest / "manifest.csv").open("w", newline="", encoding="utf-8") as file:
        out = csv.writer(file)
        out.writerow(["device_variant", "locale", "scene", "width", "height", "file"])
        for folder in sorted((ROOT / "screenshots").glob("*/*")):
            listing = ROOT / "metadata" / (folder.name + ".json")
            if not listing.is_file():
                continue
            copy = json.loads(listing.read_text(encoding="utf-8"))["screenshot_copy"]
            for image in sorted(folder.glob("*.png")):
                index = int(image.name[:2]) - 1
                destination = manifest / folder.parent.name / folder.name / (image.stem + ".jpg")
                compose(image, destination, copy[index], layout, args.fonts, folder.name)
                out.writerow([folder.parent.name, folder.name, image.stem, *layout["size"], destination.relative_to(ROOT).as_posix()])

if __name__ == "__main__":
    main()
