#!/usr/bin/env python3
"""Importa mobilia/decoracao do addon monoDeco (.mcaddon extraido) pro jogo.
Uso: python3 tools/deco_import.py <pasta_addon_extraida> --batches 1,2   (da raiz do projeto)
- Os lotes (BATCHES) sao cumulativos: rode SEMPRE com todos os lotes ja feitos (ver tools/deco/registry.json).
- registry.json so cresce no fim -> ids dos blocos nunca mudam (os mundos salvam ids).
Gera: app/src/main/resources/deco/{items.json,geo.bin,tex/N.png}
"""
import os, sys, re, json, glob, math, struct, argparse, hashlib
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

ASM_NAME = {}
def assemble(d, c):
    """Itens de varios blocos (md:segment: cama, armario alto, cortina...): -> [(geometry_id, (dx,dy,dz) em blocos)] ou None.
    Layout (frente padrao, igual aos scripts do addon): left=+x/right=-x (esquerda no indice 0), front=0/mid,back = -z, bottom=0/mid/top = +y."""
    if 'md:segment' not in d['description'].get('states', {}): return None
    if 'tag:md:horizontal_connector' in c or 'tag:md:z_horizontal_connector' in c: return None   # segmentos = alternativas por vizinhanca (dinamico), nao pecas de um moveis grande
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
    # CORRIGIDO: esquerda = x menor e frente = z menor (confere com as tampas/cabeceiras dentro de cada segmento e com o
    # modelo de item do addon). A area ocupada (footprint) continua a mesma de antes: so a ordem dos segmentos dentro dela
    # foi espelhada (esq. na ponta -x, direita em 0; frente na ponta -z, fundo em 0).
    def nmx(a): return len([o for o in {'x': ['left', 'mid', 'right'], 'z': ['front', 'mid', 'back']}[a] if o in ax[a]]) - 1
    out = []
    for n, gid in segs.items():
        r = parsed[n]
        off_ = (idx('x', r['x']) - nmx('x') if 'x' in r else 0, idx('y', r['y']) if 'y' in r else 0, idx('z', r['z']) - nmx('z') if 'z' in r else 0)
        out.append((gid, off_)); ASM_NAME[(gid, off_)] = n
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

def state_defaults(d):
    """valores padrao dos estados do bloco (1o valor de cada lista) + tracos de colocacao. Direcao padrao = 'south' (sem rotacao;
    as permutacoes do addon so giram north/east/west e o jogo gira o modelo pelo fState)."""
    st = {'minecraft:cardinal_direction': 'south', 'minecraft:facing_direction': 'down', 'minecraft:vertical_half': 'bottom'}
    for k, v in d['description'].get('states', {}).items():
        vals = v if isinstance(v, list) else (v.get('values') if isinstance(v, dict) else None)
        if isinstance(vals, list) and vals: st[k] = vals[0]
        elif isinstance(v, dict) and 'values' in v and isinstance(v['values'], dict): st[k] = v['values'].get('min', 0)
    return st

def ev_expr(expr, st):
    """avalia uma expressao molang de bone_visibility com os estados em st. Variavel/estado desconhecido -> None (indeterminado)."""
    if isinstance(expr, bool): return expr
    if isinstance(expr, (int, float)): return bool(expr)
    try:
        miss = []
        def sub(m):
            k = m.group(1)
            if k not in st: miss.append(k); return 'None'
            return repr(st[k])
        e = re.sub(r"(?:q|query)\.block_state\('([^']+)'\)", sub, str(expr))
        if miss: return None
        e = e.replace('&&', ' and ').replace('||', ' or ')
        e = re.sub(r'!(?!=)', ' not ', e); e = re.sub(r'\btrue\b', 'True', e); e = re.sub(r'\bfalse\b', 'False', e)
        if re.search(r'\b(q|query|v|variable|math|t|c)\.', e): return None
        return bool(eval(e, {'__builtins__': {}}, {}))
    except Exception:
        return None

def visible_bones(g, bv, st):
    """-> conjunto de nomes de ossos VISIVEIS. Osso escondido esconde os filhos (como no Bedrock). Osso sem regra = visivel;
    expressao que nao da pra resolver = visivel (comportamento antigo)."""
    bones = {b['name']: b for b in g.get('bones', [])}
    hid = set()
    for n, e in (bv or {}).items():
        if ev_expr(e, st) is False: hid.add(n)
    vis = set()
    for n, b in bones.items():
        cur = n; ok = True; k = 0
        while cur is not None and k < 20:
            if cur in hid: ok = False; break
            cur = bones[cur].get('parent') if cur in bones else None; k += 1
        if ok: vis.add(n)
    return vis

CARD = ['south', 'north', 'east', 'west']   # indice = dir do jogo (Furniture.faceOf): 0 sul, 1 norte(180), 2 leste(90), 3 oeste(-90)

def seg_from_flags(F, B, L, R):   # igual a decideSegmentFromFlags do addon (horizontal_full_connected.js)
    if not L and not R and not F and not B: return 'single'
    if L and not R and not F and not B: return 'right'
    if not L and R and not F and not B: return 'left'
    if not L and not R and F and not B: return 'front'
    if not L and not R and not F and B: return 'back'
    if L and R and not F and not B: return 'mid'
    if not L and not R and F and B: return 'mid2'
    if F and L and not R and not B: return 'front_right'
    if F and not L and R and not B: return 'front_left'
    if not F and L and not R and B: return 'back_right'
    if not F and not L and R and B: return 'back_left'
    if F and L and R and not B: return 'front_mid'
    if not F and L and R and B: return 'back_mid'
    if F and L and not R and B: return 'mid_right'
    if F and not L and R and B: return 'mid_left'
    return 'center'

def rel_flags(card, n, e, s_, w):   # frente/tras/esquerda/direita a partir dos vizinhos do mundo (igual getRelatives do addon)
    return {'north': (n, s_, w, e), 'south': (s_, n, e, w), 'west': (w, e, s_, n), 'east': (e, w, n, s_)}.get(card, (n, s_, w, e))

def slot_vertical(top, bottom, left, right):   # igual decideSlotAndApply (vertical_full_connected.js)
    vu, vd, hl, hr = int(bool(top)), int(bool(bottom)), int(bool(left)), int(bool(right))
    va, ha = vu or vd, hl or hr
    if not va and not ha: return 'single'
    if va and not ha: return 'bottom' if (vu and not vd) else ('mid' if (vu and vd) else 'top')
    if not va and ha: return 'left' if (not hl and hr) else ('mid2' if (hl and hr) else 'right')
    row = 'bottom' if (vu and not vd) else ('mid' if (vu and vd) else 'top')
    col = 'left' if (not hl and hr) else ('mid' if (hl and hr) else 'right')
    return 'center' if (row == 'mid' and col == 'mid') else row + '_' + col

def mkstate(d, c, ci, m, u):
    """estado do bloco para a variante: direcao ci, vizinhos do mundo m (bit0 N, 1 L/E, 2 S, 3 O/W), cima/baixo u (bit0 cima, bit1 baixo)"""
    st = state_defaults(d); states = d['description'].get('states', {})
    card = CARD[ci]; st['minecraft:cardinal_direction'] = card
    n, e, s_, w = bool(m & 1), bool(m & 2), bool(m & 4), bool(m & 8); up, dn = bool(u & 1), bool(u & 2)
    for k, v in (('md:north_neighbor', n), ('md:east_neighbor', e), ('md:south_neighbor', s_), ('md:west_neighbor', w)):
        if k in states: st[k] = 1 if v else 0
    if 'md:top' in states: st['md:top'] = 1 if up else 0
    if 'md:bottom' in states: st['md:bottom'] = 1 if dn else 0
    F, B, L, R = rel_flags(card, n, e, s_, w)
    if 'tag:md:z_horizontal_connector' in c:   # postes/esteiras em fila (z_horizontal_connected.js): so frente/tras -> single, front, mid, back
        sg = 'mid' if (F and B) else ('front' if F else ('back' if B else 'single'))
        if 'md:segment' in states: st['md:segment'] = sg
        if 'md:slot' in states: st['md:slot'] = sg
    elif 'tag:md:horizontal_connector' in c:
        sg = seg_from_flags(F, B, L, R)
        if 'md:segment' in states: st['md:segment'] = sg
        if 'md:slot' in states: st['md:slot'] = sg
    elif 'md:slot' in states:
        st['md:slot'] = slot_vertical(up, dn, L, R)
    return st

def active_geo(d, st):
    """-> (geometry_id, ossos visiveis) para o estado st, aplicando permutacoes (geometria/bone_visibility) como o Bedrock (a ultima que casa vence)"""
    gref = d['components'].get('minecraft:geometry'); gid = gref if isinstance(gref, str) else (gref or {}).get('identifier')
    bv = gref.get('bone_visibility') if isinstance(gref, dict) else None
    for pm in d.get('permutations', []):
        g2 = pm['components'].get('minecraft:geometry')
        if g2 is None or ev_expr(pm['condition'], st) is not True: continue
        if isinstance(g2, str): gid = g2; bv = None
        else: gid = g2.get('identifier', gid); bv = g2.get('bone_visibility')
    if gid not in geos: return None
    return gid, frozenset(visible_bones(geos[gid], bv if isinstance(bv, dict) else {}, st))

_dyn_cache = {}
DIMS = ('c', 'm', 'u', 'p', 'w')   # direcao, 4 vizinhos, cima/baixo, paridade da pilha (md:inout), encostado na parede (md:mount)
def dyn_size(dmode):
    n = 1
    for b, k in enumerate((4, 16, 4, 2, 2)):
        if dmode & (1 << b): n *= k
    return n

def dyn_build(d, c):
    """blocos cuja forma depende do mundo: direcao, vizinhos iguais, cima/baixo, pilha (md:inout em_0_up) e parede (md:mount wall_0).
    Devolve (mats, quads, dmode, masks) ou None se estatico. dmode: bit0 direcao(4), bit1 vizinhos(16), bit2 cima/baixo(4), bit3 pilha(2), bit4 parede(2).
    Variante idx = ci + C*(mi + M*(ui + U*(pi + P*wi))) com cada fator =1 se o bit nao esta ligado. masks[q]: bit idx = quad visivel."""
    import itertools
    states = d['description'].get('states', {})
    conn = ('tag:md:horizontal_connector' in c) or ('tag:md:z_horizontal_connector' in c)
    raw = json.dumps(d, sort_keys=True)
    txt = json.dumps(c.get('minecraft:geometry')) + ' ' + ' '.join(pm['condition'] + json.dumps(pm['components'].get('minecraft:geometry')) for pm in d.get('permutations', []))
    m_p = 'md:inout' in txt and '"md:in_0_up"' in raw
    m_w = 'md:mount' in txt and '"md:wall_0"' in raw
    sigs = txt + json.dumps(state_defaults(d), sort_keys=True) + str(conn) + str('md:slot' in states) + str(m_p) + str(m_w)
    if sigs in _dyn_cache: return _dyn_cache[sigs]
    m_n = '_neighbor' in txt or ((conn or 'md:slot' in states) and ('md:slot' in txt or 'md:segment' in txt))
    m_u = 'md:top' in txt or 'md:bottom' in txt or ('md:slot' in states and not conn and 'md:slot' in txt)
    m_c = 'cardinal_direction' in txt or m_n or m_w
    full = [4 if m_c else 1, 16 if m_n else 1, 4 if m_u else 1, 2 if m_p else 1, 2 if m_w else 1]
    def stt(co):
        st = mkstate(d, c, co[0], co[1], co[2])
        if m_p: st['md:inout'] = 2 if co[3] else 0
        if m_w: st['md:mount'] = 'wall' if co[4] else 'main'
        return st
    res = {}
    for co in itertools.product(*[range(k) for k in full]):
        res[co] = active_geo(d, stt(co))
    if any(v is None for v in res.values()): _dyn_cache[sigs] = None; return None
    dep = []
    for ax in range(5):   # a forma depende deste eixo?
        dep.append(any(res[co] != res[co[:ax] + (0,) + co[ax + 1:]] for co in res))
    if not any(dep): _dyn_cache[sigs] = None; return None
    dm = sum(1 << b for b in range(5) if dep[b])
    sz = [full[b] if dep[b] else 1 for b in range(5)]
    nbits = sz[0] * sz[1] * sz[2] * sz[3] * sz[4]; nb = (nbits + 7) // 8
    def ix_of(co): return co[0] + sz[0] * (co[1] + sz[1] * (co[2] + sz[2] * (co[3] + sz[3] * co[4])))
    comp = list(itertools.product(*[range(k) for k in sz]))   # so as combinacoes dos eixos que importam
    gids = sorted({res[co][0] for co in comp})
    mats = []; quads = []; masks = []
    for gid in gids:
        m1, q1 = bake(geos[gid])
        for mn in m1:
            if mn not in mats: mats.append(mn)
        for slot, sh, vs, bn in q1:
            mk = bytearray(nb)
            for co in comp:
                r = res[co]
                if r[0] == gid and bn in r[1]:
                    ix = ix_of(co); mk[ix >> 3] |= 1 << (ix & 7)
            if any(mk):
                quads.append((mats.index(m1[slot]), sh, vs, 3)); masks.append(bytes(mk))
    out = (mats, quads, dm, masks) if quads else None
    _dyn_cache[sigs] = out; return out

def frame0(im):
    """textura mais alta que larga = tira de flipbook (quadros w x w empilhados). A geometria do addon e quadrada e usa o 1o quadro:
    pega o quadro de cima em vez de espremer a tira inteira (que misturava todos os quadros)."""
    w, h = im.size
    return im.crop((0, 0, w, w)) if h > w else im

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
usednames = set(); skipped = 0; bv_fixed = 0; geo_dyn = {}
for key in reg['items']:
    if key not in pathof: skipped += 1; continue
    folder, p = pathof[key]
    d = json.load(open(p))['minecraft:block']; c = d['components']
    gref = c.get('minecraft:geometry'); gid = gref if isinstance(gref, str) else (gref or {}).get('identifier')
    g = geos.get(gid)
    if not g: skipped += 1; continue
    asm = assemble(d, c)
    has_tog = any(v in d['description'].get('states', {}) for v in ('md:is_open', 'md:on'))
    dy = None if (asm or has_tog) else dyn_build(d, c)
    if dy:
        dsig = 'DYN:' + hashlib.md5(repr((sorted(dy[0]), dy[2], dy[3], [round(sum(v[0]+v[1]+v[2] for v in q[2]), 3) for q in dy[1]])).encode()).hexdigest()
        if dsig not in geo_index:
            geo_dyn[len(geo_list)] = (dy[2], dy[3]); geo_index[dsig] = len(geo_list); geo_list.append((dy[0], dy[1]))
        gkey = dsig
    else:
        gkey = gid if not asm else 'ASM:' + '|'.join(f'{a}@{o}' for a, o in sorted(asm)) + '|' + hashlib.md5(json.dumps([gref, [pm['components'].get('minecraft:geometry') for pm in d.get('permutations', [])]], sort_keys=True, default=str).encode()).hexdigest()[:10]
    if gkey not in geo_index:
        if asm:
            mats = []; quads = []
            for sg, (dx, dy, dz) in asm:
                if sg not in geos: continue
                m2, q2 = bake(geos[sg])
                nm_ = ASM_NAME.get((sg, (dx, dy, dz)))
                if nm_:   # bone_visibility do bloco-segmento (ex. md:segment == 'left' esconde a cabeceira da direita)
                    st_ = state_defaults(d); st_['md:segment'] = nm_
                    if 'md:slot' in d['description'].get('states', {}): st_['md:slot'] = nm_
                    r_ = active_geo(d, st_)
                    if r_ and r_[0] == sg:
                        q3 = [qq for qq in q2 if qq[3] in r_[1]]
                        if q3: q2 = q3
                for mn in m2:
                    if mn not in mats: mats.append(mn)
                for slot, sh, vs, bn in q2:
                    quads.append((mats.index(m2[slot]), sh, [(x+dx, y+dy, z+dz, u, v) for x, y, z, u, v in vs], bn))
        else:
            mats, quads = bake(g)
        geo_index[gkey] = len(geo_list); geo_list.append((mats, quads))
    # estados abrir/ligar (md:is_open / md:on): bone_visibility ou variante aberta (transformacao da porta)
    stvar = next((v for v in ('md:is_open', 'md:on') if v in d['description'].get('states', {})), None)
    kind = 0; openbox = None; vis_sig = ''; pbv = []
    if stvar:
        isl = (stvar == 'md:on' and group(folder, 4) == 'ILUMINAÇÃO')
        kind = 2 if isl else 1
        bv = gref.get('bone_visibility') if isinstance(gref, dict) else None
        pbv = [pm['components']['minecraft:geometry'].get('bone_visibility') for pm in d.get('permutations', []) if isinstance(pm['components'].get('minecraft:geometry'), dict) and 'bone_visibility' in pm['components']['minecraft:geometry']]
        vis_sig = f'|{stvar}|{kind}|' + json.dumps(bv, sort_keys=True) + json.dumps(pbv, sort_keys=True) + json.dumps(state_defaults(d), sort_keys=True)
    bvd = gref.get('bone_visibility') if isinstance(gref, dict) else None
    if not stvar and isinstance(bvd, dict) and not asm and not dy:   # estado padrao (sem vizinhos, direcao south): so os ossos que valem nele
        vis_sig = '|bv|' + json.dumps(bvd, sort_keys=True) + json.dumps(state_defaults(d), sort_keys=True)
    gkey2 = gkey + vis_sig
    if gkey2 not in geo_index:
        mats, quads = geo_list[geo_index[gkey]]
        nq = []
        st0 = state_defaults(d); st0[stvar or 'x'] = (kind == 2); st1 = dict(st0); st1[stvar or 'x'] = (kind != 2)
        a0 = active_geo(d, st0) if stvar else None; a1 = active_geo(d, st1) if stvar else None
        has_bv = stvar and ((isinstance(gref, dict) and isinstance(gref.get('bone_visibility'), dict)) or bool(pbv))
        if has_bv and a0 and a1:
            vis0 = a0[1]; vis1 = a1[1]   # ossos visiveis em cada estado (componente + permutacoes)
            for slot, sh, vs, bn in quads:
                nq.append((slot, sh, vs, (1 if bn in vis0 else 0) | (2 if bn in vis1 else 0)))
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
        elif isinstance(bvd, dict) and not asm and not dy:
            vis = visible_bones(g, bvd, state_defaults(d))
            nq = [(slot, sh, vs, 3) for slot, sh, vs, bn in quads if bn in vis]
            if not nq: nq = [(slot, sh, vs, 3) for slot, sh, vs, bn in quads]   # tudo escondido = regra que nao entendi: mostra tudo
            bv_fixed += 1
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
    nocol = col is None   # addon sem colisao (cortina, quadro, bandeira...): no jogo atravessa, mas ainda precisa de caixa pra mirar/quebrar
    if nocol:
        qx = [v[0] for _, _, vs, _ in quads for v in vs]; qy = [v[1] for _, _, vs, _ in quads for v in vs]; qz = [v[2] for _, _, vs, _ in quads for v in vs]
        if qx:
            lo = [max(0.0, min(qx)), max(0.0, min(qy)), max(0.0, min(qz))]; hi = [min(1.0, max(qx)), min(1.0, max(qy)), min(1.0, max(qz))]
            for a in range(3):
                if hi[a] - lo[a] < 0.14:
                    mid = (hi[a] + lo[a]) / 2; lo[a] = max(0.0, mid - 0.07); hi[a] = min(1.0, mid + 0.07)
            col = [round(lo[0], 3), round(lo[1], 3), round(lo[2], 3), round(hi[0], 3), round(hi[1], 3), round(hi[2], 3)]
        else:
            col = [0, 0, 0, 1, 1, 1]
    base = names.get(key, key.replace('_drz', '').replace('_', ' ').title())
    nm = ptname(key, base)
    if nm in usednames: nm = f'{nm} ({key.replace("_drz","")})'
    usednames.add(nm)
    fp = None
    if asm:
        xs = [o[0] for _, o in asm]; ys = [o[1] for _, o in asm]; zs = [o[2] for _, o in asm]
        fp = [min(xs), max(xs), min(ys), max(ys), min(zs), max(zs)]
    items.append({'k': key, 'n': nm, 'c': 5 if is_furn(folder) else 4, 'g': geo_index[gkey], 'kd': kind, 't': tl, 'b': col,
                  'l': int(c.get('minecraft:light_emission', 0) or 0), 'f': folder, 'gp': group(folder, 5 if is_furn(folder) else 4), 'sit': 1 if 'md:sit' in c else 0, **({'fp': fp} if fp else {}), **({'nc': 1} if nocol else {}), **({'ob': openbox} if openbox else {}), **({'sc': scr} if scr else {})})

# ---- escreve ----
with open(OUT + '/geo.bin', 'wb') as fo:
    fo.write(struct.pack('<ii', -2, len(geo_list)))   # -2 = formato v2 (byte dmode por geometria + mascara de variantes por quad)
    for gi, (mats, quads) in enumerate(geo_list):
        dm, masks = geo_dyn.get(gi, (0, None))
        fo.write(struct.pack('<iiB', len(mats), len(quads), dm))
        for qi, (slot, sh, vs, vb) in enumerate(quads):
            fo.write(struct.pack('<BBB', slot, sh, vb if isinstance(vb, int) else 3))
            for x, y, z, u, v in vs:
                fo.write(struct.pack('<hhhHH', int(round(x*2048)), int(round(y*2048)), int(round(z*2048)),
                                     int(max(0, min(1, u))*65535), int(max(0, min(1, v))*65535)))
            if dm: fo.write(masks[qi])   # mascara de variantes DEPOIS dos 4 vertices
json.dump(items, open(OUT + '/items.json', 'w'), ensure_ascii=False, separators=(',', ':'))
for i, short in enumerate(reg['tex']):
    pth = texpath(short)
    if not pth: continue
    im = frame0(Image.open(pth).convert('RGBA'))
    if im.size != (64, 64):
        if im.size[0] <= 64 and im.size[1] <= 64: im = im.resize((64, 64), Image.NEAREST)   # pixel art: ampliacao exata, sem borrar
        else: im = im.convert('RGBa').resize((64, 64), Image.BOX).convert('RGBA')            # reducao por media de area, com alfa pre-multiplicado (sem halo escuro nas bordas transparentes)
    im.save(f'{OUT}/tex/{i}.png', optimize=True)
# ---- versao HD (128x128) das texturas que passam de 64px: o jogo usa tile 2x2 no atlas quando sobra espaco (Blk.buildDeco) ----
big_ids = []
for f_ in glob.glob(OUT + '/tex/*hd.png'): os.remove(f_)
for i, short in enumerate(reg['tex']):
    pth = texpath(short)
    if not pth: continue
    src = frame0(Image.open(pth).convert('RGBA'))
    if max(src.size) <= 64: continue
    if src.size == (128, 128): hd = src
    elif src.size[0] >= 128 and src.size[1] >= 128: hd = src.convert('RGBa').resize((128, 128), Image.BOX).convert('RGBA')
    else: hd = src.convert('RGBa').resize((128, 128), Image.LANCZOS).convert('RGBA')   # 72x72, 64x128... (ampliar)
    hd.save(f'{OUT}/tex/{i}hd.png', optimize=True); big_ids.append(i)
json.dump(big_ids, open(OUT + '/big.json', 'w'))
import numpy as np
q32 = []
for i, short in enumerate(reg['tex']):
    if i in set(big_ids) or not os.path.exists(f'{OUT}/tex/{i}.png'): continue
    a_ = np.array(Image.open(f'{OUT}/tex/{i}.png').convert('RGBA'))
    if np.array_equal(np.repeat(np.repeat(a_[::2, ::2], 2, 0), 2, 1), a_): q32.append(i)   # 32x32 (ou 16x16) ampliado 2x: amostrar x*2,y*2 e exato
json.dump(q32, open(OUT + '/q32.json', 'w'))
print('texturas 32x32 nativas (4 por celula):', len(q32))
print('texturas HD (128x128):', len(big_ids))
os.makedirs(os.path.dirname(REG), exist_ok=True)
json.dump(reg, open(REG, 'w'), ensure_ascii=False)
print('geometrias com bone_visibility aplicado (estado padrao):', bv_fixed)
print('itens', len(items), 'geos', len(geo_list), 'texturas', len(reg['tex']), 'pulados', skipped)
