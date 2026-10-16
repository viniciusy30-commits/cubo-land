#!/usr/bin/env python3
"""Importa mobilia/decoracao do addon monoDeco (.mcaddon extraido) pro jogo.
Uso: python3 tools/deco_import.py <pasta_addon_extraida> --batches 1,2   (da raiz do projeto)
- Os lotes (BATCHES) sao cumulativos: rode SEMPRE com todos os lotes ja feitos (ver tools/deco/registry.json).
- registry.json so cresce no fim -> ids dos blocos nunca mudam (os mundos salvam ids).
Gera: app/src/main/resources/deco/{items.json,geo.bin,tex/N.png}
"""
import os, sys, re, json, glob, math, struct, argparse
from PIL import Image

ap = argparse.ArgumentParser(); ap.add_argument('addon'); ap.add_argument('--batches', default='1')
ap.add_argument('--root', default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
A = ap.parse_args()
BP = glob.glob(A.addon + '/*_BP')[0]; RP = glob.glob(A.addon + '/*_RP')[0]
OUT = A.root + '/app/src/main/resources/deco'; REG = A.root + '/tools/deco/registry.json'
os.makedirs(OUT + '/tex', exist_ok=True)

# ---- familias de construcao que NAO sao mobilia/decoracao (ficam pra depois, se o usuario quiser) ----
SKIP = re.compile(r'(_stairs|_curve_corner|_circle_shape|_half_block(_corner(_right)?)?|_vertical_slabs|_horizontal_slabs|_ventilation_block_drz|_fence_wall|_diagonal_block_drz)$')
FAM = re.compile(r'^(asphalt|wall|tiles|sidewalk|woods|metals|marble|container|escalator_block|embedded_track|monodeus_script|paper_wall|ballast|alphabet|number|symbol)')
# ---- lotes (regex no nome da pasta). Lote 4 = todo o resto ----
BATCHES = {
 1: r'^(chair|sofa|stool|bench(?!_press)|table|bed|beds|bunk_bed|desk|night_stand|bookshelf|cabinet|drawer|wardrobe|ottoman|rocking|cinema_chair|piano_chair|cribs|countertop|shelf|rack|locker|filing|coffee_table|floating_table|tv_table|stand_tv|sofa_table|cup_board|furniture|pet_house|cat_house)',
 2: r'^(refrigerator|freezer|oven|stove|microwave|air_fryer|blender|toaster|rice_cooker|kitchen|sink|faucet|bathtub|bathroom|toilet|shower|washing|water_heater|cooker_hood|dish_rack|espresson|deep_fryer|grill|frying|teapot|plate|cups|dispenser|beverage|ice_cream|popcorn|vending|hairdryer|vacuum|robot_vacuum|ironing|iron_|basket_laundry|trash|bucket|mixer|dipper|rolling_pin|cleaning|detergent|glass_clean|glass_wiper)',
 3: r'^(lamp|hanging_light|lights_kit|light_beam|candle|plant|pot_plant|pineapple|picture|photo|canvas|modern_art|mirror|convex|clock|carpet|doormat|curtain|blind_curtain|banner|flag|globe|calendar|wreath|pillows|blanket|doll|plushi|toys|toy_box|decoration|figure|tree_display|aquarium|fireplace|radiator|fan_|bladeless|ac|ac_|jukebox|radio|vinyl|telephone|typewriter|book|paper|chess|uno|dart|gift_box|ballon|backdrop|poster|cinema_poster)',
 4: None,
}
want = [int(x) for x in A.batches.split(',')]
FURN_OLD = re.compile(r'(chair|sofa|stool|bench|table|bed|desk|stand|shelf|cabinet|drawer|wardrobe|ottoman|rack|locker|crib|counter|refrigerator|freezer|oven|stove|sink|toilet|bathtub|washing|dresser|cup_board|filing|furniture)')

def is_furn(f): return group_raw(f, 5) != 'OUTROS MOVEIS' or bool(FURN_OLD.search(f))
def folder_ok(f): return not SKIP.search(f) and not FAM.match(f)
def batch_of(f):
    for b in (1, 2, 3):
        if re.match(BATCHES[b], f): return b
    return 4

# ---- nomes (en_US.lang) + traducao simples EN->PT ----
names = {}
for ln in open(RP + '/texts/en_US.lang', encoding='utf8', errors='ignore'):
    m = re.match(r'tile\.drz:(\w+)\.name=(.*?)( - .*)?(§.*)?$', ln.strip())
    if m: names[m.group(1)] = m.group(2).strip()
PT = {'chair':'Cadeira','sofa':'Sofá','stool':'Banco','bench':'Banco','table':'Mesa','bed':'Cama','desk':'Escrivaninha','night':'Criado','stand':'Suporte','shelf':'Estante','cabinet':'Armário','drawer':'Gaveteiro','wardrobe':'Guarda-roupa','wood':'Madeira','white':'Branco','black':'Preto','gray':'Cinza','brown':'Marrom','red':'Vermelho','blue':'Azul','green':'Verde','yellow':'Amarelo','pink':'Rosa','dark':'Escuro','light':'Claro','lamp':'Luminária','plant':'Planta','pot':'Vaso','picture':'Quadro','mirror':'Espelho','clock':'Relógio','carpet':'Tapete','curtain':'Cortina','refrigerator':'Geladeira','oven':'Forno','stove':'Fogão','sink':'Pia','toilet':'Vaso Sanitário','bathtub':'Banheira','washing':'Lavadora','machine':'Máquina','kitchen':'Cozinha','rack':'Prateleira','locker':'Armário-vestiário','counter':'Balcão','countertop':'Bancada','marble':'Mármore','metal':'Metal','glass':'Vidro','door':'Porta','window':'Janela','fence':'Cerca','box':'Caixa','bag':'Bolsa','book':'Livro','candle':'Vela','flag':'Bandeira','figure':'Figura','doll':'Boneca','toy':'Brinquedo','ceiling':'Teto','wall':'Parede','ground':'Chão','floating':'Flutuante','coffee':'Café','dining':'Jantar','modern':'Moderno','school':'Escola','gaming':'Gamer','plastic':'Plástico','cafe':'Café','train':'Trem','stadium':'Estádio','cinema':'Cinema','piano':'Piano','rocking':'Balanço','bunk':'Beliche','crib':'Berço','cribs':'Berço','ottoman':'Puff'}
GROUPS = [  # (titulo, regex na pasta) - a primeira que casar vence; separado por categoria (5 = mobilia, 4 = decoracao)
 (5, 'ASSENTOS', r'chair|sofa|stool|bench|ottoman|rocking|cinema_chair'),
 (5, 'MESAS E BANCADAS', r'table|desk|countertop|floating|cashier'),
 (5, 'CAMAS E BERCOS', r'bed|crib'),
 (5, 'ARMARIOS E ESTANTES', r'cabinet|drawer|wardrobe|shelf|rack|locker|filing|cup_board|night_stand|bookshelf|furniture|display'),
 (5, 'COZINHA', r'refrigerator|freezer|oven|stove|microwave|air_fryer|blender|toaster|rice|kitchen|sink|cooker|dish|espresson|fryer|grill|frying|dispenser|vending|mixer|ice_cream'),
 (5, 'BANHEIRO E LIMPEZA', r'toilet|bath|shower|faucet|washing|water_heater|hairdryer|vacuum|ironing|laundry|trash|bucket|cleaning|detergent|glass_clean|glass_wiper'),
 (5, 'OUTROS MOVEIS', r'.'),
 (4, 'ILUMINACAO', r'lamp|light|candle|fireplace'),
 (4, 'PLANTAS', r'plant|pineapple|wreath|leaves|tree'),
 (4, 'QUADROS E ESPELHOS', r'picture|photo|canvas|modern_art|mirror|poster|calendar|frame|backdrop'),
 (4, 'RELOGIOS', r'clock'),
 (4, 'TEXTEIS E TAPETES', r'carpet|doormat|curtain|blanket|pillow|banner|flag'),
 (4, 'BONECOS E BRINQUEDOS', r'doll|plushi|toy|figure|chess|uno|dart|gift|ballon'),
 (4, 'ELETRONICOS E MUSICA', r'fan|ac|ac_|jukebox|radio|vinyl|telephone|typewriter|globe|aquarium|radiator|book|paper'),
 (4, 'OUTRAS DECORACOES', r'.'),
]
def group_raw(folder, cat):
    for c, t, rx in GROUPS:
        if c == cat and re.search(rx, folder): return t
    return ''
def group(folder, cat):
    for c, t, rx in GROUPS:
        if c == cat and re.search(rx, folder): return t.replace('MOVEIS','MÓVEIS').replace('ARMARIOS','ARMÁRIOS').replace('BERCOS','BERÇOS').replace('ILUMINACAO','ILUMINAÇÃO').replace('RELOGIOS','RELÓGIOS').replace('TEXTEIS','TÊXTEIS').replace('ELETRONICOS E MUSICA','ELETRÔNICOS E MÚSICA').replace('DECORACOES','DECORAÇÕES').replace('MESAS E BANCADAS','MESAS E BANCADAS').replace('COZINHA','COZINHA')
    return ''
def ptname(key, en):
    ws = en.split(); out = []
    for w in ws:
        lw = w.lower().rstrip('s') if w.lower() not in PT else w.lower()
        out.append(PT.get(lw, w))
    s = ' '.join(out)
    return s[:1].upper() + s[1:]

# ---- geometrias ----
geos = {}
for p in glob.glob(RP + '/models/blocks/*.geo.json'):
    try: d = json.load(open(p))
    except Exception: continue
    for g in d.get('minecraft:geometry', []):
        geos[g['description']['identifier']] = g
# ---- texturas: shortname -> arquivo ----
tt = json.load(open(RP + '/textures/terrain_texture.json'))['texture_data']
def texpath(short):
    e = tt.get(short)
    if e:
        t = e['textures']; t = t[0] if isinstance(t, list) else t
        if isinstance(t, dict): t = t['path']
        p = RP + '/' + t + '.png'
        if os.path.exists(p): return p
    p = RP + '/textures/blocks/' + short + '.png'
    return p if os.path.exists(p) else None

def rot(v, ang, piv):  # Bedrock: aplica X, depois Y, depois Z (graus)
    x, y, z = v[0]-piv[0], v[1]-piv[1], v[2]-piv[2]
    rx, ry, rz = [math.radians(a) for a in ang]
    y, z = y*math.cos(rx)-z*math.sin(rx), y*math.sin(rx)+z*math.cos(rx)
    x, z = x*math.cos(ry)+z*math.sin(ry), -x*math.sin(ry)+z*math.cos(ry)
    x, y = x*math.cos(rz)-y*math.sin(rz), x*math.sin(rz)+y*math.cos(rz)
    return [x+piv[0], y+piv[1], z+piv[2]]

FACES = {  # TL, TR, BR, BL (x:0/1 min/max, y, z)
 'north': [(1,1,0),(0,1,0),(0,0,0),(1,0,0)], 'south': [(0,1,1),(1,1,1),(1,0,1),(0,0,1)],
 'east': [(1,1,1),(1,1,0),(1,0,0),(1,0,1)], 'west': [(0,1,0),(0,1,1),(0,0,1),(0,0,0)],
 'up': [(0,1,0),(1,1,0),(1,1,1),(0,1,1)], 'down': [(0,0,1),(1,0,1),(1,0,0),(0,0,0)]}

def bake(g):
    """-> (mats[list nomes], quads[(slot, shade, [(x,y,z,u,v)*4])]) em unidades de bloco (0..1 = 1 bloco, x/z centrados em .5)"""
    tw = g['description'].get('texture_width', 16); th = g['description'].get('texture_height', 16)
    bones = {b['name']: b for b in g.get('bones', [])}
    def chain(b):  # bone -> lista de (rot, pivot) do filho pro pai
        out = []; seen = 0
        while b is not None and seen < 20:
            if b.get('rotation') and any(b['rotation']): out.append((b['rotation'], b.get('pivot', [0,0,0])))
            b = bones.get(b.get('parent')); seen += 1
        return out
    mats = []; quads = []
    for b in g.get('bones', []):
        ch = chain(b)
        for c in b.get('cubes', []):
            o = c['origin']; s = c['size']; inf = c.get('inflate', 0)
            x0, y0, z0 = o[0]-inf, o[1]-inf, o[2]-inf; x1, y1, z1 = o[0]+s[0]+inf, o[1]+s[1]+inf, o[2]+s[2]+inf
            cr = c.get('rotation'); cp = c.get('pivot', [0,0,0])
            for fn, fd in c['uv'].items() if isinstance(c.get('uv'), dict) else []:
                if fn not in FACES: continue
                uv = fd.get('uv', [0,0]); us = fd.get('uv_size', [0,0])
                if us[0] == 0 or us[1] == 0: continue
                ur = (fd.get('uv_rotation', 0) // 90) % 4
                cu = [(uv[0], uv[1]), (uv[0]+us[0], uv[1]), (uv[0]+us[0], uv[1]+us[1]), (uv[0], uv[1]+us[1])]
                cu = cu[-ur:] + cu[:-ur] if ur else cu
                vs = []
                for (a, bb, cc), (uu, vv) in zip(FACES[fn], cu):
                    p = [x1 if a else x0, y1 if bb else y0, z1 if cc else z0]
                    if cr and any(cr): p = rot(p, cr, cp)
                    for r_, pv in ch: p = rot(p, r_, pv)
                    vs.append((p[0]/16+0.5, p[1]/16, p[2]/16+0.5, uu/tw, vv/th))
                # normal -> sombreado
                ax = [vs[1][i]-vs[0][i] for i in range(3)]; bx = [vs[3][i]-vs[0][i] for i in range(3)]
                n = [ax[1]*bx[2]-ax[2]*bx[1], ax[2]*bx[0]-ax[0]*bx[2], ax[0]*bx[1]-ax[1]*bx[0]]
                l = math.sqrt(sum(t*t for t in n)) or 1; n = [t/l for t in n]
                sh = abs(n[0])*0.82 + abs(n[2])*0.7 + (n[1]*1.0 if n[1] > 0 else -n[1]*0.55)
                sh = sh / (abs(n[0])+abs(n[1])+abs(n[2])+1e-6)
                mn = fd.get('material_instance', '*')
                if mn not in mats: mats.append(mn)
                quads.append((mats.index(mn), int(max(0, min(1, sh))*255), vs))
    return mats, quads

# ---- registro persistente ----
reg = json.load(open(REG)) if os.path.exists(REG) else {'items': [], 'tex': [], 'batches': []}
folders = sorted(f for f in os.listdir(BP + '/blocks') if folder_ok(f))
cand = []  # (folder, key, path)
for f in folders:
    if batch_of(f) not in want: continue
    for p in sorted(glob.glob(f'{BP}/blocks/{f}/*.json')):
        cand.append((f, os.path.basename(p)[:-5], p))
known = set(reg['items'])
for f, k, p in cand:
    if k not in known: reg['items'].append(k); known.add(k)
reg['batches'] = sorted(set(reg['batches']) | set(want))
pathof = {os.path.basename(p)[:-5]: (f, p) for f in folders for p in glob.glob(f'{BP}/blocks/{f}/*.json')}

geo_index = {}; geo_list = []; items = []; texidx = {n: i for i, n in enumerate(reg['tex'])}
def tex_id(short):
    if short not in texidx:
        texidx[short] = len(reg['tex']); reg['tex'].append(short)
    return texidx[short]
usednames = set(); skipped = 0
for key in reg['items']:
    if key not in pathof: skipped += 1; continue
    folder, p = pathof[key]
    d = json.load(open(p))['minecraft:block']; c = d['components']
    gref = c.get('minecraft:geometry'); gid = gref if isinstance(gref, str) else (gref or {}).get('identifier')
    g = geos.get(gid)
    if not g: skipped += 1; continue
    if gid not in geo_index:
        mats, quads = bake(g)
        geo_index[gid] = len(geo_list); geo_list.append((mats, quads))
    mats, quads = geo_list[geo_index[gid]]
    mi = c.get('minecraft:material_instances', {})
    def res(n):
        e = mi.get(n) or mi.get('*') or (next(iter(mi.values())) if mi else None)
        if e is None: return None
        return e['texture'] if isinstance(e, dict) else e
    tl = []
    for mn in mats:
        t = res(mn); tl.append(tex_id(t) if t and texpath(t) else -1)
    if all(t < 0 for t in tl): skipped += 1; continue
    def box(b):
        if not b: return None
        o, s = b['origin'], b['size']
        return [round((o[0]+8)/16, 3), round(o[1]/16, 3), round((o[2]+8)/16, 3), round((o[0]+s[0]+8)/16, 3), round((o[1]+s[1])/16, 3), round((o[2]+s[2]+8)/16, 3)]
    col = c.get('minecraft:collision_box'); col = box(col) if isinstance(col, dict) else (None if col is False else [0,0,0,1,1,1])
    base = names.get(key, key.replace('_drz', '').replace('_', ' ').title())
    nm = ptname(key, base)
    if nm in usednames: nm = f'{nm} ({key.replace("_drz","")})'
    usednames.add(nm)
    items.append({'k': key, 'n': nm, 'c': 5 if is_furn(folder) else 4, 'g': geo_index[gid], 't': tl, 'b': col,
                  'l': int(c.get('minecraft:light_emission', 0) or 0), 'f': folder, 'gp': group(folder, 5 if is_furn(folder) else 4), 'sit': 1 if 'md:sit' in c else 0})

# ---- escreve ----
with open(OUT + '/geo.bin', 'wb') as fo:
    fo.write(struct.pack('<i', len(geo_list)))
    for mats, quads in geo_list:
        fo.write(struct.pack('<ii', len(mats), len(quads)))
        for slot, sh, vs in quads:
            fo.write(struct.pack('<BB', slot, sh))
            for x, y, z, u, v in vs:
                fo.write(struct.pack('<hhhHH', int(round(x*2048)), int(round(y*2048)), int(round(z*2048)),
                                     int(max(0, min(1, u))*65535), int(max(0, min(1, v))*65535)))
json.dump(items, open(OUT + '/items.json', 'w'), ensure_ascii=False, separators=(',', ':'))
for i, short in enumerate(reg['tex']):
    pth = texpath(short)
    if not pth: continue
    im = Image.open(pth).convert('RGBA')
    im = im.resize((64, 64), Image.NEAREST if im.size[0] <= 64 else Image.LANCZOS)
    im.save(f'{OUT}/tex/{i}.png', optimize=True)
os.makedirs(os.path.dirname(REG), exist_ok=True)
json.dump(reg, open(REG, 'w'), ensure_ascii=False)
print('itens', len(items), 'geos', len(geo_list), 'texturas', len(reg['tex']), 'pulados', skipped)
