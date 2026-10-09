package com.cuboland.app

import android.opengl.GLES20 as G
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class GameRenderer(val game: Game) : GLSurfaceView.Renderer {
    private val world = game.world
    private var prog = 0
    private var aPos = 0; private var aCol = 0; private var aUV = 0
    private var uVP = 0; private var uModel = 0; private var uCam = 0; private var uTint = 0
    private var uTime = 0; private var uWind = 0; private var uUnder = 0; private var uAlpha = 0; private var uFog = 0; private var uTex = 0
    private val proj = FloatArray(16); private val view = FloatArray(16); private val vp = FloatArray(16)
    private val ident = FloatArray(16)
    private val tmp = FloatArray(16); private val base = FloatArray(16); private val arm = FloatArray(16)
    private val base2 = FloatArray(16); private val fp = FloatArray(16); private val itemM = FloatArray(16)
    private var cubeVb = 0; private var cubeIb = 0
    private val vbo = IntArray(World.CX * World.CZ * 2); private val ibo = IntArray(World.CX * World.CZ * 2)
    private val cnt = IntArray(World.CX * World.CZ * 2)
    private val ob = MeshBuf(); private val wb = MeshBuf()
    private var last = 0L
    private val rnd = java.util.Random()
    private val fogR = 0.82f; private val fogG = 0.92f; private val fogB = 1.0f
    private var skyProg = 0; private var skyP = 0; private var skyInv = 0; private var skyCam = 0; private var skyHor = 0; private var skyTime = 0
    private val inv = FloatArray(16)
    private val quad = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
    private var lastId = -1; private var equip = 1f
    private var prevYaw = 0f; private var prevPitch = 0f; private var swayX = 0f; private var swayY = 0f

    private val VS = """
        uniform mat4 uVP; uniform mat4 uModel; uniform vec3 uCam; uniform float uTime; uniform float uWind; uniform float uUnder;
        attribute vec3 aPos; attribute vec3 aCol; attribute vec2 aUV;
        varying vec3 vCol; varying float vFog; varying vec2 vUV; varying vec3 vWP; varying float vTile;
        void main() {
            vec4 wp = uModel * vec4(aPos, 1.0);
            float tile = floor(aUV.x * 32.0);
            if (uWind > 0.5) {
                float gust = 0.6 + 0.4 * sin(wp.x * 0.12 + wp.z * 0.08 + uTime * 0.7);
                if (tile >= 16.0 && tile < 20.0) {
                    float w = clamp((0.99 - aUV.y) / 0.98, 0.0, 1.0); w = w * w;
                    wp.x += (sin(uTime * 2.3 + wp.x * 0.9 + wp.z * 0.6) * 0.07 + 0.04) * w * gust * 1.4;
                    wp.z += cos(uTime * 1.8 + wp.x * 0.5 + wp.z * 0.9) * 0.05 * w * gust * 1.4;
                } else if (tile == 8.0) {
                    wp.x += sin(uTime * 1.7 + wp.y * 1.3 + wp.z * 0.8) * 0.04 * gust;
                    wp.z += cos(uTime * 1.4 + wp.y * 1.1 + wp.x * 0.8) * 0.04 * gust;
                    wp.y += sin(uTime * 2.1 + wp.x * 1.2 + wp.z) * 0.018;
                } else if (tile == 10.0) {
                    wp.y += sin(uTime * 1.8 + wp.x * 1.3 + wp.z * 0.7) * 0.05 + cos(uTime * 1.4 + wp.z * 1.5 - wp.x * 0.6) * 0.04;
                }
            }
            gl_Position = uVP * wp;
            vCol = aCol; vUV = aUV; vWP = wp.xyz; vTile = tile;
            vFog = clamp((length(wp.xyz - uCam) - mix(38.0, 1.0, uUnder)) / mix(52.0, 22.0, uUnder), 0.0, 1.0);
            vFog = vFog * vFog * (3.0 - 2.0 * vFog);
        }"""
    private val FS = """
        precision mediump float;
        uniform vec3 uTint; uniform vec3 uFog; uniform float uAlpha; uniform sampler2D uTex; uniform float uTime; uniform float uUnder;
        varying vec3 vCol; varying float vFog; varying vec2 vUV; varying vec3 vWP; varying float vTile;
        void main() {
            vec2 uv = vUV;
            if (vTile > 9.5 && vTile < 10.5) {
                uv += vec2(sin(vWP.z * 2.0 + uTime * 1.2), cos(vWP.x * 2.0 + uTime)) * 0.006;
                uv = vec2(clamp(uv.x, 10.012 / 32.0, 10.988 / 32.0), clamp(uv.y, 0.012, 0.988));
            }
            vec4 t = texture2D(uTex, uv);
            if (t.a < 0.4) discard;
            float l = clamp((vCol.r - 0.3) / 0.7, 0.0, 1.0);
            vec3 light = mix(vec3(0.80, 0.84, 1.0), vec3(1.05, 1.02, 0.95), l);
            vec3 c = t.rgb * vCol * uTint * light * 1.06;
            if (vTile > 7.5 && vTile < 8.5) c *= 1.1 + 0.1 * sin(vWP.x * 0.7 + vWP.z * 0.5 + uTime * 0.8);
            if (vTile > 9.5 && vTile < 10.5) {
                float a = sin(vWP.x * 4.0 + uTime * 1.6 + sin(vWP.z * 1.7 + uTime * 0.8) * 1.5);
                float b = sin(vWP.z * 3.7 - uTime * 1.3 + sin(vWP.x * 1.9 + uTime * 0.7) * 1.5);
                float gl = smoothstep(0.62, 0.9, a * b);
                float sw = 0.5 + 0.5 * sin(vWP.x * 0.9 + vWP.z * 0.7 + uTime * 0.9);
                c = mix(c, vec3(0.62, 0.9, 1.0), 0.16 + 0.12 * sw) + vec3(1.0) * gl * 0.3;
            }
            if (vTile > 14.5 && vTile < 15.5) c = t.rgb * 1.25;
            if (uUnder > 0.5) {
                c *= vec3(0.78, 0.95, 1.05);
                float k1 = sin(vWP.x * 3.0 + uTime * 1.5) + sin(vWP.z * 3.3 - uTime * 1.2) + sin((vWP.x + vWP.z) * 2.0 + uTime);
                c += vec3(0.6, 0.9, 1.0) * pow(max(0.0, k1 * 0.33), 3.0) * 0.5;
            }
            float g = dot(c, vec3(0.299, 0.587, 0.114));
            c = mix(vec3(g), c, 1.04); c = mix(c, vec3(1.0, 0.98, 0.97), 0.05);
            c = mix(c, uFog, vFog);
            gl_FragColor = vec4(c, uAlpha * t.a);
        }"""
    private val SVS = """
        attribute vec2 aP; uniform mat4 uInv; varying vec4 vF;
        void main() { vF = uInv * vec4(aP, 1.0, 1.0); gl_Position = vec4(aP, 0.999, 1.0); }"""
    private val SFS = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform vec3 uCam; uniform vec3 uHor; uniform float uTime; varying vec4 vF;
        float hs(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
        float ns(vec2 p) { vec2 i = floor(p); vec2 f = fract(p); f = f * f * (3.0 - 2.0 * f);
            return mix(mix(hs(i), hs(i + vec2(1.0, 0.0)), f.x), mix(hs(i + vec2(0.0, 1.0)), hs(i + vec2(1.0, 1.0)), f.x), f.y); }
        void main() {
            vec3 d = normalize(vF.xyz / vF.w - uCam);
            float h = clamp(d.y, 0.0, 1.0);
            vec3 c = mix(uHor, vec3(0.50, 0.72, 1.0), pow(h, 0.55));
            vec3 sd = normalize(vec3(0.55, 0.5, 0.65));
            float s = max(dot(d, sd), 0.0);
            c += vec3(1.0, 0.85, 0.55) * pow(s, 6.0) * 0.25 + vec3(1.0, 0.95, 0.8) * pow(s, 300.0) * 2.0;
            if (d.y > 0.02) {
                vec2 q = d.xz / (d.y + 0.25) * 1.4 + vec2(uTime * 0.012, 0.0);
                float n = ns(q * 1.5) * 0.55 + ns(q * 3.1) * 0.3 + ns(q * 6.5) * 0.15;
                float cl = smoothstep(0.52, 0.78, n) * smoothstep(0.02, 0.25, d.y);
                c = mix(c, vec3(1.0, 0.99, 0.97) * (0.86 + 0.14 * ns(q * 2.0)), cl * 0.85);
            }
            gl_FragColor = vec4(c, 1.0);
        }"""

    private fun shader(type: Int, src: String): Int {
        val s = G.glCreateShader(type); G.glShaderSource(s, src); G.glCompileShader(s); return s
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        G.glClearColor(fogR, fogG, fogB, 1f)
        prog = G.glCreateProgram()
        G.glAttachShader(prog, shader(G.GL_VERTEX_SHADER, VS)); G.glAttachShader(prog, shader(G.GL_FRAGMENT_SHADER, FS))
        G.glLinkProgram(prog)
        aPos = G.glGetAttribLocation(prog, "aPos"); aCol = G.glGetAttribLocation(prog, "aCol"); aUV = G.glGetAttribLocation(prog, "aUV")
        uVP = G.glGetUniformLocation(prog, "uVP"); uModel = G.glGetUniformLocation(prog, "uModel")
        uCam = G.glGetUniformLocation(prog, "uCam"); uTint = G.glGetUniformLocation(prog, "uTint")
        uAlpha = G.glGetUniformLocation(prog, "uAlpha"); uFog = G.glGetUniformLocation(prog, "uFog")
        uTex = G.glGetUniformLocation(prog, "uTex"); uTime = G.glGetUniformLocation(prog, "uTime"); uWind = G.glGetUniformLocation(prog, "uWind"); uUnder = G.glGetUniformLocation(prog, "uUnder")
        skyProg = G.glCreateProgram()
        G.glAttachShader(skyProg, shader(G.GL_VERTEX_SHADER, SVS)); G.glAttachShader(skyProg, shader(G.GL_FRAGMENT_SHADER, SFS))
        G.glLinkProgram(skyProg)
        skyP = G.glGetAttribLocation(skyProg, "aP"); skyInv = G.glGetUniformLocation(skyProg, "uInv")
        skyCam = G.glGetUniformLocation(skyProg, "uCam"); skyHor = G.glGetUniformLocation(skyProg, "uHor")
        skyTime = G.glGetUniformLocation(skyProg, "uTime")
        Matrix.setIdentityM(ident, 0)
        G.glEnable(G.GL_DEPTH_TEST); G.glEnable(G.GL_CULL_FACE); G.glCullFace(G.GL_BACK); G.glFrontFace(G.GL_CCW)
        G.glBlendFunc(G.GL_SRC_ALPHA, G.GL_ONE_MINUS_SRC_ALPHA)
        // textura (atlas)
        val tex = IntArray(1); G.glGenTextures(1, tex, 0)
        G.glActiveTexture(G.GL_TEXTURE0); G.glBindTexture(G.GL_TEXTURE_2D, tex[0])
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR_MIPMAP_NEAREST)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(G.GL_TEXTURE_2D, 0, Atlas.bmp, 0); G.glGenerateMipmap(G.GL_TEXTURE_2D)
        // buffers de chunk
        val ids = IntArray(vbo.size * 2); G.glGenBuffers(ids.size, ids, 0)
        for (i in vbo.indices) { vbo[i] = ids[i * 2]; ibo[i] = ids[i * 2 + 1]; cnt[i] = 0 }
        java.util.Arrays.fill(world.dirty, true)
        // cubos unitários: 0 = branco (entidades), 1..13 = blocos com textura
        val cb = MeshBuf(); val p = FloatArray(3)
        for (id in 0..13) for (face in 0 until 6) {
            val a = face shr 1; val s = if ((face and 1) == 0) 1 else -1
            val u = (a + 1) % 3; val v = (a + 2) % 3
            val sh = when { a == 1 -> if (s > 0) 1f else 0.55f; a == 0 -> 0.82f; else -> 0.7f }
            val tile = if (id == 0) 0 else if (a == 1) (if (s > 0) B.tTop[id] else B.tBot[id]) else B.tSide[id]
            for (q in 0 until 4) {
                val cu = World.QU[q]; val cv = World.QV[q]
                p[a] = 0.5f * s; p[u] = cu - 0.5f; p[v] = cv - 0.5f
                var tu = 0.5f; var tv = 0.5f
                if (id != 0) {
                    if (a == 0) { tu = cv.toFloat(); tv = 1f - cu } else if (a == 1) { tu = cu.toFloat(); tv = cv.toFloat() } else { tu = cu.toFloat(); tv = 1f - cv }
                }
                cb.vert(p[0], p[1], p[2], sh, sh, sh, (tile + 0.01f + tu * 0.98f) / Atlas.NT.toFloat(), 0.01f + tv * 0.98f)
            }
            if (s > 0) { cb.tri(0, 1, 2); cb.tri(0, 2, 3) } else { cb.tri(0, 2, 1); cb.tri(0, 3, 2) }
            cb.vc += 4
        }
        val ids2 = IntArray(2); G.glGenBuffers(2, ids2, 0); cubeVb = ids2[0]; cubeIb = ids2[1]
        upload(cb, cubeVb, cubeIb)
        last = System.nanoTime()
    }

    private fun upload(b: MeshBuf, vb: Int, ib: Int): Int {
        val fb = ByteBuffer.allocateDirect(max(4, b.vn * 4)).order(ByteOrder.nativeOrder()).asFloatBuffer()
        fb.put(b.v, 0, b.vn); fb.position(0)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, vb); G.glBufferData(G.GL_ARRAY_BUFFER, b.vn * 4, fb, G.GL_STATIC_DRAW)
        val sb = ByteBuffer.allocateDirect(max(2, b.inn * 2)).order(ByteOrder.nativeOrder()).asShortBuffer()
        sb.put(b.ix, 0, b.inn); sb.position(0)
        G.glBindBuffer(G.GL_ELEMENT_ARRAY_BUFFER, ib); G.glBufferData(G.GL_ELEMENT_ARRAY_BUFFER, b.inn * 2, sb, G.GL_STATIC_DRAW)
        return b.inn
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        G.glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 70f, w.toFloat() / h, 0.05f, 200f)
    }

    private fun bindMesh(vb: Int, ib: Int) {
        G.glBindBuffer(G.GL_ARRAY_BUFFER, vb); G.glBindBuffer(G.GL_ELEMENT_ARRAY_BUFFER, ib)
        G.glEnableVertexAttribArray(aPos); G.glEnableVertexAttribArray(aCol); G.glEnableVertexAttribArray(aUV)
        G.glVertexAttribPointer(aPos, 3, G.GL_FLOAT, false, 32, 0)
        G.glVertexAttribPointer(aCol, 3, G.GL_FLOAT, false, 32, 12)
        G.glVertexAttribPointer(aUV, 2, G.GL_FLOAT, false, 32, 24)
    }

    private fun tint(c: Int, a: Float, mul: Float = 1f) {
        G.glUniform3f(uTint, ((c shr 16) and 255) / 255f * mul, ((c shr 8) and 255) / 255f * mul, (c and 255) / 255f * mul)
        G.glUniform1f(uAlpha, a)
    }

    /** caixa: parent * T(px,py,pz) * Ry * Rx * T(0,oy,0) * S. cube: 0 = branco, 1..13 = textura do bloco */
    private fun box(parent: FloatArray, px: Float, py: Float, pz: Float, rx: Float, ry: Float,
                    sx: Float, sy: Float, sz: Float, oy: Float, col: Int, a: Float = 1f, mul: Float = 1f,
                    outArm: FloatArray? = null, cube: Int = 0) {
        System.arraycopy(parent, 0, tmp, 0, 16)
        Matrix.translateM(tmp, 0, px, py, pz)
        if (ry != 0f) Matrix.rotateM(tmp, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(tmp, 0, rx, 1f, 0f, 0f)
        if (outArm != null) System.arraycopy(tmp, 0, outArm, 0, 16)
        Matrix.translateM(tmp, 0, 0f, oy, 0f)
        Matrix.scaleM(tmp, 0, sx, sy, sz)
        G.glUniformMatrix4fv(uModel, 1, false, tmp, 0)
        tint(col, a, mul)
        G.glDrawElements(G.GL_TRIANGLES, 36, G.GL_UNSIGNED_SHORT, cube * 72)
    }

    private fun setBase(x: Float, y: Float, z: Float, yawDeg: Float) {
        Matrix.setIdentityM(base, 0); Matrix.translateM(base, 0, x, y, z)
        if (yawDeg != 0f) Matrix.rotateM(base, 0, yawDeg, 0f, 1f, 0f)
    }

    private fun groundY(x: Float, y: Float, z: Float): Float {
        var yy = floor(y + 0.5f).toInt()
        while (yy > 0 && !world.solid(floor(x).toInt(), yy, floor(z).toInt())) yy--
        return yy + 1f
    }

    private fun shadow(x: Float, y: Float, z: Float, size: Float) {
        val gy = groundY(x, y, z)
        val k = (1f - (y - gy) / 5f).coerceIn(0.1f, 1f)
        setBase(x, gy + 0.03f, z, 0f)
        box(base, 0f, 0f, 0f, 0f, 0f, size, 0.02f, size, 0f, 0x000000, 0.35f * k)
    }

    private fun swingDelta(id: Int, sp: Float): Float {
        if (sp >= 1f) return 0f
        if (id == Items.STAFF) return -35f * sin(min(1f, sp * 1.5f) * 3.1416f)
        val k = if (id == Items.AXE) 1.25f else if (id in 1..13) 0.7f else 1f
        val d = when { sp < 0.3f -> -50f * (sp / 0.3f); sp < 0.55f -> -50f + 90f * ((sp - 0.3f) / 0.25f); else -> 40f * (1f - (sp - 0.55f) / 0.45f) }
        return k * d
    }

    /** Modelo do item. Origem = punho, lâmina/cabo estendem-se em +Z. Detalhado: camadas, brilho e runas. */
    private fun drawItem(m: FloatArray, id: Int, t: Float) {
        val glint = 0.5f + 0.5f * sin(t * 3f)
        when {
            id in 1..13 -> {
                box(m, 0f, 0.1f, 0.2f, 0f, 30f, 0.28f, 0.28f, 0.28f, 0f, 0xFFFFFF, 1f, 1f, null, id)
            }
            id == Items.SWORD -> {
                box(m, 0f, 0f, 0.55f, 0f, 0f, 0.115f, 0.03f, 0.78f, 0f, 0xD7E4F4)        // lâmina
                box(m, 0f, 0f, 0.55f, 0f, 0f, 0.05f, 0.045f, 0.74f, 0f, 0x8FD0FF)        // veio de cristal
                box(m, 0f, 0f, 0.55f, 0f, 0f, 0.02f, 0.05f, 0.7f, 0f, 0xFFFFFF, 0.6f + 0.4f * glint)
                box(m, 0f, 0f, 0.96f, 0f, 0f, 0.075f, 0.028f, 0.1f, 0f, 0xF2F8FF)        // ponta
                box(m, 0f, 0f, 0.1f, 0f, 0f, 0.36f, 0.06f, 0.07f, 0f, 0xFFD060)          // guarda
                box(m, 0.19f, 0f, 0.1f, 0f, 0f, 0.07f, 0.075f, 0.12f, 0f, 0xFFE9A0)
                box(m, -0.19f, 0f, 0.1f, 0f, 0f, 0.07f, 0.075f, 0.12f, 0f, 0xFFE9A0)
                box(m, 0f, 0.035f, 0.1f, 0f, 0f, 0.08f, 0.04f, 0.08f, 0f, 0xFF5FA0)      // gema
                box(m, 0f, 0f, -0.04f, 0f, 0f, 0.065f, 0.065f, 0.2f, 0f, 0x7A5230)       // cabo
                box(m, 0f, 0f, -0.04f, 0f, 0f, 0.075f, 0.055f, 0.03f, 0f, 0xC9A06A)
                box(m, 0f, 0f, -0.1f, 0f, 0f, 0.075f, 0.055f, 0.03f, 0f, 0xC9A06A)
                box(m, 0f, 0f, -0.18f, 0f, 0f, 0.11f, 0.11f, 0.1f, 0f, 0xFFA3C8)         // pomo
            }
            id == Items.AXE -> {
                box(m, 0f, 0f, 0.32f, 0f, 0f, 0.065f, 0.065f, 0.95f, 0f, 0x7A5230)       // cabo
                box(m, 0f, 0f, 0.2f, 0f, 0f, 0.075f, 0.075f, 0.04f, 0f, 0xC9A06A)
                box(m, 0f, 0f, 0.4f, 0f, 0f, 0.075f, 0.075f, 0.04f, 0f, 0xC9A06A)
                box(m, 0f, 0f, 0.7f, 0f, 0f, 0.09f, 0.12f, 0.2f, 0f, 0x8A94A2)           // soquete
                box(m, 0f, 0.14f, 0.72f, 0f, 0f, 0.055f, 0.36f, 0.3f, 0f, 0xC8D2DE)       // lâmina
                box(m, 0f, 0.3f, 0.78f, 0f, 0f, 0.06f, 0.1f, 0.34f, 0f, 0xE8F0FA)
                box(m, 0f, 0.34f, 0.8f, 0f, 0f, 0.065f, 0.04f, 0.3f, 0f, 0xFFFFFF, 0.7f + 0.3f * glint) // fio
                box(m, 0f, -0.1f, 0.7f, 0f, 0f, 0.05f, 0.14f, 0.2f, 0f, 0xA9B4C2)
                box(m, 0f, 0f, 0.0f, 0f, 0f, 0.1f, 0.1f, 0.09f, 0f, 0xFFD060)
            }
            id == Items.STAFF -> {
                box(m, 0f, 0f, 0.38f, 0f, 0f, 0.065f, 0.065f, 1.15f, 0f, 0x8E5FD6)
                box(m, 0f, 0f, 0.38f, 0f, 0f, 0.03f, 0.07f, 1.1f, 0f, 0xB48CFF)
                box(m, 0f, 0f, 0.2f, 0f, 0f, 0.1f, 0.1f, 0.05f, 0f, 0xFFD060)
                box(m, 0f, 0f, 0.82f, 0f, 0f, 0.12f, 0.12f, 0.06f, 0f, 0xFFD060)
                box(m, 0.09f, 0f, 0.92f, 0f, 0f, 0.04f, 0.04f, 0.2f, 0f, 0xFFD060)       // garras
                box(m, -0.09f, 0f, 0.92f, 0f, 0f, 0.04f, 0.04f, 0.2f, 0f, 0xFFD060)
                box(m, 0f, 0.09f, 0.92f, 0f, 0f, 0.04f, 0.04f, 0.2f, 0f, 0xFFD060)
                box(m, 0f, -0.09f, 0.92f, 0f, 0f, 0.04f, 0.04f, 0.2f, 0f, 0xFFD060)
                val pulse = 0.5f + 0.5f * sin(t * 4f)
                box(m, 0f, 0f, 1.0f, t * 120f, t * 80f, 0.2f, 0.2f, 0.2f, 0f, 0x7FE8FF, 0.95f)
                box(m, 0f, 0f, 1.0f, -t * 90f, t * 60f, 0.11f, 0.11f, 0.11f, 0f, 0xFFFFFF)
                box(m, 0f, 0f, 1.0f, 0f, 0f, 0.32f + 0.12f * pulse, 0.32f + 0.12f * pulse, 0.32f + 0.12f * pulse, 0f, 0x7FE8FF, 0.26f)
                for (k in 0 until 3) {   // estrelinhas orbitando
                    val ang = t * 2.2f + k * 2.0944f
                    box(m, cos(ang) * 0.2f, sin(ang) * 0.2f, 1.0f + sin(t * 3f + k) * 0.05f, t * 200f, 0f, 0.05f, 0.05f, 0.05f, 0f, 0xFFF2A0)
                }
            }
            id == Items.PICK -> {
                box(m, 0f, 0f, 0.32f, 0f, 0f, 0.065f, 0.065f, 0.95f, 0f, 0x7A5230)
                box(m, 0f, 0f, 0.2f, 0f, 0f, 0.075f, 0.075f, 0.04f, 0f, 0xC9A06A)
                box(m, 0f, 0f, 0.74f, 0f, 0f, 0.1f, 0.1f, 0.12f, 0f, 0x8A94A2)
                box(m, 0f, 0f, 0.74f, 0f, 0f, 0.46f, 0.075f, 0.1f, 0f, 0xC8D2DE)          // cabeça
                box(m, 0.26f, 0f, 0.68f, 0f, 20f, 0.18f, 0.07f, 0.08f, 0f, 0xE8F0FA)
                box(m, -0.26f, 0f, 0.68f, 0f, -20f, 0.18f, 0.07f, 0.08f, 0f, 0xE8F0FA)
                box(m, 0.36f, 0f, 0.62f, 0f, 35f, 0.12f, 0.06f, 0.07f, 0f, 0xFFFFFF, 0.8f + 0.2f * glint)
                box(m, -0.36f, 0f, 0.62f, 0f, -35f, 0.12f, 0.06f, 0.07f, 0f, 0xFFFFFF, 0.8f + 0.2f * glint)
                box(m, 0f, 0f, 0.0f, 0f, 0f, 0.1f, 0.1f, 0.09f, 0f, 0xFFD060)
            }
        }
    }

    private fun drawPlayer(t: Float) {
        val p = game.player
        val wp = game.walkPhase; val wa = game.walkAmt
        val sw = sin(wp) * 38f * wa
        val air = if (p.onGround) 0f else 1f
        val idle = sin(t * 2f) * 3f * (1f - wa)
        val bob = if (wa < 0.1f) sin(t * 2f) * 0.012f else abs(sin(wp)) * 0.05f * wa
        setBase(p.x, p.y + bob, p.z, Math.toDegrees(game.bodyYaw.toDouble()).toFloat())
        val skin = 0xF2C29B; val hair = 0x6B3FA0; val shirt = 0x7FD9C8
        // pernas e botas
        box(base, -0.13f, 0.55f, 0f, sw, 0f, 0.24f, 0.55f, 0.26f, -0.275f, 0x4A5BB5)
        box(base, 0.13f, 0.55f, 0f, -sw, 0f, 0.24f, 0.55f, 0.26f, -0.275f, 0x4A5BB5)
        box(base, -0.13f, 0.55f, 0f, sw, 0f, 0.25f, 0.13f, 0.29f, -0.49f, 0x5B3A29)
        box(base, 0.13f, 0.55f, 0f, -sw, 0f, 0.25f, 0.13f, 0.29f, -0.49f, 0x5B3A29)
        // corpo, cinto, cachecol
        box(base, 0f, 0.86f, 0f, 0f, 0f, 0.54f, 0.62f, 0.32f, 0f, shirt)
        box(base, 0f, 0.6f, 0f, 0f, 0f, 0.56f, 0.07f, 0.34f, 0f, 0xFFD060)
        box(base, 0f, 1.16f, 0f, 0f, 0f, 0.58f, 0.1f, 0.36f, 0f, 0xFFA3C8)
        box(base, 0.14f, 1.0f, 0.2f, 0f, 0f, 0.1f, 0.3f, 0.06f, 0f, 0xFFA3C8)
        // braço esquerdo
        box(base, -0.37f, 1.12f, 0f, -sw * 0.9f - 40f * air + idle, 0f, 0.2f, 0.4f, 0.22f, -0.2f, shirt)
        box(base, -0.37f, 1.12f, 0f, -sw * 0.9f - 40f * air + idle, 0f, 0.19f, 0.15f, 0.21f, -0.47f, skin)
        // braço direito com item
        val id = game.cur()
        val ang = -28f + sw * 0.5f - 25f * air - idle + swingDelta(id, game.swing)
        box(base, 0.37f, 1.12f, 0f, ang, 0f, 0.2f, 0.4f, 0.22f, -0.2f, shirt, outArm = arm)
        box(base, 0.37f, 1.12f, 0f, ang, 0f, 0.19f, 0.15f, 0.21f, -0.47f, skin)
        System.arraycopy(arm, 0, base2, 0, 16)
        Matrix.translateM(base2, 0, 0f, -0.5f, 0f)
        if (id !in 1..13) Matrix.rotateM(base2, 0, -35f, 1f, 0f, 0f)
        if (id == Items.AXE) Matrix.rotateM(base2, 0, 180f, 0f, 0f, 1f)
        else if (id == Items.SWORD || id == Items.PICK) Matrix.rotateM(base2, 0, 90f, 0f, 0f, 1f)
        drawItem(base2, id, t)
        // cabeça grandinha e fofa
        box(base, 0f, 1.17f, 0f, 0f, 0f, 0.58f, 0.58f, 0.58f, 0.29f, skin)
        box(base, 0f, 1.73f, -0.01f, 0f, 0f, 0.62f, 0.14f, 0.62f, 0f, hair)
        box(base, 0f, 1.48f, -0.27f, 0f, 0f, 0.62f, 0.5f, 0.14f, 0f, hair)
        box(base, -0.3f, 1.56f, 0.02f, 0f, 0f, 0.06f, 0.34f, 0.5f, 0f, hair)
        box(base, 0.3f, 1.56f, 0.02f, 0f, 0f, 0.06f, 0.34f, 0.5f, 0f, hair)
        box(base, -0.14f, 1.66f, 0.28f, 0f, 0f, 0.3f, 0.08f, 0.06f, 0f, hair)
        box(base, 0.16f, 1.66f, 0.28f, 0f, 0f, 0.26f, 0.1f, 0.06f, 0f, hair)
        box(base, -0.14f, 1.5f, 0.295f, 0f, 0f, 0.1f, 0.15f, 0.02f, 0f, 0x20232E)
        box(base, 0.14f, 1.5f, 0.295f, 0f, 0f, 0.1f, 0.15f, 0.02f, 0f, 0x20232E)
        box(base, -0.12f, 1.54f, 0.3f, 0f, 0f, 0.045f, 0.045f, 0.02f, 0f, 0xFFFFFF)
        box(base, 0.16f, 1.54f, 0.3f, 0f, 0f, 0.045f, 0.045f, 0.02f, 0f, 0xFFFFFF)
        box(base, -0.24f, 1.4f, 0.295f, 0f, 0f, 0.1f, 0.05f, 0.02f, 0f, 0xFF9AA8)
        box(base, 0.24f, 1.4f, 0.295f, 0f, 0f, 0.1f, 0.05f, 0.02f, 0f, 0xFF9AA8)
        box(base, 0f, 1.37f, 0.295f, 0f, 0f, 0.08f, 0.025f, 0.02f, 0f, 0x8A3A3A)
        // lacinho
        box(base, 0.26f, 1.72f, 0.1f, 0f, 0f, 0.14f, 0.14f, 0.1f, 0f, 0xFFA3C8)
    }

    private fun drawSlime(s: Slime) {
        val sq = s.squash + sin(s.t * 4f) * 0.04f
        val sy = 1f + sq; val sxz = 1f - sq * 0.5f
        val face = atan2(game.player.x - s.x, game.player.z - s.z)
        setBase(s.x, s.y, s.z, Math.toDegrees(face.toDouble()).toFloat())
        val f = if (s.flash > 0f) 1f else 0f
        val col = if (f > 0f) 0xFFC0C0 else 0x6FD65A
        box(base, 0f, 0f, 0f, 0f, 0f, 0.84f * sxz, 0.8f * sy, 0.84f * sxz, 0.4f, col, 0.82f, 1f + f * 0.6f)
        box(base, 0f, 0f, 0f, 0f, 0f, 0.4f * sxz, 0.34f * sy, 0.4f * sxz, 0.38f, 0x3FA83A, 0.9f)
        val y = 0.5f * sy
        box(base, -0.2f * sxz, y, 0.425f * sxz, 0f, 0f, 0.15f, 0.2f, 0.03f, 0f, 0x1A1A1A)
        box(base, 0.2f * sxz, y, 0.425f * sxz, 0f, 0f, 0.15f, 0.2f, 0.03f, 0f, 0x1A1A1A)
        box(base, -0.17f * sxz, y + 0.05f * sy, 0.44f * sxz, 0f, 0f, 0.05f, 0.06f, 0.02f, 0f, 0xFFFFFF)
        box(base, 0.23f * sxz, y + 0.05f * sy, 0.44f * sxz, 0f, 0f, 0.05f, 0.06f, 0.02f, 0f, 0xFFFFFF)
        box(base, 0f, y - 0.16f * sy, 0.425f * sxz, 0f, 0f, 0.16f, 0.05f, 0.03f, 0f, 0x1A1A1A)
        box(base, -0.3f * sxz, y - 0.1f * sy, 0.4f * sxz, 0f, 0f, 0.1f, 0.05f, 0.03f, 0f, 0xFF8FA8, 0.8f)
        box(base, 0.3f * sxz, y - 0.1f * sy, 0.4f * sxz, 0f, 0f, 0.1f, 0.05f, 0.03f, 0f, 0xFF8FA8, 0.8f)
    }

    /** punho fechado: palma + 4 dedos enrolados no cabo + polegar por cima (cabo em +Z passa por dentro) */
    private fun drawFist(m: FloatArray) {
        val skin = 0xF2C29B; val skinD = 0xE2AE86
        box(m, 0f, -0.02f, -0.045f, 0f, 0f, 0.15f, 0.12f, 0.19f, 0f, skinD)
        for (k in 0 until 4) box(m, 0f, 0.05f, -0.115f + k * 0.05f, 0f, 0f, 0.17f, 0.09f, 0.046f, 0f, if (k % 2 == 0) skin else 0xEDB78F)
        box(m, 0.025f, 0.108f, -0.02f, 0f, 0f, 0.055f, 0.05f, 0.15f, 0f, skin)
        box(m, 0.025f, 0.108f, 0.06f, 0f, 0f, 0.05f, 0.045f, 0.03f, 0f, 0xF8D2B2)
    }

    /** mão segurando bloco: palma por baixo, dedos subindo pela parte de trás e polegar do lado */
    private fun drawBlockHand(m: FloatArray) {
        val skin = 0xF2C29B
        box(m, 0f, -0.08f, 0.16f, 0f, 0f, 0.17f, 0.07f, 0.19f, 0f, skin)
        for (k in 0 until 4) box(m, -0.06f + k * 0.04f, -0.01f, 0.05f, 0f, 0f, 0.034f, 0.13f, 0.042f, 0f, if (k % 2 == 0) skin else 0xEDB78F)
        box(m, 0.17f, -0.01f, 0.2f, 0f, 0f, 0.045f, 0.045f, 0.13f, 0f, skin)
    }

    private fun drawHand(dt: Float) {
        G.glClear(G.GL_DEPTH_BUFFER_BIT)
        G.glUniformMatrix4fv(uVP, 1, false, proj, 0); G.glUniform3f(uCam, 0f, 0f, 0f)
        bindMesh(cubeVb, cubeIb)
        val id = game.cur()
        if (id != lastId) { lastId = id; equip = 0f }
        equip = min(1f, equip + dt * 3.2f)
        val e = 1f - (1f - equip) * (1f - equip) * (1f - equip)
        val dyaw = game.yaw - prevYaw; val dp = game.pitch - prevPitch; prevYaw = game.yaw; prevPitch = game.pitch
        val k = min(1f, 10f * dt)
        swayX += ((dyaw / dt * 0.02f).coerceIn(-0.1f, 0.1f) - swayX) * k
        swayY += ((-dp / dt * 0.02f).coerceIn(-0.1f, 0.1f) - swayY) * k
        val wp = game.walkPhase; val wa = game.walkAmt
        val bobX = sin(wp) * 0.03f * wa; val bobY = abs(sin(wp)) * 0.03f * wa
        val breath = sin(game.time * 1.8f) * 0.006f
        Matrix.setIdentityM(fp, 0)
        Matrix.translateM(fp, 0, 0.34f + bobX + swayX, -0.38f + bobY + swayY + breath - (1f - e) * 0.55f, -0.42f)
        Matrix.rotateM(fp, 0, 180f, 0f, 1f, 0f)
        val rest = if (id in 1..13) -12f else -20f
        Matrix.rotateM(fp, 0, rest + swingDelta(id, game.swing), 1f, 0f, 0f)
        if (game.swing < 1f) Matrix.rotateM(fp, 0, -sin(game.swing * 3.1416f) * 14f, 0f, 1f, 0f)
        val skin = 0xF2C29B
        box(fp, 0f, 0f, 0.22f, 0f, 0f, 0.15f, 0.15f, 0.44f, 0f, 0x7FD9C8)       // manga
        box(fp, 0f, 0f, 0.45f, 0f, 0f, 0.17f, 0.17f, 0.05f, 0f, 0xFFA3C8)       // punho da manga
        box(fp, 0f, 0f, 0.52f, 0f, 0f, 0.12f, 0.12f, 0.1f, 0f, skin)            // pulso
        System.arraycopy(fp, 0, itemM, 0, 16)
        Matrix.translateM(itemM, 0, 0f, 0f, 0.6f)
        Matrix.rotateM(itemM, 0, 30f, 0f, 1f, 0f)
        val ir = if (id in 1..13) 0f else if (id == Items.STAFF) -28f else -40f
        if (ir != 0f) Matrix.rotateM(itemM, 0, ir, 1f, 0f, 0f)
        if (id in 1..13) drawBlockHand(itemM) else drawFist(itemM)
        if (id == Items.AXE) Matrix.rotateM(itemM, 0, 180f, 0f, 0f, 1f)
        else if (id == Items.SWORD || id == Items.PICK) Matrix.rotateM(itemM, 0, 90f, 0f, 0f, 1f)
        drawItem(itemM, id, game.time)
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - last) / 1e9f).coerceIn(0.001f, 0.05f); last = now
        game.update(dt)
        var built = 0
        for (cz in 0 until World.CZ) for (cx in 0 until World.CX) {
            val ci = cz * World.CX + cx
            if (!world.dirty[ci] || built >= 4) continue
            world.dirty[ci] = false; built++
            world.buildChunk(cx, cz, ob, wb)
            cnt[ci * 2] = upload(ob, vbo[ci * 2], ibo[ci * 2])
            cnt[ci * 2 + 1] = upload(wb, vbo[ci * 2 + 1], ibo[ci * 2 + 1])
        }

        G.glClear(G.GL_COLOR_BUFFER_BIT or G.GL_DEPTH_BUFFER_BIT)
        G.glUseProgram(prog)
        G.glActiveTexture(G.GL_TEXTURE0); G.glUniform1i(uTex, 0)
        val sh = game.shake * 0.25f
        val ex = game.camX + (rnd.nextFloat() - 0.5f) * sh; val ey = game.camY + (rnd.nextFloat() - 0.5f) * sh
        val ez = game.camZ + (rnd.nextFloat() - 0.5f) * sh
        Matrix.setLookAtM(view, 0, ex, ey, ez, ex + game.lookDirX(), ey + game.lookDirY(), ez + game.lookDirZ(), 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        val under = world.get(floor(ex).toInt(), floor(ey).toInt(), floor(ez).toInt()) == B.WATER
        val fr = if (under) 0.38f else fogR; val fg = if (under) 0.76f else fogG; val fb = if (under) 0.93f else fogB
        // céu: degradê, sol, nuvens suaves
        Matrix.invertM(inv, 0, vp, 0)
        G.glDisable(G.GL_DEPTH_TEST); G.glDisable(G.GL_CULL_FACE)
        G.glUseProgram(skyProg)
        G.glUniformMatrix4fv(skyInv, 1, false, inv, 0); G.glUniform3f(skyCam, ex, ey, ez)
        G.glUniform3f(skyHor, fr, fg, fb); G.glUniform1f(skyTime, game.time)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, 0)
        G.glEnableVertexAttribArray(skyP); G.glVertexAttribPointer(skyP, 2, G.GL_FLOAT, false, 0, quad)
        G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
        G.glDisableVertexAttribArray(skyP)
        G.glEnable(G.GL_DEPTH_TEST); G.glEnable(G.GL_CULL_FACE)
        G.glUseProgram(prog)
        G.glActiveTexture(G.GL_TEXTURE0); G.glUniform1i(uTex, 0)
        G.glUniformMatrix4fv(uVP, 1, false, vp, 0)
        G.glUniform3f(uCam, ex, ey, ez)
        G.glUniform3f(uFog, fr, fg, fb)
        G.glUniform1f(uTime, game.time); G.glUniform1f(uWind, 1f); G.glUniform1f(uUnder, if (under) 1f else 0f)
        G.glDisable(G.GL_BLEND)

        G.glUniformMatrix4fv(uModel, 1, false, ident, 0)
        tint(0xFFFFFF, 1f)
        for (i in 0 until World.CX * World.CZ) {
            if (cnt[i * 2] == 0) continue
            bindMesh(vbo[i * 2], ibo[i * 2]); G.glDrawElements(G.GL_TRIANGLES, cnt[i * 2], G.GL_UNSIGNED_SHORT, 0)
        }
        G.glUniform1f(uWind, 0f)
        bindMesh(cubeVb, cubeIb)
        if (game.thirdPerson && game.deadTimer <= 0f) drawPlayer(game.time)
        G.glEnable(G.GL_BLEND)
        G.glDepthMask(false)
        for (s in game.slimes) shadow(s.x, s.y, s.z, 0.9f)
        if (game.thirdPerson) shadow(game.player.x, game.player.y, game.player.z, 0.8f)
        G.glDepthMask(true)
        for (s in game.slimes) drawSlime(s)
        for (q in game.parts) {
            setBase(q.x, q.y, q.z, game.time * 200f)
            box(base, 0f, 0f, 0f, 0f, 0f, q.size, q.size, q.size, 0f, q.color, min(1f, q.life * 2f))
        }
        for (b in game.bolts) {
            setBase(b.x, b.y, b.z, 0f)
            box(base, 0f, 0f, 0f, game.time * 400f, game.time * 300f, 0.18f, 0.18f, 0.18f, 0f, 0xE8FBFF)
            G.glDepthMask(false)
            box(base, 0f, 0f, 0f, 0f, 0f, 0.4f, 0.4f, 0.4f, 0f, 0x5FD8FF, 0.35f)
            G.glDepthMask(true)
        }
        if (game.hasHit && game.deadTimer <= 0f) {
            setBase(game.hx + 0.5f, game.hy + 0.5f, game.hz + 0.5f, 0f)
            G.glDepthMask(false)
            box(base, 0f, 0f, 0f, 0f, 0f, 1.01f, 1.01f, 1.01f, 0f, 0xFFFFFF, 0.18f + 0.1f * sin(game.time * 6f))
            G.glDepthMask(true)
        }
        G.glUniform1f(uWind, 1f)
        G.glUniformMatrix4fv(uModel, 1, false, ident, 0)
        G.glDisable(G.GL_CULL_FACE)
        tint(0xFFFFFF, 0.8f, 0.94f + 0.06f * sin(game.time * 2f))
        for (i in 0 until World.CX * World.CZ) {
            if (cnt[i * 2 + 1] == 0) continue
            bindMesh(vbo[i * 2 + 1], ibo[i * 2 + 1]); G.glDrawElements(G.GL_TRIANGLES, cnt[i * 2 + 1], G.GL_UNSIGNED_SHORT, 0)
        }
        G.glEnable(G.GL_CULL_FACE)
        G.glUniform1f(uWind, 0f)
        if (!game.thirdPerson && game.deadTimer <= 0f) drawHand(dt)
    }
}
