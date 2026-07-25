#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Extract ~10 long representative ridge/streamlines from reference.png for AE."""

from __future__ import annotations

import json
import math
import sys
from pathlib import Path

import cv2
import numpy as np

ROOT = Path(__file__).resolve().parent
IMG_PATH = ROOT / "reference.png"
OUT_JSON = ROOT / "paths.json"
OUT_PREVIEW = ROOT / "trace_preview.png"

TARGET_LINES = 10
WORK_LONG_SIDE = 1800
STEP = 2.5
MAX_STEPS = 9000
SNAP_RADIUS = 4
MIN_VERTICES = 12
MAX_VERTICES = 64
SMOOTH_ITERS = 2
# Hard length floor vs image width when selecting final lines
MIN_WIDTH_FRAC = 0.30


def load_image(path: Path) -> np.ndarray:
    img = cv2.imread(str(path), cv2.IMREAD_COLOR)
    if img is None:
        raise FileNotFoundError(f"Cannot read image: {path}")
    return img


def to_work_scale(img: np.ndarray) -> tuple[np.ndarray, float]:
    h, w = img.shape[:2]
    long_side = max(h, w)
    if long_side <= WORK_LONG_SIDE:
        return img.copy(), 1.0
    scale = WORK_LONG_SIDE / float(long_side)
    small = cv2.resize(
        img,
        (int(round(w * scale)), int(round(h * scale))),
        interpolation=cv2.INTER_AREA,
    )
    return small, scale


def build_ridge_field(gray: np.ndarray) -> tuple[np.ndarray, np.ndarray, np.ndarray]:
    """Return ridge strength (float), cos/sin of ridge tangent direction."""
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    eq = clahe.apply(gray)
    blur = cv2.GaussianBlur(eq, (0, 0), 1.6)

    # Dark grooves between highlights
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (11, 11))
    blackhat = cv2.morphologyEx(blur, cv2.MORPH_BLACKHAT, kernel).astype(np.float32)
    # Also local valley via Laplacian (negative lobes on ridges of dark lines)
    lap = cv2.Laplacian(blur, cv2.CV_32F, ksize=3)
    valley = np.clip(-lap, 0, None)
    ridge = 0.7 * blackhat + 0.3 * valley
    ridge = cv2.GaussianBlur(ridge, (0, 0), 1.0)
    ridge = ridge / (ridge.max() + 1e-6)

    # Structure tensor for coherent orientation along grooves
    gx = cv2.Sobel(blur, cv2.CV_32F, 1, 0, ksize=3)
    gy = cv2.Sobel(blur, cv2.CV_32F, 0, 1, ksize=3)
    jxx = cv2.GaussianBlur(gx * gx, (0, 0), 2.5)
    jyy = cv2.GaussianBlur(gy * gy, (0, 0), 2.5)
    jxy = cv2.GaussianBlur(gx * gy, (0, 0), 2.5)

    # Edge orientation angle of gradient; ridge tangent = +90deg
    # Eigenvector of smaller eigenvalue = ridge direction for dark lines on bright
    # For structure tensor, dominant gradient direction is across ridges.
    # Tangent angle:
    # theta = 0.5 * atan2(2*jxy, jxx-jyy) gives gradient axis; add pi/2 for tangent
    grad_ang = 0.5 * np.arctan2(2.0 * jxy, jxx - jyy)
    tang_ang = grad_ang + (np.pi * 0.5)
    cos_t = np.cos(tang_ang).astype(np.float32)
    sin_t = np.sin(tang_ang).astype(np.float32)
    return ridge, cos_t, sin_t


def bilinear(img: np.ndarray, x: float, y: float) -> float:
    h, w = img.shape[:2]
    if x < 0 or y < 0 or x >= w - 1 or y >= h - 1:
        return 0.0
    x0 = int(math.floor(x))
    y0 = int(math.floor(y))
    x1 = x0 + 1
    y1 = y0 + 1
    dx = x - x0
    dy = y - y0
    v00 = float(img[y0, x0])
    v01 = float(img[y0, x1])
    v10 = float(img[y1, x0])
    v11 = float(img[y1, x1])
    return (
        v00 * (1 - dx) * (1 - dy)
        + v01 * dx * (1 - dy)
        + v10 * (1 - dx) * dy
        + v11 * dx * dy
    )


def sample_dir(cos_t: np.ndarray, sin_t: np.ndarray, x: float, y: float) -> tuple[float, float]:
    c = bilinear(cos_t, x, y)
    s = bilinear(sin_t, x, y)
    n = math.hypot(c, s)
    if n < 1e-6:
        return 1.0, 0.0
    return c / n, s / n


def snap_to_ridge(ridge: np.ndarray, x: float, y: float, nx: float, ny: float) -> tuple[float, float, float]:
    """Snap along normal to local ridge maximum."""
    best_v = -1.0
    best = (x, y)
    for t in range(-SNAP_RADIUS, SNAP_RADIUS + 1):
        sx = x + nx * t
        sy = y + ny * t
        v = bilinear(ridge, sx, sy)
        if v > best_v:
            best_v = v
            best = (sx, sy)
    return best[0], best[1], best_v


def trace_one(
    ridge: np.ndarray,
    cos_t: np.ndarray,
    sin_t: np.ndarray,
    x0: float,
    y0: float,
    min_strength: float,
) -> np.ndarray:
    h, w = ridge.shape
    pts_fwd = []
    pts_back = []

    def walk(init_sign: int, bucket: list):
        x, y = x0, y0
        # Seed an initial heading once; afterwards follow continuity only.
        c0, s0 = sample_dir(cos_t, sin_t, x, y)
        px, py = c0 * init_sign, s0 * init_sign
        weak_streak = 0
        for _ in range(MAX_STEPS):
            if x < 2 or y < 2 or x >= w - 2 or y >= h - 2:
                break
            c, s = sample_dir(cos_t, sin_t, x, y)
            # Keep heading consistent with previous step
            if px * c + py * s < 0:
                c, s = -c, -s
            # Blend with previous heading to suppress local jitter / switching grooves
            c = 0.75 * c + 0.25 * px
            s = 0.75 * s + 0.25 * py
            nrm = math.hypot(c, s) or 1.0
            c, s = c / nrm, s / nrm
            # Normal for snapping
            nx, ny = -s, c
            x, y, v = snap_to_ridge(ridge, x, y, nx, ny)
            # Allow brief weak gaps so DOF-blurred zones don't shatter paths
            if v < min_strength:
                weak_streak += 1
                if weak_streak > 18:
                    break
            else:
                weak_streak = 0
            bucket.append((x, y))
            x = x + c * STEP
            y = y + s * STEP
            px, py = c, s

    walk(+1, pts_fwd)
    walk(-1, pts_back)
    pts_back.reverse()
    if pts_back:
        pts_back = pts_back[:-1]  # avoid duplicating seed
    all_pts = pts_back + [(x0, y0)] + pts_fwd
    if len(all_pts) < 8:
        return np.zeros((0, 2), dtype=np.float64)
    return np.array(all_pts, dtype=np.float64)


def path_length(pts: np.ndarray) -> float:
    if len(pts) < 2:
        return 0.0
    return float(np.linalg.norm(np.diff(pts, axis=0), axis=1).sum())


def path_span(pts: np.ndarray) -> float:
    if len(pts) < 2:
        return 0.0
    return float(math.hypot(float(np.ptp(pts[:, 0])), float(np.ptp(pts[:, 1]))))


def path_efficiency(pts: np.ndarray) -> float:
    L = path_length(pts)
    if L < 1e-6:
        return 0.0
    return path_span(pts) / L


def seed_points(ridge: np.ndarray, n_rows: int = 40, n_cols: int = 20) -> list[tuple[float, float]]:
    h, w = ridge.shape
    # NMS-ish: pick strongest points in grid cells that exceed percentile
    thr = float(np.percentile(ridge, 72))
    seeds = []
    cell_h = h / n_rows
    cell_w = w / n_cols
    for ry in range(n_rows):
        for cx in range(n_cols):
            y0 = int(ry * cell_h)
            x0 = int(cx * cell_w)
            y1 = int(min(h, (ry + 1) * cell_h))
            x1 = int(min(w, (cx + 1) * cell_w))
            patch = ridge[y0:y1, x0:x1]
            if patch.size == 0:
                continue
            yi, xi = np.unravel_index(int(np.argmax(patch)), patch.shape)
            v = float(patch[yi, xi])
            if v >= thr:
                # Mild bias to left/center so traces can grow across the frame
                x_bias = 1.0 + 0.35 * (1.0 - (x0 + xi) / max(w - 1, 1))
                seeds.append((x0 + xi, y0 + yi, v * x_bias))
    seeds.sort(key=lambda t: t[2], reverse=True)
    return [(float(x), float(y)) for x, y, _ in seeds]


def paths_too_close(a: np.ndarray, b: np.ndarray, min_dist: float) -> bool:
    # Compare resampled centroids / mean distance via coarse samples
    sa = a[:: max(1, len(a) // 24)]
    sb = b[:: max(1, len(b) // 24)]
    # Approximate mean nearest distance
    dsum = 0.0
    for p in sa:
        dsum += float(np.linalg.norm(sb - p, axis=1).min())
    return (dsum / len(sa)) < min_dist


def select_diverse(
    paths: list[np.ndarray],
    k: int,
    min_sep: float,
    min_span: float,
) -> list[np.ndarray]:
    # Drop zigzag / scribble traces that inflate arc-length but cover little space
    paths = [
        p
        for p in paths
        if path_span(p) >= min_span and path_efficiency(p) >= 0.42
    ]
    paths = sorted(paths, key=lambda p: (path_span(p), path_efficiency(p)), reverse=True)
    chosen: list[np.ndarray] = []
    for p in paths:
        if any(paths_too_close(p, c, min_sep) for c in chosen):
            continue
        chosen.append(p)
        if len(chosen) >= k:
            break
    if len(chosen) < k:
        for p in paths:
            if any(np.allclose(p.mean(0), c.mean(0), atol=2.0) for c in chosen):
                continue
            if any(paths_too_close(p, c, min_sep * 0.45) for c in chosen):
                continue
            chosen.append(p)
            if len(chosen) >= k:
                break
    chosen.sort(key=lambda p: float(p[:, 1].mean()))
    return chosen


def resample_polyline(pts: np.ndarray, n: int) -> np.ndarray:
    if len(pts) < 2:
        return pts.copy()
    d = np.linalg.norm(np.diff(pts, axis=0), axis=1)
    s = np.concatenate([[0.0], np.cumsum(d)])
    total = float(s[-1])
    if total < 1e-6:
        return np.repeat(pts[:1], n, axis=0)
    targets = np.linspace(0.0, total, n)
    out = np.zeros((n, 2), dtype=np.float64)
    for i, t in enumerate(targets):
        j = int(np.searchsorted(s, t, side="right") - 1)
        j = max(0, min(j, len(pts) - 2))
        seg = s[j + 1] - s[j]
        a = 0.0 if seg < 1e-9 else (t - s[j]) / seg
        out[i] = pts[j] * (1 - a) + pts[j + 1] * a
    return out


def chaikin_smooth(pts: np.ndarray, iterations: int = 2) -> np.ndarray:
    out = pts.astype(np.float64)
    for _ in range(iterations):
        if len(out) < 3:
            break
        new_pts = [out[0]]
        for i in range(len(out) - 1):
            p, q = out[i], out[i + 1]
            new_pts.append(0.75 * p + 0.25 * q)
            new_pts.append(0.25 * p + 0.75 * q)
        new_pts.append(out[-1])
        out = np.array(new_pts, dtype=np.float64)
    return out


def simplify_path(pts: np.ndarray, eps: float) -> np.ndarray:
    approx = cv2.approxPolyDP(pts.astype(np.float32).reshape(-1, 1, 2), eps, False)
    arr = approx.reshape(-1, 2).astype(np.float64)
    return arr if len(arr) >= 3 else pts.astype(np.float64)


def compute_tangents(pts: np.ndarray, tension: float = 0.32) -> tuple[list, list]:
    n = len(pts)
    in_t = [[0.0, 0.0] for _ in range(n)]
    out_t = [[0.0, 0.0] for _ in range(n)]
    for i in range(n):
        if i == 0:
            delta = (pts[1] - pts[0]) * tension
            out_t[i] = [float(delta[0]), float(delta[1])]
        elif i == n - 1:
            delta = (pts[-1] - pts[-2]) * tension
            in_t[i] = [float(-delta[0]), float(-delta[1])]
        else:
            delta = (pts[i + 1] - pts[i - 1]) * tension
            out_t[i] = [float(delta[0]), float(delta[1])]
            in_t[i] = [float(-delta[0]), float(-delta[1])]
    return in_t, out_t


def extract_paths(
    img_bgr: np.ndarray,
    min_strength: float = 0.18,
    min_len_ratio: float = 0.35,
) -> tuple[list[dict], np.ndarray, dict]:
    full_h, full_w = img_bgr.shape[:2]
    work, scale = to_work_scale(img_bgr)
    gray = cv2.cvtColor(work, cv2.COLOR_BGR2GRAY)
    ridge, cos_t, sin_t = build_ridge_field(gray)
    wh, ww = work.shape[:2]

    seeds = seed_points(ridge)
    raw: list[np.ndarray] = []
    for x0, y0 in seeds[:260]:
        if bilinear(ridge, x0, y0) < min_strength:
            continue
        pts = trace_one(ridge, cos_t, sin_t, x0, y0, min_strength=min_strength * 0.45)
        if len(pts) >= 20 and path_efficiency(pts) >= 0.22:
            raw.append(pts)

    diag = math.hypot(ww, wh)
    min_span = max(diag * min_len_ratio * 0.85, ww * MIN_WIDTH_FRAC)
    long_paths = [p for p in raw if path_span(p) >= min_span and path_efficiency(p) >= 0.42]
    if len(long_paths) < TARGET_LINES:
        for frac in (0.24, 0.20, 0.16, 0.12):
            min_span = ww * frac
            long_paths = [p for p in raw if path_span(p) >= min_span and path_efficiency(p) >= 0.38]
            if len(long_paths) >= TARGET_LINES:
                break
        if len(long_paths) < TARGET_LINES:
            long_paths = sorted(raw, key=path_span, reverse=True)[: TARGET_LINES * 10]
            min_span = path_span(long_paths[-1]) if long_paths else 0.0

    min_sep = max(14.0, wh / (TARGET_LINES * 1.8))
    chosen = select_diverse(long_paths, TARGET_LINES, min_sep=min_sep, min_span=min_span)

    inv = 1.0 / scale
    result = []
    preview = img_bgr.copy()
    colors = [
        (0, 255, 255),
        (255, 255, 0),
        (0, 200, 255),
        (255, 180, 0),
        (80, 255, 120),
        (255, 100, 255),
        (100, 180, 255),
        (0, 255, 160),
        (255, 220, 100),
        (180, 180, 255),
    ]

    for idx, pts_work in enumerate(chosen[:TARGET_LINES], start=1):
        pts = pts_work * inv
        eps = max(full_w, full_h) * 0.0012
        simp = simplify_path(pts, eps)
        smooth = chaikin_smooth(simp, iterations=SMOOTH_ITERS)
        n_vert = int(np.clip(len(smooth) // 3, MIN_VERTICES, MAX_VERTICES))
        # Length-adaptive vertex count
        n_vert = int(np.clip(path_length(pts) / (max(full_w, full_h) * 0.035), MIN_VERTICES, MAX_VERTICES))
        final_pts = resample_polyline(smooth, n_vert)
        final_pts = chaikin_smooth(final_pts, iterations=1)
        final_pts = resample_polyline(final_pts, n_vert)
        in_t, out_t = compute_tangents(final_pts)

        vertices = [[round(float(x), 3), round(float(y), 3)] for x, y in final_pts]
        path_obj = {
            "name": f"Line {idx:02d}",
            "closed": False,
            "vertices": vertices,
            "inTangents": [[round(a, 3), round(b, 3)] for a, b in in_t],
            "outTangents": [[round(a, 3), round(b, 3)] for a, b in out_t],
            "length": round(path_length(final_pts), 2),
            "span": round(path_span(final_pts), 2),
        }
        result.append(path_obj)

        draw = np.array([[int(v[0]), int(v[1])] for v in vertices], dtype=np.int32).reshape(-1, 1, 2)
        color = colors[(idx - 1) % len(colors)]
        cv2.polylines(preview, [draw], False, color, max(2, full_w // 2000), cv2.LINE_AA)
        for v in vertices[:: max(1, len(vertices) // 12)]:
            cv2.circle(preview, (int(v[0]), int(v[1])), max(3, full_w // 1500), (0, 0, 255), -1, cv2.LINE_AA)

    meta = {
        "imageWidth": full_w,
        "imageHeight": full_h,
        "workScale": scale,
        "seedCount": len(seeds),
        "rawPathCount": len(raw),
        "longPathCount": len(long_paths),
        "selectedCount": len(result),
        "minStrength": min_strength,
        "minLenRatio": min_len_ratio,
    }
    return result, preview, meta


def quality_ok(paths: list[dict], meta: dict) -> bool:
    if len(paths) < TARGET_LINES:
        return False
    spans = [p.get("span", p["length"]) for p in paths]
    med = float(np.median(spans))
    if med < meta["imageWidth"] * 0.28:
        return False
    if min(spans) < meta["imageWidth"] * 0.18:
        return False
    xs = [v[0] for p in paths for v in p["vertices"]]
    if not xs:
        return False
    if max(xs) - min(xs) < meta["imageWidth"] * 0.55:
        return False
    return True


def main() -> int:
    if not IMG_PATH.exists():
        print(f"ERROR: missing {IMG_PATH}", file=sys.stderr)
        return 1

    img = load_image(IMG_PATH)
    attempts = [
        dict(min_strength=0.12, min_len_ratio=0.36),
        dict(min_strength=0.09, min_len_ratio=0.30),
        dict(min_strength=0.07, min_len_ratio=0.26),
        dict(min_strength=0.05, min_len_ratio=0.22),
    ]

    best = None
    for i, cfg in enumerate(attempts, start=1):
        print(f"[attempt {i}/{len(attempts)}] cfg={cfg}")
        paths, preview, meta = extract_paths(img, **cfg)
        spans = [p.get("span", p["length"]) for p in paths]
        print(
            f"  selected={meta['selectedCount']} raw={meta['rawPathCount']} "
            f"long={meta['longPathCount']} spans={spans}"
        )
        score = 0.0
        if paths:
            xs = [v[0] for p in paths for v in p["vertices"]]
            cover = (max(xs) - min(xs)) if xs else 0.0
            score = (
                len(paths) * 1e7
                + float(np.min(spans)) * 50
                + float(np.median(spans)) * 20
                + cover * 5
            )
        if best is None or score > best[0]:
            best = (score, paths, preview, meta, cfg)
        if quality_ok(paths, meta):
            best = (score, paths, preview, meta, cfg)
            print("  quality OK — stop")
            break

    assert best is not None
    _, paths, preview, meta, cfg = best
    paths = paths[:TARGET_LINES]
    for i, p in enumerate(paths, start=1):
        p["name"] = f"Line {i:02d}"

    payload = {
        "version": 1,
        "source": IMG_PATH.name,
        "imageWidth": meta["imageWidth"],
        "imageHeight": meta["imageHeight"],
        "lineCount": len(paths),
        "extraction": {
            "method": "structure-tensor-streamline",
            "workScale": meta["workScale"],
            "seedCount": meta["seedCount"],
            "rawPathCount": meta["rawPathCount"],
            "config": cfg,
        },
        "paths": paths,
    }
    OUT_JSON.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    scale_prev = min(1.0, 2400 / preview.shape[1])
    prev_small = cv2.resize(
        preview,
        (int(preview.shape[1] * scale_prev), int(preview.shape[0] * scale_prev)),
        interpolation=cv2.INTER_AREA,
    )
    cv2.imwrite(str(OUT_PREVIEW), prev_small)
    print(f"Wrote {OUT_JSON} ({len(paths)} paths)")
    print(f"Wrote {OUT_PREVIEW}")
    ok = quality_ok(paths, meta)
    print("quality:", "PASS" if ok else "WEAK")
    return 0 if len(paths) >= 6 else 2


if __name__ == "__main__":
    raise SystemExit(main())
