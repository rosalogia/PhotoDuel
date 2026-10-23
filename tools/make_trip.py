#!/usr/bin/env python3
"""Builds a synthetic multi-day trip for testing highlights: many distinct photos a few minutes
apart (never grouped), plus one short burst per day (collapsed to one candidate).

Usage: make_trip.py BASE_DIR OUT_DIR [DAYS_AGO]
Uses every pNNNN.jpg in BASE_DIR. Writes OUT_DIR/Camera/*.jpg; push it with seed_device.sh
(without --clear to add it to an existing roll). The trip's last day is DAYS_AGO (default 14) ago.
"""
import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter

from make_bursts import OUT_SIZE, jitter, save

PER_DAY = [24, 18, 22]
BURST_FRAMES = 3


def variant(src: Image.Image, rng: random.Random) -> Image.Image:
    """A different-looking shot: a random crop of the scene with its own color and exposure."""
    w, h = src.size
    f = rng.uniform(0.45, 0.8)
    cw, ch = int(w * f), int(h * f)
    x, y = rng.randint(0, w - cw), rng.randint(0, h - ch)
    img = src.crop((x, y, x + cw, y + ch)).resize(OUT_SIZE, Image.LANCZOS)
    img = ImageEnhance.Color(img).enhance(rng.uniform(0.5, 1.5))
    img = ImageEnhance.Brightness(img).enhance(rng.uniform(0.75, 1.2))
    if rng.random() < 0.15:
        img = img.filter(ImageFilter.GaussianBlur(4))
    return img


def main() -> None:
    base_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    days_ago = int(sys.argv[3]) if len(sys.argv) > 3 else 14
    camera = out_dir / "Camera"
    camera.mkdir(parents=True, exist_ok=True)
    (out_dir / "Screenshots").mkdir(exist_ok=True)
    for old in camera.glob("*.jpg"):
        old.unlink()
    rng = random.Random(7)
    bases = [Image.open(p).convert("RGB").resize(OUT_SIZE, Image.LANCZOS) for p in sorted(base_dir.glob("p*.jpg"))]
    today = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    total = 0
    for d, count in enumerate(PER_DAY):
        day = today - timedelta(days=days_ago + len(PER_DAY) - 1 - d)
        when = day + timedelta(hours=9)
        burst_at = rng.randrange(count)
        for i in range(count):
            when += timedelta(minutes=rng.randint(4, 20))
            src = rng.choice(bases)
            if i == burst_at:
                shot = variant(src, rng)
                for k in range(BURST_FRAMES):
                    t = when + timedelta(seconds=k)
                    save(jitter(shot, rng), camera / f"PXL_{t:%Y%m%d_%H%M%S}_trip_burst_{k}.jpg", t)
                total += BURST_FRAMES
            else:
                save(variant(src, rng), camera / f"PXL_{when:%Y%m%d_%H%M%S}_trip.jpg", when)
                total += 1
    print(f"Wrote {total} trip photos over {len(PER_DAY)} days to {camera}")


if __name__ == "__main__":
    main()
