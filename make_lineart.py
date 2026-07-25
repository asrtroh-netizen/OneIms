#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fast long lineart: thin black strokes on white, screenshot-like."""

from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "reference.png"
OUT = ROOT / "lineart_long.png"
OUT_PREVIEW = ROOT / "lineart_long_preview.png"


def ridge_and_orient(gray: np.ndarray):
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    eq = clahe.apply(gray)
    blur = cv2.GaussianBlur(eq, (0, 0), 1.2)
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (9, 9))
    bh = cv2.morphologyEx(blur, cv2.MORPH_BLACKHAT, kernel).astype(np.float32)
    bh /= bh.max() + 1e-6
    lap = cv2.Laplacian(blur, cv2.CV_32F, ksize=3)
    valley = np.clip(-lap, 0, None)
    valley /= valley.max() + 1e-6
    ridge = cv2.GaussianBlur(np.clip(0.72 * bh + 0.28 * valley, 0, 1), (0, 0), 0.6)

    gx = cv2.Sobel(blur, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(blur, cv2.CV_32F, 0, 1, ksize=3)
    jxx = cv2.GaussianBlur(gx * gx, (0, 0), 2.0)
    jyy = cv2.GaussianBlur(gy * gy, (0, 0), 2.0)
    jxy = cv2.GaussianBlur(gx * gy, (0, 0), 2.0)
    ang = 0.5 * np.arctan2(2.0 * jxy, jxx - jyy) + np.pi * 0.5
    cos_t = np.cos(ang).astype(np.float32)
    sin_t = np.sin(ang).astype(np.float32)
    return ridge, cos_t, sin_t


def nms_along_normal(ridge: np.ndarray, cos_t: np.ndarray, sin_t: np.ndarray) -> np.ndarray:
    h, w = ridge.shape
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    nx, ny = -sin_t, cos_t

    def sample(d):
        return cv2.remap(
            ridge,
            xx + nx * d,
            yy + ny * d,
            interpolation=cv2.INTER_LINEAR,
            borderMode=cv2.BORDER_REFLECT,
        )

    keep = (ridge >= sample(1.0)) & (ridge >= sample(-1.0)) & (ridge > float(np.percentile(ridge, 66)))
    out = np.zeros(ridge.shape, dtype=np.uint8)
    out[keep] = 255
    return out


def xdog_lines(gray: np.ndarray) -> np.ndarray:
    g = gray.astype(np.float32) / 255.0
    g1 = cv2.GaussianBlur(g, (0, 0), 1.0)
    g2 = cv2.GaussianBlur(g, (0, 0), 1.7)
    diff = g1 - g2
    # Strong negative lobes = ink candidates
    ink = np.clip(-diff * 10.0, 0, 1)
    u8 = (ink * 255).astype(np.uint8)
    _, bw = cv2.threshold(u8, 55, 255, cv2.THRESH_BINARY)
    return bw


def major_edges(gray: np.ndarray) -> np.ndarray:
    blur = cv2.GaussianBlur(gray, (0, 0), 3.2)
    e = cv2.Canny(blur, 45, 120)
    return e


def thin_keep(bw: np.ndarray) -> np.ndarray:
    """Remove speckles; drop only compact filled blobs, keep long ridge nets."""
    bw = cv2.morphologyEx(bw, cv2.MORPH_OPEN, np.ones((2, 2), np.uint8), iterations=1)
    num, labels, stats, _ = cv2.connectedComponentsWithStats(bw, 8)
    out = np.zeros_like(bw)
    for i in range(1, num):
        area = int(stats[i, cv2.CC_STAT_AREA])
        ww = int(stats[i, cv2.CC_STAT_WIDTH])
        hh = int(stats[i, cv2.CC_STAT_HEIGHT])
        if area < 10:
            continue
        bbox = max(ww * hh, 1)
        fill = area / bbox
        # Drop compact ink blobs (fills); keep sparse elongated stroke networks
        if fill > 0.45 and min(ww, hh) > 18 and area > 400:
            continue
        if max(ww, hh) < 8 and area < 30:
            continue
        out[labels == i] = 255
    return out


def build_lineart(bgr: np.ndarray) -> np.ndarray:
    h0, w0 = bgr.shape[:2]
    # High-res working canvas, then exact resize to reference WxH (same output resolution)
    work_long = min(max(h0, w0), 3600)
    scale = min(1.0, work_long / max(h0, w0))
    work = (
        cv2.resize(bgr, (int(round(w0 * scale)), int(round(h0 * scale))), interpolation=cv2.INTER_AREA)
        if scale < 1.0
        else bgr
    )
    gray = cv2.cvtColor(work, cv2.COLOR_BGR2GRAY)
    print("work", work.shape, "target", (h0, w0), flush=True)

    ridge, cos_t, sin_t = ridge_and_orient(gray)
    nms = nms_along_normal(ridge, cos_t, sin_t)
    xdog = xdog_lines(gray)
    majors = major_edges(gray)

    # Main strokes from ridge NMS; light assist from majors; avoid fill-creating dilates
    ink = nms.copy()
    ink = cv2.bitwise_or(ink, majors)
    ink = cv2.morphologyEx(ink, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_RECT, (4, 1)), iterations=1)
    # Only drop tiny speckles in open sky (keep large ridge networks)
    num, labels, stats, _ = cv2.connectedComponentsWithStats(ink, 8)
    cleaned = np.zeros_like(ink)
    for i in range(1, num):
        area = int(stats[i, cv2.CC_STAT_AREA])
        if area >= 8:
            cleaned[labels == i] = 255
    ink = cleaned

    coverage = float((ink > 0).mean())
    print("coverage", round(coverage, 4), flush=True)
    if coverage > 0.28:
        ink = cv2.erode(ink, np.ones((2, 2), np.uint8), iterations=1)
        print("coverage_after_erode", round(float((ink > 0).mean()), 4), flush=True)

    line = np.full_like(ink, 255)
    line[ink > 0] = 0

    if line.shape[1] != w0 or line.shape[0] != h0:
        # Nearest keeps strokes binary-crisp when scaling to exact reference size
        line = cv2.resize(line, (w0, h0), interpolation=cv2.INTER_NEAREST)
        line = np.where(line < 210, 0, 255).astype(np.uint8)

    assert line.shape[0] == h0 and line.shape[1] == w0, (line.shape, h0, w0)
    return cv2.cvtColor(line, cv2.COLOR_GRAY2BGR)


def main() -> int:
    print("loading", SRC, flush=True)
    img = cv2.imread(str(SRC), cv2.IMREAD_COLOR)
    if img is None:
        raise SystemExit("cannot read source")
    print("source", img.shape, flush=True)
    out = build_lineart(img)
    cv2.imwrite(str(OUT), out, [cv2.IMWRITE_PNG_COMPRESSION, 3])
    prev_w = 1708
    prev = cv2.resize(out, (prev_w, int(out.shape[0] * prev_w / out.shape[1])), interpolation=cv2.INTER_AREA)
    g = cv2.cvtColor(prev, cv2.COLOR_BGR2GRAY)
    prev = cv2.cvtColor(np.where(g < 200, 0, 255).astype(np.uint8), cv2.COLOR_GRAY2BGR)
    cv2.imwrite(str(OUT_PREVIEW), prev)
    print("wrote", OUT, out.shape, OUT.stat().st_size, flush=True)
    print("wrote", OUT_PREVIEW, flush=True)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
