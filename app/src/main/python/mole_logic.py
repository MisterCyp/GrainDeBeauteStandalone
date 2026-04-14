import cv2
import logging
import numpy as np

logger = logging.getLogger(__name__)

# --- 1. DETECTION DE L'EMBOUT (PIÈCE 3D) ---

def _validate_circle_edges(edges, cx, cy, radius, n_samples=72, min_edge_ratio=0.15):
    """
    Vérifie qu'un cercle candidat correspond à de vrais contours dans l'image.
    Échantillonne n_samples points sur la circonférence et mesure la densité de contours.
    Retourne True si au moins min_edge_ratio des points sont sur un contour fort.
    """
    h, w = edges.shape
    hits = 0
    for i in range(n_samples):
        angle = 2 * np.pi * i / n_samples
        x = int(cx + radius * np.cos(angle))
        y = int(cy + radius * np.sin(angle))
        if 0 <= x < w and 0 <= y < h and edges[y, x] > 0:
            hits += 1
    return (hits / n_samples) >= min_edge_ratio


def detect_spacer_ring(img):
    """
    Détecte le cercle intérieur de l'embout 3D (l'ouverture de 2cm).
    Retourne (centre, rayon) ou (None, None) si non trouvé.
    """
    h, w = img.shape[:2]
    # Redimensionnement pour stabiliser la détection
    scale = 1000 / max(h, w)
    img_res = cv2.resize(img, (int(w * scale), int(h * scale)))
    gray = cv2.cvtColor(img_res, cv2.COLOR_BGR2GRAY)

    # Flou pour réduire le bruit de l'impression 3D (stries)
    gray_blurred = cv2.GaussianBlur(gray, (9, 9), 2)

    # Carte des contours pour la validation
    edges = cv2.Canny(gray_blurred, 30, 80)

    circles = cv2.HoughCircles(
        gray_blurred,
        cv2.HOUGH_GRADIENT,
        dp=1.2,
        minDist=100,
        param1=50,
        param2=50,
        minRadius=int(300*scale),
        maxRadius=int(800*scale)
    )

    if circles is not None:
        circles = np.uint16(np.around(circles))
        for c in circles[0]:
            cx, cy, r = int(c[0]), int(c[1]), int(c[2])
            # Rejet des faux positifs : vérifier que la circonférence a de vrais contours
            if not _validate_circle_edges(edges, cx, cy, r):
                logger.info(f"[DETECTION] Cercle candidat rejeté (contours insuffisants) : cx={cx}, cy={cy}, r={r}")
                continue
            # Cercle validé — remise à l'échelle d'origine
            center = (int(cx / scale), int(cy / scale))
            radius = int(r / scale)
            logger.info(f"[DETECTION] Pièce 3D validée : centre={center}, rayon={radius}px")
            return center, radius

    return None, None

# --- 2. MASQUAGE ET DÉCOUPE ---

def mask_and_crop_spacer(img, center, radius, margin_px=30):
    """
    Applique un masque pour supprimer le plastique de l'embout 3D.
    margin_px : On réduit le masque de 30px pour être sûr de ne pas avoir de plastique.
    """
    h, w = img.shape[:2]
    mask = np.zeros((h, w), np.uint8)
    # On dessine un cercle légèrement plus petit pour la sécurité
    cv2.circle(mask, center, radius - margin_px, 255, -1)
    
    # On applique le masque : tout ce qui est en dehors du trou devient noir
    masked_img = cv2.bitwise_and(img, img, mask=mask)
    
    # On garde le rayon original pour la découpe du carré (pour ne pas décaler l'image)
    x, y, r = center[0], center[1], radius
    crop = masked_img[max(0, y-r):min(h, y+r), max(0, x-r):min(w, x+r)]
    return crop

# --- 2.5 NETTOYAGE CHIRURGICAL ---

def clean_skin_hairs(img):
    """
    Retire uniquement les poils noirs et fins en utilisant l'Inpainting.
    Préserve l'intégralité de la netteté et des détails de la peau.
    """
    # 1. Conversion en niveaux de gris pour la détection
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # 2. Black Hat : isole les éléments sombres et fins (poils)
    # On utilise un noyau rectangulaire de 17x17 (ajustable selon l'épaisseur des poils)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (17, 17))
    blackhat = cv2.morphologyEx(gray, cv2.MORPH_BLACKHAT, kernel)
    
    # 3. Masque des poils (seuil bas pour attraper même les poils fins)
    _, hair_mask = cv2.threshold(blackhat, 10, 255, cv2.THRESH_BINARY)
    
    # 4. Inpainting : on "rebouche" les zones du masque
    # INPAINT_TELEA est très bon pour préserver les textures locales
    clean_img = cv2.inpaint(img, hair_mask, 1, cv2.INPAINT_TELEA)
    
    return clean_img, hair_mask

# --- 3. SEGMENTATION DU GRAIN DE BEAUTÉ ---

def segment_mole_fixed_threshold(spacer_crop, thresh_ratio=0.75):
    """
    Isole le grain de beauté par seuillage fixe par rapport à la médiane de la peau.
    thresh_ratio : coefficient (ex: 0.75). Plus il est haut, plus le contour est large.
    """
    # 1. Conversion en espace Lab pour une meilleure séparation des couleurs
    lab = cv2.cvtColor(spacer_crop, cv2.COLOR_BGR2LAB)
    l_channel = lab[:,:,0] # Luminosité
    
    # 2. Amélioration locale du contraste (CLAHE)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8,8))
    l_enhanced = clahe.apply(l_channel)
    
    # 3. Création d'un masque pour ignorer le noir (le plastique de l'embout)
    gray = cv2.cvtColor(spacer_crop, cv2.COLOR_BGR2GRAY)
    _, skin_mask = cv2.threshold(gray, 5, 255, cv2.THRESH_BINARY)
    
    # 4. Détection du grain (basée sur la médiane de la peau)
    skin_pixels = l_enhanced[skin_mask > 0]
    if len(skin_pixels) == 0:
        return None, None
        
    mole_thresh = np.median(skin_pixels) * thresh_ratio
    _, mole_mask = cv2.threshold(l_enhanced, mole_thresh, 255, cv2.THRESH_BINARY_INV)
    
    # On restreint le masque à la zone de peau uniquement
    mole_mask = cv2.bitwise_and(mole_mask, mole_mask, mask=skin_mask)
    
    # 5. Nettoyage (suppression des petits bruits/poils isolés)
    kernel = np.ones((3,3), np.uint8)
    mole_mask = cv2.morphologyEx(mole_mask, cv2.MORPH_OPEN, kernel)
    
    # 6. Extraction du contour principal
    contours, _ = cv2.findContours(mole_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if contours:
        largest_contour = max(contours, key=cv2.contourArea)
        return mole_mask, largest_contour
        
    return None, None

def segment_mole_hysteresis(spacer_crop, core_ratio=0.75, wide_ratio=0.9):
    """
    Segmentation par hystérésis améliorée :
    1. Trouve le coeur du grain (seuil strict).
    2. Ne garde que le PLUS GRAND élément du coeur (isole le grain principal).
    3. Trouve la zone large (seuil permissif).
    4. Ne garde dans la zone large que ce qui touche le plus gros élément du coeur.
    """
    # Préparation
    lab = cv2.cvtColor(spacer_crop, cv2.COLOR_BGR2LAB)
    l_enhanced = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8,8)).apply(lab[:,:,0])
    gray = cv2.cvtColor(spacer_crop, cv2.COLOR_BGR2GRAY)
    _, skin_mask = cv2.threshold(gray, 5, 255, cv2.THRESH_BINARY)
    skin_pixels = l_enhanced[skin_mask > 0]
    if len(skin_pixels) == 0: return None, None
    median_l = np.median(skin_pixels)

    # 1. Masque "Coeur" (Strict)
    _, core_mask = cv2.threshold(l_enhanced, median_l * core_ratio, 255, cv2.THRESH_BINARY_INV)
    core_mask = cv2.bitwise_and(core_mask, core_mask, mask=skin_mask)

    # 2. ISOLATION DU COEUR PRINCIPAL
    # On cherche le plus grand contour du coeur pour éviter de propager depuis des poils
    core_contours, _ = cv2.findContours(core_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not core_contours: return None, None
    largest_core_cnt = max(core_contours, key=cv2.contourArea)
    
    # On crée un masque propre qui ne contient QUE le coeur principal
    clean_core_mask = np.zeros_like(core_mask)
    cv2.drawContours(clean_core_mask, [largest_core_cnt], -1, 255, -1)

    # 3. Masque "Large" (Permissif)
    _, wide_mask = cv2.threshold(l_enhanced, median_l * wide_ratio, 255, cv2.THRESH_BINARY_INV)
    wide_mask = cv2.bitwise_and(wide_mask, wide_mask, mask=skin_mask)

    # 4. Propagation depuis le coeur principal
    num_labels, labels, stats, centroids = cv2.connectedComponentsWithStats(wide_mask, connectivity=8)
    final_mask = np.zeros_like(wide_mask)
    
    for i in range(1, num_labels):
        component_mask = (labels == i).astype(np.uint8) * 255
        # On vérifie si ce composant large touche notre coeur principal nettoyé
        intersection = cv2.bitwise_and(component_mask, clean_core_mask)
        if np.any(intersection > 0):
            final_mask = cv2.bitwise_or(final_mask, component_mask)

    # 5. Extraction du contour final
    final_contours, _ = cv2.findContours(final_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if final_contours:
        largest_final_cnt = max(final_contours, key=cv2.contourArea)
        return final_mask, largest_final_cnt
        
    return None, None

def _apply_robust_smoothing(mask, kernel_size=21, simplify_factor=0.005):
    """
    Fonction interne pour lisser et simplifier le masque et le contour.
    """
    if mask is None: return None, None
    
    # 1. Fermeture morphologique (soude les bords proches)
    k_morph = np.ones((7, 7), np.uint8)
    mask_closed = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, k_morph)
    
    # 2. Flou Gaussien (arrondit les angles)
    blurred = cv2.GaussianBlur(mask_closed, (kernel_size, kernel_size), 0)
    _, smoothed_mask = cv2.threshold(blurred, 128, 255, cv2.THRESH_BINARY)
    
    # 3. Extraction et simplification du contour
    contours, _ = cv2.findContours(smoothed_mask, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    if not contours: return smoothed_mask, None
    
    cnt = max(contours, key=cv2.contourArea)
    
    # Approximation polygonale (réduit le nombre de points et supprime les boucles)
    epsilon = simplify_factor * cv2.arcLength(cnt, True)
    approx_cnt = cv2.approxPolyDP(cnt, epsilon, True)
    
    return smoothed_mask, approx_cnt

def segment_mole_smart(spacer_crop):
    """
    Choisit automatiquement la meilleure méthode de segmentation :
    - Calcule le ratio de croissance entre les seuils 0.75 et 0.85.
    - Si Ratio > 1.30 (bord flou) -> Utilise l'Hystérésis (0.75 -> 0.9).
    - Sinon (bord net) -> Utilise le Seuil Fixe 0.75.
    Applique ensuite un lissage robuste au résultat.
    """
    # 1. Calcul des aires pour décision
    _, cnt_75 = segment_mole_fixed_threshold(spacer_crop, thresh_ratio=0.75)
    _, cnt_85 = segment_mole_fixed_threshold(spacer_crop, thresh_ratio=0.85)
    
    if cnt_75 is None: return None, None, "NONE", 0
    
    area_75 = cv2.contourArea(cnt_75)
    if area_75 == 0: return None, None, "NONE", 0
    
    area_85 = cv2.contourArea(cnt_85) if cnt_85 is not None else area_75
    growth_ratio = area_85 / area_75
    
    # 2. Décision automatique du masque brut
    method_name = ""
    if growth_ratio > 1.30:
        raw_mask, _ = segment_mole_hysteresis(spacer_crop, core_ratio=0.75, wide_ratio=0.9)
        method_name = "HYST"
    else:
        raw_mask, _ = segment_mole_fixed_threshold(spacer_crop, thresh_ratio=0.75)
        method_name = "FIXE"

    # 3. Application du lissage robuste final
    m, c = _apply_robust_smoothing(raw_mask)
    return m, c, method_name, growth_ratio

# --- 4. CALCUL DES CARACTÉRISTIQUES (ABCDE) ---

def calculate_dimensions(contour, spacer_radius):
    """
    Calcule l'aire en mm2.
    Échelle : spacer_radius = 10mm (car l'ouverture fait 20mm de diamètre).
    """
    area_px = cv2.contourArea(contour)
    mm_per_px = 10.0 / spacer_radius
    area_mm2 = area_px * (mm_per_px ** 2)
    return area_mm2

def calculate_max_dimension(contour, spacer_radius):
    """
    Calcule la plus grande longueur (grand axe) du grain en mm.
    Retourne (max_dist_mm, p1, p2).
    """
    if contour is None or len(contour) < 2: return 0, None, None
    
    pts = contour.reshape(-1, 2)
    hull = cv2.convexHull(pts)
    hull_pts = hull.reshape(-1, 2)
    
    max_dist_px = 0
    p1, p2 = None, None
    for i in range(len(hull_pts)):
        for j in range(i + 1, len(hull_pts)):
            dist = np.linalg.norm(hull_pts[i] - hull_pts[j])
            if dist > max_dist_px:
                max_dist_px = dist
                p1, p2 = tuple(hull_pts[i]), tuple(hull_pts[j])
                
    mm_per_px = 10.0 / spacer_radius
    return max_dist_px * mm_per_px, p1, p2

def calculate_circularity(contour):
    """
    B (Border) : Calcule la régularité du bord (1.0 = cercle parfait).
    """
    perimeter = cv2.arcLength(contour, True)
    area = cv2.contourArea(contour)
    if perimeter == 0: return 0
    
    # Formule de circularité : 4*PI*Aire / Périmètre^2
    circularity = (4 * np.pi * area) / (perimeter ** 2)
    return min(circularity, 1.0)

def calculate_asymmetry(mole_mask, contour):
    """
    A (Asymmetry) : Calcule l'asymétrie réelle de la lésion.
    Cherche l'axe de rotation qui donne la meilleure symétrie possible (indépendant du téléphone).
    0.0 = Parfaitement symétrique, > 0.5 = Très asymétrique.
    """
    if mole_mask is None: return 0
    
    # 1. Centrage du grain pour la rotation
    coords = np.column_stack(np.where(mole_mask > 0))
    if len(coords) == 0: return 0
    center = coords.mean(axis=0) # (y, x)
    h, w = mole_mask.shape
    
    def get_asym_at_angle(angle):
        # Rotation du masque
        M = cv2.getRotationMatrix2D((float(center[1]), float(center[0])), angle, 1.0)
        rotated = cv2.warpAffine(mole_mask, M, (w, h))
        
        # Recadrage serré après rotation
        cnts, _ = cv2.findContours(rotated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        if not cnts: return 1.0
        rx, ry, rw, rh = cv2.boundingRect(max(cnts, key=cv2.contourArea))
        roi = rotated[ry:ry+rh, rx:rx+rw]
        
        # Calcul du score de pliage horizontal
        h_flip = cv2.flip(roi, 1)
        inter = np.sum(cv2.bitwise_and(roi, h_flip))
        union = np.sum(cv2.bitwise_or(roi, h_flip))
        return 1.0 - (inter / union) if union > 0 else 1.0

    # 2. Recherche du meilleur angle (de 0 à 180° par pas de 5)
    best_score = 1.0
    for angle in range(0, 180, 5):
        score = get_asym_at_angle(angle)
        if score < best_score:
            best_score = score
            
    return best_score

def calculate_color_variation(spacer_crop, mole_mask):
    """
    C (Color) : Calcule l'écart-type moyen des couleurs (Lab) au sein du grain.
    Applique une réduction de 10 pixels (érosion) pour éviter les bords.
    """
    # 1. Érosion pour ne garder que le coeur
    kernel = np.ones((7, 7), np.uint8)
    eroded_mask = cv2.erode(mole_mask, kernel, iterations=1)
    
    # 2. Utilisation du masque érodé (ou original si grain trop petit)
    final_mask = eroded_mask if np.any(eroded_mask > 0) else mole_mask
    
    # 3. Conversion en Lab pour une variation plus proche de la perception
    lab = cv2.cvtColor(spacer_crop, cv2.COLOR_BGR2LAB)
    pixels = lab[final_mask > 0]
    
    if len(pixels) == 0: return 0
    
    # On calcule l'écart-type sur L, a et b
    stds = np.std(pixels, axis=0)
    # On renvoie la moyenne des écarts-types (Score de polychromie)
    return np.mean(stds)

# --- 5. ALIGNEMENT ET ORIENTATION (GPS CUTANÉ) ---

def extract_alignment_features(img, skin_mask):
    """
    Extrait les points d'intérêt (poils, pores, taches) de la peau.
    À utiliser sur l'image de RÉFÉRENCE (la première).
    """
    # ORB est excellent pour détecter les textures (poils, pores)
    # nfeatures=2000 pour avoir assez de détails sur la peau
    orb = cv2.ORB_create(nfeatures=2000)
    kp, des = orb.detectAndCompute(img, mask=skin_mask)
    return kp, des

def align_to_reference(target_img, target_skin_mask, ref_kp, ref_des):
    """
    Recale l'image cible sur l'image de référence en utilisant les points d'intérêt.
    Retourne l'image cibles alignée (pivotée et translatée).
    """
    # 1. Extraction des points de l'image actuelle
    orb = cv2.ORB_create(nfeatures=2000)
    kp, des = orb.detectAndCompute(target_img, mask=target_skin_mask)
    
    if des is None or ref_des is None:
        return target_img, 0 # Pas d'alignement possible

    # 2. Mise en correspondance (Matching)
    bf = cv2.BFMatcher(cv2.NORM_HAMMING, crossCheck=True)
    matches = bf.match(des, ref_des)
    matches = sorted(matches, key=lambda x: x.distance)

    # 3. Calcul de la transformation (Homographie ou Affine)
    if len(matches) > 15:
        src_pts = np.float32([ kp[m.queryIdx].pt for m in matches ]).reshape(-1, 1, 2)
        dst_pts = np.float32([ ref_kp[m.trainIdx].pt for m in matches ]).reshape(-1, 1, 2)
        
        # On utilise une transformation Affine Partielle (Rotation + Translation + Échelle)
        # C'est plus stable que l'homographie pour la peau
        matrix, inliers = cv2.estimateAffinePartial2D(src_pts, dst_pts)
        
        if matrix is not None:
            h, w = target_img.shape[:2]
            # On applique la transformation pour "recaler" l'image sur le Nord de la référence
            aligned_img = cv2.warpAffine(target_img, matrix, (w, h))
            return aligned_img, np.sum(inliers)
            
    return target_img, 0
