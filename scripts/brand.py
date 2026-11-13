from pathlib import Path
from PIL import Image, ImageDraw, ImageFont, ImageOps

ROOT = Path(__file__).resolve().parents[1]
BRAND = ROOT / "deliverables/brand-assets"
STORE = ROOT / "deliverables/store-assets"
BRAND.mkdir(parents=True, exist_ok=True)
STORE.mkdir(parents=True, exist_ok=True)

im = Image.new("RGBA", (1024, 1024))
d = ImageDraw.Draw(im)
d.rounded_rectangle((0, 0, 1023, 1023), 194, fill="#493A58")
ivory = "#FAF1DC"
gold = "#E6C383"
d.rounded_rectangle((235, 320, 701, 786), 65, outline=ivory, width=45)
d.rectangle((534, 286, 741, 480), fill="#493A58")
d.line([(240, 504), (490, 504)], fill=ivory, width=44)
d.line([(349, 269), (349, 367)], fill=ivory, width=43)
d.line([(357, 608), (437, 685), (592, 530)], fill=ivory, width=47, joint="curve")
d.polygon([(697, 203), (736, 338), (871, 377), (736, 416), (697, 551), (658, 416), (523, 377), (658, 338)], fill=gold)
im.save(BRAND / "icon-master.png")
for size in (114, 512):
    im.resize((size, size), Image.Resampling.LANCZOS).save(STORE / f"icon-{size}.png")

hero = Image.open(BRAND / "invitation-suite.png").convert("RGB")
banner = ImageOps.fit(hero, (1024, 500))
hint = Path("C:/Windows/Fonts/georgia.ttf")
font = ImageFont.truetype(str(hint), 70) if hint.exists() else ImageFont.load_default(size=70)
ImageDraw.Draw(banner).text((66, 243), "Planavora", fill=ivory, font=font, anchor="lm")
banner.save(STORE / "promotional-1024x500.jpg", quality=93)
