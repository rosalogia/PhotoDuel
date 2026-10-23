#!/usr/bin/env python3
"""Builds a synthetic camera roll of burst-like photos for testing Photo Duel.

Each burst is one base photo turned into several frames with small handheld-style shifts,
rotations, exposure changes and the odd blurry frame. EXIF DateTimeOriginal is set so
MediaStore groups them by capture time the way a real camera roll would.

Usage: make_bursts.py BASE_DIR OUT_DIR
BASE_DIR must contain the photos named in SCENARIO (pNNNN.jpg, from picsum.photos/id/NNNN).
Writes OUT_DIR/Camera/*.jpg, OUT_DIR/Screenshots/*.jpg and OUT_DIR/expected.json.
"""
import json
import random
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

from PIL import Image, ImageEnhance, ImageFilter

EXIF_IFD = 0x8769
DATETIME_ORIGINAL = 36867
OFFSET_TIME_ORIGINAL = 36881
SUBSEC_TIME_ORIGINAL = 37521
ORIENTATION = 274

# (name, base photo, frames, start offset from now, seconds between frames, blurry frame index or None)
SCENARIO = [
    ("portrait", "p1027", 5, timedelta(hours=-2), 1.5, 3),
    ("canoe", "p1011", 4, timedelta(days=-1, hours=-3), 2.0, None),
    ("forest", "p1043", 3, timedelta(days=-3), 3.0, None),
    ("jellyfish", "p1069", 6, timedelta(days=-10), 0.8, 5),
    # Two different scenes shot 20 s apart: one time session, but two visual clusters.
    ("pug-blanket", "p1025", 3, timedelta(days=-5), 1.0, None),
    ("pug-bed", "p1062", 2, timedelta(days=-5, seconds=23), 1.0, None),
    # Outside the default 30-day scope.
    ("street", "p342", 3, timedelta(days=-45), 1.2, None),
]
SINGLES = [
    ("waterfall", "p1035", timedelta(hours=-5)),
    ("hiker", "p177", timedelta(days=-2)),
    ("puppy", "p237", timedelta(days=-7)),
    ("hills", "p287", timedelta(days=-12)),
    ("beach", "p338", timedelta(days=-60)),
]
OUT_SIZE = (2000, 1500)


def jitter(img: Image.Image, rng: random.Random, strong: bool = True) -> Image.Image:
    """A handheld re-shot of the same scene: shift, small rotation and zoom, exposure change."""
    w, h = img.size
    angle = rng.uniform(-1.2, 1.2) if strong else 0.0
    dx = rng.uniform(-18, 18) if strong else 0.0
    dy = rng.uniform(-14, 14) if strong else 0.0
    scale = 1.04 + (rng.uniform(-0.01, 0.01) if strong else 0.0)
    # Zoom in slightly so rotation/shift never exposes empty borders.
    big = img.resize((int(w * scale), int(h * scale)), Image.LANCZOS).rotate(angle, resample=Image.BICUBIC)
    bw, bh = big.size
    left = (bw - w) / 2 + dx
    top = (bh - h) / 2 + dy
    out = big.crop((round(left), round(top), round(left) + w, round(top) + h))
    out = ImageEnhance.Brightness(out).enhance(rng.uniform(0.94, 1.06))
    return out


def save(img: Image.Image, path: Path, when: datetime) -> None:
    exif = Image.Exif()
    exif[ORIENTATION] = 1
    ifd = exif.get_ifd(EXIF_IFD)
    ifd[DATETIME_ORIGINAL] = when.strftime("%Y:%m:%d %H:%M:%S")
    ifd[SUBSEC_TIME_ORIGINAL] = f"{when.microsecond // 1000:03d}"
    ifd[OFFSET_TIME_ORIGINAL] = "+00:00"
    img.save(path, "JPEG", quality=90, exif=exif)


def main() -> None:
    base_dir, out_dir = Path(sys.argv[1]), Path(sys.argv[2])
    camera = out_dir / "Camera"
    shots = out_dir / "Screenshots"
    for d in (camera, shots):
        d.mkdir(parents=True, exist_ok=True)
        for old in d.glob("*.jpg"):  # file names embed the time, so stale runs would pile up
            old.unlink()
    rng = random.Random(42)
    now = datetime.now(timezone.utc).replace(microsecond=0)
    expected = {"bursts": [], "singles": [], "excluded": []}

    def load(name: str) -> Image.Image:
        return Image.open(base_dir / f"{name}.jpg").convert("RGB").resize(OUT_SIZE, Image.LANCZOS)

    for name, base, frames, offset, spacing, blurry in SCENARIO:
        src = load(base)
        files = []
        for i in range(frames):
            when = now + offset + timedelta(seconds=spacing * i)
            img = jitter(src, rng)
            if i == blurry:
                img = img.filter(ImageFilter.GaussianBlur(5))
            fname = f"PXL_{when:%Y%m%d_%H%M%S}{when.microsecond // 1000:03d}_{name}_{i}.jpg"
            save(img, camera / fname, when)
            files.append(fname)
        expected["bursts"].append({"name": name, "files": files, "time": (now + offset).isoformat()})

    for name, base, offset in SINGLES:
        when = now + offset
        fname = f"PXL_{when:%Y%m%d_%H%M%S}_{name}.jpg"
        save(jitter(load(base), rng, strong=False), camera / fname, when)
        expected["singles"].append(fname)

    # A screenshot saved under DCIM, which some OEMs do; must never be grouped.
    when = now + timedelta(hours=-2, seconds=3)
    for i in range(2):
        fname = f"Screenshot_{when:%Y%m%d_%H%M%S}_{i}.jpg"
        save(Image.new("RGB", (1080, 2220), (240, 240, 240)), shots / fname, when + timedelta(seconds=i))
        expected["excluded"].append(fname)

    (out_dir / "expected.json").write_text(json.dumps(expected, indent=2))
    print(f"Wrote {sum(len(b['files']) for b in expected['bursts'])} burst frames, "
          f"{len(expected['singles'])} singles, {len(expected['excluded'])} screenshots to {out_dir}")


if __name__ == "__main__":
    main()
