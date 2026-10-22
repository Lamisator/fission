"""Assets of the CASTOR transport chain (cask wagon, shunting locomotive, station rail, gantry crane, final repository):
textures, models, blockstates, item definitions, loot tables, tags, recipes and language entries. Run from the repository
root after gen_assets.py (which calls it at its end): it adds to the language file and tags gen_assets.py wrote.
Needs Pillow and the Minecraft client jar from the Loom cache (for the vanilla rail texture)."""
import glob, io, json, os, random, zipfile
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


def vanilla(rel):
    jars = glob.glob(os.path.expanduser("~/.gradle/caches/fabric-loom/*/minecraft-client.jar"))
    with zipfile.ZipFile(sorted(jars)[-1]) as z:
        return Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/{rel}.png"))).convert("RGBA")


CASK = (58, 64, 72)        # ductile cast iron, painted
CASK_D = (36, 40, 46)
CASK_L = (96, 104, 114)
RED = (176, 32, 28)         # traffic red, like DB shunters
RED_D = (118, 20, 18)
YELLOW = (230, 184, 32)
BLACK = (26, 26, 28)

# ================================================================ textures
# cask body: cooling fins all round
img = canvas(rgba(CASK))
d = ImageDraw.Draw(img)
for x in range(0, 16, 2):
    d.line([(x, 0), (x, 15)], fill=rgba(CASK_D))
d.line([(0, 0), (15, 0)], fill=rgba(CASK_L))
save(noise(img, 4, 1), "block/castor_cask_body")
# lid: bolted double lid with a trefoil plate
img = canvas(rgba(CASK_D))
d = ImageDraw.Draw(img)
d.ellipse([1, 1, 14, 14], fill=rgba(CASK), outline=rgba(CASK_L))
d.ellipse([4, 4, 11, 11], fill=rgba(CASK_L), outline=rgba(CASK_D))
for x, y in ((7, 1), (12, 3), (14, 7), (12, 12), (7, 14), (2, 12), (1, 7), (2, 3)):
    d.point((x, y), fill=rgba((180, 186, 194)))
d.rectangle([6, 6, 9, 9], fill=rgba(YELLOW))
d.point((7, 7), fill=rgba(BLACK))
d.point((8, 8), fill=rgba(BLACK))
save(noise(img, 3, 2), "block/castor_cask_lid")
img = canvas(rgba((70, 72, 76)))
save(noise(img, 4, 3), "block/castor_trunnion")
# flatcar deck under the cask
img = canvas(rgba((52, 46, 40)))
d = ImageDraw.Draw(img)
for y in range(0, 16, 4):
    d.line([(0, y), (15, y)], fill=rgba((36, 32, 28)))
save(noise(img, 4, 4), "block/castor_deck")

# shunter: red hood with grilles, cab with windows, grey roof, black frame
img = canvas(rgba(RED))
d = ImageDraw.Draw(img)
for y in range(3, 13, 2):
    d.line([(3, y), (12, y)], fill=rgba(RED_D))
d.line([(0, 15), (15, 15)], fill=rgba(YELLOW))
save(noise(img, 4, 5), "block/shunter_hood")
img = canvas(rgba(RED))
d = ImageDraw.Draw(img)
d.rectangle([2, 2, 13, 7], fill=rgba((150, 190, 210)))
d.rectangle([2, 2, 13, 7], outline=rgba(BLACK))
d.line([(7, 2), (7, 7)], fill=rgba(BLACK))
d.line([(0, 15), (15, 15)], fill=rgba(YELLOW))
save(noise(img, 3, 6), "block/shunter_cab")
save(noise(canvas(rgba((120, 124, 128))), 4, 7), "block/shunter_roof")
img = canvas(rgba(BLACK))
d = ImageDraw.Draw(img)
for x in range(0, 16, 4):
    d.line([(x, 0), (x + 3, 15)], fill=rgba(YELLOW))
save(img, "block/shunter_frame")

# station rail: a plain rail with a stop marker across the middle sleepers, red (waiting) or green (powered: go)
base = vanilla("block/rail")
for name, lamp in (("station_rail", (200, 40, 30)), ("station_rail_on", (40, 200, 60))):
    img = base.copy()
    d = ImageDraw.Draw(img)
    for x in range(3, 13):
        d.point((x, 7), fill=rgba(YELLOW if (x // 2) % 2 == 0 else BLACK))
        d.point((x, 8), fill=rgba(BLACK if (x // 2) % 2 == 0 else YELLOW))
    d.rectangle([7, 6, 8, 9], fill=rgba(lamp))
    save(img, f"block/{name}")

# gantry crane cab: yellow steel with a window and a trefoil
img = canvas(rgba(YELLOW))
d = ImageDraw.Draw(img)
d.rectangle([0, 0, 15, 15], outline=rgba((160, 120, 20)))
for x, y in ((2, 2), (13, 2), (2, 13), (13, 13)):
    d.point((x, y), fill=rgba((90, 90, 90)))
save(noise(img, 4, 8), "block/gantry_crane_side")
img = canvas(rgba(YELLOW))
d = ImageDraw.Draw(img)
d.rectangle([0, 0, 15, 15], outline=rgba((160, 120, 20)))
d.rectangle([2, 2, 13, 8], fill=rgba((140, 186, 210)), outline=rgba(BLACK))
d.rectangle([4, 11, 6, 13], fill=rgba((40, 200, 60)))
d.rectangle([9, 11, 11, 13], fill=rgba((200, 40, 30)))
save(noise(img, 3, 9), "block/gantry_crane_front")
img = canvas(rgba((90, 92, 96)))
d = ImageDraw.Draw(img)
d.rectangle([0, 0, 15, 15], outline=rgba((60, 62, 66)))
d.rectangle([5, 5, 10, 10], fill=rgba(YELLOW))
save(noise(img, 4, 10), "block/gantry_crane_top")

# final repository: rock with a bentonite-sealed disposal hole and a copper lid on top
img = canvas(rgba((92, 88, 84)))
d = ImageDraw.Draw(img)
r = random.Random(11)
for _ in range(14):
    x, y = r.randint(0, 15), r.randint(0, 15)
    d.point((x, y), fill=rgba((70, 66, 62)))
d.line([(0, 0), (15, 0)], fill=rgba((150, 140, 120)))
save(noise(img, 6, 11), "block/final_repository_side")
img = canvas(rgba((168, 158, 132)))      # bentonite clay
d = ImageDraw.Draw(img)
d.ellipse([3, 3, 12, 12], fill=rgba((184, 108, 60)), outline=rgba((120, 70, 40)))   # copper canister lid
d.ellipse([6, 6, 9, 9], fill=rgba((210, 140, 90)))
d.rectangle([0, 0, 15, 15], outline=rgba((100, 96, 90)))
save(noise(img, 4, 12), "block/final_repository_top")

# items
img = canvas()
d = ImageDraw.Draw(img)
d.rectangle([1, 11, 14, 13], fill=rgba((70, 70, 74)))
d.ellipse([2, 13, 5, 15], fill=rgba(BLACK))
d.ellipse([10, 13, 13, 15], fill=rgba(BLACK))
d.rectangle([2, 4, 13, 11], fill=rgba(CASK), outline=rgba(CASK_D))
for x in range(3, 13, 2):
    d.line([(x, 4), (x, 11)], fill=rgba(CASK_D))
d.rectangle([0, 6, 1, 9], fill=rgba(CASK_L))
d.rectangle([14, 6, 15, 9], fill=rgba(CASK_L))
d.rectangle([6, 6, 9, 9], fill=rgba(YELLOW))
save(img, "item/castor_wagon")
img = canvas()
d = ImageDraw.Draw(img)
d.rectangle([1, 11, 14, 12], fill=rgba(BLACK))
d.ellipse([2, 12, 5, 15], fill=rgba(BLACK))
d.ellipse([10, 12, 13, 15], fill=rgba(BLACK))
d.rectangle([1, 6, 9, 11], fill=rgba(RED), outline=rgba(RED_D))
d.rectangle([9, 2, 14, 11], fill=rgba(RED), outline=rgba(RED_D))
d.rectangle([10, 3, 13, 6], fill=rgba((150, 190, 210)))
d.rectangle([3, 3, 4, 6], fill=rgba(BLACK))
d.line([(1, 10), (14, 10)], fill=rgba(YELLOW))
save(img, "item/shunting_locomotive")

# ================================================================ models, blockstates, items


def model(name, obj):
    write(f"{A}/models/block/{name}.json", obj)


def blockstate(name, obj):
    write(f"{A}/blockstates/{name}.json", obj)


def item_def(name, model_id):
    write(f"{A}/items/{name}.json", {"model": {"type": "minecraft:model", "model": model_id}})


def box(fr, to, faces):
    return {"from": fr, "to": to, "faces": {s: {"texture": t} for s, t in faces.items()}}


ALL = ("north", "south", "east", "west", "up", "down")

# The cask lies on its flatcar along the model's z axis (the cart's renderer turns the display block so z runs along
# the track); it is a little longer and wider than a block, so a cask on a cart looks as heavy as it is.
els = [box([1, 0, -5], [15, 2, 21], {s: "#deck" for s in ALL})]
side = {"east": "#body", "west": "#body", "up": "#body", "down": "#body", "north": "#lid", "south": "#lid"}
for (x0, x1, y0, y1) in ((0, 16, 6, 14), (1, 15, 4, 16), (2, 14, 3, 17), (4, 12, 2, 18)):
    els.append(box([x0, y0, -4], [x1, y1, 20], side))
fin = {s: "#body" for s in ALL}
for z in (-1, 2, 5, 8, 11, 14, 17):
    els.append(box([-0.5, 6, z], [16.5, 14, z + 1], fin))
    els.append(box([2, 1.5, z], [14, 18.5, z + 1], fin))
for z0 in (-2, 15):
    els.append(box([-1.5, 9, z0], [17.5, 11, z0 + 3], {s: "#trunnion" for s in ALL}))
model("castor_cask", {"parent": "minecraft:block/block", "textures": {"body": "fission:block/castor_cask_body", "lid": "fission:block/castor_cask_lid",
                                                                       "trunnion": "fission:block/castor_trunnion", "deck": "fission:block/castor_deck",
                                                                       "particle": "fission:block/castor_cask_body"}, "elements": els})
blockstate("castor_cask", {"variants": {"": {"model": "fission:block/castor_cask"}}})

# The shunter along z: long hood in front, cab at the back, exhaust stack on the hood, frame with buffer beams.
els = [box([0, 0, -5], [16, 2, 21], {s: "#frame" for s in ALL}),
       box([2, 2, -4], [14, 12, 10], {"north": "#hood", "south": "#hood", "east": "#hood", "west": "#hood", "up": "#roof", "down": "#frame"}),
       box([0, 2, 10], [16, 19, 20], {"north": "#cab", "south": "#cab", "east": "#cab", "west": "#cab", "up": "#roof", "down": "#frame"}),
       box([-0.5, 19, 9.5], [16.5, 20, 20.5], {s: "#roof" for s in ALL}),
       box([7, 12, 0], [9, 16, 2], {s: "#frame" for s in ALL})]
model("shunter_body", {"parent": "minecraft:block/block", "textures": {"hood": "fission:block/shunter_hood", "cab": "fission:block/shunter_cab",
                                                                        "roof": "fission:block/shunter_roof", "frame": "fission:block/shunter_frame",
                                                                        "particle": "fission:block/shunter_hood"}, "elements": els})
blockstate("shunter_body", {"variants": {"": {"model": "fission:block/shunter_body"}}})

# station rail: like the powered rail, its texture shows whether it lets the train go
for on in ("", "_on"):
    model(f"station_rail{on}", {"parent": "minecraft:block/rail_flat", "textures": {"rail": f"fission:block/station_rail{on}"}})
    model(f"station_rail{on}_raised_ne", {"parent": "minecraft:block/template_rail_raised_ne", "textures": {"rail": f"fission:block/station_rail{on}"}})
    model(f"station_rail{on}_raised_sw", {"parent": "minecraft:block/template_rail_raised_sw", "textures": {"rail": f"fission:block/station_rail{on}"}})
variants = {}
for powered, on in (("false", ""), ("true", "_on")):
    for shape, suffix, y in (("north_south", "", 0), ("east_west", "", 90), ("ascending_north", "_raised_ne", 0), ("ascending_south", "_raised_sw", 0),
                             ("ascending_east", "_raised_ne", 90), ("ascending_west", "_raised_sw", 90)):
        variants[f"powered={powered},shape={shape}"] = {"model": f"fission:block/station_rail{on}{suffix}", **({"y": y} if y else {})}
blockstate("station_rail", {"variants": variants})
write(f"{A}/models/item/station_rail.json", {"parent": "minecraft:item/generated", "textures": {"layer0": "fission:block/station_rail"}})
item_def("station_rail", "fission:item/station_rail")

# gantry crane cab (the crane itself is drawn by its block entity renderer); front faces the span
model("gantry_crane", {"parent": "minecraft:block/orientable", "textures": {"front": "fission:block/gantry_crane_front",
                                                                          "side": "fission:block/gantry_crane_side", "top": "fission:block/gantry_crane_top"}})
blockstate("gantry_crane", {"variants": {f"facing={f}": {"model": "fission:block/gantry_crane", **({"y": r} if r else {})}
                                         for f, r in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
item_def("gantry_crane", "fission:block/gantry_crane")

model("final_repository", {"parent": "minecraft:block/cube_bottom_top", "textures": {"top": "fission:block/final_repository_top",
                                                                                     "side": "fission:block/final_repository_side",
                                                                                     "bottom": "fission:block/final_repository_side"}})
blockstate("final_repository", {"variants": {f"facing={f}": {"model": "fission:block/final_repository"} for f in ("north", "east", "south", "west")}})
item_def("final_repository", "fission:block/final_repository")

for n in ("castor_wagon", "shunting_locomotive"):
    write(f"{A}/models/item/{n}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"fission:item/{n}"}})
    item_def(n, f"fission:item/{n}")

# ================================================================ loot, tags, recipes
for n in ("station_rail", "gantry_crane", "final_repository"):
    write(f"{D}/loot_table/blocks/{n}.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "conditions": [{"condition": "minecraft:survives_explosion"}],
        "entries": [{"type": "minecraft:item", "name": f"fission:{n}"}]}], "random_sequence": f"fission:blocks/{n}"})
merge_list(f"{MC}/tags/block/mineable/pickaxe.json", ["fission:station_rail", "fission:gantry_crane", "fission:final_repository"])
merge_list(f"{MC}/tags/block/needs_iron_tool.json", ["fission:final_repository"])
merge_list(f"{MC}/tags/block/rails.json", ["fission:station_rail"])
merge_list(f"{MC}/tags/item/rails.json", ["fission:station_rail"])
merge_list(f"{RES}/data/radiation/tags/block/shielding_heavy.json", ["fission:final_repository"])


def shaped(name, pattern, key, count=1, category="redstone"):
    write(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern,
                                      "result": {"id": f"fission:{name}", **({"count": count} if count > 1 else {})}})


I = "minecraft:iron_ingot"
shaped("castor_wagon", ["BHB", "HDH", "BMB"], {"B": "minecraft:iron_block", "H": "radiation:heavy_concrete", "D": "fission:storage_drum",
                                               "M": "minecraft:minecart"}, 1, "misc")
shaped("shunting_locomotive", ["IGI", "PBP", " M "], {"I": I, "G": "minecraft:glass_pane", "P": "minecraft:piston", "B": "minecraft:iron_block",
                                                    "M": "minecraft:furnace_minecart"}, 1, "misc")
shaped("station_rail", ["I I", "IYI", "IRI"], {"I": I, "Y": "minecraft:yellow_dye", "R": "minecraft:redstone"}, 6, "misc")
shaped("gantry_crane", ["IYI", "CGC", "IKI"], {"I": "minecraft:iron_block", "Y": "minecraft:yellow_dye", "C": "minecraft:iron_chain",
                                              "G": "minecraft:glass_pane", "K": "minecraft:comparator"})
shaped("final_repository", ["BHB", "HCH", "BHB"], {"B": "minecraft:clay", "H": "radiation:heavy_concrete", "C": "minecraft:copper_block"})

# ================================================================ language
LANG = {
    "entity.fission.castor_wagon": "CASTOR Wagon",
    "entity.fission.shunting_locomotive": "Shunting Locomotive",
    "entity.fission.shunting_locomotive.started": "Shunter running %2$s with %1$s wagon(s) coupled",
    "entity.fission.shunting_locomotive.stopped": "Shunter stopped",
    "entity.fission.shunting_locomotive.no_rail": "The shunter is not on a rail",
    "direction.fission.north": "north", "direction.fission.south": "south", "direction.fission.east": "east", "direction.fission.west": "west",
    "direction.fission.up": "up", "direction.fission.down": "down",
    "item.fission.castor_wagon": "CASTOR Wagon",
    "item.fission.castor_wagon.desc": "Shielded cask on a flatcar for waste going to permanent storage. 18 places. Place it on rails.",
    "item.fission.shunting_locomotive": "Shunting Locomotive",
    "item.fission.shunting_locomotive.desc": "Use to start (drives away from you; sneak: towards you), use again to stop. Couples the wagons against it.",
    "block.fission.station_rail": "Station Rail",
    "block.fission.station_rail.desc": "A shunter stops here until the crane is done, the rail is powered or the waiting time is up.",
    "block.fission.station_rail.wait": "Waiting time without a crane: %s s",
    "block.fission.gantry_crane": "Gantry Crane",
    "block.fission.gantry_crane.desc": "230 V. Spans 9 blocks ahead and 7 to the right. Moves waste between CASTORs and storage.",
    "block.fission.gantry_crane.mode": "Gantry crane: %s",
    "block.fission.gantry_crane.mode.off": "OFF",
    "block.fission.gantry_crane.mode.load": "LOAD (storage into the CASTOR)",
    "block.fission.gantry_crane.mode.unload": "UNLOAD (CASTOR into storage)",
    "block.fission.gantry_crane.status": "Gantry crane: %s, power %s, %s CASTOR(s) under it, on the hook: %s, %s moved",
    "block.fission.final_repository": "Final Repository Cell",
    "block.fission.final_repository.desc": "Copper canisters in bentonite, deep in rock: for long-lived waste and cooled spent fuel, for good.",
}
path = f"{A}/lang/en_us.json"
with open(path) as f:
    lang = json.load(f)
lang.update(LANG)
write(path, lang)
print("castor assets written")
