import pickle,sys,statistics as st
T=pickle.load(open("tex.pkl","rb"))
def lum(c): return c[0]*.299+c[1]*.587+c[2]*.114
def Lg(nm): return [[lum(T[nm][y*16+x]) for x in range(16)] for y in range(16)]
def joints(L,band,thr=0.13):
    cm=[sum(L[y][x] for y in band)/len(band) for x in range(16)]
    med=st.median(cm); js=[]
    for x in range(16):
        if abs(cm[x]-med)>thr*max(med,40) and abs(cm[x]-cm[(x-1)%16])>thr*max(med,40)*0.6: js.append(x)
    # fuse neighbours: keep the most extreme
    out=[]
    for x in js:
        if out and x-out[-1]<=1:
            if abs(cm[x]-med)>abs(cm[out[-1]]-med): out[-1]=x
        else: out.append(x)
    return out
def layout(nm):
    g=[[0]*16 for _ in range(16)]
    L=Lg(nm)
    if nm=="brick": rows=[3,7,11,15]; J=[(3,0,2),(11,0,2),(7,4,6),(15,4,6),(3,8,10),(11,8,10),(7,12,14),(15,12,14)]
    elif (nm.startswith("planks_") or nm.endswith("_planks")) and "bamboo" not in nm:
        rows=[3,7,11,15]; J=[(7,4,6),(7,12,14)]
    elif nm in ("nether_brick","red_nether_brick","cracked_nether_bricks","resin_bricks"):
        rows=[3,7,11,15]; J=[]
    elif nm in ("stonebrick","stonebrick_mossy","stonebrick_cracked","deepslate_bricks","cracked_deepslate_bricks","polished_blackstone_bricks","cracked_polished_blackstone_bricks","tuff_bricks","mud_bricks","end_bricks","quartz_bricks"):
        rows=[7,15]; J=[]
        lo,up={"mud_bricks":([4,12],[0,8]),"end_bricks":([8],[15]),"quartz_bricks":([3,11],[15])}.get(nm,([7],[15]))
        for x in lo: J.append((x,8,14))
        for x in up: J.append((x,0,6))
    else: return None
    for r in rows:
        for x in range(16): g[r][x]=1
    for x,a,b in J:
        for y in range(a,b+1): g[y][x]=1
    return g
def crack(nm):
    L=Lg(nm); g=[[0]*16 for _ in range(16)]
    flat=[v for r in L for v in r]; m=sum(flat)/256
    dark=[[L[y][x]<m*0.80 for x in range(16)] for y in range(16)]
    seen=set()
    for y in range(16):
        for x in range(16):
            if dark[y][x] and (x,y) not in seen:
                comp=[(x,y)]; seen.add((x,y)); i=0
                while i<len(comp):
                    cx,cy=comp[i]; i+=1
                    for dx,dy in((1,0),(-1,0),(0,1),(0,-1)):
                        n=((cx+dx)%16,(cy+dy)%16)
                        if dark[n[1]][n[0]] and n not in seen: seen.add(n); comp.append(n)
                if len(comp)>=5:
                    for cx,cy in comp: g[cy][cx]=1
    return g
def groove(nm):
    L=Lg(nm); g=[[0]*16 for _ in range(16)]
    flat=[v for r in L for v in r]; m=sum(flat)/256
    dark=[[L[y][x]<m*0.78 for x in range(16)] for y in range(16)]
    for x in range(16):
        y=0
        while y<16:
            if dark[y][x]:
                e=y
                while e+1<16 and dark[e+1][x]: e+=1
                if e-y+1>=5 and not (dark[y][(x-1)%16] and dark[y][(x+1)%16]):
                    for k in range(y,e+1): g[k][x]=1
                y=e+1
            else: y+=1
    return g
def spec(nm):
    g=layout(nm)
    if g: return g
    return None
if __name__=="__main__":
    from PIL import Image
    names=[n for n in sys.argv[1:] if spec(n)]
    sc=6; w=16*sc*2+8; cols=8
    im=Image.new("RGB",(cols*w,((len(names)+cols-1)//cols)*16*sc),(40,40,40))
    for n,nm in enumerate(names):
        g=spec(nm); ox=(n%cols)*w; oy=(n//cols)*16*sc
        for y in range(16):
            for x in range(16):
                c=T[nm][y*16+x][:3]
                for dy in range(sc):
                    for dx in range(sc):
                        im.putpixel((ox+x*sc+dx,oy+y*sc+dy),c)
                        im.putpixel((ox+16*sc+x*sc+dx,oy+y*sc+dy),(255,0,255) if g[y][x] else tuple(v//2 for v in c))
    im.save("ov.png"); print(len(names),names)
