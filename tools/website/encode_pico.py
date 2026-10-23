"""Encode native Android Pico frames without changing their artwork."""
from pathlib import Path
import argparse
from PIL import Image

parser = argparse.ArgumentParser()
parser.add_argument("frames", type=Path)
parser.add_argument("destination", type=Path)
args = parser.parse_args()
frames = [Image.open(p).convert("RGBA") for p in sorted(args.frames.glob("frame-*.png"))]
if len(frames) != 32 or any(frame.size != (560, 560) for frame in frames):
    raise ValueError("Expected 32 native 560 × 560 frames")
frames[0].save(args.destination, save_all=True, append_images=frames[1:],
               duration=100, loop=0, lossless=True, method=6)
