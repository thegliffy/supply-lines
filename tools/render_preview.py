#!/usr/bin/env python3
"""Render supply-lines board PNGs from engine-dumped state JSON (see RenderPreview.kt)."""
import json, os
from PIL import Image, ImageDraw, ImageFont

BASE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PREVIEW = os.path.join(BASE, "app", "build", "preview")

BG      = (14, 17, 22)
PANEL   = (22, 27, 34)
GRID    = (26, 31, 40)
LINE_IDLE = (58, 68, 83)
LINE_HOT  = (127, 178, 255)
TXT     = (216, 222, 233)
DIM     = (138, 147, 163)
GREEN   = (111, 207, 127)
PURPLE  = (157, 124, 224)
GOLD    = (229, 192, 123)

KIND_COLOR = {
    "MINER": (224, 164, 88),
    "SMELTER": (79, 184, 168), "CONSTRUCTOR": (79, 184, 168),
    "MANUFACTURER": (79, 184, 168), "ASSEMBLER": (79, 184, 168),
    "FOUNDRY": (79, 184, 168), "MANUAL_BENCH": (96, 140, 160),
    "STORAGE": DIM, "HUB": GREEN, "ELEVATOR": PURPLE, "SINK": GOLD, "ore": None,
}
ITEM_COLOR = {
    "IRON_ORE": (224, 164, 88), "COPPER_ORE": (205, 120, 60), "COAL": (90, 95, 105),
    "IRON_INGOT": (170, 180, 195), "COPPER_INGOT": (205, 120, 60), "STEEL_INGOT": (150, 160, 175),
    "IRON_PLATE": (160, 170, 185), "IRON_ROD": (160, 170, 185), "IRON_SCREW": (140, 148, 160),
    "WIRE": (220, 200, 90), "CABLE": (200, 185, 80), "STEEL_BEAM": (150, 160, 175),
    "REINFORCED_IRON_PLATE": (100, 200, 220), "IRON_ROTOR": (79, 184, 168),
    "SMART_PLATING": PURPLE, "MODULAR_FRAME": (230, 235, 240),
}
LABEL = {
    "MINER": "MNR", "SMELTER": "SML", "CONSTRUCTOR": "CTR", "MANUFACTURER": "MNF",
    "ASSEMBLER": "ASM", "FOUNDRY": "FND", "MANUAL_BENCH": "BNC", "STORAGE": "STR",
    "HUB": "HUB", "ELEVATOR": "ELEV", "SINK": "SINK",
}

def font(sz, bold=False):
    p = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf" if bold else "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
    try: return ImageFont.truetype(p, sz)
    except Exception: return ImageFont.load_default()

def render(state, out_path, W=1080, H=1440):
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    f_title, f_lbl, f_small = font(34, True), font(22, True), font(20)

    # top bar
    d.rectangle([0, 0, W, 90], fill=PANEL)
    d.text((30, 26), f"coins {state['coins']}", font=f_title, fill=GOLD)
    d.text((W - 260, 30), f"{state['cps']} c/s", font=f_lbl, fill=DIM)

    # board area
    top, bottom = 110, H - 260
    grid = state["grid"]
    cell = min(W, bottom - top) / grid
    ox = (W - cell * grid) / 2
    oy = top + ((bottom - top) - cell * grid) / 2

    def cx(x): return ox + (x + 0.5) * cell
    def cy(y): return oy + (y + 0.5) * cell

    # grid dots
    for i in range(grid + 1):
        for j in range(grid + 1):
            d.ellipse([ox + i*cell - 2, oy + j*cell - 2, ox + i*cell + 2, oy + j*cell + 2], fill=GRID)

    # ore tint cells
    for c in state["cells"]:
        if c["kind"] != "ore": continue
        col = ITEM_COLOR.get(c["item"] or "", DIM)
        x0, y0 = ox + c["x"]*cell, oy + c["y"]*cell
        d.rounded_rectangle([x0+4, y0+4, x0+cell-4, y0+cell-4], radius=10,
                            fill=tuple(int(0.18*v) for v in col), outline=tuple(int(0.45*v) for v in col), width=2)

    # belts
    for l in state["lines"]:
        col = LINE_HOT if l["hot"] else LINE_IDLE
        d.line([cx(l["ax"]), cy(l["ay"]), cx(l["bx"]), cy(l["by"])], fill=col, width=6 if l["hot"] else 4)

    # buildings
    for c in state["cells"]:
        if c["kind"] == "ore": continue
        col = KIND_COLOR.get(c["kind"], TXT)
        r = cell * 0.36
        x, y = cx(c["x"]), cy(c["y"])
        d.rounded_rectangle([x-r, y-r, x+r, y+r], radius=int(cell*0.12), fill=PANEL, outline=col, width=3)
        t = LABEL.get(c["kind"] or "", (c["kind"] or "?"))
        bb = d.textbbox((0, 0), t, font=f_small)
        d.text((x - (bb[2]-bb[0])/2, y - (bb[3]-bb[1])/2 - 4), t, font=f_small, fill=col)

    # items in transit
    for p in state["dots"]:
        col = ITEM_COLOR.get(p["item"], TXT)
        x, y = cx(p["x"]), cy(p["y"])
        d.ellipse([x-7, y-7, x+7, y+7], fill=col)

    # gate panel
    d.rectangle([0, H-240, W, H], fill=PANEL)
    g = state.get("gate")
    if g:
        d.text((30, H-222), "NEXT UPGRADE — " + g["name"], font=f_lbl, fill=TXT)
        yy = H-180
        for it in g["items"]:
            col = ITEM_COLOR.get(it["label"], TXT)
            d.ellipse([40, yy+4, 58, yy+22], fill=col)
            bar_x, bar_w = 80, 500
            d.rounded_rectangle([bar_x, yy, bar_x+bar_w, yy+26], radius=8, fill=BG)
            frac = min(1.0, it["have"] / max(1, it["need"]))
            if frac > 0:
                d.rounded_rectangle([bar_x, yy, bar_x + int(bar_w*frac), yy+26], radius=8, fill=col)
            d.text((bar_x + bar_w + 20, yy+1), f"{it['have']} / {it['need']}", font=f_small, fill=DIM)
            yy += 38
    if state.get("gatesDone"):
        d.text((W-320, H-222), "done: " + ", ".join(state["gatesDone"]), font=f_small, fill=GREEN)

    img.save(out_path)
    print("wrote", out_path)

for name, out in [("midgame", "supplylines-board.png"), ("fresh", "supplylines-fresh.png")]:
    with open(os.path.join(PREVIEW, f"state-{name}.json")) as fh:
        render(json.load(fh), os.path.join(PREVIEW, out))
