package com.cuboland.app

import android.opengl.GLES20 as G
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/** Geometria comum das caixas texturizadas (usada pelo OpenGL do jogo e pelo preview da tela de personagem). */
object BoxFaces {
    // faces na ordem de SkinArt: TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK; cada uma com 4 cantos (TL, TR, BR, BL) x (x,y,z) em -1..1
    val CORN: Array<IntArray> = arrayOf(
        intArrayOf(-1, 1, -1, 1, 1, -1, 1, 1, 1, -1, 1, 1),
        intArrayOf(-1, -1, 1, 1, -1, 1, 1, -1, -1, -1, -1, -1),
        intArrayOf(-1, 1, -1, -1, 1, 1, -1, -1, 1, -1, -1, -1),
        intArrayOf(-1, 1, 1, 1, 1, 1, 1, -1, 1, -1, -1, 1),
        intArrayOf(1, 1, 1, 1, 1, -1, 1, -1, -1, 1, -1, 1),
        intArrayOf(1, 1, -1, -1, 1, -1, -1, -1, -1, 1, -1, -1)
    )
    val SHADE = floatArrayOf(1.0f, 0.6f, 0.8f, 0.86f, 0.8f, 0.74f)
    const val INSET = 0.02f

    /** coordenadas (em pixels da folha) do canto k (0 TL, 1 TR, 2 BR, 3 BL) da face f da caixa id; out[0]=u, out[1]=v */
    fun uv(sd: SkinData, id: Int, f: Int, k: Int, r0: Float, r1: Float, out: FloatArray) {
        val u0 = sd.bu[id]; val v0 = sd.bv[id]; val w = sd.bw[id]; val h = sd.bh[id]; val d = sd.bd[id]
        val fx: Int; val fy: Int; val fw: Int; val fh: Int
        when (f) {
            0 -> { fx = u0 + d; fy = v0; fw = w; fh = d }
            1 -> { fx = u0 + d + w; fy = v0; fw = w; fh = d }
            2 -> { fx = u0; fy = v0 + d; fw = d; fh = h }
            3 -> { fx = u0 + d; fy = v0 + d; fw = w; fh = h }
            4 -> { fx = u0 + d + w; fy = v0 + d; fw = d; fh = h }
            else -> { fx = u0 + 2 * d + w; fy = v0 + d; fw = w; fh = h }
        }
        val ra = if (f <= 1) 0f else r0
        val rb = if (f <= 1) 1f else r1
        val cu = if (k == 1 || k == 2) 1f else 0f
        val cv = if (k == 2 || k == 3) 1f else 0f
        val ua = fx + INSET; val ub = fx + fw - INSET
        val va = fy + ra * fh + INSET; val vb = fy + rb * fh - INSET
        out[0] = ua + (ub - ua) * cu
        out[1] = va + (vb - va) * cv
    }
}

/** Desenha o personagem texturizado no jogo: programa proprio, vertices na CPU, UM glDrawArrays por quadro. */
class SkinGL : TexSink {
    private var prog = 0
    private var aPos = 0; private var aUV = 0; private var aSh = 0
    private var uVP = 0; private var uCam = 0; private var uFog = 0; private var uUnder = 0; private var uTex = 0
    private var tex = 0
    private var stampSent = -1
    private val arr = FloatArray(6 * 6 * 6 * 220)
    private var n = 0
    private val buf: FloatBuffer = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val tmp = FloatArray(16)
    private val wx = FloatArray(8); private val wy = FloatArray(8); private val wz = FloatArray(8)
    private val uvo = FloatArray(2)
    private val order = intArrayOf(0, 3, 2, 0, 2, 1)
    private var sd: SkinData? = null
    private val vpm = FloatArray(16)
    private var cx = 0f; private var cy = 0f; private var cz = 0f
    private var fr = 1f; private var fg = 1f; private var fb = 1f
    private var under = false

    private val vs = """
        uniform mat4 uVP; uniform vec3 uCam; uniform float uUnder;
        attribute vec3 aPos; attribute vec2 aUV; attribute float aSh;
        varying vec2 vUV; varying float vSh; varying float vFog;
        void main() {
            gl_Position = uVP * vec4(aPos, 1.0);
            vUV = aUV; vSh = aSh;
            vFog = clamp((length(aPos - uCam) - mix(38.0, 6.0, uUnder)) / mix(52.0, 62.0, uUnder), 0.0, 1.0);
        }
    """.trimIndent()
    private val fs = """
        precision mediump float;
        uniform sampler2D uTex; uniform vec3 uFog; uniform float uUnder;
        varying vec2 vUV; varying float vSh; varying float vFog;
        void main() {
            vec4 t = texture2D(uTex, vUV);
            if (t.a < 0.1) discard;
            vec3 c = t.rgb * vSh;
            if (uUnder > 0.5) c = c * vec3(0.88, 1.02, 1.10);
            c = mix(c, uFog, vFog);
            gl_FragColor = vec4(c, t.a);
        }
    """.trimIndent()

    private fun shader(type: Int, src: String): Int {
        val s = G.glCreateShader(type)
        G.glShaderSource(s, src); G.glCompileShader(s)
        return s
    }

    /** chamar em onSurfaceCreated (o contexto GL novo perdeu tudo) */
    fun init() {
        prog = G.glCreateProgram()
        G.glAttachShader(prog, shader(G.GL_VERTEX_SHADER, vs)); G.glAttachShader(prog, shader(G.GL_FRAGMENT_SHADER, fs))
        G.glLinkProgram(prog)
        aPos = G.glGetAttribLocation(prog, "aPos"); aUV = G.glGetAttribLocation(prog, "aUV"); aSh = G.glGetAttribLocation(prog, "aSh")
        uVP = G.glGetUniformLocation(prog, "uVP"); uCam = G.glGetUniformLocation(prog, "uCam")
        uFog = G.glGetUniformLocation(prog, "uFog"); uUnder = G.glGetUniformLocation(prog, "uUnder"); uTex = G.glGetUniformLocation(prog, "uTex")
        val ids = IntArray(1); G.glGenTextures(1, ids, 0); tex = ids[0]
        stampSent = -1
    }

    fun begin(vp: FloatArray, ex: Float, ey: Float, ez: Float, r: Float, g: Float, b: Float, isUnder: Boolean) {
        System.arraycopy(vp, 0, vpm, 0, 16)
        cx = ex; cy = ey; cz = ez; fr = r; fg = g; fb = b; under = isUnder
        sd = Skin.get()
        n = 0
    }

    private fun put(x: Float, y: Float, z: Float, u: Float, v: Float, sh: Float) {
        if (n + 6 > arr.size) return
        arr[n] = x; arr[n + 1] = y; arr[n + 2] = z; arr[n + 3] = u / 128f; arr[n + 4] = v / 128f; arr[n + 5] = sh
        n += 6
    }

    override fun box(m: FloatArray, px: Float, py: Float, pz: Float, rx: Float, ry: Float, rz: Float, sx: Float, sy: Float, sz: Float, oy: Float, id: Int, r0: Float, r1: Float) {
        val d = sd ?: return
        if (id < 0 || id >= d.nb) return
        System.arraycopy(m, 0, tmp, 0, 16)
        Matrix.translateM(tmp, 0, px, py, pz)
        if (rz != 0f) Matrix.rotateM(tmp, 0, rz, 0f, 0f, 1f)
        if (ry != 0f) Matrix.rotateM(tmp, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(tmp, 0, rx, 1f, 0f, 0f)
        if (oy != 0f) Matrix.translateM(tmp, 0, 0f, oy, 0f)
        Matrix.scaleM(tmp, 0, sx, sy, sz)
        for (f in 0 until 6) {
            val cn = BoxFaces.CORN[f]
            val sh = BoxFaces.SHADE[f]
            for (k in 0 until 4) {
                val x = cn[k * 3] * 0.5f; val y = cn[k * 3 + 1] * 0.5f; val z = cn[k * 3 + 2] * 0.5f
                wx[k] = tmp[0] * x + tmp[4] * y + tmp[8] * z + tmp[12]
                wy[k] = tmp[1] * x + tmp[5] * y + tmp[9] * z + tmp[13]
                wz[k] = tmp[2] * x + tmp[6] * y + tmp[10] * z + tmp[14]
            }
            // triangulos (TL, BL, BR) e (TL, BR, TR)
            for (q in 0 until 6) {
                val k = order[q]
                BoxFaces.uv(d, id, f, k, r0, r1, uvo)
                put(wx[k], wy[k], wz[k], uvo[0], uvo[1], sh)
            }
        }
    }

    fun end() {
        val d = sd
        if (d == null || n == 0) { sd = null; return }
        G.glUseProgram(prog)
        G.glActiveTexture(G.GL_TEXTURE0)
        G.glBindTexture(G.GL_TEXTURE_2D, tex)
        if (stampSent != d.stamp) {
            val bb = ByteBuffer.allocateDirect(128 * 128 * 4).order(ByteOrder.nativeOrder())
            for (i in 0 until 128 * 128) {
                val c = d.pix[i]
                bb.put(((c shr 16) and 255).toByte()); bb.put(((c shr 8) and 255).toByte()); bb.put((c and 255).toByte()); bb.put(((c ushr 24) and 255).toByte())
            }
            bb.position(0)
            G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
            G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_NEAREST)
            G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_NEAREST)
            G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE)
            G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
            G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA, 128, 128, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, bb)
            stampSent = d.stamp
        }
        G.glUniformMatrix4fv(uVP, 1, false, vpm, 0)
        G.glUniform3f(uCam, cx, cy, cz)
        G.glUniform3f(uFog, fr, fg, fb)
        G.glUniform1f(uUnder, if (under) 1f else 0f)
        G.glUniform1i(uTex, 0)
        buf.clear(); buf.put(arr, 0, n); buf.position(0)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, 0)
        G.glBindBuffer(G.GL_ELEMENT_ARRAY_BUFFER, 0)
        G.glEnableVertexAttribArray(aPos); G.glEnableVertexAttribArray(aUV); G.glEnableVertexAttribArray(aSh)
        buf.position(0); G.glVertexAttribPointer(aPos, 3, G.GL_FLOAT, false, 24, buf)
        buf.position(3); G.glVertexAttribPointer(aUV, 2, G.GL_FLOAT, false, 24, buf)
        buf.position(5); G.glVertexAttribPointer(aSh, 1, G.GL_FLOAT, false, 24, buf)
        G.glDisable(G.GL_CULL_FACE)
        G.glEnable(G.GL_BLEND)
        G.glBlendFunc(G.GL_SRC_ALPHA, G.GL_ONE_MINUS_SRC_ALPHA)
        G.glDrawArrays(G.GL_TRIANGLES, 0, n / 6)
        G.glDisable(G.GL_BLEND)
        G.glEnable(G.GL_CULL_FACE)
        G.glDisableVertexAttribArray(aPos); G.glDisableVertexAttribArray(aUV); G.glDisableVertexAttribArray(aSh)
        buf.position(0)
        sd = null
    }
}
