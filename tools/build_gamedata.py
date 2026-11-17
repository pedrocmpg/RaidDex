#!/usr/bin/env python3
"""Gera gamedata.json a partir do Game Master (PokeMiners) e dos textos pt-BR (pogo_assets).

Uso: python3 tools/build_gamedata.py [--gm gm.json] [--pt pt.txt] [-o app/src/main/assets/gamedata.json]
Sem --gm/--pt baixa das URLs publicas.
"""
import argparse, datetime, hashlib, json, re, sys, urllib.request
from pathlib import Path

GM_URL = "https://raw.githubusercontent.com/PokeMiners/game_masters/master/latest/latest.json"
PT_URL = "https://raw.githubusercontent.com/PokeMiners/pogo_assets/master/Texts/Latest%20APK/BrazilianPortuguese.txt"
TYPE_ORDER = ["NORMAL", "FIGHTING", "FLYING", "POISON", "GROUND", "ROCK", "BUG", "GHOST", "STEEL",
              "FIRE", "WATER", "GRASS", "ELECTRIC", "PSYCHIC", "ICE", "DRAGON", "DARK", "FAIRY"]
FORM_SUFFIX = {"ALOLA": "Alola", "GALARIAN": "Galar", "HISUIAN": "Hisui", "PALDEA": "Paldea"}


def fetch(url):
    with urllib.request.urlopen(url, timeout=60) as r:
        return r.read().decode("utf-8")


def parse_texts(text):
    out, key = {}, None
    for line in text.splitlines():
        if line.startswith("RESOURCE ID: "):
            key = line[13:].strip()
        elif line.startswith("TEXT:") and key:
            out[key] = line[5:].strip()
            key = None
    return out


def tname(t):
    return t.replace("POKEMON_TYPE_", "")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--gm"); ap.add_argument("--pt")
    ap.add_argument("-o", default="app/src/main/assets/gamedata.json")
    a = ap.parse_args()
    raw_gm = Path(a.gm).read_text() if a.gm else fetch(GM_URL)
    pt = parse_texts(Path(a.pt).read_text() if a.pt else fetch(PT_URL))
    T = {e["templateId"]: e["data"] for e in json.loads(raw_gm)}

    # --- tipos: attackScalar na ordem de TYPE_ORDER (Agua->Fogo = 1.6 verificado abaixo)
    chart = {}
    for t in TYPE_ORDER:
        sc = T[f"POKEMON_TYPE_{t}"]["typeEffective"]["attackScalar"]
        chart[t] = dict(zip(TYPE_ORDER, sc))
    assert chart["WATER"]["FIRE"] == 1.6 and chart["FIRE"]["WATER"] == 0.625, "ordem de tipos inesperada"

    bs = T["BATTLE_SETTINGS"]["battleSettings"]
    friendship = {}
    for k, v in T.items():
        m = re.fullmatch(r"FRIENDSHIP_LEVEL_(\d+)", k)
        if m:
            friendship[m.group(1)] = v["friendshipMilestoneSettings"]["attackBonusPercentage"]
    multipliers = {
        "stab": bs["sameTypeAttackBonusMultiplier"],
        "weather": T["WEATHER_BONUS_SETTINGS"]["weatherBonusSettings"]["attackBonusMultiplier"],
        "shadowAttack": bs["shadowPokemonAttackBonusMultiplier"],
        "shadowDefense": bs["shadowPokemonDefenseBonusMultiplier"],
        "friendship": friendship,
    }
    weather = {}
    for k, v in T.items():
        if k.startswith("WEATHER_AFFINITY_"):
            w = v["weatherAffinities"]
            weather[w["weatherCondition"]] = [tname(x) for x in w["pokemonType"]]

    cpm = T["PLAYER_LEVEL_SETTINGS"]["playerLevel"]["cpMultiplier"]  # niveis inteiros 1..80

    # --- ataques PvE (V####_MOVE_*), nunca os COMBAT_* (PvP)
    moves = {}
    alias = {}  # id numerico usado em algumas listas de especies -> id do golpe
    for k, v in T.items():
        m = re.fullmatch(r"V(\d{4})_MOVE_(.+)", k)
        if not m or "moveSettings" not in v:
            continue
        s = v["moveSettings"]
        e = s.get("energyDelta", 0)
        mid = m.group(2)  # movementId as vezes e numerico; o sufixo do template bate com as listas das especies
        if not isinstance(s["movementId"], str):
            alias[s["movementId"]] = mid
        moves[mid] = {
            "id": mid,
            "name": pt.get(f"move_name_{m.group(1)}", mid.replace("_", " ").title()),
            "type": tname(s["pokemonType"]),
            "power": s.get("power", 0.0),
            "energy": abs(e),
            "durationMs": s["durationMs"],
            "fast": e > 0,
        }

    def mv(ids):
        return [alias.get(i, i) for i in ids]

    # --- especies (um registro por forma realmente distinta)
    raw, seen = [], set()
    for k, v in sorted(T.items()):
        m = re.fullmatch(r"V(\d{4})_POKEMON_(.+)", k)
        if not m or "pokemonSettings" not in v:
            continue
        s = v["pokemonSettings"]
        if "stats" not in s or "quickMoves" not in s or "cinematicMoves" not in s:
            continue
        dex, suffix = int(m.group(1)), m.group(2)
        if suffix.endswith("_NORMAL") and f"V{m.group(1)}_POKEMON_{suffix[:-7]}" in T:
            continue
        if suffix in seen:
            continue
        seen.add(suffix)
        types = [tname(s["type"])] + ([tname(s["type2"])] if "type2" in s else [])
        st = s["stats"]
        entry = {
            "id": suffix, "dex": dex, "name": pt.get(f"pokemon_name_{m.group(1)}", s["pokemonId"].title()),
            "types": types,
            "atk": st["baseAttack"], "def": st["baseDefense"], "sta": st["baseStamina"],
            "fast": mv(s["quickMoves"]), "charged": mv(s["cinematicMoves"]),
            "eliteFast": mv(s.get("eliteQuickMove", [])), "eliteCharged": mv(s.get("eliteCinematicMove", [])),
            "mega": False, "shadowable": "shadow" in s,
        }
        raw.append((entry, s, s["pokemonId"]))

    def sig(e):
        return (tuple(e["types"]), e["atk"], e["def"], e["sta"], tuple(e["fast"]), tuple(e["charged"]),
                tuple(e["eliteFast"]), tuple(e["eliteCharged"]))

    def form_label(base_name, pid, sid):
        suffix = sid[len(pid) + 1:] if sid.startswith(pid + "_") else sid
        low = suffix.lower()
        parts = suffix.split("_")
        if parts[0] in FORM_SUFFIX and len(parts) > 1:
            rest = "_".join(parts[1:]).lower()
            txt = next((pt[k] for k in (f"form_{rest}", f"form_{rest}_mode") if pt.get(k)), rest.replace("_", " ").title())
            return f"{base_name} ({FORM_SUFFIX[parts[0]]} {txt})"
        for key in (f"form_{low}", f"form_{low}_cloak", f"form_{low}_mode", f"form_{low}_sea", f"form_{pid.lower()}_{low}"):
            if pt.get(key):
                return f"{base_name} ({pt[key]})"
        if suffix.split("_")[0] in FORM_SUFFIX:
            return f"{base_name} ({FORM_SUFFIX[suffix.split('_')[0]]})"
        return f"{base_name} ({suffix.replace('_', ' ').title()})"

    species, kept_sigs = [], {}
    for entry, s, pid in raw:
        group = kept_sigs.setdefault((entry["dex"], pid), {})
        sg = sig(entry)
        if sg in group:  # fantasia/evento identica a uma forma ja mantida
            continue
        is_base = entry["id"] == pid or not group
        group[sg] = entry["id"]
        if not is_base:
            entry["name"] = form_label(entry["name"], pid, entry["id"])
        species.append(entry)
        for o in s.get("tempEvoOverrides", []):
            if "tempEvoId" not in o or "stats" not in o:
                continue
            tid = o["tempEvoId"].replace("TEMP_EVOLUTION_", "")
            ot = [tname(o["typeOverride1"])] + ([tname(o["typeOverride2"])] if "typeOverride2" in o else [])
            nice = {"MEGA": "Mega", "MEGA_X": "Mega", "MEGA_Y": "Mega", "PRIMAL": "Primal"}.get(tid, tid.title())
            suf = {"MEGA_X": " X", "MEGA_Y": " Y"}.get(tid, "")
            me = dict(entry)
            me.update({
                "id": f"{entry['id']}_{tid}", "name": f"{nice} {entry['name']}{suf}", "types": ot or entry["types"],
                "atk": o["stats"]["baseAttack"], "def": o["stats"]["baseDefense"], "sta": o["stats"]["baseStamina"],
                "mega": True, "megaOf": entry["id"], "shadowable": False,
            })
            species.append(me)

    names = {}
    for sp in species:
        names.setdefault(sp["name"], []).append(sp["id"])
    clash = {n: ids for n, ids in names.items() if len(ids) > 1}
    if clash:
        sys.exit(f"nomes duplicados entre especies: {clash}")

    for sp in species:
        for mid in sp["fast"] + sp["charged"] + sp["eliteFast"] + sp["eliteCharged"]:
            if mid not in moves:
                print(f"aviso: {sp['id']} usa golpe sem dados PvE: {mid}", file=sys.stderr)

    bosses = json.loads(Path(__file__).with_name("raid_bosses.json").read_text())
    out = {
        "schemaVersion": 1,
        "version": "",
        "sources": {"gameMaster": GM_URL, "texts": PT_URL},
        "types": TYPE_ORDER, "typeChart": chart, "weather": weather,
        "multipliers": multipliers, "cpm": cpm,
        "moves": sorted(moves.values(), key=lambda x: x["id"]),
        "species": species,
        "bosses": bosses["tiers"], "bossParams": bosses["params"],
    }
    # Versao ordenavel (data) + hash do conteudo final: muda se QUALQUER entrada mudar (GM, textos, chefes).
    digest = hashlib.sha256(json.dumps(out, ensure_ascii=False, sort_keys=True).encode()).hexdigest()[:10]
    prev = Path(a.o)
    stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d%H%M")
    if prev.exists():
        try:
            old = json.loads(prev.read_text())["version"]
            if old.endswith("-" + digest):
                stamp = old.split("-")[0]  # conteudo identico: mantem a versao
        except (ValueError, KeyError):
            pass
    version = f"{stamp}-{digest}"
    out["version"] = version
    p = Path(a.o); p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(json.dumps(out, ensure_ascii=False, separators=(",", ":")))
    print(f"{p}: versao {version}, {len(species)} especies, {len(moves)} golpes, {p.stat().st_size//1024} KiB")


if __name__ == "__main__":
    main()
