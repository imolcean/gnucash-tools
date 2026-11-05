"""Builds gnucash-togo-icon.svg: the GnuCash icon with a palm tree behind the chart board.

Usage: python -I make_icon.py <gnucash-icon.svg> <out-dir>
Writes <out-dir>/gnucash-togo-icon.svg and <out-dir>/render.html (draws it to canvases for PNG export).
"""
import math
import re
import sys
import base64
from pathlib import Path

src, out = Path(sys.argv[1]), Path(sys.argv[2])
svg = src.read_text(encoding="utf-8")

# Shrink and lower the original art so the palm crown fits above the board.
SCALE, DX, DY = 0.78, 5.6, 10.6


def bezier(p0, p1, p2, t):
    return tuple((1 - t) ** 2 * a + 2 * (1 - t) * t * b + t * t * c for a, b, c in zip(p0, p1, p2))


def frond(p0, p1, p2, width, n=30):
    """A palm frond along a quadratic curve: a midrib with leaflets on both sides, narrow at the stalk and tip."""
    left, right, rib = [], [], []
    for i in range(n + 1):
        t = i / n
        x, y = bezier(p0, p1, p2, t)
        dx = 2 * (1 - t) * (p1[0] - p0[0]) + 2 * t * (p2[0] - p1[0])
        dy = 2 * (1 - t) * (p1[1] - p0[1]) + 2 * t * (p2[1] - p1[1])
        length = math.hypot(dx, dy) or 1
        nx, ny = -dy / length, dx / length
        taper = 0.15 if t < 0.08 else math.sin(math.pi * (0.08 + 0.92 * t)) ** 0.7 * (1 - t) ** 0.15
        tip = i % 2 and 0.08 <= t < 1
        w = max(0.12, width * taper * (1.0 if tip else 0.4))
        # leaflets sweep back towards the base: pull the outer points back along the curve
        back = 0.7 * w if tip else 0
        bx, by = dx / length * back, dy / length * back
        left.append((x + nx * w - bx, y + ny * w - by))
        right.append((x - nx * w - bx, y - ny * w - by))
        if t <= 0.92:
            rib.append((x, y))
    pts = left + right[::-1]
    d = "M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"
    rib_d = "M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in rib)
    return (f'<path d="{d}" style="fill:url(#palmLeaf);stroke:#2e5c02;stroke-width:0.45;stroke-linejoin:round"/>'
            f'<path d="{rib_d}" style="fill:none;stroke:#2e5c02;stroke-width:0.35;stroke-linecap:round;opacity:0.8"/>')


def cubic(p0, p1, p2, p3, t):
    return tuple((1 - t) ** 3 * a + 3 * (1 - t) ** 2 * t * b + 3 * (1 - t) * t * t * c + t ** 3 * d
                 for a, b, c, d in zip(p0, p1, p2, p3))


def palm_trunk(p0, p1, p2, p3, base_width, top_width, n=40, rings=9):
    """A tapering trunk along a cubic curve, with ring marks across it."""
    left, right, marks = [], [], []
    for i in range(n + 1):
        t = i / n
        x, y = cubic(p0, p1, p2, p3, t)
        x2, y2 = cubic(p0, p1, p2, p3, min(1, t + 0.01))
        x1, y1 = cubic(p0, p1, p2, p3, max(0, t - 0.01))
        length = math.hypot(x2 - x1, y2 - y1) or 1
        nx, ny = -(y2 - y1) / length, (x2 - x1) / length
        w = (base_width + (top_width - base_width) * t) / 2
        left.append((x + nx * w, y + ny * w))
        right.append((x - nx * w, y - ny * w))
    for k in range(1, rings + 1):
        t = k / (rings + 1)
        i = round(t * n)
        (lx, ly), (rx, ry) = left[i], right[i]
        mx, my = (lx + rx) / 2, (ly + ry) / 2
        # bow the ring a little towards the base
        x0, y0 = cubic(p0, p1, p2, p3, max(0, t - 0.03))
        bx, by = (x0 - mx) * 0.5, (y0 - my) * 0.5
        marks.append(f'<path d="M {lx:.2f},{ly:.2f} Q {mx + bx:.2f},{my + by:.2f} {rx:.2f},{ry:.2f}" '
                     'style="fill:none;stroke:#5c3a0e;stroke-width:0.35;opacity:0.75"/>')
    pts = left + right[::-1]
    d = "M " + " L ".join(f"{x:.2f},{y:.2f}" for x, y in pts) + " Z"
    return (f'<path d="{d}" style="fill:url(#palmTrunk);stroke:#5c3a0e;stroke-width:0.45;stroke-linejoin:round"/>'
            + "".join(marks))


CROWN = (19.0, 4.2)
# The trunk comes in from beyond the left edge (its base is out of frame), rises left of the board
# and bends over the top to the crown above the board's centre.
trunk = palm_trunk((-3.5, 37.0), (2.2, 17.0), (5.0, 5.6), (CROWN[0] + 0.3, CROWN[1] + 0.4), 3.6, 1.7)
rings = ""
# fronds behind/above the board (drawn before the original art): they arch up and droop
back = [
    frond(CROWN, (11.0, 0.6), (3.5, 6.5), 1.9),
    frond(CROWN, (27.0, 0.6), (35.5, 7.0), 1.9),
    frond(CROWN, (14.5, 0.4), (9.5, 2.2), 1.6),
    frond(CROWN, (24.0, 0.4), (30.0, 2.0), 1.6),
]
# fronds hanging from behind over the board's top edge (drawn after the original art)
front = [
    frond(CROWN, (14.0, 3.5), (12.0, 13.0), 1.7),
    frond(CROWN, (22.5, 4.5), (22.5, 14.2), 1.7),
    frond(CROWN, (26.5, 3.5), (29.5, 13.0), 1.7),
]
coconuts = "".join(
    f'<circle cx="{x}" cy="{y}" r="0.95" style="fill:#8f5902;stroke:#4a2c03;stroke-width:0.32"/>'
    for x, y in [(18.2, 5.2), (20.0, 5.3), (19.1, 6.0)])

defs = """
    <linearGradient id="palmLeaf" x1="0" y1="0" x2="0" y2="1">
      <stop offset="0" style="stop-color:#8ae234"/><stop offset="1" style="stop-color:#4e9a06"/>
    </linearGradient>
    <linearGradient id="palmTrunk" x1="0" y1="0" x2="1" y2="0">
      <stop offset="0" style="stop-color:#c17d11"/><stop offset="1" style="stop-color:#8f5902"/>
    </linearGradient>
"""
svg = svg.replace("</defs>", defs + "</defs>", 1)
svg = re.sub(r'(<g\s+id="layer1")', f'<g id="palm-back">{trunk}{rings}{"".join(back)}</g>\n  '
             rf'<g transform="translate({DX},{DY}) scale({SCALE})">\n  \1', svg, count=1)
# close the scale group after layer1, then draw the hanging fronds and coconuts on top
idx = svg.rfind("</g>")
svg = svg[:idx] + f'</g>\n  </g>\n  <g id="palm-front">{"".join(front)}{coconuts}</g>' + svg[idx + 4:]
svg = svg.replace("<dc:title>", "<dc:title>gnucash-togo icon, after ", 1)

(out / "gnucash-togo-icon.svg").write_text(svg, encoding="utf-8")
data = base64.b64encode(svg.encode("utf-8")).decode("ascii")
(out / "render.html").write_text(f"""<!doctype html><body style="background:#ddd">
<img id="src" src="data:image/svg+xml;base64,{data}" width="256" height="256" style="background:#fff">
<img id="src2" src="data:image/svg+xml;base64,{data}" width="48" height="48">
<img id="src3" src="data:image/svg+xml;base64,{data}" width="32" height="32">
<script>
function png(size, bg) {{
  const c = document.createElement('canvas'); c.width = c.height = size;
  const g = c.getContext('2d');
  if (bg) {{ g.fillStyle = bg; g.fillRect(0, 0, size, size); }}
  g.drawImage(document.getElementById('src'), 0, 0, size, size);
  return c.toDataURL('image/png');
}}
</script></body>""", encoding="utf-8")
print("ok")
