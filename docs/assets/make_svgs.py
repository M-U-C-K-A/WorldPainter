#!/usr/bin/env python3
"""
Generates the animated SVG illustrations used in the README, in a light and a dark variant (transparent backgrounds).

Usage: python3 docs/assets/make_svgs.py
"""

import math
import os

OUT = os.path.dirname(os.path.abspath(__file__))

THEMES = {
    "light": {"fg": "#18181b", "muted": "#71717a", "faint": "#d4d4d8", "accent": "#f97316", "fill": "#18181b", "chip": "#f4f4f5"},
    "dark": {"fg": "#fafafa", "muted": "#a1a1aa", "faint": "#3f3f46", "accent": "#fb923c", "fill": "#fafafa", "chip": "#18181b"},
}

SANS = "Inter, ui-sans-serif, system-ui, -apple-system, 'Segoe UI', Helvetica, Arial, sans-serif"
MONO = "ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace"

COS30 = math.cos(math.radians(30))
SIN30 = 0.5


def iso(x, y, z, s, ox, oy):
    """Isometric projection of grid coordinates to screen coordinates."""
    return (ox + (x - y) * COS30 * s, oy + (x + y) * SIN30 * s - z * s)


def pts(points):
    return " ".join(f"{x:.1f},{y:.1f}" for x, y in points)


def svg(width, height, body, style):
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}" fill="none">\n'
            f'<style>{style}</style>\n{body}\n</svg>\n')


def terrain_height(x, y):
    """A mountain with a ridge, for the isometric terrain."""
    h = 3.2 * math.exp(-((x - 4.2) ** 2 + (y - 3.6) ** 2) / 5.5)
    h += 1.6 * math.exp(-((x - 1.6) ** 2 + (y - 5.6) ** 2) / 3.0)
    h += 0.35 * math.sin(x * 1.3) * math.cos(y * 1.1)
    return max(0.25, round(h * 2) / 2)


def blocky_terrain(t, n, s, ox, oy, height=terrain_height):
    """Isometric blocky terrain drawn with hairlines: top faces and the visible side faces."""
    parts = []
    # Draw back to front
    for d in range(2 * n - 1):
        for x in range(n):
            y = d - x
            if not (0 <= y < n):
                continue
            h = height(x, y)
            top = [iso(x, y, h, s, ox, oy), iso(x + 1, y, h, s, ox, oy), iso(x + 1, y + 1, h, s, ox, oy), iso(x, y + 1, h, s, ox, oy)]
            right = [iso(x + 1, y, h, s, ox, oy), iso(x + 1, y + 1, h, s, ox, oy), iso(x + 1, y + 1, 0, s, ox, oy), iso(x + 1, y, 0, s, ox, oy)]
            left = [iso(x, y + 1, h, s, ox, oy), iso(x + 1, y + 1, h, s, ox, oy), iso(x + 1, y + 1, 0, s, ox, oy), iso(x, y + 1, 0, s, ox, oy)]
            shade = min(1.0, h / 3.5)
            parts.append(f'<polygon points="{pts(right)}" fill="{t["fill"]}" fill-opacity="{0.05 + 0.05 * shade:.3f}" stroke="{t["fg"]}" stroke-opacity="0.55" stroke-width="1"/>')
            parts.append(f'<polygon points="{pts(left)}" fill="{t["fill"]}" fill-opacity="{0.02 + 0.03 * shade:.3f}" stroke="{t["fg"]}" stroke-opacity="0.55" stroke-width="1"/>')
            top_fill = t["accent"] if h >= 3 else t["fill"]
            top_opacity = 0.22 if h >= 3 else 0.015
            parts.append(f'<polygon points="{pts(top)}" fill="{top_fill}" fill-opacity="{top_opacity}" stroke="{t["fg"]}" stroke-opacity="0.85" stroke-width="1"/>')
    return "\n".join(parts)


def dotted_plane(t, n, s, ox, oy, margin=2):
    """A dotted grid on the ground plane, like the canvas of the editor."""
    dots = []
    for x in range(-margin, n + margin + 1):
        for y in range(-margin, n + margin + 1):
            px, py = iso(x, y, 0, s, ox, oy)
            dots.append(f'<circle cx="{px:.1f}" cy="{py:.1f}" r="1.1" fill="{t["muted"]}" fill-opacity="0.55"/>')
    return "\n".join(dots)


def hero(theme):
    t = THEMES[theme]
    w, h = 1200, 460
    style = f"""
.float {{ animation: float 7s ease-in-out infinite; }}
.float2 {{ animation: float 7s ease-in-out -3.5s infinite; }}
@keyframes float {{ 0%, 100% {{ transform: translateY(0px); }} 50% {{ transform: translateY(-7px); }} }}
.blink {{ animation: blink 1.1s steps(1) infinite; }}
@keyframes blink {{ 0%, 50% {{ opacity: 1; }} 50.01%, 100% {{ opacity: 0; }} }}
.bar1 {{ animation: bar 5s ease-in-out infinite; transform-box: fill-box; transform-origin: left; }}
.bar2 {{ animation: bar 5s ease-in-out -1.7s infinite; transform-box: fill-box; transform-origin: left; }}
.bar3 {{ animation: bar 5s ease-in-out -3.1s infinite; transform-box: fill-box; transform-origin: left; }}
@keyframes bar {{ 0%, 100% {{ transform: scaleX(0.35); }} 50% {{ transform: scaleX(1); }} }}
.draw {{ stroke-dasharray: 6 6; animation: dash 1.6s linear infinite; }}
@keyframes dash {{ to {{ stroke-dashoffset: -24; }} }}
.pulse {{ animation: pulse 2.4s ease-out infinite; transform-box: fill-box; transform-origin: center; }}
@keyframes pulse {{ 0% {{ transform: scale(0.6); opacity: 0.9; }} 100% {{ transform: scale(2.2); opacity: 0; }} }}
"""
    body = []
    # Text block
    body.append(f'<text x="64" y="118" font-family="{MONO}" font-size="13" letter-spacing="3" fill="{t["accent"]}">MINECRAFT MAP EDITOR · MODERNISED EDITION</text>')
    body.append(f'<text x="60" y="198" font-family="{SANS}" font-size="78" font-weight="800" letter-spacing="-2" fill="{t["fg"]}">WorldPainter</text>')
    body.append(f'<text x="64" y="240" font-family="{SANS}" font-size="20" fill="{t["muted"]}">Paint Minecraft worlds like a landscape artist.</text>')
    body.append(f'<text x="64" y="268" font-family="{SANS}" font-size="20" fill="{t["muted"]}">Procedural terrain, soft map edges, a modern editor.</text>')
    x = 64
    for label in ["Minecraft 26.x", "Java 21", "macOS · Windows · Linux", "GPL-3.0"]:
        width = 22 + len(label) * 7.6
        body.append(f'<rect x="{x}" y="296" width="{width:.0f}" height="28" rx="7" fill="{t["chip"]}" stroke="{t["faint"]}"/>')
        body.append(f'<text x="{x + 11}" y="315" font-family="{MONO}" font-size="12.5" fill="{t["fg"]}">{label}</text>')
        x += width + 10
    body.append(f'<text x="64" y="372" font-family="{MONO}" font-size="14" fill="{t["muted"]}">$ <tspan fill="{t["fg"]}">packaging/package.sh dmg</tspan></text>')
    body.append(f'<rect class="blink" x="290" y="360" width="8" height="16" fill="{t["accent"]}"/>')

    # Isometric terrain on the right
    n, s, ox, oy = 8, 30, 880, 150
    body.append('<g class="float">')
    body.append(dotted_plane(t, n, s, ox, oy))
    body.append(blocky_terrain(t, n, s, ox, oy))
    # Brush marker hovering over the peak
    px, py = iso(4.5, 4.0, terrain_height(4, 3) + 0.15, s, ox, oy)
    body.append(f'<ellipse class="pulse" cx="{px:.1f}" cy="{py:.1f}" rx="26" ry="15" stroke="{t["accent"]}" stroke-width="1.5"/>')
    body.append(f'<ellipse cx="{px:.1f}" cy="{py:.1f}" rx="26" ry="15" stroke="{t["accent"]}" stroke-width="1.5" stroke-dasharray="4 4"/>')
    body.append('</g>')

    # Floating inspector card, like the editor's panels
    cx, cy = 1000, 300
    body.append('<g class="float2">')
    card = [(cx, cy), (cx + 150, cy - 26), (cx + 150, cy + 64), (cx, cy + 90)]
    body.append(f'<polygon points="{pts(card)}" fill="{t["chip"]}" fill-opacity="0.92" stroke="{t["fg"]}" stroke-opacity="0.7"/>')
    body.append(f'<text transform="translate({cx + 12},{cy + 16}) skewY(-10)" font-family="{MONO}" font-size="11" fill="{t["accent"]}">[ PROCEDURAL ]</text>')
    for i, (label, cls) in enumerate([("ridges", "bar1"), ("warp", "bar2"), ("detail", "bar3")]):
        yy = cy + 36 + i * 17
        body.append(f'<text transform="translate({cx + 12},{yy}) skewY(-10)" font-family="{MONO}" font-size="10" fill="{t["muted"]}">{label}</text>')
        body.append(f'<g transform="translate({cx + 60},{yy - 14}) skewY(-10)"><rect width="76" height="4" rx="2" fill="{t["faint"]}"/><rect class="{cls}" width="76" height="4" rx="2" fill="{t["fg"]}"/></g>')
    body.append('</g>')
    # Leader line from the card to the terrain
    lx, ly = iso(6.5, 3.0, 1.0, s, ox, oy)
    body.append(f'<path class="draw" d="M {cx + 20} {cy + 4} L {lx:.1f} {ly:.1f}" stroke="{t["muted"]}" stroke-width="1"/>')
    body.append(f'<circle cx="{lx:.1f}" cy="{ly:.1f}" r="3" fill="{t["accent"]}"/>')
    return svg(w, h, "\n".join(body), style)


def procedural(theme):
    t = THEMES[theme]
    w, h = 620, 440
    style = """
.l1 { animation: stack 6s ease-in-out infinite; }
.l2 { animation: stack 6s ease-in-out -0.25s infinite; }
.l3 { animation: stack 6s ease-in-out -0.5s infinite; }
@keyframes stack { 0%, 100% { transform: translateY(0px); } 50% { transform: translateY(14px); } }
.draw { stroke-dasharray: 5 5; animation: dash 1.4s linear infinite; }
@keyframes dash { to { stroke-dashoffset: -20; } }
"""
    s, ox = 24, 230
    body = []
    labels = [("continents · smooth", 0), ("mountain ranges · ridged", 1), ("detail · billow", 2)]
    for i, (label, kind) in enumerate(labels):
        oy = 60 + i * 92
        cls = f"l{i + 1}"
        body.append(f'<g class="{cls}">')
        plane = [iso(0, 0, 0, s, ox, oy), iso(6, 0, 0, s, ox, oy), iso(6, 6, 0, s, ox, oy), iso(0, 6, 0, s, ox, oy)]
        body.append(f'<polygon points="{pts(plane)}" fill="{t["fill"]}" fill-opacity="0.03" stroke="{t["fg"]}" stroke-opacity="0.8"/>')
        # Hairline contour pattern on the plane
        for k in range(1, 6):
            line = []
            for j in range(0, 61):
                u = j / 10
                if kind == 0:
                    v = k + 0.35 * math.sin(u * 1.1 + k)
                elif kind == 1:
                    v = k + 0.45 * (1 - abs(math.sin(u * 1.6 + k * 0.7)))
                else:
                    v = k + 0.18 * math.sin(u * 4.5 + k * 2)
                line.append(iso(u, min(6, max(0, v)), 0, s, ox, oy))
            accent = (k == 3)
            body.append(f'<polyline points="{pts(line)}" stroke="{t["accent"] if accent else t["fg"]}" stroke-opacity="{1 if accent else 0.55}" stroke-width="1"/>')
        lx, ly = iso(6, 3, 0, s, ox, oy)
        body.append(f'<path class="draw" d="M {lx:.1f} {ly:.1f} L {lx + 70:.1f} {ly:.1f}" stroke="{t["muted"]}"/>')
        body.append(f'<text x="{lx + 78:.1f}" y="{ly + 4:.1f}" font-family="{MONO}" font-size="12" fill="{t["fg"]}">{label}</text>')
        body.append('</g>')
    body.append(f'<text x="24" y="{h - 16}" font-family="{MONO}" font-size="12" fill="{t["muted"]}">layers + rules → terrain, generated in parallel on every core</text>')
    return svg(w, h, "\n".join(body), style)


def edges(theme):
    t = THEMES[theme]
    w, h = 600, 420
    style = """
.cliff { animation: cliff 5s ease-in-out infinite; }
@keyframes cliff { 0%, 35% { opacity: 0.9; } 50%, 85% { opacity: 0.1; } 100% { opacity: 0.9; } }
.draw { stroke-dasharray: 6 6; animation: dash 1.6s linear infinite; }
@keyframes dash { to { stroke-dashoffset: -24; } }
"""
    s, ox, oy = 22, 110, 150
    n_map, n_total, depth = 4, 13, 3

    def profile(x):
        if x < n_map:
            return 4.0
        t_ = min(1.0, (x - n_map) / (n_total - n_map))
        smooth = t_ * t_ * (3 - 2 * t_)
        return 4.0 + (1.0 - 4.0) * smooth

    body = []
    # Strip of terrain columns along x, with a fixed depth
    for x in range(n_total):
        hgt = round(profile(x + 0.5) * 2) / 2
        top = [iso(x, 0, hgt, s, ox, oy), iso(x + 1, 0, hgt, s, ox, oy), iso(x + 1, depth, hgt, s, ox, oy), iso(x, depth, hgt, s, ox, oy)]
        front = [iso(x, depth, hgt, s, ox, oy), iso(x + 1, depth, hgt, s, ox, oy), iso(x + 1, depth, 0, s, ox, oy), iso(x, depth, 0, s, ox, oy)]
        in_map = x < n_map
        body.append(f'<polygon points="{pts(front)}" fill="{t["fill"]}" fill-opacity="{0.07 if in_map else 0.03}" stroke="{t["fg"]}" stroke-opacity="0.55"/>')
        body.append(f'<polygon points="{pts(top)}" fill="{t["accent"] if in_map else t["fill"]}" fill-opacity="{0.18 if in_map else 0.02}" stroke="{t["fg"]}" stroke-opacity="0.85"/>')
    # The old cliff: a dashed vertical drop at the map edge
    a = iso(n_map, depth, 4.0, s, ox, oy)
    b = iso(n_map, depth, 1.0, s, ox, oy)
    c = iso(n_total, depth, 1.0, s, ox, oy)
    body.append(f'<path class="cliff" d="M {a[0]:.1f} {a[1]:.1f} L {b[0]:.1f} {b[1]:.1f} L {c[0]:.1f} {c[1]:.1f}" stroke="{t["muted"]}" stroke-width="1.5" stroke-dasharray="4 4"/>')
    # Map edge marker and transition bracket
    e1 = iso(n_map, 0, 5.4, s, ox, oy)
    e2 = iso(n_map, depth, 0, s, ox, oy)
    body.append(f'<path d="M {e1[0]:.1f} {e1[1]:.1f} L {e1[0]:.1f} {e2[1] + 16:.1f}" stroke="{t["accent"]}" stroke-width="1" stroke-dasharray="2 4"/>')
    body.append(f'<text x="{e1[0] - 8:.1f}" y="{e1[1] - 8:.1f}" text-anchor="end" font-family="{MONO}" font-size="12" fill="{t["fg"]}">[ MAP EDGE ]</text>')
    br1 = iso(n_map, depth, 0, s, ox, oy + 58)
    br2 = iso(n_total, depth, 0, s, ox, oy + 58)
    body.append(f'<path class="draw" d="M {br1[0]:.1f} {br1[1]:.1f} L {br2[0]:.1f} {br2[1]:.1f}" stroke="{t["muted"]}"/>')
    mid = ((br1[0] + br2[0]) / 2, (br1[1] + br2[1]) / 2)
    body.append(f'<text x="{mid[0] + 10:.1f}" y="{mid[1] + 24:.1f}" font-family="{MONO}" font-size="12" fill="{t["fg"]}">transition · 16 to 4096 blocks</text>')
    body.append(f'<text x="24" y="{h - 16}" font-family="{MONO}" font-size="12" fill="{t["muted"]}">dashed: the cliff it replaces · Minecraft blends the last chunks</text>')
    return svg(w, h, "\n".join(body), style)


def interface(theme):
    t = THEMES[theme]
    w, h = 560, 330
    style = """
.tip { animation: tip 5s ease-in-out infinite; }
@keyframes tip { 0%, 30% { opacity: 0; } 40%, 80% { opacity: 1; } 90%, 100% { opacity: 0; } }
.cursor { animation: move 5s ease-in-out infinite; }
@keyframes move { 0% { transform: translate(60px, 40px); } 30%, 85% { transform: translate(0px, 0px); } 100% { transform: translate(60px, 40px); } }
.sel { animation: sel 5s steps(1) infinite; }
@keyframes sel { 0%, 49% { opacity: 0.18; } 50%, 100% { opacity: 0.32; } }
"""
    body = []

    def quad(x0, y0, x1, y1, z=0, s=1.0, ox=0, oy=0):
        return [iso(x0, y0, z, s, ox, oy), iso(x1, y0, z, s, ox, oy), iso(x1, y1, z, s, ox, oy), iso(x0, y1, z, s, ox, oy)]

    s, ox, oy = 20, 280, 30
    # Window
    body.append(f'<polygon points="{pts(quad(0, 0, 14, 11, 0, s, ox, oy))}" fill="{t["fill"]}" fill-opacity="0.025" stroke="{t["fg"]}" stroke-opacity="0.85"/>')
    # Header bar
    body.append(f'<polygon points="{pts(quad(0, 0, 14, 1, 0, s, ox, oy))}" fill="{t["fill"]}" fill-opacity="0.04" stroke="{t["fg"]}" stroke-opacity="0.6"/>')
    for i in range(3):
        cx, cy = iso(0.5 + i * 0.45, 0.5, 0, s, ox, oy)
        body.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="2.2" stroke="{t["fg"]}" stroke-opacity="0.6"/>')
    # Sidebar with layer rows
    body.append(f'<polygon points="{pts(quad(0, 1, 3.5, 11, 0, s, ox, oy))}" stroke="{t["fg"]}" stroke-opacity="0.6"/>')
    for r in range(8):
        y0 = 1.6 + r * 1.1
        row = quad(0.3, y0, 3.2, y0 + 0.7, 0, s, ox, oy)
        if r == 2:
            body.append(f'<polygon class="sel" points="{pts(row)}" fill="{t["accent"]}"/>')
        ex, ey = iso(0.6, y0 + 0.35, 0, s, ox, oy)
        body.append(f'<circle cx="{ex:.1f}" cy="{ey:.1f}" r="2.4" stroke="{t["fg"]}" stroke-opacity="0.7"/>')
        a, b = iso(1.1, y0 + 0.35, 0, s, ox, oy), iso(2.9 - (r % 3) * 0.4, y0 + 0.35, 0, s, ox, oy)
        body.append(f'<path d="M {a[0]:.1f} {a[1]:.1f} L {b[0]:.1f} {b[1]:.1f}" stroke="{t["fg"]}" stroke-opacity="0.55"/>')
    # Canvas with dot grid and a map
    for x in range(4, 14):
        for y in range(2, 11):
            cx, cy = iso(x + 0.5, y, 0, s, ox, oy)
            body.append(f'<circle cx="{cx:.1f}" cy="{cy:.1f}" r="0.9" fill="{t["muted"]}" fill-opacity="0.6"/>')
    body.append(f'<polygon points="{pts(quad(6, 3, 12, 9, 0, s, ox, oy))}" fill="{t["accent"]}" fill-opacity="0.12" stroke="{t["fg"]}" stroke-opacity="0.85"/>')
    for k in range(1, 5):
        line = [iso(6 + u / 10 * 6, 3 + k * 1.2 + 0.3 * math.sin(u / 2 + k), 0, s, ox, oy) for u in range(0, 11)]
        body.append(f'<polyline points="{pts(line)}" stroke="{t["fg"]}" stroke-opacity="0.45"/>')
    # Cursor and tooltip
    tx, ty = iso(9, 6, 0, s, ox, oy)
    body.append(f'<g class="cursor"><path d="M {tx:.1f} {ty:.1f} l 0 16 l 4.5 -4 l 3.5 7 l 2.5 -1.2 l -3.4 -6.8 l 6 -0.4 z" fill="{t["chip"]}" stroke="{t["fg"]}" stroke-linejoin="round"/></g>')
    body.append(f'<g class="tip"><rect x="{tx + 18:.1f}" y="{ty + 16:.1f}" width="168" height="42" rx="7" fill="{t["fg"]}"/>'
                f'<text x="{tx + 30:.1f}" y="{ty + 33:.1f}" font-family="{SANS}" font-size="12" font-weight="700" fill="{t["chip"]}">Raise mountain</text>'
                f'<text x="{tx + 30:.1f}" y="{ty + 49:.1f}" font-family="{SANS}" font-size="11" fill="{t["chip"]}">Hover any tool to learn it</text></g>')
    body.append(f'<text x="24" y="314" font-family="{MONO}" font-size="12" fill="{t["muted"]}">layer panel · dotted canvas · tooltips · light and dark</text>')
    return svg(w, h, "\n".join(body), style)


def divider(theme):
    t = THEMES[theme]
    w, h = 1200, 24
    style = ""
    body = [f'<line x1="12" y1="12" x2="1188" y2="12" stroke="{t["muted"]}" stroke-opacity="0.6" stroke-dasharray="2 5"/>',
            f'<path d="M 12 6 L 12 18 M 6 12 L 18 12 M 1188 6 L 1188 18 M 1182 12 L 1194 12" stroke="{t["muted"]}"/>',
            f'<circle cx="600" cy="12" r="3" fill="{t["accent"]}"/>']
    return svg(w, h, "\n".join(body), style)


if __name__ == "__main__":
    for theme in THEMES:
        for name, function in [("hero", hero), ("procedural", procedural), ("edges", edges), ("interface", interface), ("divider", divider)]:
            path = os.path.join(OUT, f"{name}-{theme}.svg")
            with open(path, "w") as f:
                f.write(function(theme))
            print("wrote", path)
