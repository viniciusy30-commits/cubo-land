import math, random
cmds=[]; vox={}
AIR="-"
def _fill(n,a,b,c,d,e,f):
    for y in range(min(b,e),max(b,e)+1):
        for z in range(min(c,f),max(c,f)+1):
            for x in range(min(a,d),max(a,d)+1): vox[(x,y,z)]=n
def F(n,x1,y1,z1,x2,y2,z2): cmds.append(f"F|{n}|{x1}|{y1}|{z1}|{x2}|{y2}|{z2}"); _fill(n,x1,y1,z1,x2,y2,z2)
def H(x1,y1,z1,x2,y2,z2): F(AIR,x1,y1,z1,x2,y2,z2)
def P(n,x,y,z): cmds.append(f"P|{n}|{x}|{y}|{z}"); vox[(x,y,z)]=n
def R(n,d,x,y,z): cmds.append(f"R|{n}|{d}|{x}|{y}|{z}"); vox[(x,y,z)]=n+"#"+str(d)
def T(x,z,t,wood="",leaf=""): cmds.append(f"T|{x}|{z}|{t}|{wood}|{leaf}")
def L(x,y,z,big,txt): cmds.append(f"L|{x}|{y}|{z}|{big}|{txt}")
def disc(n,cx,cz,r,y1,y2=None,rin=-1):
    y2=y1 if y2 is None else y2
    for z in range(int(cz-r)-1,int(cz+r)+2):
        for x in range(int(cx-r)-1,int(cx+r)+2):
            d=math.hypot(x-cx,z-cz)
            if rin<=d<=r:
                for y in range(y1,y2+1): P(n,x,y,z)
W="Concreto Branco"; K="Concreto Preto"; G="Concreto Cinza"; TRIM="Pilar de Quartzo"; QZ="Quartzo"
WOOD="Tábua"; SLATE="Ladrilhos de Ardósia"; LJ="Laje de Concreto Branco"; GL="Vidro"; GRASS="Grama"
LAMP="Bloco Luminoso Amarelo"; LAMPW="Bloco Luminoso Branco"; PLINTH="Tijolos de Pedra"; BARS="Janela Gradeada"
TILE="Concreto Azul-claro"; STONE="Pedra Lisa"; LEAF="Folhas"; ROOF="Telhado Preto"
GX0,GX1,GZ0,GZ1=-40,96,-34,82
# ================= lote =================
F(GRASS,GX0,-1,GZ0,GX1,-1,GZ1)
# muro com pilares, grades e portão aberto
for x in range(GX0,GX1+1):
    for z in (GZ0,GZ1):
        F(PLINTH,x,0,z,x,0,z); F(BARS,x,1,z,x,3,z)
for z in range(GZ0,GZ1+1):
    for x in (GX0,GX1):
        F(PLINTH,x,0,z,x,0,z); F(BARS,x,1,z,x,3,z)
pil=[]
for x in range(GX0,GX1+1,8): pil+= [(x,GZ0),(x,GZ1)]
for z in range(GZ0,GZ1+1,8): pil+= [(GX0,z),(GX1,z)]
for (x,z) in pil:
    F(QZ,x,0,z,x,4,z); P(LAMP,x,5,z)
H(26,0,GZ0,34,3,GZ0)
for (xa,xb) in ((22,23),(37,38)):
    F(QZ,xa,0,GZ0-1,xb,5,GZ0); F(TRIM,xa,6,GZ0-1,xb,6,GZ0); P(LAMP,xa,7,GZ0-1); P(LAMP,xb,7,GZ0-1)
for x in list(range(24,26))+list(range(35,37)): F(PLINTH,x,0,GZ0,x,0,GZ0); F(BARS,x,1,GZ0,x,3,GZ0)
# folhas das duas bandas do portão abertas pra dentro
for x in (24,36):
    F(BARS,x,1,GZ0+1,x,3,GZ0+4)
L(30,9,GZ0,1,"MANSÃO")
# ================= acesso: rua, rotatória, chafariz =================
CX,CZ=30,-20
F(G,26,-1,GZ0,34,-1,-8)
disc(G,CX,CZ,12.5,-1,-1,8.5)
F(SLATE,26,-1,-8,34,-1,-1)
disc(GRASS,CX,CZ,8.4,-1)
disc(QZ,CX,CZ,4.6,-3,-2)
disc(QZ,CX,CZ,4.5,-1,0,3.4)
disc("Água",CX,CZ,3.3,-1)
F(QZ,CX,-1,CZ,CX,2,CZ); P("Bloco Luminoso Azul-claro",CX,3,CZ)
for k in range(8):
    a=k*math.pi/4+0.2
    px=round(CX+7*math.cos(a)); pz=round(CZ+7*math.sin(a))
    F("Folhas de Cerejeira" if k%2==0 else "Folhas Amarelas",px,0,pz,px,0,pz)
# postes da rotatória
for k in range(12):
    a=k*math.pi/6
    px=round(CX+13.8*math.cos(a)); pz=round(CZ+13.8*math.sin(a))
    if pz>-6: continue
    F(K,px,0,pz,px,2,pz); P("Lanterna",px,3,pz)
for z in range(GZ0+3,-8,6):
    for x in (25,35): F(K,x,0,z,x,2,z); P("Lanterna",x,3,z)
# ================= casa =================
X0,X1,Z0,Z1=8,52,0,26
F(WOOD,X0,-1,Z0,X1,-1,Z1)
F(QZ,25,-1,1,35,-1,12); F(QZ,25,-1,14,35,-1,25)
F(W,X0,0,Z0,X1,8,Z0); F(W,X0,0,Z1,X1,8,Z1); F(W,X0,0,Z0,X0,8,Z1); F(W,X1,0,Z0,X1,8,Z1)
F(PLINTH,X0,-1,Z0,X1,-1,Z0)   # frisos
# laje do 2º andar (vazado: foyer e escada)
F(WOOD,X0,4,Z0,X1,4,Z1)
H(25,4,1,35,4,12)
F(QZ,25,4,7,27,4,12); F(QZ,28,4,10,35,4,12)
# teto/cobertura
F(W,X0-2,9,Z0-2,X1+2,9,Z1+2)
# telhado mansarda de ardósia em volta (3 degraus) + chaminés
for k in range(3):
    xa,xb,za,zb,y=X0-2+k,X1+2-k,Z0-2+k,Z1+2-k,10+k
    for x in range(xa+1,xb): R(ROOF,0,x,y,za); R(ROOF,1,x,y,zb)
    for z in range(za+1,zb): R(ROOF,2,xa,y,z); R(ROOF,3,xb,y,z)
    for (x,z) in ((xa,za),(xb,za),(xa,zb),(xb,zb)): P(K,x,y,z)
for (cx,cz) in ((14,12),(46,12),(30,20)):
    F("Tijolo",cx,10,cz,cx+1,16,cz+1); F(K,cx,17,cz,cx+1,17,cz+1)
# fachada frontal: pilastras, janelas, foyer envidraçado
for x in (8,13,18,24,36,42,47,52): F(TRIM,x,0,Z0,x,8,Z0)
bays=[(9,12),(14,17),(19,23),(37,41),(43,46),(48,51)]
for (a,b) in bays:
    F(GL,a,1,Z0,b,2,Z0); F(GL,a,6,Z0,b,7,Z0)
F(GL,25,0,Z0,35,8,Z0)
for x in (25,28,32,35): F(K,x,0,Z0,x,8,Z0)
F(K,25,3,Z0,35,3,Z0); F(K,25,4,Z0,35,4,Z0)
H(29,0,Z0,31,2,Z0)
# pórtico com colunas e frontão
F(SLATE,22,-1,-7,38,-1,-1)
for x in (23,26,34,37): F(TRIM,x,0,-6,x,3,-6)
F(W,22,4,-7,38,4,-1)
for k in range(8):   # frontão triangular, 2 de profundidade
    for z in (-7,-6):
        R(ROOF,2,22+k,5+k,z); R(ROOF,3,37-k,5+k,z)
        for y in range(5,5+k):
            P(W,22+k,y,z); P(W,37-k,y,z)
P(LAMP,30,5,-7)
for x in (23,26,34,37): P(LAMP,x,4,-7)
# sacadas laterais (suíte master e quarto 2)
for (xa,xb) in ((10,22),(38,50)):
    F(W,xa,4,-4,xb,4,-1)
    F(GL,xa,5,-4,xb,6,-4); F(GL,xa,5,-4,xa,6,-1); F(GL,xb,5,-4,xb,6,-1)
    F(TRIM,xa,0,-4,xa,3,-4); F(TRIM,xb,0,-4,xb,3,-4)
    F(SLATE,xa,-1,-4,xb,-1,-1)
H(15,5,Z0,16,6,Z0); H(43,5,Z0,44,6,Z0)
# janelas laterais e dos fundos
for y0 in (1,6):
    for z in (3,8,16,21):
        F(GL,X0,y0,z,X0,y0+1,z+2); F(GL,X1,y0,z,X1,y0+1,z+2)
    for x in (10,14,18,38,42,46,50):
        F(GL,x,y0,Z1,x+2,y0+1,Z1)
F(GL,30,1,Z1,35,2,Z1)
F(GL,26,0,Z1,29,2,Z1)   # portas de vidro pra varanda
for x in (26,30,36): F(K,x,0,Z1,x,2,Z1)
H(27,0,Z1,28,2,Z1)
for y in range(5,9): F(GL,25,y,Z1,35,y,Z1)
for x in (25,28,32,35): F(K,x,5,Z1,x,8,Z1)
# paredes internas térreo
F(W,24,0,1,24,3,25); F(W,36,0,1,36,3,25)
H(24,0,5,24,2,6); H(36,0,5,36,2,6); H(24,0,18,24,2,19); H(36,0,18,36,2,19)
F(W,9,0,13,23,3,13); H(15,0,13,17,2,13)
F(W,37,0,13,51,3,13); H(43,0,13,45,2,13)
F(W,25,0,13,35,3,13); H(28,0,13,32,2,13)
# paredes internas andar de cima
F(W,24,5,1,24,8,25); F(W,36,5,1,36,8,25)
H(24,5,9,24,6,10); H(24,5,19,24,6,20); H(36,5,11,36,6,12); H(36,5,19,36,6,20)
F(W,9,5,13,23,8,13); H(12,5,13,13,6,13)
F(W,37,5,13,51,8,13)
F(W,44,5,14,44,8,25); H(44,5,18,44,6,19)
F(W,16,5,14,16,8,25); H(16,5,19,16,6,20)
for z in (13,): F(GL,25,5,z,35,6,z)
H(25,5,13,27,8,13)
F(GL,28,5,10,35,6,10); F(GL,28,5,7,28,6,9)
F(QZ,26,4,13,26,4,13)
# escada de concreto
st=0
for s in range(5):
    R("Escada de Concreto Branco",0,26,s,2+s); R("Escada de Concreto Branco",0,25,s,2+s); R("Escada de Concreto Branco",0,27,s,2+s)
    for k in range(s):
        for x in (25,26,27): P(W,x,k,2+s)
# ============ MOBÍLIA térreo ============
# foyer
F("Lã Vermelha",29,-1,1,31,-1,11)
for x in (29,30,31): P("Aparador Ébano",x,0,1)
P("Vaso de Flores Rosas Vermelhas",30,1,1); P("Candelabro Dourado",29,1,1); P("Candelabro Dourado",31,1,1)
P("Palmeira em Vaso Branco",28,0,1); P("Palmeira em Vaso Branco",35,0,1)
P("Palmeira em Vaso Branco",35,0,12); P("Samambaia Branco",28,0,9)
P("Relógio de Pé Nogueira",35,0,2); P("Aparador Ébano",35,0,8); P("Globo Dourado",35,1,8); P("Quadro Pôr do Sol Dourado",35,2,9)
P("Quadro Noite Estrelada Dourado",35,2,3)
for z in (3,9): P("Arandela Dourada",35,3,z)
for (x,z) in ((31,3),(31,7)): P("Lustre Dourado",x,8,z)
P("Lustre Dourado",31,3,11) 
P("Banco",33,0,12)
P("Mesa Redonda de Mármore",31,0,5)   # mesa central da recepção
P("Vaso de Flores Girassóis",31,1,5)
# sala de estar
F("Lã Cinza-clara",11,-1,4,21,-1,10)
P("Lareira de Mármore",16,0,12); P("Espelho Dourado",16,1,12)
for x in (12,13,14,18,19,20): P("Estante Moderna Branca",x,0,12)
P("Quadro Mar Calmo Dourado",13,2,12); P("Quadro Floresta Dourado",19,2,12); P("Quadro Pôr do Sol Dourado",12,2,12); P("Quadro Noite Estrelada Dourado",20,2,12)
for x in range(13,18): P("Sofá Moderno Cinza",x,0,6)
P("Mesa de Centro",15,0,8); P("Mesa de Centro",16,0,8)
P("Poltrona Orelhuda Vermelha",12,0,8); P("Poltrona Orelhuda Vermelha",19,0,8)
P("Vaso de Flores Lavandas",15,1,8); P("Pilha de Livros 2",16,1,8)
P("Luminária de Chão Cinza",11,0,6); P("Luminária Arco Branca",20,0,6)
P("Puff Rosa",11,0,9); P("Puff Azul",20,0,9)
P("Palmeira em Vaso Barro",9,0,1); P("Palmeira em Vaso Branco",23,0,1); P("Samambaia Preto",9,0,12); P("Cacto em Vaso Branco",23,0,12)
for x in (22,23): P("Aparador Ébano",x,0,5)
P("Cristaleira Ébano",23,0,8); P("Aparador Ébano",23,0,3); P("Globo Dourado",23,1,3)
for x in (10,15,20): 
    for k in range(3): P("Cortina Transparente Branca",x,k,1)
P("Lustre Prateado",16,3,8); P("Lustre Prateado",12,3,4)
# biblioteca / escritório
F("Lã Vermelha",12,-1,16,20,-1,22)
for x in (10,11,12,13,19,20,21,22):
    P("Estante Moderna Nogueira",x,0,25); P("Estante Moderna Nogueira",x,1,25)
for x in (14,15,16,17,18): P("Estante Moderna Carvalho",x,0,25)
P("Relógio de Pé Nogueira",14,1,25) if False else None
for x in (14,15,16): P("Escrivaninha",x,0,21)
P("Computador",14,1,21); P("Luminária de Mesa Dourada",16,1,21); P("Cadeira de Escritório",15,0,20)
P("Sofá Chesterfield Azul",10,0,14); P("Sofá Chesterfield Azul",11,0,14); P("Sofá Chesterfield Azul",12,0,14)
P("Sofá Chesterfield Azul",20,0,14); P("Sofá Chesterfield Azul",21,0,14); P("Sofá Chesterfield Azul",22,0,14)
P("Mesa Lateral Nogueira",13,0,14); P("Mesa Lateral Nogueira",19,0,14)
P("Abajur Dourada",13,1,14) if False else None
P("Poltrona Orelhuda Vermelha",10,0,18); P("Poltrona Orelhuda Vermelha",22,0,18)
P("Globo Dourado",18,1,25) if False else None
P("Lustre Dourado",15,3,18); P("Palmeira em Vaso Barro",9,0,25); P("Samambaia Branco",23,0,25)
for z in (17,21): P("Arandela Dourada",23,2,z)
P("Pilha de Livros 3",10,1,14) if False else None
# jantar
F("Lã Cinza-clara",39,-1,3,49,-1,10)
for x in range(41,48):
    for z in (6,7): P("Mesa de Jantar Carvalho",x,0,z)
for x in range(41,48): P("Cadeira Moderna Cinza",x,0,5); P("Cadeira Moderna Cinza",x,0,8)
P("Cadeira Moderna Azul",40,0,6); P("Cadeira Moderna Azul",48,0,7)
P("Fruteira Carvalho",43,1,6); P("Vaso de Flores Rosas Vermelhas",44,1,7); P("Candelabro Dourado",45,1,6); P("Candelabro Dourado",42,1,7)
for x in (42,44,46): P("Lustre Dourado",x,3,6)
for x in (41,42,43,44,45,46,47): P("Aparador Ébano",x,0,12)
P("Globo Dourado",41,1,12); P("Candelabro Dourado",44,1,12); P("Bonsai Verde",47,1,12)
P("Quadro Mar Calmo Dourado",42,2,12); P("Espelho Dourado",44,2,12); P("Quadro Floresta Dourado",46,2,12)
P("Cristaleira Ébano",38,0,12); P("Cristaleira Ébano",50,0,12)
P("Palmeira em Vaso Branco",50,0,1); P("Palmeira em Vaso Barro",38,0,1)
for x in (39,45,51):
    for k in range(3): P("Cortina Transparente Branca",x,k,1)
P("Relógio de Pé Nogueira",51,0,12)
# cozinha
P("Geladeira Inox",38,0,25)
for x in (39,40,41): P("Balcão de Cozinha Branco",x,0,25)
P("Pia de Cozinha Branco",42,0,25); P("Balcão de Cozinha Branco",43,0,25); P("Balcão de Cozinha Branco",44,0,25)
P("Fogão Inox",45,0,25); P("Balcão de Cozinha Branco",46,0,25); P("Balcão de Cozinha Branco",47,0,25); P("Armário Alto Carvalho",48,0,25); P("Geladeira Inox",49,0,25)
for x in (39,40,41,43,44,46,47): P("Armário Suspenso Branco",x,2,25)
P("Coifa Inox",45,2,25); P("Micro-ondas Inox",47,1,25); P("Fruteira Carvalho",40,1,25)
for x in range(40,47): P("Balcão de Cozinha Branco",x,0,20)
for x in range(41,46): P("Banqueta de Bar Branca",x,0,19)
for x in (41,43,45): P("Pendente Branco",x,3,20)
P("Vaso de Flores Margaridas",43,1,20); P("Palmeira em Vaso Preto",50,0,14); P("Frigobar Preto",51,0,25) if False else None
P("Palmeira em Vaso Branco",38,0,14)
# estar / cinema (térreo, fundos)
F("Lã Azul-clara",27,-1,16,34,-1,24)
for x in (31,32,33): P("Aparador Ébano",x,0,14)
P("Televisão",32,1,14); P("Abajur Vermelho",31,1,14) if False else None
for x in (30,31,32,33): P("Sofá Moderno Azul",x,0,25)
P("Mesa de Centro",31,0,20); P("Mesa de Centro",32,0,20)
P("Poltrona Orelhuda Vermelha",28,0,20); P("Poltrona Orelhuda Vermelha",35,0,20)
P("Puff Branco",29,0,17); P("Puff Branco",34,0,17)
P("Luminária de Chão Cinza",35,0,24); P("Samambaia Branco",26,0,24); P("Palmeira em Vaso Barro",26,0,15)
P("Pendente Azul",32,3,19); P("Arandela Dourada",35,2,15) if False else None
# ============ MOBÍLIA andar de cima ============
# suíte master
F("Lã Cinza-clara",10,4,3,17,4,10)
for x in (9,10,11): P("Cama Moderna Azul",x,5,6)
P("Mesa de Cabeceira Ébano",9,5,5); P("Mesa de Cabeceira Ébano",9,5,7); P("Abajur Branco",9,6,5); P("Abajur Branco",9,6,7)
P("Quadro Pôr do Sol Dourado",9,7,6)
P("Aparador Cinza",15,5,12); P("Aparador Cinza",16,5,12); P("Aparador Cinza",17,5,12); P("Televisão",16,6,12)
for x in range(14,19): P("Sofá Moderno Azul",x,5,8)
P("Mesa de Centro",15,5,10); P("Mesa de Centro",16,5,10)
P("Poltrona Orelhuda Branca",12,5,10); P("Divã Branco",22,5,6); P("Mesa Lateral Carvalho",22,5,8)
P("Luminária de Chão Cinza",22,5,10); P("Palmeira em Vaso Branco",9,5,12); P("Samambaia Branco",23,5,12)
P("Lustre Prateado",14,8,6); P("Pendente Preto",16,8,10)
P("Cômoda",9,5,10) if False else None
for x in (10,11,12): P("Cortina Transparente Branca",x,5,1)
# sacada master
P("Mesa Redonda",16,5,-2); P("Cadeira Moderna Branca",15,5,-2); P("Cadeira Moderna Branca",17,5,-2); P("Palmeira em Vaso Preto",11,5,-3); P("Palmeira em Vaso Preto",21,5,-3)
P("Divã Branco",20,5,-2) if False else None
# closet e banheiro master
for z in range(15,25): P("Guarda-Roupa Moderno Cinza",9,5,z)
for x in (10,11,12,13,14,15): P("Guarda-Roupa Moderno Branca",x,5,25)
P("Puff Branco",12,5,19); P("Espelho Dourado",9,6,19) if False else None
F(QZ,17,4,14,23,4,25)
P("Banheira Branco",21,5,25); P("Banheira Branco",22,5,25); P("Espelho Preto",21,7,25) if False else None
P("Vaso Sanitário Branco",23,5,20); P("Pia de Banheiro Branco",23,5,16); P("Pia de Banheiro Branco",23,5,17)
P("Espelho Preto",23,6,16); P("Espelho Preto",23,6,17); P("Máquina de Lavar Branca",17,5,15)
P("Pendente Branco",20,8,19)
# quarto 2 (leste, frente)
F("Lã Rosa",38,4,3,48,4,10)
for x in (49,50,51): P("Cama Moderna Rosa",x,5,6)
P("Mesa de Cabeceira Branca",51,5,5); P("Mesa de Cabeceira Branca",51,5,7); P("Abajur Rosa",51,6,5); P("Luz Noturna Lua Rosa",51,6,7)
P("Quadro Redemoinho Rosa Dourado",51,7,6)
for z in (8,9,10): P("Guarda-Roupa Moderno Branca",37,5,z)
for x in (40,41,42): P("Escrivaninha",x,5,12)
P("Computador",41,6,12); P("Cadeira de Escritório",41,5,11); P("Luminária de Mesa Branca",42,6,12)
P("Divã Cinza",45,5,3); P("Poltrona Orelhuda Rosa",40,5,3); P("Luminária de Chão Rosa",39,5,3)
P("Pendente Rosa",44,8,6); P("Palmeira em Vaso Rosa",51,5,12) if False else None
P("Palmeira em Vaso Rosa",51,5,2)
P("Mesa Redonda",44,5,-2); P("Cadeira Moderna Rosa",43,5,-2); P("Cadeira Moderna Rosa",45,5,-2); P("Palmeira em Vaso Branco",39,5,-3); P("Palmeira em Vaso Branco",49,5,-3)
# quarto 3 (leste, fundos, infantil)
for x in (37,38,39): P("Cama Moderna Azul-clara",x,5,16)
P("Mesa de Cabeceira Bétula",37,5,15); P("Mesa de Cabeceira Bétula",37,5,17); P("Luz Noturna Lua Azul",37,6,15); P("Abajur Azul",37,6,17)
P("Berço Carvalho",42,5,24)
for x in (38,39,40): P("Armário Alto Branca",x,5,25)
P("Escrivaninha",42,5,15) if False else None
F("Lã Azul-clara",38,4,18,42,4,22)
P("Cacto em Vaso Azul",43,5,14) if False else None
# banheiro do quarto 3
F(QZ,45,4,14,51,4,25)
P("Banheira Azul",50,5,25); P("Banheira Azul",51,5,25); P("Vaso Sanitário Azul",45,5,22) if False else None
P("Vaso Sanitário Azul",51,5,20); P("Pia de Banheiro Azul",51,5,16); P("Pia de Banheiro Azul",51,5,17); P("Espelho Preto",51,6,16); P("Espelho Preto",51,6,17)
P("Pendente Branco",48,8,19)
# loft / salão de leitura (centro, fundos)
F("Lã Cinza-clara",27,4,16,34,4,24)
for z in range(15,25): P("Estante Moderna Carvalho",25,5,z)
for x in (28,29,30,31,32): P("Sofá Chesterfield Rosa",x,5,25)
P("Mesa de Centro",29,5,21); P("Mesa de Centro",30,5,21)
P("Poltrona Orelhuda Cinza",28,5,18); P("Poltrona Orelhuda Cinza",34,5,18); P("Mesa Lateral Carvalho",31,5,18)
P("Luminária Arco Branca",27,5,24); P("Palmeira em Vaso Branco",35,5,24); P("Samambaia Branco",35,5,15)
P("Lustre Prateado",31,8,20)
# galeria / ponte
P("Palmeira em Vaso Preto",27,5,12); P("Vaso de Flores Lavandas",35,5,12) if False else None
P("Lustre Dourado",31,8,4); P("Lustre Dourado",31,8,7)
P("Arandela Dourada",35,7,4); P("Arandela Dourada",35,7,8)
P("Quadro Mar Calmo Dourado",35,7,6)
# ================= fundos: varanda, deque e piscina =================
F(SLATE,2,-1,27,58,-1,41)
F(W,6,4,27,54,4,33)
for x in range(8,55,10): F(TRIM,x,0,33,x,3,33)
# sala externa
F("Lã Branca",9,-1,28,15,-1,32)
for x in (10,11,12,13): P("Sofá Moderno Cinza",x,0,28)
P("Mesa de Centro",11,0,30); P("Mesa de Centro",12,0,30)
P("Poltrona Orelhuda Cinza",9,0,31); P("Poltrona Orelhuda Cinza",14,0,31); P("Palmeira em Vaso Preto",9,0,28); P("Palmeira em Vaso Preto",15,0,28)
P("Lustre Preto",12,3,30)
# jantar externo
for x in range(30,38):
    for z in (30,31): P("Mesa de Jantar Carvalho",x,0,z)
for x in range(30,38): P("Cadeira Moderna Cinza",x,0,29); P("Cadeira Moderna Cinza",x,0,32)
P("Cadeira Moderna Cinza",29,0,30); P("Cadeira Moderna Cinza",38,0,31)
P("Fruteira Carvalho",32,1,30); P("Vaso de Flores Margaridas",34,1,31); P("Candelabro Dourado",36,1,30)
for x in (32,35): P("Lustre Dourado",x,3,30)
# churrasqueira / bar
for x in range(44,52): P("Balcão de Cozinha Preto",x,0,27)
P("Pia de Cozinha Preto",46,0,27); P("Fogão Preto",49,0,27); P("Geladeira Preta",52,0,27)
P("Coifa Preta",49,2,27) if False else None
for x in range(45,51): P("Banqueta de Bar Preta",x,0,29)
for x in range(45,51): P("Balcão de Cozinha Preto",x,0,28) if False else None
for x in (45,48): P("Pendente Preto",x,3,28)
P("Palmeira em Vaso Preto",53,0,28)
for x in (7,54): P("Lanterna",x,0,27) if False else None
for (x,z) in ((6,33),(18,33),(28,33),(38,33),(48,33),(54,33)): P(LAMP,x,4,z) if False else None
# piscina grande
PX1,PX2,PZ1,PZ2=10,50,43,62
F(W,PX1-1,-1,PZ1-1,PX2+1,-1,PZ2+1)      # borda branca
F(SLATE,4,-1,38,62,-1,67)
F(W,PX1-1,-1,PZ1-1,PX2+1,-1,PZ2+1)
F(TILE,PX1,-4,PZ1,PX2,-2,PZ2)
F("Água",PX1+1,-3,PZ1+1,PX2-1,-1,PZ2-1)
F(TILE,PX1+1,-3,PZ1+1,PX1+7,-2,PZ2-1)     # praia rasa (1 de água)
F("Água",PX1+1,-1,PZ1+1,PX1+7,-1,PZ2-1)
for x in range(PX1+10,PX2-1,6):
    for z in range(PZ1+3,PZ2-2,5): P("Bloco Luminoso Azul-claro",x,-4,z)
for z in range(PZ1+2,PZ2-1,4): P("Bloco Luminoso Azul-claro",PX1+1,-3,z) if False else None
# spa elevado
SPX,SPZ=55,52
disc(TILE,SPX,SPZ,4.6,-4,-2)
disc(QZ,SPX,SPZ,4.6,-1,0,3.4)
disc("Água",SPX,SPZ,3.3,-3,-1)
P("Bloco Luminoso Azul-claro",SPX,-4,SPZ)
# espreguiçadeiras e guarda-sóis
for x in range(12,50,4):
    P("Divã Branco",x,0,PZ1-3); P("Mesa Lateral Branca",x+1,0,PZ1-3)
    P("Divã Branco",x,0,PZ2+3); P("Mesa Lateral Branca",x+1,0,PZ2+3)
for (x,z,c) in ((19,PZ1-3,"Lã Branca"),(31,PZ1-3,"Lã Azul-clara"),(43,PZ1-3,"Lã Branca"),(19,PZ2+3,"Lã Azul-clara"),(31,PZ2+3,"Lã Branca"),(43,PZ2+3,"Lã Azul-clara")):
    pass
for (x,z,c) in ((14,PZ1-5,"Lã Branca"),(26,PZ1-5,"Lã Azul-clara"),(38,PZ1-5,"Lã Branca"),(14,PZ2+5,"Lã Azul-clara"),(26,PZ2+5,"Lã Branca"),(38,PZ2+5,"Lã Azul-clara")):
    F(W,x,0,z,x,2,z); F(c,x-1,3,z-1,x+1,3,z+1); P(c,x,4,z)
# cabana ao fundo da piscina
for (x,z) in ((2,66),(2,74),(14,66),(14,74)): F(TRIM,x,0,z,x,3,z)
F(W,1,4,65,15,4,75); F(LJ,0,5,64,16,5,76) if False else None
F(SLATE,2,-1,66,14,-1,74)
for x in (4,5,6): P("Cama Moderna Branca",x,0,74)
P("Mesa Lateral Branca",3,0,74); P("Mesa Lateral Branca",7,0,74); P("Abajur Branco",3,1,74); P("Abajur Branco",7,1,74)
P("Sofá Moderno Branco",10,0,74) if False else None
for x in (9,10,11,12): P("Sofá Moderno Branco",x,0,74)
P("Mesa de Centro",10,0,71); P("Mesa de Centro",11,0,71); P("Poltrona Orelhuda Branca",8,0,71); P("Poltrona Orelhuda Branca",13,0,71)
P("Lustre Prateado",8,3,70); P("Palmeira em Vaso Branco",14,0,66)
# gazebo no jardim dos fundos
GXc,GZc=32,74
F("Pedra Lisa",29,-1,63,35,-1,69) if False else None
F(STONE,31,-1,64,33,-1,70)
F(STONE,28,-1,70,36,-1,78)
for (x,z) in ((28,70),(36,70),(28,78),(36,78)): F(TRIM,x,0,z,x,3,z)
F(W,28,4,70,36,4,78); F(LJ,29,5,71,35,5,77); F(LJ,30,6,72,34,6,76); F(LJ,31,7,73,33,7,75); P(LJ,32,8,74)
P("Mesa Redonda de Mármore",32,0,74)
for (x,z) in ((31,74),(33,74),(32,73),(32,75)): P("Banco",x,0,z)
P("Lustre Dourado",32,3,76) if False else None
P("Lustre Dourado",32,3,72)
for (x,z) in ((29,71),(35,71),(29,77),(35,77)): P("Vaso de Planta",x,0,z)
# ================= jardins e iluminação =================
for x in range(GX0+1,GX1):
    P(LEAF,x,0,GZ1-1); P(LEAF,x,1,GZ1-1)
for x in range(GX0+1,GX1):
    if 22<=x<=38: continue
    P(LEAF,x,0,GZ0+1); P(LEAF,x,1,GZ0+1)
for z in range(GZ0+2,GZ1-1):
    P(LEAF,GX0+1,0,z); P(LEAF,GX0+1,1,z); P(LEAF,GX1-1,0,z); P(LEAF,GX1-1,1,z)
# canteiros de flores (folhas coloridas) ao longo da fachada
for x in range(9,52):
    if 22<=x<=38: continue
    P("Folhas de Cerejeira" if (x//3)%2==0 else "Folhas Amarelas",x,0,-2) if x not in range(10,23) and x not in range(38,51) else None
for x in (9,51,23,37):
    for z in (-2,-3): pass
# caminhos laterais de pedra e postes
for z in range(-6,66,1):
    F(STONE,-6,-1,z,-4,-1,z) if z>=0 and False else None
F(STONE,4,-1,-6,6,-1,26) if False else None
for z in range(2,66,6):
    for x in (-5,59):
        F(K,x,0,z,x,2,z); P("Lanterna",x,3,z)
for x in range(0,58,8):
    F(K,x,0,68,x,2,68); P("Lanterna",x,3,68)
# postes do deque/piscina
for x in range(4,58,8):
    for z in (41,64):
        F(K,x,0,z,x,1,z); P("Lanterna",x,2,z)
# árvores
rnd=random.Random(5)
spots=[(-34,-28,1),(-26,-12,2),(-34,8,1),(-24,22,2),(-34,40,1),(-24,56,2),(-34,70,1),(-14,78,2),
       (90,-28,1),(80,-12,2),(90,10,1),(78,26,2),(90,44,1),(78,60,2),(90,74,1),(70,78,2),
       (-10,-28,1),(66,-26,1),(2,-24,2),(58,-22,2),(-12,34,2),(66,40,1),(-12,60,1),(70,64,2),(44,76,1),(18,78,2),(-6,60,0),(60,70,0)]
for (x,z,t) in spots: T(x,z,t)
T(-18,-30,1,"Tronco de Cerejeira","Folhas de Cerejeira"); T(76,-30,1,"Tronco de Cerejeira","Folhas de Cerejeira")
T(-34,-4,4); T(90,-4,4); T(-34,28,4); T(90,28,4)
# placas
L(30,8,-9,0,"Entrada")
L(30,6,45,0,"Piscina")
cmds.append("S|30|-30")  # spawn
