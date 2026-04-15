#!/usr/bin/env python3
"""
Découpe SilhouetteHommeFemmeFaceDos.svg en 4 silhouettes autonomes.
Résultat dans docs/silhouettes/ : woman_front.svg, man_front.svg,
woman_back.svg, man_back.svg.

Les groupes sont dans layer1 (translate(-64.704637,-62.604335)) :
  g5268 → femme face   (haut gauche)
  g5423 → homme face   (haut droite)
  g5553 → femme dos    (bas gauche)
  g4158 → homme dos    (bas droite)
"""
import re
import os
from copy import deepcopy
import xml.etree.ElementTree as ET

SVG_NS = 'http://www.w3.org/2000/svg'

# Préserver les namespaces dans les fichiers de sortie
for prefix, uri in [
    ('', SVG_NS),
    ('dc',       'http://purl.org/dc/elements/1.1/'),
    ('cc',       'http://creativecommons.org/ns#'),
    ('rdf',      'http://www.w3.org/1999/02/22-rdf-syntax-ns#'),
    ('xlink',    'http://www.w3.org/1999/xlink'),
    ('inkscape', 'http://www.inkscape.org/namespaces/inkscape'),
    ('sodipodi', 'http://sodipodi.sourceforge.net/DTD/sodipodi-0.0.dtd'),
]:
    ET.register_namespace(prefix, uri)


def parse_translate(transform_str: str):
    if not transform_str:
        return 0.0, 0.0
    m = re.search(r'translate\(\s*([-\d.]+)\s*,\s*([-\d.]+)\s*\)', transform_str)
    if not m:
        print(f'  WARN: transform "{transform_str}" is not a simple translate — using (0, 0)')
        return 0.0, 0.0
    return float(m.group(1)), float(m.group(2))


def _parse_path_endpoints(d: str):
    """
    Parse SVG path data and return absolute (x, y) coordinate points.
    Handles M/L/C/S/Q/T/A/H/V/Z and their lowercase (relative) variants.
    Used for bounding-box estimation.
    """
    result = []
    cx, cy = 0.0, 0.0

    # Split on each command letter, keeping the letter
    for part in re.split(r'(?=[MmCcSsQqTtLlAaZzHhVv])', d):
        part = part.strip()
        if not part:
            continue
        cmd = part[0]
        nums = [float(n) for n in re.findall(
            r'[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?', part[1:]
        )]

        if cmd in 'Zz':
            pass
        elif cmd == 'H':
            for x in nums:
                cx = x
                result.append((cx, cy))
        elif cmd == 'h':
            for x in nums:
                cx += x
                result.append((cx, cy))
        elif cmd == 'V':
            for y in nums:
                cy = y
                result.append((cx, cy))
        elif cmd == 'v':
            for y in nums:
                cy += y
                result.append((cx, cy))
        elif cmd in 'Mm':
            for i in range(0, len(nums) - 1, 2):
                x, y = nums[i], nums[i + 1]
                if cmd == 'm':
                    cx += x; cy += y
                else:
                    cx, cy = x, y
                result.append((cx, cy))
        elif cmd in 'Ll':
            for i in range(0, len(nums) - 1, 2):
                x, y = nums[i], nums[i + 1]
                if cmd == 'l':
                    cx += x; cy += y
                else:
                    cx, cy = x, y
                result.append((cx, cy))
        elif cmd in 'Cc':
            # 6 numbers per segment: x1 y1 x2 y2 x y
            for i in range(0, len(nums) - 5, 6):
                x1, y1 = nums[i],     nums[i + 1]
                x2, y2 = nums[i + 2], nums[i + 3]
                x,  y  = nums[i + 4], nums[i + 5]
                if cmd == 'c':
                    x1 += cx; y1 += cy
                    x2 += cx; y2 += cy
                    x  += cx; y  += cy
                cx, cy = x, y
                result.extend([(x1, y1), (x2, y2), (cx, cy)])
        elif cmd in 'Ss':
            # 4 numbers per segment: x2 y2 x y
            for i in range(0, len(nums) - 3, 4):
                x2, y2 = nums[i],     nums[i + 1]
                x,  y  = nums[i + 2], nums[i + 3]
                if cmd == 's':
                    x2 += cx; y2 += cy
                    x  += cx; y  += cy
                cx, cy = x, y
                result.extend([(x2, y2), (cx, cy)])
        elif cmd in 'Qq':
            # 4 numbers per segment: x1 y1 x y
            for i in range(0, len(nums) - 3, 4):
                x1, y1 = nums[i],     nums[i + 1]
                x,  y  = nums[i + 2], nums[i + 3]
                if cmd == 'q':
                    x1 += cx; y1 += cy
                    x  += cx; y  += cy
                cx, cy = x, y
                result.extend([(x1, y1), (cx, cy)])
        elif cmd in 'Tt':
            # 2 numbers per segment: x y
            for i in range(0, len(nums) - 1, 2):
                x, y = nums[i], nums[i + 1]
                if cmd == 't':
                    x += cx; y += cy
                cx, cy = x, y
                result.append((cx, cy))
        elif cmd in 'Aa':
            # 7 numbers per arc: rx ry x-rot large-arc-flag sweep-flag x y
            for i in range(0, len(nums) - 6, 7):
                x, y = nums[i + 5], nums[i + 6]
                if cmd == 'a':
                    x += cx; y += cy
                cx, cy = x, y
                result.append((cx, cy))

    return result


def bbox_of_group(group, tx: float, ty: float, padding: float = 8.0):
    """Compute viewport bounding box for all paths in a group."""
    all_x, all_y = [], []
    for elem in group.iter(f'{{{SVG_NS}}}path'):
        d = elem.get('d', '')
        for px, py in _parse_path_endpoints(d):
            all_x.append(px + tx)
            all_y.append(py + ty)

    if not all_x:
        return 0.0, 0.0, 100.0, 200.0

    x0 = min(all_x) - padding
    y0 = min(all_y) - padding
    x1 = max(all_x) + padding
    y1 = max(all_y) + padding
    return x0, y0, x1 - x0, y1 - y0


def write_silhouette(group, tx: float, ty: float, name: str, out_dir: str):
    vx, vy, vw, vh = bbox_of_group(group, tx, ty)
    aspect = vh / vw if vw else 2.0
    if aspect > 3.0 or vw > 450:
        print(f'  WARN: {name} has unusual viewBox aspect ({aspect:.2f}) — verify visually!')

    root = ET.Element(f'{{{SVG_NS}}}svg')
    root.set('version', '1.1')
    root.set('viewBox', f'{vx:.3f} {vy:.3f} {vw:.3f} {vh:.3f}')
    root.set('width', '100')
    root.set('height', f'{100 * aspect:.1f}')

    g = ET.SubElement(root, 'g')
    g.set('transform', f'translate({tx:.6f},{ty:.6f})')
    for child in group:
        g.append(deepcopy(child))

    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, f'{name}.svg')
    ET.ElementTree(root).write(out_path, xml_declaration=True, encoding='UTF-8')
    print(f'  OK  {out_path}  (viewBox: {vx:.1f} {vy:.1f} {vw:.1f} {vh:.1f})')


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    src = os.path.join(script_dir, 'SilhouetteHommeFemmeFaceDos.svg')
    out_dir = os.path.join(script_dir, 'silhouettes')

    print(f'Source : {src}')
    tree = ET.parse(src)
    root = tree.getroot()

    layer1 = root.find(f'.//{{{SVG_NS}}}g[@id="layer1"]')
    if layer1 is None:
        raise RuntimeError('layer1 introuvable dans le SVG')

    tx, ty = parse_translate(layer1.get('transform', ''))
    print(f'Layer translate : ({tx}, {ty})')

    children = list(layer1)
    if len(children) < 4:
        raise RuntimeError(f'Attendu 4 groupes dans layer1, trouvé {len(children)}')

    configs = [
        ('woman_front', children[0]),
        ('man_front',   children[1]),
        ('woman_back',  children[2]),
        ('man_back',    children[3]),
    ]

    print(f'\nGénération des silhouettes dans {out_dir}/')
    for name, group in configs:
        print(f'  Processing {name} (id={group.get("id", "?")})')
        write_silhouette(group, tx, ty, name, out_dir)

    print('\nTerminé. Vérifie les 4 SVG dans un navigateur avant l\'import Android Studio.')
    print('Si une silhouette est mal orientée (femme↔homme), échange les noms dans `configs`.')


if __name__ == '__main__':
    main()
