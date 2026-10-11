import re,sys
from PIL import Image, ImageDraw
src=open('../app/src/main/java/com/cuboland/app/NatArt.kt').read()
names=re.findall(r'const val (\w+) = (\d+)',src)
names={int(v):n for n,v in names}
arts=[]
for m in re.finditer(r'Art\(intArrayOf\((.*?)\),\s*arrayOf\((.*?)\)\)',src,re.S):
    pal=[int(x,16)&0xFFFFFF for x in re.findall(r'0x([0-9A-Fa-f]{8})',m.group(1))]
    rows=re.findall(r'"([^"]{16})"',m.group(2))
    arts.append((pal,rows))
print(len(arts),len(names))
if __name__=='__main__':
    S=8
    cols=8
    n=len(arts)
    im=Image.new('RGB',(cols*(16*S+8),((n+cols-1)//cols)*(16*S+20)),(40,40,40))
    d=ImageDraw.Draw(im)
    for i,(pal,rows) in enumerate(arts):
        ox=(i%cols)*(16*S+8); oy=(i//cols)*(16*S+20)
        for y in range(16):
            for x in range(16):
                ch=rows[y][x]
                if ch=='.': c=(255,0,255)
                else: c=tuple(divmod(pal[int(ch,16)],1)) if False else ((pal[int(ch,16)]>>16)&255,(pal[int(ch,16)]>>8)&255,pal[int(ch,16)]&255)
                d.rectangle([ox+x*S,oy+y*S,ox+x*S+S-1,oy+y*S+S-1],fill=c)
        d.text((ox,oy+16*S+2),f"{i}{names.get(i,'')[:14]}",fill=(255,255,255))
    im.save('sheet.png')
