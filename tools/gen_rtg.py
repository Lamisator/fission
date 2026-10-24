"""Assets of the radioisotope thermoelectric generator and its Pu-238 heat source: textures, models, blockstate, item
definitions, loot table, tags, recipes and language entries. Run from the repository root after gen_assets.py (which
calls it at its end): it adds to the language file and tags gen_assets.py wrote. Needs Pillow."""
import json, os, random
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


def merge_list(path, values):
    """Adds values to a tag file (keeping what is there)."""
    obj = {"replace": False, "values": []}
    if os.path.exists(path):
        with open(path) as f:
            obj = json.load(f)
    for v in values:
        if v not in obj["values"]:
            obj["values"].append(v)
    write(path, obj)


def save(img, rel):
    path = f"{A}/textures/{rel}.png"
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def rgba(c, a=255):
    return tuple(c) + (a,)


def noise(img, amount=5, seed=0):
    r = random.Random(seed)
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            p = px[x, y]
            if p[3] == 0:
                continue
            d = r.randint(-amount, amount)
            px[x, y] = tuple(max(0, min(255, c + d)) for c in p[:3]) + (p[3],)
    return img


def canvas(color=(0, 0, 0, 0)):
    return Image.new("RGBA", (16, 16), color)


def trefoil(d, cx, cy, r, fg):
    for k in range(3):
        a0 = -90 + k * 120 - 30
        d.pieslice([cx - r, cy - r, cx + r, cy + r], a0, a0 + 60, fill=fg)
    d.ellipse([cx - r * 0.22, cy - r * 0.22, cx + r * 0.22, cy + r * 0.22], fill=fg)


FIN = (74, 78, 84)
FIN_L = (120, 126, 134)
FIN_D = (40, 42, 46)
YELLOW = (230, 190, 40)

# ================================================================ textures
# side: vertical cooling fins with a yellow band and a trefoil
side = canvas(rgba(FIN))
d = ImageDraw.Draw(side)
for x in range(0, 16, 3):
    d.line([(x, 0), (x, 15)], fill=rgba(FIN_L))
    d.line([(x + 1, 0), (x + 1, 15)], fill=rgba(FIN_D))
d.rectangle([2, 3, 13, 12], fill=rgba(YELLOW), outline=rgba((140, 110, 20)))
# the trefoil, drawn large and scaled down so its three blades and the dot stay apart
big = Image.new("L", (100, 100), 0)
bd = ImageDraw.Draw(big)
for k in range(3):
    a0 = -90 + k * 120 - 30
    bd.pieslice([0, 0, 99, 99], a0, a0 + 60, fill=255)
bd.ellipse([30, 30, 69, 69], fill=0)
bd.ellipse([40, 40, 59, 59], fill=255)
small = big.resize((10, 10), Image.BOX)
for yy in range(10):
    for xx in range(10):
        if small.getpixel((xx, yy)) >= 110:
            d.point((3 + xx, 3 + yy), fill=rgba((20, 20, 20)))
d.line([(0, 0), (15, 0)], fill=rgba(FIN_D))
d.line([(0, 15), (15, 15)], fill=rgba(FIN_D))
save(noise(side, 4, 91), "block/rtg_side")
top = canvas(rgba((96, 100, 106)))
d = ImageDraw.Draw(top)
d.ellipse([1, 1, 14, 14], outline=rgba(FIN_D))
d.ellipse([4, 4, 11, 11], fill=rgba((70, 72, 78)), outline=rgba(FIN_D))
for x, y in ((2, 7), (13, 7), (7, 2), (7, 13)):
    d.point((x, y), fill=rgba(FIN_L))
d.rectangle([6, 6, 9, 9], fill=rgba((176, 32, 28)))
save(noise(top, 3, 92), "block/rtg_top")

# heat source: an iridium-clad pellet stack glowing orange at the ends
img = canvas()
d = ImageDraw.Draw(img)
d.rectangle([4, 2, 11, 13], fill=rgba((200, 204, 212)), outline=rgba((110, 114, 122)))
for y in (5, 8, 11):
    d.line([(5, y), (10, y)], fill=rgba((150, 154, 162)))
d.rectangle([6, 1, 9, 2], fill=rgba((255, 120, 40)))
d.rectangle([6, 13, 9, 14], fill=rgba((255, 120, 40)))
d.point((7, 6), fill=rgba((255, 170, 80)))
save(img, "item/pu238_heat_source")

# ================================================================ models, blockstates, items
ALL = ("north", "south", "east", "west", "up", "down")
faces = {s: {"texture": "#s"} for s in ("north", "south", "east", "west")}
faces["up"] = {"texture": "#t"}
faces["down"] = {"texture": "#t"}
write(f"{A}/models/block/rtg.json", {"parent": "minecraft:block/block", "textures": {"s": "fission:block/rtg_side", "t": "fission:block/rtg_top",
                                                                                   "particle": "fission:block/rtg_side"},
                                     "elements": [{"from": [2, 0, 2], "to": [14, 14, 14], "faces": faces},
                                                  {"from": [1, 1, 1], "to": [15, 2, 15], "faces": {s: {"texture": "#t"} for s in ALL}},
                                                  {"from": [1, 12, 1], "to": [15, 13, 15], "faces": {s: {"texture": "#t"} for s in ALL}}]})
write(f"{A}/blockstates/rtg.json", {"variants": {f"facing={f}": {"model": "fission:block/rtg", **({"y": r} if r else {})}
                                                 for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
write(f"{A}/items/rtg.json", {"model": {"type": "minecraft:model", "model": "fission:block/rtg"}})
write(f"{A}/models/item/pu238_heat_source.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "fission:item/pu238_heat_source"}})
write(f"{A}/items/pu238_heat_source.json", {"model": {"type": "minecraft:model", "model": "fission:item/pu238_heat_source"}})

# ================================================================ loot, tags, recipes
write(f"{D}/loot_table/blocks/rtg.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
    "entries": [{"type": "minecraft:item", "name": "fission:rtg"}]}], "random_sequence": "fission:blocks/rtg"})
merge_list(f"{MC}/tags/block/mineable/pickaxe.json", ["fission:rtg"])
merge_list(f"{MC}/tags/block/needs_iron_tool.json", ["fission:rtg"])


def shaped(name, pattern, key, count=1, category="redstone"):
    write(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
                                      "result": {"id": f"fission:{name}", **({"count": count} if count > 1 else {})}})


shaped("rtg", ["BIB", "CRC", "BIB"], {"B": "minecraft:iron_bars", "I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot", "R": "minecraft:redstone"})
shaped("pu238_heat_source", ["PZP", "ZGZ", "PZP"], {"P": "fission:plutonium", "Z": "fission:zircaloy_cladding", "G": "fission:graphite_moderator"},
       1, "misc")

# ================================================================ language
LANG = {
    "block.fission.rtg": "Radioisotope Generator",
    "block.fission.rtg.desc": "12 V DC from up to 4 Pu-238 heat sources, 30 W each. No fuel to feed: half power after ~445 real days.",
    "block.fission.rtg.output": "Output %s W at 12 V",
    "item.fission.pu238_heat_source": "Pu-238 Heat Source",
    "item.fission.pu238_heat_source.desc": "Iridium-clad plutonium-238 oxide, 500 W of decay heat. Fuel for the radioisotope generator.",
    "item.fission.pu238_heat_source.output": "%s W in a generator (%s %% of fresh)",
    "item.fission.pu238_heat_source.half_life": "Half-life 87.7 years: half power after %s real days",
}
path = f"{A}/lang/en_us.json"
with open(path) as f:
    lang = json.load(f)
lang.update(LANG)
write(path, lang)
