# graindebeaute_android_web/app/src/main/python/analysis_runner.py
import cv2
import json
import os
import sys
import traceback
import uuid
import numpy as np
from mole_logic import (
    detect_spacer_ring,
    mask_and_crop_spacer,
    segment_mole_smart,
    calculate_dimensions,
    calculate_max_dimension,
    calculate_circularity,
    calculate_asymmetry,
    calculate_color_variation,
)
from matching_service_local import compute_feature_vector

MARGIN = 30


def _make_tight_circle_png(img_bgr):
    """Applique un masque circulaire et rogne serré autour du contenu."""
    h, w = img_bgr.shape[:2]
    cx, cy = w // 2, h // 2
    r = min(w, h) // 2 - MARGIN
    r = max(r, 1)
    alpha = np.zeros((h, w), dtype=np.uint8)
    cv2.circle(alpha, (cx, cy), r, 255, -1)
    bgra = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2BGRA)
    bgra[:, :, 3] = alpha
    return bgra[cy - r:cy + r, cx - r:cx + r]


def _log(msg):
    print(f"[GrainAnalysis] {msg}", flush=True)


def run_analysis(image_path, output_dir):
    """
    Exécute le pipeline ABCDE complet sur une image.

    Paramètres:
        image_path (str): chemin absolu vers l'image JPEG source
        output_dir (str): dossier où sauvegarder les images résultats

    Retourne un dict avec:
        area_mm2, max_dimension_mm, circularity, asymmetry, color_variation,
        method_used, analyzed_image_path, cropped_image_path, feature_vector (list[float])

    Lève ValueError si l'analyse échoue.
    """
    _log(f"START image_path={image_path!r} output_dir={output_dir!r}")

    os.makedirs(output_dir, exist_ok=True)

    img = cv2.imread(image_path)
    if img is None:
        _log(f"FAILED: cv2.imread returned None — fichier inexistant ou illisible")
        raise ValueError("Impossible de lire l'image : " + image_path)
    _log(f"imread OK shape={img.shape} dtype={img.dtype}")

    try:
        center, radius = detect_spacer_ring(img)
    except Exception:
        _log(f"EXCEPTION in detect_spacer_ring:\n{traceback.format_exc()}")
        raise
    _log(f"detect_spacer_ring → center={center} radius={radius}")
    if center is None:
        raise ValueError("Pièce 3D (spacer ring) non détectée dans l'image")

    crop = mask_and_crop_spacer(img, center, radius)
    _log(f"mask_and_crop_spacer OK crop.shape={crop.shape}")

    try:
        mask, contour, method_name, growth_ratio = segment_mole_smart(crop)
    except Exception:
        _log(f"EXCEPTION in segment_mole_smart:\n{traceback.format_exc()}")
        raise
    _log(f"segment_mole_smart → method={method_name} mask={'OK' if mask is not None else 'None'} contour={'OK' if contour is not None else 'None'}")
    if mask is None or contour is None:
        raise ValueError("Échec de la segmentation du grain de beauté")

    area_mm2 = calculate_dimensions(contour, radius)
    max_dim_mm, p1, p2 = calculate_max_dimension(contour, radius)
    circularity = calculate_circularity(contour)
    asymmetry = calculate_asymmetry(mask, contour)
    color_variation = calculate_color_variation(crop, mask)
    _log(f"métriques: area={area_mm2:.3f} max_dim={max_dim_mm:.3f} circ={circularity:.3f} asym={asymmetry:.3f} color={color_variation:.3f}")

    metrics = {
        "area_mm2": area_mm2,
        "max_dimension_mm": max_dim_mm,
        "circularity": circularity,
        "asymmetry": asymmetry,
        "color_variation": color_variation,
    }

    try:
        feature_vector = compute_feature_vector(crop, mask, metrics)
        _log(f"feature_vector OK len={len(feature_vector) if feature_vector is not None else 0}")
    except Exception:
        _log(f"EXCEPTION in compute_feature_vector (non bloquant):\n{traceback.format_exc()}")
        feature_vector = None

    # Image annotée
    vis = crop.copy()
    cv2.drawContours(vis, [contour], -1, (0, 255, 0), 2)
    if p1 is not None and p2 is not None:
        cv2.line(vis, p1, p2, (0, 0, 255), 2)
        cv2.circle(vis, p1, 4, (0, 0, 255), -1)
        cv2.circle(vis, p2, 4, (0, 0, 255), -1)

    vis_tight = _make_tight_circle_png(vis)
    analyzed_path = os.path.join(output_dir, "analyzed_" + uuid.uuid4().hex + ".png")
    ok = cv2.imwrite(analyzed_path, vis_tight)
    _log(f"imwrite analyzed → {'OK' if ok else 'FAILED'} path={analyzed_path!r}")

    # Image croppée
    cropped_image_path = None
    try:
        crop_tight = _make_tight_circle_png(crop)
        cropped_path = os.path.join(output_dir, "cropped_" + uuid.uuid4().hex + ".png")
        if cv2.imwrite(cropped_path, crop_tight):
            cropped_image_path = cropped_path
            _log(f"imwrite cropped OK path={cropped_path!r}")
        else:
            _log("imwrite cropped FAILED")
    except Exception:
        _log(f"EXCEPTION writing cropped image:\n{traceback.format_exc()}")

    result = {
        "area_mm2": float(area_mm2),
        "max_dimension_mm": float(max_dim_mm),
        "circularity": float(circularity),
        "asymmetry": float(asymmetry),
        "color_variation": float(color_variation),
        "method_used": method_name,
        "analyzed_image_path": analyzed_path,
        "cropped_image_path": cropped_image_path,
        "feature_vector": feature_vector,
    }
    _log(f"SUCCESS — returning JSON results")
    return json.dumps(result)
