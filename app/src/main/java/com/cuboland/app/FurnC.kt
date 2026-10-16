package com.cuboland.app

import kotlin.math.max
import kotlin.math.min

/**
 * Mobília v3 (subtipos 256+), orientada a dados: cada móvel é uma lista de peças (caixas/cilindros) + comportamento.
 * ATENÇÃO: a ordem de [defs] define os ids dos blocos no mundo salvo. Só adicione NO FIM, nunca reordene.
 * Paleta de cada bloco (Blk.fp): 0 cor principal, 1 cor 2, 2 madeira, 3 madeira escura, 4 metal, 5 branco/pedra, 6 luz/água/fogo, 7 preto.
 * kind 3 = gira 90° por toque; kind 4 = guarda itens (slots = [x, y, z, tamanho] por lugar, até 8); boxOpen = colisão com o móvel ligado/aberto (portas e portões abertos deixam passar).
 * Interação (kind): 0 = só uso (sentar/mesa), 1 = abre/fecha, 2 = liga/desliga (ou acende/apaga, abre/fecha, dobra/abre, instala/recolhe).
 */
object FurnC {
    const val BASE = 256
    private const val C0 = 0; private const val C1 = 1; private const val W = 2; private const val WD = 3
    private const val M = 4; private const val D = 5; private const val L = 6; private const val K = 7

    class Def(
        val name: String, val desc: String, val box: FloatArray, val parts: List<FloatArray>, val alt: List<FloatArray>?,
        val fem: Boolean, val ways: Int, val fabric: Boolean, val mat: Int, val kind: Int, val seat: Float, val top: Float,
        val small: Boolean, val lt: Int, val color: Int, val open: FloatArray?, val lid: FloatArray?, val lab: Int, val inv: Boolean,
        val boxOpen: FloatArray?, val slots: FloatArray?, val group: String, val sy: Float
    ) { var sub = 0 }

    val defs = ArrayList<Def>()
    private var grp = ""
    /** seções da galeria de blocos (aba MOBÍLIA), na ordem em que aparecem */
    val GROUPS = listOf("ASSENTOS EXTERNOS", "MESAS EXTERNAS", "ARMÁRIOS E CAIXAS", "LUZ, FOGO E ÁGUA", "ABRIGO E CAMPING", "ELETRODOMÉSTICOS",
        "CASA: BANHEIRO, LAVANDERIA E LAZER", "OFICINA", "FAZENDA E QUINTAL", "PORTAS E JANELAS", "SALA E DECORAÇÃO", "EXPOSIÇÃO E ARMAZENAMENTO")
    val WAY_M = arrayOf("Branco", "Azul-claro", "Amarelo", "Rosa", "Lima", "Vermelho", "Azul", "Laranja")
    val WAY_F = arrayOf("Branca", "Azul-clara", "Amarela", "Rosa", "Lima", "Vermelha", "Azul", "Laranja")
    /** nome do bloco da cor k deste móvel (os que têm uma cor só levam o nome puro) */
    fun fullName(d: Def, k: Int): String = if (d.ways > 1) d.name + " " + (if (d.fem) WAY_F[k] else WAY_M[k]) else d.name
    private val nameGroup: HashMap<String, String> by lazy {
        val m = HashMap<String, String>()
        for (d in defs) for (k in 0 until (if (d.ways > 1) WAY_M.size else 1)) m[fullName(d, k)] = d.group
        m
    }
    fun groupOf(n: String): String = nameGroup[n] ?: ""
    fun def(s: Int): Def? = defs.getOrNull(s - BASE)

    // ---- pincéis ----
    private fun b(p: Int, x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double) =
        floatArrayOf(0f, p.toFloat(), x0.toFloat(), y0.toFloat(), z0.toFloat(), x1.toFloat(), y1.toFloat(), z1.toFloat())
    private fun g(p: Int, x0: Double, y0: Double, z0: Double, x1: Double, y1: Double, z1: Double) =
        floatArrayOf(1f, p.toFloat(), x0.toFloat(), y0.toFloat(), z0.toFloat(), x1.toFloat(), y1.toFloat(), z1.toFloat())
    private fun y(p: Int, cx: Double, cz: Double, r: Double, y0: Double, y1: Double) =
        floatArrayOf(2f, p.toFloat(), cx.toFloat(), cz.toFloat(), r.toFloat(), r.toFloat(), y0.toFloat(), y1.toFloat())
    /** cilindro com raio de baixo e de cima diferentes (mesmo que cone) */
    private fun y(p: Int, cx: Double, cz: Double, r0: Double, r1: Double, y0: Double, y1: Double) = cone(p, cx, cz, r0, r1, y0, y1)
    private fun cone(p: Int, cx: Double, cz: Double, r0: Double, r1: Double, y0: Double, y1: Double) =
        floatArrayOf(2f, p.toFloat(), cx.toFloat(), cz.toFloat(), r0.toFloat(), r1.toFloat(), y0.toFloat(), y1.toFloat())
    private fun a(vararg v: Double) = FloatArray(v.size) { v[it].toFloat() }
    private fun parts(vararg p: FloatArray): List<FloatArray> = p.toList()
    /** versão "desligada": as peças que brilham viram pretas (luzinhas, telas) */
    private fun dim(l: List<FloatArray>): List<FloatArray> = l.map { if (it[0] == 1f) floatArrayOf(0f, K.toFloat(), it[2], it[3], it[4], it[5], it[6], it[7]) else it }

    private fun add(
        name: String, desc: String, box: FloatArray, parts: List<FloatArray>, alt: List<FloatArray>? = null,
        fem: Boolean = false, ways: Int = 8, fabric: Boolean = false, mat: Int = 2, kind: Int = 0, seat: Double = 0.0,
        top: Double = 0.0, small: Boolean = false, lt: Int = 0, color: Int = 0xC8935A, open: FloatArray? = null,
        lid: FloatArray? = null, lab: Int = 0, inv: Boolean = false, dimOff: Boolean = false, boxOpen: FloatArray? = null, slots: FloatArray? = null, sy: Double = 1.0
    ) {
        val alt2 = alt ?: if (dimOff && kind == 2) dim(parts) else null
        val d = Def(name, desc, box, parts, alt2, fem, ways, fabric, mat, kind, seat.toFloat(), top.toFloat(), small, lt, color, open, lid, lab, inv, boxOpen, slots, grp, sy.toFloat())
        d.sub = BASE + defs.size
        defs.add(d)
    }

    // rótulos [com o móvel ligado/aberto, com ele desligado/fechado]
    private val LABS = arrayOf(arrayOf("DESLIGAR", "LIGAR"), arrayOf("APAGAR", "ACENDER"), arrayOf("FECHAR", "ABRIR"),
        arrayOf("DOBRAR", "ABRIR"), arrayOf("RECOLHER", "INSTALAR"), arrayOf("ESVAZIAR", "ENCHER"), arrayOf("RECLINAR", "ENDIREITAR"), arrayOf("SUBIR", "DESCER"))

    init {
        // ===================== ASSENTOS =====================
        grp = "ASSENTOS EXTERNOS"
        add("Espreguiçadeira", "Espreguiçadeira reclinável de piscina com encosto e travesseiro. Dá pra sentar e relaxar.",
            a(0.08, 0.0, 0.02, 0.92, 0.5, 0.98),
            parts(b(M, 0.12, 0.0, 0.08, 0.2, 0.2, 0.16), b(M, 0.8, 0.0, 0.08, 0.88, 0.2, 0.16), b(M, 0.12, 0.0, 0.84, 0.2, 0.2, 0.92), b(M, 0.8, 0.0, 0.84, 0.88, 0.2, 0.92),
                b(W, 0.1, 0.2, 0.04, 0.9, 0.25, 0.96), b(C0, 0.1, 0.25, 0.3, 0.9, 0.33, 0.96), b(C0, 0.1, 0.25, 0.04, 0.9, 0.35, 0.34),
                b(C0, 0.1, 0.35, 0.04, 0.9, 0.45, 0.24), b(C0, 0.1, 0.45, 0.04, 0.9, 0.52, 0.16), b(C1, 0.16, 0.52, 0.05, 0.84, 0.58, 0.15)),
            fem = true, fabric = true, seat = 0.4, color = 0x6DB9EE)
        add("Cadeira de Piscina", "Cadeira de plástico resistente à água, ótima pra beira da piscina.",
            a(0.14, 0.0, 0.14, 0.86, 0.95, 0.86),
            parts(b(C0, 0.16, 0.0, 0.16, 0.24, 0.45, 0.24), b(C0, 0.76, 0.0, 0.16, 0.84, 0.45, 0.24), b(C0, 0.16, 0.0, 0.76, 0.24, 0.45, 0.84), b(C0, 0.76, 0.0, 0.76, 0.84, 0.45, 0.84),
                b(C0, 0.14, 0.45, 0.14, 0.86, 0.52, 0.86), b(C0, 0.14, 0.52, 0.12, 0.86, 0.95, 0.2), b(C1, 0.3, 0.62, 0.2, 0.7, 0.85, 0.23)),
            fem = true, seat = 0.45, mat = 3, color = 0xF2F2EE)
        add("Cadeira de Praia", "Cadeira de praia de lona com braços de madeira, bem baixinha e reclinada.",
            a(0.1, 0.0, 0.1, 0.9, 0.9, 0.9),
            parts(b(W, 0.1, 0.0, 0.78, 0.16, 0.36, 0.84), b(W, 0.84, 0.0, 0.78, 0.9, 0.36, 0.84), b(W, 0.1, 0.0, 0.06, 0.16, 0.9, 0.12), b(W, 0.84, 0.0, 0.06, 0.9, 0.9, 0.12),
                b(C0, 0.12, 0.34, 0.2, 0.88, 0.4, 0.86), b(C0, 0.12, 0.4, 0.12, 0.88, 0.62, 0.22), b(C0, 0.12, 0.62, 0.08, 0.88, 0.86, 0.16),
                b(W, 0.08, 0.4, 0.18, 0.13, 0.55, 0.8), b(W, 0.87, 0.4, 0.18, 0.92, 0.55, 0.8)),
            fem = true, fabric = true, seat = 0.36, color = 0xF59B3D)
        add("Cadeira de Camping", "Cadeira dobrável de camping com estrutura de metal e assento de lona.",
            a(0.12, 0.0, 0.1, 0.88, 0.88, 0.88),
            parts(b(M, 0.12, 0.0, 0.14, 0.18, 0.4, 0.2), b(M, 0.82, 0.0, 0.14, 0.88, 0.4, 0.2), b(M, 0.12, 0.0, 0.8, 0.18, 0.4, 0.86), b(M, 0.82, 0.0, 0.8, 0.88, 0.4, 0.86),
                b(C0, 0.14, 0.4, 0.16, 0.86, 0.45, 0.84), b(C0, 0.14, 0.45, 0.1, 0.86, 0.85, 0.16), b(M, 0.12, 0.45, 0.08, 0.17, 0.88, 0.14), b(M, 0.83, 0.45, 0.08, 0.88, 0.88, 0.14)),
            fem = true, fabric = true, seat = 0.45, color = 0x4C74C4)
        add("Cadeira de Balanço", "Cadeira de balanço de madeira com almofada. Perfeita pra varanda.",
            a(0.1, 0.0, 0.08, 0.9, 0.95, 0.92),
            parts(b(WD, 0.1, 0.0, 0.08, 0.18, 0.07, 0.92), b(WD, 0.82, 0.0, 0.08, 0.9, 0.07, 0.92), b(W, 0.12, 0.07, 0.2, 0.18, 0.45, 0.26), b(W, 0.82, 0.07, 0.2, 0.88, 0.45, 0.26),
                b(W, 0.12, 0.07, 0.74, 0.18, 0.45, 0.8), b(W, 0.82, 0.07, 0.74, 0.88, 0.45, 0.8), b(W, 0.12, 0.42, 0.18, 0.88, 0.48, 0.84), b(C0, 0.16, 0.48, 0.22, 0.84, 0.54, 0.82),
                b(W, 0.14, 0.48, 0.12, 0.2, 0.95, 0.2), b(W, 0.8, 0.48, 0.12, 0.86, 0.95, 0.2), b(C1, 0.2, 0.62, 0.14, 0.8, 0.88, 0.2)),
            fem = true, fabric = true, seat = 0.5, color = 0xC8444E)
        add("Poltrona Externa", "Poltrona de área externa com almofadas grossas. Dá pra sentar e conversar.",
            a(0.05, 0.0, 0.05, 0.95, 0.9, 0.95),
            parts(b(M, 0.08, 0.0, 0.08, 0.14, 0.1, 0.14), b(M, 0.86, 0.0, 0.08, 0.92, 0.1, 0.14), b(M, 0.08, 0.0, 0.86, 0.14, 0.1, 0.92), b(M, 0.86, 0.0, 0.86, 0.92, 0.1, 0.92),
                b(C1, 0.05, 0.1, 0.05, 0.95, 0.4, 0.95), b(C1, 0.05, 0.4, 0.05, 0.95, 0.9, 0.25), b(C1, 0.05, 0.4, 0.25, 0.2, 0.62, 0.95), b(C1, 0.8, 0.4, 0.25, 0.95, 0.62, 0.95),
                b(C0, 0.2, 0.4, 0.28, 0.8, 0.5, 0.95)),
            fem = true, fabric = true, seat = 0.5, mat = 3, color = 0x9AD93E, sy = 1.0)
        add("Pufe Impermeável", "Pufe redondo resistente à água. Dá pra sentar em qualquer lugar.",
            a(0.14, 0.0, 0.14, 0.86, 0.46, 0.86),
            parts(y(C0, 0.5, 0.5, 0.36, 0.0, 0.42), y(C1, 0.5, 0.5, 0.3, 0.42, 0.46)), seat = 0.44, mat = 3, color = 0xF5A3C4)
        add("Banco de Piquenique", "Banco de madeira rústico de piquenique.",
            a(0.0, 0.0, 0.1, 1.0, 0.46, 0.9),
            parts(b(W, 0.0, 0.4, 0.1, 1.0, 0.46, 0.9), b(WD, 0.08, 0.0, 0.2, 0.16, 0.4, 0.8), b(WD, 0.84, 0.0, 0.2, 0.92, 0.4, 0.8), b(WD, 0.0, 0.2, 0.4, 1.0, 0.26, 0.5)),
            ways = 1, seat = 0.46)
        add("Banco de Pesca", "Banquinho de pesca com assento de lona.",
            a(0.24, 0.0, 0.24, 0.76, 0.52, 0.76),
            parts(y(C0, 0.5, 0.5, 0.3, 0.45, 0.52), b(M, 0.26, 0.0, 0.26, 0.32, 0.45, 0.32), b(M, 0.68, 0.0, 0.26, 0.74, 0.45, 0.32), b(M, 0.26, 0.0, 0.68, 0.32, 0.45, 0.74), b(M, 0.68, 0.0, 0.68, 0.74, 0.45, 0.74)),
            seat = 0.5, color = 0x5FA070)
        add("Banco de Balanço", "Banco de balanço com armação de metal. Dá pra sentar.",
            a(0.05, 0.0, 0.1, 0.95, 1.0, 0.9),
            parts(b(M, 0.05, 0.0, 0.1, 0.12, 1.0, 0.18), b(M, 0.88, 0.0, 0.1, 0.95, 1.0, 0.18), b(M, 0.05, 0.0, 0.82, 0.12, 1.0, 0.9), b(M, 0.88, 0.0, 0.82, 0.95, 1.0, 0.9),
                b(M, 0.05, 0.94, 0.1, 0.95, 1.0, 0.9), b(C0, 0.12, 0.35, 0.2, 0.88, 0.4, 0.8), b(C0, 0.12, 0.4, 0.18, 0.88, 0.75, 0.24), b(M, 0.15, 0.4, 0.22, 0.17, 0.94, 0.24), b(M, 0.83, 0.4, 0.22, 0.85, 0.94, 0.24)),
            seat = 0.4, color = 0xD96FCB)
        add("Daybed Externo", "Cama de área externa com colchão e almofadas. Dá pra sentar e descansar.",
            a(0.0, 0.0, 0.0, 1.0, 0.7, 1.0),
            parts(b(W, 0.0, 0.0, 0.0, 1.0, 0.25, 1.0), b(C0, 0.02, 0.25, 0.02, 0.98, 0.38, 0.98), b(C1, 0.02, 0.38, 0.02, 0.98, 0.7, 0.24), b(C1, 0.15, 0.38, 0.3, 0.5, 0.46, 0.6), b(C1, 0.55, 0.38, 0.3, 0.85, 0.46, 0.6)),
            fabric = true, seat = 0.4, mat = 3, color = 0xF2F2EE)
        add("Rede de Descanso", "Rede presa em dois postes. Dá pra sentar e relaxar.",
            a(0.0, 0.0, 0.3, 1.0, 0.45, 0.7),
            parts(b(WD, 0.0, 0.0, 0.45, 0.1, 1.0, 0.55), b(WD, 0.9, 0.0, 0.45, 1.0, 1.0, 0.55), b(C0, 0.1, 0.62, 0.4, 0.22, 0.72, 0.6), b(C0, 0.22, 0.5, 0.38, 0.78, 0.56, 0.62),
                b(C0, 0.78, 0.62, 0.4, 0.9, 0.72, 0.6), b(C1, 0.3, 0.56, 0.4, 0.7, 0.6, 0.6)),
            fem = true, fabric = true, seat = 0.5, color = 0xF5D63B)

        // ===================== MESAS =====================
        grp = "MESAS EXTERNAS"
        add("Mesa Lateral Externa", "Mesinha redonda de apoio pra copos e livros.", a(0.16, 0.0, 0.16, 0.84, 0.56, 0.84),
            parts(y(C0, 0.5, 0.5, 0.34, 0.5, 0.56), y(M, 0.5, 0.5, 0.06, 0.0, 0.5), y(M, 0.5, 0.5, 0.2, 0.0, 0.04)),
            fem = true, top = 0.56, mat = 3, color = 0xF2F2EE)
        add("Mesa de Centro Externa", "Mesa de centro baixa pra servir lanches.", a(0.04, 0.0, 0.04, 0.96, 0.46, 0.96),
            parts(b(C0, 0.04, 0.4, 0.04, 0.96, 0.46, 0.96), b(W, 0.08, 0.0, 0.08, 0.16, 0.4, 0.16), b(W, 0.84, 0.0, 0.08, 0.92, 0.4, 0.16), b(W, 0.08, 0.0, 0.84, 0.16, 0.4, 0.92), b(W, 0.84, 0.0, 0.84, 0.92, 0.4, 0.92)),
            fem = true, top = 0.46, color = 0xF2F2EE)
        add("Mesa Bistrô", "Mesa alta e redonda, combina com banquetas.", a(0.1, 0.0, 0.1, 0.9, 0.94, 0.9),
            parts(y(C0, 0.5, 0.5, 0.4, 0.88, 0.94), y(M, 0.5, 0.5, 0.05, 0.0, 0.88), y(M, 0.5, 0.5, 0.22, 0.0, 0.05)),
            fem = true, top = 0.94, color = 0xF5D63B)
        add("Mesa de Praia", "Mesinha de praia que dobra: toque em DOBRAR e ela vira uma prancha em pé.", a(0.04, 0.0, 0.04, 0.96, 0.5, 0.96),
            parts(b(C0, 0.04, 0.46, 0.04, 0.96, 0.5, 0.96), b(M, 0.08, 0.0, 0.08, 0.14, 0.46, 0.14), b(M, 0.86, 0.0, 0.08, 0.92, 0.46, 0.14), b(M, 0.08, 0.0, 0.86, 0.14, 0.46, 0.92), b(M, 0.86, 0.0, 0.86, 0.92, 0.46, 0.92)),
            alt = parts(b(C0, 0.3, 0.0, 0.4, 0.7, 0.9, 0.46), b(M, 0.3, 0.0, 0.46, 0.7, 0.06, 0.5)),
            fem = true, top = 0.5, kind = 2, lab = 3, color = 0xF59B3D)
        add("Mesa de Camping", "Mesa dobrável de camping. Toque pra dobrar e guardar.", a(0.04, 0.0, 0.04, 0.96, 0.7, 0.96),
            parts(b(C0, 0.04, 0.66, 0.04, 0.96, 0.7, 0.96), b(M, 0.08, 0.0, 0.08, 0.14, 0.66, 0.14), b(M, 0.86, 0.0, 0.08, 0.92, 0.66, 0.14), b(M, 0.08, 0.0, 0.86, 0.14, 0.66, 0.92), b(M, 0.86, 0.0, 0.86, 0.92, 0.66, 0.92)),
            alt = parts(b(C0, 0.3, 0.0, 0.4, 0.7, 0.95, 0.46), b(M, 0.3, 0.0, 0.46, 0.7, 0.06, 0.5)),
            fem = true, top = 0.7, kind = 2, lab = 3, color = 0x9AD93E)
        add("Mesa de Buffet", "Mesa longa com toalha pra servir comida.", a(0.0, 0.0, 0.0, 1.0, 0.92, 1.0),
            parts(b(C0, 0.0, 0.86, 0.0, 1.0, 0.92, 1.0), b(C1, 0.02, 0.0, 0.02, 0.98, 0.86, 0.98)),
            fem = true, fabric = true, top = 0.92, mat = 3, color = 0xF2F2EE)
        add("Mesa de Trabalho Rural", "Mesa de madeira grossa com prateleira embaixo, pra preparar a colheita.", a(0.0, 0.0, 0.0, 1.0, 0.92, 1.0),
            parts(b(W, 0.0, 0.84, 0.0, 1.0, 0.92, 1.0), b(WD, 0.04, 0.0, 0.04, 0.12, 0.84, 0.12), b(WD, 0.88, 0.0, 0.04, 0.96, 0.84, 0.12), b(WD, 0.04, 0.0, 0.88, 0.12, 0.84, 0.96), b(WD, 0.88, 0.0, 0.88, 0.96, 0.84, 0.96), b(W, 0.06, 0.25, 0.06, 0.94, 0.3, 0.94)),
            fem = true, ways = 1, top = 0.92)
        add("Mesa de Jardim Redonda", "Mesa redonda de jardim com pé central.", a(0.04, 0.0, 0.04, 0.96, 0.76, 0.96),
            parts(y(C0, 0.5, 0.5, 0.46, 0.7, 0.76), y(M, 0.5, 0.5, 0.06, 0.0, 0.7), y(M, 0.5, 0.5, 0.24, 0.0, 0.05)),
            fem = true, top = 0.76, color = 0xF2F2EE)

        // ===================== ARMÁRIOS E CAIXAS (abrem) =====================
        grp = "ARMÁRIOS E CAIXAS"
        add("Armário de Piscina", "Armário de duas portas pra toalhas e brinquedos. Toque pra abrir e fechar.", a(0.02, 0.0, 0.02, 0.98, 0.95, 0.98),
            parts(b(C0, 0.02, 0.0, 0.02, 0.98, 0.95, 0.98), b(C1, 0.08, 0.06, 0.97, 0.48, 0.9, 0.99), b(C1, 0.52, 0.06, 0.97, 0.92, 0.9, 0.99), b(M, 0.46, 0.45, 0.985, 0.48, 0.55, 0.995), b(M, 0.52, 0.45, 0.985, 0.54, 0.55, 0.995)),
            open = a(0.99, 0.08, 0.06, 0.92, 0.9), kind = 1, color = 0x6DB9EE, sy = 1.6)
        add("Armário Externo", "Armário de madeira pra varanda e quintal. Toque pra abrir e fechar.", a(0.02, 0.0, 0.02, 0.98, 1.0, 0.98),
            parts(b(W, 0.02, 0.0, 0.02, 0.98, 1.0, 0.98), b(C0, 0.08, 0.06, 0.97, 0.48, 0.94, 0.99), b(C0, 0.52, 0.06, 0.97, 0.92, 0.94, 0.99), b(M, 0.46, 0.45, 0.985, 0.48, 0.55, 0.995), b(M, 0.52, 0.45, 0.985, 0.54, 0.55, 0.995)),
            open = a(0.99, 0.08, 0.06, 0.92, 0.94), kind = 1, color = 0x9AD93E, sy = 1.8)
        add("Armário de Ferramentas", "Armário de metal pra guardar ferramentas. Toque pra abrir.", a(0.02, 0.0, 0.02, 0.98, 1.0, 0.98),
            parts(b(M, 0.02, 0.0, 0.02, 0.98, 1.0, 0.98), b(C0, 0.06, 0.04, 0.97, 0.94, 0.96, 0.99), b(K, 0.46, 0.45, 0.99, 0.54, 0.55, 1.0)),
            open = a(0.99, 0.06, 0.04, 0.94, 0.96), kind = 1, mat = 5, color = 0xF59B3D, sy = 1.9)
        add("Armário de Praia", "Cabana-armário de praia pra guardar pertences. Toque pra abrir.", a(0.05, 0.0, 0.1, 0.95, 0.98, 0.9),
            parts(b(C0, 0.05, 0.0, 0.1, 0.95, 0.9, 0.9), b(C1, 0.0, 0.9, 0.05, 1.0, 0.98, 0.95), b(W, 0.12, 0.06, 0.9, 0.88, 0.8, 0.915), b(M, 0.78, 0.4, 0.915, 0.84, 0.5, 0.93)),
            open = a(0.91, 0.12, 0.06, 0.88, 0.8), kind = 1, color = 0xF5D63B, sy = 1.8)
        add("Caixa de Correio", "Caixa de correio num poste. Toque pra abrir a portinhola.", a(0.22, 0.0, 0.3, 0.78, 1.0, 0.8),
            parts(b(WD, 0.45, 0.0, 0.45, 0.55, 0.7, 0.55), b(C0, 0.22, 0.7, 0.3, 0.78, 0.98, 0.8), b(C1, 0.22, 0.9, 0.3, 0.78, 0.98, 0.8), b(M, 0.6, 0.78, 0.81, 0.72, 0.84, 0.83)),
            open = a(0.81, 0.26, 0.74, 0.74, 0.94), kind = 1, color = 0xE04040, sy = 1.4)
        add("Baú Impermeável", "Baú à prova d'água pra toalhas e brinquedos. Toque pra levantar a tampa.", a(0.06, 0.0, 0.1, 0.94, 0.55, 0.9),
            parts(b(C0, 0.06, 0.0, 0.1, 0.94, 0.5, 0.9), b(C1, 0.06, 0.5, 0.1, 0.94, 0.55, 0.9), b(M, 0.44, 0.42, 0.9, 0.56, 0.54, 0.92)),
            lid = a(0.06, 0.1, 0.94, 0.9, 0.55), kind = 1, color = 0x6DB9EE)
        add("Caixa Térmica", "Caixa térmica pra bebidas geladas. Toque pra abrir a tampa.", a(0.08, 0.0, 0.16, 0.92, 0.5, 0.84),
            parts(b(C0, 0.1, 0.0, 0.18, 0.9, 0.45, 0.82), b(D, 0.08, 0.45, 0.16, 0.92, 0.5, 0.84), b(M, 0.4, 0.5, 0.45, 0.6, 0.54, 0.55)),
            lid = a(0.1, 0.18, 0.9, 0.82, 0.5), kind = 1, small = true, fem = true, color = 0xE04040)
        add("Lixeira com Tampa", "Lixeira com tampa articulada. Toque pra abrir e fechar.", a(0.2, 0.0, 0.2, 0.8, 0.78, 0.8),
            parts(y(C0, 0.5, 0.5, 0.3, 0.0, 0.72), y(C1, 0.5, 0.5, 0.33, 0.33, 0.72, 0.78)),
            lid = a(0.2, 0.2, 0.8, 0.8, 0.72), kind = 1, fem = true, color = 0x9AD93E)

        // ===================== LUZ, FOGO E ÁGUA (ligam/desligam) =====================
        grp = "LUZ, FOGO E ÁGUA"
        add("Fogueira", "Fogueira de jardim com lenha. Toque pra apagar ou acender.", a(0.15, 0.0, 0.15, 0.85, 0.4, 0.85),
            parts(b(WD, 0.2, 0.0, 0.25, 0.8, 0.1, 0.35), b(WD, 0.2, 0.0, 0.65, 0.8, 0.1, 0.75), b(WD, 0.25, 0.1, 0.2, 0.35, 0.2, 0.8), b(WD, 0.65, 0.1, 0.2, 0.75, 0.2, 0.8),
                g(L, 0.35, 0.1, 0.35, 0.65, 0.5, 0.65), g(L, 0.42, 0.4, 0.42, 0.58, 0.72, 0.58)),
            alt = parts(b(WD, 0.2, 0.0, 0.25, 0.8, 0.1, 0.35), b(WD, 0.2, 0.0, 0.65, 0.8, 0.1, 0.75), b(K, 0.3, 0.02, 0.3, 0.7, 0.08, 0.7)),
            fem = true, ways = 1, kind = 2, lab = 1, lt = 2, color = 0xF28A2E)
        add("Braseiro Decorativo", "Braseiro de metal com brasas. Toque pra apagar ou acender.", a(0.18, 0.0, 0.18, 0.82, 0.72, 0.82),
            parts(b(M, 0.22, 0.0, 0.22, 0.28, 0.38, 0.28), b(M, 0.72, 0.0, 0.22, 0.78, 0.38, 0.28), b(M, 0.22, 0.0, 0.72, 0.28, 0.38, 0.78), b(M, 0.72, 0.0, 0.72, 0.78, 0.38, 0.78),
                cone(C0, 0.5, 0.5, 0.22, 0.38, 0.36, 0.64), g(L, 0.26, 0.62, 0.26, 0.74, 0.68, 0.74)),
            alt = parts(b(M, 0.22, 0.0, 0.22, 0.28, 0.38, 0.28), b(M, 0.72, 0.0, 0.22, 0.78, 0.38, 0.28), b(M, 0.22, 0.0, 0.72, 0.28, 0.38, 0.78), b(M, 0.72, 0.0, 0.72, 0.78, 0.38, 0.78),
                cone(C0, 0.5, 0.5, 0.22, 0.38, 0.36, 0.64), b(K, 0.26, 0.62, 0.26, 0.74, 0.66, 0.74)),
            ways = 8, kind = 2, lab = 1, lt = 2, mat = 5, color = 0x4A4A4A)
        add("Tocha de Jardim", "Tocha de jardim com chama. Toque pra apagar ou acender.", a(0.4, 0.0, 0.4, 0.6, 0.95, 0.6),
            parts(b(WD, 0.45, 0.0, 0.45, 0.55, 0.7, 0.55), b(M, 0.4, 0.66, 0.4, 0.6, 0.74, 0.6), g(L, 0.42, 0.74, 0.42, 0.58, 0.95, 0.58)),
            alt = parts(b(WD, 0.45, 0.0, 0.45, 0.55, 0.7, 0.55), b(M, 0.4, 0.66, 0.4, 0.6, 0.74, 0.6), b(K, 0.44, 0.74, 0.44, 0.56, 0.8, 0.56)),
            fem = true, ways = 1, kind = 2, lab = 1, lt = 2, color = 0xF28A2E, sy = 1.6)
        add("Lanterna de Jardim", "Lanterna de chão com luz quentinha. Toque pra ligar e desligar.", a(0.3, 0.0, 0.3, 0.7, 0.86, 0.7),
            parts(b(M, 0.3, 0.0, 0.3, 0.7, 0.08, 0.7), b(M, 0.46, 0.08, 0.46, 0.54, 0.5, 0.54), g(L, 0.34, 0.5, 0.34, 0.66, 0.78, 0.66), b(C0, 0.3, 0.78, 0.3, 0.7, 0.86, 0.7)),
            fem = true, kind = 2, lab = 0, color = 0xF5D63B)
        add("Balizador de Jardim", "Luminária baixa pra iluminar caminhos. Toque pra ligar e desligar.", a(0.34, 0.0, 0.34, 0.66, 0.6, 0.66),
            parts(b(M, 0.44, 0.0, 0.44, 0.56, 0.4, 0.56), g(L, 0.36, 0.4, 0.36, 0.64, 0.55, 0.64), b(C0, 0.34, 0.55, 0.34, 0.66, 0.6, 0.66)),
            kind = 2, lab = 0, color = 0x9AD93E)
        add("Poste de Rua", "Poste com luminária. Toque pra ligar e desligar.", a(0.4, 0.0, 0.4, 0.6, 1.0, 0.6),
            parts(b(K, 0.45, 0.0, 0.45, 0.55, 0.92, 0.55), b(K, 0.35, 0.92, 0.45, 0.55, 0.98, 0.55), g(L, 0.3, 0.86, 0.42, 0.5, 0.93, 0.58)),
            ways = 1, kind = 2, lab = 0, mat = 5, color = 0x2A2C30, sy = 3.0)
        add("Holofote", "Holofote de chão com base. Toque pra ligar e desligar.", a(0.28, 0.0, 0.28, 0.72, 0.55, 0.72),
            parts(b(M, 0.3, 0.0, 0.3, 0.7, 0.1, 0.7), b(M, 0.46, 0.1, 0.46, 0.54, 0.3, 0.54), b(K, 0.28, 0.3, 0.3, 0.72, 0.55, 0.7), g(L, 0.3, 0.32, 0.7, 0.7, 0.52, 0.72)),
            ways = 1, kind = 2, lab = 0, mat = 5, color = 0x2A2C30)
        add("Lanterna de Camping", "Lanterna de camping com alça. Toque pra ligar e desligar.", a(0.3, 0.0, 0.3, 0.7, 0.55, 0.7),
            parts(b(M, 0.3, 0.0, 0.3, 0.7, 0.04, 0.7), g(L, 0.35, 0.04, 0.35, 0.65, 0.4, 0.65), b(C0, 0.3, 0.4, 0.3, 0.7, 0.46, 0.7), b(M, 0.4, 0.46, 0.48, 0.6, 0.55, 0.52)),
            fem = true, small = true, kind = 2, lab = 0, color = 0xE04040)
        add("Letreiro Luminoso", "Letreiro de chão iluminado. Toque pra ligar e desligar.", a(0.06, 0.0, 0.4, 0.94, 0.84, 0.6),
            parts(b(K, 0.1, 0.0, 0.4, 0.9, 0.08, 0.6), b(C0, 0.06, 0.16, 0.42, 0.94, 0.84, 0.58), g(L, 0.1, 0.2, 0.44, 0.9, 0.8, 0.56)),
            kind = 2, lab = 0, color = 0xF59B3D)
        add("Caixa de Som Externa", "Caixa de som pra festa na piscina. Toque pra ligar e desligar o painel (som ainda não implementado).", a(0.22, 0.0, 0.3, 0.78, 0.85, 0.7),
            parts(b(K, 0.22, 0.0, 0.3, 0.78, 0.85, 0.7), b(M, 0.3, 0.12, 0.7, 0.7, 0.45, 0.72), b(M, 0.4, 0.55, 0.7, 0.6, 0.72, 0.72), g(L, 0.46, 0.78, 0.7, 0.54, 0.82, 0.72)),
            alt = parts(b(K, 0.22, 0.0, 0.3, 0.78, 0.85, 0.7), b(M, 0.3, 0.12, 0.7, 0.7, 0.45, 0.72), b(M, 0.4, 0.55, 0.7, 0.6, 0.72, 0.72), b(K, 0.46, 0.78, 0.7, 0.54, 0.82, 0.72)),
            fem = true, ways = 8, small = true, kind = 2, lab = 0, mat = 5, color = 0x2A2C30)
        add("Chuveiro Externo", "Chuveiro de poste pra quem sai da piscina. Toque pra ligar a água.", a(0.4, 0.0, 0.4, 0.6, 1.0, 0.6),
            parts(b(M, 0.45, 0.0, 0.45, 0.55, 1.0, 0.55), b(M, 0.3, 0.92, 0.4, 0.7, 1.0, 0.6), g(L, 0.4, 0.1, 0.4, 0.6, 0.92, 0.6), g(L, 0.3, 0.0, 0.3, 0.7, 0.03, 0.7)),
            alt = parts(b(M, 0.45, 0.0, 0.45, 0.55, 1.0, 0.55), b(M, 0.3, 0.92, 0.4, 0.7, 1.0, 0.6)),
            ways = 1, kind = 2, lab = 0, lt = 1, inv = true, mat = 5, color = 0xBFD8E8, sy = 2.0)
        add("Fonte de Jardim", "Fonte de pedra com água corrente. Toque pra ligar e desligar a água.", a(0.08, 0.0, 0.08, 0.92, 0.62, 0.92),
            parts(y(D, 0.5, 0.5, 0.42, 0.0, 0.18), y(L, 0.5, 0.5, 0.36, 0.16, 0.2), y(D, 0.5, 0.5, 0.1, 0.18, 0.6), y(D, 0.5, 0.5, 0.24, 0.55, 0.62), y(L, 0.5, 0.5, 0.2, 0.6, 0.64), g(L, 0.46, 0.62, 0.46, 0.54, 0.95, 0.54)),
            alt = parts(y(D, 0.5, 0.5, 0.42, 0.0, 0.18), b(K, 0.3, 0.17, 0.3, 0.7, 0.19, 0.7), y(D, 0.5, 0.5, 0.1, 0.18, 0.6), y(D, 0.5, 0.5, 0.24, 0.55, 0.62)),
            fem = true, ways = 1, kind = 2, lab = 0, lt = 1, mat = 1, color = 0xD8D8D8)
        add("Churrasqueira a Carvão", "Churrasqueira com brasas. Toque pra apagar ou acender o carvão.", a(0.1, 0.0, 0.1, 0.9, 0.74, 0.9),
            parts(b(M, 0.12, 0.0, 0.12, 0.18, 0.4, 0.18), b(M, 0.82, 0.0, 0.12, 0.88, 0.4, 0.18), b(M, 0.12, 0.0, 0.82, 0.18, 0.4, 0.88), b(M, 0.82, 0.0, 0.82, 0.88, 0.4, 0.88),
                y(C0, 0.5, 0.5, 0.4, 0.4, 0.64), g(L, 0.2, 0.62, 0.2, 0.8, 0.67, 0.8), b(M, 0.14, 0.7, 0.14, 0.86, 0.74, 0.86)),
            alt = parts(b(M, 0.12, 0.0, 0.12, 0.18, 0.4, 0.18), b(M, 0.82, 0.0, 0.12, 0.88, 0.4, 0.18), b(M, 0.12, 0.0, 0.82, 0.18, 0.4, 0.88), b(M, 0.82, 0.0, 0.82, 0.88, 0.4, 0.88),
                y(C0, 0.5, 0.5, 0.4, 0.4, 0.64), b(K, 0.2, 0.62, 0.2, 0.8, 0.66, 0.8), b(M, 0.14, 0.7, 0.14, 0.86, 0.74, 0.86)),
            fem = true, kind = 2, lab = 1, lt = 2, mat = 5, color = 0x2A2C30)
        add("Fogareiro de Camping", "Fogareiro portátil com panelinha. Toque pra apagar ou acender.", a(0.28, 0.0, 0.28, 0.72, 0.4, 0.72),
            parts(b(M, 0.3, 0.0, 0.3, 0.7, 0.12, 0.7), g(L, 0.38, 0.12, 0.38, 0.62, 0.2, 0.62), y(M, 0.5, 0.5, 0.22, 0.2, 0.4)),
            alt = parts(b(M, 0.3, 0.0, 0.3, 0.7, 0.12, 0.7), y(M, 0.5, 0.5, 0.22, 0.2, 0.4)),
            ways = 1, small = true, kind = 2, lab = 1, lt = 2, mat = 5, color = 0xBFBFBF)
        add("Forno de Pizza", "Forno de tijolo pra assar pizza. Toque pra apagar ou acender o fogo.", a(0.04, 0.0, 0.04, 0.96, 0.95, 0.96),
            parts(b(D, 0.04, 0.0, 0.04, 0.96, 0.4, 0.96), b(C0, 0.08, 0.4, 0.08, 0.92, 0.7, 0.92), b(C0, 0.2, 0.7, 0.2, 0.8, 0.86, 0.8), b(K, 0.38, 0.86, 0.38, 0.62, 0.95, 0.62),
                b(K, 0.28, 0.42, 0.9, 0.72, 0.64, 0.93), g(L, 0.32, 0.44, 0.93, 0.68, 0.6, 0.945)),
            alt = parts(b(D, 0.04, 0.0, 0.04, 0.96, 0.4, 0.96), b(C0, 0.08, 0.4, 0.08, 0.92, 0.7, 0.92), b(C0, 0.2, 0.7, 0.2, 0.8, 0.86, 0.8), b(K, 0.38, 0.86, 0.38, 0.62, 0.95, 0.62),
                b(K, 0.28, 0.42, 0.9, 0.72, 0.64, 0.93), b(K, 0.32, 0.44, 0.93, 0.68, 0.6, 0.945)),
            ways = 8, kind = 2, lab = 1, lt = 2, mat = 1, color = 0xB5503C)

        // ===================== ABRIGO E CAMPING =====================
        grp = "ABRIGO E CAMPING"
        add("Guarda-Sol", "Guarda-sol de haste. Toque pra fechar ou abrir.", a(0.42, 0.0, 0.42, 0.58, 0.85, 0.58),
            parts(b(W, 0.46, 0.0, 0.46, 0.54, 0.82, 0.54), cone(C0, 0.5, 0.5, 0.5, 0.12, 0.66, 0.88), cone(C1, 0.5, 0.5, 0.12, 0.04, 0.88, 1.0)),
            alt = parts(b(W, 0.46, 0.0, 0.46, 0.54, 0.82, 0.54), b(C0, 0.44, 0.45, 0.44, 0.56, 0.85, 0.56)),
            ways = 8, kind = 2, lab = 2, mat = 3, color = 0xF59B3D, sy = 2.4)
        add("Tenda de Camping", "Tenda de camping de lona. Toque pra abrir a entrada.", a(0.06, 0.0, 0.04, 0.94, 0.85, 0.96),
            parts(b(C0, 0.06, 0.0, 0.04, 0.94, 0.22, 0.96), b(C1, 0.18, 0.22, 0.04, 0.82, 0.44, 0.96), b(C0, 0.3, 0.44, 0.04, 0.7, 0.66, 0.96), b(C1, 0.42, 0.66, 0.04, 0.58, 0.85, 0.96), b(C1, 0.36, 0.0, 0.96, 0.64, 0.4, 0.975)),
            alt = parts(b(C0, 0.06, 0.0, 0.04, 0.94, 0.22, 0.96), b(C1, 0.18, 0.22, 0.04, 0.82, 0.44, 0.96), b(C0, 0.3, 0.44, 0.04, 0.7, 0.66, 0.96), b(C1, 0.42, 0.66, 0.04, 0.58, 0.85, 0.96)),
            fem = true, fabric = true, kind = 1, open = a(0.96, 0.36, 0.0, 0.64, 0.4), mat = 3, color = 0xF59B3D)
        add("Rede de Vôlei", "Rede de vôlei de praia com dois postes. Toque pra recolher ou instalar a rede.", a(0.0, 0.0, 0.4, 1.0, 1.0, 0.6),
            parts(b(W, 0.02, 0.0, 0.45, 0.1, 1.0, 0.55), b(W, 0.9, 0.0, 0.45, 0.98, 1.0, 0.55), b(D, 0.1, 0.55, 0.49, 0.9, 0.95, 0.51), b(C0, 0.1, 0.9, 0.48, 0.9, 0.95, 0.52)),
            alt = parts(b(W, 0.02, 0.0, 0.45, 0.1, 1.0, 0.55), b(W, 0.9, 0.0, 0.45, 0.98, 1.0, 0.55), b(C0, 0.1, 0.0, 0.45, 0.3, 0.08, 0.55)),
            fem = true, kind = 2, lab = 4, color = 0xF2F2EE, sy = 2.4)
        add("Varal Externo", "Varal com roupas penduradas. Toque pra recolher ou pendurar as roupas.", a(0.0, 0.0, 0.4, 1.0, 1.0, 0.6),
            parts(b(M, 0.02, 0.0, 0.45, 0.08, 1.0, 0.55), b(M, 0.92, 0.0, 0.45, 0.98, 1.0, 0.55), b(D, 0.08, 0.94, 0.49, 0.92, 0.96, 0.51),
                b(C0, 0.14, 0.5, 0.48, 0.36, 0.94, 0.52), b(C1, 0.42, 0.6, 0.48, 0.58, 0.94, 0.52), b(C0, 0.64, 0.45, 0.48, 0.86, 0.94, 0.52)),
            alt = parts(b(M, 0.02, 0.0, 0.45, 0.08, 1.0, 0.55), b(M, 0.92, 0.0, 0.45, 0.98, 1.0, 0.55), b(D, 0.08, 0.94, 0.49, 0.92, 0.96, 0.51)),
            ways = 8, kind = 2, lab = 4, mat = 5, color = 0xBFBFBF, sy = 2.0)

        // ===================== ETAPA 3: INTERIOR (eletrodomésticos, banheiro, lavanderia, lazer, oficina, fazenda) =====================
        // --- eletrodomésticos pequenos (ficam em cima de balcões; ligam e desligam) ---
        grp = "ELETRODOMÉSTICOS"
        add("Air Fryer", "Fritadeira elétrica sem óleo. Toque pra ligar e desligar.", a(0.2, 0.0, 0.2, 0.8, 0.6, 0.8),
            parts(b(C0, 0.2, 0.0, 0.2, 0.8, 0.55, 0.8), b(K, 0.2, 0.55, 0.2, 0.8, 0.6, 0.8), b(M, 0.4, 0.18, 0.8, 0.6, 0.28, 0.9), g(L, 0.64, 0.46, 0.8, 0.76, 0.5, 0.82)),
            fem = true, small = true, kind = 2, dimOff = true, mat = 5, color = 0x2A2C30)
        add("Liquidificador", "Liquidificador com copo cheio de suco. Toque pra ligar e desligar.", a(0.3, 0.0, 0.3, 0.7, 0.84, 0.7),
            parts(b(C0, 0.3, 0.0, 0.3, 0.7, 0.3, 0.7), b(L, 0.32, 0.3, 0.32, 0.68, 0.78, 0.68), b(K, 0.3, 0.78, 0.3, 0.7, 0.84, 0.7), g(L, 0.4, 0.1, 0.7, 0.6, 0.16, 0.72)),
            small = true, kind = 2, dimOff = true, lt = 1, mat = 5, color = 0xE04040)
        add("Cafeteira", "Cafeteira elétrica com jarra. Toque pra ligar e desligar.", a(0.2, 0.0, 0.25, 0.8, 0.8, 0.75),
            parts(b(C0, 0.2, 0.0, 0.25, 0.8, 0.1, 0.75), b(C0, 0.2, 0.1, 0.25, 0.8, 0.8, 0.4), b(C0, 0.2, 0.7, 0.25, 0.8, 0.8, 0.75), y(K, 0.5, 0.58, 0.12, 0.1, 0.45), g(L, 0.62, 0.72, 0.745, 0.74, 0.78, 0.76)),
            fem = true, small = true, kind = 2, dimOff = true, mat = 5, color = 0x2A2C30)
        add("Torradeira", "Torradeira de duas fatias. Toque pra ligar e desligar.", a(0.2, 0.0, 0.3, 0.8, 0.45, 0.7),
            parts(b(C0, 0.2, 0.0, 0.3, 0.8, 0.4, 0.7), b(K, 0.28, 0.4, 0.36, 0.72, 0.44, 0.46), b(K, 0.28, 0.4, 0.54, 0.72, 0.44, 0.64), b(M, 0.84, 0.2, 0.45, 0.9, 0.26, 0.55), g(L, 0.24, 0.3, 0.7, 0.3, 0.34, 0.72)),
            fem = true, small = true, kind = 2, dimOff = true, mat = 5, color = 0xBFBFBF)
        add("Chaleira Elétrica", "Chaleira elétrica com base luminosa. Toque pra ligar e desligar.", a(0.25, 0.0, 0.25, 0.75, 0.65, 0.75),
            parts(cone(C0, 0.5, 0.5, 0.28, 0.2, 0.0, 0.5), cone(K, 0.5, 0.5, 0.2, 0.08, 0.5, 0.62), b(K, 0.7, 0.2, 0.45, 0.78, 0.55, 0.55), g(L, 0.3, 0.0, 0.3, 0.7, 0.04, 0.7)),
            fem = true, small = true, kind = 2, dimOff = true, mat = 5, color = 0xBFBFBF)
        add("Batedeira", "Batedeira com tigela de metal. Toque pra ligar e desligar.", a(0.2, 0.0, 0.2, 0.8, 0.7, 0.85),
            parts(b(C0, 0.25, 0.0, 0.5, 0.75, 0.1, 0.85), b(C0, 0.25, 0.1, 0.7, 0.75, 0.65, 0.85), b(C0, 0.25, 0.55, 0.35, 0.75, 0.65, 0.85), y(M, 0.5, 0.6, 0.22, 0.1, 0.36), g(L, 0.6, 0.56, 0.84, 0.7, 0.6, 0.86)),
            fem = true, small = true, kind = 2, dimOff = true, mat = 5, color = 0xF5A3C4)
        add("Sanduicheira", "Sanduicheira elétrica. Toque pra ligar e desligar.", a(0.15, 0.0, 0.2, 0.85, 0.2, 0.8),
            parts(b(C0, 0.15, 0.0, 0.2, 0.85, 0.1, 0.8), b(C0, 0.15, 0.1, 0.2, 0.85, 0.18, 0.8), b(K, 0.2, 0.1, 0.25, 0.8, 0.11, 0.75), g(L, 0.78, 0.04, 0.8, 0.84, 0.08, 0.82)),
            fem = true, small = true, kind = 2, dimOff = true, mat = 5, color = 0x2A2C30)
        add("Ferro de Passar", "Ferro de passar roupa. Toque pra ligar e desligar.", a(0.25, 0.0, 0.25, 0.75, 0.3, 0.75),
            parts(b(M, 0.28, 0.0, 0.22, 0.72, 0.06, 0.78), b(C0, 0.3, 0.06, 0.3, 0.7, 0.2, 0.7), b(K, 0.38, 0.18, 0.3, 0.62, 0.28, 0.62), g(L, 0.44, 0.12, 0.7, 0.56, 0.16, 0.72)),
            small = true, kind = 2, dimOff = true, mat = 5, color = 0x6DB9EE)
        add("Roteador", "Roteador de internet com luzinhas. Toque pra ligar e desligar.", a(0.2, 0.0, 0.3, 0.8, 0.5, 0.7),
            parts(b(K, 0.2, 0.0, 0.3, 0.8, 0.12, 0.7), b(K, 0.24, 0.12, 0.32, 0.28, 0.5, 0.36), b(K, 0.72, 0.12, 0.32, 0.76, 0.5, 0.36), g(L, 0.3, 0.04, 0.7, 0.34, 0.08, 0.72), g(L, 0.42, 0.04, 0.7, 0.46, 0.08, 0.72), g(L, 0.54, 0.04, 0.7, 0.58, 0.08, 0.72)),
            ways = 1, small = true, kind = 2, dimOff = true, mat = 5, color = 0x2A2C30)
        add("Impressora", "Impressora de mesa. Toque pra ligar e desligar.", a(0.12, 0.0, 0.2, 0.88, 0.34, 0.8),
            parts(b(C0, 0.12, 0.0, 0.2, 0.88, 0.26, 0.8), b(D, 0.2, 0.26, 0.4, 0.8, 0.28, 0.78), b(K, 0.2, 0.1, 0.8, 0.8, 0.14, 0.82), g(L, 0.76, 0.2, 0.8, 0.82, 0.24, 0.82)),
            fem = true, small = true, kind = 2, dimOff = true, mat = 5, color = 0xBFBFBF)
        add("Notebook", "Notebook com tela acesa. Toque pra fechar e abrir a tampa.", a(0.15, 0.0, 0.2, 0.85, 0.6, 0.8),
            parts(b(C0, 0.15, 0.0, 0.25, 0.85, 0.04, 0.8), b(K, 0.15, 0.04, 0.2, 0.85, 0.6, 0.26), g(L, 0.18, 0.08, 0.26, 0.82, 0.56, 0.27)),
            alt = parts(b(C0, 0.15, 0.0, 0.25, 0.85, 0.04, 0.8), b(K, 0.15, 0.04, 0.25, 0.85, 0.08, 0.8)),
            small = true, kind = 2, lab = 2, lt = 1, mat = 5, color = 0xBFBFBF)
        add("Monitor", "Monitor de computador. Toque pra ligar e desligar a tela.", a(0.1, 0.0, 0.3, 0.9, 0.75, 0.7),
            parts(b(M, 0.3, 0.0, 0.35, 0.7, 0.04, 0.65), b(M, 0.46, 0.04, 0.45, 0.54, 0.22, 0.55), b(K, 0.08, 0.22, 0.44, 0.92, 0.74, 0.54), g(L, 0.12, 0.26, 0.54, 0.88, 0.7, 0.55)),
            ways = 1, small = true, kind = 2, dimOff = true, lt = 1, mat = 5, color = 0x2A2C30)
        add("Videogame", "Console de videogame com luz de ligado. Toque pra ligar e desligar.", a(0.2, 0.0, 0.25, 0.8, 0.14, 0.75),
            parts(b(K, 0.2, 0.0, 0.25, 0.8, 0.12, 0.75), b(C0, 0.2, 0.12, 0.25, 0.8, 0.14, 0.75), g(L, 0.26, 0.04, 0.75, 0.34, 0.08, 0.77)),
            small = true, kind = 2, dimOff = true, lt = 1, mat = 5, color = 0x4C74C4)
        add("Projetor", "Projetor com lente acesa. Toque pra ligar e desligar.", a(0.2, 0.0, 0.25, 0.8, 0.3, 0.8),
            parts(b(C0, 0.2, 0.0, 0.25, 0.8, 0.26, 0.75), b(K, 0.28, 0.06, 0.75, 0.5, 0.22, 0.8), g(L, 0.32, 0.1, 0.8, 0.46, 0.18, 0.82)),
            small = true, kind = 2, dimOff = true, mat = 5, color = 0xF2F2EE)
        add("Despertador", "Despertador com sinos e visor. Toque pra ligar e desligar o alarme.", a(0.3, 0.0, 0.35, 0.7, 0.42, 0.65),
            parts(b(C0, 0.3, 0.0, 0.35, 0.7, 0.34, 0.65), b(M, 0.3, 0.34, 0.4, 0.42, 0.42, 0.52), b(M, 0.58, 0.34, 0.4, 0.7, 0.42, 0.52), g(L, 0.36, 0.14, 0.65, 0.64, 0.26, 0.67)),
            small = true, kind = 2, dimOff = true, lt = 2, color = 0xE04040)
        add("Máquina de Costura", "Máquina de costura com luzinha. Toque pra ligar e desligar.", a(0.15, 0.0, 0.3, 0.85, 0.6, 0.7),
            parts(b(C0, 0.15, 0.0, 0.3, 0.85, 0.1, 0.7), b(C0, 0.15, 0.1, 0.3, 0.35, 0.55, 0.7), b(C0, 0.15, 0.45, 0.3, 0.85, 0.6, 0.5), b(M, 0.6, 0.1, 0.4, 0.64, 0.45, 0.44), g(L, 0.2, 0.5, 0.5, 0.3, 0.56, 0.6)),
            fem = true, small = true, kind = 2, dimOff = true, mat = 5, color = 0xF2F2EE)
        add("Carregador de Bateria", "Carregador de bateria de oficina. Toque pra ligar e desligar.", a(0.2, 0.0, 0.3, 0.8, 0.35, 0.7),
            parts(b(C0, 0.2, 0.0, 0.3, 0.8, 0.3, 0.7), b(K, 0.26, 0.3, 0.4, 0.34, 0.36, 0.48), b(K, 0.66, 0.3, 0.4, 0.74, 0.36, 0.48), g(L, 0.4, 0.18, 0.7, 0.6, 0.24, 0.72)),
            small = true, kind = 2, dimOff = true, mat = 5, color = 0xF5D63B)
        add("Compressor de Ar", "Compressor de ar com tanque e rodinhas. Toque pra ligar e desligar.", a(0.1, 0.0, 0.2, 0.9, 0.7, 0.8),
            parts(b(C0, 0.1, 0.2, 0.2, 0.9, 0.5, 0.8), b(K, 0.14, 0.0, 0.2, 0.26, 0.2, 0.3), b(K, 0.74, 0.0, 0.2, 0.86, 0.2, 0.3), b(M, 0.2, 0.5, 0.4, 0.5, 0.7, 0.6), g(L, 0.7, 0.4, 0.8, 0.8, 0.46, 0.82)),
            small = false, kind = 2, dimOff = true, mat = 5, color = 0xE04040)

        // --- eletrodomésticos e móveis grandes (abrem) ---
        grp = "CASA: BANHEIRO, LAVANDERIA E LAZER"
        add("Freezer Horizontal", "Freezer de tampa pra congelados. Toque pra abrir e fechar.", a(0.04, 0.0, 0.1, 0.96, 0.8, 0.9),
            parts(b(D, 0.04, 0.0, 0.1, 0.96, 0.75, 0.9), b(M, 0.04, 0.75, 0.1, 0.96, 0.8, 0.9), b(K, 0.4, 0.5, 0.9, 0.6, 0.56, 0.93)),
            lid = a(0.04, 0.1, 0.96, 0.9, 0.8), ways = 1, kind = 1, mat = 5, color = 0xF6F6F2)
        add("Lava-Louças", "Lava-louças embutida com painel. Toque pra abrir a porta.", a(0.04, 0.0, 0.04, 0.96, 0.9, 0.96),
            parts(b(M, 0.04, 0.0, 0.04, 0.96, 0.9, 0.96), b(K, 0.1, 0.78, 0.96, 0.9, 0.88, 0.975), b(C0, 0.08, 0.08, 0.96, 0.92, 0.74, 0.975), b(K, 0.2, 0.68, 0.975, 0.8, 0.72, 0.99)),
            open = a(0.975, 0.08, 0.08, 0.92, 0.74), fem = true, kind = 1, mat = 5, color = 0xBFBFBF)
        add("Secadora", "Secadora de roupas com janela redonda. Toque pra abrir a porta.", a(0.04, 0.0, 0.04, 0.96, 0.9, 0.96),
            parts(b(D, 0.04, 0.0, 0.04, 0.96, 0.9, 0.96), b(C0, 0.04, 0.74, 0.04, 0.96, 0.9, 0.96), b(K, 0.3, 0.2, 0.96, 0.7, 0.7, 0.975), b(K, 0.1, 0.78, 0.96, 0.9, 0.86, 0.975)),
            open = a(0.975, 0.28, 0.18, 0.72, 0.72), fem = true, kind = 1, mat = 5, color = 0xF6F6F2)
        add("Forno Elétrico", "Forno de embutir com janela. Toque pra abrir a porta.", a(0.04, 0.0, 0.04, 0.96, 0.9, 0.96),
            parts(b(M, 0.04, 0.0, 0.04, 0.96, 0.9, 0.96), b(C0, 0.04, 0.8, 0.96, 0.96, 0.9, 0.975), b(K, 0.12, 0.2, 0.96, 0.88, 0.7, 0.975), b(M, 0.15, 0.74, 0.975, 0.85, 0.78, 0.99)),
            open = a(0.975, 0.12, 0.2, 0.88, 0.7), kind = 1, mat = 5, color = 0xBFBFBF)
        add("Cesto de Roupa", "Cesto de roupa suja com tampa. Toque pra abrir e fechar.", a(0.2, 0.0, 0.2, 0.8, 0.66, 0.8),
            parts(y(C0, 0.5, 0.5, 0.3, 0.0, 0.6), y(C1, 0.5, 0.5, 0.32, 0.32, 0.6, 0.66)),
            lid = a(0.2, 0.2, 0.8, 0.8, 0.6), fabric = true, kind = 1, mat = 3, color = 0xF5D63B)
        add("Gabinete de Banheiro", "Gabinete de duas portas com tampo de pedra. Toque pra abrir.", a(0.02, 0.0, 0.02, 0.98, 0.92, 0.98),
            parts(b(C0, 0.02, 0.0, 0.02, 0.98, 0.88, 0.98), b(D, 0.0, 0.88, 0.0, 1.0, 0.92, 1.0), b(C1, 0.08, 0.06, 0.97, 0.48, 0.82, 0.99), b(C1, 0.52, 0.06, 0.97, 0.92, 0.82, 0.99), b(M, 0.46, 0.4, 0.985, 0.48, 0.5, 0.995), b(M, 0.52, 0.4, 0.985, 0.54, 0.5, 0.995)),
            open = a(0.99, 0.08, 0.06, 0.92, 0.82), kind = 1, color = 0xF2F2EE)
        add("Chuveiro Interno", "Ducha com piso de azulejo. Toque pra ligar a água.", a(0.4, 0.0, 0.4, 0.6, 1.0, 0.6),
            parts(b(D, 0.05, 0.0, 0.05, 0.95, 0.03, 0.95), b(M, 0.46, 0.03, 0.46, 0.54, 1.0, 0.54), b(M, 0.3, 0.92, 0.4, 0.7, 1.0, 0.6), g(L, 0.4, 0.1, 0.4, 0.6, 0.92, 0.6)),
            alt = parts(b(D, 0.05, 0.0, 0.05, 0.95, 0.03, 0.95), b(M, 0.46, 0.03, 0.46, 0.54, 1.0, 0.54), b(M, 0.3, 0.92, 0.4, 0.7, 1.0, 0.6)),
            ways = 1, kind = 2, lt = 1, inv = true, mat = 5, color = 0xBFD8E8, sy = 2.0)
        add("Tanque de Lavar", "Tanque de lavar roupa com torneira. Toque pra abrir e fechar a água.", a(0.04, 0.0, 0.1, 0.96, 0.95, 0.9),
            parts(b(D, 0.04, 0.0, 0.1, 0.96, 0.55, 0.9), b(C0, 0.04, 0.55, 0.1, 0.96, 0.7, 0.9), b(L, 0.1, 0.66, 0.16, 0.9, 0.7, 0.84), b(M, 0.46, 0.7, 0.14, 0.54, 0.9, 0.2), b(M, 0.46, 0.86, 0.14, 0.54, 0.9, 0.36), g(L, 0.48, 0.7, 0.34, 0.52, 0.86, 0.36)),
            alt = parts(b(D, 0.04, 0.0, 0.1, 0.96, 0.55, 0.9), b(C0, 0.04, 0.55, 0.1, 0.96, 0.7, 0.9), b(K, 0.1, 0.66, 0.16, 0.9, 0.7, 0.84), b(M, 0.46, 0.7, 0.14, 0.54, 0.9, 0.2), b(M, 0.46, 0.86, 0.14, 0.54, 0.9, 0.36)),
            kind = 2, lt = 1, inv = true, mat = 5, color = 0xBFD8E8)
        add("Torneira de Jardim", "Torneira de parede com volante. Toque pra abrir e fechar a água.", a(0.4, 0.0, 0.4, 0.6, 0.7, 0.6),
            parts(b(M, 0.46, 0.0, 0.46, 0.54, 0.5, 0.54), b(M, 0.46, 0.5, 0.46, 0.54, 0.58, 0.7), b(C0, 0.4, 0.6, 0.46, 0.6, 0.68, 0.54), g(L, 0.48, 0.1, 0.66, 0.52, 0.5, 0.7)),
            alt = parts(b(M, 0.46, 0.0, 0.46, 0.54, 0.5, 0.54), b(M, 0.46, 0.5, 0.46, 0.54, 0.58, 0.7), b(C0, 0.4, 0.6, 0.46, 0.6, 0.68, 0.54)),
            fem = true, small = true, kind = 2, lt = 1, inv = true, mat = 5, color = 0xBFBFBF)
        add("Tábua de Passar", "Tábua de passar roupa. Toque pra dobrar e abrir.", a(0.1, 0.0, 0.04, 0.9, 0.86, 0.96),
            parts(b(C0, 0.1, 0.8, 0.04, 0.9, 0.86, 0.96), b(M, 0.2, 0.0, 0.4, 0.26, 0.8, 0.46), b(M, 0.74, 0.0, 0.4, 0.8, 0.8, 0.46), b(M, 0.2, 0.0, 0.54, 0.8, 0.04, 0.6)),
            alt = parts(b(C0, 0.35, 0.0, 0.4, 0.65, 0.9, 0.46), b(M, 0.3, 0.0, 0.46, 0.7, 0.06, 0.5)),
            fem = true, kind = 2, lab = 3, mat = 5, color = 0x4C74C4)
        add("Ventilador de Pé", "Ventilador com pedestal. Toque pra ligar e desligar as pás.", a(0.25, 0.0, 0.25, 0.75, 1.0, 0.75),
            parts(y(M, 0.5, 0.5, 0.22, 0.0, 0.05), b(M, 0.47, 0.05, 0.47, 0.53, 0.75, 0.53), b(K, 0.38, 0.72, 0.38, 0.62, 0.9, 0.62), b(C1, 0.22, 0.62, 0.7, 0.78, 0.98, 0.72)),
            alt = parts(y(M, 0.5, 0.5, 0.22, 0.0, 0.05), b(M, 0.47, 0.05, 0.47, 0.53, 0.75, 0.53), b(K, 0.38, 0.72, 0.38, 0.62, 0.9, 0.62), b(C1, 0.46, 0.62, 0.7, 0.54, 0.98, 0.72), b(C1, 0.28, 0.78, 0.7, 0.72, 0.82, 0.72)),
            kind = 2, mat = 5, color = 0x6DB9EE, sy = 1.5)
        add("Ar-Condicionado Portátil", "Ar-condicionado de chão com visor. Toque pra ligar e desligar.", a(0.2, 0.0, 0.2, 0.8, 0.95, 0.8),
            parts(b(D, 0.2, 0.0, 0.2, 0.8, 0.9, 0.8), b(C0, 0.2, 0.9, 0.2, 0.8, 0.95, 0.8), b(K, 0.26, 0.7, 0.8, 0.74, 0.86, 0.82), g(L, 0.34, 0.9, 0.8, 0.66, 0.93, 0.82)),
            kind = 2, dimOff = true, lt = 1, mat = 5, color = 0xF2F2EE)
        add("Lareira de Tijolo", "Lareira de tijolo com fogo crepitante. Toque pra apagar ou acender.", a(0.0, 0.0, 0.1, 1.0, 1.0, 0.9),
            parts(b(C0, 0.0, 0.0, 0.1, 0.18, 1.0, 0.9), b(C0, 0.82, 0.0, 0.1, 1.0, 1.0, 0.9), b(C0, 0.18, 0.7, 0.1, 0.82, 1.0, 0.9), b(K, 0.18, 0.0, 0.1, 0.82, 0.7, 0.4), b(D, 0.0, 0.0, 0.9, 1.0, 0.05, 1.0),
                g(L, 0.3, 0.05, 0.3, 0.7, 0.35, 0.6), g(L, 0.4, 0.3, 0.35, 0.6, 0.55, 0.55)),
            alt = parts(b(C0, 0.0, 0.0, 0.1, 0.18, 1.0, 0.9), b(C0, 0.82, 0.0, 0.1, 1.0, 1.0, 0.9), b(C0, 0.18, 0.7, 0.1, 0.82, 1.0, 0.9), b(K, 0.18, 0.0, 0.1, 0.82, 0.7, 0.4), b(D, 0.0, 0.0, 0.9, 1.0, 0.05, 1.0), b(K, 0.3, 0.05, 0.3, 0.7, 0.08, 0.6)),
            fem = true, kind = 2, lab = 1, lt = 2, mat = 1, color = 0xB5503C, sy = 1.5)
        add("Aquário", "Aquário iluminado com peixinhos. Toque pra ligar e desligar a luz.", a(0.04, 0.0, 0.2, 0.96, 0.8, 0.8),
            parts(b(WD, 0.04, 0.0, 0.2, 0.96, 0.3, 0.8), g(L, 0.08, 0.3, 0.24, 0.92, 0.74, 0.76), b(C0, 0.3, 0.5, 0.4, 0.42, 0.56, 0.5), b(C1, 0.6, 0.6, 0.45, 0.7, 0.64, 0.52), b(K, 0.06, 0.74, 0.22, 0.94, 0.8, 0.78)),
            alt = parts(b(WD, 0.04, 0.0, 0.2, 0.96, 0.3, 0.8), b(L, 0.08, 0.3, 0.24, 0.92, 0.74, 0.76), b(C0, 0.3, 0.5, 0.4, 0.42, 0.56, 0.5), b(C1, 0.6, 0.6, 0.45, 0.7, 0.64, 0.52), b(K, 0.06, 0.74, 0.22, 0.94, 0.8, 0.78)),
            small = false, kind = 2, lt = 1, color = 0xF59B3D)
        add("Fliperama", "Máquina de fliperama com tela e botões. Toque pra ligar e desligar.", a(0.1, 0.0, 0.1, 0.9, 1.0, 0.9),
            parts(b(C0, 0.1, 0.0, 0.1, 0.9, 1.0, 0.9), g(L, 0.2, 0.55, 0.9, 0.8, 0.74, 0.915), g(L, 0.16, 0.84, 0.88, 0.84, 0.96, 0.905), b(K, 0.16, 0.38, 0.7, 0.84, 0.46, 0.92), b(C1, 0.3, 0.46, 0.78, 0.4, 0.5, 0.86), b(C1, 0.6, 0.46, 0.78, 0.7, 0.5, 0.86)),
            kind = 2, dimOff = true, lt = 1, mat = 5, color = 0x4C74C4, sy = 1.7)
        add("Mesa de Pingue-Pongue", "Mesa de pingue-pongue com rede. Toque pra dobrar e abrir.", a(0.0, 0.0, 0.04, 1.0, 0.86, 0.96),
            parts(b(C0, 0.0, 0.8, 0.04, 1.0, 0.86, 0.96), b(D, 0.48, 0.86, 0.0, 0.52, 0.95, 1.0), b(M, 0.05, 0.0, 0.1, 0.1, 0.8, 0.15), b(M, 0.9, 0.0, 0.1, 0.95, 0.8, 0.15), b(M, 0.05, 0.0, 0.85, 0.1, 0.8, 0.9), b(M, 0.9, 0.0, 0.85, 0.95, 0.8, 0.9)),
            alt = parts(b(C0, 0.1, 0.0, 0.46, 0.9, 0.86, 0.54), b(M, 0.1, 0.0, 0.54, 0.9, 0.05, 0.6)),
            fem = true, top = 0.86, kind = 2, lab = 3, mat = 5, color = 0x4C74C4)

        // --- oficina e garagem ---
        grp = "OFICINA"
        add("Bancada de Ferramentas", "Bancada de oficina com gavetas. Toque pra abrir as gavetas.", a(0.0, 0.0, 0.0, 1.0, 0.92, 1.0),
            parts(b(WD, 0.0, 0.86, 0.0, 1.0, 0.92, 1.0), b(M, 0.02, 0.0, 0.04, 0.98, 0.86, 0.96), b(C0, 0.08, 0.5, 0.96, 0.92, 0.8, 0.975), b(C0, 0.08, 0.12, 0.96, 0.92, 0.42, 0.975), b(K, 0.4, 0.62, 0.975, 0.6, 0.66, 0.99), b(K, 0.4, 0.24, 0.975, 0.6, 0.28, 0.99)),
            open = a(0.975, 0.08, 0.12, 0.92, 0.8), fem = true, top = 0.92, kind = 1, mat = 5, color = 0xE04040)
        add("Armário Metálico", "Armário de aço pra equipamentos. Toque pra abrir as portas.", a(0.04, 0.0, 0.04, 0.96, 1.0, 0.96),
            parts(b(M, 0.04, 0.0, 0.04, 0.96, 1.0, 0.96), b(C0, 0.08, 0.06, 0.96, 0.48, 0.94, 0.975), b(C0, 0.52, 0.06, 0.96, 0.92, 0.94, 0.975), b(K, 0.12, 0.8, 0.975, 0.44, 0.86, 0.985), b(K, 0.56, 0.8, 0.975, 0.88, 0.86, 0.985)),
            open = a(0.975, 0.08, 0.06, 0.92, 0.94), kind = 1, mat = 5, color = 0x6E747B, sy = 1.9)
        add("Caixa de Ferramentas", "Caixa de ferramentas com alça. Toque pra abrir a tampa.", a(0.12, 0.0, 0.25, 0.88, 0.4, 0.75),
            parts(b(C0, 0.12, 0.0, 0.25, 0.88, 0.3, 0.75), b(C0, 0.12, 0.3, 0.25, 0.88, 0.4, 0.75), b(M, 0.4, 0.4, 0.45, 0.6, 0.48, 0.55)),
            lid = a(0.12, 0.25, 0.88, 0.75, 0.4), fem = true, small = true, kind = 1, mat = 5, color = 0xE04040)

        // --- fazenda e quintal produtivo ---
        grp = "FAZENDA E QUINTAL"
        add("Composteira", "Composteira de ripas com tampa. Toque pra abrir e fechar.", a(0.1, 0.0, 0.1, 0.9, 0.8, 0.9),
            parts(b(W, 0.1, 0.0, 0.1, 0.9, 0.75, 0.9), b(WD, 0.1, 0.2, 0.08, 0.9, 0.26, 0.1), b(WD, 0.1, 0.5, 0.08, 0.9, 0.56, 0.1), b(C1, 0.06, 0.75, 0.06, 0.94, 0.8, 0.94)),
            lid = a(0.1, 0.1, 0.9, 0.9, 0.75), fem = true, kind = 1, color = 0x9AD93E)
        add("Barril", "Barril de madeira com cintas de metal. Toque pra abrir a tampa.", a(0.12, 0.0, 0.12, 0.88, 0.9, 0.88),
            parts(y(W, 0.5, 0.5, 0.38, 0.0, 0.85), y(M, 0.5, 0.5, 0.4, 0.15, 0.22), y(M, 0.5, 0.5, 0.4, 0.65, 0.72), y(C0, 0.5, 0.5, 0.36, 0.85, 0.9)),
            lid = a(0.14, 0.14, 0.86, 0.86, 0.85), kind = 1, color = 0xB87D45)
        add("Silo Pequeno", "Silo pequeno pra guardar ração e grãos. Toque pra abrir a portinha.", a(0.1, 0.0, 0.1, 0.9, 1.0, 0.9),
            parts(y(C0, 0.5, 0.5, 0.4, 0.0, 0.8), cone(C1, 0.5, 0.5, 0.42, 0.1, 0.8, 1.0), b(M, 0.4, 0.0, 0.88, 0.6, 0.4, 0.9)),
            open = a(0.9, 0.4, 0.0, 0.6, 0.4), kind = 1, mat = 5, color = 0xBFBFBF, sy = 2.5)
        add("Comedouro de Animais", "Tigela de ração. Toque pra encher e esvaziar.", a(0.2, 0.0, 0.2, 0.8, 0.2, 0.8),
            parts(y(C0, 0.5, 0.5, 0.3, 0.0, 0.18), y(WD, 0.5, 0.5, 0.24, 0.24, 0.14, 0.2)),
            alt = parts(y(C0, 0.5, 0.5, 0.3, 0.0, 0.18), y(K, 0.5, 0.5, 0.24, 0.24, 0.16, 0.18)),
            small = true, kind = 2, lab = 5, mat = 3, color = 0xE04040)
        add("Bebedouro de Animais", "Tigela de água. Toque pra encher e esvaziar.", a(0.2, 0.0, 0.2, 0.8, 0.2, 0.8),
            parts(y(C0, 0.5, 0.5, 0.3, 0.0, 0.18), y(L, 0.5, 0.5, 0.25, 0.25, 0.14, 0.2)),
            alt = parts(y(C0, 0.5, 0.5, 0.3, 0.0, 0.18), y(K, 0.5, 0.5, 0.25, 0.25, 0.16, 0.18)),
            small = true, kind = 2, lab = 5, lt = 1, mat = 3, color = 0x6DB9EE)
        add("Cocho", "Cocho de madeira pra ração. Toque pra encher e esvaziar.", a(0.0, 0.0, 0.2, 1.0, 0.45, 0.8),
            parts(b(W, 0.0, 0.0, 0.2, 1.0, 0.1, 0.8), b(W, 0.0, 0.1, 0.2, 1.0, 0.45, 0.28), b(W, 0.0, 0.1, 0.72, 1.0, 0.45, 0.8), b(W, 0.0, 0.1, 0.2, 0.08, 0.45, 0.8), b(W, 0.92, 0.1, 0.2, 1.0, 0.45, 0.8), b(WD, 0.08, 0.1, 0.28, 0.92, 0.36, 0.72)),
            alt = parts(b(W, 0.0, 0.0, 0.2, 1.0, 0.1, 0.8), b(W, 0.0, 0.1, 0.2, 1.0, 0.45, 0.28), b(W, 0.0, 0.1, 0.72, 1.0, 0.45, 0.8), b(W, 0.0, 0.1, 0.2, 0.08, 0.45, 0.8), b(W, 0.92, 0.1, 0.2, 1.0, 0.45, 0.8)),
            ways = 1, kind = 2, lab = 5)
        add("Caixa de Colheita", "Caixa de madeira com legumes e frutas. Toque pra esvaziar e encher.", a(0.1, 0.0, 0.15, 0.9, 0.5, 0.85),
            parts(b(W, 0.1, 0.0, 0.15, 0.9, 0.1, 0.85), b(W, 0.1, 0.1, 0.15, 0.9, 0.45, 0.2), b(W, 0.1, 0.1, 0.8, 0.9, 0.45, 0.85), b(W, 0.1, 0.1, 0.15, 0.15, 0.45, 0.85), b(W, 0.85, 0.1, 0.15, 0.9, 0.45, 0.85),
                b(C0, 0.18, 0.1, 0.24, 0.44, 0.5, 0.48), b(C1, 0.5, 0.1, 0.5, 0.76, 0.52, 0.76), b(C0, 0.58, 0.1, 0.24, 0.82, 0.5, 0.46)),
            alt = parts(b(W, 0.1, 0.0, 0.15, 0.9, 0.1, 0.85), b(W, 0.1, 0.1, 0.15, 0.9, 0.45, 0.2), b(W, 0.1, 0.1, 0.8, 0.9, 0.45, 0.85), b(W, 0.1, 0.1, 0.15, 0.15, 0.45, 0.85), b(W, 0.85, 0.1, 0.15, 0.9, 0.45, 0.85)),
            fem = true, kind = 2, lab = 5, color = 0xE04040)
        add("Comedouro de Pássaros", "Comedouro com telhadinho e sementes. Toque pra encher e esvaziar.", a(0.15, 0.0, 0.15, 0.85, 0.96, 0.85),
            parts(b(WD, 0.46, 0.0, 0.46, 0.54, 0.7, 0.54), b(W, 0.2, 0.7, 0.2, 0.8, 0.76, 0.8), b(W, 0.2, 0.76, 0.2, 0.24, 0.9, 0.24), b(W, 0.76, 0.76, 0.76, 0.8, 0.9, 0.8), b(C0, 0.15, 0.9, 0.15, 0.85, 0.96, 0.85), b(WD, 0.26, 0.76, 0.26, 0.74, 0.8, 0.74)),
            alt = parts(b(WD, 0.46, 0.0, 0.46, 0.54, 0.7, 0.54), b(W, 0.2, 0.7, 0.2, 0.8, 0.76, 0.8), b(W, 0.2, 0.76, 0.2, 0.24, 0.9, 0.24), b(W, 0.76, 0.76, 0.76, 0.8, 0.9, 0.8), b(C0, 0.15, 0.9, 0.15, 0.85, 0.96, 0.85)),
            kind = 2, lab = 5, color = 0xC46A5A)
        add("Banheira de Pássaros", "Bacia de pedra com água. Toque pra encher e esvaziar.", a(0.2, 0.0, 0.2, 0.8, 0.62, 0.8),
            parts(y(D, 0.5, 0.5, 0.1, 0.1, 0.0, 0.5), y(D, 0.5, 0.5, 0.3, 0.3, 0.5, 0.6), y(L, 0.5, 0.5, 0.25, 0.25, 0.56, 0.62)),
            alt = parts(y(D, 0.5, 0.5, 0.1, 0.1, 0.0, 0.5), y(D, 0.5, 0.5, 0.3, 0.3, 0.5, 0.6), y(K, 0.5, 0.5, 0.25, 0.25, 0.56, 0.58)),
            fem = true, ways = 1, kind = 2, lab = 5, lt = 1, mat = 1, color = 0xD8D8D8)

        // ===================== ETAPA 4: PORTAS, PORTÕES, JANELAS, RECLINÁVEL, DECORAÇÃO QUE GIRA =====================
        grp = "PORTAS E JANELAS"
        // portas/janelas: o painel fica fino em x (x local = largura do vão), porque o móvel gira de costas pra parede ao lado.
        add("Porta", "Porta de madeira com maçanetas. Toque pra abrir e fechar. Aberta, dá pra passar (empilhe duas pra uma porta alta).", a(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
            parts(b(W, 0.36, 0.0, 0.0, 0.64, 1.0, 0.07), b(W, 0.36, 0.0, 0.93, 0.64, 1.0, 1.0), b(W, 0.36, 0.94, 0.0, 0.64, 1.0, 1.0), b(C0, 0.0, 0.0, 0.0, 0.86, 0.94, 0.06), b(M, 0.7, 0.45, 0.06, 0.78, 0.5, 0.1)),
            alt = parts(b(W, 0.36, 0.0, 0.0, 0.64, 1.0, 0.07), b(W, 0.36, 0.0, 0.93, 0.64, 1.0, 1.0), b(W, 0.36, 0.94, 0.0, 0.64, 1.0, 1.0), b(C0, 0.42, 0.0, 0.07, 0.58, 0.94, 0.93), b(M, 0.3, 0.45, 0.8, 0.42, 0.5, 0.86), b(M, 0.58, 0.45, 0.8, 0.7, 0.5, 0.86)),
            fem = true, kind = 2, lab = 2, inv = true, boxOpen = a(0.0, 0.0, 0.0, 0.06, 1.0, 0.06), color = 0xB87D45, sy = 2.0)
        add("Porta de Correr", "Porta de correr moderna. Toque pra abrir e fechar. Aberta, dá pra passar.", a(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
            parts(b(M, 0.34, 0.96, 0.0, 0.66, 1.0, 1.0), b(M, 0.34, 0.0, 0.0, 0.66, 0.03, 1.0), b(C0, 0.44, 0.03, 0.0, 0.56, 0.96, 0.14)),
            alt = parts(b(M, 0.34, 0.96, 0.0, 0.66, 1.0, 1.0), b(M, 0.34, 0.0, 0.0, 0.66, 0.03, 1.0), b(C0, 0.44, 0.03, 0.04, 0.56, 0.96, 0.96), b(K, 0.4, 0.45, 0.8, 0.44, 0.55, 0.84)),
            fem = true, kind = 2, lab = 2, inv = true, boxOpen = a(0.0, 0.0, 0.0, 0.06, 1.0, 0.06), mat = 5, color = 0xF2F2EE, sy = 2.0)
        add("Portão de Jardim", "Portão baixo de jardim. Toque pra abrir e fechar. Aberto, dá pra passar.", a(0.0, 0.0, 0.0, 1.0, 0.75, 1.0),
            parts(b(W, 0.38, 0.0, 0.0, 0.62, 0.8, 0.1), b(W, 0.38, 0.0, 0.9, 0.62, 0.8, 1.0), b(C0, 0.0, 0.2, 0.0, 0.9, 0.3, 0.06), b(C0, 0.0, 0.45, 0.0, 0.9, 0.55, 0.06), b(C0, 0.0, 0.65, 0.0, 0.9, 0.72, 0.06)),
            alt = parts(b(W, 0.38, 0.0, 0.0, 0.62, 0.8, 0.1), b(W, 0.38, 0.0, 0.9, 0.62, 0.8, 1.0), b(C0, 0.44, 0.2, 0.1, 0.56, 0.3, 0.9), b(C0, 0.44, 0.45, 0.1, 0.56, 0.55, 0.9), b(C0, 0.44, 0.65, 0.1, 0.56, 0.72, 0.9)),
            kind = 2, lab = 2, inv = true, boxOpen = a(0.0, 0.0, 0.0, 0.06, 0.75, 0.06), color = 0xF2F2EE, sy = 2.5)
        add("Portão de Curral", "Portão de tábuas do curral. Toque pra abrir e fechar. Aberto, dá pra passar.", a(0.0, 0.0, 0.0, 1.0, 0.9, 1.0),
            parts(b(WD, 0.4, 0.0, 0.0, 0.6, 0.95, 0.1), b(WD, 0.4, 0.0, 0.9, 0.6, 0.95, 1.0), b(W, 0.0, 0.15, 0.0, 0.9, 0.3, 0.06), b(W, 0.0, 0.4, 0.0, 0.9, 0.55, 0.06), b(W, 0.0, 0.65, 0.0, 0.9, 0.8, 0.06)),
            alt = parts(b(WD, 0.4, 0.0, 0.0, 0.6, 0.95, 0.1), b(WD, 0.4, 0.0, 0.9, 0.6, 0.95, 1.0), b(W, 0.45, 0.15, 0.1, 0.55, 0.3, 0.9), b(W, 0.45, 0.4, 0.1, 0.55, 0.55, 0.9), b(W, 0.45, 0.65, 0.1, 0.55, 0.8, 0.9), b(W, 0.46, 0.15, 0.1, 0.54, 0.8, 0.2)),
            ways = 1, kind = 2, lab = 2, inv = true, boxOpen = a(0.0, 0.0, 0.0, 0.1, 0.95, 0.1), sy = 2.0)
        add("Janela de Abrir", "Janela com vidro e travessas. Toque pra abrir e fechar o vidro.", a(0.0, 0.0, 0.0, 1.0, 1.0, 1.0),
            parts(b(W, 0.4, 0.0, 0.0, 0.6, 0.1, 1.0), b(W, 0.4, 0.9, 0.0, 0.6, 1.0, 1.0), b(W, 0.4, 0.0, 0.0, 0.6, 1.0, 0.1), b(W, 0.4, 0.0, 0.9, 0.6, 1.0, 1.0), b(L, 0.47, 0.1, 0.1, 0.53, 0.9, 0.9), b(W, 0.45, 0.1, 0.46, 0.55, 0.9, 0.54), b(W, 0.45, 0.46, 0.1, 0.55, 0.54, 0.9)),
            alt = parts(b(W, 0.4, 0.0, 0.0, 0.6, 0.1, 1.0), b(W, 0.4, 0.9, 0.0, 0.6, 1.0, 1.0), b(W, 0.4, 0.0, 0.0, 0.6, 1.0, 0.1), b(W, 0.4, 0.0, 0.9, 0.6, 1.0, 1.0), b(L, 0.0, 0.1, 0.0, 0.86, 0.9, 0.05)),
            fem = true, kind = 2, lab = 2, inv = true, lt = 1, color = 0xF2F2EE)
        add("Persiana de Enrolar", "Persiana de lâminas. Toque pra subir e descer.", a(0.38, 0.0, 0.0, 0.62, 1.0, 1.0),
            parts(b(C0, 0.46, 0.05, 0.08, 0.54, 0.95, 0.92), b(C1, 0.44, 0.25, 0.08, 0.56, 0.28, 0.92), b(C1, 0.44, 0.5, 0.08, 0.56, 0.53, 0.92), b(C1, 0.44, 0.75, 0.08, 0.56, 0.78, 0.92), b(M, 0.42, 0.95, 0.06, 0.58, 1.0, 0.94)),
            alt = parts(b(C0, 0.44, 0.84, 0.08, 0.56, 0.95, 0.92), b(M, 0.42, 0.95, 0.06, 0.58, 1.0, 0.94)),
            fem = true, kind = 2, lab = 7, color = 0xF2F2EE)

        // ===================== sala e quarto =====================
        grp = "SALA E DECORAÇÃO"
        add("Poltrona Reclinável", "Poltrona com apoio de pés. Toque pra reclinar e endireitar. Dá pra sentar!", a(0.05, 0.0, 0.05, 0.95, 0.9, 0.95),
            parts(b(WD, 0.08, 0.0, 0.1, 0.92, 0.2, 0.9), b(C0, 0.1, 0.2, 0.2, 0.9, 0.42, 0.9), b(C0, 0.1, 0.42, 0.1, 0.9, 0.95, 0.3), b(C1, 0.05, 0.2, 0.2, 0.14, 0.6, 0.9), b(C1, 0.86, 0.2, 0.2, 0.95, 0.6, 0.9), b(C1, 0.12, 0.12, 0.9, 0.88, 0.3, 0.97)),
            alt = parts(b(WD, 0.08, 0.0, 0.1, 0.92, 0.2, 0.9), b(C0, 0.1, 0.2, 0.2, 0.9, 0.42, 0.9), b(C0, 0.1, 0.42, 0.1, 0.9, 0.62, 0.4), b(C0, 0.1, 0.52, 0.0, 0.9, 0.78, 0.2), b(C1, 0.05, 0.2, 0.2, 0.14, 0.6, 0.9), b(C1, 0.86, 0.2, 0.2, 0.95, 0.6, 0.9), b(C1, 0.12, 0.3, 0.9, 0.88, 0.4, 1.0)),
            fem = true, fabric = true, seat = 0.46, kind = 2, lab = 6, mat = 3, color = 0x8A5A3C)
        add("Rack de TV", "Rack baixo com duas gavetas, ótimo pra TV e videogame. Toque pra abrir.", a(0.0, 0.0, 0.1, 1.0, 0.5, 0.9),
            parts(b(C0, 0.0, 0.0, 0.1, 1.0, 0.5, 0.9), b(WD, 0.06, 0.08, 0.9, 0.48, 0.4, 0.915), b(WD, 0.52, 0.08, 0.9, 0.94, 0.4, 0.915), b(M, 0.22, 0.2, 0.915, 0.32, 0.26, 0.93), b(M, 0.68, 0.2, 0.915, 0.78, 0.26, 0.93)),
            open = a(0.915, 0.06, 0.08, 0.94, 0.4), top = 0.5, kind = 1, color = 0xF2F2EE)

        // ===================== decoração que gira (toque em GIRAR: 90° por vez) =====================
        add("Estátua", "Estátua de pedra com um braço levantado. Toque pra girar.", a(0.25, 0.0, 0.25, 0.75, 0.76, 0.75),
            parts(b(D, 0.25, 0.0, 0.25, 0.75, 0.2, 0.75), b(D, 0.4, 0.2, 0.4, 0.6, 0.6, 0.6), b(D, 0.42, 0.6, 0.42, 0.58, 0.76, 0.58), b(D, 0.6, 0.42, 0.45, 0.72, 0.5, 0.55)),
            fem = true, ways = 1, kind = 3, mat = 1, color = 0xD8D8D8)
        add("Escultura Moderna", "Escultura abstrata colorida. Toque pra girar.", a(0.25, 0.0, 0.25, 0.75, 0.85, 0.75),
            parts(b(K, 0.25, 0.0, 0.25, 0.75, 0.1, 0.75), b(C0, 0.35, 0.1, 0.35, 0.55, 0.4, 0.55), b(C0, 0.45, 0.4, 0.4, 0.75, 0.6, 0.6), b(C1, 0.4, 0.6, 0.35, 0.6, 0.85, 0.55)),
            fem = true, kind = 3, mat = 1, color = 0xF59B3D)
        add("Espantalho", "Espantalho de palha com chapéu. Toque pra girar.", a(0.3, 0.0, 0.3, 0.7, 1.0, 0.7),
            parts(b(WD, 0.46, 0.0, 0.46, 0.54, 0.95, 0.54), b(W, 0.1, 0.6, 0.46, 0.9, 0.66, 0.54), b(C0, 0.3, 0.4, 0.4, 0.7, 0.72, 0.6), b(W, 0.4, 0.75, 0.4, 0.6, 0.92, 0.6), b(C1, 0.3, 0.92, 0.3, 0.7, 0.98, 0.7), b(C1, 0.4, 0.98, 0.4, 0.6, 1.06, 0.6), b(W, 0.1, 0.5, 0.46, 0.2, 0.6, 0.54)),
            kind = 3, color = 0xC8444E, sy = 1.8)
        add("Prancha de Surfe", "Prancha de surfe em pé num suporte. Toque pra girar.", a(0.25, 0.0, 0.25, 0.75, 0.95, 0.75),
            parts(b(WD, 0.25, 0.0, 0.3, 0.75, 0.06, 0.7), b(C0, 0.4, 0.06, 0.46, 0.6, 0.95, 0.54), b(C1, 0.4, 0.4, 0.46, 0.6, 0.5, 0.54), b(C1, 0.44, 0.7, 0.46, 0.56, 0.78, 0.54)),
            fem = true, kind = 3, color = 0x6DB9EE)

        // ===================== ETAPA 5: EXPOSIÇÃO E ARMAZENAMENTO (kind 4) =====================
        grp = "EXPOSIÇÃO E ARMAZENAMENTO"
        // slots = lista [x, y, z, tamanho] por item guardado; tocar num slot vazio guarda o que você segura, tocar num cheio devolve pra mão.
        add("Prateleira de Parede", "Prateleira com 3 lugares. Toque num lugar vazio pra guardar o item da mão, ou num cheio pra pegar de volta.", a(0.0, 0.3, 0.0, 1.0, 0.72, 0.5),
            parts(b(WD, 0.0, 0.3, 0.0, 1.0, 0.7, 0.06), b(W, 0.0, 0.4, 0.0, 1.0, 0.46, 0.5), b(C0, 0.0, 0.34, 0.0, 1.0, 0.4, 0.5)),
            slots = a(0.06, 0.46, 0.14, 0.26, 0.37, 0.46, 0.14, 0.26, 0.68, 0.46, 0.14, 0.26), kind = 4, fem = true, color = 0xC8935A)
        add("Estante de Exposição", "Estante alta com 6 lugares pra expor itens. Toque num lugar pra guardar ou pegar.", a(0.0, 0.0, 0.0, 1.0, 1.0, 0.5),
            parts(b(WD, 0.04, 0.0, 0.0, 0.96, 1.0, 0.06), b(C0, 0.0, 0.0, 0.0, 0.06, 1.0, 0.5), b(C0, 0.94, 0.0, 0.0, 1.0, 1.0, 0.5), b(W, 0.06, 0.0, 0.0, 0.94, 0.05, 0.5),
                b(W, 0.06, 0.3, 0.0, 0.94, 0.35, 0.5), b(W, 0.06, 0.65, 0.0, 0.94, 0.7, 0.5), b(C0, 0.0, 0.95, 0.0, 1.0, 1.0, 0.5)),
            slots = a(0.12, 0.35, 0.14, 0.22, 0.39, 0.35, 0.14, 0.22, 0.66, 0.35, 0.14, 0.22, 0.12, 0.7, 0.14, 0.22, 0.39, 0.7, 0.14, 0.22, 0.66, 0.7, 0.14, 0.22),
            kind = 4, fem = true, color = 0xC8935A, sy = 1.9)
        add("Vitrine de Vidro", "Vitrine de balcão com 4 lugares pra coleções. Toque num lugar pra guardar ou pegar.", a(0.05, 0.0, 0.05, 0.95, 0.95, 0.95),
            parts(b(W, 0.05, 0.0, 0.05, 0.95, 0.3, 0.95), b(K, 0.05, 0.3, 0.05, 0.1, 0.95, 0.1), b(K, 0.9, 0.3, 0.05, 0.95, 0.95, 0.1), b(K, 0.05, 0.3, 0.9, 0.1, 0.95, 0.95), b(K, 0.9, 0.3, 0.9, 0.95, 0.95, 0.95),
                b(C0, 0.05, 0.9, 0.05, 0.95, 0.95, 0.95), b(L, 0.1, 0.3, 0.1, 0.9, 0.9, 0.12)),
            slots = a(0.15, 0.3, 0.2, 0.3, 0.55, 0.3, 0.2, 0.3, 0.15, 0.3, 0.55, 0.3, 0.55, 0.3, 0.55, 0.3), kind = 4, fem = true, lt = 1, color = 0x6DB9EE, sy = 1.9)
        add("Pedestal de Exposição", "Pedestal de pedra com 1 lugar de destaque. Toque pra expor ou pegar o item.", a(0.15, 0.0, 0.15, 0.85, 0.65, 0.85),
            parts(b(D, 0.2, 0.0, 0.2, 0.8, 0.6, 0.8), b(D, 0.15, 0.6, 0.15, 0.85, 0.65, 0.85)),
            slots = a(0.3, 0.65, 0.3, 0.4), kind = 4, ways = 1, mat = 1, color = 0xD8D8D8)
        add("Armário de Troféus", "Armário com vitrine e 6 lugares pra troféus e lembranças. Toque pra guardar ou pegar.", a(0.0, 0.0, 0.0, 1.0, 1.0, 0.5),
            parts(b(C0, 0.0, 0.0, 0.0, 1.0, 0.2, 0.5), b(C0, 0.0, 0.95, 0.0, 1.0, 1.0, 0.5), b(C0, 0.0, 0.2, 0.0, 0.05, 0.95, 0.5), b(C0, 0.95, 0.2, 0.0, 1.0, 0.95, 0.5),
                b(L, 0.05, 0.2, 0.0, 0.95, 0.95, 0.04), b(W, 0.05, 0.55, 0.0, 0.95, 0.6, 0.5), b(M, 0.46, 0.0, 0.5, 0.54, 0.12, 0.54)),
            slots = a(0.1, 0.2, 0.12, 0.24, 0.38, 0.2, 0.12, 0.24, 0.66, 0.2, 0.12, 0.24, 0.1, 0.6, 0.12, 0.24, 0.38, 0.6, 0.12, 0.24, 0.66, 0.6, 0.12, 0.24),
            kind = 4, lt = 1, color = 0x8A5A3C, sy = 1.9)
        add("Caixa Organizadora", "Caixa aberta com 4 divisões pra equipamentos. Toque pra guardar ou pegar.", a(0.1, 0.0, 0.1, 0.9, 0.5, 0.9),
            parts(b(C0, 0.1, 0.0, 0.1, 0.9, 0.08, 0.9), b(C0, 0.1, 0.08, 0.1, 0.9, 0.4, 0.16), b(C0, 0.1, 0.08, 0.84, 0.9, 0.4, 0.9), b(C0, 0.1, 0.08, 0.1, 0.16, 0.4, 0.9), b(C0, 0.84, 0.08, 0.1, 0.9, 0.4, 0.9), b(C1, 0.47, 0.08, 0.16, 0.53, 0.4, 0.84), b(C1, 0.16, 0.08, 0.47, 0.84, 0.4, 0.53)),
            slots = a(0.2, 0.08, 0.2, 0.22, 0.56, 0.08, 0.2, 0.22, 0.2, 0.08, 0.56, 0.22, 0.56, 0.08, 0.56, 0.22), kind = 4, fem = true, small = true, mat = 3, color = 0x4C74C4)
        add("Prateleira de Toalhas", "Prateleira de dois andares com 4 lugares pra toalhas e acessórios. Toque pra guardar ou pegar.", a(0.04, 0.0, 0.2, 0.96, 1.0, 0.8),
            parts(b(WD, 0.04, 0.0, 0.2, 0.1, 1.0, 0.8), b(WD, 0.9, 0.0, 0.2, 0.96, 1.0, 0.8), b(W, 0.1, 0.0, 0.2, 0.9, 0.05, 0.8), b(W, 0.1, 0.4, 0.2, 0.9, 0.45, 0.8), b(W, 0.1, 0.75, 0.2, 0.9, 0.8, 0.8), b(C0, 0.04, 0.95, 0.2, 0.96, 1.0, 0.8)),
            slots = a(0.15, 0.45, 0.35, 0.3, 0.55, 0.45, 0.35, 0.3, 0.15, 0.8, 0.35, 0.3, 0.55, 0.8, 0.35, 0.3), kind = 4, fem = true, color = 0xF2F2EE, sy = 1.8)
        add("Prateleira de Sementes", "Prateleira baixa de sementes com 4 lugares. Toque pra guardar ou pegar.", a(0.04, 0.0, 0.2, 0.96, 0.7, 0.8),
            parts(b(WD, 0.04, 0.0, 0.2, 0.1, 0.7, 0.8), b(WD, 0.9, 0.0, 0.2, 0.96, 0.7, 0.8), b(W, 0.1, 0.0, 0.2, 0.9, 0.05, 0.8), b(W, 0.1, 0.35, 0.2, 0.9, 0.4, 0.8), b(C0, 0.04, 0.65, 0.2, 0.96, 0.7, 0.8), b(C1, 0.1, 0.4, 0.2, 0.9, 0.44, 0.24)),
            slots = a(0.14, 0.05, 0.35, 0.28, 0.58, 0.05, 0.35, 0.28, 0.14, 0.4, 0.35, 0.28, 0.58, 0.4, 0.35, 0.28), kind = 4, fem = true, color = 0x9AD93E)
    }

    // ---- consulta ----
    fun sittable(s: Int) = (def(s)?.seat ?: 0f) > 0f
    fun isTable(s: Int) = (def(s)?.top ?: 0f) > 0f
    fun seatH(s: Int) = def(s)?.seat ?: 0.5f
    fun topH(s: Int) = def(s)?.top ?: 0f
    fun small(s: Int) = def(s)?.small ?: false
    fun fKind(s: Int) = def(s)?.kind ?: 0
    /** lugares de guardar: os definidos no móvel; móveis que abrem sem lista própria ganham 6 (porta), 4 (tampa) ou 2 (abertura pequena) */
    fun slots(s: Int): Int {
        val d = def(s) ?: return 0
        val sl = d.slots
        if (sl != null) return sl.size / 4
        if (d.kind != 1) return 0
        if (d.lid != null) return 4
        val o = d.open
        return if (o != null && o[3] - o[1] < 0.6f) 2 else 6
    }
    /** colisão no estado atual e girada junto com o desenho (rot 0..3, mesma regra do Furniture.C) */
    fun box(s: Int, st: Int, rot: Int): FloatArray? {
        val d = def(s) ?: return null
        val b = (if (d.boxOpen != null && d.kind == 2 && !isOff(s, st)) d.boxOpen else d.box).copyOf()
        if (rot == 0) return b
        fun mx(x: Float, z: Float) = when (rot) { 1 -> 1f - x; 2 -> z; else -> 1f - z }
        fun mz(x: Float, z: Float) = when (rot) { 1 -> 1f - z; 2 -> 1f - x; else -> x }
        val ax = mx(b[0], b[2]); val bx = mx(b[3], b[5]); val az = mz(b[0], b[2]); val bz = mz(b[3], b[5])
        b[0] = min(ax, bx); b[3] = max(ax, bx); b[2] = min(az, bz); b[5] = max(az, bz)
        return b
    }
    fun openSpec(s: Int): FloatArray? = def(s)?.open
    fun lid(s: Int): FloatArray? = def(s)?.lid
    /** esticada na vertical (portas, armários altos...): 1 = um bloco, 2 = dois blocos de altura */
    fun tallOf(s: Int): Float = def(s)?.sy ?: 1f
    /** luz apagada / desligado / dobrado? (alguns móveis nascem desligados) */
    fun isOff(s: Int, st: Int): Boolean = ((st and Furniture.ST_OFF) != 0) != (def(s)?.inv ?: false)
    fun fLabel(s: Int, st: Int): String {
        val d = def(s) ?: return ""
        return when (d.kind) {
            1 -> if ((st and Furniture.ST_OPEN) != 0) "FECHAR" else "ABRIR"
            2 -> LABS[d.lab][if (isOff(s, st)) 1 else 0]
            3 -> "GIRAR"
            else -> ""
        }
    }

    internal fun emit(c: Furniture.C, id: Int, s: Int) {
        val d = def(s) ?: return
        val p = Blk.fp(id)
        c.lit = false
        if (d.kind == 3) c.rot = (c.rot + c.spin) and 3
        val src = if (d.alt != null && ((d.kind == 2 && c.off) || (d.kind == 1 && c.opened))) d.alt else d.parts
        for (q in src) {
            val t = q[0].toInt(); val tx = p[q[1].toInt()]
            if (t == 0 || t == 1) c.box(tx, tx, tx, q[2], q[3], q[4], q[5], q[6], q[7], t == 1)
            else c.cyl(tx, tx, tx, q[2], q[3], q[4], q[5], q[6], q[7], 12, false)
        }
        val st = c.store; val sl = d.slots
        if (st != null && sl != null) {   // itens guardados: um cubinho com a textura do bloco (ferramentas viram um cubinho de madeira)
            for (i in 0 until min(st.size, sl.size / 4)) {
                val it = st[i]; if (it == 0) continue
                Furniture.miniItem(c, it, sl[i * 4], sl[i * 4 + 1], sl[i * 4 + 2], sl[i * 4 + 3])
            }
        }
    }
}
