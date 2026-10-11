import re, sys
from parse import arts, names
from collections import deque
N=16
def idxgrid(i):
    pal,rows=arts[i]
    return [[(-1 if rows[y][x]=='.' else int(rows[y][x],16)) for x in range(N)] for y in range(N)]
def lum(i):
    pal,rows=arts[i]; g=idxgrid(i)
    out=[[0]*N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            if g[y][x]<0: continue
            c=pal[g[y][x]]; out[y][x]=(((c>>16)&255)*0.299+((c>>8)&255)*0.587+(c&255)*0.114)/255
    return out
def nb4(x,y):
    for dx,dy in((1,0),(-1,0),(0,1),(0,-1)):
        a,b=x+dx,y+dy
        if 0<=a<N and 0<=b<N: yield a,b
def nb8(x,y):
    for dx in(-1,0,1):
        for dy in(-1,0,1):
            if dx or dy:
                a,b=x+dx,y+dy
                if 0<=a<N and 0<=b<N: yield a,b
def new(v=0): return [[v]*N for _ in range(N)]
def mapping(i,m,default=0):
    g=idxgrid(i); H=new(default)
    for y in range(N):
        for x in range(N):
            if g[y][x] in m: H[y][x]=m[g[y][x]]
    return H
def despeckle(H,keep=lambda x,y:False):
    """remove níveis ±1 isolados (sem vizinho 4 do mesmo nível)"""
    out=[r[:] for r in H]
    for y in range(N):
        for x in range(N):
            v=H[y][x]
            if abs(v)!=1 or keep(x,y): continue
            if not any(H[b][a]==v for a,b in nb4(x,y)): out[y][x]=0
    return out
def ring(H):
    for k in range(N):
        for (x,y) in((k,0),(k,N-1),(0,k),(N-1,k)):
            if H[y][x]>0: H[y][x]=0
    return H
def blur_lumps(i,hi=0.78,lo=0.18,passes=1):
    L=lum(i)
    for _ in range(passes):
        L2=new(0.0)
        for y in range(N):
            for x in range(N):
                s=0;c=0
                for dy in(-1,0,1):
                    for dx in(-1,0,1):
                        a=(x+dx)%N;b=(y+dy)%N; s+=L[b][a];c+=1
                L2[y][x]=s/c
        L=L2
    flat=sorted(v for r in L for v in r)
    th=flat[int(hi*len(flat))]; tl=flat[int(lo*len(flat))]
    H=new()
    for y in range(N):
        for x in range(N):
            H[y][x]=1 if L[y][x]>=th else (-1 if L[y][x]<=tl else 0)
    return H
def dist_in(mask):
    """distância (4-viz) até o primeiro pixel fora da máscara; fora=0, borda interna=1"""
    D=[[0]*N for _ in range(N)]
    q=deque()
    for y in range(N):
        for x in range(N):
            if not mask[y][x]: D[y][x]=0
            else:
                edge=any(not mask[b][a] for a,b in nb4(x,y)) or x in(0,N-1) and False
                if edge: D[y][x]=1; q.append((x,y))
                else: D[y][x]=99
    while q:
        x,y=q.popleft()
        for a,b in nb4(x,y):
            if mask[b][a] and D[b][a]>D[y][x]+1: D[b][a]=D[y][x]+1; q.append((a,b))
    return D
def dome(mask,lo=1,hi=2):
    D=dist_in(mask); H=new()
    for y in range(N):
        for x in range(N):
            if mask[y][x]: H[y][x]=lo if D[y][x]<=1 else hi
    return H
def maskof(i,s):
    g=idxgrid(i); return [[g[y][x] in s for x in range(N)] for y in range(N)]
def merge(H,*layers):  # camadas posteriores sobrescrevem onde != None
    for L in layers:
        for y in range(N):
            for x in range(N):
                if L[y][x] is not None: H[y][x]=L[y][x]
    return H
def none(): return [[None]*N for _ in range(N)]

R={}
# ---- terra e solos
R[0]=despeckle(blur_lumps(0,0.82,0.12))
R[1]=despeckle(mapping(1,{6:1,9:1,7:2,8:2,5:-1}))
R[2]=despeckle(mapping(2,{5:1,6:1,7:2}))
def sidelayer(i,dirt,shadow,lvl=1,lip=-1):
    g=idxgrid(i); H=new()
    for y in range(N):
        for x in range(N):
            v=g[y][x]
            if y<=3: H[y][x]=lvl
            elif v not in dirt: H[y][x]=lvl
    for y in range(N):
        for x in range(N):
            v=g[y][x]
            if y>=4 and v in shadow: H[y][x]=lip
    # pixel de terra logo abaixo da camada: sombra
    out=[r[:] for r in H]
    for y in range(1,N):
        for x in range(N):
            if H[y][x]==0 and H[y-1][x]==lvl and y>3: out[y][x]=lip
    return out
R[3]=sidelayer(3,{4,7,8},set())
R[4]=despeckle(mapping(4,{4:1,5:2,6:-1}))
R[5]=sidelayer(5,{7,8,9},{6})
R[6]=blur_lumps(6,0.78,0.2,2)
R[7]=despeckle(mapping(7,{5:1,6:1,7:2}))
R[8]=sidelayer(8,{8,9,10},{4}, lvl=1, lip=-1)
# fix: neve lateral – sombra azul da linha 5
R[9]=despeckle(mapping(9,{5:-1,6:-1,4:1,7:-1}))
# argila: camadas (perfil por linha)
def rowprofile(i,thr=(0.4,0.62)):
    L=lum(i); H=new()
    means=[sum(L[y])/N for y in range(N)]
    sm=[(means[max(0,y-1)]+means[y]*2+means[min(N-1,y+1)])/4 for y in range(N)]
    for y in range(N):
        v=-1 if sm[y]<thr[0] else (1 if sm[y]>thr[1] else 0)
        for x in range(N): H[y][x]=v
    return H
H=new()
for y in range(N): H[y]=[(1 if 3<=y<=9 else (-1 if y==10 else 0))]*N
R[10]=H
# cascalho
def pebbles(i,mortar,lowset=()):
    g=idxgrid(i); m=[[g[y][x] not in mortar for x in range(N)] for y in range(N)]
    H=dome(m,1,2)
    for y in range(N):
        for x in range(N):
            if not m[y][x]: H[y][x]=-1
    return H
R[11]=pebbles(11,{0,5})
for y in range(N):
    for x in range(N):
        if R[11][y][x]==2: R[11][y][x]=1
R[12]=blur_lumps(12,0.75,0.22,2)
R[13]=blur_lumps(13,0.75,0.22,2)
R[14]=despeckle(mapping(14,{5:-1,4:1}))
g=idxgrid(15)
H=new(1)
for y in range(N):
    for x in range(N):
        if g[y][x] in (6,0): H[y][x]=-1
R[15]=H
# minérios
def ore(i,hl=(7,8)):
    g=idxgrid(i); m=[[g[y][x]>=5 for x in range(N)] for y in range(N)]
    H=dome(m,1,2)
    for y in range(N):
        for x in range(N):
            if m[y][x] and g[y][x] in hl: H[y][x]=2
            if not m[y][x] and any(m[b][a] for a,b in nb4(x,y)): H[y][x]=-1
    return H
for i in range(16,24): R[i]=ore(i)
R[16]=ore(16,(7,8))
# mel
g=idxgrid(24); m=[[2<=x<=13 and 2<=y<=13 for x in range(N)] for y in range(N)]
R[24]=dome(m,1,2)
# esponja
g=idxgrid(25)
H=new(1)
for y in range(N):
    for x in range(N):
        v=g[y][x]
        if v==5: H[y][x]=-2
        elif v==0: H[y][x]=-1
for y in range(N):
    for x in range(N):
        if H[y][x]==1 and any(H[b][a]<0 for a,b in nb4(x,y)): H[y][x]=0
R[25]=H
# favo
g=idxgrid(26); m=[[g[y][x] not in (6,0) for x in range(N)] for y in range(N)]
H=dome(m,1,2)
for y in range(N):
    for x in range(N):
        if not m[y][x]: H[y][x]=-1
R[26]=H
R[27]=mapping(27,{0:1,1:1,2:1,3:1,4:-1,5:-2,6:-2,7:-2})
R[28]=despeckle(mapping(28,{0:-1,1:-1,2:0,3:1,4:2,5:1,6:2,7:2}))
g=idxgrid(29)
R[29]=mapping(29,{0:0,1:0,4:0,2:-1,3:-1,5:2,6:2,7:2},0)
R[30]=despeckle(mapping(30,{0:-1,4:1,5:2,6:2}))
R[31]=despeckle(mapping(31,{1:-1,4:1}))
R[32]=mapping(32,{0:-1,1:0,2:1,3:1,4:2,5:2})
# melancia topo
g=idxgrid(33); H=mapping(33,{0:-1,1:-1,2:0,3:1,4:1,5:1,6:2,7:2})
for y in range(N):
    for x in range(N):
        if g[y][x] not in (6,7) and any(g[b][a] in (6,7) for a,b in nb8(x,y)): H[y][x]=-1
R[33]=H
R[34]=mapping(34,{0:-1,1:0,2:1,3:2,4:2})
g=idxgrid(35); H=mapping(35,{0:-1,1:0,2:1,3:2,4:2,6:2,7:2,8:2,5:-1})
R[35]=H
g=idxgrid(36)
H=mapping(36,{2:1,1:1,6:0,0:-1,5:-1,3:-2,4:-2})
R[36]=H
R[37]=mapping(37,{0:-1,1:0,2:1,3:2,4:2,6:0,5:0})
# palha: cursos sobrepostos (a borda de baixo de cada curso fica por cima do de baixo)
H=new()
lv=[-1,0,1,2]
for y in range(N):
    for x in range(N): H[y][x]=lv[y%4]
R[38]=H
for k in R: ring(R[k])

# ---- padrões K_PIX
PIX={}
src=open('../app/src/main/java/com/cuboland/app/Pix.kt').read()
body=src[src.index('val P'):]
pats=re.findall(r'arrayOf\(((?:"[0-9]{16}",?\s*){16})\)',body)
def pat(k): return [[int(c) for c in r] for r in re.findall(r'"([0-9]{16})"',pats[k])]
def runs_zero(p):
    H=new(); 
    for y in range(N):
        for x in range(N):
            if p[y][x]!=0: continue
            hr=1;a=x-1
            while a>=0 and p[y][a]==0: hr+=1;a-=1
            a=x+1
            while a<N and p[y][a]==0: hr+=1;a+=1
            vr=1;b=y-1
            while b>=0 and p[b][x]==0: vr+=1;b-=1
            b=y+1
            while b<N and p[b][x]==0: vr+=1;b+=1
            H[y][x]=-2 if (hr>=4 or vr>=3) else -1
    return H
PIX[14]=ring(runs_zero(pat(14)))   # tábuas: fundo só nos vãos
p=pat(15); H=new()
for y in range(N):
    for x in range(N): H[y][x]={0:-2,1:0,2:0,3:1}[p[y][x]]
PIX[15]=ring(despeckle(H))   # casca: sulcos fundos, cristas
p=pat(16); H=new()
for y in range(N):
    for x in range(N):
        r=((x-7.5)**2+(y-7.5)**2)**0.5
        v=p[y][x]
        H[y][x]=(1 if r>5.2 else (-1 if r<2.6 else 0)) if v==1 else 0
        if v==0: H[y][x]=0
PIX[16]=ring(H)  # topo: casca em volta alta, miolo afundado
p=pat(18); H=new()
for y in range(N):
    for x in range(N):
        v=p[y][x]
        H[y][x]=-1 if v==0 else (1 if v==3 else 0)
for y in (3,11):
    for x in range(N): H[y][x]=1
for y in (4,12):
    for x in range(N): H[y][x]=1
PIX[18]=ring(H)

def enc(H): return [''.join(str(v+2) for v in r) for r in H]

def count_quads(H):
    # estimativa: retângulos gulosos + paredes por segmentos
    used=[[False]*N for _ in range(N)]; q=0
    for y in range(N):
        for x in range(N):
            if used[y][x]: continue
            v=H[y][x]; w=1
            while x+w<N and H[y][x+w]==v and not used[y][x+w]: w+=1
            h=1
            while y+h<N and all(H[y+h][x+k]==v and not used[y+h][x+k] for k in range(w)): h+=1
            for yy in range(y,y+h):
                for xx in range(x,x+w): used[yy][xx]=True
            q+=1
    def hh(x,y): return 0 if x<0 or y<0 or x>=N or y>=N else H[y][x]
    for (dx,dy) in((-1,0),(1,0),(0,-1),(0,1)):
        for y in range(N):
            for x in range(N):
                pass
    # paredes por direção com mesclagem em linha
    for (dx,dy) in((-1,0),(1,0)):
        for x in range(N):
            y=0
            while y<N:
                v=hh(x,y); n=hh(x+dx,y)
                if n<v:
                    e=y
                    while e+1<N and hh(x,e+1)==v and hh(x+dx,e+1)==n: e+=1
                    q+=1; y=e+1
                else: y+=1
    for (dx,dy) in((0,-1),(0,1)):
        for y in range(N):
            x=0
            while x<N:
                v=hh(x,y); n=hh(x,y+dy)
                if n<v:
                    e=x
                    while e+1<N and hh(e+1,y)==v and hh(e+1,y+dy)==n: e+=1
                    q+=1; x=e+1
                else: x+=1
    return q

if __name__=='__main__':
    tot=0
    for k in sorted(R):
        q=count_quads(R[k]); print(k,names[k],q)
    for k in sorted(PIX): print('pix',k,count_quads(PIX[k]))
    out=['package com.cuboland.app','',
     '/** Mapas de relevo escritos à mão (gerados por relief_gen.py): cada pixel 16x16 da textura tem um nível, \'0\'..\'4\' = -2..+2.',
     ' *  A altura segue a lógica de cada bloco: pepitas saem da pedra (com a cavidade em volta), vãos entre tábuas e rejunte entram,',
     ' *  gomos/costelas sobem nas cristas e descem nos sulcos, cascas de neve/micélio ficam por cima da terra etc.',
     ' *  Blocos que não estão aqui continuam com o relevo automático da Relief (por brilho). */',
     'object NatRelief {','    const val NONE: String = ""','    /** por índice da NatArt (tipo K_PAL de Blk) */',
     '    val art: Array<Array<String>?> = arrayOf(']
    for i in range(len(arts)):
        if i in R:
            out.append('        arrayOf('+', '.join('"%s"'%s for s in enc(R[i]))+'),  // '+names[i])
        else: out.append('        null,  // '+names[i])
    out.append('    )')
    out.append('    /** por padrão Pix (tipo K_PIX de Blk): tábuas, casca, topo de tronco, bambu */')
    out.append('    val pix: Array<Array<String>?> = arrayOf(')
    for i in range(20):
        if i in PIX: out.append('        arrayOf('+', '.join('"%s"'%s for s in enc(PIX[i]))+'),  // pix %d'%i)
        else: out.append('        null,')
    out.append('    )'); out.append('}')
    open('NatRelief.kt','w').write('\n'.join(out)+'\n')
    # preview
    from PIL import Image,ImageDraw
    S=6; cols=8
    tiles=sorted(R)
    W=cols*(16*S*2+10); Hh=((len(tiles)+cols-1)//cols)*(16*S+18)
    im=Image.new('RGB',(W,Hh),(30,30,30)); d=ImageDraw.Draw(im)
    for n,k in enumerate(tiles):
        ox=(n%cols)*(16*S*2+10); oy=(n//cols)*(16*S+18)
        pal,rows=arts[k]; H=R[k]
        for y in range(N):
            for x in range(N):
                ch=rows[y][x]; c=(255,0,255) if ch=='.' else ((pal[int(ch,16)]>>16)&255,(pal[int(ch,16)]>>8)&255,pal[int(ch,16)]&255)
                d.rectangle([ox+x*S,oy+y*S,ox+x*S+S-1,oy+y*S+S-1],fill=c)
                v=H[y][x]; g=int(128+v*55)
                col=(g,g,g) if v>=0 else (g//2+10,g//2+10,g+40)
                d.rectangle([ox+16*S+x*S,oy+y*S,ox+16*S+x*S+S-1,oy+y*S+S-1],fill=col)
        d.text((ox,oy+16*S+2),f"{k}{names[k][:16]}",fill=(255,255,255))
    im.save('relief_sheet.png')
