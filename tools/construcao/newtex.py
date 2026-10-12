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
            t=3+(1 if v>0.60 else 0)-(1 if v<0.40 else 0)
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
        x=r.randrange(1,14);y=r.randrange(1,14); g[y][x]=5; g[y+1][x+1]=2
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
