import random, math
N=16
def blank(v=3): return [[v]*N for _ in range(N)]
def hx(v): return '0123456789abcdef'[v]
class Noise:
    def __init__(s,seed,cells):
        r=random.Random(seed); s.c=cells; s.t=[[r.random() for _ in range(cells)] for _ in range(cells)]
    def __call__(s,x,y,sx=1.0,sy=1.0):
        c=s.c; fx=(x*sx/N*c)%c; fy=(y*sy/N*c)%c
        x0=int(fx); y0=int(fy); tx=fx-x0; ty=fy-y0
        tx=tx*tx*(3-2*tx); ty=ty*ty*(3-2*ty)
        a=s.t[y0%c][x0%c]; b=s.t[y0%c][(x0+1)%c]; cc=s.t[(y0+1)%c][x0%c]; d=s.t[(y0+1)%c][(x0+1)%c]
        return (a*(1-tx)+b*tx)*(1-ty)+(cc*(1-tx)+d*tx)*ty
def clamp(v,a=1,b=5): return max(a,min(b,v))
def H(*a):
    r=random.Random(hash(a)&0xffffffff); return r.random()

def concrete(seed=1):
    r=random.Random(seed); n=Noise(seed,4); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            v=n(x,y); t=3+(1 if v>0.74 else 0)-(1 if v<0.30 else 0)
            q=r.random()
            if q<0.035: t-=1
            elif q<0.07: t+=1
            if r.random()<0.012: t=1
            elif r.random()<0.01: t=5
            g[y][x]=clamp(t,2,5)
    return g,rl
def clay(seed=2):
    r=random.Random(seed); n=Noise(seed,4); n2=Noise(seed+9,8); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            v=n(x,y,0.5,1.5)*0.7+n2(x,y,1,2)*0.3
            t=3+(1 if v>0.62 else 0)-(1 if v<0.38 else 0)
            if r.random()<0.05: t=clamp(t+r.choice([-1,1]))
            if r.random()<0.03: t=2; rl[y][x]=1
            g[y][x]=t
    for _ in range(5):
        x=r.randrange(1,15);y=r.randrange(1,15)
        g[y][x]=5; rl[y][x]=3
    return g,rl
def brick(seed=3,rowh=4,bw=8):
    r=random.Random(seed); g=blank(); rl=blank(2)
    for y in range(N):
        row=y//rowh; ry=y%rowh; off=(row%2)*(bw//2)
        for x in range(N):
            xx=(x+off)%bw
            if ry==rowh-1 or xx==bw-1:
                g[y][x]=7; rl[y][x]=1; continue
            bid=(row, ((x+off)//bw))
            base=[2,3,3,4,3][int(H(bid,seed)*5)%5]
            t=base
            if ry==0: t+=1
            if ry==rowh-2: t-=1
            if xx==0: t+=1
            if xx==bw-2: t-=1
            if r.random()<0.16: t+=r.choice([-1,1])
            g[y][x]=clamp(t,1,5); rl[y][x]=3
    return g,rl
def sbrick(seed=4, crack=False, moss=False):
    r=random.Random(seed); g=blank(); rl=blank(2)
    rows=[(0,5),(5,5),(10,6)]; joints=[[3,10],[7,14],[1,9]]
    for ri,(y0,h) in enumerate(rows):
        for y in range(y0,y0+h):
            ry=y-y0
            for x in range(N):
                if ry==h-1 or x in joints[ri]:
                    g[y][x]=7; rl[y][x]=1; continue
                # stone id and local x
                js=sorted(joints[ri]); k=sum(1 for j in js if x>j)%2
                lx=(x-js[0]-1)%N if k==0 else (x-js[1]-1)
                stid=(ri,k)
                base=[2,3,3,4,3,2][int(H(stid,seed)*6)%6]
                t=base
                if ry==0: t+=1
                if ry==h-2: t-=1
                if x-1 in joints[ri] or (x-1)%N in joints[ri]: t+=1
                if (x+1)%N in joints[ri]: t-=1
                if r.random()<0.18: t+=r.choice([-1,1])
                g[y][x]=clamp(t); rl[y][x]=3
    if crack:
        for (sx,sy) in [(4,1),(11,6),(7,11)]:
            x,y=sx,sy
            for i in range(5):
                if 0<=x<N and 0<=y<N and g[y][x]!=7: g[y][x]=1; rl[y][x]=1
                x+=r.choice([0,1,1]); y+=r.choice([1,1,0])
                if y>=N or x>=N: break
    if moss:
        for y in range(N):
            for x in range(N):
                top=(g[y][x]!=7)
                p=0.0
                ry=0
                # moss on top rows of each stone and in mortar
                for (y0,h) in rows:
                    if y0<=y<y0+h: ry=y-y0
                if g[y][x]==7: p=0.35
                elif ry<=1: p=0.5 if ry==0 else 0.25
                else: p=0.07
                if r.random()<p:
                    if g[y][x]==7: g[y][x]=9
                    else: g[y][x]=8+[1,2,3,2][r.randrange(4)]
                    rl[y][x]=3
    return g,rl
def voronoi(seed,cells=3,jit=1.6):
    r=random.Random(seed); pts=[]
    step=N/cells
    for j in range(cells):
        for i in range(cells):
            pts.append(((i+0.5)*step+r.uniform(-jit,jit),(j+0.5)*step+r.uniform(-jit,jit)))
    return pts
def tdist(a,b):
    d=abs(a-b)%N; return min(d,N-d)
def vor_eval(pts,x,y):
    ds=[]
    for k,(px,py) in enumerate(pts):
        dx=x+0.5-px; dy=y+0.5-py
        dx=(dx+N/2)%N-N/2; dy=(dy+N/2)%N-N/2
        ds.append((math.hypot(dx,dy),k,dx,dy))
    ds.sort(); return ds[0],ds[1]
def cobble(seed=5,moss=False):
    r=random.Random(seed); pts=voronoi(seed,4 if False else 3,1.8)
    pts=voronoi(seed,3,1.9)+[(r.uniform(0,16),r.uniform(0,16)) for _ in range(2)]
    g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            a,b=vor_eval(pts,x,y); e=b[0]-a[0]
            if e<1.05:
                g[y][x]=0 if r.random()<0.7 else 1; rl[y][x]=1; continue
            base=[2,3,3,4,3][int(H(a[1],seed)*5)%5]
            lit=-(a[2]+a[3])*0.16
            t=base+round(lit)
            if e<2.0 and (a[2]+a[3])>0: t-=1
            if e<2.0 and (a[2]+a[3])<0: t+=1
            if r.random()<0.1: t+=r.choice([-1,1])
            g[y][x]=clamp(t,1,5); rl[y][x]=3 if e<2.4 else 4
    if moss:
        for y in range(N):
            for x in range(N):
                a,b=vor_eval(pts,x,y); e=b[0]-a[0]
                top=(a[3]<-0.6) or (a[2]<-1.5)
                p=0.4 if (e<1.05) else (0.3 if top else 0.03)
                if r.random()<p:
                    g[y][x]=8+[0,1,2,3,2][r.randrange(5)] if e>=1.05 else 8+[0,1][r.randrange(2)]
                    rl[y][x]=3
    return g,rl
def rough(seed):
    r=random.Random(seed); n=Noise(seed,4); n2=Noise(seed+5,8); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            v=n(x,y)*0.6+n2(x,y)*0.4
            t=3+(1 if v>0.58 else 0)-(1 if v<0.40 else 0)
            if v>0.7: t+=0
            q=r.random()
            if q<0.06: t-=1
            elif q<0.12: t+=1
            g[y][x]=clamp(t,2,5); rl[y][x]={2:1,3:2,4:3,5:3}[g[y][x]]
    for _ in range(7):
        x=r.randrange(1,15);y=r.randrange(1,15)
        g[y][x]=1; rl[y][x]=0
        if r.random()<0.6 and x<14: g[y][x+1]=2; rl[y][x+1]=1
    for _ in range(6):
        x=r.randrange(1,15);y=r.randrange(1,15)
        g[y][x]=5; rl[y][x]=3
    return g,rl
def smooth(seed=6):
    r=random.Random(seed); n=Noise(seed,3); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            t=3+(1 if (x+y)<9 else 0)-(1 if (x+y)>22 else 0)
            if n(x,y)>0.7: t+=0
            if r.random()<0.05: t=clamp(t+r.choice([-1,1]))
            if x==0 or y==0: t=5
            if x==15 or y==15: t=2
            g[y][x]=clamp(t,1,6)
    return g,rl
def speck(seed=7):
    r=random.Random(seed); n=Noise(seed,4); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            v=n(x,y); g[y][x]=3+(1 if v>0.66 else 0)-(1 if v<0.34 else 0)
    for _ in range(26):
        x=r.randrange(N);y=r.randrange(N); kind=r.random()
        c=1 if kind<0.35 else (5 if kind<0.75 else 9)
        cells=[(0,0)]+([(1,0)] if r.random()<0.6 else [])+([(0,1)] if r.random()<0.3 else [])
        for dx,dy in cells:
            xx=(x+dx)%N; yy=(y+dy)%N; g[yy][xx]=c
    return g,rl
def tiles(seed=8):
    r=random.Random(seed); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            if x%8==7 or y%8==7: g[y][x]=7; rl[y][x]=1; continue
            tid=(x//8,y//8); base=[2,3,3,4][int(H(tid,seed)*4)%4]
            lx=x%8; ly=y%8; t=base
            if ly==0 or lx==0: t+=1
            if ly==6 or lx==6: t-=1
            if r.random()<0.12: t+=r.choice([-1,1])
            g[y][x]=clamp(t); rl[y][x]=3
    return g,rl
def chisel(seed=9):
    r=random.Random(seed); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            t=3
            if x==0 or y==0 or x==15 or y==15: t=7; rl[y][x]=2
            elif x==1 or y==1: t=4
            elif x==14 or y==14: t=2
            elif x in (3,12) and 3<=y<=12 or y in (3,12) and 3<=x<=12:
                t=1 if (x==3 or y==3) else 4; rl[y][x]=1
            elif 4<=x<=11 and 4<=y<=11: t=2; rl[y][x]=1
            if t==3 and r.random()<0.12: t=clamp(t+r.choice([-1,1]))
            g[y][x]=t
    for (x,y) in [(7,5),(8,5),(5,7),(10,7),(5,8),(10,8),(7,10),(8,10),(7,7),(8,7),(7,8),(8,8)]:
        g[y][x]=5 if (x,y) in [(7,7),(8,8)] else 4; rl[y][x]=3
    return g,rl
def pillar(seed=10):
    r=random.Random(seed); g=blank(); rl=blank(2)
    pat=[5,4,3,2]
    for y in range(N):
        for x in range(N):
            t=pat[x%4]
            if y<2: t=5 if y==0 else 4; rl[y][x]=3
            elif y==2: t=1; rl[y][x]=1
            elif y==13: t=1; rl[y][x]=1
            elif y>=14: t=4 if y==14 else 3; rl[y][x]=3
            else:
                rl[y][x]=3 if x%4<2 else 1
                if r.random()<0.07: t=clamp(t+r.choice([-1,1]))
            g[y][x]=t
    return g,rl
def sandside(seed=11):
    r=random.Random(seed); g=blank(); rl=blank(2)
    bands=[3,3,4,3,3,2,3,4,4,3,3,2,3,3,4,3]
    for y in range(N):
        for x in range(N):
            t=bands[y]
            if r.random()<0.16: t=clamp(t+r.choice([-1,1]))
            g[y][x]=t
        if bands[y]==2:
            for x in range(N): rl[y][x]=1
    for _ in range(5):
        y=r.randrange(1,15);x0=r.randrange(0,12)
        for x in range(x0,x0+3): g[y][x]=2
    return g,rl
def obsidian(seed=12):
    r=random.Random(seed); pts=voronoi(seed,3,1.9); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            a,b=vor_eval(pts,x,y); e=b[0]-a[0]
            base=[1,2,2,3,1,2][int(H(a[1],seed)*6)%6]
            t=base
            if e<0.9: g[y][x]=8+[3,4,3][r.randrange(3)]; rl[y][x]=3; continue
            if (a[2]+a[3])<-1.5: t+=1
            if r.random()<0.08: t+=1
            g[y][x]=clamp(t,0,4)
    for _ in range(5):
        x=r.randrange(1,15);y=r.randrange(1,15); g[y][x]=14; rl[y][x]=3
    return g,rl
def plank(seed=13):
    r=random.Random(seed); n=Noise(seed,8); g=blank(); rl=blank(2)
    for row in range(4):
        y0=row*4; shift=[0,1,0,-1][row] if False else [0,0,1,0][row]
        joint=(row*5+3)%N
        for ry in range(4):
            y=y0+ry
            for x in range(N):
                if ry==3: g[y][x]=1; rl[y][x]=1; continue
                v=n(x,y,0.6,2.2)+0.0
                t=3+(1 if v>0.6 else 0)-(1 if v<0.4 else 0)
                t+=shift
                if ry==0: t+=1
                if x==joint: t=1; rl[y][x]=1; g[y][x]=1; continue
                if x==(joint+1)%N: t+=1
                if x==(joint-1)%N: t-=1
                if r.random()<0.07: t=clamp(t+r.choice([-1,1]))
                g[y][x]=clamp(t,1,5)
                if rl[y][x]==2: rl[y][x]=3
        # grain streak
        gy=y0+1+r.randrange(2); gx=r.randrange(N)
        for k in range(r.randrange(3,6)):
            xx=(gx+k)%N
            if g[gy][xx]!=1: g[gy][xx]=clamp(g[gy][xx]-1)
        for dx in (-2,2):
            xx=(joint+dx)%N; g[y0+1][xx]=0
    return g,rl
def mosaic(seed=14):
    r=random.Random(seed); n=Noise(seed,8); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            cx=x//8; cy=y//8; horiz=((cx+cy)%2==0)
            lx=x%8; ly=y%8
            if horiz: slat=ly//4; pos=ly%4; along=lx
            else: slat=lx//4; pos=lx%4; along=ly
            base=[3,4,3,2][int(H(cx,cy,slat,seed)*4)%4]
            if pos==3: g[y][x]=1; rl[y][x]=1; continue
            t=base+(1 if pos==0 else 0)
            if r.random()<0.14: t+=r.choice([-1,1])
            if (along==0 or along==7) and pos<3: t-=1
            g[y][x]=clamp(t,1,5); rl[y][x]=3
    return g,rl
def shingle(seed=15):
    r=random.Random(seed); g=blank(); rl=blank(2)
    for y in range(N):
        row=y//4; r4=y%4; off=(row%2)*4
        for x in range(N):
            xx=(x+off)%8; sid=(row,(x+off)//8)
            shift=[0,0,1,-1,0][int(H(sid,seed)*5)%5]
            if xx==0 and r4>=1: g[y][x]=1; rl[y][x]=1; continue
            if r4==3 and xx==7: g[y][x]=1; rl[y][x]=1; continue
            t={0:2,1:3,2:4,3:3}[r4]+shift
            if r4==3 and xx in (1,6): t-=1
            if r.random()<0.1: t+=r.choice([-1,1])
            g[y][x]=clamp(t,1,5); rl[y][x]={0:1,1:2,2:3,3:3}[r4]
    return g,rl
def plaster(seed=16):
    r=random.Random(seed); n=Noise(seed,5); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            v=n(x,y); t=3+(1 if v>0.72 else 0)-(1 if v<0.28 else 0)
            q=r.random()
            if q<0.03: t-=1
            elif q<0.07: t+=1
            g[y][x]=clamp(t,2,5)
    return g,rl
def metal(seed=17,patina=0.0):
    r=random.Random(seed); n=Noise(seed,3); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            t=3
            if x==0 or y==0: t=6
            elif x==15 or y==15: t=1
            elif x==1 or y==1: t=5
            elif x==14 or y==14: t=2
            else:
                t=4 if (x+y)<12 else (3 if (x+y)<20 else 2)
                if y%2==1 and r.random()<0.35: t=clamp(t+r.choice([-1,1]))
                rl[y][x]=1
            g[y][x]=t
    for x in range(4,12):
        y=15-x-1+0
    for k in range(3,12):
        for (x,y) in [(k,13-k),(k,12-k)]:
            if 2<=x<=13 and 2<=y<=13:
                g[y][x]=6 if (x+y)==13 else 5
    for (x,y) in [(3,3),(12,3),(3,12),(12,12)]:
        g[y][x]=6; g[y+1][x+1]=1; rl[y][x]=4
    if patina>0:
        n2=Noise(seed+3,4)
        for y in range(2,14):
            for x in range(2,14):
                v=n2(x,y)*0.6+r.random()*0.4
                if v>1-patina and 0<=g[y][x]<=6: g[y][x]=8+g[y][x]
    return g,rl
def glass(seed=18,thin=False):
    # liso e sem moldura: tudo repete a cada 16 px (faixas diagonais com (x+y)%16), então blocos vizinhos se emendam sem marca
    g=blank(3); rl=blank(2)
    for y in range(N):
        for x in range(N):
            d=(x+y)%16
            t=3
            if d in (2,3,4,5): t=4
            if d==3 or d==4: t=5 if d==3 else 4
            if d in (11,12,13,14): t=2
            if d==9: t=5 if not thin else 4
            g[y][x]=t
    for (x,y) in [(5,3),(12,11)]:
        g[y][x]=6
    if not thin:
        for (x,y) in [(9,5),(2,12)]: g[y][x]=6
    return g,rl
def window(seed=19):
    g=blank(3); rl=blank(2)
    for y in range(N):
        for x in range(N):
            t=3
            fr=False
            if y==0 or x==0: t=5; fr=True
            elif y==15 or x==15: t=1; fr=True
            elif y==1: t=4; fr=True
            elif x==1: t=4; fr=True
            elif y==14 or x==14: t=2; fr=True
            elif x in (7,8) or y in (7,8):
                fr=True
                if x==7 and y not in (7,8): t=4
                elif x==8 and y not in (7,8): t=2
                elif y==7 and x not in (7,8): t=4
                elif y==8 and x not in (7,8): t=2
                else: t=3
            if not fr: t=11
            g[y][x]=t
    for (x0,y0) in [(2,2),(9,2),(2,9),(9,9)]:
        for ly in range(5):
            for lx in range(5):
                if lx==0 or ly==0: g[y0+ly][x0+lx]=9
        for (lx,ly) in [(3,1),(2,2),(1,3)]: g[y0+ly][x0+lx]=14
        for (lx,ly) in [(4,1),(3,2),(2,3),(1,4)]:
            if g[y0+ly][x0+lx]==11: g[y0+ly][x0+lx]=13
    return g,rl
def azplain(seed=20):
    r=random.Random(seed); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            if x%8==7 or y%8==7: g[y][x]=7; rl[y][x]=1; continue
            lx=x%8; ly=y%8; t=4
            if lx==0 or ly==0: t=5
            if lx==6 or ly==6: t=3
            if r.random()<0.06: t=clamp(t+r.choice([-1,1]))
            g[y][x]=t; rl[y][x]=3
    for ox in (0,8):
        for oy in (0,8):
            g[oy+1][ox+1]=6; g[oy+1][ox+2]=6; g[oy+2][ox+1]=6
    return g,rl
def azdecor(seed=21):
    g=blank(); rl=blank(2)
    motif=["b.....b",".b.c.b.","..bcb..",".cbabc.","..bcb..",".b.c.b.","b.....b"]
    for y in range(N):
        for x in range(N):
            if x%8==7 or y%8==7: g[y][x]=7; rl[y][x]=1; continue
            ch=motif[y%8][x%8]
            g[y][x]={'.':12,'a':1,'b':3,'c':5}[ch]
            rl[y][x]=3 if ch!='.' else 2
    return g,rl
def strip(seed=22):
    r=random.Random(seed); n=Noise(seed,8); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            v=n(x,y,1.0,0.28)
            t=3+(1 if v>0.6 else 0)-(1 if v<0.38 else 0)
            if r.random()<0.07: t=clamp(t+r.choice([-1,1]))
            g[y][x]=clamp(t,2,5)
    for x in (1,6,11):
        for y in range(N):
            if r.random()<0.7: g[y][(x+r.randrange(2))%N]=2; 
    kx,ky=10,6
    for (dx,dy) in [(0,0),(1,0),(0,1),(1,1),(0,2)]: g[ky+dy][kx+dx]=2
    g[ky+1][kx]=1
    for y in range(N):
        for x in range(N):
            if g[y][x]==2: rl[y][x]=1
    return g,rl
def rings(seed=23):
    r=random.Random(seed); n=Noise(seed,3); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            d=max(abs(x-7.5),abs(y-7.5))+(n(x,y)-0.5)*1.2
            k=int(d*0.75)%4
            t=[4,4,3,2][k]
            if d<1.2: t=2
            if x==0 or y==0: t=5
            if x==15 or y==15: t=2
            if r.random()<0.05 and 0<x<15 and 0<y<15: t=clamp(t+r.choice([-1,1]),2,5)
            g[y][x]=t; rl[y][x]=1 if t==2 else 2
    return g,rl


def ring2(g):
    for y in range(N):
        for x in range(N):
            if y==0 or x==0: g[y][x]=5
            elif y==15 or x==15: g[y][x]=1
            elif y==1 or x==1: g[y][x]=4
            elif y==14 or x==14: g[y][x]=2
def pane(g,x0,y0,w,h,glint=True):
    for ly in range(h):
        for lx in range(w):
            g[y0+ly][x0+lx]=9 if (lx==0 or ly==0) else 11
    if glint:
        for (lx,ly) in [(3,1),(2,2),(1,3)]:
            if lx<w and ly<h: g[y0+ly][x0+lx]=14
        for (lx,ly) in [(4,1),(3,2),(2,3),(1,4)]:
            if lx<w and ly<h and g[y0+ly][x0+lx]==11: g[y0+ly][x0+lx]=13
def jan_frameless(seed=40):
    g=blank(11); rl=blank(2)
    for y in range(N):
        for x in range(N):
            d=(x+y)%16; t=11
            if d in (3,4,5): t=12
            if d==4: t=13
            if d in (12,13,14): t=9
            g[y][x]=t
    for (x,y) in [(9,5),(2,12),(12,10)]: g[y][x]=14
    for (cx,cy) in [(0,0),(13,0),(0,13),(13,13)]:
        for dy in range(3):
            for dx in range(3):
                if (dx==0 and cx==0 and True) or True:
                    pass
        for dy in range(3):
            for dx in range(3):
                x=cx+dx;y=cy+dy
                g[y][x]=5 if (dx==0 or dy==0) else (1 if (dx==2 or dy==2) else 3)
        g[cy+1][cx+1]=6
    return g,rl
def jan_slide(seed=41):
    g=blank(11); rl=blank(2)
    for y in range(N):
        for x in range(N):
            if y==0 or x==0: g[y][x]=5
            elif y==15 or x==15: g[y][x]=1
            elif y==1 or y==14 or x==1 or x==14: g[y][x]=3
    pane(g,2,2,5,12); pane(g,9,2,5,12)
    for y in range(2,14): g[y][7]=4; g[y][8]=2
    for y in (6,7,8,9): g[y][9]=6; g[y][10]=1 if g[y][10]==11 else g[y][10]
    return g,blank(2)
def jan_colonial(seed=42):
    g=blank(3)
    ring2(g)
    for (x0,y0) in [(2,2),(6,2),(11,2),(2,9),(6,9),(11,9)]: pass
    # 3 colunas x 2 linhas: barras x=5,10 e y=7,8
    cols=[(2,3),(6,4),(11,3)]
    for y in range(2,14):
        for x in range(2,14): g[y][x]=11
    for y in range(2,14):
        g[y][5]=4; g[y][10]=3
    for x in range(2,14):
        g[7][x]=4; g[8][x]=2
    for (x0,x1) in [(2,5),(6,10),(11,14)]:
        for (y0,y1) in [(2,7),(9,14)]:
            for y in range(y0,y1):
                g[y][x0]=9 if g[y][x0]==11 else g[y][x0]
            for x in range(x0,x1):
                if g[y0][x]==11: g[y0][x]=9
            if g[y0+1][x0+1]==11: g[y0+1][x0+1]=14
            if g[y0+1][x0+2]==11: g[y0+1][x0+2]=13
            if g[y0+2][x0+1]==11: g[y0+2][x0+1]=13
    return g,blank(2)
def jan_louver(seed=43):
    g=blank(3); rl=blank(2)
    for y in range(N):
        for x in range(N):
            if x in (0,1,14,15):
                g[y][x]={0:5,1:4,14:2,15:1}[x]
                continue
            r4=y%4
            g[y][x]={0:5,1:3,2:2,3:9}[r4]
            if r4==3: g[y][x]=9
    return g,rl
def jan_porthole(seed=44):
    g=blank(3); rl=blank(2)
    for y in range(N):
        for x in range(N):
            dx=x-7.5; dy=y-7.5; r=math.hypot(dx,dy)
            if r<4.3:
                g[y][x]=11
            elif r<5.5:
                g[y][x]=3 if abs(dx+dy)<2 else (4 if dx+dy<0 else 2)
            elif r<6.8:
                g[y][x]=6 if dx+dy<-2 else (1 if dx+dy>2 else 5)
            else:
                g[y][x]=3
                if x==0 or y==0: g[y][x]=4
                if x==15 or y==15: g[y][x]=2
    for (x,y) in [(2,2),(13,2),(2,13),(13,13)]:
        g[y][x]=6; g[y+(1 if y<8 else -1)][x+(1 if x<8 else -1)]=1
    for y in range(N):
        for x in range(N):
            if g[y][x]==11:
                dx=x-7.5; dy=y-7.5
                if dx+dy<-1 and dx+dy>-3: g[y][x]=13
                if dx+dy<-3: g[y][x]=9
    g[5][5]=14; g[6][5]=14
    return g,blank(2)
def jan_bars(seed=45):
    g=blank(11)
    for y in range(N):
        for x in range(N):
            d=(x+y)%16; t=11
            if d in (3,4): t=12
            if d==4: t=13
            g[y][x]=t
    for bx in (2,7,12):
        for y in range(N): g[y][bx]=5; g[y][bx+1]=1
    for y in range(N):
        for x in range(N):
            pass
    for x in range(N):
        if g[7][x] in (11,12,13): g[7][x]=5
        if g[8][x] in (11,12,13): g[8][x]=1
    return g,blank(2)
def vitral(seed=46):
    r=random.Random(seed); pts=voronoi(seed,3,1.6)
    g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            a,b=vor_eval(pts,x,y); e=b[0]-a[0]
            if e<0.95: g[y][x]=7; continue
            k=int(H(a[1],seed)*4)%4
            base=[3,4,11,12][k]
            lit=(a[2]+a[3])
            if lit<-1.8: base+= 1 if base in (3,4,11) else 1
            elif lit>2.2: base-=1 if base in (4,12) else 0
            g[y][x]=base
    return g,rl
vt=[255]*16; vt[3]=185; vt[4]=200; vt[5]=222; vt[7]=255; vt[11]=185; vt[12]=200; vt[13]=222
wn2=[255]*16; wn2[9]=120; wn2[10]=90; wn2[11]=62; wn2[12]=95; wn2[13]=150; wn2[14]=215
# ===== texturas refeitas (v2): tudo repete a cada 16 px =====
def _panel(g,hi=5,lo=2):
    for i in range(N):
        g[0][i]=hi; g[i][0]=max(g[i][0],hi-1) if hi>1 else hi
        g[N-1][i]=lo; g[i][N-1]=min(g[i][N-1],lo+0) if False else lo
    g[0][N-1]=3; g[N-1][0]=3
def concrete(seed=1):
    r=random.Random(seed); n=Noise(seed,4); n2=Noise(seed+7,8); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            v=n(x,y)*0.6+n2(x,y)*0.4
            t=3+(1 if v>0.68 else 0)-(1 if v<0.32 else 0)
            q=r.random()
            if q<0.07: t-=1
            elif q<0.12: t+=1
            g[y][x]=clamp(t,2,4)
    for _ in range(6):
        x=r.randrange(2,13);y=r.randrange(2,13); g[y][x]=1; g[y][x+1]=2; rl[y][x]=1
    for (x,y) in [(4,4),(11,4),(4,11),(11,11)]:
        g[y][x]=1; g[y+1][x+1]=5; rl[y][x]=0
    _panel(g,5,2)
    return g,rl
def clay(seed=2):
    r=random.Random(seed); n=Noise(seed,4); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            v=n(x,y,0.5,3.0)
            t=3+(1 if v>0.6 else 0)-(1 if v<0.36 else 0)
            if r.random()<0.07: t+=r.choice([-1,1])
            g[y][x]=clamp(t,2,5)
    for _ in range(9):
        x=r.randrange(1,15);y=r.randrange(1,15); g[y][x]=1 if r.random()<0.5 else 5
    for y in (3,8,13):
        for x in range(N):
            if r.random()<0.7: g[y][x]=clamp(g[y][x]-1,1,5)
    _panel(g,5,2)
    return g,rl
def rough(seed):
    r=random.Random(seed); n=Noise(seed,3); n2=Noise(seed+5,6); g=blank(); rl=blank(2)
    v=[[n(x,y)*0.55+n2(x,y)*0.45 for x in range(N)] for y in range(N)]
    for y in range(N):
        for x in range(N):
            t=clamp(1+int(v[y][x]*5.6),1,5)
            d=v[y][x]-v[(y+1)%N][(x+1)%N]
            if d>0.07: t+=1
            elif d<-0.07: t-=1
            if r.random()<0.08: t+=r.choice([-1,1])
            g[y][x]=clamp(t,1,5)
    for _ in range(3):
        x=r.randrange(N);y=r.randrange(N)
        for _k in range(r.randrange(6,12)):
            g[y%N][x%N]=1; x+=r.choice([-1,0,1,1]); y+=r.choice([0,1,1])
    for y in range(N):
        for x in range(N):
            rl[y][x]=[0,1,2,3,3,4][g[y][x]]
    return g,rl
def smooth(seed=6):
    r=random.Random(seed); n=Noise(seed,4); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            d=(x+y)%16
            t=3+(1 if d in (5,6,7,8) else 0)+(1 if d in (6,7) else 0)
            if n(x,y)>0.7: t+=0
            if r.random()<0.05: t+=r.choice([-1,1])
            g[y][x]=clamp(t,2,5)
    for _ in range(2):
        x=r.randrange(2,12);y=r.randrange(2,14)
        for k in range(4): g[y][x+k]=clamp(g[y][x+k]-1,2,5)
    _panel(g,5,2)
    return g,rl
def speck(seed=7):
    r=random.Random(seed); n=Noise(seed,4); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            t=3+(1 if n(x,y)>0.62 else 0)-(1 if n(x,y)<0.35 else 0)
            q=r.random()
            if q<0.10: t=11
            elif q<0.17: t=1
            elif q<0.22: t=12
            elif q<0.28: t=5
            g[y][x]=t if t>5 else clamp(t,1,5)
            rl[y][x]=2
    return g,rl
def plaster(seed=16):
    r=random.Random(seed); n=Noise(seed,4); n2=Noise(seed+3,8); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            v=n(x,y)*0.6+n2(x,y)*0.4
            t=3+(1 if v>0.66 else 0)-(1 if v<0.33 else 0)
            if r.random()<0.06: t-=1
            g[y][x]=clamp(t,2,4)
    for _ in range(5):
        x=r.randrange(1,14);y=r.randrange(1,14); g[y][x]=4; g[y+1][x+1]=3
    return g,rl
def plank(seed=13):
    r=random.Random(seed); g=blank(); rl=blank(2)
    for b in range(4):
        base=[3,4,3,2][(b+seed)%4]; jx=(b*5+seed*3)%16
        for ry in range(4):
            y=b*4+ry
            for x in range(N):
                if ry==3: g[y][x]=1; rl[y][x]=0; continue
                t=base+(1 if ry==0 else 0)
                if r.random()<0.14: t+=r.choice([-1,1])
                if x==jx: t=1; rl[y][x]=1
                g[y][x]=clamp(t,1,5)
        for _ in range(2):
            gx=r.randrange(N); gy=b*4+r.randrange(1,3)
            for k in range(r.randrange(3,7)): g[gy][(gx+k)%N]=clamp(g[gy][(gx+k)%N]-1,1,5)
        for nx in ((jx+2)%16,(jx-2)%16): g[b*4+1][nx]=6; g[b*4+1][nx]=6
        g[b*4+1][(jx+2)%16]=1; g[b*4+1][(jx-2)%16]=1
    return g,rl
def mosaic(seed=14):
    r=random.Random(seed); g=blank(); rl=blank(2)
    for by in range(2):
        for bx in range(2):
            horiz=((bx+by)%2==0)
            for p in range(2):
                base=[3,4,3,2][int(H(bx,by,p,seed)*4)%4]
                for a in range(8):
                    for b in range(4):
                        if horiz: x=bx*8+a; y=by*8+p*4+b
                        else: x=bx*8+p*4+b; y=by*8+a
                        t=base+(1 if b==0 else 0)
                        if b==3: t=1
                        if r.random()<0.12: t+=r.choice([-1,1])
                        g[y][x]=clamp(t,1,5)
                        rl[y][x]=0 if b==3 else 2
    return g,rl
def shingle(seed=15):
    r=random.Random(seed); g=blank(); rl=blank(2)
    for y in range(N):
        row=y//4; ry=y%4; off=(row%2)*4
        for x in range(N):
            xx=(x+off)%8; bid=(row,(x+off)//8)
            base=[3,3,4,2,3][int(H(bid,seed)*5)%5]
            if ry==3: t=1; rl[y][x]=0
            elif xx==7: t=2; rl[y][x]=1
            else:
                t=base+(1 if ry==0 else 0)-(1 if ry==2 else 0)
                if xx==0: t+=1
                if ry==2 and xx in (0,6): t-=1
                if r.random()<0.12: t+=r.choice([-1,1])
                rl[y][x]=3 if ry<2 else 2
            g[y][x]=clamp(t,1,5)
    return g,rl
def metal(seed=17,patina=0.0):
    r=random.Random(seed); n=Noise(seed,3); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            if y==0 or x==0: t=6
            elif y==N-1 or x==N-1: t=1
            elif y==1 or x==1: t=5
            elif y==N-2 or x==N-2: t=2
            else:
                t=3+(1 if y%2==0 else 0)
                d=(x+y)%16
                if d in (6,7,8): t+=1
                if r.random()<0.18: t+=r.choice([-1,1])
                t=clamp(t,2,5); rl[y][x]=1
            g[y][x]=t
    for (x,y) in [(3,3),(12,3),(3,12),(12,12)]:
        g[y][x]=6; g[y+1][x+1]=1; rl[y][x]=4
    if patina>0:
        n2=Noise(seed+3,4)
        for y in range(2,14):
            for x in range(2,14):
                v=n2(x,y)*0.6+r.random()*0.4
                if v>1-patina and 0<=g[y][x]<=6: g[y][x]=8+g[y][x]
    return g,rl
def strip(seed=22):
    r=random.Random(seed); g=blank(); rl=blank(2)
    cols=[]; x=0
    while x<N:
        w=r.choice([2,3,3]); w=min(w,N-x); cols.append((x,w,r.choice([2,3,3,4]))); x+=w
    for (x0,w,b) in cols:
        for x in range(x0,x0+w):
            for y in range(N):
                t=b+(1 if x==x0 else 0)-(1 if x==x0+w-1 and w>1 else 0)
                if r.random()<0.10: t+=r.choice([-1,1])
                g[y][x]=clamp(t,1,5)
    for _ in range(5):
        y=r.randrange(N); x=r.randrange(N)
        for k in range(r.randrange(2,5)): g[y][(x+k)%N]=clamp(g[y][(x+k)%N]-1,1,5)
    return g,rl
def rings(seed=23):
    r=random.Random(seed); n=Noise(seed,3); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            d=math.hypot(x-7.5+(n(x,y)-0.5)*2.2,y-7.5+(n(y,x)-0.5)*2.2)
            k=int(d*1.15)%3
            t=[3,4,2][k]
            if d<1.6: t=1
            if r.random()<0.06: t+=r.choice([-1,1])
            g[y][x]=clamp(t,1,5)
    for i in range(N):
        g[0][i]=2; g[N-1][i]=2; g[i][0]=2; g[i][N-1]=2
    return g,rl
def sandside(seed=11):
    r=random.Random(seed); g=blank(); rl=blank(2)
    bands=[4,4,4,3,3,2,4,4,3,3,3,2,4,4,4,3]
    for y in range(N):
        for x in range(N):
            t=bands[y]
            if r.random()<0.12: t+=r.choice([-1,1])
            g[y][x]=clamp(t,1,5)
    for y in (5,11,15):
        for x in range(N): g[y][x]=2 if r.random()<0.8 else 3
    return g,rl
def pillar(seed=10):
    r=random.Random(seed); g=blank(); rl=blank(2)
    prof=[2,4,5,3]
    for y in range(N):
        for x in range(N):
            t=prof[x%4]
            if y<2: t=5 if y==0 else 4
            elif y==2: t=2
            elif y==N-3: t=2
            elif y>=N-2: t=4 if y==N-2 else 3
            elif r.random()<0.07: t=clamp(t+r.choice([-1,1]),1,5)
            g[y][x]=t
    return g,rl
def obsidian(seed=12):
    r=random.Random(seed); n=Noise(seed,4); g=blank(1); rl=blank(2)
    for y in range(N):
        for x in range(N):
            t=1+(1 if n(x,y)>0.6 else 0)
            d=(x+y)%8
            if d==0: t=3
            elif d==1: t=2
            if (x*7+y*3)%13==0: t=11
            g[y][x]=t
    return g,rl
def chisel(seed=9):
    r=random.Random(seed); g=blank(); rl=blank(2)
    for y in range(N):
        for x in range(N):
            ring=min(x,y,N-1-x,N-1-y)
            if ring==0: t=5 if (y==0 or x==0) else 1
            elif ring==1: t=4 if (y==1 or x==1) else 2
            elif ring in (2,3): t=3
            elif ring==4: t=2 if (y==4 or x==4) else 5
            else:
                t=3
                if abs(x-7.5)+abs(y-7.5)<3: t=2
                if abs(x-7.5)+abs(y-7.5)<2: t=4
            if r.random()<0.06: t=clamp(t+r.choice([-1,1]),1,5)
            g[y][x]=t
            rl[y][x]=[2,3,2,2,1,2,2,2][min(ring,7)] if ring<5 else 2
    return g,rl

PATS=[]  # (name, fn, alpha)
def add(name,fn,alpha=None): PATS.append((name,fn,alpha))
add('CONCRETE',lambda:concrete())
add('CLAY',lambda:clay())
add('BRICK',lambda:brick())
add('SBRICK',lambda:sbrick(4))
add('SBRICK_CRACK',lambda:sbrick(4,crack=True))
add('SBRICK_MOSS',lambda:sbrick(4,moss=True))
add('COBBLE',lambda:cobble(5))
add('COBBLE_MOSS',lambda:cobble(5,moss=True))
add('ROUGH0',lambda:rough(31)); add('ROUGH1',lambda:rough(32)); add('ROUGH2',lambda:rough(33))
add('SMOOTH',lambda:smooth())
add('SPECK',lambda:speck())
add('TILES',lambda:tiles())
add('CHISEL',lambda:chisel())
add('PILLAR',lambda:pillar())
add('SANDSIDE',lambda:sandside())
add('OBSIDIAN',lambda:obsidian())
add('PLANK',lambda:plank())
add('MOSAIC',lambda:mosaic())
add('SHINGLE',lambda:shingle())
add('PLASTER',lambda:plaster())
add('METAL',lambda:metal())
add('METAL_P1',lambda:metal(17,0.22))
add('METAL_P2',lambda:metal(17,0.55))
GA=[255]*16
gl=[255]*16; gl[1]=235; gl[6]=235; gl[3]=70; gl[4]=105; gl[2]=130; gl[5]=170
# per-slot alpha for glass: rim light(6) & dark(1) opaque-ish, body 3 low
gl=[255,235,128,100,128,165,215]+[255]*9
add('GLASS',lambda:glass(),gl)
wn=[255]*16; wn[8]=255; wn[9]=120; wn[10]=90; wn[11]=62; wn[12]=95; wn[13]=150; wn[14]=215
add('WINDOW',lambda:window(),wn)
add('AZ_PLAIN',lambda:azplain())
add('AZ_DECOR',lambda:azdecor())
add('STRIP',lambda:strip())
add('RINGS',lambda:rings())
glt=[255,235,50,38,62,105,170]+[255]*9
add('GLASS_THIN',lambda:glass(thin=True),glt)
add('JAN_FRAMELESS',lambda:jan_frameless(),wn2)
add('JAN_SLIDE',lambda:jan_slide(),wn2)
add('JAN_COLONIAL',lambda:jan_colonial(),wn2)
add('JAN_LOUVER',lambda:jan_louver(),wn2)
add('JAN_PORTHOLE',lambda:jan_porthole(),wn2)
add('JAN_BARS',lambda:jan_bars(),wn2)
add('VITRAL',lambda:vitral(),vt)

def emit():
    out=[]
    names=[]
    P=[];R=[];A=[]
    for i,(nm,fn,al) in enumerate(PATS):
        g,rl=fn()
        P.append('"'+'","'.join(''.join(hx(v) for v in row) for row in g)+'"')
        R.append('"'+'","'.join(''.join(str(v) for v in row) for row in rl)+'"')
        A.append(al)
        names.append((nm,i))
    return names,P,R,A
if __name__=='__main__':
    names,P,R,A=emit()
    print(len(names))
