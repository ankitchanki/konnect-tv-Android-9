"""Tiny SVG drawing kit shared by the StashDrop and Konnect TV Plus UI renders."""
from html import escape
import os

FONT = "Roboto, 'Google Sans', Inter, 'Segoe UI', Arial, sans-serif"
MONO = "'Roboto Mono', Menlo, Consolas, monospace"


def esc(s):
    return escape(str(s), quote=True)


class Canvas:
    """Collects SVG elements in a w×h coordinate space."""

    def __init__(self, w, h, uid="s"):
        self.w, self.h, self.uid = w, h, uid
        self.parts, self.defs = [], []
        self._n = 0

    def id(self, base):
        self._n += 1
        return f"{self.uid}-{base}{self._n}"

    def add(self, s):
        self.parts.append(s)

    # ---------- primitives ----------
    def rect(self, x, y, w, h, r=0, fill="none", stroke=None, sw=1, opacity=None, extra=""):
        a = f'<rect x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}"'
        if r:
            a += f' rx="{r:g}"'
        a += f' fill="{fill}"'
        if stroke:
            a += f' stroke="{stroke}" stroke-width="{sw:g}"'
        if opacity is not None:
            a += f' opacity="{opacity:g}"'
        self.add(a + f" {extra}/>")

    def circle(self, cx, cy, r, fill="none", stroke=None, sw=1, opacity=None, extra=""):
        a = f'<circle cx="{cx:g}" cy="{cy:g}" r="{r:g}" fill="{fill}"'
        if stroke:
            a += f' stroke="{stroke}" stroke-width="{sw:g}"'
        if opacity is not None:
            a += f' opacity="{opacity:g}"'
        self.add(a + f" {extra}/>")

    def line(self, x1, y1, x2, y2, stroke, sw=1, cap="round", opacity=None):
        o = f' opacity="{opacity:g}"' if opacity is not None else ""
        self.add(f'<line x1="{x1:g}" y1="{y1:g}" x2="{x2:g}" y2="{y2:g}" stroke="{stroke}" stroke-width="{sw:g}" stroke-linecap="{cap}"{o}/>')

    def path(self, d, fill="none", stroke=None, sw=1, extra=""):
        a = f'<path d="{d}" fill="{fill}"'
        if stroke:
            a += f' stroke="{stroke}" stroke-width="{sw:g}" stroke-linecap="round" stroke-linejoin="round"'
        self.add(a + f" {extra}/>")

    def text(self, x, y, s, size=14, fill="#fff", weight=400, anchor="start", ls=0, family=FONT, opacity=None, italic=False):
        a = f'<text x="{x:g}" y="{y:g}" font-family="{family}" font-size="{size:g}" fill="{fill}"'
        if weight != 400:
            a += f' font-weight="{weight}"'
        if anchor != "start":
            a += f' text-anchor="{anchor}"'
        if ls:
            a += f' letter-spacing="{ls:g}"'
        if opacity is not None:
            a += f' opacity="{opacity:g}"'
        if italic:
            a += ' font-style="italic"'
        self.add(a + f">{esc(s)}</text>")

    def para(self, x, y, s, width, size=12, fill="#fff", lh=None, weight=400, max_lines=None, family=FONT, anchor="start", cw=0.53):
        """Word-wrapped text; returns the y just below the last line."""
        lh = lh or size * 1.38
        lines = wrap(s, width, size, cw)
        lines = [fit(ln, width, size, cw) for ln in lines]
        if max_lines and len(lines) > max_lines:
            lines = lines[:max_lines]
            lines[-1] = lines[-1].rstrip(" .,") + "…"
        for i, ln in enumerate(lines):
            self.text(x, y + i * lh, ln, size, fill, weight, anchor, family=family)
        return y + (len(lines) - 1) * lh + lh

    def group(self, inner, transform="", extra=""):
        t = f' transform="{transform}"' if transform else ""
        self.add(f"<g{t} {extra}>" + "".join(inner) + "</g>")

    def linear(self, stops, x1=0, y1=0, x2=1, y2=1):
        gid = self.id("lg")
        st = "".join(f'<stop offset="{o:g}" stop-color="{c}"{f" stop-opacity={chr(34)}{a:g}{chr(34)}" if a is not None else ""}/>' for o, c, a in stops)
        self.defs.append(f'<linearGradient id="{gid}" x1="{x1:g}" y1="{y1:g}" x2="{x2:g}" y2="{y2:g}">{st}</linearGradient>')
        return f"url(#{gid})"

    def radial(self, stops, cx=0.5, cy=0.5, r=0.5):
        gid = self.id("rg")
        st = "".join(f'<stop offset="{o:g}" stop-color="{c}" stop-opacity="{a:g}"/>' for o, c, a in stops)
        self.defs.append(f'<radialGradient id="{gid}" cx="{cx:g}" cy="{cy:g}" r="{r:g}">{st}</radialGradient>')
        return f"url(#{gid})"

    def clip_rect(self, x, y, w, h, r=0):
        cid = self.id("clip")
        self.defs.append(f'<clipPath id="{cid}"><rect x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}" rx="{r:g}"/></clipPath>')
        return f'clip-path="url(#{cid})"'

    def begin(self, extra="", transform=""):
        t = f' transform="{transform}"' if transform else ""
        self.add(f"<g{t} {extra}>")

    def end(self):
        self.add("</g>")

    def filter_shadow(self, dy=8, blur=14, opacity=0.45):
        fid = self.id("sh")
        self.defs.append(
            f'<filter id="{fid}" x="-30%" y="-30%" width="160%" height="170%">'
            f'<feDropShadow dx="0" dy="{dy}" stdDeviation="{blur / 2:g}" flood-color="#000" flood-opacity="{opacity:g}"/></filter>')
        return f'filter="url(#{fid})"'

    def blur_filter(self, sd=20):
        fid = self.id("bl")
        self.defs.append(f'<filter id="{fid}" x="-50%" y="-50%" width="200%" height="200%"><feGaussianBlur stdDeviation="{sd}"/></filter>')
        return f'filter="url(#{fid})"'

    def body(self):
        return ("<defs>" + "".join(self.defs) + "</defs>" if self.defs else "") + "".join(self.parts)

    def svg(self, title, desc=""):
        return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{self.w}" height="{self.h}" viewBox="0 0 {self.w} {self.h}" role="img">'
                f"<title>{esc(title)}</title>" + (f"<desc>{esc(desc)}</desc>" if desc else "") + self.body() + "</svg>\n")


def wrap(s, width, size, cw=0.53):
    out = []
    for para in str(s).split("\n"):
        words, line = para.split(" "), ""
        for w in words:
            trial = (line + " " + w).strip()
            if text_w(trial, size, cw) <= width or not line:
                line = trial
            else:
                out.append(line)
                line = w
        out.append(line)
    return out


def fit(s, width, size, cw=0.53):
    """Cuts an over-long single word (a URL) to fit, ending in an ellipsis."""
    if text_w(s, size, cw) <= width:
        return s
    while s and text_w(s + "…", size, cw) > width:
        s = s[:-1]
    return s + "…"


def text_w(s, size, cw=0.53):
    narrow = sum(1 for ch in s if ch in "iljtf.,:;'|! ·()[]")
    wide = sum(1 for ch in s if ch in "MWmw@%")
    caps = sum(1 for ch in s if ch.isupper())
    return size * (cw * (len(s) - narrow - wide) + 0.3 * narrow + 0.82 * wide + 0.1 * caps)


# ---------- 24-unit outline icons ----------
ICONS = {
    "home": "M4 10.5 12 4l8 6.5V20h-5.5v-5.5h-5V20H4z",
    "layers": "M12 3 3 8l9 5 9-5-9-5zM3 12.5l9 5 9-5M3 16.5l9 5 9-5",
    "tune": "M4 7h9M17 7h3M15 5v4M4 12h3M11 12h9M9 10v4M4 17h11M19 17h1M17 15v4",
    "search": "M10.5 4a6.5 6.5 0 1 1 0 13 6.5 6.5 0 0 1 0-13zM15.5 15.5 20 20",
    "close": "M6 6l12 12M18 6 6 18",
    "chev": "M9.5 6l6 6-6 6",
    "chevdown": "M6 9.5l6 6 6-6",
    "back": "M19 12H5M11 6l-6 6 6 6",
    "folder": "M3.5 6.5a1.5 1.5 0 0 1 1.5-1.5h4.5l2 2.2H19a1.5 1.5 0 0 1 1.5 1.5v9.3A1.5 1.5 0 0 1 19 19.5H5a1.5 1.5 0 0 1-1.5-1.5z",
    "crop": "M4 8V5a1 1 0 0 1 1-1h3M16 4h3a1 1 0 0 1 1 1v3M20 16v3a1 1 0 0 1-1 1h-3M8 20H5a1 1 0 0 1-1-1v-3",
    "check_circle": "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18zM8 12.2l2.7 2.7L16.2 9.4",
    "add": "M12 5v14M5 12h14",
    "share": "M12 3v12M8 7l4-4 4 4M6 11H5v9h14v-9h-1",
    "send": "M4 12 20 4l-4 16-4-7zM12 13l8-9",
    "more": "M6 12h.01M12 12h.01M18 12h.01",
    "delete": "M5 7h14M10 7V4.5h4V7M7 7l1 13h8l1-13M10.5 11v5M13.5 11v5",
    "bubble": "M8 13a4 4 0 1 1 0 .01M16.5 7.5a2.5 2.5 0 1 1 0 .01M16 17a2 2 0 1 1 0 .01",
    "bell": "M6 17V11a6 6 0 0 1 12 0v6l1.5 1.5h-15zM10 20.5h4",
    "hub": "M12 9.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5zM12 3v6.5M12 14.5V21M4.2 7.5l5.6 3.2M14.2 13.3l5.6 3.2M4.2 16.5l5.6-3.2M14.2 10.7l5.6-3.2",
    "assistant": "M5 21V4h11l-1.5 4L16 12H5",
    "autofix": "M4 20 15 9M14 5l1-2 1 2 2 1-2 1-1 2-1-2-2-1zM19 11l.6-1.2L21 9l-1.4-.8L19 7l-.6 1.2L17 9l1.4.8z",
    "image": "M4 5h16v14H4zM4 16l5-5 4 4 2.5-2.5L20 17M15.5 8.5a1 1 0 1 1 0 .01",
    "link": "M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1",
    "doc": "M6 3h8l4 4v14H6zM14 3v4h4M9 12h6M9 16h6",
    "video": "M3 7h12v10H3zM15 10.5l6-3.5v10l-6-3.5",
    "password": "M4 17h16M6 9l1.5 2.5M7.5 9 6 11.5M4.5 10.2h4.5M12 9l1.5 2.5M13.5 9 12 11.5M10.5 10.2H15M18 9l1.5 2.5M19.5 9 18 11.5M16.5 10.2h4.5",
    "inventory": "M3 4h18v4H3zM4.5 8v12h15V8M10 12h4",
    "file": "M6 3h8l4 4v14H6zM14 3v4h4",
    "play_circle": "M12 3a9 9 0 1 1 0 18 9 9 0 0 1 0-18zM10 8.5v7l5.5-3.5z",
    "copy": "M8 8h11v12H8zM5 16V4h11",
    "moon": "M20 14.5A8 8 0 0 1 9.5 4a8 8 0 1 0 10.5 10.5z",
    "sun": "M12 8a4 4 0 1 1 0 8 4 4 0 0 1 0-8zM12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4",
    "wifi": "M3 9a13 13 0 0 1 18 0M6 12.5a8.5 8.5 0 0 1 12 0M9 16a4 4 0 0 1 6 0M12 19.5h.01",
    "battery": "M3 7h16v10H3zM21 10.5v3",
    "signal": "M4 20h2v-3H4zM9 20h2v-7H9zM14 20h2V9h-2zM19 20h2V4h-2z",
    "kbd": "M3 6h18v12H3zM6.5 9.5h.01M9.5 9.5h.01M12.5 9.5h.01M15.5 9.5h.01M17.5 12.5h.01M6.5 12.5h.01M8 15h8",
    "vol_down": "M4 9.5h3.5L12 5.5v13l-4.5-4H4zM15.5 12h4.5",
    "vol_up": "M4 9.5h3.5L12 5.5v13l-4.5-4H4zM15.5 12h4.5M17.75 9.75v4.5",
    "mute": "M4 9.5h3.5L12 5.5v13l-4.5-4H4zM15.5 9.5l5 5M20.5 9.5l-5 5",
    "playpause": "M4 6v12l7-6zM14.5 6v12M19 6v12",
    "menu": "M4 7h16M4 12h16M4 17h16",
    "gear": "M12 9a3 3 0 1 1 0 6 3 3 0 0 1 0-6zM12 3v2.5M12 18.5V21M3 12h2.5M18.5 12H21M5.6 5.6l1.8 1.8M16.6 16.6l1.8 1.8M5.6 18.4l1.8-1.8M16.6 7.4l1.8-1.8",
    "input": "M4 6h16v10H4zM9 20h6M12 16v4M9 10.5l3 2.5 3-2.5",
    "plus": "M12 5v14M5 12h14",
    "sparkle": "M12 2.5 13.8 10.2 21.5 12 13.8 13.8 12 21.5 10.2 13.8 2.5 12 10.2 10.2z",
    "undo": "M8 8H14a5 5 0 0 1 0 10H8M8 8l3-3M8 8l3 3",
    "drag": "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01",
}


def icon(c, name, x, y, size=24, color="#fff", sw=1.8, fill=False):
    """Draws ICONS[name] with its 24-unit box placed at (x, y)."""
    k = size / 24
    d = ICONS[name]
    f = color if fill else "none"
    s = None if fill else color
    w = sw / k
    extra_cap = ' stroke-linecap="round" stroke-linejoin="round"' if not fill else ""
    stroke = f' stroke="{s}" stroke-width="{w:g}"' if s else ""
    c.add(f'<path transform="translate({x:g} {y:g}) scale({k:g})" d="{d}" fill="{f}"{stroke}{extra_cap}/>')


def write(path, svg):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        fh.write(svg)


def board(screens, cols, cell_w, cell_h, title, subtitle, out, gap=48, label_h=56, pad=64, bg="#08090C", fg="#ECEEF4", muted="#8A8D99", head=140):
    """Lays out (label, body_svg_fragment, w, h) screens on one labeled overview sheet."""
    rows = (len(screens) + cols - 1) // cols
    W = pad * 2 + cols * cell_w + (cols - 1) * gap
    H = head + pad + rows * (cell_h + label_h) + (rows - 1) * gap + pad
    parts = [f'<rect width="{W}" height="{H}" fill="{bg}"/>',
             f'<text x="{pad}" y="{pad + 30}" font-family="{FONT}" font-size="34" font-weight="700" fill="{fg}">{esc(title)}</text>',
             f'<text x="{pad}" y="{pad + 62}" font-family="{FONT}" font-size="16" fill="{muted}">{esc(subtitle)}</text>']
    for i, (label, frag, w, h) in enumerate(screens):
        r, col = divmod(i, cols)
        x = pad + col * (cell_w + gap) + (cell_w - w) / 2
        y = head + pad + r * (cell_h + label_h + gap)
        parts.append(f'<text x="{x:g}" y="{y + 22:g}" font-family="{FONT}" font-size="15" font-weight="600" fill="{fg}">{esc(label)}</text>')
        parts.append(f'<g transform="translate({x:g} {y + label_h - 16:g})">{frag}</g>')
    svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" viewBox="0 0 {W} {H}" role="img"><title>{esc(title)}</title>'
           + "".join(parts) + "</svg>\n")
    write(out, svg)
