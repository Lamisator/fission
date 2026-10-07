"""Generates every Fission asset: textures, models, blockstates, item definitions, loot tables, tags, recipes,
worldgen, language entries and sounds. Run from the repository root: python3 tools/gen_assets.py
Needs Pillow, numpy, soundfile and oggenc (vorbis-tools)."""
import json, math, os, random, subprocess, tempfile
import math
import numpy as np
import soundfile as sf
from PIL import Image, ImageDraw

RES = "src/main/resources"
A = f"{RES}/assets/fission"
D = f"{RES}/data/fission"
MC = f"{RES}/data/minecraft"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def save(img, rel):
    path = f"{A}/textures/{rel}.png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def noise(img, amount=6, seed=0):
    r = random.Random(seed)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            p = px[x, y]
            if len(p) == 4 and p[3] == 0:
                continue
            d = r.randint(-amount, amount)
            px[x, y] = tuple(max(0, min(255, c + d)) for c in p[:3]) + tuple(p[3:])
    return img


def canvas(color=(0, 0, 0, 0), size=16):
    return Image.new("RGBA", (size, size), color)


def rgba(c):
    return tuple(c) + (255,)


STEEL = (128, 134, 142)
STEEL_D = (82, 86, 94)
STEEL_L = (176, 182, 190)
YELLOW = (230, 190, 40)


def housing(base, edge, seed, rivets=True):
    img = canvas(rgba(base))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, 15, 15], outline=rgba(edge))
    d.line([(1, 1), (14, 1)], fill=rgba(tuple(min(255, c + 25) for c in base)))
    if rivets:
        for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
            d.point((x, y), fill=rgba(STEEL_L))
    return noise(img, 4, seed)


def trefoil(d, cx, cy, r, fg):
    for k in range(3):
        a0 = -90 + k * 120 - 30
        d.pieslice([cx - r, cy - r, cx + r, cy + r], a0, a0 + 60, fill=fg)
    d.ellipse([cx - r * 0.22, cy - r * 0.22, cx + r * 0.22, cy + r * 0.22], fill=fg)


# ================================================================ textures
for name, base in (("uranium_ore", (125, 125, 125)), ("deepslate_uranium_ore", (78, 78, 84))):
    img = noise(canvas(rgba(base)), 12, 3 if base[0] > 100 else 4)
    d = ImageDraw.Draw(img)
    r = random.Random(7)
    for _ in range(7):
        x, y = r.randint(1, 13), r.randint(1, 13)
        d.rectangle([x, y, x + r.randint(1, 2), y + 1], fill=rgba((170, 210, 40)))
        d.point((x, y), fill=rgba((230, 255, 120)))
    save(img, f"block/{name}")

# fuel channel: steel pressure tube; the side window shows the assembly; the top glows blue when fissioning
for load in range(3):
    img = housing(STEEL, STEEL_D, 10, False)
    d = ImageDraw.Draw(img)
    d.rectangle([5, 1, 10, 14], fill=rgba((40, 44, 50)))
    if load:
        col = (90, 180, 90) if load == 1 else (70, 70, 60)
        for y in range(2, 14, 3):
            d.rectangle([6, y, 9, y + 1], fill=rgba(col))
    save(img, f"block/fuel_channel_side_{load}")
    for glow in (0, 1):
        t = housing(STEEL_D, (50, 52, 58), 11, False)
        d = ImageDraw.Draw(t)
        d.ellipse([3, 3, 12, 12], fill=rgba((30, 120, 255) if glow else (40, 44, 50)), outline=rgba(STEEL_L))
        if load:
            d.ellipse([6, 6, 9, 9], fill=rgba((190, 240, 255) if glow else ((90, 180, 90) if load == 1 else (70, 70, 60))))
        save(t, f"block/fuel_channel_top_{load}_{glow}")

# control rod: drive head on top shows insertion
save(housing((90, 96, 104), (50, 54, 60), 12), "block/control_rod_side")
for n in range(9):
    t = housing((60, 64, 70), (36, 38, 42), 13, False)
    d = ImageDraw.Draw(t)
    d.rectangle([2, 2, 13, 13], fill=rgba((20, 22, 26)))
    h = round(n / 8 * 10)
    if h:
        d.rectangle([4, 13 - h, 11, 12], fill=rgba(YELLOW))
        for y in range(13 - h, 13, 2):
            d.line([(4, y), (11, y)], fill=rgba((30, 30, 30)))
    save(t, f"block/control_rod_top_{n}")

g = canvas(rgba((48, 48, 52)))
d = ImageDraw.Draw(g)
for y in (0, 8):
    d.line([(0, y), (15, y)], fill=rgba((32, 32, 36)))
for x, y0 in ((0, 0), (8, 8)):
    d.line([(x, y0), (x, y0 + 7)], fill=rgba((32, 32, 36)))
d.line([(8, 0), (8, 7)], fill=rgba((32, 32, 36)))
d.line([(0, 8), (0, 15)], fill=rgba((32, 32, 36)))
save(noise(g, 5, 14), "block/graphite_moderator")
b = canvas(rgba((178, 184, 190)))
d = ImageDraw.Draw(b)
for y in range(0, 16, 4):
    for x in range(0, 16, 4):
        d.rectangle([x + (y // 4 % 2) * 2, y, x + 2 + (y // 4 % 2) * 2, y + 2], outline=rgba((150, 156, 164)))
save(noise(b, 4, 15), "block/beryllium_reflector")
vessel = housing((96, 100, 106), (60, 62, 68), 16)
d = ImageDraw.Draw(vessel)
d.line([(0, 8), (15, 8)], fill=rgba((70, 72, 78)))
save(vessel, "block/reactor_vessel")
for name, col in (("feedwater_inlet", (40, 110, 210)), ("steam_outlet", (200, 200, 210)), ("relief_valve", YELLOW)):
    img = housing((96, 100, 106), (60, 62, 68), 17)
    d = ImageDraw.Draw(img)
    d.ellipse([3, 3, 12, 12], fill=rgba(col), outline=rgba((40, 40, 44)))
    d.ellipse([6, 6, 9, 9], fill=rgba((30, 30, 34)))
    save(img, f"block/{name}")

ctrl = housing((70, 76, 84), (40, 44, 50), 18)
d = ImageDraw.Draw(ctrl)
d.rectangle([2, 2, 13, 7], fill=rgba((20, 30, 24)))
for i, c in enumerate([(80, 220, 90), (230, 180, 40), (220, 50, 40)]):
    d.rectangle([3 + i * 4, 9, 4 + i * 4, 10], fill=rgba(c))
d.line([(3, 5), (6, 4), (9, 5), (12, 3)], fill=rgba((90, 230, 110)))
d.rectangle([3, 12, 12, 13], fill=rgba((150, 150, 150)))
save(ctrl, "block/reactor_controller_front")
save(housing((70, 76, 84), (40, 44, 50), 19), "block/control_side")
desk = housing((80, 86, 94), (44, 48, 54), 20, False)
d = ImageDraw.Draw(desk)
d.rectangle([1, 1, 7, 6], fill=rgba((20, 28, 24)))
d.rectangle([8, 1, 14, 6], fill=rgba((20, 28, 24)))
d.line([(2, 4), (4, 3), (6, 4)], fill=rgba((90, 230, 110)))
d.line([(9, 5), (11, 2), (13, 3)], fill=rgba((240, 180, 50)))
for i in range(6):
    d.point((2 + i * 2, 10), fill=rgba([(220, 50, 40), (80, 220, 90), (230, 180, 40)][i % 3]))
d.rectangle([3, 12, 12, 13], fill=rgba((40, 40, 44)))
save(desk, "block/reactor_console_top")
screen = canvas(rgba((24, 28, 32)))
d = ImageDraw.Draw(screen)
d.rectangle([1, 1, 14, 14], fill=rgba((12, 30, 20)), outline=rgba((60, 64, 70)))
for y in (4, 7, 10):
    d.line([(3, y), (12, y)], fill=rgba((60, 200, 90)))
save(screen, "block/reactor_console_screen")
panel = canvas(rgba((40, 42, 46)))
d = ImageDraw.Draw(panel)
d.rectangle([0, 0, 15, 15], outline=rgba((70, 74, 80)))
save(panel, "block/panel_front")
mon = housing((220, 180, 40), (150, 120, 20), 21)
d = ImageDraw.Draw(mon)
d.rectangle([2, 2, 13, 7], fill=rgba((16, 30, 16)))
trefoil(d, 8, 11, 3, rgba((20, 20, 20)))
save(mon, "block/radiation_monitor_front")
save(housing((220, 180, 40), (150, 120, 20), 22), "block/radiation_monitor_side")
red = canvas(rgba((200, 30, 30)))
d = ImageDraw.Draw(red)
d.ellipse([2, 2, 13, 13], fill=rgba((235, 60, 50)))
save(red, "block/scram_red")
glass = canvas((210, 230, 245, 90))
d = ImageDraw.Draw(glass)
d.rectangle([0, 0, 15, 15], outline=(240, 200, 40, 255))
save(glass, "block/scram_guard")
save(housing((60, 62, 66), (36, 38, 42), 23), "block/scram_base")

# pipes and tubes
for name, base, stripe in (("water_pipe", (60, 110, 200), (40, 80, 160)), ("steam_pipe", (200, 204, 210), (170, 60, 50)),
                           ("fuel_transfer_tube", (180, 196, 210), (60, 160, 90)), ("isotope_pipe", (210, 180, 40), (40, 40, 40))):
    img = canvas(rgba(base))
    d = ImageDraw.Draw(img)
    for x in range(0, 16, 6):
        d.line([(x, 0), (x, 15)], fill=rgba(stripe))
    save(noise(img, 4, 24), f"block/{name}")

pump = housing((50, 100, 180), (30, 60, 120), 25)
d = ImageDraw.Draw(pump)
d.ellipse([3, 3, 12, 12], fill=rgba((40, 44, 50)), outline=rgba((160, 170, 180)))
for a in range(0, 360, 60):
    d.line([(8, 8), (8 + 4 * math.cos(math.radians(a)), 8 + 4 * math.sin(math.radians(a)))], fill=rgba((140, 150, 160)))
save(pump, "block/feedwater_pump_front")
for on in (0, 1):
    side = housing((50, 100, 180), (30, 60, 120), 26)
    ImageDraw.Draw(side).rectangle([11, 2, 13, 4], fill=rgba((80, 230, 90) if on else (40, 60, 40)))
    save(side, f"block/feedwater_pump_side_{on}")
tur = housing((60, 120, 80), (36, 80, 50), 27)
d = ImageDraw.Draw(tur)
for x in range(2, 14, 3):
    d.line([(x, 2), (x + 2, 13)], fill=rgba((40, 90, 60)))
save(tur, "block/steam_turbine_side")
tf = housing((60, 120, 80), (36, 80, 50), 28)
d = ImageDraw.Draw(tf)
d.ellipse([4, 4, 11, 11], fill=rgba((170, 176, 184)), outline=rgba((40, 40, 44)))
d.ellipse([6, 6, 9, 9], fill=rgba((60, 60, 66)))
save(tf, "block/steam_turbine_front")
for on in (0, 1):
    gen = housing(YELLOW, (150, 120, 20), 29)
    d = ImageDraw.Draw(gen)
    d.rectangle([3, 4, 12, 9], fill=rgba((230, 230, 220)), outline=rgba((40, 40, 40)))
    d.line([(4, 7), (5, 5), (7, 8), (9, 5), (11, 7)], fill=rgba((40, 40, 40)))
    d.rectangle([6, 11, 9, 13], fill=rgba((80, 230, 90) if on else (40, 60, 40)))
    save(gen, f"block/turbine_generator_{on}")
cond = housing((150, 156, 164), (100, 104, 110), 30)
d = ImageDraw.Draw(cond)
for y in range(3, 14, 2):
    d.line([(2, y), (13, y)], fill=rgba((110, 116, 124)))
save(cond, "block/condenser")
rack = canvas((0, 0, 0, 0))
d = ImageDraw.Draw(rack)
d.rectangle([0, 0, 15, 15], outline=rgba(STEEL_D))
for x in (3, 7, 11):
    d.rectangle([x, 2, x + 1, 13], fill=rgba((90, 170, 90)))
d.line([(0, 2), (15, 2)], fill=rgba(STEEL))
d.line([(0, 13), (15, 13)], fill=rgba(STEEL))
save(rack, "block/fuel_rack")
water = canvas(rgba((30, 90, 200)))
d = ImageDraw.Draw(water)
d.rectangle([0, 0, 15, 15], outline=rgba((150, 150, 146)))
for x, y in ((4, 5), (10, 9), (6, 11)):
    d.rectangle([x, y, x + 1, y], fill=rgba((90, 200, 255)))
save(noise(water, 6, 31), "block/holding_basin_top")
save(noise(canvas(rgba((150, 150, 146))), 8, 32), "block/holding_basin_side")
drum = canvas(rgba((210, 175, 40)))
d = ImageDraw.Draw(drum)
for y in (2, 13):
    d.line([(0, y), (15, y)], fill=rgba((140, 110, 20)))
trefoil(d, 8, 8, 4, rgba((20, 20, 20)))
save(noise(drum, 5, 33), "block/storage_drum_side")
dt = canvas(rgba((190, 160, 36)))
ImageDraw.Draw(dt).ellipse([2, 2, 13, 13], outline=rgba((120, 100, 20)))
save(dt, "block/storage_drum_top")
cell = housing((110, 110, 106), (80, 80, 76), 34)
d = ImageDraw.Draw(cell)
d.rectangle([3, 3, 12, 9], fill=rgba((80, 140, 120)), outline=rgba((40, 40, 40)))
d.line([(4, 12), (6, 10), (8, 12)], fill=rgba((40, 40, 44)))
d.line([(9, 12), (11, 10), (13, 12)], fill=rgba((40, 40, 44)))
save(cell, "block/reprocessing_plant_front")
save(housing((110, 110, 106), (80, 80, 76), 35), "block/reprocessing_plant_side")
cen = canvas(rgba((200, 206, 214)))
d = ImageDraw.Draw(cen)
for y in range(0, 16, 5):
    d.line([(0, y), (15, y)], fill=rgba((150, 156, 164)))
save(noise(cen, 4, 36), "block/gas_centrifuge")
for hot in (0, 1):
    c = canvas(rgba((255, 120, 20) if hot else (190, 60, 20)))
    r = random.Random(37 + hot)
    px = c.load()
    for y in range(16):
        for x in range(16):
            v = r.random()
            if v < 0.25:
                px[x, y] = rgba((255, 220, 90) if hot else (230, 120, 30))
            elif v > 0.85:
                px[x, y] = rgba((120, 30, 10))
    save(c, f"block/corium_{'hot' if hot else 'cool'}")
sc = noise(canvas(rgba((70, 50, 40))), 12, 39)
d = ImageDraw.Draw(sc)
for x, y in ((3, 4), (10, 7), (6, 12)):
    d.point((x, y), fill=rgba((200, 90, 30)))
save(sc, "block/solid_corium")
deb = noise(canvas(rgba((44, 44, 46))), 14, 40)
d = ImageDraw.Draw(deb)
for x, y in ((2, 3), (9, 5), (5, 11), (12, 12)):
    d.line([(x, y), (x + 3, y + 1)], fill=rgba((20, 20, 22)))
d.point((7, 8), fill=rgba((240, 120, 40)))
save(deb, "block/reactor_debris")
# a broken piece of a fuel assembly: zirconium cladding, ceramic fuel, still glowing in places
ff = noise(canvas(rgba((70, 72, 66))), 16, 41)
d = ImageDraw.Draw(ff)
for x0 in (1, 6, 11):
    d.line([(x0, 0), (x0 + 1, 15)], fill=rgba((150, 152, 145)))
for x, y in ((3, 5), (8, 9), (13, 3), (4, 13), (10, 14)):
    d.point((x, y), fill=rgba((255, 150, 50)))
    d.point((x + 1, y), fill=rgba((200, 80, 30)))
save(ff, "block/fuel_fragment")
os.makedirs(f"{A}/textures/entity", exist_ok=True)
canvas((255, 255, 255, 255)).save(f"{A}/textures/entity/white.png")
# a soft, ragged puff of smoke for radioactive clouds (white: the renderer tints it)
puff = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
pr = random.Random(77)
pp = puff.load()
for py in range(64):
    for px in range(64):
        dx, dy = (px - 31.5) / 31.5, (py - 31.5) / 31.5
        rr = (dx * dx + dy * dy) ** 0.5
        edge = 0.82 + 0.12 * math.sin(math.atan2(dy, dx) * 5 + 1.1) + 0.06 * math.sin(math.atan2(dy, dx) * 11)
        a = max(0.0, 1 - rr / edge)
        a = a ** 0.8 * (0.82 + 0.18 * pr.random())
        v = int(200 + 55 * pr.random())
        pp[px, py] = (v, v, v, int(255 * min(1.0, a * 1.15)))
puff.save(f"{A}/textures/entity/cloud_puff.png")

# items
def item_img(draw):
    img = canvas()
    draw(ImageDraw.Draw(img))
    save(img, f"item/{draw.__name__}")


def raw_uranium(d):
    d.polygon([(3, 8), (6, 3), (11, 4), (13, 9), (9, 13), (4, 12)], fill=rgba((90, 100, 60)), outline=rgba((50, 56, 30)))
    for x, y in ((6, 7), (9, 9), (8, 5)):
        d.point((x, y), fill=rgba((190, 230, 60)))


def yellowcake(d):
    d.ellipse([2, 6, 13, 13], fill=rgba((230, 200, 40)), outline=rgba((150, 120, 20)))
    d.ellipse([4, 4, 11, 9], fill=rgba((245, 215, 60)))


def enriched_uranium(d):
    for i, x in enumerate((3, 7, 11)):
        d.rectangle([x, 4 + i % 2, x + 2, 12], fill=rgba((60, 70, 80)), outline=rgba((30, 34, 40)))
        d.point((x + 1, 6), fill=rgba((120, 255, 120)))


def depleted_uranium(d):
    d.rectangle([3, 5, 12, 11], fill=rgba((70, 72, 76)), outline=rgba((40, 40, 44)))
    d.line([(4, 6), (11, 6)], fill=rgba((110, 112, 118)))


def plutonium(d):
    d.ellipse([4, 4, 11, 11], fill=rgba((150, 160, 170)), outline=rgba((90, 96, 104)))
    d.ellipse([6, 6, 9, 9], fill=rgba((255, 160, 80)))


def zircaloy_cladding(d):
    d.rectangle([7, 1, 8, 14], fill=rgba((190, 196, 204)))
    d.line([(6, 1), (6, 14)], fill=rgba((130, 136, 144)))


def rod(color):
    def f(d):
        d.line([(3, 13), (12, 2)], fill=rgba((150, 156, 164)), width=3)
        d.line([(4, 12), (11, 3)], fill=rgba(color), width=1)
        d.rectangle([2, 12, 4, 14], fill=rgba((90, 96, 104)))
    return f


def isotope_canister(d):
    d.rectangle([4, 3, 11, 13], fill=rgba((180, 186, 196)), outline=rgba((90, 96, 104)))
    d.rectangle([5, 1, 10, 3], fill=rgba((120, 126, 134)))
    trefoil(d, 8, 8, 3, rgba((230, 190, 40)))


def decayed_canister(d):
    d.rectangle([4, 3, 11, 13], fill=rgba((150, 156, 160)), outline=rgba((90, 96, 104)))
    d.rectangle([5, 1, 10, 3], fill=rgba((120, 126, 134)))


def corium_fragment(d):
    d.polygon([(3, 9), (6, 4), (12, 5), (13, 10), (8, 13)], fill=rgba((80, 50, 36)), outline=rgba((40, 24, 16)))
    d.point((8, 8), fill=rgba((255, 140, 40)))
    d.point((10, 7), fill=rgba((255, 200, 60)))


def reactor_linker(d):
    d.ellipse([2, 2, 13, 13], fill=rgba((40, 40, 44)), outline=rgba((20, 20, 22)))
    d.ellipse([5, 5, 10, 10], fill=rgba((180, 120, 60)))
    d.arc([3, 3, 12, 12], 0, 270, fill=rgba((60, 160, 230)))


for f in (raw_uranium, yellowcake, enriched_uranium, depleted_uranium, plutonium, zircaloy_cladding, isotope_canister, decayed_canister,
          corium_fragment, reactor_linker):
    item_img(f)
for name, col in (("natural_fuel_rod", (110, 120, 80)), ("leu_fuel_rod", (90, 200, 90)), ("mox_fuel_rod", (230, 140, 60))):
    img = canvas()
    rod(col)(ImageDraw.Draw(img))
    save(img, f"item/{name}")

icon = Image.new("RGBA", (128, 128), (20, 26, 34, 255))
d = ImageDraw.Draw(icon)
d.ellipse([10, 10, 118, 118], fill=(40, 120, 255, 255))
d.ellipse([18, 18, 110, 110], fill=(230, 190, 40, 255))
trefoil(d, 64, 64, 40, (20, 20, 20, 255))
icon.save(f"{A}/icon.png")

# ================================================================ models, blockstates, items
def model(name, obj):
    write(f"{A}/models/block/{name}.json", obj)


def blockstate(name, obj):
    write(f"{A}/blockstates/{name}.json", obj)


def item_def(name, model_id):
    write(f"{A}/items/{name}.json", {"model": {"type": "minecraft:model", "model": model_id}})


def gen_item(name):
    write(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"fission:item/{name}"}})
    item_def(name, f"fission:item/{name}")


def box(fr, to, tex, faces=("north", "south", "east", "west", "up", "down")):
    return {"from": fr, "to": to, "faces": {s: {"texture": tex} for s in faces}}


H4 = [("north", 0), ("east", 90), ("south", 180), ("west", 270)]


def simple(name, tex=None):
    model(name, {"parent": "minecraft:block/cube_all", "textures": {"all": f"fission:block/{tex or name}"}})
    blockstate(name, {"variants": {"": {"model": f"fission:block/{name}"}}})
    item_def(name, f"fission:block/{name}")


def oriented(name, front, side, top=None, extra=None):
    """Facing models: front points north in the base model."""
    model(name, {"parent": "minecraft:block/orientable", "textures": {"front": f"fission:block/{front}", "side": f"fission:block/{side}",
                                                                      "top": f"fission:block/{top or side}"}})


for n in ("uranium_ore", "deepslate_uranium_ore", "graphite_moderator", "beryllium_reflector", "reactor_vessel", "feedwater_inlet", "steam_outlet",
          "relief_valve", "condenser", "solid_corium", "reactor_debris", "fuel_fragment"):
    simple(n)

for load in range(3):
    for glow in (0, 1):
        model(f"fuel_channel_{load}_{glow}", {"parent": "minecraft:block/cube_column", "textures": {
            "side": f"fission:block/fuel_channel_side_{load}", "end": f"fission:block/fuel_channel_top_{load}_{glow}"}})
blockstate("fuel_channel", {"variants": {f"glow={str(bool(g)).lower()},load={l}": {"model": f"fission:block/fuel_channel_{l}_{g}"}
                                         for l in range(3) for g in (0, 1)}})
item_def("fuel_channel", "fission:block/fuel_channel_1_0")
for n in range(9):
    model(f"control_rod_{n}", {"parent": "minecraft:block/cube_column", "textures": {"side": "fission:block/control_rod_side",
                                                                                     "end": f"fission:block/control_rod_top_{n}"}})
blockstate("control_rod", {"variants": {f"insertion={n}": {"model": f"fission:block/control_rod_{n}"} for n in range(9)}})
item_def("control_rod", "fission:block/control_rod_8")

def facing_state(name, models_by_props, props=None):
    variants = {}
    for f, r in H4:
        for key, m in models_by_props.items():
            k = f"facing={f}" + (("," + key) if key else "")
            variants[k] = {"model": m, **({"y": r} if r else {})}
    blockstate(name, {"variants": variants})


oriented("reactor_controller", "reactor_controller_front", "control_side")
facing_state("reactor_controller", {"": "fission:block/reactor_controller"})
item_def("reactor_controller", "fission:block/reactor_controller")
model("reactor_console", {"parent": "minecraft:block/block", "textures": {"top": "fission:block/reactor_console_top", "side": "fission:block/control_side",
                                                                          "screen": "fission:block/reactor_console_screen", "particle": "fission:block/control_side"},
                          "elements": [{"from": [0, 0, 0], "to": [16, 9, 16], "faces": {"up": {"texture": "#top"}, "north": {"texture": "#side"},
                                        "south": {"texture": "#side"}, "east": {"texture": "#side"}, "west": {"texture": "#side"}, "down": {"texture": "#side"}}},
                                       {"from": [0, 9, 11], "to": [16, 16, 16], "faces": {"north": {"texture": "#screen"}, "up": {"texture": "#side"},
                                        "south": {"texture": "#side"}, "east": {"texture": "#side"}, "west": {"texture": "#side"}}}]})
facing_state("reactor_console", {f"open={o},powered={p}": "fission:block/reactor_console" for o in ("false", "true") for p in ("false", "true")})
item_def("reactor_console", "fission:block/reactor_console")
for open_ in (0, 1):
    for pressed in (0, 1):
        els = [box([3, 0, 3], [13, 8, 13], "#base"), box([5, 8, 5], [11, 9 if pressed else 10, 11], "#red")]
        els.append(box([3, 8, 12], [13, 16, 13], "#guard") if open_ else box([3, 8, 3], [13, 12, 13], "#guard"))
        model(f"scram_button_{open_}_{pressed}", {"parent": "minecraft:block/block", "render_type": "minecraft:translucent",
                                                  "textures": {"base": "fission:block/scram_base", "red": "fission:block/scram_red",
                                                               "guard": "fission:block/scram_guard", "particle": "fission:block/scram_base"},
                                                  "elements": els})
facing_state("scram_button", {f"open={str(bool(o)).lower()},powered={str(bool(p)).lower()}": f"fission:block/scram_button_{o}_{p}"
                              for o in (0, 1) for p in (0, 1)})
item_def("scram_button", "fission:block/scram_button_0_0")
for n in ("annunciator_panel", "core_map"):
    oriented(n, "panel_front", "control_side")
    facing_state(n, {f"open={o},powered={p}": f"fission:block/{n}" for o in ("false", "true") for p in ("false", "true")})
    item_def(n, f"fission:block/{n}")
oriented("radiation_monitor", "radiation_monitor_front", "radiation_monitor_side")
facing_state("radiation_monitor", {f"alarm={a}": "fission:block/radiation_monitor" for a in ("false", "true")})
item_def("radiation_monitor", "fission:block/radiation_monitor")

THICK = {"water_pipe": 6, "steam_pipe": 8, "fuel_transfer_tube": 6, "isotope_pipe": 5}
for name, t in THICK.items():
    lo, hi = 8 - t / 2, 8 + t / 2
    tex = f"fission:block/{name}"
    model(f"{name}_core", {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex}, "elements": [box([lo, lo, lo], [hi, hi, hi], "#t")]})
    model(f"{name}_arm", {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex},
                          "elements": [box([lo, lo, 0], [hi, hi, lo], "#t", ["east", "west", "up", "down", "north"])]})
    rots = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
    parts = [{"apply": {"model": f"fission:block/{name}_core"}}]
    for d_, r in rots.items():
        parts.append({"when": {d_: "true"}, "apply": {"model": f"fission:block/{name}_arm", **r}})
    blockstate(name, {"multipart": parts})
    model(f"{name}_inventory", {"parent": "minecraft:block/block", "textures": {"t": tex, "particle": tex}, "elements": [box([0, lo, lo], [16, hi, hi], "#t")],
                                "display": {"gui": {"rotation": [30, 45, 0], "scale": [0.8, 0.8, 0.8]}}})
    item_def(name, f"fission:block/{name}_inventory")

for on in (0, 1):
    model(f"feedwater_pump_{on}", {"parent": "minecraft:block/orientable", "textures": {"front": "fission:block/feedwater_pump_front",
                                    "side": f"fission:block/feedwater_pump_side_{on}", "top": f"fission:block/feedwater_pump_side_{on}"}})
facing_state("feedwater_pump", {f"on={str(bool(o)).lower()}": f"fission:block/feedwater_pump_{o}" for o in (0, 1)})
item_def("feedwater_pump", "fission:block/feedwater_pump_0")
oriented("steam_turbine", "steam_turbine_front", "steam_turbine_side")
facing_state("steam_turbine", {f"spinning={s}": "fission:block/steam_turbine" for s in ("false", "true")})
item_def("steam_turbine", "fission:block/steam_turbine")
for on in (0, 1):
    model(f"turbine_generator_{on}", {"parent": "minecraft:block/cube_all", "textures": {"all": f"fission:block/turbine_generator_{on}"}})
facing_state("turbine_generator", {f"on={str(bool(o)).lower()}": f"fission:block/turbine_generator_{o}" for o in (0, 1)})
item_def("turbine_generator", "fission:block/turbine_generator_0")
model("fuel_rack", {"parent": "minecraft:block/block", "render_type": "minecraft:cutout", "textures": {"r": "fission:block/fuel_rack", "particle": "fission:block/fuel_rack"},
                    "elements": [box([1, 0, 1], [15, 16, 15], "#r")]})
facing_state("fuel_rack", {"": "fission:block/fuel_rack"})
item_def("fuel_rack", "fission:block/fuel_rack")
model("holding_basin", {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": "fission:block/holding_basin_top", "side": "fission:block/holding_basin_side",
                                                                                   "bottom": "fission:block/holding_basin_side"}})
facing_state("holding_basin", {"": "fission:block/holding_basin"})
item_def("holding_basin", "fission:block/holding_basin")
model("storage_drum", {"parent": "minecraft:block/block", "textures": {"s": "fission:block/storage_drum_side", "t": "fission:block/storage_drum_top",
                                                                       "particle": "fission:block/storage_drum_side"},
                       "elements": [{"from": [2, 0, 2], "to": [14, 15, 14], "faces": {"north": {"texture": "#s"}, "south": {"texture": "#s"},
                                     "east": {"texture": "#s"}, "west": {"texture": "#s"}, "up": {"texture": "#t"}, "down": {"texture": "#t"}}}]})
facing_state("storage_drum", {"": "fission:block/storage_drum"})
item_def("storage_drum", "fission:block/storage_drum")
oriented("reprocessing_plant", "reprocessing_plant_front", "reprocessing_plant_side")
facing_state("reprocessing_plant", {"": "fission:block/reprocessing_plant"})
item_def("reprocessing_plant", "fission:block/reprocessing_plant")
model("gas_centrifuge", {"parent": "minecraft:block/block", "textures": {"c": "fission:block/gas_centrifuge", "particle": "fission:block/gas_centrifuge"},
                         "elements": [box([4, 0, 4], [12, 16, 12], "#c")]})
facing_state("gas_centrifuge", {"": "fission:block/gas_centrifuge"})
item_def("gas_centrifuge", "fission:block/gas_centrifuge")
for hot in ("hot", "cool"):
    model(f"corium_{hot}", {"parent": "minecraft:block/block", "textures": {"c": f"fission:block/corium_{hot}", "particle": f"fission:block/corium_{hot}"},
                            "elements": [box([0, 0, 0], [16, 14, 16], "#c")]})
blockstate("corium", {"variants": {f"heat={h}": {"model": f"fission:block/corium_{'hot' if h >= 8 else 'cool'}"} for h in range(16)}})
item_def("corium", "fission:block/corium_hot")

ITEMS = ["raw_uranium", "yellowcake", "enriched_uranium", "depleted_uranium", "plutonium", "zircaloy_cladding", "natural_fuel_rod", "leu_fuel_rod",
         "mox_fuel_rod", "isotope_canister", "decayed_canister", "corium_fragment", "reactor_linker"]
for n in ITEMS:
    gen_item(n)

# ================================================================ loot, tags, recipes, worldgen
BLOCKS = ["fuel_channel", "control_rod", "graphite_moderator", "beryllium_reflector", "reactor_vessel", "feedwater_inlet", "steam_outlet", "relief_valve",
          "reactor_controller", "reactor_console", "scram_button", "annunciator_panel", "core_map", "radiation_monitor", "water_pipe", "steam_pipe",
          "feedwater_pump", "steam_turbine", "turbine_generator", "condenser", "fuel_transfer_tube", "isotope_pipe", "fuel_rack", "holding_basin",
          "storage_drum", "reprocessing_plant", "gas_centrifuge", "reactor_debris", "fuel_fragment"]


def drop_self(n):
    write(f"{D}/loot_table/blocks/{n}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
        "entries": [{"type": "minecraft:item", "name": f"fission:{n}"}]}], "random_sequence": f"fission:blocks/{n}"})


for n in BLOCKS:
    drop_self(n)
for n in ("uranium_ore", "deepslate_uranium_ore"):
    write(f"{D}/loot_table/blocks/{n}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:alternatives", "children": [
        {"type": "minecraft:item", "name": f"fission:{n}", "conditions": [{"condition": "minecraft:match_tool", "predicate": {"predicates": {
            "minecraft:enchantments": [{"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}]},
        {"type": "minecraft:item", "name": "fission:raw_uranium", "functions": [{"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune",
                                                                               "formula": "minecraft:ore_drops"}, {"function": "minecraft:explosion_decay"}]}]}]}],
        "random_sequence": f"fission:blocks/{n}"})
write(f"{D}/loot_table/blocks/solid_corium.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item",
    "name": "fission:corium_fragment", "functions": [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}}]}]}],
    "random_sequence": "fission:blocks/solid_corium"})
PICK = [n for n in BLOCKS] + ["uranium_ore", "deepslate_uranium_ore", "solid_corium"]
write(f"{MC}/tags/block/mineable/pickaxe.json", {"replace": False, "values": [f"fission:{n}" for n in PICK if n != "reactor_debris"]})
write(f"{MC}/tags/block/mineable/shovel.json", {"replace": False, "values": ["fission:reactor_debris"]})
write(f"{MC}/tags/block/needs_iron_tool.json", {"replace": False, "values": ["fission:uranium_ore", "fission:deepslate_uranium_ore", "fission:reactor_vessel"]})
write(f"{MC}/tags/block/needs_diamond_tool.json", {"replace": False, "values": ["fission:solid_corium"]})
# the vessel and graphite count as concrete-like shielding for the Radiation mod
write(f"{RES}/data/radiation/tags/block/shielding_heavy.json", {"replace": False, "values": ["fission:reactor_vessel", "fission:feedwater_inlet",
                                                                                           "fission:steam_outlet", "fission:relief_valve"]})
write(f"{RES}/data/radiation/tags/block/shielding_concrete.json", {"replace": False, "values": ["fission:graphite_moderator", "fission:beryllium_reflector",
                                                                                              "fission:holding_basin"]})

write(f"{D}/worldgen/feature/uranium_ore.json", {"type": "minecraft:ore", "discard_chance_on_air_exposure": 0.0, "size": 5, "targets": [
    {"state": "fission:uranium_ore", "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:stone_ore_replaceables"}},
    {"state": "fission:deepslate_uranium_ore", "target": {"predicate_type": "minecraft:tag_match", "tag": "minecraft:deepslate_ore_replaceables"}}]})
write(f"{D}/worldgen/placed_feature/uranium_ore.json", {"feature": "fission:uranium_ore", "placement": [
    {"type": "minecraft:count", "count": 4}, {"type": "minecraft:in_square"},
    {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -60}, "max_inclusive": {"absolute": 16}}},
    {"type": "minecraft:biome"}]})


def shaped(name, pattern, key, count=1, category="redstone"):
    write(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
                                      "result": {"id": f"fission:{name}", **({"count": count} if count > 1 else {})}})


def shapeless(name, ingredients, count=1, category="redstone"):
    write(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients,
                                      "result": {"id": f"fission:{name}", **({"count": count} if count > 1 else {})}})


I, C, R, N = "minecraft:iron_ingot", "minecraft:copper_ingot", "minecraft:redstone", "minecraft:iron_nugget"
for kind in ("smelting", "blasting"):
    write(f"{D}/recipe/yellowcake_from_{kind}.json", {"type": f"minecraft:{kind}", "category": "misc", "ingredient": "fission:raw_uranium",
                                                      "result": {"id": "fission:yellowcake"}, "experience": 0.7, "cookingtime": 200 if kind == "smelting" else 100})
write(f"{D}/recipe/graphite_moderator.json", {"type": "minecraft:blasting", "category": "blocks", "ingredient": "minecraft:coal_block",
                                              "result": {"id": "fission:graphite_moderator"}, "experience": 0.3, "cookingtime": 200})
shapeless("zircaloy_cladding", [I, I, "minecraft:quartz"], 4, "misc")
shaped("natural_fuel_rod", ["ZYZ", "ZYZ", "ZYZ"], {"Z": "fission:zircaloy_cladding", "Y": "fission:yellowcake"}, 1, "misc")
shaped("leu_fuel_rod", ["ZEZ", "ZEZ", "ZEZ"], {"Z": "fission:zircaloy_cladding", "E": "fission:enriched_uranium"}, 1, "misc")
shaped("mox_fuel_rod", ["ZDZ", "ZPZ", "ZDZ"], {"Z": "fission:zircaloy_cladding", "D": "fission:depleted_uranium", "P": "fission:plutonium"}, 1, "misc")
shaped("fuel_channel", ["ZGZ", "Z Z", "ZGZ"], {"Z": "fission:zircaloy_cladding", "G": "minecraft:glass"}, 2)
shaped("control_rod", ["ICI", "IRI", "ICI"], {"I": I, "C": "minecraft:coal_block", "R": R}, 2)
shaped("beryllium_reflector", ["IEI", "EIE", "IEI"], {"I": I, "E": "minecraft:emerald"}, 4)
shaped("reactor_vessel", ["III", "IOI", "III"], {"I": I, "O": "minecraft:obsidian"}, 4)
shapeless("feedwater_inlet", ["fission:reactor_vessel", "fission:water_pipe"])
shapeless("steam_outlet", ["fission:reactor_vessel", "fission:steam_pipe"])
shapeless("relief_valve", ["fission:reactor_vessel", "fission:steam_pipe", "minecraft:lever"])
shaped("reactor_controller", ["IGI", "RKR", "III"], {"I": I, "G": "minecraft:glass_pane", "R": R, "K": "minecraft:comparator"})
shaped("reactor_console", ["GGG", "RKR", "III"], {"I": I, "G": "minecraft:glass_pane", "R": R, "K": "minecraft:comparator"})
shapeless("scram_button", ["minecraft:red_dye", "minecraft:stone_button", I, "minecraft:glass_pane"])
shaped("annunciator_panel", ["LLL", "RIR", "III"], {"L": "minecraft:redstone_lamp", "R": R, "I": I})
shaped("core_map", ["LLL", "LKL", "III"], {"L": "minecraft:redstone_lamp", "K": "minecraft:comparator", "I": I})
shaped("radiation_monitor", ["IGI", "IRI"], {"I": I, "G": "radiation:geiger_counter", "R": R})
shapeless("water_pipe", [C, C, I, "minecraft:blue_dye"], 8)
shapeless("steam_pipe", [I, I, I, "minecraft:white_wool"], 8)
shaped("feedwater_pump", ["IPI", "WMW", "III"], {"I": I, "P": "minecraft:piston", "W": "fission:water_pipe", "M": "minecraft:copper_block"})
shaped("steam_turbine", ["III", "SBS", "III"], {"I": I, "S": "fission:steam_pipe", "B": "minecraft:iron_block"})
shaped("turbine_generator", ["ICI", "CBC", "ICI"], {"I": I, "C": "minecraft:copper_block", "B": "minecraft:redstone_block"})
shaped("condenser", ["IBI", "BSB", "IBI"], {"I": I, "B": "minecraft:iron_bars", "S": "fission:steam_pipe"})
shapeless("fuel_transfer_tube", ["minecraft:glass", I, I], 8)
shapeless("isotope_pipe", [I, I, "minecraft:gold_ingot"], 8)
shaped("fuel_rack", ["I I", "IZI", "I I"], {"I": I, "Z": "fission:zircaloy_cladding"})
shaped("holding_basin", ["CWC", "CWC", "CCC"], {"C": "radiation:reinforced_concrete", "W": "minecraft:water_bucket"})
shaped("storage_drum", ["ICI", "I I", "ICI"], {"I": I, "C": "radiation:reinforced_concrete"}, 2)
shaped("reprocessing_plant", ["CGC", "CPC", "CCC"], {"C": "radiation:heavy_concrete", "G": "minecraft:glass_pane", "P": "minecraft:piston"})
shaped("gas_centrifuge", ["IRI", "IKI", "IRI"], {"I": I, "R": R, "K": "minecraft:comparator"})
shapeless("reactor_linker", [C, C, R, "minecraft:string"], 1, "equipment")

# ================================================================ language
lang = {
    "itemGroup.fission": "Fission",
    "fission.yes": "yes", "fission.no": "no",
    "block.fission.uranium_ore": "Uranium Ore", "block.fission.uranium_ore.desc": "Pitchblende in stone. Deep down, below Y 16.",
    "block.fission.deepslate_uranium_ore": "Deepslate Uranium Ore", "block.fission.deepslate_uranium_ore.desc": "Pitchblende in deepslate.",
    "block.fission.fuel_channel": "Fuel Channel", "block.fission.fuel_channel.desc": "Pressure tube for one fuel assembly. Nominal 1 MW thermal.",
    "block.fission.fuel_channel.empty": "Empty fuel channel", "block.fission.fuel_channel.holds": "Holds: %s (sneak-use to pull it out - careful!)",
    "block.fission.control_rod": "Control Rod", "block.fission.control_rod.desc": "Boron carbide absorber in its channel, driven by the reactor controller.",
    "block.fission.graphite_moderator": "Graphite Moderator", "block.fission.graphite_moderator.desc": "Nuclear-grade graphite: slows neutrons down without eating them.",
    "block.fission.beryllium_reflector": "Beryllium Reflector", "block.fission.beryllium_reflector.desc": "Sends escaping neutrons back into the core.",
    "block.fission.reactor_vessel": "Reactor Vessel", "block.fission.reactor_vessel.desc": "Steel pressure vessel wall.",
    "block.fission.feedwater_inlet": "Feedwater Inlet", "block.fission.feedwater_inlet.desc": "Vessel nozzle for the feedwater pipe.",
    "block.fission.steam_outlet": "Steam Outlet", "block.fission.steam_outlet.desc": "Vessel nozzle for the main steam line.",
    "block.fission.relief_valve": "Pressure Relief Valve", "block.fission.relief_valve.desc": "Blows off steam above 85 bar. Without one the vessel bursts at 150.",
    "block.fission.reactor_controller": "Reactor Controller", "block.fission.reactor_controller.desc": "Instrumentation and rod drives. Place it against the core.",
    "block.fission.reactor_console": "Reactor Control Console", "block.fission.reactor_console.desc": "The control desk. Link it with the data cable.",
    "block.fission.scram_button": "SCRAM Button", "block.fission.scram_button.desc": "Lift the guard, press: all rods drop in. Redstone works too.",
    "block.fission.annunciator_panel": "Annunciator Panel", "block.fission.annunciator_panel.desc": "Alarm windows that light up.",
    "block.fission.core_map": "Core Map", "block.fission.core_map.desc": "Power of every fuel channel, seen from above.",
    "block.fission.radiation_monitor": "Area Radiation Monitor", "block.fission.radiation_monitor.desc": "Shows the dose rate in front of it and alarms.",
    "block.fission.radiation_monitor.status": "%s rad/s, alarm at %s rad/s (sneak-use to change)",
    "block.fission.water_pipe": "Feedwater Pipe", "block.fission.water_pipe.desc": "Carries water from the pumps to the reactor.",
    "block.fission.steam_pipe": "Steam Pipe", "block.fission.steam_pipe.desc": "Insulated main steam line to turbines and condensers.",
    "block.fission.feedwater_pump": "Feedwater Pump", "block.fission.feedwater_pump.desc": "10 kV, up to 20 kg/s from water next to it; about 9 kW per kg/s at 70 bar.",
    "block.fission.feedwater_pump.status": "Powered: %s, water: %s, pumping %s kg/s",
    "block.fission.steam_turbine": "Steam Turbine", "block.fission.steam_turbine.desc": "Spins up to 3000 rpm on steam above 40 bar.",
    "block.fission.steam_turbine.status": "%s rpm, steam %s kg/s at %s bar, generating %s MW",
    "block.fission.turbine_generator": "Turbine Generator", "block.fission.turbine_generator.desc": "5 MW at 10 kV into Gridworks. Put it on the turbine's shaft end.",
    "block.fission.condenser": "Condenser", "block.fission.condenser.desc": "Turbine bypass: 20 kg/s with cooling water next to it, 3 kg/s without.",
    "block.fission.fuel_transfer_tube": "Fuel Transfer Tube", "block.fission.fuel_transfer_tube.desc": "Moves fuel assemblies: rack to reactor to basin to reprocessing.",
    "block.fission.isotope_pipe": "Isotope Pipe", "block.fission.isotope_pipe.desc": "Shielded pipe for canisters, plutonium and other waste.",
    "block.fission.fuel_rack": "Fuel Rack", "block.fission.fuel_rack.desc": "Fresh fuel. Sends it down fuel transfer tubes to empty channels.",
    "block.fission.holding_basin": "Holding Basin", "block.fission.holding_basin.desc": "Pool for short-lived isotopes and spent fuel. Water shields almost everything.",
    "block.fission.storage_drum": "Long-Term Storage Drum", "block.fission.storage_drum.desc": "For long-lived isotopes. Holds back about 97 % of their radiation.",
    "block.fission.reprocessing_plant": "Reprocessing Plant", "block.fission.reprocessing_plant.desc": "230 V, 3 kW. Splits cooled spent fuel into isotopes.",
    "block.fission.gas_centrifuge": "Gas Centrifuge", "block.fission.gas_centrifuge.desc": "230 V, 2 kW. Enriches yellowcake: 4 in, 1 enriched + 3 depleted out.",
    "block.fission.corium": "Molten Corium", "block.fission.solid_corium": "Solidified Corium",
    "block.fission.solid_corium.desc": "The elephant's foot. Lethal for centuries.",
    "block.fission.reactor_debris": "Radioactive Graphite Debris", "block.fission.reactor_debris.desc": "Irradiated graphite blown out of a reactor. Very radioactive.",
    "entity.fission.radioactive_cloud": "Radioactive Cloud",
    "block.fission.fuel_fragment": "Fuel Fragment", "block.fission.fuel_fragment.desc": "A piece of a fuel assembly from an exploded core. Deadly within metres.",
    "block.fission.not_linked": "Not linked: use the data cable on a reactor controller, then here",
    "block.fission.linked_to": "Linked to the reactor at %s",
    "item.fission.raw_uranium": "Raw Uranium", "item.fission.yellowcake": "Yellowcake", "item.fission.yellowcake.desc": "Natural uranium oxide, 0.7 % U-235.",
    "item.fission.enriched_uranium": "Enriched Uranium", "item.fission.enriched_uranium.desc": "3.5 % U-235.",
    "item.fission.depleted_uranium": "Depleted Uranium", "item.fission.depleted_uranium.desc": "Mostly U-238.",
    "item.fission.plutonium": "Plutonium", "item.fission.plutonium.desc": "Pu-239 from reprocessing, for MOX fuel.",
    "item.fission.zircaloy_cladding": "Zircaloy Cladding", "item.fission.zircaloy_cladding.desc": "Fuel rod tubes.",
    "item.fission.natural_fuel_rod": "Natural Uranium Fuel Assembly", "item.fission.natural_fuel_rod.desc": "0.7 % U-235. Needs a big graphite pile.",
    "item.fission.natural_fuel_rod.irradiated": "Irradiated Natural Uranium Assembly", "item.fission.natural_fuel_rod.spent": "Spent Natural Uranium Assembly",
    "item.fission.leu_fuel_rod": "LEU Fuel Assembly", "item.fission.leu_fuel_rod.desc": "3.5 % enriched. The standard fuel.",
    "item.fission.leu_fuel_rod.irradiated": "Irradiated LEU Assembly", "item.fission.leu_fuel_rod.spent": "Spent LEU Assembly",
    "item.fission.mox_fuel_rod": "MOX Fuel Assembly", "item.fission.mox_fuel_rod.desc": "Recycled plutonium in depleted uranium.",
    "item.fission.mox_fuel_rod.irradiated": "Irradiated MOX Assembly", "item.fission.mox_fuel_rod.spent": "Spent MOX Assembly",
    "item.fission.fuel_rod.burnup": "Burnup %s of %s MWd",
    "item.fission.fuel_rod.activity": "Activity %s, %s at 1 m",
    "item.fission.fuel_rod.decay_heat": "Decay heat %s W",
    "item.fission.isotope_canister": "Isotope Canister", "item.fission.isotope_canister.of": "Canister: %s",
    "item.fission.isotope_canister.half_life": "Half-life %s",
    "item.fission.isotope_canister.activity": "%s, %s at 1 m",
    "item.fission.isotope_canister.short": "Short-lived: holding basin",
    "item.fission.isotope_canister.long": "Long-lived: long-term storage drum",
    "item.fission.decayed_canister": "Decayed Canister", "item.fission.decayed_canister.desc": "Nothing left worth worrying about.",
    "item.fission.corium_fragment": "Corium Fragment", "item.fission.corium_fragment.desc": "A piece of the elephant's foot. Do not carry.",
    "item.fission.reactor_linker": "Data Cable", "item.fission.reactor_linker.desc": "Use on a reactor controller, then on control room equipment.",
    "item.fission.reactor_linker.picked": "Reactor at %s - now use the cable on a console, SCRAM button, annunciator or core map",
    "item.fission.reactor_linker.first": "Use the cable on a reactor controller first",
    "item.fission.reactor_linker.linked": "Linked to the reactor at %s",
    "screen.fission.reactor": "REACTOR CONTROL", "screen.fission.no_signal": "No signal from the reactor (not loaded?)",
    "screen.fission.powered": "Powered", "screen.fission.no_power": "No power (230 V)",
    "alarm.fission.scram": "SCRAM", "alarm.fission.rps_bypassed": "RPS BYPASSED", "alarm.fission.high_power": "HIGH POWER",
    "alarm.fission.short_period": "SHORT PERIOD", "alarm.fission.high_fuel_temp": "FUEL TEMP", "alarm.fission.low_water": "LOW LEVEL",
    "alarm.fission.high_pressure": "HIGH PRESS", "alarm.fission.relief_open": "RELIEF OPEN", "alarm.fission.feedwater_low": "FEED LOW",
    "alarm.fission.xenon": "XENON", "alarm.fission.high_reactivity": "HIGH REACT", "alarm.fission.core_damage": "CORE DAMAGE",
    "subtitles.fission.scram": "Reactor SCRAM klaxon", "subtitles.fission.alarm": "Control room alarm", "subtitles.fission.rad_alarm": "Radiation alarm",
    "subtitles.fission.relief_valve": "Relief valve blows", "subtitles.fission.turbine": "Turbine whines", "subtitles.fission.corium": "Corium sizzles",
    "subtitles.fission.steam_hiss": "Water flashes to steam", "subtitles.fission.reactor_boom": "Reactor explodes",
}
write(f"{A}/lang/en_us.json", lang)

# ================================================================ sounds
SR = 44100
rs = np.random.default_rng(5)


def ogg(name, x, volume=0.9):
    x = np.asarray(x, dtype=np.float64)
    peak = np.max(np.abs(x)) or 1
    x = x / peak * volume
    os.makedirs(f"{A}/sounds", exist_ok=True)
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as t:
        sf.write(t.name, x.astype(np.float32), SR)
        subprocess.run(["oggenc", "-Q", "-q", "4", "-o", f"{A}/sounds/{name}.ogg", t.name], check=True)
        os.unlink(t.name)


def lowpass(x, a):
    y = np.zeros_like(x)
    for i in range(1, len(x)):
        y[i] = y[i - 1] + a * (x[i] - y[i - 1])
    return y


def t(sec):
    return np.arange(int(SR * sec)) / SR


# klaxon: two-tone horn, square-ish with vibrato
tt = t(2.4)
f = np.where((tt % 0.8) < 0.4, 520, 390)
ph = np.cumsum(2 * np.pi * f / SR)
k = np.sign(np.sin(ph)) * 0.6 + np.sin(2 * ph) * 0.3
ogg("scram", lowpass(k, 0.25) * np.minimum(1, (2.4 - tt) * 8), 0.9)
tt = t(0.9)
beep = np.sin(2 * np.pi * 880 * tt) * (((tt % 0.3) < 0.18).astype(float))
ogg("alarm", beep, 0.7)
tt = t(0.5)
ogg("rad_alarm", np.sin(2 * np.pi * 1400 * tt) * (((tt % 0.12) < 0.06).astype(float)), 0.6)
tt = t(2.0)
hiss = rs.standard_normal(len(tt))
ogg("relief_valve", lowpass(hiss, 0.6) * np.minimum(1, tt * 6) * np.minimum(1, (2.0 - tt) * 4), 0.9)
ogg("steam_hiss", lowpass(rs.standard_normal(len(t(1.2))), 0.5) * np.exp(-t(1.2) * 2.0), 0.9)
tt = t(2.0)
whine = np.sin(2 * np.pi * 1000 * tt) * 0.25 + np.sin(2 * np.pi * 50 * tt) * 0.35 + lowpass(rs.standard_normal(len(tt)), 0.05) * 0.4
ogg("turbine", whine, 0.6)
tt = t(1.5)
crack = lowpass(rs.standard_normal(len(tt)), 0.3) * (rs.random(len(tt)) < 0.02) * 4 + lowpass(rs.standard_normal(len(tt)), 0.1) * 0.5
ogg("corium", crack * np.exp(-tt * 0.8), 0.9)
tt = t(4.0)
boom = lowpass(rs.standard_normal(len(tt)), 0.02) * np.exp(-tt * 1.2) * 3 + np.sin(2 * np.pi * 32 * tt) * np.exp(-tt * 2) * 2
ogg("reactor_boom", boom, 1.0)
sounds = {}
for name, dist in (("scram", 48), ("alarm", 24), ("rad_alarm", 16), ("relief_valve", 48), ("turbine", 24), ("corium", 24), ("steam_hiss", 24),
                   ("reactor_boom", 256)):
    sounds[name] = {"sounds": [{"name": f"fission:{name}", "attenuation_distance": dist}], "subtitle": f"subtitles.fission.{name}"}
write(f"{A}/sounds.json", sounds)
print("fission assets written")
