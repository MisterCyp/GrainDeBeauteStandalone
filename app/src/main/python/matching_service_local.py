# graindebeaute_android_web/app/src/main/python/matching_service_local.py
import logging
from typing import List, Dict, Any

import numpy as np
import cv2
from skimage.feature import local_binary_pattern

logger = logging.getLogger(__name__)


def compute_feature_vector(crop, mask, metrics=None):
    """
    Calcule un vecteur de 1044 features (identique à matching_service.py du backend).
    Retourne list[float] normalisé L2.
    """
    def _extract_hsv_lbp(image, pixel_mask):
        if not np.any(pixel_mask):
            return np.zeros(522, dtype=np.float32)

        hsv = cv2.cvtColor(image, cv2.COLOR_BGR2HSV)
        h_vals = hsv[:, :, 0][pixel_mask].ravel()
        s_vals = hsv[:, :, 1][pixel_mask].ravel()
        v_vals = hsv[:, :, 2][pixel_mask].ravel()
        hist_3d, _ = np.histogramdd(
            np.stack([h_vals, s_vals, v_vals], axis=1),
            bins=[8, 8, 8],
            range=[(0, 180), (0, 256), (0, 256)],
        )
        hsv_feat = hist_3d.ravel().astype(np.float32)

        gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY)
        lbp = local_binary_pattern(gray, P=8, R=1, method="uniform")
        lbp_hist, _ = np.histogram(lbp[pixel_mask], bins=10, range=(0, 10))
        lbp_feat = lbp_hist.astype(np.float32)

        feat = np.concatenate([hsv_feat, lbp_feat])
        norm = np.linalg.norm(feat)
        return feat / norm if norm > 0 else feat

    mole_pixels = mask > 0
    mole_feat = _extract_hsv_lbp(crop, mole_pixels)

    kernel = np.ones((100, 100), np.uint8)
    dilated_mask = cv2.dilate(mask, kernel, iterations=1)
    halo_mask = (dilated_mask > 0) & (mask == 0)
    gray_crop = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)
    skin_pixels = halo_mask & (gray_crop > 5)
    if not np.any(skin_pixels):
        skin_pixels = (mask == 0) & (gray_crop > 5)
    skin_feat = _extract_hsv_lbp(crop, skin_pixels)

    full_feat = np.concatenate([mole_feat * 0.5, skin_feat * 0.5])
    final_norm = np.linalg.norm(full_feat)
    if final_norm > 0:
        full_feat = full_feat / final_norm

    return full_feat.tolist()


def cosine_similarity(v1, v2):
    """Similarité cosinus. Gère vecteurs de tailles différentes par troncature."""
    if not v1 or not v2:
        return 0.0
    size = min(len(v1), len(v2))
    a = np.array(v1[:size], dtype=np.float32)
    b = np.array(v2[:size], dtype=np.float32)
    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)
    if norm_a == 0 or norm_b == 0:
        return 0.0
    return float(np.dot(a, b) / (norm_a * norm_b))


def find_matching_moles(feature_vector, candidates, top_k=3):
    """
    Calcule la similarité cosinus entre feature_vector et chaque candidat.

    Paramètres:
        feature_vector (list[float]): vecteur 1044-dim de la nouvelle capture
        candidates (list[dict]): captures de référence, chacune avec:
            mole_id (int), mole_name (str), body_part (str|None),
            last_capture_id (int), cropped_image_path (str|None),
            feature_vector (list[float])
        top_k (int): nombre de résultats à retourner

    Retourne dict avec:
        matches (list[dict]): top_k résultats triés par score décroissant
        is_confident (bool): score_1 > 0.95 et écart > 0.05 avec le second
    """
    if not candidates:
        return {"matches": [], "is_confident": False}

    best = {}
    last_cap = {}

    for cand in candidates:
        mole_id = cand["mole_id"]
        stored = cand.get("feature_vector")
        if not stored:
            continue
        score = cosine_similarity(feature_vector, stored)

        if mole_id not in last_cap:
            last_cap[mole_id] = cand["last_capture_id"]

        if mole_id not in best or score > best[mole_id]["score"]:
            best[mole_id] = {
                "mole_id": mole_id,
                "mole_name": cand["mole_name"],
                "body_part": cand.get("body_part"),
                "score": float(score),
                "last_capture_id": cand["last_capture_id"],
                "cropped_image_path": cand.get("cropped_image_path"),
            }

    sorted_results = sorted(best.values(), key=lambda x: x["score"], reverse=True)
    matches = sorted_results[:top_k]

    is_confident = False
    if matches:
        score_1 = matches[0]["score"]
        if score_1 > 0.95:
            if len(matches) < 2 or (score_1 - matches[1]["score"]) > 0.05:
                is_confident = True

    return {"matches": matches, "is_confident": is_confident}


def find_matching_moles_json(feature_vector_json, candidates_json, top_k=3):
    """
    Wrapper JSON utilisé par Kotlin/Chaquopy pour éviter les problèmes
    de marshalling avec List<Map>. Accepte des chaînes JSON et retourne
    le résultat sérialisé en JSON string.
    """
    import json
    feature_vector = json.loads(feature_vector_json)
    candidates = json.loads(candidates_json)
    result = find_matching_moles(feature_vector, candidates, top_k)
    return json.dumps(result)
