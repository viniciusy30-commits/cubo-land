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
- novidades.txt com entrada nova.

## FALTA
- **Compilar e olhar no aparelho**: se o build falhar, mande o log. Se as folhas ficarem pesadas/estranhas, ajuste `STYLES` (menos `cards`) ou o limite 46000 em `buildChunk`; formas dos sprites estão em `Leaf3D.shape()`.
- Possível ajuste: cartões da cerejeira são só flores (sem folha rosa separada); os sprites usam as 5 cores mais escuras→claras da paleta de cada NatArt.
- `DECOR` em World.kt continua `false` (código antigo de galhos/folhinhas); pode ser removido.
- Conferir no aparelho o relevo da Natureza (ajustar regra em `tools/relief_gen.py` e rodar `python3 relief_gen.py`).
- Depois: Construção e Decoração ainda usam o relevo automático por brilho; fazer mapas lógicos pra elas (tijolos/rejunte, ladrilhos, telhados, estante etc.).

## Como entregar
Este zip tem os arquivos na raiz (sem pasta-embrulho). Suba em `_incoming/` no GitHub.
