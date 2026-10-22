#!/usr/bin/env python3
"""Audita um .mcaddon monoDeco sem modificar o projeto ou o arquivo original.

Uso: python3 tools/deco_audit.py monodeco_gen_7_v7.5.0.mcaddon
Gera um relatorio JSON opcional com --json relatorio.json.
"""
import argparse
import collections
import json
import os
import zipfile

def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("addon", help="arquivo .mcaddon/.zip original")
    ap.add_argument("--json", dest="json_out", help="salva o relatorio completo em JSON")
    args = ap.parse_args()
    with zipfile.ZipFile(args.addon) as z:
        names = set(z.namelist())
        # ZIPs nem sempre incluem entradas de diretorio terminadas em "/".
        # Descobrir os packs pelos componentes de caminho evita falso erro em .mcaddon validos.
        roots = {n.split("/", 1)[0] for n in names}
        bp_roots = sorted(root for root in roots if root.endswith("_BP"))
        rp_roots = sorted(root for root in roots if root.endswith("_RP"))
        if not bp_roots or not rp_roots:
            raise SystemExit("Erro: nao encontrei as pastas _BP e _RP no pacote.")
        bp, rp = bp_roots[0] + "/", rp_roots[0] + "/"
        def read_json(path):
            try:
                return json.loads(z.read(path).decode("utf-8-sig"))
            except Exception:
                return None
        geo_ids = set()
        geo_files = 0
        for p in names:
            if p.startswith(rp + "models/blocks/") and p.endswith(".geo.json"):
                d = read_json(p)
                if d is None:
                    continue
                geo_files += 1
                for g in d.get("minecraft:geometry", []):
                    ident = g.get("description", {}).get("identifier")
                    if ident:
                        geo_ids.add(ident)
        tt = read_json(rp + "textures/terrain_texture.json") or {}
        tex_data = tt.get("texture_data", {})
        def resolve_texture(short):
            entry = tex_data.get(short)
            if entry:
                paths = entry.get("textures", [])
                if not isinstance(paths, list):
                    paths = [paths]
                for t in paths:
                    if isinstance(t, dict):
                        t = t.get("path", "")
                    if t and rp + str(t) + ".png" in names:
                        return True
            return any(rp + base + short + ".png" in names for base in
                       ("textures/blocks/", "textures/items/", "textures/models/"))
        # Geometrias incorporadas ao Bedrock, sem arquivo .geo.json no addon.
        builtin_geometries = {
            "minecraft:geometry.full_block",
            "minecraft:geometry.cross",
            "minecraft:geometry.torch",
            "minecraft:geometry.cube",
        }
        blocks = 0
        missing_primary_geometry = []
        missing_permutation_geometries = []
        builtin_geometry_refs = collections.Counter()
        missing_texture_refs = collections.Counter()
        state_counts = collections.Counter()
        folder_counts = collections.Counter()
        permutation_blocks = 0
        dynamic_blocks = 0
        malformed = []
        for p in names:
            if not (p.startswith(bp + "blocks/") and p.endswith(".json")):
                continue
            d0 = read_json(p)
            if d0 is None:
                malformed.append(p)
                continue
            d = d0.get("minecraft:block", {})
            if not d:
                malformed.append(p)
                continue
            blocks += 1
            folder = p[len(bp + "blocks/"):].split("/")[0]
            folder_counts[folder] += 1
            desc = d.get("description", {})
            comps = d.get("components", {})
            if d.get("permutations"):
                permutation_blocks += 1
            if desc.get("states"):
                dynamic_blocks += 1
                state_counts.update(desc["states"].keys())
            g = comps.get("minecraft:geometry")
            gid = g if isinstance(g, str) else (g or {}).get("identifier")
            if gid in builtin_geometries:
                builtin_geometry_refs[gid] += 1
            elif gid and gid not in geo_ids:
                missing_primary_geometry.append({"block": p, "geometry": gid})
            for pi, perm in enumerate(d.get("permutations", [])):
                pg = perm.get("components", {}).get("minecraft:geometry")
                pgid = pg if isinstance(pg, str) else (pg or {}).get("identifier")
                if not pgid:
                    continue
                if pgid in builtin_geometries:
                    builtin_geometry_refs[pgid] += 1
                elif pgid not in geo_ids:
                    missing_permutation_geometries.append({
                        "block": p, "permutation": pi, "geometry": pgid
                    })
            mats = comps.get("minecraft:material_instances", {})
            for mat in mats.values() if isinstance(mats, dict) else []:
                if isinstance(mat, dict):
                    short = mat.get("texture")
                else:
                    short = mat
                if short and not resolve_texture(short):
                    missing_texture_refs[short] += 1
        report = {
            "archive": os.path.basename(args.addon),
            "resource_pack": rp.rstrip("/"),
            "behavior_pack": bp.rstrip("/"),
            "archive_entries": len(names),
            "block_definitions": blocks,
            "geometry_files": geo_files,
            "geometry_identifiers": len(geo_ids),
            "blocks_with_permutations": permutation_blocks,
            "blocks_with_states": dynamic_blocks,
            "texture_data_entries": len(tex_data),
            "png_files": sum(1 for p in names if p.startswith(rp) and p.lower().endswith(".png")),
            "builtin_geometry_references": dict(builtin_geometry_refs),
            "missing_primary_geometry_count": len(missing_primary_geometry),
            "missing_primary_geometry": missing_primary_geometry,
            "missing_permutation_geometry_count": len(missing_permutation_geometries),
            "missing_permutation_geometries": missing_permutation_geometries,
            "missing_texture_reference_count": sum(missing_texture_refs.values()),
            "missing_texture_references": dict(missing_texture_refs),
            "malformed_block_json_count": len(malformed),
            "malformed_block_json": malformed,
            "most_common_folders": folder_counts.most_common(40),
            "most_common_states": state_counts.most_common(30),
        }
        print("Auditoria monoDeco")
        for k in ("archive_entries", "block_definitions", "geometry_files",
                  "geometry_identifiers", "blocks_with_permutations", "blocks_with_states",
                  "texture_data_entries", "png_files", "missing_primary_geometry_count",
                  "missing_permutation_geometry_count", "missing_texture_reference_count",
                  "malformed_block_json_count"):
            print(f"{k}: {report[k]}")
        if report["missing_texture_references"]:
            print("Texturas ausentes (referencia: ocorrencias):")
            for k, v in sorted(report["missing_texture_references"].items()):
                print(f"  {k}: {v}")
        if args.json_out:
            with open(args.json_out, "w", encoding="utf-8") as f:
                json.dump(report, f, ensure_ascii=False, indent=2)
            print("Relatorio JSON salvo em:", args.json_out)

if __name__ == "__main__":
    main()
