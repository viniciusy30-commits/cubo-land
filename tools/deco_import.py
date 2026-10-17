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
LIGHTFOLDERS = set()
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
 (4, 'COMIDAS E BEBIDAS', r'food|drink|cake|cup|bottle|tumbler|lunch|popcorn|retail_item|retail_stuff|gallon|pack'),
 (4, 'PLACAS E SINALIZAÇÃO', r'sign|board|billboard|traffic|warning|menu|panel|cinema_poster|street'),
 (4, 'PORTAS E JANELAS', r'door|window|gate|garage|rolling|slide|prison|partition|railing'),
 (4, 'CERCAS E EXTERIOR', r'fence|barrier|guard|bollard|pole|pipe|hydrant|mailbox|bike|catenary|train|garden|lawn|swing|manhole|path|parking|ladder|stairs|escalator|elevator|landing|exhaust|expansion|net$'),
 (4, 'ROUPAS E ACESSÓRIOS', r'clothes|shirt|shoes|boots|slippers|sneakers|bag|hat|jewel|mannequin|totebag|vest|helmet|umbrella|hanger'),
 (4, 'ESPORTE E MÚSICA', r'sport|gym|guitar|piano|drum|mic|speaker|flute|violin|melodica|angklung|cajon|kentungan|dj|music|racket|skate'),
 (4, 'CAIXAS E LOJA', r'box|crate|barrel|sack|basket|shopping|cart|trunk|trolley|pharmacy|retail|cashier|atm|payment|scanner|checkout|money|gold|display'),
 (4, 'ELETRÔNICOS E TECNOLOGIA', r'laptop|pc_|electronic|gaming|projector|camera|cctv|^tv|printer|smartphone|phone|electric|antena|bluray|drone|headphone|earphone|joystick|switch|router|gpu|teleprompter|monolink|mono_play|sensor|shaver|pump_it'),
 (4, 'HOSPITAL E OFICINA', r'hospital|syringe|oxygen|nitrogen|scissor|mechanic|gas_|gasoline|lpg|pump|fire_ext|tire|hand_truck|water_storage|watering|hoe|axe|pruning|dipper|steel|wheel|guillotine|gavel'),
 (4, 'ESTRUTURAS E TELHADOS', r'brick|curve|roof|awning|pillar|handrail|iron_stage|travelator|speed_bump|pulpit|podium|wine_wall|rail'),
 (4, 'SUPORTES E EXPOSITORES', r'stand|hanger|book_stand|tripod'),
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
                quads.append((mats.index(mn), int(max(0, min(1, sh))*255), vs, b['name']))
    return mats, quads

def assemble(d, c):
    """Itens de varios blocos (md:segment: cama, armario alto, cortina...): -> [(geometry_id, (dx,dy,dz) em blocos)] ou None.
    Layout (frente padrao, igual aos scripts do addon): left=+x/right=-x (esquerda no indice 0), front=0/mid,back = -z, bottom=0/mid/top = +y."""
    if 'md:segment' not in d['description'].get('states', {}): return None
    key = next((k for k in c if re.match(r'md:\d', k)), '')
    segs = {}
    for pm in d.get('permutations', []):
        m = re.search(r"md:segment'\) == '(\w+)'", pm['condition'])
        g = pm['components'].get('minecraft:geometry')
        if m and g: segs[m.group(1)] = g['identifier'] if isinstance(g, dict) else g
    if len(segs) < 2: return None
    def toks(n):
        if n == 'center': return ['mid', 'mid']
        return n.split('_')
    zfirst = 'zh' in key
    ax = {'x': set(), 'y': set(), 'z': set()}
    parsed = {}
    for n in segs:
        t = toks(n); r = {}
        if len(t) == 1:
            if 'h' in key and 'xh' in key and not key.startswith('md:2y') and not key.startswith('md:3y'): r['x'] = t[0]
            elif 'zh' in key and 'xh' not in key: r['z'] = t[0]
            elif t[0] in ('left', 'right'): r['x'] = t[0]
            else: r['y'] = t[0]
        else:
            if zfirst: r['z'], r['x'] = t[0], t[1]
            else: r['y'], r['x'] = t[0], t[1]
        parsed[n] = r
        for a, v in r.items(): ax[a].add(v)
    def idx(a, v):
        order = {'x': ['left', 'mid', 'right'], 'y': ['bottom', 'mid', 'top'], 'z': ['front', 'mid', 'back']}[a]
        present = [o for o in order if o in ax[a]]
        return present.index(v) if v in present else 0
    out = []
    for n, gid in segs.items():
        r = parsed[n]
        out.append((gid, (-idx('x', r['x']) if 'x' in r else 0, idx('y', r['y']) if 'y' in r else 0, -idx('z', r['z']) if 'z' in r else 0)))
    return out

SCREEN_RX = re.compile(r'^(tv|tv_vertical|laptop|pc_setup|pc_potato|teleprompter|touchscreen_menu|tablet_display|smartphone_display)(_\d+[a-z]?)*$')
def screen_of(mats, quads):
    """tela -> [cx,cy,cz, nx,nz, rx,rz, largura, altura] no quadro padrao do modelo, ou None.
    1) maior quad do material 'light'; 2) (sem 'light') o maior quad vertical virado pra +z, dando preferencia pro mais alto"""
    allv = [v for _, _, vs, _ in quads for v in vs]
    if not allv: return None
    cen = [sum(v[i] for v in allv)/len(allv) for i in range(3)]
    def cand(only_light):
        out = []
        li = mats.index('light') if 'light' in mats else -1
        for slot, sh, vs, bn in quads:
            if only_light and slot != li: continue
            ax = [vs[1][i]-vs[0][i] for i in range(3)]; bx = [vs[3][i]-vs[0][i] for i in range(3)]
            n = [ax[1]*bx[2]-ax[2]*bx[1], ax[2]*bx[0]-ax[0]*bx[2], ax[0]*bx[1]-ax[1]*bx[0]]
            area = math.sqrt(sum(t*t for t in n))
            if area < 1e-6: continue
            n = [t/area for t in n]
            c = [sum(v[i] for v in vs)/4 for i in range(3)]
            if sum(n[i]*(c[i]-cen[i]) for i in range(3)) < 0: n = [-t for t in n]
            if math.hypot(n[0], n[2]) < 0.3: continue
            out.append((area, n, c, vs))
        return out
    best = None
    c1 = cand(True)
    if c1: best = max(c1, key=lambda t: t[0])
    else:
        c2 = [t for t in cand(False) if t[0] >= 0.02]
        if c2: best = max(c2, key=lambda t: t[0] * (1 + t[2][1]) * (1.5 if t[1][2] > 0.5 else 1))
    if not best or best[0] < 0.02: return None
    _, n, c, vs = best
    hl = math.hypot(n[0], n[2]); nx, nz = n[0]/hl, n[2]/hl; rx, rz = nz, -nx
    pr = [(v[0]-c[0])*rx + (v[2]-c[2])*rz for v in vs]; ys = [v[1] for v in vs]
    w = max(pr)-min(pr); h = max(ys)-min(ys)
    if w < 0.1 or h < 0.1: return None
    return [round(c[0], 3), round(c[1], 3), round(c[2], 3), round(nx, 3), round(nz, 3), round(rx, 3), round(rz, 3), round(w, 3), round(h, 3)]

def ev_vis(expr, var, val):
    """avalia expressao de bone_visibility do addon com var (ex. md:on) = val; qualquer coisa estranha -> visivel"""
    if isinstance(expr, bool): return expr
    try:
        e = str(expr).replace("q.block_state('" + var + "')", 'ST_' + str(bool(val))).replace('&&', ' and ').replace('||', ' or ')
        e = re.sub(r'!(?!=)', ' not ', e).replace('true', 'True').replace('false', 'False')
        e = e.replace('ST_True', 'True').replace('ST_False', 'False')
        if 'q.' in e or 'v.' in e: return True
        return bool(eval(e, {'__builtins__': {}}, {}))
    except Exception:
        return True

def xform(quads, rotY, trans):
    out = []
    for slot, sh, vs, bn in quads:
        nv = []
        for x, y, z, u, v in vs:
            p = rot([x*16-8, y*16, z*16-8], [0, rotY, 0], [0, 0, 0]) if rotY else [x*16-8, y*16, z*16-8]
            nv.append((p[0]/16+0.5+trans[0], p[1]/16+trans[1], p[2]/16+0.5+trans[2], u, v))
        out.append((slot, sh, nv, bn))
    return out

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
    asm = assemble(d, c)
    gkey = gid if not asm else 'ASM:' + '|'.join(f'{a}@{o}' for a, o in sorted(asm))
    if gkey not in geo_index:
        if asm:
            mats = []; quads = []
            for sg, (dx, dy, dz) in asm:
                if sg not in geos: continue
                m2, q2 = bake(geos[sg])
                for mn in m2:
                    if mn not in mats: mats.append(mn)
                for slot, sh, vs, bn in q2:
                    quads.append((mats.index(m2[slot]), sh, [(x+dx, y+dy, z+dz, u, v) for x, y, z, u, v in vs], bn))
        else:
            mats, quads = bake(g)
        geo_index[gkey] = len(geo_list); geo_list.append((mats, quads))
    # estados abrir/ligar (md:is_open / md:on): bone_visibility ou variante aberta (transformacao da porta)
    stvar = next((v for v in ('md:is_open', 'md:on') if v in d['description'].get('states', {})), None)
    kind = 0; openbox = None; vis_sig = ''
    if stvar:
        isl = (stvar == 'md:on' and group(folder, 4) == 'ILUMINAÇÃO')
        kind = 2 if isl else 1
        bv = gref.get('bone_visibility') if isinstance(gref, dict) else None
        vis_sig = f'|{stvar}|{kind}|' + json.dumps(bv, sort_keys=True)
    gkey2 = gkey + vis_sig
    if gkey2 not in geo_index:
        mats, quads = geo_list[geo_index[gkey]]
        nq = []
        if stvar and isinstance(gref, dict) and isinstance(gref.get('bone_visibility'), dict):
            bvm = gref['bone_visibility']
            for slot, sh, vs, bn in quads:
                e = bvm.get(bn, True)
                v0 = ev_vis(e, stvar, kind == 2); v1 = ev_vis(e, stvar, kind != 2)
                nq.append((slot, sh, vs, (1 if v0 else 0) | (2 if v1 else 0)))
        elif stvar == 'md:is_open':
            tr = {'rotation': [0, 90, 0], 'translation': [0.875, 0, 0]}
            for pm in d.get('permutations', []):
                cd = pm['condition']
                if "'south'" in cd and "!q.block_state('md:mirror')" in cd and "md:is_open')" in cd and "!q.block_state('md:is_open')" not in cd and 'minecraft:transformation' in pm['components']:
                    tr = pm['components']['minecraft:transformation']; break
            rr = tr.get('rotation', [0, 0, 0])[1]; tv = tr.get('translation', [0, 0, 0])
            for slot, sh, vs, bn in quads: nq.append((slot, sh, vs, 1))
            for slot, sh, vs, bn in xform(quads, rr, tv): nq.append((slot, sh, vs, 2))
            ob = c.get('minecraft:collision_box')
            if isinstance(ob, dict):
                o_, s_ = ob['origin'], ob['size']
                pts = [[o_[0]+a*s_[0], o_[1]+b2*s_[1], o_[2]+cc*s_[2]] for a in (0, 1) for b2 in (0, 1) for cc in (0, 1)]
                pts = [rot(p_, [0, rr, 0], [0, 0, 0]) for p_ in pts]
                openbox = [round(min(p_[0] for p_ in pts)/16+0.5+tv[0], 3), round(o_[1]/16, 3), round(min(p_[2] for p_ in pts)/16+0.5+tv[2], 3),
                           round(max(p_[0] for p_ in pts)/16+0.5+tv[0], 3), round((o_[1]+s_[1])/16, 3), round(max(p_[2] for p_ in pts)/16+0.5+tv[2], 3)]
        else:
            nq = [(slot, sh, vs, 3) for slot, sh, vs, bn in quads]
        geo_index[gkey2] = len(geo_list); geo_list.append((mats, nq))
    gkey = gkey2
    mats, quads = geo_list[geo_index[gkey]]
    mi = c.get('minecraft:material_instances', {})
    def res(n):
        e = mi.get(n) or mi.get('*') or (next(iter(mi.values())) if mi else None)
        if e is None: return None
        return e['texture'] if isinstance(e, dict) else e
    tl = []
    for mn in mats:
        t = res(mn); tl.append(tex_id(t) if t and texpath(t) else -1)
    if all(t < 0 for t in tl): skipped += 1; continue
    scr = screen_of(mats, quads) if SCREEN_RX.match(folder) else None
    def box(b):
        if not b: return None
        o, s = b['origin'], b['size']
        return [round((o[0]+8)/16, 3), round(o[1]/16, 3), round((o[2]+8)/16, 3), round((o[0]+s[0]+8)/16, 3), round((o[1]+s[1])/16, 3), round((o[2]+s[2]+8)/16, 3)]
    col = c.get('minecraft:collision_box'); col = box(col) if isinstance(col, dict) else (None if col is False else [0,0,0,1,1,1])
    base = names.get(key, key.replace('_drz', '').replace('_', ' ').title())
    nm = ptname(key, base)
    if nm in usednames: nm = f'{nm} ({key.replace("_drz","")})'
    usednames.add(nm)
    fp = None
    if asm:
        xs = [o[0] for _, o in asm]; ys = [o[1] for _, o in asm]; zs = [o[2] for _, o in asm]
        fp = [min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)]
    items.append({'k': key, 'n': nm, 'c': 5 if is_furn(folder) else 4, 'g': geo_index[gkey], 'kd': kind, 't': tl, 'b': col,
                  'l': int(c.get('minecraft:light_emission', 0) or 0), 'f': folder, 'gp': group(folder, 5 if is_furn(folder) else 4), 'sit': 1 if 'md:sit' in c else 0, **({'fp': fp} if fp else {}), **({'ob': openbox} if openbox else {}), **({'sc': scr} if scr else {})})

# ---- escreve ----
with open(OUT + '/geo.bin', 'wb') as fo:
    fo.write(struct.pack('<i', len(geo_list)))
    for mats, quads in geo_list:
        fo.write(struct.pack('<ii', len(mats), len(quads)))
        for slot, sh, vs, vb in quads:
            fo.write(struct.pack('<BBB', slot, sh, vb if isinstance(vb, int) else 3))
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
