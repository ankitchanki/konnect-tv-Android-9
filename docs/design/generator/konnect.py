"""Renders every Konnect TV Plus phone screen and sheet, plus Glowlauncher on the TV, as SVG."""
import sys
from svgkit import Canvas, icon, write, board, text_w, wrap, FONT, MONO

OUT = sys.argv[1]
W, H = 390, 844

DARK = dict(Bg="#0C0C0F", Card="#16161B", Border="#222229", Raised="#1F1F26", RaisedBorder="#2A2A33", Text="#ECEEF4",
            Muted="#8A8D99", Accent="#7CF0B0", OnAccent="#06140C", Danger="#FF7A7A", Glass="#1E1E24", GlassA=.97,
            Hairline="#FFFFFF", Knob="#777777", Handle="#555555", Scrim=.6, OrbOn="#10201A")
LIGHT = dict(Bg="#F4F5F7", Card="#FFFFFF", Border="#E3E5EA", Raised="#EEF0F3", RaisedBorder="#DADDE3", Text="#14151A",
             Muted="#6B6F7B", Accent="#12B76A", OnAccent="#FFFFFF", Danger="#E5484D", Glass="#FFFFFF", GlassA=.96,
             Hairline="#000000", Knob="#B0B4BD", Handle="#C4C7CE", Scrim=.4, OrbOn="#E3F8EE")
C = dict(DARK)
MINT, CYAN = "#3FD6A0", "#9FF7FF"
TABS = ["Remote", "Apps", "Detox", "Boost", "Settings"]
CLEAR = 110


def use(theme):
    C.clear()
    C.update(theme)


# ---------- chrome ----------
def phone(uid, draw, light=False):
    use(LIGHT if light else DARK)
    c = Canvas(W, H, uid)
    c.begin(c.clip_rect(0, 0, W, H, 36))
    c.rect(0, 0, W, H, 0, C["Bg"])
    draw(c)
    c.text(28, 25, "9:41", 14, C["Text"], 600)
    icon(c, "signal", 300, 12, 15, C["Text"], fill=True)
    icon(c, "wifi", 320, 11, 16, C["Text"])
    c.rect(342, 15, 20, 10, 3, "none", C["Text"], 1.2)
    c.rect(344, 17, 13, 6, 1.5, C["Text"])
    c.rect(W / 2 - 54, H - 10, 108, 4, 2, C["Text"], opacity=.5)
    c.end()
    c.rect(.5, .5, W - 1, H - 1, 36, "none", "#2A2A33" if not light else "#D0D3DA", 1)
    use(DARK)
    return c


def tab_icon(c, tab, cx, cy, col, s=22):
    x, y = cx - s / 2, cy - s / 2
    sw = 1.8
    if tab == "Settings":
        c.circle(cx, cy, s * .3, "none", col, sw)
        c.circle(cx, cy, s * .1, "none", col, sw)
        import math
        for i in range(8):
            a = i * math.pi / 4
            c.line(cx + math.cos(a) * s * .3, cy + math.sin(a) * s * .3, cx + math.cos(a) * s * .43, cy + math.sin(a) * s * .43, col, sw)
    elif tab == "Remote":
        c.rect(x + s * .28, y + s * .06, s * .44, s * .88, s * .14, "none", col, sw)
        c.circle(cx, y + s * .32, s * .09, "none", col, sw)
        c.circle(cx, y + s * .62, s * .04, col)
    elif tab == "Apps":
        g = s * .38
        for i in range(2):
            for j in range(2):
                c.rect(x + s * .08 + i * (g + s * .08), y + s * .08 + j * (g + s * .08), g, g, s * .1, "none", col, sw)
    elif tab == "Detox":
        c.circle(cx, cy, s * .4, "none", col, sw)
        c.line(x + s * .22, y + s * .22, x + s * .78, y + s * .78, col, sw)
    elif tab == "Boost":
        p = [(.58, .04), (.2, .56), (.48, .56), (.4, .96), (.8, .42), (.52, .42)]
        c.path("M" + " L".join(f"{x + a * s:g} {y + b * s:g}" for a, b in p) + "Z", "none", col, 1.6)


def tab_bar(c, current):
    y = H - 24 - 64 - 12
    glass(c, 16, y, W - 32, 64, 32)
    for i, t in enumerate(TABS):
        cx = 16 + (W - 32) / 5 * (i + .5)
        on = t == current
        tab_icon(c, t, cx, y + 22, C["Accent"] if on else C["Muted"])
        c.text(cx, y + 50, t, 12, C["Text"] if on else C["Muted"], 400, "middle")


def glass(c, x, y, w, h, r, top_only=False):
    c.rect(x, y, w, h, r, C["Glass"], opacity=C["GlassA"], extra=c.filter_shadow(-2 if top_only else 4, 18, .35))
    c.rect(x + .5, y + .5, w - 1, h - 1, r, "none", C["Hairline"], 1, opacity=.08)


def card(c, x, y, w, h, r=18, fill=None):
    c.rect(x, y, w, h, r, fill or C["Card"], C["Border"], 1)


def h1(c, y, s, x=16):
    c.text(x, y, s, 24, C["Text"], 600)


def small(c, x, y, s, col=None, size=12, **k):
    c.text(x, y, s, size, col or C["Muted"], **k)


def body(c, x, y, s, col=None, size=14, weight=400, **k):
    c.text(x, y, s, size, col or C["Text"], weight, **k)


def para(c, x, y, s, w, col=None, size=12, lh=None, **k):
    return c.para(x, y, s, w, size, col or C["Muted"], lh or size * 1.42, **k)


def section(c, x, y, s, col=None):
    c.text(x, y, s.upper(), 11, col or C["Muted"], 500, ls=.8)


def primary(c, x, y, w, label, enabled=True, h=50):
    c.rect(x, y, w, h, 16, C["Accent"] if enabled else C["Raised"])
    c.text(x + w / 2, y + h / 2 + 5, label, 14, C["OnAccent"] if enabled else C["Muted"], 600, "middle")


def secondary(c, x, y, w, label, col=None, h=48):
    c.rect(x, y, w, h, 16, C["Raised"], C["RaisedBorder"])
    c.text(x + w / 2, y + h / 2 + 5, label, 14, col or C["Text"], 400, "middle")


def toggle(c, x, y, on):
    c.rect(x, y, 44, 26, 13, C["Accent"] if on else C["RaisedBorder"])
    c.circle(x + (30 if on else 12), y + 13, 10, C["OnAccent"] if on else C["Knob"])


def check(c, x, y, on, enabled=True):
    c.rect(x, y, 20, 20, 6, C["Accent"] if on else "none", C["Accent"] if on else (C["Handle"] if enabled else C["Border"]), 1.5)
    if on:
        c.path(f"M{x + 5.2:g} {y + 10.6:g} L{x + 8.8:g} {y + 14.2:g} L{x + 15:g} {y + 6.4:g}", "none", C["OnAccent"], 2)


def monogram(c, x, y, label, s=36):
    import colorsys
    hue = (java_hash(label) & 0x7fffffff) % 360
    light = C["Bg"] == LIGHT["Bg"]
    bg = hsl(hue, .45 if light else .25, .9 if light else .22)
    fg = hsl(hue, .6, .35 if light else .8)
    c.rect(x, y, s, s, 11, bg)
    c.text(x + s / 2, y + s / 2 + 5, label[:1].upper(), 14 if s < 40 else 17, fg, 600, "middle")


def java_hash(s):
    h = 0
    for ch in s:
        h = (31 * h + ord(ch)) & 0xFFFFFFFF
    return h


def hsl(h, s, l):
    import colorsys
    r, g, b = colorsys.hls_to_rgb(h / 360, l, s)
    return "#%02X%02X%02X" % (round(r * 255), round(g * 255), round(b * 255))


def app_icon(c, x, y, label, s=36):
    """Approximates real TV app icons for a few well-known apps; otherwise the letter tile."""
    brand = {"Netflix": ("#000000", "#E50914", "N"), "YouTube": ("#FFFFFF", "#FF0000", "▶"), "Prime Video": ("#00A8E1", "#FFFFFF", "prime"),
             "Spotify": ("#1DB954", "#000000", "♫"), "Disney+": ("#0E2A6B", "#FFFFFF", "D+"), "Plex": ("#1F1F1F", "#E5A00D", "›")}
    if label in brand:
        bg, fg, t = brand[label]
        c.rect(x, y, s, s, 11, bg, "#2A2A33" if bg in ("#000000", "#FFFFFF") else None, .5)
        c.text(x + s / 2, y + s / 2 + s * .14, t, s * (.26 if len(t) > 2 else .42), fg, 700, "middle")
    else:
        monogram(c, x, y, label, s)


def field(c, x, y, w, hint, value="", focused=False, h=48):
    c.rect(x, y, w, h, 14, C["Card"], C["Accent"] if focused else C["RaisedBorder"])
    if value:
        body(c, x + 14, y + h / 2 + 5, value)
        if focused:
            c.rect(x + 15 + text_w(value, 14), y + 15, 1.5, 18, 0, C["Accent"])
    else:
        c.text(x + 14, y + h / 2 + 5, c_fit(hint, w - 28, 14), 14, C["Muted"])
        if focused:
            c.rect(x + 14, y + 15, 1.5, 18, 0, C["Accent"])


def c_fit(s, w, size):
    from svgkit import fit
    return fit(s, w, size)


def chip(c, x, y, label, on):
    w = text_w(label, 13) + 28
    c.rect(x, y, w, 34, 14, C["Accent"] if on else C["Card"], C["Accent"] if on else C["Border"], opacity=None)
    if on:
        c.rect(x, y, w, 34, 14, C["Bg"], opacity=.87)
        c.rect(x, y, w, 34, 14, "none", C["Accent"], 1, opacity=.4)
    c.text(x + w / 2, y + 22, label, 13, C["Accent"] if on else C["Text"], 400, "middle")
    return w


def sheet(c, draw, height):
    """Frosted bottom sheet over a dimmed screen; draw(c, x, y, w) lays out content and returns used height."""
    c.rect(0, 0, W, H, 0, "#000", opacity=C["Scrim"])
    y = H - height
    glass(c, 0, y, W, height + 40, 28, True)
    c.rect(W / 2 - 18, y + 10, 36, 4, 2, C["Handle"])
    draw(c, 18, y + 26, W - 36)


def spinner(c, cx, cy, r, col, sw=2):
    import math
    a0, a1 = -math.pi / 2, math.pi * .9
    c.path(f"M{cx + r * math.cos(a0):g} {cy + r * math.sin(a0):g} A{r} {r} 0 1 1 {cx + r * math.cos(a1):g} {cy + r * math.sin(a1):g}", "none", col, sw)


def arc(c, cx, cy, r, frac, col, sw, start=-90):
    import math
    if frac >= .999:
        c.circle(cx, cy, r, "none", col, sw)
        return
    a0 = math.radians(start)
    a1 = math.radians(start + 360 * frac)
    large = 1 if frac > .5 else 0
    c.path(f"M{cx + r * math.cos(a0):g} {cy + r * math.sin(a0):g} A{r} {r} 0 {large} 1 {cx + r * math.cos(a1):g} {cy + r * math.sin(a1):g}", "none", col, sw)


# ---------- sample data ----------
SAVED = [dict(name="Living room TV", tag="Home", host="192.168.1.24", port=5555, live=41733),
         dict(name="Bedroom Mi Box", tag="", host="192.168.1.37", port=5555, live=None)]
FOUND = [("Sony BRAVIA 4K", "192.168.1.52", "ready to pair"), ("Friend's Google TV", "192.168.1.61", "wireless debugging on")]
APPS = [("Netflix", "com.netflix.ninja", "Enabled", False), ("YouTube", "com.google.android.youtube.tv", "Enabled", False),
        ("Prime Video", "com.amazon.amazonvideo.livingroom", "Enabled", False), ("Spotify", "com.spotify.tv.android", "Enabled", False),
        ("Plex", "com.plexapp.android", "Enabled", False), ("Kodi", "org.xbmc.kodi", "Enabled", False), ("VLC", "org.videolan.vlc", "Enabled", False)]


# ---------- Connect ----------
def connect(c, busy=True, first=False):
    small(c, 16, 70, "Konnect TV Plus", C["Accent"])
    h1(c, 100, "Connect a TV")
    small(c, 374, 96, "Settings / logs", C["Accent"], anchor="end")
    y = 118
    if busy:
        card(c, 16, y, W - 32, 48)
        spinner(c, 40, y + 24, 9, C["Accent"])
        body(c, 62, y + 29, "Connecting to Living room TV…")
        small(c, 362, y + 29, "Cancel", C["Accent"], anchor="end")
        y += 58
    if not first:
        section(c, 16, y + 18, "Saved TVs")
        para(c, 16, y + 38, "Tap to reconnect using the saved IP. Edit names, tags or addresses for each TV.", W - 32)
        y += 68
        for tv in SAVED:
            h = 78 if tv["tag"] else 62
            card(c, 16, y, W - 32, h)
            monogram(c, 30, y + (h - 36) / 2, tv["name"])
            ty = y + 26
            body(c, 78, ty, tv["name"])
            if tv["tag"]:
                small(c, 78, ty + 18, tv["tag"], C["Accent"]); ty += 18
            port = tv["live"] or tv["port"]
            small(c, 78, ty + 18, f"{tv['host']}:{port} · {'on Wi-Fi now' if tv['live'] else 'saved address'}")
            small(c, 360, y + h / 2 + 4, "Edit", C["Accent"], anchor="end")
            y += h + 10
    section(c, 16, y + 18, "Found on your Wi-Fi")
    y += 30
    if first:
        card(c, 16, y, W - 32, 44)
        spinner(c, 38, y + 22, 7, C["Muted"])
        small(c, 58, y + 26, "Looking for Android TVs…")
        y += 54
    else:
        for name, host, state in FOUND:
            card(c, 16, y, W - 32, 62)
            monogram(c, 30, y + 13, name)
            body(c, 78, y + 27, name)
            small(c, 78, y + 45, f"{host} · {state}")
            body(c, 360, y + 36, "Pair" if "pair" in state else "Connect", C["Accent"], anchor="end")
            y += 72
    y += 4
    secondary(c, 16, y, W - 32, "Enter IP address manually")
    y += 66
    para(c, 16, y, "First time? On the TV: Settings › Device Preferences › About › tap Build 7 times. In Developer options, turn on USB debugging.\n\n"
         "Android TV 9–10: turn on Network debugging if available, then connect to the TV IP on port 5555 and approve Allow debugging on the TV. "
         "Some TVs need a one-time USB connection to a computer and the command adb tcpip 5555.\n\n"
         "Android TV 11+: if Wireless debugging is available, open Pair device with pairing code. Use the pairing port for Pair, and the separate connection port for Connect.", W - 32)


def pair_sheet(c):
    connect(c, busy=False)

    def d(c, x, y, w):
        body(c, x, y + 8, "Pair with Sony BRAVIA 4K", size=17)
        para(c, x, y + 32, "On the TV: Developer options › Wireless debugging › Pair device with pairing code.", w)
        bx = x + (w - (6 * 44 + 5 * 8)) / 2
        code = "4829"
        for i in range(6):
            c.rect(bx + i * 52, y + 70, 44, 54, 12, C["Card"], C["Accent"] if i == len(code) else C["RaisedBorder"])
            if i < len(code):
                c.text(bx + i * 52 + 22, y + 105, code[i], 24, C["Text"], 600, "middle")
        primary(c, x, y + 140, w, "Pair", False)
        numpad(c, y + 210)
    sheet(c, d, 470)


def numpad(c, y):
    c.rect(0, y, W, H - y, 0, "#1B1B21" if C["Bg"] == DARK["Bg"] else "#D6D9DF")
    keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫"]
    for i, k in enumerate(keys):
        r, col = divmod(i, 3)
        x = 8 + col * ((W - 16) / 3)
        yy = y + 10 + r * 52
        if k:
            c.rect(x + 4, yy, (W - 16) / 3 - 8, 44, 8, "#2C2C34" if C["Bg"] == DARK["Bg"] else "#FFFFFF")
            c.text(x + (W - 16) / 6, yy + 29, k, 22, C["Text"], 400, "middle")


def manual_sheet(c):
    connect(c, busy=False)

    def d(c, x, y, w):
        body(c, x, y + 8, "Connect by IP", size=17)
        small(c, x, y + 30, "Find the IP on the TV: Settings › Network › your Wi-Fi.")
        secondary(c, x, y + 46, w, "Use last IP · Living room TV")
        field(c, x, y + 106, w, "TV IP, e.g. 192.168.1.24", "192.168.1.", True)
        field(c, x, y + 164, w, "Port (5555, or the one under Wireless debugging)", "5555")
        field(c, x, y + 222, w, "TV name (optional), e.g. Living room")
        field(c, x, y + 280, w, "Tag (optional), e.g. Friend's TV")
        ny = para(c, x, y + 348, "Remembered on this phone after a successful connection. Each TV keeps its own name, tag and address.", w)
        primary(c, x, ny + 4, w, "Connect")
        secondary(c, x, ny + 64, w, "Pair with code instead")
    sheet(c, d, 560)


def edit_sheet(c, base=None):
    (base or connect)(c, busy=False) if base is None else base(c)

    def d(c, x, y, w):
        body(c, x, y + 8, "Edit saved TV", size=17)
        small(c, x, y + 36, "TV name")
        field(c, x, y + 46, w, "Living room TV", "Living room TV", True)
        small(c, x, y + 114, "Tag (optional)")
        field(c, x, y + 124, w, "Home, Friend's TV, Bedroom…", "Home")
        small(c, x, y + 192, "Address for the next connection")
        field(c, x, y + 202, w, "TV IP", "192.168.1.24")
        field(c, x, y + 260, w, "Connection port", "5555")
        primary(c, x, y + 324, w, "Save TV")
        secondary(c, x, y + 384, w, "Forget this TV", C["Danger"])
    sheet(c, d, 480)


# ---------- Remote ----------
def tv_header(c, name="Living room TV"):
    y = 70
    c.circle(24, y - 4, 4, C["Accent"])
    body(c, 36, y + 1, name)
    icon(c, "chevdown", 36 + text_w(name, 14) + 2, y - 13, 20, C["Muted"])
    icon(c, "moon" if C["Bg"] == LIGHT["Bg"] else "sun", 314, y - 14, 20, C["Muted"])
    c.text(362, y + 1, "•••", 14, C["Muted"], 400, "end")


def media_card(c, y, error=False, seeking=False):
    h = 130 if not error else 206
    card(c, 16, y, W - 32, h)
    x, w = 30, W - 60
    body(c, x, y + 26, "Media controls unavailable" if error else "Stranger Things · S4 E1 — Netflix")
    frac = 0 if error else .38
    mline(c, x, y + 36, w, frac, not error)
    small(c, x, y + 62, "0:00" if error else "29:17")
    small(c, x + w, y + 62, "—:—" if error else "1:17:52", anchor="end")
    small(c, x, y + 80, "Seeking… waiting for the player" if seeking else ("Connect a TV to read playback and volume." if error else "Tap or drag the line to seek"))
    small(c, x, y + 104, "Volume")
    small(c, x + w, y + 104, "Unavailable" if error else "42%", anchor="end")
    mline(c, x, y + 112, w, 0 if error else .42, not error)
    if error:
        small(c, x, y + 146, "Waiting for TV volume…")
        small(c, x, y + 164, "Other remote buttons still work. See Diagnostic logs.")
        primary(c, x, y + 176, w, "Retry media controls", h=34)
    return h


def mline(c, x, y, w, frac, enabled):
    col = C["Accent"] if enabled else C["Muted"]
    c.line(x + 3, y + 6, x + w - 3, y + 6, C["Border"], 6)
    if frac:
        c.line(x + 3, y + 6, x + w * frac, y + 6, col, 6)
    c.circle(x + max(6, w * frac), y + 6, 6, col)


def remote(c, error=False):
    tv_header(c)
    cx, cy = W / 2, 90 + 132
    c.circle(cx, cy, 132, C["Card"], C["Border"])
    for ang, (dx, dy) in [(180, (0, -88)), (0, (0, 88)), (90, (-88, 0)), (270, (88, 0))]:
        c.add(f'<g transform="translate({cx + dx:g} {cy + dy:g}) rotate({ang + 90})"><path d="M-5 -10 L5 0 L-5 10" fill="none" stroke="{C["Muted"]}" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round"/></g>')
    c.circle(cx, cy, 50, C["Raised"], C["RaisedBorder"])
    c.text(cx, cy + 6, "OK", 17, C["Accent"], 600, "middle")
    y = cy + 132 + 14
    # Vol −/+ keys are gone: the volume line in the media card does that job.
    # Everyday keys sit in one row of equal keys under the media card.
    key_row(c, y, [("Type", "kbd"), ("YouTube", "yt"), ("Hotstar", "hotstar"), ("Play/Pause", "playpause"), ("Menu", "menu")])
    y += 76
    y += media_card(c, y, error) + 2
    key_row(c, y, [("Home", "home"), ("Back", "back"), ("Settings", "gear"), ("Mute", "mute"), ("Off", "power")])
    tab_bar(c, "Remote")


def key_row(c, y, keys):
    """Equal-width remote keys across the screen."""
    kw = (W - 32 - 10 * (len(keys) - 1)) / len(keys)
    x = 16
    for label, ic in keys:
        c.rect(x, y, kw, 66, 18, C["Card"], C["Border"])
        if ic == "power":
            power(c, x + kw / 2, y + 26, C["Danger"])
        elif ic == "yt":
            c.rect(x + kw / 2 - 14, y + 16, 28, 20, 6, "#FF0000")
            c.path(f"M{x + kw / 2 - 4:g} {y + 21:g} L{x + kw / 2 + 6:g} {y + 26:g} L{x + kw / 2 - 4:g} {y + 31:g}Z", "#FFFFFF")
        elif ic == "hotstar":
            c.rect(x + kw / 2 - 13, y + 13, 26, 26, 7, "#0F1E5C")
            icon(c, "sparkle", x + kw / 2 - 8, y + 18, 16, "#FFFFFF", fill=True)
        else:
            icon(c, ic, x + kw / 2 - 11, y + 15, 22, C["Text"], 1.7)
        c.text(x + kw / 2, y + 54, label, 11, C["Muted"], 400, "middle")
        x += kw + 10


def power(c, cx, cy, col, s=20):
    import math
    r = s * .38
    c.path(f"M{cx + r * math.cos(math.radians(-60)):g} {cy + r * math.sin(math.radians(-60)):g} A{r} {r} 0 1 1 {cx + r * math.cos(math.radians(240)):g} {cy + r * math.sin(math.radians(240)):g}", "none", col, 1.8)
    c.line(cx, cy - s * .46, cx, cy - s * .04, col, 1.8)


def type_sheet(c):
    remote(c)

    def d(c, x, y, w):
        body(c, x, y + 8, "Type on TV", size=17)
        small(c, x, y + 30, "Select a search box on the TV first.")
        field(c, x, y + 44, w, "Text to send", "stranger things", True)
        hw = (w - 10) / 2
        secondary(c, x, y + 106, hw, "⌫ Delete")
        primary(c, x + hw + 10, y + 106, hw, "Send", h=48)
        keyboard(c, y + 172)
    sheet(c, d, 470)


def keyboard(c, y):
    dark = C["Bg"] == DARK["Bg"]
    c.rect(0, y, W, H - y, 0, "#1B1B21" if dark else "#D6D9DF")
    rows = ["qwertyuiop", "asdfghjkl", "zxcvbnm"]
    kw = (W - 12) / 10
    for r, row in enumerate(rows):
        off = 6 + (10 - len(row)) * kw / 2
        for i, k in enumerate(row):
            x = off + i * kw
            c.rect(x + 3, y + 10 + r * 50, kw - 6, 42, 6, "#2C2C34" if dark else "#FFFFFF")
            c.text(x + kw / 2, y + 38 + r * 50, k, 18, C["Text"], 400, "middle")
    c.rect(6 + kw * 2 + 3, y + 160, kw * 6 - 6, 42, 6, "#2C2C34" if dark else "#FFFFFF")


def menu_sheet(c):
    remote(c)

    def d(c, x, y, w):
        body(c, x, y + 8, "Living room TV", size=17)
        small(c, x, y + 28, "192.168.1.24:41733")
        small(c, x, y + 46, "Home")
        yy = y + 60
        for label, col in [("Edit TV name, tag or IP", None), ("Refresh everything", None), ("Switch TV", None), ("Forget this TV", C["Danger"])]:
            secondary(c, x, yy, w, label, col)
            yy += 60
        small(c, x, yy + 10, "Konnect TV Plus · Android 9+ fork · github.com/ankitchanki/konnect-tv-Android-9", size=11)
    sheet(c, d, 370)


# ---------- Apps ----------
def apps(c, filt="Installed"):
    h1(c, 74, "Apps")
    small(c, 82, 72, "143", size=14)
    field(c, 16, 92, W - 32, "Search apps")
    x = 16
    for f in ["Installed", "System", "Disabled"]:
        x += chip(c, x, 152, f, f == filt) + 8
    y = 198
    for label, pkg, state, dis in APPS:
        card(c, 16, y, W - 32, 74)
        app_icon(c, 30, y + 19, label)
        body(c, 78, y + 24, label, C["Muted"] if dis else None)
        small(c, 78, y + 42, c_fit(pkg, 250, 12))
        small(c, 78, y + 60, state, C["Accent"] if dis else None)
        c.text(360, y + 42, "⋯", 18, C["Muted"], 400, "end")
        y += 82
    tab_bar(c, "Apps")


def app_sheet(c, disabled=False):
    apps(c, "Disabled" if disabled else "Installed")

    def d(c, x, y, w):
        label, pkg = ("Samsung TV Plus", "com.samsung.tvplus") if disabled else ("Kodi", "org.xbmc.kodi")
        app_icon(c, x, y, label, 44)
        body(c, x + 56, y + 18, label, size=17)
        small(c, x + 56, y + 38, pkg)
        if disabled:
            ny = para(c, x, y + 70, "Free ad-supported channels. Safe to turn off if you don't watch them.", w)
            primary(c, x, ny + 6, w, "Turn back on")
            return
        para(c, x, y + 70, "Your app · installed from the phone or Play Store.", w)
        hw = (w - 10) / 2
        secondary(c, x, y + 92, hw, "Open")
        secondary(c, x + hw + 10, y + 92, hw, "Force stop")
        secondary(c, x, y + 152, w, "Tap again: erase all its data")
        secondary(c, x, y + 212, w, "Turn off (undo anytime)")
        secondary(c, x, y + 272, w, "Uninstall", C["Danger"])
    sheet(c, d, 230 if disabled else 370)


# ---------- Detox ----------
def detox(c):
    h1(c, 74, "Detox")
    small(c, 272, 72, "Refresh", C["Accent"], anchor="end")
    small(c, 370, 72, "⟲ History · 3", C["Accent"], size=13, anchor="end")
    card(c, 16, 92, W - 32, 112)
    small(c, 30, 116, "Xiaomi · MiTV-AXFR0 · Android 9")
    body(c, 30, 140, "6 optional apps reviewed", C["Accent"], 16, 600)
    para(c, 30, 162, "Matches actual packages on this TV. Review feature trade-offs before disabling. System apps are turned off, never deleted.", W - 60)
    section(c, 16, 230, "Optional · review trade-offs · 6")
    rows = [("PatchWall ads", "Home-screen promos. Banners disappear.", "com.miui.systemAdSolution", True),
            ("Mi TV feedback", "Usage reports to the vendor.", "com.xiaomi.mitv.upgrade.feedback", True),
            ("Google Play Movies", "Keep if you rent films.", "com.google.android.videos", False),
            ("Live channels demo", "Sample channel list.", "com.android.tv.demo", True)]
    y = 244
    for label, reason, pkg, on in rows:
        h = 96
        card(c, 16, y, W - 32, h)
        check(c, 30, y + h / 2 - 10, on)
        body(c, 62, y + 24, label)
        small(c, 62, y + 42, reason)
        small(c, 62, y + 60, pkg)
        small(c, 62, y + 80, "Xiaomi · Rule source", C["Accent"])
        y += h + 8
    tab_bar(c, "Detox")


def detox_bottom(c):
    """The lower half of Detox, scrolled."""
    h1(c, 74, "Detox")
    y = 100
    for label, n, shown in [("Needed by TV · 18 · locked", 18, False), ("Not reviewed · 41 · kept enabled", 41, False), ("Already off · 2", 2, True)]:
        section(c, 16, y, label)
        c.text(374, y, "HIDE" if shown else "SHOW", 11, C["Accent"], 500, "end", ls=.8)
        y += 14
        if shown:
            y = para(c, 16, y + 6, "Includes apps disabled before Plus was installed and factory-disabled components. Restore only features you need.", W - 32)
            for label2, pkg, inst in [("Samsung TV Plus", "com.samsung.tvplus", True), ("Live TV", "com.android.tv", False)]:
                card(c, 16, y, W - 32, 150)
                body(c, 30, y + 26, label2)
                small(c, 30, y + 44, pkg)
                small(c, 30, y + 62, "Already disabled / off" if inst else "Removed for TV profile · original system APK available", C["Accent"])
                small(c, 30, y + 80, "Optional · free channels." if inst else "Tuner app. Restore if you use antenna/HDMI channels.")
                primary(c, 30, y + 92, W - 60, "Enable" if inst else "Restore original app", h=44)
                y += 160
        y += 20
    section(c, 16, y, "Your apps · 7 · kept")
    para(c, 16, y + 18, "Netflix · YouTube · Prime Video · Spotify · Plex · Kodi · VLC", W - 32)
    primary(c, 16, y + 48, W - 32, "Turn off 5 apps")
    tab_bar(c, "Detox")


def history_sheet(c):
    detox(c)

    def d(c, x, y, w):
        body(c, x, y + 8, "Undo history", size=17)
        yy = y + 24
        for label, when in [("PatchWall ads turned off", "21 Oct 2026, 9:12 AM"), ("Mi TV feedback turned off", "21 Oct 2026, 9:12 AM"), ("Live channels demo turned off", "20 Oct 2026, 8:40 PM")]:
            card(c, x, yy, w, 62)
            body(c, x + 14, yy + 26, label)
            small(c, x + 14, yy + 44, when)
            body(c, x + w - 14, yy + 36, "Undo", C["Accent"], anchor="end")
            yy += 70
        secondary(c, x, yy + 4, w, "Undo everything")
    sheet(c, d, 330)


# ---------- Boost ----------
def stat(c, x, y, w, title, free, total):
    card(c, x, y, w, 186)
    c.text(x + w / 2, y + 26, title.upper(), 11, C["Muted"], 500, "middle", ls=.8)
    cx, cy = x + w / 2, y + 96
    c.circle(cx, cy, 50.5, "none", C["Border"], 9)
    arc(c, cx, cy, 50.5, free / total, C["Accent"], 9)
    c.text(cx, cy + 2, f"{free:.1f} GB", 17, C["Text"], 600, "middle")
    small(c, cx, cy + 20, "free", anchor="middle")
    small(c, cx, y + 172, f"{total:.1f} GB total", anchor="middle")


def boost(c, on=False, busy=False):
    h1(c, 74, "Boost")
    hw = (W - 42) / 2
    stat(c, 16, 92, hw, "RAM", .6, 1.5)
    stat(c, 26 + hw, 92, hw, "Storage", 2.9, 8.0)
    small(c, W / 2, 302, "RAM free before Konnect TV Plus: 0.3 GB", anchor="middle")
    y = 318
    card(c, 16, y, W - 32, 330)
    cx, cy = W / 2, y + 101
    if on:
        c.circle(cx, cy, 95, c.radial([(0, MINT, .28), (1, MINT, 0)]))
    c.circle(cx, cy, 89.5, "none", C["Border"], 7)
    if on:
        arc(c, cx, cy, 89.5, 1, c.linear([(0, MINT, None), (1, CYAN, None)]), 7)
    c.circle(cx, cy, 66, C["OrbOn"] if on else C["Raised"], C["Border"])
    if busy:
        spinner(c, cx, cy, 18, C["Accent"])
    else:
        sp = 46 * (1.08 if on else 1)
        c.add(f'<g transform="rotate({90 if on else 0} {cx} {cy})">')
        icon(c, "sparkle", cx - sp / 2, cy - sp / 2, sp, C["Accent"] if on else C["Muted"], 1.6)
        c.add("</g>")
    body(c, cx, y + 228, "Glowlauncher", size=20, weight=600, anchor="middle")
    pill = "WORKING…" if busy else ("ACTIVE ON LIVING ROOM TV" if on else "TAP THE ORB TO CLEAN")
    pw = text_w(pill, 11) + 30
    c.rect(cx - pw / 2, y + 242, pw, 24, 12, C["Accent"] if on else C["Raised"], opacity=.13 if on else 1)
    c.text(cx, y + 258, pill, 11, C["Accent"] if on else C["Muted"], 700, "middle", ls=.6)
    small(c, cx, y + 292, "A calm home screen with only the apps you pick. Undo anytime.", anchor="middle")
    y += 340
    card(c, 16, y, W - 32, 184 if on else 128)
    body(c, 30, y + 28, "TV wallpaper")
    para(c, 30, y + 48, "Choose a photo from your phone, preview its crop and send it to Living room TV." if on else "Turn on Glowlauncher above to personalize its background.", W - 60)
    primary(c, 30, y + 66 + (14 if on else 0), W - 60, "Choose wallpaper", on)
    if on:
        secondary(c, 30, y + 140, W - 60, "Restore glow background", h=36)
        y += 194
    else:
        y += 138
    for t, s, v in [("Animations off", "Menus open instantly", True), ("Screensaver off", "No background work while idle", False)]:
        card(c, 16, y, W - 32, 62)
        body(c, 30, y + 27, t)
        small(c, 30, y + 45, s)
        toggle(c, 330, y + 18, v)
        y += 72
    tab_bar(c, "Boost")


def boost_lower(c):
    """Boost scrolled to its bottom."""
    h1(c, 74, "Boost")
    y = 100
    for t, s, v in [("Animations off", "Menus open instantly", True), ("Screensaver off", "No background work while idle", False)]:
        card(c, 16, y, W - 32, 62)
        body(c, 30, y + 27, t)
        small(c, 30, y + 45, s)
        toggle(c, 330, y + 18, v)
        y += 72
    card(c, 16, y, W - 32, 62)
    body(c, 30, y + 27, "Sleep after")
    small(c, 30, y + 45, "TV turns off when nobody uses it")
    body(c, 360, y + 36, "30 min ›", C["Accent"], anchor="end")
    y += 76
    secondary(c, 16, y, W - 32, "Kill background apps + free cache")
    tab_bar(c, "Boost")


def sleep_sheet(c):
    boost_lower(c)

    def d(c, x, y, w):
        body(c, x, y + 8, "Sleep after", size=17)
        xx = x
        for m in [5, 15, 30, 60]:
            xx += chip(c, xx, y + 26, f"{m} min", m == 30) + 8
    sheet(c, d, 120)


def launcher_menu(c):
    boost(c, on=True)

    def d(c, x, y, w):
        body(c, x, y + 8, "Glowlauncher", size=17)
        small(c, x, y + 30, "Active on Living room TV.")
        primary(c, x, y + 46, w, "Edit home apps")
        secondary(c, x, y + 108, w, "Turn off, bring back the TV's own home")
    sheet(c, d, 220)


def clear_wall_sheet(c):
    boost(c, on=True)

    def d(c, x, y, w):
        body(c, x, y + 8, "Remove TV wallpaper?")
        small(c, x, y + 30, "Restore Glowlauncher's original background on Living room TV.")
        primary(c, x, y + 46, w, "Restore background")
    sheet(c, d, 150)


ACCENTS = [("Mint", "#3FD6A0"), ("Sky", "#4FB3FF"), ("Violet", "#9B7BFF"), ("Rose", "#FF6B9A"), ("Amber", "#FFB547"), ("Snow", "#E8ECF2"), ("RGB", None)]


def home_picker(c):
    boost(c)

    def d(c, x, y, w):
        body(c, x, y + 8, "Set up Glowlauncher", size=17)
        glow_preview(c, x, y + 24, w, 120, "#3FD6A0")
        yy = y + 160
        for i, (n, col) in enumerate(ACCENTS):
            cx = x + 17 + i * 44
            fill = col or c.linear([(0, "#FF0000", None), (.33, "#00FF00", None), (.66, "#0000FF", None), (1, "#FF00FF", None)])
            c.circle(cx, yy + 17, 17, fill)
            c.circle(cx, yy + 17, 15.5 if i == 0 else 16.5, "none", C["Text"] if i == 0 else C["Border"], 3 if i == 0 else 1)
        small(c, x, yy + 54, "Glow: Mint")
        section(c, x, yy + 80, "Apps on your home")
        ay = yy + 92
        for label, pkg, *_ in APPS[:5] + [("Live TV", "", "", False)]:
            app_icon(c, x + 4, ay + 8, label, 34)
            body(c, x + 50, ay + 30, label)
            check(c, x + w - 24, ay + 15, label != "Live TV")
            ay += 50
        primary(c, x, ay + 8, w, "Turn on with 5 apps")
    sheet(c, d, 700)


def glow_preview(c, x, y, w, h, col):
    c.begin(c.clip_rect(x, y, w, h, 16))
    c.rect(x, y, w, h, 0, "#08090B")
    c.add(f'<ellipse cx="{x + w / 2:g}" cy="{y + h * .72:g}" rx="{w * .38:g}" ry="{w * .38:g}" fill="{c.radial([(0, col, .35), (1, col, 0)])}"/>')
    c.end()
    c.text(x + w / 2, y + 52, "9:41", 30, "#FFFFFF", 300, "middle")
    dw = 5 * 22 + 4 * 7 + 20
    dx = x + w / 2 - dw / 2
    c.rect(dx, y + 70, dw, 36, 18, "#FFFFFF", opacity=.06)
    for i in range(5):
        c.rect(dx + 10 + i * 29, y + 77, 22, 22, 6, "#16191E", "#9FEFD0" if i == 0 else None, 2)


def setup_progress(c, step=1, done=False, error=None):
    boost(c, busy=not done)

    def d(c, x, y, w):
        cx, cy = W / 2, y + 62
        ok = error is None
        running = not done and ok
        c.circle(cx, cy, 60, c.radial([(0, MINT if ok else C["Danger"], .3), (1, MINT, 0)]))
        c.circle(cx, cy, 55.5, "none", C["Border"], 5)
        if running:
            arc(c, cx, cy, 55.5, .75, c.linear([(0, MINT, 0), (.5, MINT, None), (1, CYAN, None)]), 5, start=30)
        else:
            c.circle(cx, cy, 55.5, "none", MINT if ok else C["Danger"], 5)
        icon(c, "sparkle", cx - 19, cy - 19, 38, C["Accent"] if ok else C["Danger"], 1.6)
        body(c, cx, y + 148, "Didn't finish" if error else ("Glowlauncher is on" if done else "Turning on Glowlauncher"), size=18, weight=600, anchor="middle")
        small(c, cx, y + 168, "0:42" if not done else "1:18", anchor="middle")
        yy = y + 196
        steps = ["Making room on the TV", "Installing Glowlauncher", "Setting it as the home screen", "Adding your apps"]
        st = 4 if done else step
        for i, label in enumerate(steps):
            if i < st:
                check(c, x + 1, yy - 15, True)
            elif i == st and error:
                c.text(x + 11, yy, "!", 16, C["Danger"], 700, "middle")
            elif i == st:
                spinner(c, x + 11, yy - 5, 8, C["Accent"])
            else:
                c.circle(x + 11, yy - 5, 4, C["Border"])
            body(c, x + 34, yy, label, C["Text"] if i <= st else C["Muted"])
            yy += 32
        if error:
            para(c, x, yy + 6, error, w, C["Danger"])
            primary(c, x, yy + 40, w, "Done")
        elif done:
            small(c, x, yy + 6, "Press Home on the remote to see it.")
            primary(c, x, yy + 24, w, "Done")
        else:
            c.rect(x, yy, w, 76, 12, C["Raised"])
            para(c, x + 12, yy + 22, "The TV can look frozen for up to 2 minutes while it switches home screens. That's normal. Leave it on and don't unplug it.", w - 24)
    sheet(c, d, 470 if not done else 450)


def wallpaper_editor(c):
    x, w = 18, W - 36
    h1(c, 76, "TV wallpaper", 18)
    small(c, x, 98, "TV screen: 1920 × 1080")
    para(c, x, 118, "Pinch to zoom and drag to choose the visible area. Clock and dock are preview guides.", w)
    py, ph = 150, w * 9 / 16
    c.begin(c.clip_rect(x, py, w, ph, 0))
    c.rect(x, py, w, ph, 0, c.linear([(0, "#F7A76C", None), (.45, "#C76B8E", None), (1, "#2D2F6B", None)], 0, 0, 0, 1))
    c.circle(x + w * .7, py + ph * .52, 26, "#FFD9A0", opacity=.9)
    c.path(f"M{x} {py + ph * .78} L{x + w * .22} {py + ph * .55} L{x + w * .42} {py + ph * .72} L{x + w * .62} {py + ph * .5} L{x + w} {py + ph * .8} L{x + w} {py + ph} L{x} {py + ph}Z", "#1C1D3E")
    c.rect(x, py, w, ph, 0, "#000", opacity=.25)
    c.end()
    c.text(W / 2, py + 72, "9:41", 34, "#FFFFFF", 400, "middle")
    c.text(W / 2, py + 88, "Glowlauncher preview", 10, "#FFFFFF", 400, "middle", opacity=.7)
    c.rect(W / 2 - 74, py + 104, 148, 30, 0, "#000", opacity=.45)
    c.text(W / 2, py + 124, "▣    ▣    ▣    ▣    +", 14, "#FFFFFF", 400, "middle")
    y = py + ph + 12
    xx = x
    xx += chip(c, xx, y, "Fill / crop", True) + 10
    chip(c, xx, y, "Fit whole image", False)
    y += 58
    for label, frac, big in [("Zoom · 1.4×", .13, True), ("Horizontal position", .5, False), ("Vertical position", .62, False), ("Darken background · 25%", .36, False)]:
        if big:
            body(c, x, y, label)
        else:
            small(c, x, y, label)
        sl(c, x, y + 6, w, frac)
        y += 62
    para(c, x, y, "Optimized on this phone; sent directly to this TV over ADB. Fit keeps the whole photo with black bars where needed.", w)
    y += 40
    primary(c, x, y, w, "Apply to TV")
    secondary(c, x, y + 60, w, "Reset crop")


def sl(c, x, y, w, frac):
    c.rect(x, y + 14, w, 16, 8, C["Border"])
    c.rect(x, y + 14, w * frac, 16, 8, C["Accent"])
    c.rect(x + w * frac + 4, y + 4, 4, 36, 2, C["Accent"])


# ---------- Settings & logs ----------
def settings(c, from_connect=False):
    h1(c, 74, "Settings")
    y = 92
    if from_connect:
        secondary(c, 16, y, W - 32, "Back to connections")
        y += 60
    card(c, 16, y, W - 32, 62)
    body(c, 30, y + 26, "Konnect TV Plus")
    small(c, 30, y + 44, "v3.4.0 · Android 14 · Pixel 8")
    y += 74
    primary(c, 16, y, W - 32, "Diagnostic logs")
    y += 70
    for t in ["Record connection attempts, discovery, TV command responses, installation errors, wallpaper transfers and app crashes. Logs stay on this phone until you export or share them.",
              "Phone package: io.konnecttv.plus. This is a separate installation from the older companion. Glowlauncher's TV package stays compatible with existing wallpaper and home settings.",
              "Package rules match the TV's actual packages. Critical components are protected; unreviewed system apps stay enabled. A model year alone cannot establish safety."]:
        y = para(c, 16, y, t, W - 32) + 12
    if not from_connect:
        tab_bar(c, "Settings")


def logs(c):
    h1(c, 74, "Diagnostic logs")
    x, w = 16, W - 32
    secondary(c, x, 92, w, "Back to settings")
    card(c, x, 150, w, 142)
    body(c, 30, 176, "Recording on this phone")
    para(c, 30, 196, "Local files rotate at about 1 MB; the viewer shows the latest 400 events. Pairing codes, typed text, private keys and image bytes are excluded. Addresses/identifiers are redacted. TV snapshots can include installed app names; review before sharing.", w - 28)
    hw = (w - 10) / 2
    secondary(c, x, 304, hw, "Pause recording")
    secondary(c, x + hw + 10, 304, hw, "Detailed ADB: off")
    primary(c, x, 362, hw, "Export logs", h=48)
    secondary(c, x + hw + 10, 362, hw, "Share logs")
    primary(c, x, 420, w, "Collect TV diagnostics")
    secondary(c, x, 480, w, "Clear logs", C["Danger"])
    field(c, x, 538, w, "Search logs or installation error code")
    xx = x
    xx += chip(c, xx, 596, "All events", True) + 10
    chip(c, xx, 596, "Errors / warnings", False)
    small(c, x, 650, "214 visible · newest first")
    y = 662
    for t, lvl, cat, msg in [("09:41:07.214", "INFO", "adb", "connect 192.168.x.x:41733 ok · tls"), ("09:41:05.902", "ERROR", "install", "INSTALL_FAILED_INSUFFICIENT_STORAGE")]:
        card(c, x, y, w, 58)
        small(c, 30, y + 22, f"{t} · {lvl} · {cat}", C["Danger"] if lvl == "ERROR" else C["Accent"])
        c.text(30, y + 42, msg, 12, C["Text"], family=MONO)
        y += 66
    tab_bar(c, "Settings")


def clear_logs_sheet(c):
    logs(c)

    def d(c, x, y, w):
        body(c, x, y + 8, "Clear diagnostic logs?")
        para(c, x, y + 30, "Deletes the diagnostic events stored on this phone. TV settings and apps are unaffected.", w)
        primary(c, x, y + 62, w, "Delete local logs")
    sheet(c, d, 170)


def toast(c):
    remote(c)
    glass(c, 16, 44, W - 32, 42, 18)
    body(c, 32, 70, "Sent “stranger things” to the TV")


# ---------- Glowlauncher (TV, 1920×1080) ----------
TW, TH = 1920, 1080
DOCK = ["Netflix", "YouTube", "Prime Video", "Disney+", "Spotify", "Plex"]


def tv(uid, draw):
    c = Canvas(TW, TH, uid)
    c.begin(c.clip_rect(0, 0, TW, TH, 0))
    draw(c)
    c.end()
    return c


def glow_home(c, focus=0, accent="#3FD6A0", moving=False, wallpaper=False, dim_panel=False):
    if wallpaper:
        c.rect(0, 0, TW, TH, 0, c.linear([(0, "#F7A76C", None), (.45, "#C76B8E", None), (1, "#2D2F6B", None)], 0, 0, 0, 1))
        c.circle(1340, 560, 120, "#FFD9A0", opacity=.85)
        c.path("M0 840 L420 600 L800 780 L1190 540 L1920 860 L1920 1080 L0 1080Z", "#1C1D3E")
        c.rect(0, 0, TW, TH, 0, "#000", opacity=.25)
    else:
        c.rect(0, 0, TW, TH, 0, "#08090B")
    tile, gap, pad = 128, 44, 34
    n = len(DOCK) + 1
    dw = n * tile + (n - 1) * gap + pad * 2
    dx = (TW - dw) / 2
    dy = TH - 150 - (tile + pad * 2)
    fx = dx + pad + focus * (tile + gap) + tile / 2
    fy = dy + pad + tile / 2
    c.circle(fx, fy, 220, c.radial([(0, accent, .45), (.5, accent, .12), (1, accent, 0)]))
    c.text(TW / 2, 210 + 180, "9:41", 220, "#FFFFFF", 300, "middle", ls=-8)
    c.text(TW / 2, 470 + 34, "Wednesday, 21 October", 32, "#FFFFFF", 400, "middle", opacity=.6)
    # top right system icons
    for i, ic in enumerate(["input", "gear", "wifi"]):
        x = TW - 96 + 18 - 72 * (3 - i) - 14 * (2 - i)
        icon(c, ic, x + 16, 56 + 16, 40, "#FFFFFF", 2.2)
    c.rect(dx, dy, dw, tile + pad * 2, (tile + pad * 2) / 2, "#FFFFFF", opacity=.05)
    c.rect(dx, dy, dw, tile + pad * 2, (tile + pad * 2) / 2, "none", "#FFFFFF", 2, opacity=.09)
    for i in range(n):
        x = dx + pad + i * (tile + gap)
        y = dy + pad
        s = 1.14 if i == focus else 1
        cx, cy = x + tile / 2, y + tile / 2
        c.begin(transform=f"translate({cx} {cy}) scale({s}) translate({-cx} {-cy})")
        if i < len(DOCK):
            app_icon_tv(c, x, y, tile, DOCK[i])
        else:
            c.rect(x, y, tile, tile, 32, "#FFFFFF", opacity=.08)
            c.rect(x, y, tile, tile, 32, "none", "#FFFFFF", 2, opacity=.08)
            icon(c, "plus", cx - 26, cy - 26, 52, "#FFFFFF", 3)
        if i == focus:
            c.rect(x, y, tile, tile, 32, "none", "#FFFFFF" if moving else lighten(accent), 5 if moving else 4)
        c.end()
    label = (DOCK + ["Add app"])[focus]
    c.text(TW / 2, TH - 84 - 6, label, 30, "#FFFFFF", 500, "middle")
    if moving:
        c.text(TW / 2, TH - 370 - 10, "Use ◀ ▶ to move it, then press OK", 26, "#FFFFFF", 400, "middle", opacity=.7)


def lighten(hexcol):
    r, g, b = int(hexcol[1:3], 16), int(hexcol[3:5], 16), int(hexcol[5:7], 16)
    return "#%02X%02X%02X" % tuple(round(v + (255 - v) * .5) for v in (r, g, b))


def app_icon_tv(c, x, y, s, label):
    brand = {"Netflix": ("#000000", "#E50914", "N"), "YouTube": ("#FFFFFF", "#FF0000", "▶"), "Prime Video": ("#00A8E1", "#FFFFFF", "prime"),
             "Spotify": ("#1DB954", "#000000", "♫"), "Disney+": ("#0E2A6B", "#FFFFFF", "D+"), "Plex": ("#1F1F1F", "#E5A00D", "›")}
    bg, fg, t = brand[label]
    c.rect(x, y, s, s, 32, bg)
    c.rect(x, y, s, s, 32, "none", "#FFFFFF", 2, opacity=.08)
    c.text(x + s / 2, y + s / 2 + s * .16, t, s * (.26 if len(t) > 2 else .44), fg, 700, "middle")


def tv_panel(c, title, labels, focus=0, accent="#3FD6A0", icons=None):
    c.rect(0, 0, TW, TH, 0, "#000", opacity=.5)
    px = TW - 640
    c.rect(px, 0, 640, TH, 0, "#0E1013", opacity=.97)
    c.text(px + 72, 88 + 40, title, 40, "#FFFFFF", 500)
    y = 88 + 40 + 36 + 24
    for i, label in enumerate(labels):
        if i == focus:
            c.rect(px + 48, y, 640 - 96, 88, 22, accent, opacity=.2)
        tx = px + 72
        if icons:
            app_icon_tv(c, tx, y + 18, 52, icons[i]) if icons[i] in ("Netflix", "YouTube", "Prime Video", "Spotify", "Disney+", "Plex") else monogram_tv(c, tx, y + 18, icons[i])
            tx += 52 + 24
        c.text(tx, y + 55, label, 30, "#FFFFFF", 400, opacity=.9)
        y += 88


def monogram_tv(c, x, y, label):
    use(DARK)
    hue = (java_hash(label) & 0x7fffffff) % 360
    c.rect(x, y, 52, 52, 14, hsl(hue, .25, .22))
    c.text(x + 26, y + 35, label[0], 24, hsl(hue, .6, .8), 600, "middle")


# ---------- components ----------
def components():
    c = Canvas(1200, 900, "kcmp")
    use(DARK)
    c.rect(0, 0, 1200, 900, 0, "#08080A")
    c.text(48, 70, "Konnect TV Plus · components & tokens", 30, C["Text"], 700)
    c.text(48, 98, "“Mono matte” from ui/Theme.kt — solid surfaces, frosted glass only on things that float. Dark default + light theme.", 14, C["Muted"])
    keys = ["Bg", "Card", "Border", "Raised", "RaisedBorder", "Text", "Muted", "Accent", "OnAccent", "Danger", "OrbOn"]
    for t, (theme, name) in enumerate([(DARK, "Dark"), (LIGHT, "Light")]):
        c.text(48, 140 + t * 120, name, 14, C["Text"], 600)
        for i, k in enumerate(keys):
            x = 120 + i * 96
            c.rect(x, 120 + t * 120, 44, 44, 12, theme[k], "#3A3A44")
            c.text(x, 182 + t * 120, k, 11, C["Text"], 500)
            c.text(x, 198 + t * 120, theme[k], 10, C["Muted"], family=MONO)
    y = 370
    c.text(48, y, "Buttons", 16, C["Text"], 600)
    primary(c, 48, y + 16, 220, "Pair"); primary(c, 284, y + 16, 220, "Pair", False)
    secondary(c, 48, y + 80, 220, "Switch TV"); secondary(c, 284, y + 80, 220, "Forget this TV", C["Danger"])
    c.text(560, y, "Controls", 16, C["Text"], 600)
    toggle(c, 560, y + 28, True); toggle(c, 620, y + 28, False)
    check(c, 690, y + 31, True); check(c, 724, y + 31, False); check(c, 758, y + 31, False, False)
    xx = 560
    xx += chip(c, xx, y + 76, "Installed", True) + 8
    chip(c, xx, y + 76, "System", False)
    mline(c, 560, y + 136, 240, .4, True)
    mline(c, 560, y + 160, 240, 0, False)
    c.text(860, y, "Tiles", 16, C["Text"], 600)
    monogram(c, 860, y + 24, "Living room TV"); monogram(c, 904, y + 24, "Bedroom Mi Box"); monogram(c, 948, y + 24, "Sony BRAVIA 4K")
    app_icon(c, 860, y + 72, "Netflix"); app_icon(c, 904, y + 72, "YouTube"); app_icon(c, 948, y + 72, "Prime Video"); app_icon(c, 992, y + 72, "Spotify")
    y2 = 600
    c.text(48, y2, "Tab bar (glass) & tab icons", 16, C["Text"], 600)
    c.begin(transform=f"translate(32 {y2 + 20 - (H - 100)})"); tab_bar(c, "Remote"); c.end()
    c.text(480, y2, "Field", 16, C["Text"], 600)
    field(c, 480, y2 + 16, 300, "TV IP, e.g. 192.168.1.24"); field(c, 480, y2 + 72, 300, "", "192.168.1.24", True)
    c.text(820, y2, "Pairing code", 16, C["Text"], 600)
    for i in range(6):
        c.rect(820 + i * 52, y2 + 16, 44, 54, 12, C["Card"], C["Accent"] if i == 3 else C["RaisedBorder"])
        if i < 3:
            c.text(842 + i * 52, y2 + 51, "482"[i], 24, C["Text"], 600, "middle")
    c.text(48, y2 + 140, "Glow accents (Glowlauncher)", 16, C["Text"], 600)
    for i, (n, col) in enumerate(ACCENTS):
        fill = col or c.linear([(0, "#FF0000", None), (.33, "#00FF00", None), (.66, "#0000FF", None), (1, "#FF00FF", None)])
        c.circle(70 + i * 90, y2 + 186, 20, fill)
        c.text(70 + i * 90, y2 + 226, n, 12, C["Muted"], 400, "middle")
    c.text(700, y2 + 140, "Type scale", 16, C["Text"], 600)
    c.text(700, y2 + 180, "H1 24 SemiBold", 24, C["Text"], 600)
    c.text(700, y2 + 206, "Body 14 Regular", 14, C["Text"])
    c.text(700, y2 + 226, "Small 12 Muted", 12, C["Muted"])
    c.text(700, y2 + 246, "SECTION 11 MEDIUM +0.8", 11, C["Muted"], 500, ls=.8)
    return c


PHONE = [
    ("01-connect", "Connect a TV", lambda c: connect(c), False),
    ("02-connect-first-run", "Connect · first run", lambda c: connect(c, busy=False, first=True), False),
    ("03-pair-sheet", "Pair with code", pair_sheet, False),
    ("04-manual-ip-sheet", "Connect by IP", manual_sheet, False),
    ("05-edit-tv-sheet", "Edit saved TV", lambda c: edit_sheet(c), False),
    ("06-remote", "Remote", lambda c: remote(c), False),
    ("07-remote-light", "Remote · light theme", lambda c: remote(c), True),
    ("08-remote-media-error", "Remote · media unavailable", lambda c: remote(c, error=True), False),
    ("09-remote-type-sheet", "Type on TV", type_sheet, False),
    ("10-remote-tv-menu", "TV menu", menu_sheet, False),
    ("11-remote-toast", "Status toast", toast, False),
    ("12-apps", "Apps", lambda c: apps(c), False),
    ("13-app-actions-sheet", "App actions", lambda c: app_sheet(c), False),
    ("14-app-disabled-sheet", "Disabled app", lambda c: app_sheet(c, True), False),
    ("15-detox", "Detox", detox, False),
    ("16-detox-lower", "Detox · groups & action", detox_bottom, False),
    ("17-detox-history-sheet", "Undo history", history_sheet, False),
    ("18-boost-off", "Boost · Glowlauncher off", lambda c: boost(c), False),
    ("19-boost-on", "Boost · Glowlauncher on", lambda c: boost(c, on=True), False),
    ("20-boost-lower", "Boost · tweaks", boost_lower, False),
    ("21-home-picker-sheet", "Set up Glowlauncher", home_picker, False),
    ("22-setup-progress", "Turning on Glowlauncher", lambda c: setup_progress(c), False),
    ("23-setup-done", "Glowlauncher is on", lambda c: setup_progress(c, done=True), False),
    ("24-setup-error", "Setup didn't finish", lambda c: setup_progress(c, step=1, error="INSTALL_FAILED_INSUFFICIENT_STORAGE: free some space on the TV and try again."), False),
    ("25-sleep-sheet", "Sleep after", sleep_sheet, False),
    ("26-launcher-menu", "Glowlauncher menu", launcher_menu, False),
    ("27-remove-wallpaper", "Remove wallpaper?", clear_wall_sheet, False),
    ("28-wallpaper-editor", "Wallpaper editor", wallpaper_editor, False),
    ("29-settings", "Settings", lambda c: settings(c), False),
    ("30-settings-from-connect", "Settings · before connecting", lambda c: settings(c, True), False),
    ("31-diagnostic-logs", "Diagnostic logs", logs, False),
    ("32-clear-logs-sheet", "Clear logs?", clear_logs_sheet, False),
]

TVS = [
    ("01-glow-home", "Glowlauncher · home", lambda c: glow_home(c)),
    ("02-glow-home-wallpaper", "Glowlauncher · custom wallpaper, Sky glow", lambda c: glow_home(c, 2, "#4FB3FF", wallpaper=True)),
    ("03-glow-move", "Glowlauncher · move mode", lambda c: glow_home(c, 3, "#9B7BFF", moving=True)),
    ("04-glow-edit-menu", "Glowlauncher · app menu", lambda c: (glow_home(c, 1), tv_panel(c, "YouTube", ["Move", "Remove from home", "App info"]))),
    ("05-glow-add-app", "Glowlauncher · add an app", lambda c: (glow_home(c, 6), tv_panel(c, "Add an app", ["Kodi", "Live TV", "Play Store", "VLC", "Zee5"], 0, icons=["Kodi", "Live TV", "Play Store", "VLC", "Zee5"]))),
    ("06-glow-inputs", "Glowlauncher · inputs", lambda c: (glow_home(c), tv_panel(c, "Inputs", ["HDMI 1 · PlayStation", "HDMI 2", "HDMI 3 (ARC)"]))),
]

if __name__ == "__main__":
    frags = []
    for slug, label, fn, light in PHONE:
        c = phone(slug, fn, light)
        write(f"{OUT}/phone/{slug}.svg", c.svg(f"Konnect TV Plus · {label}"))
        frags.append((label, c.body(), W, H))
    board(frags, 8, W, H, "Konnect TV Plus · phone UI", f"{len(frags)} screens & sheets · 390 × 844 dp · generated from the current Compose source", f"{OUT}/konnect-tv-plus-all-screens.svg")
    tfr = []
    for slug, label, fn in TVS:
        c = tv(slug, fn)
        write(f"{OUT}/tv/{slug}.svg", c.svg(label))
        tfr.append((label, f'<g transform="scale(0.5)">{c.body()}</g>', TW / 2, TH / 2))
    board(tfr, 2, TW / 2, TH / 2, "Glowlauncher · TV home (1920 × 1080)", "Rendered at 50 % · D-pad focus, move mode and side panels from glowlauncher/HomeActivity.kt", f"{OUT}/glowlauncher-all-screens.svg")
    write(f"{OUT}/components.svg", components().svg("Konnect TV Plus · components & tokens"))
    print(len(frags), "phone,", len(tfr), "tv")
