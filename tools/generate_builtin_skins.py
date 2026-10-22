#!/usr/bin/env python3
"""Generate original, offline-ready 64x64 Minecraft-style skins for CuboLand.
All artwork is drawn pixel-by-pixel here; no third-party skin files are bundled.
"""
from PIL import Image, ImageDraw
from pathlib import Path

OUT = Path("app/src/main/assets/preset_skins")
OUT.mkdir(parents=True, exist_ok=True)
# file, label, skin, hair, hair highlight, outfit, highlight, shadow, pants, boots, eyes, accent, style
SKINS = [
("01_crimson_mage","Mago Carmesim",(224,174,139),(42,24,31),(101,35,48),(105,20,38),(220,61,71),(45,16,29),(48,33,49),(29,22,32),(245,194,69),(255,127,54),"mage"),
("02_void_knight","Cavaleiro do Vazio",(185,145,120),(25,26,42),(67,66,100),(36,35,54),(100,76,155),(17,19,31),(31,29,43),(19,20,31),(173,103,255),(77,225,238),"armor"),
("03_cyber_ninja","Ninja Cibernético",(224,185,157),(24,32,42),(40,108,126),(28,49,66),(42,180,192),(15,25,39),(22,36,53),(12,22,34),(66,235,230),(255,75,120),"ninja"),
("04_royal_mage","Maga Imperial",(246,205,177),(231,196,125),(255,232,173),(50,42,100),(112,92,183),(26,25,62),(45,37,80),(28,25,53),(71,164,235),(255,218,100),"royal"),
("05_forest_ranger","Patrulheiro Élfico",(197,151,113),(72,45,28),(133,86,43),(47,86,57),(106,146,78),(25,51,39),(66,61,48),(52,37,27),(177,218,112),(219,183,93),"ranger"),
("06_gothic_rose","Gótica Carmesim",(239,192,178),(35,22,36),(100,35,63),(67,29,56),(152,48,86),(29,20,38),(42,28,47),(25,19,31),(229,95,151),(218,191,206),"goth"),
("07_frost_guardian","Guardião Glacial",(225,190,166),(200,223,237),(245,253,255),(42,98,130),(113,203,226),(25,53,87),(37,70,99),(22,45,68),(94,217,255),(237,253,255),"frost"),
("08_streetwear","Streetwear Neon",(178,124,91),(22,23,31),(84,83,105),(39,42,53),(70,75,93),(19,21,29),(32,34,44),(16,17,23),(115,245,155),(251,78,177),"street"),
("09_sun_paladin","Paladino Solar",(224,172,132),(103,63,32),(174,114,52),(163,115,38),(242,198,80),(83,53,25),(67,53,38),(43,32,24),(102,199,255),(255,242,170),"paladin"),
("10_shadow_assassin","Assassino Sombrio",(199,157,132),(19,19,27),(58,57,77),(31,31,40),(73,73,93),(12,13,19),(25,25,34),(13,13,19),(255,57,70),(167,50,69),"assassin"),
]

def shade(c, f):
    return tuple(max(0, min(255, int(v*f))) for v in c)

def draw_skin(s):
    name,label,skin,hair,hh,cloth,light,dark,pants,boots,eye,accent,style=s
    im=Image.new("RGBA",(64,64),(0,0,0,0)); d=ImageDraw.Draw(im)
    def patch(box, base, detail=True):
        x0,y0,x1,y1=box
        for y in range(y0,y1):
            for x in range(x0,x1):
                fac=(1.10 if x==x0 else .80 if x==x1-1 else 1.0)
                fac*=1.07 if y==y0 else .84 if y==y1-1 else 1.0
                d.point((x,y),fill=shade(base,fac))
        if detail and x1-x0>=4 and y1-y0>=5:
            d.point((x0+1,y0+2),fill=shade(base,1.18))
            d.point((x1-2,y1-2),fill=shade(base,.68))
    # Head base, Minecraft UV layout: face x8-15/y8-15.
    for b,c in [((8,8,16,16),skin),((24,8,32,16),shade(skin,.83)),((0,8,8,16),hair),((16,8,24,16),hair),((8,0,16,8),hair),((16,0,24,8),shade(skin,.8))]:
        patch(b,c,False)
    patch((24,8,32,12),hair); patch((24,12,32,16),hh)
    patch((8,8,16,10),hair,False)
    for x in range(8,16):
        if (x+len(style))%3==0: d.point((x,10),fill=hh)
    for x in (9,13):
        d.point((x,11),fill=shade(hair,.65)); d.point((x,12),fill=eye); d.point((x,13),fill=shade(eye,.65))
    d.point((11,14),fill=shade(skin,.7)); d.point((12,14),fill=shade(skin,.7))
    if style in ("mage","royal","goth","frost"): d.point((11,15),fill=shade(accent,.8))
    for x in range(8,16): d.point((x,1+x%2),fill=hh if x%3==0 else hair)
    # Torso, sleeves and legs. Each region follows the standard 64x64 UV atlas.
    for b,c in [((20,20,28,32),cloth),((32,20,40,32),dark),((16,20,20,32),dark),((28,20,32,32),light),((20,16,28,20),light),((28,16,36,20),dark)]:
        patch(b,c)
    if style in ("mage","royal","goth"):
        d.rectangle((23,20,24,31),fill=accent); d.rectangle((21,20,26,21),fill=light)
        for y in (23,26,29): d.point((24,y),fill=(255,240,183))
    elif style in ("armor","paladin","frost"):
        d.rectangle((20,21,27,25),fill=light); d.rectangle((22,21,25,24),fill=cloth)
        d.rectangle((23,25,24,30),fill=accent); d.rectangle((20,30,27,31),fill=dark)
    elif style in ("ninja","assassin"):
        d.rectangle((20,22,27,24),fill=dark); d.rectangle((20,27,27,28),fill=dark)
        d.rectangle((24,23,25,27),fill=accent); d.rectangle((21,30,26,31),fill=light)
    elif style=="ranger":
        d.line((20,21,27,29),fill=accent,width=1); d.rectangle((22,25,25,27),fill=dark)
    else:
        d.rectangle((20,21,27,22),fill=light); d.rectangle((20,29,27,30),fill=accent)
    arms=[((44,20,48,32),(52,20,56,32),(40,20,44,32),(48,20,52,32),(44,16,48,20),(48,16,52,20)),
          ((36,52,40,64),(44,52,48,64),(32,52,36,64),(40,52,44,64),(36,48,40,52),(40,48,44,52))]
    for front,back,side1,side2,top,bottom in arms:
        for b,c in [(front,cloth),(back,dark),(side1,dark),(side2,light),(top,light),(bottom,skin)]: patch(b,c)
        x0,y0,x1,y1=front; d.rectangle((x0,y0+1,x1-1,y0+2),fill=accent); d.rectangle((x0,y1-3,x1-1,y1-2),fill=dark)
    legs=[((4,20,8,32),(12,20,16,32),(0,20,4,32),(8,20,12,32),(4,16,8,20),(8,16,12,20)),
          ((20,52,24,64),(28,52,32,64),(16,52,20,64),(24,52,28,64),(20,48,24,52),(24,48,28,52))]
    for front,back,side1,side2,top,bottom in legs:
        for b,c in [(front,pants),(back,shade(pants,.8)),(side1,shade(pants,.85)),(side2,shade(pants,1.15)),(top,light),(bottom,boots)]: patch(b,c)
        x0,y0,x1,y1=front; d.rectangle((x0,y1-3,x1-1,y1-1),fill=boots)
        if style in ("armor","paladin","frost"): d.rectangle((x0,y0+2,x1-1,y0+4),fill=light)
    # Outer head: hoods, hair accessories, crowns and masks.
    if style in ("mage","royal","frost","paladin"):
        patch((40,0,48,3),accent,False); patch((40,3,48,8),hair,False)
        d.rectangle((42,1,45,2),fill=light)
    elif style in ("ninja","assassin"):
        patch((40,0,48,8),dark,False); d.rectangle((42,3,45,4),fill=eye)
    elif style=="goth":
        patch((40,0,48,8),hair,False); d.rectangle((41,0,42,2),fill=accent); d.rectangle((45,0,46,2),fill=accent)
    else:
        patch((40,0,48,4),hh,False); patch((40,4,48,8),hair,False)
    # Outer torso/cape and distinct shoulder details.
    if style in ("mage","royal","goth","frost","paladin"):
        patch((20,32,28,44),dark,False); patch((20,32,22,44),accent,False); patch((26,32,28,44),light,False)
        for y in range(34,43):
            for x in range(23,26): im.putpixel((x,y),(0,0,0,0))
    elif style in ("armor","assassin"):
        patch((20,32,28,35),light,False); patch((20,35,28,44),dark,False); d.rectangle((22,36,25,40),fill=accent)
    elif style=="ranger":
        patch((20,32,28,44),dark,False); d.line((20,33,27,42),fill=accent,width=1)
    else:
        patch((20,32,28,35),light,False); patch((20,35,28,44),dark,False)
    for box in [(44,32,48,44),(52,32,56,44),(52,48,56,60),(60,48,64,60)]:
        patch(box,light if style in ("armor","paladin","frost") else dark,False)
        x0,y0,x1,y1=box; d.rectangle((x0,y0+2,x1-1,y0+3),fill=accent)
    for box in [(4,32,8,44),(12,32,16,44),(4,48,8,60),(12,48,16,60)]:
        if style in ("armor","paladin","frost","assassin"):
            patch(box,light if style!="assassin" else dark,False)
            x0,y0,x1,y1=box; d.rectangle((x0,y0+2,x1-1,y0+3),fill=accent)
    if style=="mage": d.rectangle((20,32,27,34),fill=accent); d.rectangle((22,35,25,39),fill=light)
    if style=="ninja": d.rectangle((20,32,27,34),fill=dark); d.rectangle((22,35,25,37),fill=accent)
    if style=="ranger": d.rectangle((20,32,27,33),fill=accent); d.point((24,36),fill=light)
    if style=="street": d.rectangle((20,32,27,34),fill=accent); d.rectangle((22,35,25,38),fill=light)
    return im

for skin in SKINS:
    draw_skin(skin).save(OUT / (skin[0]+".png"), optimize=True)
print("Generated", len(SKINS), "original 64x64 skins in", OUT)
