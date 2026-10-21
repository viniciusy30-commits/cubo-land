# >>> CAMAS / MOVEIS MULTIBLOCO (parte 21) — NAO COMPILADO <<<
CORRIGIDO em `tools/deco_import.py` `assemble()`: a ordem dos segmentos em x e z estava espelhada (esquerda em x maior, frente em z maior). Conferido pela geometria de cada segmento (tampa do pe em z local -8 no segmento 'front', trilho externo em x -7 no 'left') e pelo modelo de item do addon. Agora esquerda = x menor, frente = z menor, mantendo o MESMO footprint (`fp`) de antes (so a ordem dentro dele mudou), entao DecoRooms/DecoBuilding nao mudam. Regerado com `--batches 1,2,3,4` (registry igual, ids iguais): `geo.bin` e `items.json` (so `sc` de 2 itens mudou). Se algum movel multibloco ainda ficar estranho, conferir o eixo dele com o modelo de item (`*.item.geo.json`).

# >>> ICONES DOS ITENS (parte 20) — NAO COMPILADO <<<
NOVO/MUDADO: icones dos itens de decoracao/mobilia (Deco.isDeco) agora sao gerados do modelo real: `Deco.emitIcon` (malha do item sem mundo + caixa), `GameRenderer.bakeDecoIcons` (FBO 128x128, vista isometrica Rx30/Ry-35, enquadra pelos 8 cantos da caixa, ate 6 icones por quadro), `ToolIcons.deco/putDeco` (fila + LruCache de 320), `HudView.icon` (usa o bitmap; sem ele nao desenha nada ate ficar pronto). Ajustes provaveis: angulo da vista, `0.92f` de enquadramento, brilho `1.12f`. Blocos de formato (B.shape != 0: escada, laje, cerca, cadeira, cacto...) tambem: `bakeDecoIcons` emite via `Shapes.emit` num `World` vazio (`scratchW`) em (8,8,8). Blocos cubicos comuns usam `blockIcon`.

# >>> SKATE v2 (parte 19) — NAO COMPILADO <<<
NOVO/MUDADO: `Skate.kt` (manual no chao com GRAB + barra `bal`, powerslide, empurrao `pushPh/pushW`, pouso `landK`, pop `popK`, `lift` do flip, `speedK`, banco de combo de chao `groundT`, nota do combo `rate()`), `McChar.kt` (pose de skatista: pernas/bracos/queda via `Anim.sk*`, joelho real quando `skw`>0; o modelo usado no jogo e o McChar, NAO o CharModel — CharModel so ganhou os campos de `Anim`), `GameRenderer.kt` (`drawSkate` nova, preenchimento de `an.sk*` com pesos suaves `skSm/skManSm/skFlSm/skGdSm`, FOV dinamico `fovCur`), `Game.kt` (fumaca da derrapagem, poeira, slam, perfect, distancia da camera), `HudView.kt` (analogico flutuante `skStickId`, botoes 4..9, rotulos contextuais, linhas de velocidade, barra de equilibrio tambem no manual).
Ajustes provaveis se algo ficar estranho: valores de quadril/joelho/`spF/spR` em McChar (pes fora da prancha ou atravessando), altura do corpo no empurrao (`drop` 0.25) e no grab (0.2 + prancha sobe 0.3), sinais de `rz` dos bracos, tamanho/posicao dos botoes em `skBtn`, constantes de drift (`2.0f` de amortecimento lateral) e manual (`2.3f`, `6.5f`). Falta: wallride, rampas em curva, som, escolher cor/grafico da prancha.

# >>> SKATE (parte 18) — NAO COMPILADO <<<
NOVO: `Skate.kt` (classe `SkateState`): fisica propria (empurrar, esterçar, ollie carregado, gravidade 24, degrau de 0.52), flips no ar (5 tipos), grabs (5), giro 180 em 180 com checagem de pouso, GRINDS em quinas de blocos (`railTop`/`edgeAt`: bloco solido sem solido por cima e com algum vizinho horizontal vazio) com barra de equilibrio (`bal`), combo/pontos (`addTrick/bank/lose`), bail (`bailT`). Ligado em `Game.kt` (`skateV`, `skating` = driving && curKind==2, `updateSkate`, entradas `sk*`, botao via `wantVeh = 3`), `GameRenderer.kt` (`drawSkate`, boneco de lado na prancha, inclinacao) e `HudView.kt` (`skBtn`, `drawSkateUi`).
Ajustes provaveis: gravidade/alturas em `SkateState` (GRAV, 7.8+5*charge), duracao do flip (0.5s), tolerancia de pouso (48 graus), tamanho dos botoes. Falta: manual, wallride, rampas em curva, animacao de bracos nos grabs (hoje a prancha sobe e o boneco agacha), som.

# >>> STATUS ATUAL (parte 17) <<<
NOVO: Galeria > PASSEIOS > MINI PRÉDIO (`DecoBuilding.kt`, `World.GAL_BUILDING`): prédio de 4 andares (piso a cada 5 blocos, pé direito 4), saguão central com Elevador do jogo (lx 18, lz 1; 4 paradas) e 16 ambientes decorados à mão (sala, cozinha, loja, café, quartos, banheiro, lavanderia, escritório, jogos, música, boutique, consultório, academia, galeria de arte, jardim). Cada peça tem direção FIXA (`putL(..., dir)` grava fState 64|dir<<4). Chaves de item = nome do bloco no addon sem `_drz` (ex. `sofa_blue`); chave inexistente é ignorada em silêncio (se faltar algo na cena, conferir a chave em items.json).

NOVO: Galeria > PASSEIOS > APLICAÇÕES (`DecoRooms.kt`, `World.GAL_APPS`): um ambiente temático por grupo (piso/paredes por tema), móveis em fileiras, quadros/cortinas nas paredes, janelas, luminárias de teto penduradas, plaquinha em cada item. É uma vitrine em grade, NÃO cômodos decorados à mão. Também: vitrines antigas (mansão) não mostram mais a mobília removida (post-pass em `World.generateTest`).

FEITO: importação de TODA mobília/decoração do addon (2736 itens, lotes 1-4), remoção da mobília antiga (menos TV/Elevador antigos), móveis de vários blocos montados + colisão + mira/quebra, portas/armários que abrem (83), TVs/computadores com navegador (26), galeria em 25 grupos.
NÃO PRECISA: ligar/desligar luzes (as luminárias do addon são sempre acesas, sem estado `md:on`).
NÃO MEXER (pedido do usuário): laptops e smartphones deitados (20 itens) — ele vai fazer outra coisa neles.
AGUARDANDO DECISÃO DO USUÁRIO: (a) remover itens de arma (assault_rifle, bazooka, sniper, landmine, guillotine) — remover desloca ids seguintes; (b) importar famílias de construção (asfalto, paredes, pisos, calçadas, degraus/lajes/curvas, alfabeto/números/símbolos ≈ 5000 blocos; estoura limites de ids/tiles, exigiria atlas separado).
NUNCA TESTADO NO JOGO além do build compilar e das camas (corrigidas): conferir visual de portas, TVs, colisão e desempenho do raycast perto de móveis grandes; memória do atlas em celular fraco (se der OOM, reduzir texturas deco para 32x32 em deco_import.py).

# >>> IMPORTAÇÃO DO ADDON monoDeco (mobília + decoração) — LEIA PRIMEIRO <<<

AVISO: nada compilado (sem Android SDK no ambiente). Se o build falhar, mande o log.

## Como funciona
- `tools/deco_import.py <addon_extraido> --batches 1,2,...` lê o .mcaddon (descompacte antes: ele é um zip) e gera em `app/src/main/resources/deco/`: `items.json` (nome, categoria, caixa de colisão, luz, texturas), `geo.bin` (quads já assados em coordenadas de bloco, rotações de osso/cubo aplicadas) e `tex/N.png` (64x64).
- `tools/deco/registry.json` guarda a ordem dos itens/texturas. **Só cresce no fim** (ids de bloco vão pro save). Rode SEMPRE com todos os lotes já feitos + o novo. Ex.: lotes feitos = 1 → próximo: `--batches 1,2`.
- `Deco.kt`: carrega os dados, desenha (`emit`, gira pela direção como a mobília antiga) e dá a caixa de colisão. `Blk.buildDeco()` registra um bloco por item (kind 7, sub 0; `Deco.idx[id]`), cat 5 (Mobília) ou 4 (Decoração). `K_DECO` = tile 64x64 vindo do PNG (Atlas.kt).
- Mobília antiga: `Blk.removeOldFurniture()` esconde todos os blocos cat 5 antigos e os põe em `Blk.vanished` (viram AR ao abrir mundo velho; `World.kt` trata alias < 0). `buildFurn2/3` rodam em modo `dead` (não gastam tile). **Mantidos de propósito:** Televisão (navegador) e Elevador (andares), por terem funções próprias — diga se quer remover. Decorações antigas (cat 4) foram mantidas.
- Sentar: itens com `md:sit` do addon são sentáveis (`Furniture.sittable` → `Deco.sit`).

## FEITO
- **Lotes 1, 2, 3 e 4 já importados** (2736 itens, 961 modelos, 1796 texturas; registry.json em `--batches 1,2,3,4`; só faltam as famílias de construção, ver item 3). Se estourar o limite de tiles (MAXT 4096) os últimos itens ficam sem registro/sem textura (Blk.buildDeco já evita tile errado). Lote 2 = cozinha/banheiro/eletros; lote 3 = luminárias, plantas, quadros, relógios, tapetes, cortinas, bonecos.
- Lote 1 (508 itens, 121 modelos, 176 texturas): cadeiras, sofás, bancos, mesas, camas, escrivaninhas, criados-mudos, estantes, armários, gavetas, guarda-roupas, bancadas, prateleiras/racks, armários-vestiário, berços etc.
- Remoção da mobília antiga + ganchos em Shapes/Furniture/Atlas/World/Blk.

- Itens de vários blocos (cama, armário alto, cortina... `md:segment`, ~385 itens) agora são MONTADOS num modelo só pelo conversor (`assemble()` em deco_import.py): o bloco âncora desenha o móvel inteiro, que passa da célula (x/z negativos, y pra cima). COLISÃO das células extras: FEITA (`Deco.extraBox` + `World.boxOf/solidBox`; `fp` no items.json = pegada em células). MIRA/quebrar pelas células extras: FEITO (`Deco.extraFind`; `World.solidPt` e `Game.raycast` remapeiam o acerto pro bloco âncora).

- Estados abrir/fechar: FEITO para 83 itens (`kd`=1 no items.json): portas (variante aberta = giro 90°+deslize, com caixa de colisão aberta `ob`) e itens com `bone_visibility` por `md:on` (ex. armário de parede). Alterna pelo botão ABRIR/FECHAR que já existia (`Furniture.fKind` → `Deco.kindOf`). geo.bin agora tem 1 byte `vis` por quad (bit0 = estado normal, bit1 = alternado). Portas altas de 2-3 blocos: colocar empilhadas. Luzes ligar/desligar (`kd`=2) existe no código mas nenhum item usa ainda.

- Galeria refinada: grupos novos (ELETRÔNICOS E TECNOLOGIA, HOSPITAL E OFICINA, ESTRUTURAS E TELHADOS, SUPORTES E EXPOSITORES). TVs e computadores novos (pastas tv, tv_vertical, laptop, pc_setup*, pc_potato, teleprompter, touchscreen_menu, tablet/smartphone_display) ABREM O NAVEGADOR: o conversor acha a tela (material 'light' ou o maior quad vertical +z) e grava `sc` = [cx,cy,cz,nx,nz,rx,rz,w,h] no items.json; `Furniture.isTv` inclui `Deco.isTv` e `Game.tvGeom` usa `Deco.tvGeom`. Botão 'Tamanho' não age nos novos. Para outro item virar TV, inclua a pasta em SCREEN_RX. A Televisão ANTIGA foi mantida. AVISO: o addon tem itens de armas (assault_rifle, bazooka, sniper, landmine, guillotine) — decidir com o usuário se remove (tirar de items.json desloca ids dos seguintes: só faça se nenhum mundo importante usa).

## FALTA (nesta ordem)
1. **Compilar e testar** o lote 1 (modelo girado/espelhado errado? ajuste `FACES`/rotação em `deco_import.py` e `Deco.emit`).
2. (Lote 4 FEITO) Antigo: (`--batches 1,2,3,4`, ~1850 itens/~1100 texturas) — rode depois de confirmar que o jogo compila e cabe nos limites. Lote 4: todo o resto (comida, placas, esportes, etc.). Conferir no jogo o limite de tiles (`Blk.MAXT = 4096`) e de ids (16383): total do addon fora das famílias de construção ≈ 3006 itens / 1802 texturas. Se faltar, reduza a 32x32 (tool) ou priorize.
3. Fora do escopo por enquanto (famílias de construção do addon: asfalto, paredes, tiles, calçadas, degraus/curvas/lajes, alfabeto/números/símbolos). Pergunte ao usuário se quer.
4. Galeria: FEITO — seções por grupo (`gp` no items.json → `Deco.groups/groupOf`), e a galeria agora ignora blocos escondidos. Já agrupado em 14 seções grandes (GROUPS em deco_import.py).
5. Comportamentos do addon ainda não portados: portas/janelas que abrem, multiblocos (`md:2y`, conectores), TVs novas ligadas ao navegador, estados (`md:top/bottom`), transparência/vidro (`render_method`), animações (texturas altas tipo 64x1600 foram esticadas).
6. Ícones do inventário dos itens grandes (>1 bloco) podem ficar cortados.
7. Se der falta de memória no atlas, liberar `Atlas.raw`/cópias ou usar atlas separado.

---
# Cubo Land – estado do trabalho (Natureza 3D) e o que falta

AVISO: nada disso foi compilado (sem Android SDK/Maven no ambiente). Só checei a lógica com scripts. Se o build do GitHub falhar, mande o log de erro pro Claude.

## FEITO
1. **NatRelief.kt (novo)**: mapas de relevo 16x16 escritos com lógica por bloco ('0'..'4' = -2..+2). Gerado por `tools/relief_gen.py` (regras por bloco: pepitas sobem com cavidade em volta, gomos/costelas, rejunte, camada de neve/micélio/podzol sobre a terra, palha em cursos sobrepostos...). Também cobre padrões Pix: tábuas (fundo só nos vãos), casca de tronco, topo de tronco, bambu.
2. **Relief.kt**: usa NatRelief quando existe; senão mantém o relevo automático por brilho (8x8). Nova classe `Relief.Map(g, lv)` e `Relief.tone()`.
3. **World.kt `reliefFace`**: reescrita pra grade de tamanho variável, mesclando peças de mesmo nível em retângulos e paredes em trechos, e selando frestas que chegam na borda do bloco.
4. **Galeria** (`generateTest`): categoria "BÁSICOS" removida; cada básico vai pra sua categoria (usa `Items.cat`). Na mochila eles já estavam nas categorias certas.
5. novidades.txt com uma entrada nova.

## FEITO NA SEGUNDA RODADA (também NÃO compilado)
- **Leaf3D.kt (novo)**: `Leaf3D.pixel()` desenha o sprite 16x16 de cada espécie (14 estilos); `Leaf3D.emit()` cria 3-4 cartões dobrados na nervura (2 quads, 8 vértices) por bloco, saindo das faces expostas, com queda da ponta e sombreado base→ponta. Estilos em `STYLES` (cards, len, wid, fold, droop, out).
- **Blk.kt**: novo `K_SPRITE = 38`, `spriteOf[tile da casca] = tile do sprite`, `spriteFor()`, helper `leaf3()` que cria casca + sprite juntos (13 folhagens + Musgo); o carvalho (tile 8) tem sprite próprio (`spriteOf[8]`). `leafRuns` inclui K_SPRITE (vento/luz de folha).
- **Atlas.kt**: caso `K_SPRITE` em `genNew` e na lista "sem pixelArt".
- **World.kt `buildChunk`**: depois da casca + cubo interno chama `Leaf3D.emit` (só se `o.vc < 46000`).
- **Mundo de testes travado**: `World.locked` (BitSet preenchido no fim de `generateTest`) + `isLocked()`; `Game.blockHit`, `cutFoliage` e `fellTree` ignoram blocos travados. Só quebra o que o jogador colocar.
- **Folhas em toda a copa**: `buildChunk` junta os blocos de folha numa lista e emite os cartões num passe só no fim, de cima pra baixo, com menos cartões (4/2/1) conforme `o.vc` sobe (antes o orçamento acabava nos blocos de baixo).
- **Frutinhas** (`Leaf3D.fruitPixel` + `emit`): dois quads cruzados pendurados; `Style.fruit/fsize` por espécie (bambu não tem); `Blk.fruitOf[tile]`.
- **Grãos variáveis** (`NatVar.kt`, tipo `K_PALV`): 6 variantes do topo de Podzol, Grama Nevada, Micélio e Terra Grossa (nenhum/poucos/mais/todos, deslocados e espelhados), com relevo derivado; `Blk.pickTile` escolhe por posição em `buildChunk`.
- **Estática**: ruído do `pixelArt` reduzido pela metade e filtro de minificação trilinear (`GameRenderer`).
- **Terreno básico em pixel art** (tiles 1-7,9,11-14 opacos) em `Atlas.build`.
- novidades.txt com entrada nova.

## FALTA
- **Compilar e olhar no aparelho**: se o build falhar, mande o log. Se as folhas ficarem pesadas/estranhas, ajuste `STYLES` (menos `cards`) ou o limite 46000 em `buildChunk`; formas dos sprites estão em `Leaf3D.shape()`.
- Possível ajuste: cartões da cerejeira são só flores (sem folha rosa separada); os sprites usam as 5 cores mais escuras→claras da paleta de cada NatArt.
- `DECOR` em World.kt continua `false` (código antigo de galhos/folhinhas); pode ser removido.
- Conferir no aparelho o relevo da Natureza (ajustar regra em `tools/relief_gen.py` e rodar `python3 relief_gen.py`).
- Depois: Construção e Decoração ainda usam o relevo automático por brilho; fazer mapas lógicos pra elas (tijolos/rejunte, ladrilhos, telhados, estante etc.).

## Como entregar
Este zip tem os arquivos na raiz (sem pasta-embrulho). Suba em `_incoming/` no GitHub.

## TV COM NAVEGADOR (terceira rodada, também NÃO compilado)
- **TvOverlay.kt (novo)**: janela da TV = `WebView` (motor do Chrome) numa moldura, por cima do jogo. Barra: arrastar, voltar, recarregar, início, endereço/pesquisa, tela cheia, fechar. Página inicial própria com atalhos (YouTube, Google, Twitch, Wikipédia). Vídeo em tela cheia do próprio site via `onShowCustomView`. Tela cheia e vídeo em tela cheia pausam o jogo (`holdPause`/`releasePause`).
- **Game.kt**: `scanTv` (a cada 0,12s acha a TV olhada: cone ~37°, até 6,5 blocos, com linha de visão livre) -> `tvAim`; `cycleTvSize`.
- **HudView.kt**: botões USAR TV / DESLIGAR e TAMANHO n/4 aparecem quando `game.tvAim != null` (toque tratado antes da mira, não coloca bloco).
- **Furniture.kt**: `TV_SCALE = 1, 1.5, 2, 3`; o pincel `C` ganhou `sc` (escala a partir do centro da base; a TV pode passar da célula pros lados). Colisão/mira continuam do tamanho de 1 bloco.
- **World.kt**: `tvSize` (por bloco), salvo num trailer opcional depois dos blocos (saves antigos abrem normal); trocar/quebrar o bloco zera o tamanho.
- **GameActivity.kt**: cria/fecha a `TvOverlay`; botão voltar fecha o vídeo em tela cheia -> tela cheia -> TV.
- Limites conhecidos: a imagem do navegador NÃO é pintada na tela 3D da TV (vídeo do WebView não dá pra capturar pra textura); login do Google costuma ser bloqueado em WebView; precisa do Android System WebView no aparelho.
- Ideias depois: janela seguir a TV em perspectiva, fechar sozinha ao se afastar muito, favoritos.
