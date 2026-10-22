#!/usr/bin/env python3
"""Valida consistencia del catalogo de decoracao antes de compilar o CuboLand.

Uso: python3 tools/deco_validate.py [--root .]
Confere JSON, IDs estaveis, indices de geometria/textura e estrutura do geo.bin.
Nao altera arquivos.
"""
import argparse
import json
import os
import struct
import sys

def fail(msg):
    print("ERRO:", msg)
    return False

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--root", default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    a = ap.parse_args()
    root = os.path.abspath(a.root)
    deco = os.path.join(root, "app/src/main/resources/deco")
    regpath = os.path.join(root, "tools/deco/registry.json")
    errors = []
    def read_json(path):
        try:
            with open(path, encoding="utf-8") as f:
                return json.load(f)
        except Exception as e:
            errors.append(f"{os.path.relpath(path, root)}: JSON invalido/ausente ({e})")
            return None
    items = read_json(os.path.join(deco, "items.json"))
    reg = read_json(regpath)
    big = read_json(os.path.join(deco, "big.json"))
    q32 = read_json(os.path.join(deco, "q32.json"))
    if items is None or reg is None or big is None or q32 is None:
        for e in errors: print("ERRO:", e)
        return 1
    if not isinstance(items, list) or not isinstance(reg.get("items"), list) or not isinstance(reg.get("tex"), list):
        print("ERRO: estrutura inesperada em items.json ou registry.json")
        return 1
    keys = [x.get("k") for x in items if isinstance(x, dict)]
    if len(keys) != len(items): errors.append("items.json contem entradas que nao sao objetos")
    if len(keys) != len(set(keys)): errors.append("items.json contem IDs de item duplicados")
    if len(reg["items"]) != len(set(reg["items"])): errors.append("registry.json contem IDs de item duplicados")
    if len(reg["tex"]) != len(set(reg["tex"])): errors.append("registry.json contem IDs de textura duplicados")
    known = set(reg["items"])
    absent = sorted(set(keys) - known)
    if absent: errors.append(f"{len(absent)} item(ns) do catalogo nao constam no registry")
    tex_names = reg["tex"]
    tex_dir = os.path.join(deco, "tex")
    tex_missing = []
    for i in range(len(tex_names)):
        if not os.path.isfile(os.path.join(tex_dir, f"{i}.png")):
            tex_missing.append(i)
    if tex_missing: errors.append(f"{len(tex_missing)} textura(s) base ausentes; primeiros IDs: {tex_missing[:20]}")
    try:
        with open(os.path.join(deco, "geo.bin"), "rb") as f:
            raw = f.read()
        if len(raw) < 8: raise ValueError("arquivo menor que o cabecalho")
        version, count = struct.unpack_from("<ii", raw, 0)
        if version != -2: raise ValueError(f"versao de geometria {version}, esperada -2")
        if count < 0 or count > 100000: raise ValueError(f"quantidade de geometrias invalida: {count}")
        pos = 8
        for gi in range(count):
            if pos + 9 > len(raw): raise ValueError(f"geometria {gi}: cabecalho truncado")
            mats, quads, dm = struct.unpack_from("<iiB", raw, pos); pos += 9
            if mats < 0 or quads < 0: raise ValueError(f"geometria {gi}: contagens negativas")
            if dm & ~31: raise ValueError(f"geometria {gi}: mascara dinamica invalida {dm}")
            mask_len = 1
            for bit, factor in enumerate((4, 16, 4, 2, 2)):
                if dm & (1 << bit): mask_len *= factor
            mask_len = (mask_len + 7) // 8 if dm else 0
            rec_size = 3 + 40 + mask_len
            end = pos + quads * rec_size
            if end > len(raw): raise ValueError(f"geometria {gi}: dados de faces truncados")
            pos = end
        if pos != len(raw): errors.append(f"geo.bin contem {len(raw)-pos} bytes extras apos a ultima geometria")
    except Exception as e:
        count = 0
        errors.append(f"geo.bin invalido: {e}")
    else:
        for i, item in enumerate(items):
            g = item.get("g")
            if not isinstance(g, int) or g < 0 or g >= count:
                errors.append(f"item {item.get('k', i)} aponta para geometria invalida: {g}")
            ts = item.get("t", [])
            if not isinstance(ts, list):
                errors.append(f"item {item.get('k', i)} tem lista de texturas invalida")
                continue
            for t in ts:
                if not isinstance(t, int) or t < -1 or t >= len(tex_names):
                    errors.append(f"item {item.get('k', i)} aponta para textura invalida: {t}")
                    break
    for label, values in (("big.json", big), ("q32.json", q32)):
        if not isinstance(values, list): errors.append(f"{label} deve ser uma lista")
        elif any(not isinstance(i, int) or i < 0 or i >= len(tex_names) for i in values):
            errors.append(f"{label} contem IDs de textura fora do registry")
    print(f"Itens no catalogo: {len(items)}")
    print(f"IDs estaveis no registry: {len(reg['items'])}")
    print(f"Texturas registradas: {len(tex_names)}")
    print(f"Geometrias no geo.bin: {count}")
    print(f"Texturas base presentes: {len(tex_names)-len(tex_missing) if 'tex_missing' in locals() else 'nao verificadas'}")
    if errors:
        print(f"VALIDACAO FALHOU: {len(errors)} problema(s)")
        for e in errors[:100]: print(" -", e)
        if len(errors) > 100: print(f" - ... e mais {len(errors)-100}")
        return 1
    print("VALIDACAO OK: indices, IDs, texturas e estrutura binaria consistentes.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
