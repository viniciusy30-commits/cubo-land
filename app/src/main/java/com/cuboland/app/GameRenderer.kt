package com.cuboland.app

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20 as G
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.*

class GameRenderer(val game: Game, private val appContext: Context? = null) : GLSurfaceView.Renderer {
    private val minecraftSkin = MinecraftSkinRenderer()
    private val world = game.world
    private var prog = 0
    private var aPos = 0; private var aCol = 0; private var aUV = 0
    private var uVP = 0; private var uModel = 0; private var uCam = 0; private var uTint = 0
    private var uRip = 0; private var uTime = 0; private var uWind = 0; private var uUnder = 0; private var uAlpha = 0; private var uFog = 0; private var uTex = 0
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
    private val fogR = 0.70f; private val fogG = 0.83f; private val fogB = 0.97f
    private var skyProg = 0; private var skyP = 0; private var skyInv = 0; private var skyCam = 0; private var skyHor = 0; private var skyTime = 0
    private val inv = FloatArray(16)
    private val quad = ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0) }
    private var atlasTex = 0; private var iconsBaked = false; private var vw = 1; private var vh = 1
    private var lastId = -1; private var equip = 1f
    private var prevYaw = 0f; private var prevPitch = 0f; private var swayX = 0f; private var swayY = 0f

    /** condição GLSL: o tile é de folhagem dos blocos novos (balança e ilumina como a folha do carvalho) */
    private fun leafCond(v: String): String = Blk.leafRuns.joinToString(" || ") { "($v > ${it[0] - 0.5f} && $v < ${it[1] + 0.5f})" }.ifEmpty { "false" }
    private fun glassCond(v: String): String = Blk.glassRuns.joinToString(" || ") { "($v > ${it[0] - 0.5f} && $v < ${it[1] + 0.5f})" }.ifEmpty { "false" }
    private val VS = """
        uniform mat4 uVP; uniform mat4 uModel; uniform vec3 uCam; uniform float uTime; uniform float uWind; uniform float uUnder;
        attribute vec3 aPos; attribute vec3 aCol; attribute vec2 aUV;
        varying vec3 vCol; varying float vFog; varying vec2 vUV; varying vec3 vWP; varying float vTile; varying vec3 vView; varying float vGlass;
        void main() {
            vec4 wp = uModel * vec4(aPos, 1.0);
            float tile = floor(aUV.x * ${Atlas.NT}.0) + ${Atlas.NT}.0 * floor(aUV.y * ${Atlas.ROWS}.0);
            if (uWind > 0.5) {
                float gust = 0.6 + 0.4 * sin(wp.x * 0.12 + wp.z * 0.08 + uTime * 0.7);
                if (tile >= 16.0 && tile < 20.0) {
                    float w = clamp((0.99 - aUV.y * ${Atlas.ROWS}.0) / 0.98, 0.0, 1.0); w = w * w;
                    wp.x += (sin(uTime * 2.3 + wp.x * 0.9 + wp.z * 0.6) * 0.07 + 0.04) * w * gust * 1.4;
                    wp.z += cos(uTime * 1.8 + wp.x * 0.5 + wp.z * 0.9) * 0.05 * w * gust * 1.4;
                } else if (tile == 8.0 || tile == 20.0 || tile == 21.0 || tile == 22.0 || ${leafCond("tile")}) {
                    float ph = wp.x * 0.8 + wp.z * 0.7;
                    float dx = (sin(uTime * 1.6 + wp.y * 1.2 + ph) * 0.05 + sin(uTime * 3.1 + ph * 1.7) * 0.015 + 0.02) * gust;
                    float dz = (cos(uTime * 1.3 + wp.y * 1.0 + ph) * 0.05 + cos(uTime * 2.7 + ph * 1.3) * 0.015) * gust;
                    float dy = sin(uTime * 2.0 + ph * 1.4 + wp.y) * 0.03;
                    wp.x += dx; wp.z += dz; wp.y += dy;
                } else if (tile == 10.0) {
                    wp.y += sin(uTime * 1.2 + wp.x * 0.7 + wp.z * 0.4) * 0.018 + cos(uTime * 0.9 + wp.z * 0.8 - wp.x * 0.4) * 0.014;
                }
            }
            gl_Position = uVP * wp;
            vGlass = (${glassCond("tile")}) ? 1.0 : 0.0; vCol = aCol; vUV = aUV; vWP = wp.xyz; vTile = tile; vView = uCam - wp.xyz;
            vFog = clamp((length(wp.xyz - uCam) - mix(38.0, 6.0, uUnder)) / mix(52.0, 62.0, uUnder), 0.0, 1.0);
            vFog = vFog * vFog * (3.0 - 2.0 * vFog);
        }"""
    private val FS = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform vec3 uTint; uniform vec3 uFog; uniform float uAlpha; uniform sampler2D uTex; uniform float uTime; uniform float uUnder; uniform float uWind; uniform vec4 uRip[4];
        varying vec3 vCol; varying float vFog; varying vec2 vUV; varying vec3 vWP; varying float vTile; varying vec3 vView; varying float vGlass;

        // brilho de luz no fundo (cáusticas): rede de linhas que dançam
        float caus(vec3 p, float t) {
            float a = sin(p.x * 1.7 + p.y * 0.9 + t * 0.9 + sin(p.z * 1.3 - t * 0.6) * 1.4);
            float b = sin(p.z * 1.9 - p.y * 0.7 - t * 0.8 + sin(p.x * 1.1 + t * 0.5) * 1.4);
            float k = abs(a + b) * 0.5;
            return pow(1.0 - k, 5.0);
        }

        void main() {
            vec2 uv = vUV;
            bool isWater = vTile > 9.5 && vTile < 10.5;
            bool isLeaf = (vTile > 7.5 && vTile < 8.5) || (vTile > 19.5 && vTile < 22.5) || ${leafCond("vTile")};
            vec4 t = texture2D(uTex, uv);
            bool isGlass = vGlass > 0.5;
            if (t.a < (isGlass ? 0.03 : 0.4)) discard;
            float l = clamp((vCol.r - 0.3) / 0.7, 0.0, 1.0);
            vec3 light = mix(vec3(0.80, 0.84, 1.0), vec3(1.05, 1.02, 0.95), l);
            vec3 c = t.rgb * vCol * uTint * light * 0.9;
            float alpha = uAlpha * t.a;
            if (isGlass) {   // vidro liso
                float sheen = clamp((t.a - 0.16) * 3.0, 0.0, 1.0);
                c = t.rgb * (0.92 + 0.12 * l) + vec3(0.07, 0.10, 0.14) * sheen;   // vidro liso: cor uniforme, sem faixa de reflexo
            }
            if (isLeaf) { float shl = sin(vWP.x * 0.9 + vWP.z * 0.8 + uTime * 1.1) * sin(vWP.y * 1.3 + vWP.x * 0.4 - uTime * 0.8); c *= 1.18 + 0.09 * shl; c += vec3(0.03, 0.05, 0.0) * max(shl, 0.0); }
            if (isWater) {
                float tm = uTime;
                vec2 P = vWP.xz;
                vec2 g = vec2(0.0);
                // ondas largas e suaves (nada de serrilhado)
                g += vec2(0.80, 0.60) * cos(dot(P, vec2(0.80, 0.60)) * 0.85 + tm * 0.75) * 0.040;
                g += vec2(-0.50, 0.87) * cos(dot(P, vec2(-0.50, 0.87)) * 1.35 + tm * 0.95) * 0.030;
                g += vec2(0.95, -0.31) * cos(dot(P, vec2(0.95, -0.31)) * 2.5 + tm * 1.3) * 0.013;
                float ripGlow = 0.0;
                for (int ri = 0; ri < 4; ri++) {
                    vec4 rp = uRip[ri];
                    if (rp.w > 0.0) {
                        vec2 dd = P - rp.xy; float dl = max(length(dd), 0.001);
                        float xx = dl - rp.z * 1.7;
                        float env = exp(-xx * xx * 2.4) * rp.w * exp(-rp.z * 0.8);
                        g += (dd / dl) * sin(xx * 3.0) * env * 0.35;
                        ripGlow += env * 0.5;
                    }
                }
                float pool = 1.0 - step(0.5, vCol.b);   // piscina/tanque (acima do nível do mar): água calma, sem espuma de praia
                g *= mix(1.0, 0.45, pool);
                vec3 n = normalize(vec3(-g.x * 1.25, 1.0, -g.y * 1.25));
                vec3 V = normalize(vView);
                if (dot(n, V) < 0.0) n = -n;
                vec3 L = normalize(vec3(0.55, 0.5, 0.65));
                float depth = mix(clamp(vCol.g, 0.0, 1.0), 0.40, pool);
                vec3 shallow = vec3(0.38, 0.86, 0.84);
                vec3 deep = vec3(0.10, 0.48, 0.78);
                vec3 base = mix(shallow, deep, smoothstep(0.12, 0.95, depth) * 0.6);
                float ndv = max(dot(n, V), 0.0);
                float fres = 0.02 + 0.98 * pow(1.0 - ndv, 5.0);
                vec3 R = reflect(-V, n);
                vec3 sky = mix(uFog, vec3(0.42, 0.66, 0.97), pow(clamp(R.y, 0.0, 1.0), 0.45));
                float rl = max(dot(R, L), 0.0);
                vec3 wc = base * (0.88 + 0.22 * dot(n, L));
                // luz suave desenhando teia na superfície
                wc += vec3(0.55, 0.95, 0.95) * caus(vec3(P.x * 0.9, 0.0, P.y * 0.9), tm * 0.7) * 0.10;
                wc = mix(wc, sky, clamp(fres * 1.05 + 0.05, 0.0, 0.85));
                wc += vec3(0.6) * ripGlow * 0.16;
                // brilho do sol: halo suave + cintilado delicado
                wc += vec3(1.0, 0.97, 0.88) * (pow(rl, 48.0) * 0.28);
                // espuma macia na beirada
                float foam = smoothstep(0.17, 0.0, depth) * (0.55 + 0.45 * sin(P.x * 4.0 + P.y * 3.5 + tm * 1.4 + sin(P.y * 2.5 - tm) * 2.0));
                wc = mix(wc, vec3(1.0), clamp(foam, 0.0, 1.0) * 0.45);
                float wa = mix(0.50, 0.78, smoothstep(0.0, 0.5, depth));
                wa = clamp(wa + fres * 0.30 + foam * 0.3, 0.0, 1.0);
                wa = mix(wa, clamp(0.56 + fres * 0.26, 0.0, 0.88), pool);
                if (uUnder > 0.5) {   // vista de baixo: lisa, degradê suave com brilho do sol e um balanço bem leve
                    float win = smoothstep(0.52, 0.80, ndv);
                    float sh = 0.5 + 0.5 * sin(P.x * 0.45 + P.y * 0.35 + tm * 0.5);
                    vec3 mir = mix(vec3(0.20, 0.58, 0.80), vec3(0.30, 0.70, 0.88), sh);
                    float sun = pow(max(dot(-V, L), 0.0), 8.0);
                    vec3 winc = mix(vec3(0.58, 0.88, 0.98), vec3(0.76, 0.96, 1.0), sh) + vec3(0.18) * ripGlow + vec3(1.0, 0.98, 0.88) * sun * 0.40;
                    wc = mix(mir, winc, win);
                    wa = mix(0.50, 0.16, win);
                }
                c = wc; alpha = wa;
            } else if (uWind > 0.5 && vWP.y < 9.86) {   // tudo que está debaixo d'água: azulado, escuro com a profundidade e luz dançando
                float dep = clamp((9.88 - vWP.y) / 6.0, 0.0, 1.0);
                c *= mix(vec3(0.92, 1.0, 1.0), vec3(0.8, 0.95, 1.0), dep * 0.5);
                c += vec3(0.55, 0.95, 1.0) * caus(vWP * 1.1, uTime) * 0.5 * (1.0 - dep * 0.6);
            }
            if ((vTile > 14.5 && vTile < 15.5) ) c = t.rgb * 1.25;
            if (uUnder > 0.5 && !isWater) {
                c *= vec3(0.88, 1.02, 1.10);
                c += vec3(0.5, 0.85, 1.0) * caus(vWP * 0.9, uTime * 1.2) * 0.35;
            }
            float g2 = dot(c, vec3(0.299, 0.587, 0.114));
            c = mix(vec3(g2), c, 1.12); c = mix(c, smoothstep(0.0, 1.0, c), 0.3);
            c = mix(c, uFog, vFog);
            gl_FragColor = vec4(c, alpha);
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
            vec3 c = mix(uHor, vec3(0.36, 0.60, 0.92), pow(h, 0.55));
            vec3 sd = normalize(vec3(0.55, 0.5, 0.65));
            float s = max(dot(d, sd), 0.0);
            c += vec3(1.0, 0.85, 0.55) * pow(s, 6.0) * 0.25 + vec3(1.0, 0.95, 0.8) * pow(s, 300.0) * 2.0;
            if (d.y > 0.02) {
                vec2 q = d.xz / (d.y + 0.25) * 1.4 + vec2(uTime * 0.012, 0.0);
                float n = ns(q * 1.5) * 0.55 + ns(q * 3.1) * 0.3 + ns(q * 6.5) * 0.15;
                float cl = smoothstep(0.52, 0.78, n) * smoothstep(0.02, 0.25, d.y);
                c = mix(c, vec3(0.93, 0.93, 0.93) * (0.84 + 0.14 * ns(q * 2.0)), cl * 0.85);
            }
            gl_FragColor = vec4(c, 1.0);
        }"""

    private fun shader(type: Int, src: String): Int {
        val s = G.glCreateShader(type); G.glShaderSource(s, src); G.glCompileShader(s); return s
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        minecraftSkin.init(appContext)
        for (d in voxOwned) { d.vb = 0; d.ib = 0; d.vdirty = true }; voxOwned.clear()
        G.glClearColor(fogR, fogG, fogB, 1f)
        prog = G.glCreateProgram()
        G.glAttachShader(prog, shader(G.GL_VERTEX_SHADER, VS)); G.glAttachShader(prog, shader(G.GL_FRAGMENT_SHADER, FS))
        G.glLinkProgram(prog)
        aPos = G.glGetAttribLocation(prog, "aPos"); aCol = G.glGetAttribLocation(prog, "aCol"); aUV = G.glGetAttribLocation(prog, "aUV")
        uVP = G.glGetUniformLocation(prog, "uVP"); uModel = G.glGetUniformLocation(prog, "uModel")
        uCam = G.glGetUniformLocation(prog, "uCam"); uTint = G.glGetUniformLocation(prog, "uTint")
        uAlpha = G.glGetUniformLocation(prog, "uAlpha"); uFog = G.glGetUniformLocation(prog, "uFog")
        uTex = G.glGetUniformLocation(prog, "uTex"); uTime = G.glGetUniformLocation(prog, "uTime"); uWind = G.glGetUniformLocation(prog, "uWind"); uUnder = G.glGetUniformLocation(prog, "uUnder"); uRip = G.glGetUniformLocation(prog, "uRip")
        skyProg = G.glCreateProgram()
        G.glAttachShader(skyProg, shader(G.GL_VERTEX_SHADER, SVS)); G.glAttachShader(skyProg, shader(G.GL_FRAGMENT_SHADER, SFS))
        G.glLinkProgram(skyProg)
        skyP = G.glGetAttribLocation(skyProg, "aP"); skyInv = G.glGetUniformLocation(skyProg, "uInv")
        skyCam = G.glGetUniformLocation(skyProg, "uCam"); skyHor = G.glGetUniformLocation(skyProg, "uHor")
        skyTime = G.glGetUniformLocation(skyProg, "uTime")
        Matrix.setIdentityM(ident, 0)
        tvProg = 0; tvTex = 0; tvTexOk = false
        run { val sb = IntArray(1); G.glGetIntegerv(G.GL_STENCIL_BITS, sb, 0); stencilOk = sb[0] >= 1 }
        G.glEnable(G.GL_DEPTH_TEST); G.glEnable(G.GL_CULL_FACE); G.glCullFace(G.GL_BACK); G.glFrontFace(G.GL_CCW)
        G.glBlendFunc(G.GL_SRC_ALPHA, G.GL_ONE_MINUS_SRC_ALPHA)
        // textura (atlas)
        val tex = IntArray(1); G.glGenTextures(1, tex, 0); atlasTex = tex[0]; iconsBaked = false
        G.glActiveTexture(G.GL_TEXTURE0); G.glBindTexture(G.GL_TEXTURE_2D, tex[0])
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR_MIPMAP_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_NEAREST)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
        val atlasBmp = Atlas.bmp; val rawPx = Atlas.raw
        if (rawPx != null) {   // sobe os pixels crus (RGBA sem pré-multiplicar): borda transparente mantém o verde e não vira contorno preto no mipmap
            val wT = Atlas.T * Atlas.NT; val hT = Atlas.T * Atlas.ROWS
            G.glPixelStorei(G.GL_UNPACK_ALIGNMENT, 1)
            G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA, wT, hT, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, null)
            val bb = java.nio.ByteBuffer.allocateDirect(wT * 128 * 4).order(java.nio.ByteOrder.nativeOrder())   // sobe em faixas de 128 linhas (antes: um buffer com o atlas inteiro, ~64 MB a mais no pico)
            var yy = 0
            while (yy < hT) {
                val nrow = Math.min(128, hT - yy)
                bb.clear()
                for (k in 0 until wT * nrow) { val c = rawPx[yy * wT + k]; bb.put(((c shr 16) and 255).toByte()); bb.put(((c shr 8) and 255).toByte()); bb.put((c and 255).toByte()); bb.put(((c ushr 24) and 255).toByte()) }
                bb.position(0)
                G.glTexSubImage2D(G.GL_TEXTURE_2D, 0, 0, yy, wT, nrow, G.GL_RGBA, G.GL_UNSIGNED_BYTE, bb)
                yy += nrow
            }
        } else GLUtils.texImage2D(G.GL_TEXTURE_2D, 0, atlasBmp, 0)
        G.glGenerateMipmap(G.GL_TEXTURE_2D)
        // buffers de chunk
        val ids = IntArray(vbo.size * 2); G.glGenBuffers(ids.size, ids, 0)
        for (i in vbo.indices) { vbo[i] = ids[i * 2]; ibo[i] = ids[i * 2 + 1]; cnt[i] = 0 }
        java.util.Arrays.fill(world.dirty, true)
        // cubos unitários: 0 = branco (entidades), 1..255 = blocos com textura
        val cb = MeshBuf(); val p = FloatArray(3)
        for (id in 0 until B.used) for (face in 0 until 6) {
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
                cb.vert(p[0], p[1], p[2], sh, sh, sh, Atlas.u(tile, tu), Atlas.v(tile, tv))
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
        val sb = ByteBuffer.allocateDirect(max(4, b.inn * 4)).order(ByteOrder.nativeOrder()).asIntBuffer()
        sb.put(b.ix, 0, b.inn); sb.position(0)
        G.glBindBuffer(G.GL_ELEMENT_ARRAY_BUFFER, ib); G.glBufferData(G.GL_ELEMENT_ARRAY_BUFFER, b.inn * 4, sb, G.GL_STATIC_DRAW)
        return b.inn
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        vw = w; vh = h
        G.glViewport(0, 0, w, h)
        Matrix.perspectiveM(proj, 0, 70f, w.toFloat() / h, 0.15f, 200f)   // near maior = menos z-fighting (as faces piscando)
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

    /** caixa: parent * T(px,py,pz) * Ry * Rx * T(0,oy,0) * S. cube: 0 = branco, 1..255 = textura do bloco */
    private fun box(parent: FloatArray, px: Float, py: Float, pz: Float, rx: Float, ry: Float,
                    sx: Float, sy: Float, sz: Float, oy: Float, col: Int, a: Float = 1f, mul: Float = 1f,
                    outArm: FloatArray? = null, cube: Int = 0, rz: Float = 0f) {
        System.arraycopy(parent, 0, tmp, 0, 16)
        Matrix.translateM(tmp, 0, px, py, pz)
        if (rz != 0f) Matrix.rotateM(tmp, 0, rz, 0f, 0f, 1f)
        if (ry != 0f) Matrix.rotateM(tmp, 0, ry, 0f, 1f, 0f)
        if (rx != 0f) Matrix.rotateM(tmp, 0, rx, 1f, 0f, 0f)
        if (outArm != null) System.arraycopy(tmp, 0, outArm, 0, 16)
        Matrix.translateM(tmp, 0, 0f, oy, 0f)
        Matrix.scaleM(tmp, 0, sx, sy, sz)
        G.glUniformMatrix4fv(uModel, 1, false, tmp, 0)
        tint(col, a, mul)
        G.glDrawElements(G.GL_TRIANGLES, 36, G.GL_UNSIGNED_INT, cube * 144)
    }

    private fun setBase(x: Float, y: Float, z: Float, yawDeg: Float) {
        Matrix.setIdentityM(base, 0); Matrix.translateM(base, 0, x, y, z)
        if (yawDeg != 0f) Matrix.rotateM(base, 0, yawDeg, 0f, 1f, 0f)
    }

    // ---------- marcas de impacto (decalques desenhados em cima da face do bloco) ----------
    private val dm = FloatArray(16)
    private val fT1 = arrayOf(floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 0f), floatArrayOf(1f, 0f, 0f))
    private val fT2 = arrayOf(floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 1f, 0f))

    /** retângulo fino colado na face (a = eixo, s = sinal), centrado em (u,v), girado ang, comprimento len x largura wid */
    private var fadeMul = 1f
    private fun decal(bx: Int, by: Int, bz: Int, a: Int, s: Int, u: Float, v: Float, ang: Float, len: Float, wid: Float, col: Int, al0: Float) {
        val al = al0 * fadeMul
        if (al < 0.02f || len <= 0.001f || wid <= 0.001f) return
        val t1 = fT1[a]; val t2 = fT2[a]
        val c = cos(ang); val sn = sin(ang)
        val e1x = t1[0] * c + t2[0] * sn; val e1y = t1[1] * c + t2[1] * sn; val e1z = t1[2] * c + t2[2] * sn
        val n3x = if (a == 0) s.toFloat() else 0f; val n3y = if (a == 1) s.toFloat() else 0f; val n3z = if (a == 2) s.toFloat() else 0f
        val e2x = n3y * e1z - n3z * e1y; val e2y = n3z * e1x - n3x * e1z; val e2z = n3x * e1y - n3y * e1x
        val lift = 0.5f + 0.006f
        dm[0] = e1x * len; dm[1] = e1y * len; dm[2] = e1z * len; dm[3] = 0f
        dm[4] = e2x * wid; dm[5] = e2y * wid; dm[6] = e2z * wid; dm[7] = 0f
        dm[8] = n3x * 0.008f; dm[9] = n3y * 0.008f; dm[10] = n3z * 0.008f; dm[11] = 0f
        dm[12] = bx + 0.5f + t1[0] * (u - 0.5f) + t2[0] * (v - 0.5f) + n3x * lift
        dm[13] = by + 0.5f + t1[1] * (u - 0.5f) + t2[1] * (v - 0.5f) + n3y * lift
        dm[14] = bz + 0.5f + t1[2] * (u - 0.5f) + t2[2] * (v - 0.5f) + n3z * lift
        dm[15] = 1f
        G.glUniformMatrix4fv(uModel, 1, false, dm, 0)
        tint(col, al)
        G.glDrawElements(G.GL_TRIANGLES, 36, G.GL_UNSIGNED_INT, 0)
    }

    private fun mixCol(a: Int, b: Int, t: Float): Int {
        val r = ((a shr 16 and 255) * (1 - t) + (b shr 16 and 255) * t).toInt(); val g = ((a shr 8 and 255) * (1 - t) + (b shr 8 and 255) * t).toInt(); val bl = ((a and 255) * (1 - t) + (b and 255) * t).toInt()
        return (r shl 16) or (g shl 8) or bl
    }

    private fun octa(d: Dmg, m: Mark, u: Float, v: Float, size: Float, col: Int, al: Float) {
        decal(d.x, d.y, d.z, m.a, m.s, u, v, 0.2f, size, size * 0.86f, col, al)
        decal(d.x, d.y, d.z, m.a, m.s, u, v, 0.2f + 0.7854f, size, size * 0.86f, col, al)
    }

    private fun drawMark(d: Dmg, m: Mark, dt: Float) {
        val age = game.time - m.born
        val pop = max(0f, 1f - age * 3f)            // estufadinha de impacto, dura ~0.33s
        val kk = 1f + 0.3f * pop
        val pale = mixCol(B.top[d.id], 0xFFF3DC, 0.55f)
        val tgt = (0.3f + 0.85f * d.prog).coerceAtMost(1f)
        m.g += (tgt - m.g) * min(1f, dt * 9f)
        val a = m.a; val s = m.s; val bx = d.x; val by = d.y; val bz = d.z
        // pedaço arrancado: cratera irregular mostrando o miolo do bloco, com borda iluminada embaixo
        val gs = (1f + 0.6f * d.prog.coerceIn(0f, 1f)) * kk
        val dark = mixCol(B.top[d.id], 0x000000, 0.5f); val deep = mixCol(B.top[d.id], 0x000000, 0.75f)
        var bi = 0
        while (bi < m.blobs.size && d.vox == null) {
            val bu = (m.u + m.blobs[bi]).coerceIn(0.04f, 0.96f); val bv = (m.v + m.blobs[bi + 1]).coerceIn(0.04f, 0.96f); val ba = m.blobs[bi + 2]; val sz = m.blobs[bi + 3] * gs
            decal(bx, by, bz, a, s, bu, bv, ba, sz * 1.3f, sz * 1.05f, 0x120904, 0.28f)
            decal(bx, by, bz, a, s, bu, bv, ba, sz, sz * 0.8f, dark, 0.92f)
            decal(bx, by, bz, a, s, bu, bv, ba + 0.7f, sz * 0.7f, sz * 0.55f, deep, 0.95f)
            decal(bx, by, bz, a, s, bu, bv - sz * 0.45f, 0f, sz * 0.8f, sz * 0.12f, pale, 0.4f)
            bi += 4
        }
        // rachaduras: sombra suave, brilho de borda e miolo escuro
        if (d.vox == null) for (sg in m.segs) {
            val r = ((m.g - sg.t0) / (sg.t1 - sg.t0)).coerceIn(0f, 1f)
            if (r <= 0f) continue
            val x2 = sg.x1 + (sg.x2 - sg.x1) * r; val y2 = sg.y1 + (sg.y2 - sg.y1) * r
            val ddx = x2 - sg.x1; val ddy = y2 - sg.y1; val l = hypot(ddx, ddy)
            if (l < 0.004f || !surfaceHas(d, a, s, (sg.x1 + x2) * 0.5f, (sg.y1 + y2) * 0.5f)) continue
            val an = atan2(ddy, ddx); val cx = (sg.x1 + x2) * 0.5f; val cy = (sg.y1 + y2) * 0.5f; val w = sg.w * 0.6f * (1f + 0.4f * pop)
            decal(bx, by, bz, a, s, cx, cy, an, l + w * 0.9f, w * 2.4f, 0x120904, 0.2f)
            decal(bx, by, bz, a, s, cx - sin(an) * w * 0.85f, cy + cos(an) * w * 0.85f, an, l, w * 0.5f, pale, 0.25f)
            decal(bx, by, bz, a, s, cx, cy, an, l + w * 0.8f, w, 0x2B1B12, 0.88f)
        }
        val u = m.u; val v = m.v; val c = cos(m.ang); val sn = sin(m.ang)
        if (d.vox != null) {   // bloco esculpido: o próprio formato já mostra o golpe; sobra só o brilhinho da espada
            if (m.kind == 2 && pop > 0f) for (e in 0 until 3) { val t = (e - 1) * m.len * 0.4f
                decal(bx, by, bz, a, s, u + c * t, v + sn * t, 0.785f, 0.045f * pop, 0.045f * pop, 0xCFF6FF, pop)
                decal(bx, by, bz, a, s, u + c * t, v + sn * t, 0f, 0.07f * pop, 0.01f, 0xFFFFFF, pop * 0.8f) }
        } else when (m.kind) {
            0 -> {   // machado: corte em cunha com lábios claros e lascas
                val L = m.len
                decal(bx, by, bz, a, s, u, v, m.ang, L * 1.14f, 0.09f * kk, 0x120904, 0.22f)
                decal(bx, by, bz, a, s, u, v, m.ang, L, 0.05f * kk, 0x24150C, 0.93f)
                decal(bx, by, bz, a, s, u, v, m.ang, L * 0.86f, 0.02f * kk, 0x050201, 1f)
                decal(bx, by, bz, a, s, u - sn * 0.033f, v + c * 0.033f, m.ang, L * 0.86f, 0.011f, pale, 0.7f)
                decal(bx, by, bz, a, s, u + sn * 0.03f, v - c * 0.03f, m.ang, L * 0.8f, 0.008f, 0x000000, 0.3f)
                var i = 0; while (i < m.chips.size) { decal(bx, by, bz, a, s, u + m.chips[i], v + m.chips[i + 1], m.chips[i + 2], m.chips[i + 3], m.chips[i + 3] * 0.6f, pale, 0.88f); i += 4 }
            }
            1 -> {   // picareta: furo fundo e destrutivo, borda esfarelada
                octa(d, m, u, v, 0.21f * kk, 0x120904, 0.24f)
                octa(d, m, u, v, 0.13f * kk, 0x2A190F, 0.92f)
                octa(d, m, u, v, 0.085f * kk, 0x0A0503, 1f)
                decal(bx, by, bz, a, s, u - 0.03f, v + 0.03f, 2.36f, 0.06f, 0.011f, pale, 0.55f)
                var i = 0; while (i < m.chips.size) { decal(bx, by, bz, a, s, u + m.chips[i], v + m.chips[i + 1], m.chips[i + 2], m.chips[i + 3], m.chips[i + 3] * 0.7f, pale, 0.9f); i += 4 }
            }
            2 -> {   // espada: talho fino e limpo, com brilhinho de cristal
                val L = m.len
                decal(bx, by, bz, a, s, u, v, m.ang, L * 1.06f, 0.032f, 0x120904, 0.2f)
                decal(bx, by, bz, a, s, u, v, m.ang, L, 0.012f * kk, 0x2A190F, 0.9f)
                decal(bx, by, bz, a, s, u, v, m.ang, L * 0.5f, 0.022f * kk, 0x1A0E08, 0.92f)
                decal(bx, by, bz, a, s, u - sn * 0.016f, v + c * 0.016f, m.ang, L * 0.9f, 0.006f, pale, 0.55f)
                if (pop > 0f) for (e in 0 until 3) { val t = (e - 1) * L * 0.4f
                    decal(bx, by, bz, a, s, u + c * t, v + sn * t, 0.785f, 0.045f * pop, 0.045f * pop, 0xCFF6FF, pop)
                    decal(bx, by, bz, a, s, u + c * t, v + sn * t, 0f, 0.07f * pop, 0.01f, 0xFFFFFF, pop * 0.8f) }
            }
            3 -> {   // estocada: furinho fino
                octa(d, m, u, v, 0.09f * kk, 0x120904, 0.25f)
                octa(d, m, u, v, 0.05f * kk, 0x1A0E08, 0.95f)
                octa(d, m, u, v, 0.025f * kk, 0x050201, 1f)
                var i = 0; while (i < m.chips.size) { decal(bx, by, bz, a, s, u + m.chips[i], v + m.chips[i + 1], m.chips[i + 2], m.chips[i + 3], m.chips[i + 3] * 0.7f, pale, 0.85f); i += 4 }
            }
            else -> { octa(d, m, u, v, 0.16f * kk, 0x000000, 0.16f); octa(d, m, u, v, 0.09f * kk, 0x000000, 0.14f) }
        }
    }

    private val voxOwned = ArrayList<Dmg>(); private val voxBuf = MeshBuf()

    private fun surfaceHas(d: Dmg, a: Int, s: Int, u: Float, v: Float): Boolean {
        val vx = d.vox ?: return true; val n = VN
        val i1 = (u * n).toInt().coerceIn(0, n - 1); val i2 = (v * n).toInt().coerceIn(0, n - 1); val io = if (s > 0) n - 1 else 0
        val x: Int; val y: Int; val z: Int
        when (a) { 0 -> { x = io; z = i1; y = i2 } 1 -> { x = i1; y = io; z = i2 } else -> { x = i1; y = i2; z = io } }
        return vx[(z * n + y) * n + x]
    }

    /** monta a malha do bloco esculpido: só as faces expostas dos mini-voxels que sobraram, com a textura do bloco */
    private fun buildVox(d: Dmg) {
        val v = d.vox ?: return
        val n = VN; val mb = voxBuf; mb.clear()
        val c = IntArray(3); val nb = IntArray(3); val p = FloatArray(3)
        for (k in 0 until n) for (j in 0 until n) for (i in 0 until n) {
            if (!v[(k * n + j) * n + i]) continue
            c[0] = i; c[1] = j; c[2] = k
            for (face in 0 until 6) {
                val a = face shr 1; val s = if ((face and 1) == 0) 1 else -1
                nb[0] = i; nb[1] = j; nb[2] = k; nb[a] += s
                val boundary = nb[a] < 0 || nb[a] >= n
                if (!boundary && v[(nb[2] * n + nb[1]) * n + nb[0]]) continue
                val u = (a + 1) % 3; val w = (a + 2) % 3
                var sh = when { a == 1 -> if (s > 0) 1f else 0.55f; a == 0 -> 0.82f; else -> 0.7f }
                var tile = if (a == 1) (if (s > 0) B.tTop[d.id] else B.tBot[d.id]) else B.tSide[d.id]
                var hh = (i * 73856093) xor (j * 19349663) xor (k * 83492791) xor (face * 2654435); hh = (hh xor (hh ushr 13)) * 1274126177; hh = hh xor (hh ushr 16)
                sh *= 0.95f + 0.1f * ((hh and 255) / 255f)
                if (!boundary) {   // miolo exposto: madeira mostra os anéis, grama mostra terra; escurece conforme afunda
                    val shell = min(min(i, n - 1 - i), min(min(j, n - 1 - j), min(k, n - 1 - k)))
                    sh *= (0.95f - 0.05f * min(shell, 6)).coerceAtLeast(0.66f)
                    tile = when (d.id) { B.WOOD -> 23; B.GRASS, B.DIRT -> B.tTop[B.DIRT]; else -> B.tSide[d.id] }
                }
                for (q in 0 until 4) {
                    val cu = World.QU[q]; val cv = World.QV[q]
                    p[a] = (if (s > 0) c[a] + 1 else c[a]).toFloat() / n - 0.5f
                    p[u] = (c[u] + cu).toFloat() / n - 0.5f; p[w] = (c[w] + cv).toFloat() / n - 0.5f
                    val fu = (c[u] + cu).toFloat() / n; val fv = (c[w] + cv).toFloat() / n
                    val tu: Float; val tv: Float
                    if (a == 0) { tu = fv; tv = 1f - fu } else if (a == 1) { tu = fu; tv = fv } else { tu = fu; tv = 1f - fv }
                    mb.vert(p[0], p[1], p[2], sh, sh, sh, Atlas.u(tile, tu), Atlas.v(tile, tv))
                }
                if (s > 0) { mb.tri(0, 1, 2); mb.tri(0, 2, 3) } else { mb.tri(0, 2, 1); mb.tri(0, 3, 2) }
                mb.vc += 4
            }
        }
        if (d.vb == 0) { val ids = IntArray(2); G.glGenBuffers(2, ids, 0); d.vb = ids[0]; d.ib = ids[1]; voxOwned.add(d) }
        d.icnt = upload(mb, d.vb, d.ib); d.vdirty = false
    }

    private fun drawVox(d: Dmg) {
        if (d.vdirty || d.vb == 0) buildVox(d)
        if (d.icnt == 0) return
        bindMesh(d.vb, d.ib)
        setBase(d.x + 0.5f, d.y + 0.5f, d.z + 0.5f, 0f)
        G.glUniformMatrix4fv(uModel, 1, false, base, 0); tint(0xFFFFFF, 1f)
        G.glDrawElements(G.GL_TRIANGLES, d.icnt, G.GL_UNSIGNED_INT, 0)
        bindMesh(cubeVb, cubeIb)
    }

    private fun drawDamage(dt: Float) {
        if (voxOwned.isNotEmpty()) {
            val it = voxOwned.iterator()
            while (it.hasNext()) { val d = it.next(); if (d.dead) { G.glDeleteBuffers(2, intArrayOf(d.vb, d.ib), 0); d.vb = 0; d.ib = 0; it.remove() } }
        }
        if (game.dmg.isEmpty() && game.slashes.isEmpty()) return
        for (d in game.dmg.values) {
            if (!d.carved) continue
            val dx = d.x + 0.5f - game.camX; val dy = d.y + 0.5f - game.camY; val dz = d.z + 0.5f - game.camZ
            if (dx * dx + dy * dy + dz * dz < 40f * 40f) drawVox(d)
        }
        G.glDepthMask(false)
        for (d in game.dmg.values) {
            val dx = d.x + 0.5f - game.camX; val dy = d.y + 0.5f - game.camY; val dz = d.z + 0.5f - game.camZ
            if (dx * dx + dy * dy + dz * dz > 18f * 18f) continue
            if (!d.carved) {
                setBase(d.x + 0.5f, d.y + 0.5f, d.z + 0.5f, 0f)
                box(base, 0f, 0f, 0f, 0f, 0f, 1.004f, 1.004f, 1.004f, 0f, 0x000000, 0.02f + 0.13f * d.prog.coerceIn(0f, 1f))
                if (d.x == game.brX && d.y == game.brY && d.z == game.brZ && game.hitPulse > 0f) { val kp = 1.012f + 0.03f * game.hitPulse; box(base, 0f, 0f, 0f, 0f, 0f, kp, kp, kp, 0f, 0xFFFFFF, 0.1f * game.hitPulse) }
            }
            for (m in d.marks) drawMark(d, m, dt)
        }
        for (sl in game.slashes) {
            val dx = sl.d.x + 0.5f - game.camX; val dy = sl.d.y + 0.5f - game.camY; val dz = sl.d.z + 0.5f - game.camZ
            if (dx * dx + dy * dy + dz * dz > 22f * 22f) continue
            fadeMul = ((Game.SLASH_LIFE - (game.time - sl.born)) / 25f).coerceIn(0f, 1f)
            drawMark(sl.d, sl.m, dt)
        }
        fadeMul = 1f
        G.glDepthMask(true)
    }

    private val ripArr = FloatArray(16)
    private fun fillRip() {
        java.util.Arrays.fill(ripArr, 0f)
        var k = 0; var i = game.ripples.size - 1
        while (i >= 0 && k < 4) {
            val r = game.ripples[i]; i--
            if (r.age > 3.1f) continue
            ripArr[k * 4] = r.x; ripArr[k * 4 + 1] = r.z; ripArr[k * 4 + 2] = r.age; ripArr[k * 4 + 3] = r.amp * (1f - r.age / 3.2f); k++
        }
    }

    /** bloco de folha cortado: treme, racha em 8 cubinhos que se afastam girando e encolhem (as folhinhas saem junto) */
    private fun drawLeafBreaks() {
        for (b in game.leafBreaks) {
            val cx = b.x + 0.5f; val cy = b.y + 0.5f; val cz = b.z + 0.5f
            if (!seen(cx, cy, cz, 30f)) continue
            if (b.t < Game.LB_SHAKE) {
                val u = max(0f, b.t) / Game.LB_SHAKE
                val j = sin(b.t * 140f) * 0.025f * (0.4f + u)
                val sc = 1f - 0.12f * u
                setBase(cx + j, cy + cos(b.t * 120f) * 0.015f, cz - j, 0f)
                box(base, 0f, 0f, 0f, j * 120f, sin(b.t * 90f) * 6f, sc, sc, sc, 0f, 0xFFFFFF, 1f, 1f + 0.12f * u, null, B.LEAVES)
            } else {
                val p = min(1f, (b.t - Game.LB_SHAKE) / Game.LB_FLY); val e = 1f - (1f - p) * (1f - p)
                val sz = 0.5f * (1f - p * 0.85f) * 0.88f
                val al = min(1f, (1f - p) * 1.6f)
                if (al <= 0.02f) continue
                for (i in 0 until 8) {
                    val sx = if (i and 1 == 0) -1f else 1f; val sy = if (i and 2 == 0) -1f else 1f; val sz2 = if (i and 4 == 0) -1f else 1f
                    val out = 0.25f + e * (0.55f + 0.12f * ((i * 5) % 3))
                    setBase(cx + sx * out, cy + sy * out * 0.8f + 0.25f * e - p * p * 0.9f, cz + sz2 * out, 0f)
                    box(base, 0f, 0f, 0f, p * (260f + i * 40f) * sx, p * (320f - i * 30f) * sy, sz, sz, sz, 0f, 0xFFFFFF, al, 1.08f, null, B.LEAVES, p * 200f * sz2)
                }
            }
        }
    }

    /** pegadinha fofa de patinha: almofada grande + 4 dedinhos redondos (quadrados girados 45 graus parecem círculos) */
    private fun paw(y: Float, grow: Float, col: Int, a: Float, big: Boolean) {
        val k = 1f + grow
        box(base, 0f, y, -0.030f, 0f, 0f, 0.115f * k, 0.012f, 0.085f * k, 0f, col, a)
        box(base, 0f, y, -0.030f, 0f, 45f, 0.082f * k, 0.012f, 0.082f * k, 0f, col, a)
        box(base, 0f, y, -0.030f, 0f, 22f, 0.095f * k, 0.012f, 0.095f * k, 0f, col, a * 0.8f)
        box(base, -0.062f, y, 0.050f, 0f, 45f, 0.040f * k, 0.012f, 0.040f * k, 0f, col, a)
        box(base, -0.021f, y, 0.075f, 0f, 45f, 0.044f * k, 0.012f, 0.044f * k, 0f, col, a)
        box(base, 0.021f, y, 0.075f, 0f, 45f, 0.044f * k, 0.012f, 0.044f * k, 0f, col, a)
        box(base, 0.062f, y, 0.050f, 0f, 45f, 0.040f * k, 0.012f, 0.040f * k, 0f, col, a)
    }

    /** pegadas fofinhas: areia = patinha funda com borda macia levantada, terra = patinha escura, grama = patinha de folhinha */
    private fun drawPrints() {
        for (q in game.prints) {
            if (!seen(q.x, q.y, q.z, 22f)) continue
            val fade = min(1f, (q.life() - (game.time - q.born)) / 4f)
            if (fade <= 0f) continue
            setBase(q.x, q.y + 0.012f, q.z, Math.toDegrees(q.ang.toDouble()).toFloat())
            when (q.id) {
                B.SAND -> {
                    paw(0.000f, 0.32f, 0xFFF5D2, 0.50f * fade, true)    // borda de areia levantada, bem macia
                    paw(0.004f, 0.00f, 0xB59C58, 0.80f * fade, true)    // fundo da patinha
                    paw(0.008f, -0.25f, 0x96813F, 0.40f * fade, true)   // sombra funda no meio
                }
                B.DIRT -> {
                    paw(0.003f, 0.20f, 0x7A5634, 0.40f * fade, true)
                    paw(0.006f, 0.00f, 0x4A3321, 0.78f * fade, true)
                }
                else -> {
                    paw(0.003f, 0.15f, 0x7FCB5E, 0.30f * fade, true)
                    paw(0.005f, -0.05f, 0x3F8A2E, 0.45f * fade, true)
                }
            }
            // as patinhas ficam alternadas: lado esquerdo/direito
        }
    }

    /** o 1 bloquinho que sobra no chão: gira e balança */
    private fun drawDrops() {
        for (q in game.drops) {
            val bob = sin(game.time * 3f + q.x * 2f) * 0.045f
            setBase(q.x, q.y + 0.06f + bob, q.z, game.time * 80f + q.z * 40f)
            box(base, 0f, 0f, 0f, 0f, 0f, 0.28f, 0.28f, 0.28f, 0f, 0xFFFFFF, 1f, 1f, null, q.id)
        }
    }

    /** anéis de espuma que se espalham a partir de onde algo entrou na água */
    private fun drawRipples() {
        val sy = World.WATER_Y + 0.9f
        for (r in game.ripples) {
            if (r.age < 0f) continue
            val life = 1f - r.age / 3.2f
            if (life <= 0.02f) continue
            // três anéis por onda: o da frente é forte, os de trás ficam mais finos e transparentes (dá volume/movimento)
            for (ring in 0 until 3) {
                val rad = 0.25f + r.age * 1.7f - ring * 0.32f
                if (rad < 0.15f) continue
                val al = life * min(1f, r.amp + 0.25f) * (0.8f - ring * 0.22f)
                if (al <= 0.02f) continue
                val n = (12 + rad * 6f).toInt().coerceAtMost(28)
                val seg = 6.2832f * rad / n * 1.08f
                val wob = r.age * 2f + ring
                for (k in 0 until n) {
                    val a = k * 6.2832f / n
                    val rr = rad * (1f + 0.04f * sin(a * 5f + wob))   // borda levemente ondulada
                    val x = r.x + cos(a) * rr; val z = r.z + sin(a) * rr
                    if (world.get(floor(x).toInt(), World.WATER_Y, floor(z).toInt()) != B.WATER) continue
                    val glint = if ((k + ring) % 3 == 0) 0xFFFFFF else 0xCDEFFF
                    setBase(x, sy + ring * 0.002f, z, a * 57.29578f - 90f)
                    box(base, 0f, 0f, 0f, 0f, 0f, seg * (if (ring == 0) 1f else 0.8f), 0.012f, if (ring == 0) 0.06f else 0.035f, 0f, glint, al)
                }
            }
        }
    }

    private fun drawDebris() {
        for (q in game.debris) {
            if (!seen(q.x, q.y, q.z, 40f)) continue
            val sc = q.size * min(1f, q.life * 2f)
            if (sc < 0.01f) continue
            setBase(q.x, q.y, q.z, 0f)
            box(base, 0f, 0f, 0f, q.rx, q.ry, sc, sc, sc, 0f, 0xFFFFFF, 1f, 1f, null, q.id, q.rz)
        }
    }

    /** a árvore inteira (troncos e folhas) girando em volta do ponto de corte */
    private val treeBuf = HashMap<FallTree, IntArray>()   // vbo, ibo, nº de índices dos galhos finos de cada árvore caindo

    /** folhinha que cai: cada espécie com o seu formato (octógono = 2 retângulos girados 45°) */
    private fun drawLeafShape(l: LeafP, tl: Float, rl: Float, al: Float) {
        val s = l.size; val c = l.color
        val lite = mixCol(c, 0xFFFFFF, 0.28f); val dark = mixCol(c, 0x000000, 0.32f)
        val th = 0.01f
        fun round(w: Float, len: Float, y0: Float, col: Int, mul: Float = 1f) {
            box(base, 0f, y0, 0f, tl, 0f, w, th, len, 0f, col, al, mul, null, 0, rl)
            box(base, 0f, y0 + 0.0008f, 0f, tl, 45f, w * 0.86f, th, len * 0.86f, 0f, col, al, mul, null, 0, rl)
        }
        fun vein(len: Float, z0: Float = 0f) = box(base, 0f, 0.006f, z0, tl, 0f, s * 0.07f, th * 1.2f, len, 0f, dark, al * 0.9f, 1f, null, 0, rl)
        when (l.shape) {
            Leaf3D.ABETO -> {   // agulhas
                for (k in -1..1) box(base, 0f, 0.001f * (k + 2), 0f, tl, k * 28f, s * 0.07f, th, s * 1.7f, 0f, if (k == 0) dark else c, al, 1f, null, 0, rl)
            }
            Leaf3D.CEREJA -> {   // pétala arredondada com pontinha clara
                round(s * 0.9f, s * 1.05f, 0f, c); box(base, 0f, 0.004f, s * 0.2f, tl, 0f, s * 0.5f, th, s * 0.5f, 0f, lite, al * 0.9f, 1.05f, null, 0, rl)
            }
            Leaf3D.OUTONO -> {   // folha de bordo: três lâminas em estrela
                for (k in 0..2) box(base, 0f, 0.001f * k, 0f, tl, k * 60f, s * 0.5f, th, s * 1.7f, 0f, if (k == 1) lite else c, al, 1f, null, 0, rl)
                vein(s * 1.6f)
            }
            Leaf3D.GINKGO -> {   // leque
                box(base, 0f, 0f, s * 0.35f, tl, 0f, s * 1.5f, th, s * 0.8f, 0f, c, al, 1f, null, 0, rl)
                box(base, 0f, 0.001f, s * 0.35f, tl, 40f, s * 1.1f, th, s * 0.8f, 0f, c, al, 1f, null, 0, rl)
                box(base, 0f, 0.003f, s * 0.35f, tl, 0f, s * 0.8f, th, s * 0.5f, 0f, lite, al * 0.9f, 1.05f, null, 0, rl)
                box(base, 0f, 0.005f, -s * 0.25f, tl, 0f, s * 0.07f, th, s * 0.7f, 0f, dark, al, 1f, null, 0, rl)
            }
            Leaf3D.BAMBU -> {   // lâmina fina e comprida
                box(base, 0f, 0f, 0f, tl, 0f, s * 0.38f, th, s * 2.3f, 0f, c, al, 1f, null, 0, rl)
                box(base, 0f, 0.002f, s * 0.1f, tl, 0f, s * 0.2f, th, s * 1.5f, 0f, lite, al * 0.9f, 1.05f, null, 0, rl)
                vein(s * 2.2f)
            }
            Leaf3D.ACACIA -> {   // folíolos em fileira
                for (k in -2..2) { box(base, -s * 0.28f, 0.001f * k, k * s * 0.34f, tl, 0f, s * 0.5f, th, s * 0.3f, 0f, c, al, 1f, null, 0, rl); box(base, s * 0.28f, 0.001f * k, k * s * 0.34f, tl, 0f, s * 0.5f, th, s * 0.3f, 0f, lite, al, 1f, null, 0, rl) }
                vein(s * 1.8f)
            }
            Leaf3D.SELVA -> { round(s * 1.25f, s * 1.6f, 0f, c); round(s * 0.7f, s * 0.8f, 0.003f, lite, 1.04f); vein(s * 1.8f) }
            Leaf3D.CARMESIM, Leaf3D.DISTORCIDO, Leaf3D.MUSGO -> { round(s * 0.85f, s * 0.85f, 0f, c); round(s * 0.45f, s * 0.45f, 0.003f, lite, 1.05f) }
            else -> { round(s * 0.95f, s * 1.2f, 0f, c); round(s * 0.5f, s * 0.55f, 0.003f, lite, 1.04f); vein(s * 1.7f) }   // carvalho, bétula, mangue…
        }
    }

    private fun drawTrees() {
        val it = treeBuf.entries.iterator()
        while (it.hasNext()) { val e = it.next(); if (!game.trees.contains(e.key)) { G.glDeleteBuffers(2, e.value, 0); it.remove() } }
        for (f in game.trees) {
            Matrix.setIdentityM(base2, 0); Matrix.translateM(base2, 0, f.px, f.py, f.pz)
            Matrix.rotateM(base2, 0, f.ang * 57.29578f, f.kx, 0f, f.kz)
            for (i in 0 until f.n) if (!f.gone[i]) box(base2, f.bl[i * 4].toFloat(), f.bl[i * 4 + 1] + f.yo, f.bl[i * 4 + 2].toFloat(), 0f, 0f, 1f, 1f, 1f, 0f, 0xFFFFFF, 1f, 1f, null, f.bl[i * 4 + 3])
            var bufs = treeBuf[f]
            val dm = f.dm
            if (bufs == null && dm != null) {
                val ids = IntArray(3); G.glGenBuffers(2, ids, 0); ids[2] = upload(dm, ids[0], ids[1]); treeBuf[f] = ids; bufs = ids; f.dm = null
            }
            if (bufs != null && bufs[2] > 0) {   // galhos finos e folhinhas caem junto (os dos blocos já quebrados somem)
                bindMesh(bufs[0], bufs[1])
                G.glUniformMatrix4fv(uModel, 1, false, base2, 0); tint(0xFFFFFF, 1f)
                var i = 0
                while (i < f.n) {
                    if (f.gone[i]) { i++; continue }
                    var j = i; while (j < f.n && !f.gone[j]) j++
                    val c = f.rs[j] - f.rs[i]
                    if (c > 0) G.glDrawElements(G.GL_TRIANGLES, c, G.GL_UNSIGNED_INT, f.rs[i] * 4)
                    i = j
                }
                bindMesh(cubeVb, cubeIb)
            }
        }
    }

    /** renderiza as ferramentas 3D da mão (mesmo modelo) em texturas para os ícones do HUD */
    private fun bakeIcons() {
        iconsBaked = true
        val S = 256
        val fbo = IntArray(1); val tx = IntArray(1); val rb = IntArray(1)
        G.glGenFramebuffers(1, fbo, 0); G.glGenTextures(1, tx, 0); G.glGenRenderbuffers(1, rb, 0)
        G.glBindTexture(G.GL_TEXTURE_2D, tx[0])
        G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA, S, S, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, null as java.nio.Buffer?)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR); G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE); G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
        G.glBindRenderbuffer(G.GL_RENDERBUFFER, rb[0]); G.glRenderbufferStorage(G.GL_RENDERBUFFER, G.GL_DEPTH_COMPONENT16, S, S)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, fbo[0])
        G.glFramebufferTexture2D(G.GL_FRAMEBUFFER, G.GL_COLOR_ATTACHMENT0, G.GL_TEXTURE_2D, tx[0], 0)
        G.glFramebufferRenderbuffer(G.GL_FRAMEBUFFER, G.GL_DEPTH_ATTACHMENT, G.GL_RENDERBUFFER, rb[0])
        if (G.glCheckFramebufferStatus(G.GL_FRAMEBUFFER) == G.GL_FRAMEBUFFER_COMPLETE) {
            G.glUseProgram(prog)
            G.glActiveTexture(G.GL_TEXTURE0); G.glBindTexture(G.GL_TEXTURE_2D, atlasTex); G.glUniform1i(uTex, 0)
            G.glViewport(0, 0, S, S)
            G.glEnable(G.GL_DEPTH_TEST); G.glEnable(G.GL_CULL_FACE); G.glEnable(G.GL_BLEND)
            G.glBlendFuncSeparate(G.GL_SRC_ALPHA, G.GL_ONE_MINUS_SRC_ALPHA, G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
            val ipj = FloatArray(16)
            G.glUniform3f(uCam, 0f, 0f, 0f); G.glUniform3f(uFog, 1f, 1f, 1f)
            G.glUniform1f(uTime, 0f); G.glUniform1f(uWind, 0f); G.glUniform1f(uUnder, 0f)
            bindMesh(cubeVb, cubeIb)
            val ids = intArrayOf(Items.SWORD, Items.AXE, Items.PICK, Items.STAFF)
            val cy = floatArrayOf(0.42f, 0.38f, 0.40f, 0.55f)
            val hf = floatArrayOf(0.74f, 0.62f, 0.66f, 0.92f)
            val mm = FloatArray(16)
            val px = ByteBuffer.allocateDirect(S * S * 4).order(ByteOrder.nativeOrder())
            for ((i, id) in ids.withIndex()) {
                G.glClearColor(0f, 0f, 0f, 0f); G.glClear(G.GL_COLOR_BUFFER_BIT or G.GL_DEPTH_BUFFER_BIT)
                Matrix.orthoM(ipj, 0, -hf[i], hf[i], -hf[i], hf[i], -6f, 6f)
                G.glUniformMatrix4fv(uVP, 1, false, ipj, 0)
                Matrix.setIdentityM(mm, 0)
                Matrix.rotateM(mm, 0, -42f, 0f, 0f, 1f)
                Matrix.rotateM(mm, 0, -72f, 0f, 1f, 0f)
                Matrix.translateM(mm, 0, 0f, -cy[i], 0f)
                drawTool3D(mm, id, 0.5f, 0f)
                px.clear(); G.glReadPixels(0, 0, S, S, G.GL_RGBA, G.GL_UNSIGNED_BYTE, px); px.position(0)
                val raw = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888); raw.copyPixelsFromBuffer(px)
                val fm = android.graphics.Matrix(); fm.preScale(1f, -1f)
                ToolIcons.map[id] = Bitmap.createBitmap(raw, 0, 0, S, S, fm, true)
            }
        }
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
        G.glBlendFunc(G.GL_SRC_ALPHA, G.GL_ONE_MINUS_SRC_ALPHA)
        G.glClearColor(fogR, fogG, fogB, 1f)
        G.glBindTexture(G.GL_TEXTURE_2D, atlasTex)
        G.glViewport(0, 0, vw, vh)
        G.glDeleteFramebuffers(1, fbo, 0); G.glDeleteRenderbuffers(1, rb, 0); G.glDeleteTextures(1, tx, 0)
    }


    /** gera (aos poucos, alguns por quadro) os ícones dos itens de decoração a partir do modelo real, em vista isométrica */
    private var scratchW: World? = null
    private fun bakeDecoIcons() {
        val S = 128
        val fbo = IntArray(1); val tx = IntArray(1); val rb = IntArray(1)
        G.glGenFramebuffers(1, fbo, 0); G.glGenTextures(1, tx, 0); G.glGenRenderbuffers(1, rb, 0)
        G.glBindTexture(G.GL_TEXTURE_2D, tx[0])
        G.glTexImage2D(G.GL_TEXTURE_2D, 0, G.GL_RGBA, S, S, 0, G.GL_RGBA, G.GL_UNSIGNED_BYTE, null as java.nio.Buffer?)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR); G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE); G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
        G.glBindRenderbuffer(G.GL_RENDERBUFFER, rb[0]); G.glRenderbufferStorage(G.GL_RENDERBUFFER, G.GL_DEPTH_COMPONENT16, S, S)
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, fbo[0])
        G.glFramebufferTexture2D(G.GL_FRAMEBUFFER, G.GL_COLOR_ATTACHMENT0, G.GL_TEXTURE_2D, tx[0], 0)
        G.glFramebufferRenderbuffer(G.GL_FRAMEBUFFER, G.GL_DEPTH_ATTACHMENT, G.GL_RENDERBUFFER, rb[0])
        if (G.glCheckFramebufferStatus(G.GL_FRAMEBUFFER) == G.GL_FRAMEBUFFER_COMPLETE) {
            G.glUseProgram(prog)
            G.glActiveTexture(G.GL_TEXTURE0); G.glBindTexture(G.GL_TEXTURE_2D, atlasTex); G.glUniform1i(uTex, 0)
            G.glViewport(0, 0, S, S)
            G.glEnable(G.GL_DEPTH_TEST); G.glEnable(G.GL_CULL_FACE); G.glEnable(G.GL_BLEND)
            G.glBlendFuncSeparate(G.GL_SRC_ALPHA, G.GL_ONE_MINUS_SRC_ALPHA, G.GL_ONE, G.GL_ONE_MINUS_SRC_ALPHA)
            val ipj = FloatArray(16)
            Matrix.orthoM(ipj, 0, -1f, 1f, -1f, 1f, -8f, 8f)
            G.glUniformMatrix4fv(uVP, 1, false, ipj, 0)
            G.glUniform3f(uCam, 0f, 0f, 0f); G.glUniform3f(uFog, 1f, 1f, 1f)
            G.glUniform1f(uTime, 0f); G.glUniform1f(uWind, 0f); G.glUniform1f(uUnder, 0f)
            tint(0xFFFFFF, 1f)
            val px = ByteBuffer.allocateDirect(S * S * 4).order(ByteOrder.nativeOrder())
            val mb = MeshBuf()
            val bufs = IntArray(2); G.glGenBuffers(2, bufs, 0)
            val rot = FloatArray(16); val mm = FloatArray(16); val cin = FloatArray(4); val cout = FloatArray(4)
            Matrix.setIdentityM(rot, 0); Matrix.rotateM(rot, 0, 30f, 1f, 0f, 0f); Matrix.rotateM(rot, 0, -35f, 0f, 1f, 0f)
            var done = 0
            while (done < 6) {
                val id = ToolIcons.nextPending()
                if (id < 0) break
                done++
                mb.clear()
                var bb: FloatArray? = null
                if (Deco.isDeco(id)) bb = Deco.emitIcon(mb, id, 1.12f)
                else if (B.shape(id) != 0) {
                    // blocos de formato próprio (escada, laje, cerca, cadeira...): emite num mundo vazio, sem vizinhos
                    var sw = scratchW
                    if (sw == null) { sw = World(); scratchW = sw }
                    Shapes.emit(mb, sw, 8, 8, 8, id, B.shape(id), 1.12f)
                    if (mb.vn >= 8) {
                        val r = floatArrayOf(1e9f, 1e9f, 1e9f, -1e9f, -1e9f, -1e9f)
                        var q = 0
                        while (q + 8 <= mb.vn) {
                            for (a in 0 until 3) { val vv = mb.v[q + a]; if (vv < r[a]) r[a] = vv; if (vv > r[a + 3]) r[a + 3] = vv }
                            q += 8
                        }
                        bb = r
                    }
                }
                if (bb == null) { ToolIcons.putDeco(id, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)); continue }
                val ccx = (bb[0] + bb[3]) / 2f; val ccy = (bb[1] + bb[4]) / 2f; val ccz = (bb[2] + bb[5]) / 2f
                // projeta os 8 cantos da caixa na vista pra enquadrar o item inteiro (itens grandes não cortam)
                var mnx = 1e9f; var mxx = -1e9f; var mny = 1e9f; var mxy = -1e9f
                for (k in 0 until 8) {
                    cin[0] = (if ((k and 1) != 0) bb[3] else bb[0]) - ccx
                    cin[1] = (if ((k and 2) != 0) bb[4] else bb[1]) - ccy
                    cin[2] = (if ((k and 4) != 0) bb[5] else bb[2]) - ccz
                    cin[3] = 1f
                    Matrix.multiplyMV(cout, 0, rot, 0, cin, 0)
                    if (cout[0] < mnx) mnx = cout[0]; if (cout[0] > mxx) mxx = cout[0]
                    if (cout[1] < mny) mny = cout[1]; if (cout[1] > mxy) mxy = cout[1]
                }
                val half = max(max(mxx - mnx, mxy - mny) / 2f, 0.05f)
                val sc = 0.92f / half
                Matrix.setIdentityM(mm, 0)
                Matrix.translateM(mm, 0, -(mnx + mxx) / 2f * sc, -(mny + mxy) / 2f * sc, 0f)
                Matrix.rotateM(mm, 0, 30f, 1f, 0f, 0f); Matrix.rotateM(mm, 0, -35f, 0f, 1f, 0f)
                Matrix.scaleM(mm, 0, sc, sc, sc)
                Matrix.translateM(mm, 0, -ccx, -ccy, -ccz)
                G.glUniformMatrix4fv(uModel, 1, false, mm, 0)
                val n = upload(mb, bufs[0], bufs[1])
                bindMesh(bufs[0], bufs[1])
                G.glClearColor(0f, 0f, 0f, 0f); G.glClear(G.GL_COLOR_BUFFER_BIT or G.GL_DEPTH_BUFFER_BIT)
                G.glDrawElements(G.GL_TRIANGLES, n, G.GL_UNSIGNED_INT, 0)
                px.clear(); G.glReadPixels(0, 0, S, S, G.GL_RGBA, G.GL_UNSIGNED_BYTE, px); px.position(0)
                val raw = Bitmap.createBitmap(S, S, Bitmap.Config.ARGB_8888); raw.copyPixelsFromBuffer(px)
                val fm = android.graphics.Matrix(); fm.preScale(1f, -1f)
                ToolIcons.putDeco(id, Bitmap.createBitmap(raw, 0, 0, S, S, fm, true))
                raw.recycle()
            }
            G.glDeleteBuffers(2, bufs, 0)
        }
        G.glBindFramebuffer(G.GL_FRAMEBUFFER, 0)
        G.glBlendFunc(G.GL_SRC_ALPHA, G.GL_ONE_MINUS_SRC_ALPHA)
        G.glClearColor(fogR, fogG, fogB, 1f)
        G.glBindTexture(G.GL_TEXTURE_2D, atlasTex)
        G.glViewport(0, 0, vw, vh)
        G.glDeleteFramebuffers(1, fbo, 0); G.glDeleteRenderbuffers(1, rb, 0); G.glDeleteTextures(1, tx, 0)
        bindMesh(cubeVb, cubeIb)
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
        val k = if (id == Items.AXE) 1.25f else if (Items.isBlock(id)) 0.7f else 1f
        val d = when { sp < 0.3f -> -50f * (sp / 0.3f); sp < 0.55f -> -50f + 90f * ((sp - 0.3f) / 0.25f); else -> 40f * (1f - (sp - 0.55f) / 0.45f) }
        return k * d
    }

    /** Modelos 3D próprios das ferramentas (pastel, camadas, gemas e brilho). Origem = centro do punho; o eixo da ferramenta é +Z; o fio/cabeça fica em -Y. */
    private fun drawItem(m: FloatArray, id: Int, t: Float) {
        val glint = 0.5f + 0.5f * sin(t * 3f)
        when {
            Items.isBlock(id) -> {
                box(m, 0f, 0.2f, 0.18f, 0f, 30f, 0.34f, 0.34f, 0.34f, 0f, 0xFFFFFF, 1f, 1f, null, id)
            }
            id == Items.SWORD -> {
                box(m, 0f, 0f, -0.17f, 0f, 0f, 0.12f, 0.12f, 0.12f, 0f, 0xFF8CBF, 1f, rz = 0f)
                box(m, 0f, 0f, -0.17f, 0f, 0f, 0.09f, 0.09f, 0.15f, 0f, 0xFFC2DC, 1f, rz = 0f)
                box(m, 0f, 0f, -0.2f, 0f, 0f, 0.05f, 0.05f, 0.05f, 0f, 0xFFFFFF, 0.63f + 0.27f * glint, rz = 0f)
                box(m, 0f, 0f, 0.05f, 0f, 0f, 0.07f, 0.07f, 0.38f, 0f, 0xB892F0, 1f, rz = 0f)
                box(m, 0f, 0f, 0.05f, 0f, 0f, 0.045f, 0.045f, 0.4f, 0f, 0xD2B8FF, 1f, rz = 0f)
                box(m, 0f, 0f, -0.07f, 0f, 0f, 0.085f, 0.085f, 0.035f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, 0.02f, 0f, 0f, 0.085f, 0.085f, 0.035f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, 0.11f, 0f, 0f, 0.085f, 0.085f, 0.035f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, 0.255f, 0f, 0f, 0.08f, 0.4f, 0.07f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, 0.285f, 0f, 0f, 0.07f, 0.36f, 0.03f, 0f, 0xFFEDA8, 1f, rz = 0f)
                box(m, 0f, -0.21f, 0.27f, 0f, 0f, 0.1f, 0.1f, 0.13f, 0f, 0xFFEDA8, 1f, rz = 0f)
                box(m, 0f, 0.21f, 0.27f, 0f, 0f, 0.1f, 0.1f, 0.13f, 0f, 0xFFEDA8, 1f, rz = 0f)
                box(m, 0f, -0.21f, 0.34f, 0f, 0f, 0.07f, 0.07f, 0.06f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0.21f, 0.34f, 0f, 0f, 0.07f, 0.07f, 0.06f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0.045f, 0f, 0.27f, 0f, 0f, 0.05f, 0.11f, 0.11f, 0f, 0xFF8CBF, 1f, rz = 0f)
                box(m, -0.045f, 0f, 0.27f, 0f, 0f, 0.05f, 0.11f, 0.11f, 0f, 0xFF8CBF, 1f, rz = 0f)
                box(m, 0.05f, 0f, 0.27f, 0f, 0f, 0.03f, 0.05f, 0.05f, 0f, 0xFFFFFF, 0.63f + 0.27f * glint, rz = 0f)
                box(m, -0.05f, 0f, 0.27f, 0f, 0f, 0.03f, 0.05f, 0.05f, 0f, 0xFFFFFF, 0.63f + 0.27f * glint, rz = 0f)
                box(m, 0f, 0f, 0.64f, 0f, 0f, 0.05f, 0.17f, 0.72f, 0f, 0xD6ECFF, 1f, rz = 0f)
                box(m, 0f, 0f, 0.64f, 0f, 0f, 0.065f, 0.09f, 0.7f, 0f, 0x8FD0FF, 1f, rz = 0f)
                box(m, 0f, -0.085f, 0.64f, 0f, 0f, 0.052f, 0.03f, 0.7f, 0f, 0xF4FAFF, 1f, rz = 0f)
                box(m, 0f, 0.085f, 0.64f, 0f, 0f, 0.052f, 0.03f, 0.7f, 0f, 0xF4FAFF, 1f, rz = 0f)
                box(m, 0f, 0f, 0.64f, 0f, 0f, 0.07f, 0.025f, 0.64f, 0f, 0xFFFFFF, 0.49f + 0.21f * glint, rz = 0f)
                box(m, 0f, 0f, 1.04f, 0f, 0f, 0.05f, 0.12f, 0.12f, 0f, 0xE2F2FF, 1f, rz = 0f)
                box(m, 0f, 0f, 1.11f, 0f, 0f, 0.04f, 0.065f, 0.1f, 0f, 0xF4FAFF, 1f, rz = 0f)
                box(m, 0f, 0f, 1.15f, 0f, 0f, 0.03f, 0.03f, 0.05f, 0f, 0xFFFFFF, 0.63f + 0.27f * glint, rz = 0f)
                box(m, 0.04f, 0f, 0.45f, 0f, 0f, 0.075f, 0.05f, 0.05f, 0f, 0xFFF2A0, 0.63f + 0.27f * glint, rz = 0f)
                box(m, -0.04f, 0f, 0.74f, 0f, 0f, 0.075f, 0.05f, 0.05f, 0f, 0xFFF2A0, 0.63f + 0.27f * glint, rz = 0f)
                box(m, 0.05f, 0f, 0.96f, t * 150f, 0f, 0.05f, 0.05f, 0.05f, 0f, 0xFFF2A0, 0.6f + 0.4f * glint)
            }
            id == Items.AXE -> {
                box(m, 0f, 0f, -0.1f, 0f, 0f, 0.11f, 0.11f, 0.07f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, -0.1f, 0f, 0f, 0.085f, 0.085f, 0.09f, 0f, 0xFFEDA8, 1f, rz = 0f)
                box(m, 0f, 0f, 0.38f, 0f, 0f, 0.075f, 0.075f, 1f, 0f, 0xC99A68, 1f, rz = 0f)
                box(m, 0f, 0f, 0.38f, 0f, 0f, 0.05f, 0.05f, 1.02f, 0f, 0xE3B987, 1f, rz = 0f)
                box(m, 0f, 0f, 0f, 0f, 0f, 0.095f, 0.095f, 0.3f, 0f, 0x7FD9C8, 1f, rz = 0f)
                box(m, 0f, 0f, 0f, 0f, 0f, 0.105f, 0.105f, 0.04f, 0f, 0xA6EBDD, 1f, rz = 0f)
                box(m, 0f, 0f, 0.1f, 0f, 0f, 0.105f, 0.105f, 0.04f, 0f, 0xA6EBDD, 1f, rz = 0f)
                box(m, 0f, 0f, -0.1f, 0f, 0f, 0.105f, 0.105f, 0.04f, 0f, 0xA6EBDD, 1f, rz = 0f)
                box(m, 0f, 0f, 0.46f, 0f, 0f, 0.1f, 0.1f, 0.07f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, 0.86f, 0f, 0f, 0.1f, 0.1f, 0.07f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, 0.7f, 0f, 0f, 0.11f, 0.13f, 0.24f, 0f, 0x8E9DB8, 1f, rz = 0f)
                box(m, 0f, 0f, 0.7f, 0f, 0f, 0.12f, 0.09f, 0.2f, 0f, 0xB9C8DE, 1f, rz = 0f)
                box(m, 0f, -0.1f, 0.7f, 0f, 0f, 0.07f, 0.1f, 0.3f, 0f, 0xB9C8DE, 1f, rz = 0f)
                box(m, 0f, -0.17f, 0.7f, 0f, 0f, 0.065f, 0.08f, 0.42f, 0f, 0xB9C8DE, 1f, rz = 0f)
                box(m, 0f, -0.24f, 0.7f, 0f, 0f, 0.055f, 0.08f, 0.52f, 0f, 0xB9C8DE, 1f, rz = 0f)
                box(m, 0f, -0.31f, 0.7f, 0f, 0f, 0.045f, 0.08f, 0.6f, 0f, 0xB9C8DE, 1f, rz = 0f)
                box(m, 0f, -0.1f, 0.7f, 0f, 0f, 0.075f, 0.07f, 0.2f, 0f, 0xE7F0FC, 1f, rz = 0f)
                box(m, 0f, -0.17f, 0.7f, 0f, 0f, 0.07f, 0.07f, 0.28f, 0f, 0xE7F0FC, 1f, rz = 0f)
                box(m, 0f, -0.24f, 0.7f, 0f, 0f, 0.06f, 0.07f, 0.36f, 0f, 0xE7F0FC, 1f, rz = 0f)
                box(m, 0f, -0.37f, 0.7f, 0f, 0f, 0.035f, 0.05f, 0.62f, 0f, 0xFFFFFF, 0.595f + 0.255f * glint, rz = 0f)
                box(m, 0f, -0.34f, 0.7f, 0f, 0f, 0.04f, 0.03f, 0.58f, 0f, 0xFFFFFF, 0.385f + 0.165f * glint, rz = 0f)
                box(m, 0f, -0.17f, 1f, 0f, 0f, 0.075f, 0.2f, 0.04f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, -0.17f, 0.4f, 0f, 0f, 0.075f, 0.2f, 0.04f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0.035f, -0.14f, 0.7f, 0f, 0f, 0.04f, 0.12f, 0.12f, 0f, 0xFF8CBF, 1f, rz = 0f)
                box(m, -0.035f, -0.14f, 0.7f, 0f, 0f, 0.04f, 0.12f, 0.12f, 0f, 0xFF8CBF, 1f, rz = 0f)
                box(m, 0.04f, -0.14f, 0.72f, 0f, 0f, 0.03f, 0.05f, 0.05f, 0f, 0xFFFFFF, 0.63f + 0.27f * glint, rz = 0f)
                box(m, -0.04f, -0.14f, 0.72f, 0f, 0f, 0.03f, 0.05f, 0.05f, 0f, 0xFFFFFF, 0.63f + 0.27f * glint, rz = 0f)
                box(m, 0f, 0.1f, 0.7f, 0f, 0f, 0.09f, 0.12f, 0.14f, 0f, 0x8E9DB8, 1f, rz = 0f)
                box(m, 0f, 0.17f, 0.7f, 0f, 0f, 0.07f, 0.07f, 0.1f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0.17f, 0.7f, 0f, 0f, 0.05f, 0.09f, 0.06f, 0f, 0xFFEDA8, 1f, rz = 0f)
            }
            id == Items.PICK -> {
                box(m, 0f, 0f, -0.1f, 0f, 0f, 0.11f, 0.11f, 0.07f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, -0.1f, 0f, 0f, 0.085f, 0.085f, 0.09f, 0f, 0xFFEDA8, 1f, rz = 0f)
                box(m, 0f, 0f, 0.38f, 0f, 0f, 0.075f, 0.075f, 1f, 0f, 0xC99A68, 1f, rz = 0f)
                box(m, 0f, 0f, 0.38f, 0f, 0f, 0.05f, 0.05f, 1.02f, 0f, 0xE3B987, 1f, rz = 0f)
                box(m, 0f, 0f, 0f, 0f, 0f, 0.095f, 0.095f, 0.3f, 0f, 0x7FD9C8, 1f, rz = 0f)
                box(m, 0f, 0f, 0f, 0f, 0f, 0.105f, 0.105f, 0.04f, 0f, 0xA6EBDD, 1f, rz = 0f)
                box(m, 0f, 0f, 0.1f, 0f, 0f, 0.105f, 0.105f, 0.04f, 0f, 0xA6EBDD, 1f, rz = 0f)
                box(m, 0f, 0f, -0.1f, 0f, 0f, 0.105f, 0.105f, 0.04f, 0f, 0xA6EBDD, 1f, rz = 0f)
                box(m, 0f, 0f, 0.46f, 0f, 0f, 0.1f, 0.1f, 0.07f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, 0.84f, 0f, 0f, 0.12f, 0.14f, 0.14f, 0f, 0x8E9DB8, 1f, rz = 0f)
                box(m, 0f, 0f, 0.86f, 0f, 0f, 0.13f, 0.1f, 0.1f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, 0.86f, 0f, 0f, 0.14f, 0.06f, 0.12f, 0f, 0xFFEDA8, 1f, rz = 0f)
                box(m, 0f, -0.2f, 0.88f, 18f, 0f, 0.075f, 0.34f, 0.1f, 0f, 0xB9C8DE, 1f, rz = 0f)
                box(m, 0f, 0.2f, 0.88f, -18f, 0f, 0.075f, 0.34f, 0.1f, 0f, 0xB9C8DE, 1f, rz = 0f)
                box(m, 0f, -0.2f, 0.9f, 18f, 0f, 0.085f, 0.3f, 0.05f, 0f, 0xE7F0FC, 1f, rz = 0f)
                box(m, 0f, 0.2f, 0.9f, -18f, 0f, 0.085f, 0.3f, 0.05f, 0f, 0xE7F0FC, 1f, rz = 0f)
                box(m, 0f, -0.42f, 0.8f, 38f, 0f, 0.06f, 0.2f, 0.08f, 0f, 0xB9C8DE, 1f, rz = 0f)
                box(m, 0f, 0.42f, 0.8f, -38f, 0f, 0.06f, 0.2f, 0.08f, 0f, 0xB9C8DE, 1f, rz = 0f)
                box(m, 0f, -0.42f, 0.82f, 38f, 0f, 0.07f, 0.17f, 0.04f, 0f, 0xE7F0FC, 1f, rz = 0f)
                box(m, 0f, 0.42f, 0.82f, -38f, 0f, 0.07f, 0.17f, 0.04f, 0f, 0xE7F0FC, 1f, rz = 0f)
                box(m, 0f, -0.53f, 0.68f, 60f, 0f, 0.045f, 0.12f, 0.06f, 0f, 0xFFFFFF, 0.63f + 0.27f * glint, rz = 0f)
                box(m, 0f, 0.53f, 0.68f, -60f, 0f, 0.045f, 0.12f, 0.06f, 0f, 0xFFFFFF, 0.63f + 0.27f * glint, rz = 0f)
                box(m, 0.045f, 0f, 0.86f, 0f, 0f, 0.04f, 0.07f, 0.07f, 0f, 0xFF8CBF, 1f, rz = 0f)
                box(m, -0.045f, 0f, 0.86f, 0f, 0f, 0.04f, 0.07f, 0.07f, 0f, 0xFF8CBF, 1f, rz = 0f)
            }
            id == Items.STAFF -> {
                box(m, 0f, 0f, 0.4f, 0f, 0f, 0.075f, 0.075f, 1.15f, 0f, 0xB892F0, 1f, rz = 0f)
                box(m, 0f, 0f, 0.4f, 0f, 0f, 0.04f, 0.04f, 1.17f, 0f, 0xD8C2FF, 1f, rz = 0f)
                box(m, 0f, 0f, 0f, 0f, 0f, 0.1f, 0.1f, 0.28f, 0f, 0x7FD9C8, 1f, rz = 0f)
                box(m, 0f, 0f, -0.12f, 0f, 0f, 0.11f, 0.11f, 0.06f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0f, 0.86f, 0f, 0f, 0.13f, 0.13f, 0.07f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0.1f, 0f, 0.96f, 0f, 0f, 0.04f, 0.04f, 0.2f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, -0.1f, 0f, 0.96f, 0f, 0f, 0.04f, 0.04f, 0.2f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, -0.1f, 0.96f, 0f, 0f, 0.04f, 0.04f, 0.2f, 0f, 0xFFD66B, 1f, rz = 0f)
                box(m, 0f, 0.1f, 0.96f, 0f, 0f, 0.04f, 0.04f, 0.2f, 0f, 0xFFD66B, 1f, rz = 0f)
                val pulse = 0.5f + 0.5f * sin(t * 4f)
                box(m, 0f, 0f, 1.1f, t * 120f, t * 80f, 0.2f, 0.2f, 0.2f, 0f, 0x7FE8FF, 0.95f)
                box(m, 0f, 0f, 1.1f, -t * 90f, t * 60f, 0.11f, 0.11f, 0.11f, 0f, 0xFFFFFF)
                box(m, 0f, 0f, 1.1f, 0f, 0f, 0.32f + 0.12f * pulse, 0.32f + 0.12f * pulse, 0.32f + 0.12f * pulse, 0f, 0x7FE8FF, 0.26f)
                for (k in 0 until 3) {
                    val ang = t * 2.2f + k * 2.0944f
                    box(m, cos(ang) * 0.2f, sin(ang) * 0.2f, 1.1f + sin(t * 3f + k) * 0.05f, t * 200f, 0f, 0.05f, 0.05f, 0.05f, 0f, 0xFFF2A0)
                }
            }
        }
    }

    /** ângulo do cabo na 3ª pessoa: TILT = quanto deita pra frente (0 = em pé), OUT = quanto abre pro lado (inverta o sinal se abrir pra dentro) */
    private val TOOL_TILT = -93f; private val TOOL_OUT = 0f
    private val cm = CharModel()
    private val handM = FloatArray(16)
    private val charBox: BoxFn = { m, px, py, pz, rx, ry, sx, sy, sz, oy, col, a, rz -> box(m, px, py, pz, rx, ry, sx, sy, sz, oy, col, a, 1f, null, 0, rz) }

    private val an = Anim()
    private var lastPT = 0f; private var phaseA = 0f; private var runA = 0f; private var landA = 0f
    private var wasAirP = false; private var lastVyP = 0f; private var airSm = 0f; private var landSm = 0f; private var vyHold = 0f; private var flySm = 0f; private var flySp = 0f; private val tpP = FloatArray(10); private var diveSm = 0f; private var tiltSm = 0f; private var dashSm = 0f; private var swimPhA = 0f; private var swimFastA = 0f; private var stompSm = 0f; private var vpSm = 0f; private var svySm = 0f
    private var skSm = 0f; private var skManSm = 0f; private var skFlSm = 0f; private var skGdSm = 0f; private var fovCur = 70f
    private var airTimeP = 0f; private var jchSm = 0f; private var sjSm = 0f; private var flipAng = 0f; private var landRate = 5f
    private val SUPER_FLIP = true   // cambalhota no super pulo com a carga quase cheia (false = sem cambalhota)

    private fun drawPlayer(t: Float) {
        val p = game.player
        val dtp = (t - lastPT).coerceIn(0f, 0.1f); lastPT = t
        val wa = game.walkAmt
        val swm = game.swimAmt
        val airRaw = !p.onGround && swm < 0.2f
        airTimeP = if (airRaw) airTimeP + dtp else 0f
        val airNow = airRaw && (airTimeP > 0.08f || p.vy > 2.5f)   // descer degrau/rampa não vira "pulo" (acabou o piscar de pose); pulo de verdade entra na hora
        val runTarget = if (p.onGround && swm < 0.2f) ((wa - 0.62f) / 0.3f).coerceIn(0f, 1f) else 0f
        runA += (runTarget - runA) * min(1f, 8f * dtp)
        // passada ligada à velocidade real do chão: o pé não desliza (nem devagar)
        val spdH = sqrt(p.vx * p.vx + p.vz * p.vz)
        val strideA = (26f + 30f * runA) * 0.01745f
        val stepIn = hypot(game.stickX, game.stickY) > 0.1f   // dedo no analógico: a fase já corre desde o 1º frame (antes ficava parada até a velocidade subir = pernas travadas)
        val stepMin = if (stepIn) 8.5f * (1f - 0.6f * game.lieA) * min(1f, wa * 2.5f) else 0f
        phaseA += dtp * (if (wa > 0.02f && p.onGround) max(stepMin, (spdH / ((0.89f - 0.49f * game.lieA) * strideA)).coerceIn(0f, 30f)) else 0f)
        if (wasAirP && p.onGround && lastVyP < -4f) { landA = (-lastVyP / 12f).coerceIn(0.35f, 1.5f); landRate = if (landA > 1.05f) 2.8f else 5f }   // queda de super pulo: afunda mais e levanta mais devagar
        landA = max(0f, landA - dtp * landRate)
        if (airNow && !wasAirP) vyHold = p.vy   // começa a pose já na velocidade certa (sem herdar a queda anterior)
        wasAirP = airNow; lastVyP = p.vy
        val id = if (game.driving) 0 else game.cur()   // dirigindo: mãos no volante/guidão, sem item
        airSm += ((if (airNow) 1f else 0f) - airSm) * min(1f, 9f * dtp)
        if (airNow) vyHold += (p.vy - vyHold) * min(1f, 12f * dtp)   // segura a pose de queda até os pés tocarem de verdade (sem pulo de frame)
        landSm += (landA - landSm) * min(1f, 16f * dtp)
        val flyNow = game.flying && !p.onGround
        flySm += ((if (flyNow) 1f else 0f) - flySm) * min(1f, 6f * dtp)
        flySp += (min(1f, spdH / 10f) - flySp) * min(1f, 5f * dtp)
        dashSm += ((if (flyNow && (spdH > 11f || game.dashActive)) 1f else 0f) - dashSm) * min(1f, 3.5f * dtp)
        stompSm += (game.airDashStomp - stompSm) * min(1f, (if (game.airDashStomp > stompSm) 20f else 5f) * dtp)   // pisada no ar: entra rápido, sai devagar
        val vpT = if (!flyNow) 0f else if (game.airDashStomp > 0.05f && !game.dashActive) game.dashDirPitch else if (dashSm > 0.3f) Math.toDegrees(Math.atan2(p.vy.toDouble(), spdH.toDouble())).toFloat() else 0f
        vpSm += (vpT.coerceIn(-75f, 75f) - vpSm) * min(1f, 8f * dtp)   // transição suave pro voo rápido (e de volta)
        svySm += (p.vy - svySm) * min(1f, 7f * dtp)
        swimFastA += ((if (swm > 0.2f && ((game.sprint && wa > 0.1f) || abs(svySm) > 1.6f)) 1f else 0f) - swimFastA) * min(1f, 7f * dtp)   // subindo/afundando de verdade também usa as braçadas
        swimPhA += dtp * (2.2f + 1.8f * min(1f, wa * 2f)) * (if (game.sprint) 1.3f else 1f) * (1f + 0.7f * swimFastA * (1f - diveSm))   // nado rápido na superfície: braçadas mais rápidas
        val surfY = World.WATER_Y + 0.88f
        val diveTarget = ((surfY - (p.y + 1.55f)) / 0.45f).coerceIn(0f, 1f)
        diveSm += (diveTarget - diveSm) * min(1f, 6f * dtp)
        val pitchDeg = Math.toDegrees(game.pitch.toDouble()).toFloat()
        // superfície: quase em pé, inclinado pra frente; mergulhado: deitado, seguindo o olhar
        // carga do pulo, super pulo e cambalhota (suavizados)
        jchSm += (game.jumpCharge - jchSm) * min(1f, (if (game.jumpCharge > jchSm) 16f else 26f) * dtp)
        sjSm += ((if (game.superAir && airNow) 1f else 0f) - sjSm) * min(1f, 10f * dtp)
        if (SUPER_FLIP && game.superAir && airNow && game.superVy0 > 14.6f) {
            val v0 = game.superVy0
            val fp = ((0.8f * v0 - p.vy) / (1.6f * v0)).coerceIn(0f, 1f)   // centrada no topo do pulo
            flipAng = 360f * fp * fp * (3f - 2f * fp)
        } else if (flipAng > 0f) {   // pousou no meio da volta: termina rapidinho em vez de estalar
            flipAng += (360f - flipAng) * min(1f, 14f * dtp)
            if (flipAng > 358f) flipAng = 0f
        }
        val tiltTarget = swm * (32f + 36f * swimFastA * (1f - diveSm) + (((78f - pitchDeg * 0.35f).coerceIn(40f, 110f)) - 32f) * diveSm)   // acelerando na superfície: deita bastante, como nadador de crawl
        // direção vertical: afundando o corpo aponta pra baixo (cabeça primeiro), subindo aponta pra cima; a mesma braçada/pernada do nado
        val vw = (abs(svySm) / 2f).coerceIn(0f, 1f).let { it * it * (3f - 2f * it) }
        val thetaV = Math.toDegrees(Math.atan2(svySm.toDouble(), max(hypot(p.vx, p.vz) * 0.7f, 0.5f).toDouble())).toFloat()
        val tiltV = (90f - thetaV).coerceIn(8f, 150f) * swm
        tiltSm += ((tiltTarget + (tiltV - tiltTarget) * vw) - tiltSm) * min(1f, 8f * dtp)
        an.t = t; an.phase = phaseA; an.move = wa; an.run = runA; an.vy = vyHold; an.air = airNow; an.airA = airSm; an.fly = flySm; an.flySpd = flySp
        val impK = ((1f - game.flyImp) * 3f).coerceIn(0f, 1f)   // o impulso entra em ~0,2s em vez de estalar
        an.dash = max(dashSm, stompSm); an.dst = stompSm; an.vp = vpSm; an.imp = game.flyImp * impK * impK * (3f - 2f * impK); an.swimFast = swimFastA; an.swimPh = swimPhA; an.crouch = game.crouchA; an.sit = game.sitA; an.lie = game.lieA; an.ride = if (game.driving && !game.skating) 1f else 0f; an.bent = if (game.driving && game.car.kind == 1) 1f else 0f; an.landT = landSm; an.swim = swm; an.dive = diveSm; an.tilt = tiltSm
        an.jch = jchSm; an.sj = sjSm; an.flip = flipAng; an.launch = game.flyLaunch; an.dch = game.airDashCharge
        run {   // pose de skatista (pesos suaves pra não estalar entre as fases)
            val sv = game.skateV
            val on = game.skating && sv.bailT <= 0f
            skSm += ((if (on) 1f else 0f) - skSm) * min(1f, 10f * dtp)
            skManSm += ((if (on && sv.manual) 1f else 0f) - skManSm) * min(1f, 9f * dtp)
            skFlSm += ((if (on && sv.flipF > 0f && sv.flipF < 1f) 1f else 0f) - skFlSm) * min(1f, 12f * dtp)
            skGdSm += ((if (on && sv.grinding) 1f else 0f) - skGdSm) * min(1f, 9f * dtp)
            an.sk = skSm; an.skMan = skManSm; an.skFlip = skFlSm; an.skGrind = skGdSm
            an.skPush = if (on) sv.pushW else 0f; an.skPushPh = sv.pushPh; an.skFace = if (on) sv.faceForward else 0f
            an.skGrab = if (on) sv.grabA else 0f; an.skGrabK = sv.grab.toFloat()
            an.skPop = if (on) sv.popK else 0f; an.skLand = if (on) sv.landK else 0f; an.skBal = if (on) sv.bal else 0f
        }
        an.atk = game.swing; an.atkArm = swingDelta(id, game.swing); an.hasTool = id > 0
        // mesmos keyframes de golpe/carga da 1ª pessoa, aplicados no braço do boneco
        if (game.charging) { chargePose(tpP, id, game.charge, t); an.charge = min(1f, game.charge) }
        else { evalPose(tpP, kfFor(id), game.swing); an.charge = 0f }
        an.poseOn = true; an.pP = tpP[3]; an.pY = tpP[4]; an.pR = tpP[5]; an.pW = tpP[6]
        setBase(p.x, p.y, p.z, Math.toDegrees(game.bodyYaw.toDouble()).toFloat())
        if (game.skating) {   // skatista inclina com a prancha (eixo X do corpo = frente da prancha)
            Matrix.rotateM(base, 0, game.skateV.lean, 1f, 0f, 0f)
        }
        if (game.driving && game.car.kind == 1) {   // piloto acompanha a inclinação da moto e a empinada
            Matrix.rotateM(base, 0, -game.car.roll, 0f, 0f, 1f)
            Matrix.rotateM(base, 0, -game.car.wheelieDeg(), 1f, 0f, 0f)
        }
        if (swm > 0.01f) {   // nadando: boia na superfície (cabeça fora) ou desliza deitado quando mergulha
            Matrix.translateM(base, 0, 0f, sin(t * 2.2f) * 0.025f * (1f - diveSm) * swm, 0f)
            Matrix.translateM(base, 0, 0f, 0.12f * swimFastA * (1f - diveSm) * swm, 0f)   // deitado: sobe só um pouquinho (antes 0.3: ficava muito levantado)
            Matrix.translateM(base, 0, 0f, 0.9f, 0f)
            Matrix.rotateM(base, 0, tiltSm, 1f, 0f, 0f)
            Matrix.translateM(base, 0, 0f, -0.9f, 0f)
        }
        val tk = game.trickKind; val tA = game.trickA
        if (tk > 0 && tA > 0f && tA < 1f) {   // manobra: arco do pulo + giro nos 3 eixos em volta do centro (curva com preparação e acabamento)
            val e = game.trickEase(tA)
            Matrix.translateM(base, 0, 0f, game.trickHop(tA), 0f)
            Matrix.translateM(base, 0, 0f, 0.9f, 0f)
            if (game.trickYaw != 0f) Matrix.rotateM(base, 0, game.trickYaw * e, 0f, 1f, 0f)
            if (game.trickPitch != 0f) Matrix.rotateM(base, 0, game.trickPitch * e, 1f, 0f, 0f)
            if (game.trickRoll != 0f) Matrix.rotateM(base, 0, game.trickRoll * e, 0f, 0f, 1f)
            Matrix.translateM(base, 0, 0f, -0.9f, 0f)
            val r1 = (tA / 0.05f).coerceIn(0f, 1f); val r2 = ((tA - 0.95f) / 0.05f).coerceIn(0f, 1f)
            an.trk = r1 * r1 * (3f - 2f * r1) * (1f - r2 * r2 * (3f - 2f * r2)); an.trkP = tA
            an.trkK = tk.toFloat(); an.trkF = if (game.trickFast) 1f else 0f
        } else { an.trk = 0f; an.trkK = 0f; an.trkF = 0f; an.trkP = 0f }
        G.glEnable(G.GL_POLYGON_OFFSET_FILL)   // camadas do boneco: o que está por cima ganha na profundidade (acaba o z-fighting nas dobras)
        cm.mc.layerHook = { l -> G.glPolygonOffset(0f, -l.toFloat()) }
        if (minecraftSkin.isActive()) {
            minecraftSkin.draw(vp, base, an, handM)
            // O renderizador de skin usa outro shader e malha; restaura o estado do renderer principal.
            G.glUseProgram(prog)
            G.glActiveTexture(G.GL_TEXTURE0); G.glBindTexture(G.GL_TEXTURE_2D, atlasTex); G.glUniform1i(uTex, 0)
            bindMesh(cubeVb, cubeIb)
        } else cm.draw(charBox, base, an, handM)
        cm.mc.layerHook = null
        G.glPolygonOffset(0f, 0f); G.glDisable(G.GL_POLYGON_OFFSET_FILL)
        if (id > 0) {   // mesmo modelo 3D da 1ª pessoa e dos ícones: cabo pra cima, fio pra frente
            System.arraycopy(handM, 0, base2, 0, 16)
            if (an.toolA != 0f) Matrix.rotateM(base2, 0, an.toolA, 1f, 0f, 0f)   // nado e voo rápido: o item acompanha o corpo e não fura a cabeça
            if (Items.isBlock(id)) {
                Matrix.translateM(base2, 0, 0f, -0.16f, 0.04f)
                Matrix.scaleM(base2, 0, 0.8f, 0.8f, 0.8f)
                drawItem(base2, id, t)
            } else {
                Matrix.rotateM(base2, 0, 180f, 0f, 1f, 0f)
                Matrix.rotateM(base2, 0, TOOL_OUT, 0f, 0f, 1f)   // abre o cabo pra fora (longe da cabeça)
                Matrix.rotateM(base2, 0, TOOL_TILT, 1f, 0f, 0f)
                Matrix.scaleM(base2, 0, 0.7f, 0.7f, 0.7f)
                drawTool3D(base2, id, t, 0f)
            }
        }
    }

    /** rastro de vento do voo rápido: riscos finos ao longo do voo, hélice dupla saindo das mãos e anéis se abrindo */
    private val wu = FloatArray(3); private val wv = FloatArray(3)
    private fun windAxes(w: WindP) {
        val up = abs(w.dy) < 0.9f
        var ux = if (up) -w.dz else 0f; var uy = if (up) 0f else w.dz; var uz = if (up) w.dx else -w.dy
        val ul = max(0.001f, sqrt(ux * ux + uy * uy + uz * uz)); ux /= ul; uy /= ul; uz /= ul
        wu[0] = ux; wu[1] = uy; wu[2] = uz
        wv[0] = w.dy * uz - w.dz * uy; wv[1] = w.dz * ux - w.dx * uz; wv[2] = w.dx * uy - w.dy * ux
    }
    private fun yawOf(dx: Float, dz: Float) = Math.toDegrees(atan2(dx, dz).toDouble()).toFloat()
    private fun pitchOf(dy: Float) = -Math.toDegrees(asin(dy.coerceIn(-1f, 1f).toDouble())).toFloat()
    private fun drawWind() {
        if (game.wind.isEmpty()) return
        G.glDepthMask(false)
        for (w in game.wind) {
            if (!seen(w.x, w.y, w.z, 44f)) continue
            val k = w.age / w.life; val fade = 1f - k
            when (w.kind) {
                0 -> {   // risco fino, esticado na direção do voo
                    val grow = min(1f, 0.4f + w.age * 8f)
                    setBase(w.x, w.y, w.z, yawOf(w.dx, w.dz))
                    box(base, 0f, 0f, 0f, pitchOf(w.dy), 0f, 0.028f, 0.028f, w.len * grow, 0f, w.color, min(0.85f, fade * 1.7f) * min(1f, w.age * 14f) * w.amp, 1.1f)
                }
                1 -> {   // ponto da hélice: gira em volta do eixo do voo e vai abrindo
                    windAxes(w)
                    val ang = w.ph + w.age * 9f; val r = w.rad * min(1f, 0.12f + w.age * 2.4f)
                    val c = cos(ang) * r; val s = sin(ang) * r
                    setBase(w.x + wu[0] * c + wv[0] * s, w.y + wu[1] * c + wv[1] * s, w.z + wu[2] * c + wv[2] * s, yawOf(w.dx, w.dz))
                    val ps = 0.075f * (1f - 0.5f * k)
                    box(base, 0f, 0f, 0f, pitchOf(w.dy), 0f, ps, ps, ps * 3f, 0f, w.color, min(0.9f, fade * 1.8f) * min(1f, w.age * 20f) * w.amp, 1.1f)
                }
                else -> {   // anel de vento: segmentos finos ao redor do eixo, raio abrindo com desaceleração
                    windAxes(w)
                    val e = 1f - (1f - k) * (1f - k); val r = w.rad * (0.22f + 0.78f * e)
                    val n = (10 + w.rad * 5f).toInt().coerceAtMost(34)
                    val seg = 6.2832f * r / n * 0.85f; val th = 0.05f + 0.03f * min(1f, w.rad / 3f) * fade
                    val al = min(0.75f, fade * 1.3f) * min(1f, w.age * 16f) * w.amp
                    for (i in 0 until n) {
                        val a = 6.2832f * i / n; val ca = cos(a); val sa = sin(a)
                        val tx = -wu[0] * sa + wv[0] * ca; val ty = -wu[1] * sa + wv[1] * ca; val tz = -wu[2] * sa + wv[2] * ca
                        setBase(w.x + (wu[0] * ca + wv[0] * sa) * r, w.y + (wu[1] * ca + wv[1] * sa) * r, w.z + (wu[2] * ca + wv[2] * sa) * r, yawOf(tx, tz))
                        box(base, 0f, 0f, 0f, pitchOf(ty), 0f, th, th, seg, 0f, w.color, al, 1.1f)
                    }
                }
            }
        }
        G.glDepthMask(true)
    }

    /** elevadores 4x4: poço de vidro, cabine com espelho e portas, painéis com setas e número do andar (tudo desenhado aqui; o bloco é só a âncora) */
    private fun drawElevators() {
        if (game.elevShafts.isEmpty()) return
        val near = ArrayList<ElevShaft>()
        for (sh in game.elevShafts.values) { val dx = sh.ax + 2f - cvx; val dz = sh.az + 2f - cvz; if (dx * dx + dz * dz < 90f * 90f) near.add(sh) }
        if (near.isEmpty()) return
        for (sh in near) drawShaft(sh, false)
        reflectCabins()
        G.glDepthMask(false)
        for (sh in near) drawShaft(sh, true)
        G.glDepthMask(true)
    }


    // ---------- espelhos de verdade (reflexo planar com stencil + corte oblíquo do plano) ----------
    private var stencilOk = false
    private var mirSkip = -1
    private var curFr = 0.6f; private var curFg = 0.8f; private var curFb = 1f; private var curUnder = false
    private var curVx = 0f; private var curVy = 0f; private var curVz = 0f
    private val rmM = FloatArray(16); private val vR = FloatArray(16); private val pO = FloatArray(16)
    private val vpR = FloatArray(16); private val pvR = FloatArray(16); private val tmpI = FloatArray(16)
    private val mirN = FloatArray(3)

    /** caixa do espelho k (0 fundo, 1 esquerda, 2 direita) em coords locais do poço: x0,y0,z0,x1,y1,z1 */
    private fun mirBox(k: Int, cy: Float, o: FloatArray) {
        val y0 = cy + 1.0f; val y1 = cy + 2.8f
        when (k) { 0 -> { o[0] = 0.32f; o[2] = 0.28f; o[3] = 3.68f; o[5] = 0.34f }
                   1 -> { o[0] = 0.28f; o[2] = 0.34f; o[3] = 0.34f; o[5] = 3.6f }
                   else -> { o[0] = 3.66f; o[2] = 0.34f; o[3] = 3.72f; o[5] = 3.6f } }
        o[1] = y0; o[4] = y1
    }
    private val mb = FloatArray(6)
    private fun drawMirBox(sh: ElevShaft, k: Int) {
        mirBox(k, sh.carY, mb)
        setBase(sh.ax.toFloat(), 0f, sh.az.toFloat(), 0f)
        box(base, (mb[0] + mb[3]) / 2f, (mb[1] + mb[4]) / 2f, (mb[2] + mb[5]) / 2f, 0f, 0f, mb[3] - mb[0], mb[4] - mb[1], mb[5] - mb[2], 0f, 0x9FB4C2, 1f, 1f)
    }
    private fun drawSkyWith(m: FloatArray, ex: Float, ey: Float, ez: Float) {
        Matrix.invertM(tmpI, 0, m, 0)
        G.glUseProgram(skyProg)
        G.glUniformMatrix4fv(skyInv, 1, false, tmpI, 0); G.glUniform3f(skyCam, ex, ey, ez)
        G.glUniform3f(skyHor, curFr, curFg, curFb); G.glUniform1f(skyTime, game.time)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, 0)
        G.glEnableVertexAttribArray(skyP); G.glVertexAttribPointer(skyP, 2, G.GL_FLOAT, false, 0, quad)
        G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
        G.glDisableVertexAttribArray(skyP)
    }

    private fun reflectCabins() {
        if (!stencilOk) return
        var sref = 0
        for (sh in game.elevShafts.values) {
            val lx = curVx - sh.ax; val lz = curVz - sh.az; val cy = sh.carY
            if (lx < -1f || lx > 5f || lz < -1f || lz > 9f || curVy < cy - 0.5f || curVy > cy + 3.4f) continue
            val inside = lx > 0f && lx < 4f && lz > 0f && lz < 4f
            for (k in 0..2) {
                // plano do espelho (mundo): n aponta pra dentro da cabine
                mirBox(k, cy, mb)
                val nx = when (k) { 1 -> 1f; 2 -> -1f; else -> 0f }; val nz = if (k == 0) 1f else 0f
                val px = sh.ax + (when (k) { 1 -> mb[3]; 2 -> mb[0]; else -> 0f }); val pz = sh.az + (if (k == 0) mb[5] else 0f)
                val d = -(nx * px + nz * pz)
                if (nx * curVx + nz * curVz + d < 0.03f) continue          // câmera atrás do espelho
                if (!inside) {   // fora da cabine: só se algum canto do espelho cai na tela
                    var any = false
                    for (c in 0 until 4) {
                        val wx = sh.ax + (if (k == 0) (if (c and 1 == 0) mb[0] else mb[3]) else (if (k == 1) mb[3] else mb[0]))
                        val wz = sh.az + (if (k == 0) mb[5] else (if (c and 1 == 0) mb[2] else mb[5]))
                        val wy = if (c and 2 == 0) mb[1] else mb[4]
                        val cx = vp[0] * wx + vp[4] * wy + vp[8] * wz + vp[12]; val cyy = vp[1] * wx + vp[5] * wy + vp[9] * wz + vp[13]
                        val cw = vp[3] * wx + vp[7] * wy + vp[11] * wz + vp[15]
                        if (cw > 0.05f && abs(cx) <= cw * 1.1f && abs(cyy) <= cw * 1.1f) { any = true; break }
                    }
                    if (!any) continue
                }
                sref++
                // 1) marca o stencil só onde o espelho aparece (respeita oclusão)
                G.glEnable(G.GL_STENCIL_TEST); G.glStencilMask(0xFF)
                G.glStencilFunc(G.GL_ALWAYS, sref, 0xFF); G.glStencilOp(G.GL_KEEP, G.GL_KEEP, G.GL_REPLACE)
                G.glColorMask(false, false, false, false); G.glDepthMask(false); G.glDepthFunc(G.GL_LEQUAL)
                drawMirBox(sh, k)
                // 2) zera a profundidade dentro do espelho
                G.glStencilFunc(G.GL_EQUAL, sref, 0xFF); G.glStencilOp(G.GL_KEEP, G.GL_KEEP, G.GL_KEEP)
                G.glDepthFunc(G.GL_ALWAYS); G.glDepthMask(true); G.glDisable(G.GL_CULL_FACE)
                G.glUseProgram(skyProg); G.glBindBuffer(G.GL_ARRAY_BUFFER, 0)
                G.glEnableVertexAttribArray(skyP); G.glVertexAttribPointer(skyP, 2, G.GL_FLOAT, false, 0, quad)
                G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4); G.glDisableVertexAttribArray(skyP)
                G.glEnable(G.GL_CULL_FACE)
                // 3) câmera refletida
                for (c in 0 until 3) for (r in 0 until 3) rmM[c * 4 + r] = (if (r == c) 1f else 0f) - 2f * (if (r == 0) nx else if (r == 1) 0f else nz) * (if (c == 0) nx else if (c == 1) 0f else nz)
                rmM[3] = 0f; rmM[7] = 0f; rmM[11] = 0f; rmM[15] = 1f
                rmM[12] = -2f * d * nx; rmM[13] = 0f; rmM[14] = -2f * d * nz
                Matrix.multiplyMM(vR, 0, view, 0, rmM, 0)
                val ed = nx * curVx + nz * curVz + d
                val rex = curVx - 2f * ed * nx; val rey = curVy; val rez = curVz - 2f * ed * nz
                Matrix.multiplyMM(pvR, 0, proj, 0, vR, 0)
                // corte oblíquo: plano do espelho vira o near plane
                Matrix.invertM(tmpI, 0, vR, 0)
                val pl = floatArrayOf(nx, 0f, nz, d)
                val cc = FloatArray(4); for (i in 0..3) { var sum = 0f; for (j in 0..3) sum += tmpI[i * 4 + j] * pl[j]; cc[i] = sum }
                System.arraycopy(proj, 0, pO, 0, 16)
                val qx = ((if (cc[0] > 0f) 1f else -1f) + pO[8]) / pO[0]; val qy = ((if (cc[1] > 0f) 1f else -1f) + pO[9]) / pO[5]
                val qz = -1f; val qw = (1f + pO[10]) / pO[14]
                val sc = 2f / (cc[0] * qx + cc[1] * qy + cc[2] * qz + cc[3] * qw)
                pO[2] = cc[0] * sc; pO[6] = cc[1] * sc; pO[10] = cc[2] * sc + 1f; pO[14] = cc[3] * sc
                Matrix.multiplyMM(vpR, 0, pO, 0, vR, 0)
                // 4) desenha o mundo espelhado só dentro do stencil
                G.glColorMask(true, true, true, true); G.glDepthFunc(G.GL_LEQUAL); G.glFrontFace(G.GL_CW)
                G.glDisable(G.GL_DEPTH_TEST); G.glDisable(G.GL_CULL_FACE)
                drawSkyWith(pvR, rex, rey, rez)
                G.glEnable(G.GL_DEPTH_TEST); G.glEnable(G.GL_CULL_FACE)
                G.glUseProgram(prog)
                G.glActiveTexture(G.GL_TEXTURE0); G.glUniform1i(uTex, 0)
                G.glUniformMatrix4fv(uVP, 1, false, vpR, 0); G.glUniform3f(uCam, rex, rey, rez)
                updateFrustum(vpR)
                G.glDisable(G.GL_BLEND)
                G.glUniformMatrix4fv(uModel, 1, false, ident, 0); tint(0xFFFFFF, 1f); G.glUniform1f(uWind, 1f)
                for (i in 0 until World.CX * World.CZ) {
                    if (cnt[i * 2] == 0 || !chunkVis[i]) continue
                    bindMesh(vbo[i * 2], ibo[i * 2]); G.glDrawElements(G.GL_TRIANGLES, cnt[i * 2], G.GL_UNSIGNED_INT, 0)
                }
                G.glUniform1f(uWind, 0f); bindMesh(cubeVb, cubeIb)
                G.glEnable(G.GL_BLEND)
                mirSkip = k; drawShaft(sh, false); mirSkip = -1
                if (!game.thirdPerson && game.deadTimer <= 0f) drawPlayer(game.time)
                G.glUniform1f(uWind, 1f); G.glUniformMatrix4fv(uModel, 1, false, ident, 0); G.glDisable(G.GL_CULL_FACE); tint(0xFFFFFF, 1f)
                for (i in 0 until World.CX * World.CZ) {
                    if (cnt[i * 2 + 1] == 0 || !chunkVis[i]) continue
                    bindMesh(vbo[i * 2 + 1], ibo[i * 2 + 1]); G.glDrawElements(G.GL_TRIANGLES, cnt[i * 2 + 1], G.GL_UNSIGNED_INT, 0)
                }
                G.glUniform1f(uWind, 0f); bindMesh(cubeVb, cubeIb); G.glEnable(G.GL_CULL_FACE)
                // 5) restaura câmera real e a profundidade do próprio espelho
                G.glFrontFace(G.GL_CCW)
                G.glUniformMatrix4fv(uVP, 1, false, vp, 0); G.glUniform3f(uCam, curVx, curVy, curVz)
                G.glColorMask(false, false, false, false); G.glDepthFunc(G.GL_ALWAYS); G.glDepthMask(true)
                drawMirBox(sh, k)
                G.glColorMask(true, true, true, true); G.glDepthFunc(G.GL_LESS)
            }
        }
        if (sref > 0) {
            G.glDisable(G.GL_STENCIL_TEST); G.glDepthFunc(G.GL_LESS); G.glDepthMask(true)
            updateFrustum(vp)
            G.glUseProgram(prog); G.glUniformMatrix4fv(uVP, 1, false, vp, 0); G.glUniform3f(uCam, curVx, curVy, curVz)
            G.glUniformMatrix4fv(uModel, 1, false, ident, 0); bindMesh(cubeVb, cubeIb)
        }
    }


    // ---------- tela da TV ao vivo (o navegador vira textura colada na tela da TV do mundo) ----------
    private var tvProg = 0; private var tvTex = 0; private var tvTexOk = false
    private var tvAPos = 0; private var tvAUv = 0; private var tvUVP = 0; private var tvUT = 0; private var tvUB = 0
    private val tvQuad = ByteBuffer.allocateDirect(20 * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
    private val TVVS = """
        attribute vec3 aPos; attribute vec2 aUv; uniform mat4 uVP; varying vec2 vUv;
        void main() { vUv = aUv; gl_Position = uVP * vec4(aPos, 1.0); }"""
    private val TVFS = """
        precision mediump float; uniform sampler2D uT; uniform float uB; varying vec2 vUv;
        void main() { vec3 c = texture2D(uT, vUv).rgb; gl_FragColor = vec4(c * uB + vec3(0.03, 0.04, 0.06), 1.0); }"""
    private fun drawTvScreen() {
        val tl = game.tvLive ?: return
        if (!Furniture.isTv(world.get(tl.x, tl.y, tl.z))) return
        if (tvProg == 0) {
            tvProg = G.glCreateProgram()
            G.glAttachShader(tvProg, shader(G.GL_VERTEX_SHADER, TVVS)); G.glAttachShader(tvProg, shader(G.GL_FRAGMENT_SHADER, TVFS))
            G.glLinkProgram(tvProg)
            tvAPos = G.glGetAttribLocation(tvProg, "aPos"); tvAUv = G.glGetAttribLocation(tvProg, "aUv")
            tvUVP = G.glGetUniformLocation(tvProg, "uVP"); tvUT = G.glGetUniformLocation(tvProg, "uT"); tvUB = G.glGetUniformLocation(tvProg, "uB")
        }
        val g = game.tvGeom(tl.x, tl.y, tl.z)
        val dx = g[0] - cvx; val dy = g[1] - cvy; val dz = g[2] - cvz
        tl.seen = dx * dx + dy * dy + dz * dz < 40f * 40f && (dx * cdx + dy * cdy + dz * cdz) > -2f
        if (!tl.seen) return
        val ids = IntArray(1)
        if (tvTex == 0) { G.glGenTextures(1, ids, 0); tvTex = ids[0] }
        G.glActiveTexture(G.GL_TEXTURE0); G.glBindTexture(G.GL_TEXTURE_2D, tvTex)
        if (tl.ready) {
            val b = tl.bmp
            if (b != null && !b.isRecycled) {
                if (!tvTexOk) {
                    G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_LINEAR); G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_LINEAR)
                    G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE); G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
                    GLUtils.texImage2D(G.GL_TEXTURE_2D, 0, b, 0); tvTexOk = true
                } else GLUtils.texSubImage2D(G.GL_TEXTURE_2D, 0, 0, 0, b)
            }
            tl.ready = false
        }
        if (!tvTexOk) { G.glBindTexture(G.GL_TEXTURE_2D, atlasTex); return }
        val o = 0.004f * g[7] / 0.88f
        val hw = g[7] / 2f; val hh = g[8] / 2f
        val px = g[0] + g[3] * o; val pz = g[2] + g[4] * o
        val q = tvQuad; q.position(0)
        // TL, BL, TR, BR (u,v: bitmap com a linha 0 em cima)
        q.put(px - g[5] * hw); q.put(g[1] + hh); q.put(pz - g[6] * hw); q.put(0f); q.put(0f)
        q.put(px - g[5] * hw); q.put(g[1] - hh); q.put(pz - g[6] * hw); q.put(0f); q.put(1f)
        q.put(px + g[5] * hw); q.put(g[1] + hh); q.put(pz + g[6] * hw); q.put(1f); q.put(0f)
        q.put(px + g[5] * hw); q.put(g[1] - hh); q.put(pz + g[6] * hw); q.put(1f); q.put(1f)
        G.glUseProgram(tvProg)
        G.glUniformMatrix4fv(tvUVP, 1, false, vp, 0); G.glUniform1i(tvUT, 0); G.glUniform1f(tvUB, 1.05f)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, 0)
        q.position(0); G.glEnableVertexAttribArray(tvAPos); G.glVertexAttribPointer(tvAPos, 3, G.GL_FLOAT, false, 20, q)
        q.position(3); G.glEnableVertexAttribArray(tvAUv); G.glVertexAttribPointer(tvAUv, 2, G.GL_FLOAT, false, 20, q)
        G.glDisable(G.GL_CULL_FACE); G.glDisable(G.GL_BLEND)
        G.glDrawArrays(G.GL_TRIANGLE_STRIP, 0, 4)
        G.glDisableVertexAttribArray(tvAPos); G.glDisableVertexAttribArray(tvAUv)
        G.glEnable(G.GL_CULL_FACE); G.glEnable(G.GL_BLEND)
        G.glUseProgram(prog); G.glBindTexture(G.GL_TEXTURE_2D, atlasTex)
        G.glUniform1i(uTex, 0)
        bindMesh(cubeVb, cubeIb)
    }

    private val SEG7 = intArrayOf(63, 6, 91, 79, 102, 109, 125, 7, 127, 111)

    private fun drawShaft(s: ElevShaft, glass: Boolean) {
        setBase(s.ax.toFloat(), 0f, s.az.toFloat(), 0f)
        fun cb(x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float, col: Int, a: Float = 1f, mul: Float = 1f) =
            box(base, (x0 + x1) / 2f, (y0 + y1) / 2f, (z0 + z1) / 2f, 0f, 0f, x1 - x0, y1 - y0, z1 - z0, 0f, col, a, mul)
        /** retângulo num plano: alongX = plano de frente (u = x, profundidade em z); senão plano lateral (u = z, profundidade em x) */
        fun pb(alongX: Boolean, p0: Float, p1: Float, u0: Float, u1: Float, v0: Float, v1: Float, col: Int, a: Float = 1f, mul: Float = 1f) {
            if (alongX) cb(u0, v0, p0, u1, v1, p1, col, a, mul) else cb(p0, v0, u0, p1, v1, u1, col, a, mul)
        }
        val LIT = 0xFFB347; val AMB = 0xFFC24A
        fun digit(d: Int, u: Float, v: Float, w: Float, h: Float, t: Float, alongX: Boolean, p0: Float, p1: Float, col: Int = LIT) {
            val m = SEG7[d.coerceIn(0, 9)]; val mid = v + h / 2f
            if (m and 1 != 0) pb(alongX, p0, p1, u + t, u + w - t, v + h - t, v + h, col, 1f, 1.8f)
            if (m and 8 != 0) pb(alongX, p0, p1, u + t, u + w - t, v, v + t, col, 1f, 1.8f)
            if (m and 64 != 0) pb(alongX, p0, p1, u + t, u + w - t, mid - t / 2f, mid + t / 2f, col, 1f, 1.8f)
            if (m and 2 != 0) pb(alongX, p0, p1, u + w - t, u + w, mid, v + h - t / 2f, col, 1f, 1.8f)
            if (m and 4 != 0) pb(alongX, p0, p1, u + w - t, u + w, v + t / 2f, mid, col, 1f, 1.8f)
            if (m and 16 != 0) pb(alongX, p0, p1, u, u + t, v + t / 2f, mid, col, 1f, 1.8f)
            if (m and 32 != 0) pb(alongX, p0, p1, u, u + t, mid, v + h - t / 2f, col, 1f, 1.8f)
        }
        fun number(n: Int, cu: Float, v: Float, w: Float, h: Float, t: Float, alongX: Boolean, p0: Float, p1: Float, col: Int = LIT) {
            val gap = w * 0.35f
            if (n >= 10) { val tot = w * 2 + gap; digit(n / 10, cu - tot / 2f, v, w, h, t, alongX, p0, p1, col); digit(n % 10, cu - tot / 2f + w + gap, v, w, h, t, alongX, p0, p1, col) }
            else digit(n, cu - w / 2f, v, w, h, t, alongX, p0, p1, col)
        }
        /** seta em degraus (3 fileiras + haste): cima = ponta em cima, baixo = ponta embaixo */
        fun arrow(up: Boolean, cu: Float, cv: Float, sz: Float, alongX: Boolean, p0: Float, p1: Float, on: Boolean) {
            val col = if (on) AMB else 0x5A4E38; val mul = if (on) 1.7f else 1f
            val rh = sz * 0.25f; val base0 = cv - sz * 0.5f; val stem = rh * 0.9f
            for (r in 0..2) {
                val hw = sz * (0.12f + 0.2f * (if (up) 2 - r else r))
                val v0 = if (up) base0 + stem + r * rh else base0 + r * rh
                pb(alongX, p0, p1, cu - hw, cu + hw, v0, v0 + rh, col, 1f, mul)
            }
            val sv = if (up) base0 else base0 + 3 * rh
            pb(alongX, p0, p1, cu - sz * 0.07f, cu + sz * 0.07f, sv, sv + stem, col, 1f, mul)
        }
        val ys = s.ys; val n = ys.size
        val y0 = ys.first().toFloat(); val yt = ys.last().toFloat(); val top = yt + 3.4f
        val cy = s.carY; val cur = s.idxNear()
        val GRAPH = 0x2E333B; val STEEL = 0x8E97A3; val GOLD = 0xD4AF37; val WOOD = 0x5E4030; val GL = 0xBFE3F7
        if (glass) {
            // vidros do poço (fundo e laterais) e piso transparente de cada andar
            cb(0.22f, y0, 0.05f, 3.78f, top, 0.1f, GL, 0.24f)
            cb(0.05f, y0, 0.22f, 0.1f, top, 3.78f, GL, 0.24f)
            cb(3.9f, y0, 0.22f, 3.95f, top, 3.78f, GL, 0.24f)
            for (y in ys) {
                val yy = y.toFloat()
                cb(0f, yy, 0f, 4f, yy + 0.05f, 4f, 0xD6EEF9, 0.14f)
                cb(0f, yy, 0f, 4f, yy + 0.05f, 0.05f, 0xC6D2DC, 0.8f)
                cb(0f, yy, 0f, 0.05f, yy + 0.05f, 4f, 0xC6D2DC, 0.8f)
                cb(3.95f, yy, 0f, 4f, yy + 0.05f, 4f, 0xC6D2DC, 0.8f)
                cb(0f, yy, 3.95f, 4f, yy + 0.05f, 4f, 0xC6D2DC, 0.8f)
            }
            // véu de vidro sobre os espelhos (o reflexo vem por baixo)
            run { val g0 = cy + 1.0f; val g1 = cy + 2.8f
                cb(0.32f, g0, 0.342f, 3.68f, g1, 0.347f, 0xCFE6F2, 0.13f)
                cb(0.342f, g0, 0.34f, 0.347f, g1, 3.6f, 0xCFE6F2, 0.13f)
                cb(3.653f, g0, 0.34f, 3.658f, g1, 3.6f, 0xCFE6F2, 0.13f) }
            // portas de vidro da cabine
            val w = 0.02f + 1.7f * (1f - s.door)
            cb(0.28f, cy + 0.15f, 3.62f, 0.28f + w, cy + 2.9f, 3.68f, 0xC9E4F2, 0.42f)
            cb(3.72f - w, cy + 0.15f, 3.62f, 3.72f, cy + 2.9f, 3.68f, 0xC9E4F2, 0.42f)
            cb(0.28f, cy + 1.45f, 3.615f, 0.28f + w, cy + 1.55f, 3.685f, 0xFFFFFF, 0.5f)
            cb(3.72f - w, cy + 1.45f, 3.615f, 3.72f, cy + 1.55f, 3.685f, 0xFFFFFF, 0.5f)
            cb(0.28f + w - 0.04f, cy + 0.15f, 3.6f, 0.28f + w, cy + 2.9f, 3.7f, 0xB9C2CC, 0.95f)
            cb(3.72f - w, cy + 0.15f, 3.6f, 3.72f - w + 0.04f, cy + 2.9f, 3.7f, 0xB9C2CC, 0.95f)
            return
        }
        // colunas grafite finas com frisos dourados em cada andar
        for (cx in floatArrayOf(0f, 3.78f)) for (cz in floatArrayOf(0f, 3.78f)) {
            cb(cx, y0, cz, cx + 0.22f, top, cz + 0.22f, GRAPH)
            for (y in ys) cb(cx - 0.015f, y + 0.05f, cz - 0.015f, cx + 0.235f, y + 0.11f, cz + 0.235f, GOLD)
        }
        for (i in 0 until n) {
            val y = ys[i].toFloat()
            // verga da porta + friso dourado + visor com o andar da cabine
            cb(0.22f, y + 2.78f, 3.9f, 3.78f, y + 3f, 4.02f, GRAPH)
            cb(0.22f, y + 2.75f, 3.9f, 3.78f, y + 2.78f, 4.03f, GOLD)
            // painel do pilar ESQUERDO (espelha o de chamada): em cima o andar DESTE patamar (azul), embaixo o andar onde o elevador está (âmbar),
            // e no pé uma seta quando ele anda (sobe/desce) ou uma lâmpada verde quando está parado neste andar. Fica abaixo de y+2: a sacada de cima não cobre.
            cb(0.02f, y + 0.66f, 4.0f, 0.26f, y + 1.98f, 4.05f, GRAPH)
            cb(0.04f, y + 0.68f, 4.05f, 0.24f, y + 1.96f, 4.055f, 0x1A1E23)
            for (k in 0..1) {
                val lo = if (k == 0) 1.46f else 0.92f; val hi = lo + 0.47f
                cb(0.05f, y + lo, 4.055f, 0.23f, y + hi, 4.06f, GOLD)
                cb(0.06f, y + lo + 0.01f, 4.06f, 0.22f, y + hi - 0.01f, 4.065f, 0x0B0F12)
                val num = if (k == 0) i + 1 else cur + 1
                val two = num >= 10
                number(num, 0.14f, y + lo + 0.105f, if (two) 0.056f else 0.08f, 0.26f, if (two) 0.015f else 0.02f, true, 4.065f, 4.075f, if (k == 0) 0x6FD3FF else AMB)
            }
            cb(0.05f, y + 0.70f, 4.055f, 0.23f, y + 0.88f, 4.06f, GOLD)
            cb(0.06f, y + 0.71f, 4.06f, 0.22f, y + 0.87f, 4.065f, 0x0B0F12)
            if (s.moving) arrow(s.dir > 0, 0.14f, y + 0.79f, 0.13f, true, 4.065f, 4.075f, true)
            else { val here = cur == i; cb(0.09f, y + 0.75f, 4.065f, 0.19f, y + 0.83f, 4.075f, if (here) 0x57E389 else 0x2B3A30, 1f, if (here) 1.8f else 1f) }
            // painel de chamada no pilar direito: só subir e descer
            cb(3.74f, y + 0.98f, 4.0f, 3.98f, y + 1.62f, 4.05f, GRAPH)
            cb(3.76f, y + 1.0f, 4.05f, 3.96f, y + 1.6f, 4.055f, 0x1A1E23)
            cb(3.78f, y + 1.3f, 4.055f, 3.94f, y + 1.55f, 4.065f, 0x39414B)
            cb(3.78f, y + 1.04f, 4.055f, 3.94f, y + 1.29f, 4.065f, 0x39414B)
            arrow(true, 3.86f, 1.425f + y, 0.2f, true, 4.065f, 4.08f, i < n - 1)
            arrow(false, 3.86f, 1.165f + y, 0.2f, true, 4.065f, 4.08f, i > 0)
        }
        for (i in 0 until n - 1) if (ys[i + 1] > ys[i] + 3) cb(0.22f, ys[i] + 3f, 3.9f, 3.78f, ys[i + 1].toFloat(), 4.0f, GRAPH)
        cb(0.22f, yt + 3f, 3.9f, 3.78f, top, 4.0f, GRAPH)
        cb(0f, yt + 3.2f, 0f, 4f, top, 4f, GRAPH)
        cb(-0.02f, yt + 3.18f, -0.02f, 4.02f, yt + 3.23f, 4.02f, GOLD)
        // ---------- cabine ----------
        // piso escuro polido com aro dourado
        cb(0.22f, cy + 0.05f, 0.22f, 3.78f, cy + 0.075f, 3.78f, 0x1F2228)
        cb(0.8f, cy + 0.075f, 0.8f, 3.2f, cy + 0.082f, 3.2f, GOLD)
        cb(0.86f, cy + 0.082f, 0.86f, 3.14f, cy + 0.09f, 3.14f, 0x34383F)
        // paredes: lambri de madeira embaixo, aço escovado em cima, friso dourado
        cb(0.22f, cy + 0.075f, 0.22f, 3.78f, cy + 0.9f, 0.28f, WOOD)
        cb(0.22f, cy + 0.9f, 0.22f, 3.78f, cy + 3f, 0.28f, STEEL)
        cb(0.22f, cy + 0.075f, 0.28f, 0.28f, cy + 0.9f, 3.78f, WOOD)
        cb(0.22f, cy + 0.9f, 0.28f, 0.28f, cy + 3f, 3.78f, STEEL)
        cb(3.72f, cy + 0.075f, 0.28f, 3.78f, cy + 0.9f, 3.78f, WOOD)
        cb(3.72f, cy + 0.9f, 0.28f, 3.78f, cy + 3f, 3.78f, STEEL)
        cb(0.28f, cy + 0.9f, 0.28f, 3.72f, cy + 0.95f, 0.32f, GOLD)
        cb(0.28f, cy + 0.9f, 0.32f, 0.32f, cy + 0.95f, 3.6f, GOLD)
        cb(3.68f, cy + 0.9f, 0.32f, 3.72f, cy + 0.95f, 3.6f, GOLD)
        // espelhos conectados (fundo + 2 lados, mesma altura): o reflexo de verdade é desenhado em reflectCabin() (stencil)
        val MY0 = cy + 1.0f; val MY1 = cy + 2.8f
        cb(0.32f, MY1, 0.28f, 3.68f, MY1 + 0.05f, 0.34f, GOLD); cb(0.28f, MY1, 0.28f, 0.34f, MY1 + 0.05f, 3.6f, GOLD); cb(3.66f, MY1, 0.28f, 3.72f, MY1 + 0.05f, 3.6f, GOLD)
        cb(0.28f, MY0 - 0.05f, 0.28f, 3.72f, MY0, 0.34f, GOLD)
        for (px in floatArrayOf(0.28f, 3.66f)) cb(px, MY0 - 0.05f, 0.28f, px + 0.06f, MY1 + 0.05f, 0.34f, GOLD)
        cb(0.28f, MY0 - 0.05f, 0.34f, 0.34f, MY0, 3.6f, GOLD); cb(3.66f, MY0 - 0.05f, 0.34f, 3.72f, MY0, 3.6f, GOLD)
        if (mirSkip != 0) cb(0.32f, MY0, 0.28f, 3.68f, MY1, 0.34f, 0x9FB4C2)
        if (mirSkip != 1) cb(0.28f, MY0, 0.34f, 0.34f, MY1, 3.6f, 0x9FB4C2)
        if (mirSkip != 2) cb(3.66f, MY0, 0.34f, 3.72f, MY1, 3.6f, 0x9FB4C2)
        // teto com luz embutida
        cb(0.22f, cy + 3f, 0.22f, 3.78f, cy + 3.08f, 3.78f, GRAPH)
        cb(0.64f, cy + 2.975f, 0.64f, 3.36f, cy + 2.99f, 3.36f, GOLD)
        cb(0.7f, cy + 2.99f, 0.7f, 3.3f, cy + 3f, 3.3f, 0xFFF4D6, 1f, 1.9f)
        // moldura e trilho da porta
        cb(0.28f, cy + 2.9f, 3.58f, 3.72f, cy + 2.98f, 3.72f, GRAPH)
        cb(0.28f, cy + 0.075f, 3.58f, 3.72f, cy + 0.15f, 3.72f, GRAPH)
        // painel da cabine (parede direita): visor com o andar + seta pra cima + seta pra baixo
        cb(3.64f, cy + 1.0f, 2.7f, 3.72f, cy + 1.95f, 3.4f, GRAPH)
        cb(3.62f, cy + 1.03f, 2.73f, 3.64f, cy + 1.92f, 3.37f, 0x1A1E23)
        cb(3.61f, cy + 1.62f, 2.8f, 3.63f, cy + 1.86f, 3.3f, 0x0B0F12)
        number(cur + 1, 3.05f, cy + 1.66f, 0.1f, 0.16f, 0.03f, false, 3.595f, 3.61f)
        cb(3.61f, cy + 1.3f, 2.9f, 3.63f, cy + 1.54f, 3.2f, 0x39414B)
        cb(3.61f, cy + 1.05f, 2.9f, 3.63f, cy + 1.29f, 3.2f, 0x39414B)
        val goingUp = s.moving && s.dir > 0; val goingDn = s.moving && s.dir < 0
        arrow(true, 3.05f, cy + 1.42f, 0.22f, false, 3.595f, 3.61f, goingUp || (!s.moving && cur < n - 1))
        arrow(false, 3.05f, cy + 1.17f, 0.22f, false, 3.595f, 3.61f, goingDn || (!s.moving && cur > 0))
    }

    // ---------- veículos: carro esportivo vermelho (conversível, o boneco aparece sentado) e moto ----------
    private fun drawVeh(c: CarState) {
        if (!c.placed) return
        if (!seen(c.x, c.y + 0.6f, c.z, 70f)) return
        setBase(c.x, c.y + c.yOff, c.z, Math.toDegrees(c.h.toDouble()).toFloat())
        Matrix.rotateM(base, 0, c.pitchA, 1f, 0f, 0f)
        Matrix.rotateM(base, 0, -c.roll, 0f, 0f, 1f)
        if (c.kind == 1) drawBike(c) else drawCar(c)
    }

    private fun drawCar(c: CarState) {
        val red = 0xD4141C; val redD = 0xA80F16; val blk = 0x15151A; val glass = 0x2A4157; val wht = 0xF4F4F4; val leather = 0x2A2A30
        fun b(px: Float, py: Float, pz: Float, sx: Float, sy: Float, sz: Float, col: Int, a: Float = 1f, mul: Float = 1f, rx: Float = 0f) =
            box(base, px, py, pz, rx, 0f, sx, sy, sz, 0f, col, a, mul)
        // rodas (eixo X local; a esquerda do carro é +X): 3 quadrados girados formam um pneu redondinho
        val steerDeg = -c.steer * 26f
        for (i in 0 until 4) {
            val left = i % 2 == 0; val front = i < 2
            val wx = if (left) 0.86f else -0.86f
            val wz = if (front) 1.32f else -1.30f
            val ry = if (front) steerDeg else 0f
            for (k in 0 until 3) box(base, wx, 0.33f, wz, c.wheel + k * 30f, ry, 0.3f, 0.5f, 0.5f, 0f, 0x141414)
            box(base, wx, 0.33f, wz, c.wheel, ry, 0.34f, 0.3f, 0.07f, 0f, 0xD9DDE2, 1f, 1.1f)
            box(base, wx, 0.33f, wz, c.wheel, ry, 0.34f, 0.07f, 0.3f, 0f, 0xD9DDE2, 1f, 1.1f)
            box(base, wx, 0.33f, wz, c.wheel + 45f, ry, 0.34f, 0.2f, 0.05f, 0f, 0xB4B9C0)
            box(base, wx, 0.33f, wz, c.wheel + 45f, ry, 0.34f, 0.05f, 0.2f, 0f, 0xB4B9C0)
            b(if (left) 0.96f else -0.96f, 0.76f, wz, 0.34f, 0.09f, 0.86f, red)   // paralama
        }
        // fundo e carroceria CONVERSÍVEL: frente (capô) e traseira inteiras, cabine aberta no meio pro boneco aparecer
        b(0f, 0.27f, 0f, 1.6f, 0.16f, 3.9f, blk)
        b(0f, 0.5f, 1.26f, 1.7f, 0.42f, 1.62f, red)            // capô
        b(0f, 0.5f, -1.55f, 1.7f, 0.42f, 1.04f, red)           // traseira
        b(0f, 0.37f, -0.28f, 1.6f, 0.1f, 1.5f, 0x1D1D22)        // assoalho da cabine
        b(0.8f, 0.5f, -0.28f, 0.1f, 0.42f, 1.5f, red); b(-0.8f, 0.5f, -0.28f, 0.1f, 0.42f, 1.5f, red)   // portas
        b(0.75f, 0.66f, -0.28f, 0.04f, 0.1f, 1.46f, leather); b(-0.75f, 0.66f, -0.28f, 0.04f, 0.1f, 1.46f, leather) // forro das portas
        b(0f, 0.43f, 2.06f, 1.76f, 0.26f, 0.2f, redD)
        b(0f, 0.43f, -2.06f, 1.76f, 0.26f, 0.2f, redD)
        b(0f, 0.26f, 2.12f, 1.84f, 0.06f, 0.42f, blk)
        b(0f, 0.4f, 2.17f, 0.9f, 0.12f, 0.03f, 0x0C0C0F)
        b(0.9f, 0.33f, 0f, 0.05f, 0.09f, 2.4f, blk); b(-0.9f, 0.33f, 0f, 0.05f, 0.09f, 2.4f, blk)
        b(0f, 0.8f, -1.5f, 1.5f, 0.1f, 1.0f, red)               // tampa do porta-malas
        b(0f, 0.75f, 1.25f, 1.3f, 0.07f, 1.5f, red, 1f, 1.08f)   // capô com leve volume
        // painel, volante e bancos (o motorista senta no banco da esquerda)
        b(0f, 0.78f, 0.4f, 1.6f, 0.22f, 0.22f, leather)
        b(0.32f, 0.9f, 0.18f, 0.34f, 0.04f, 0.34f, 0x101010, 1f, 1f, -55f)
        for (sx in floatArrayOf(0.32f, -0.32f)) { b(sx, 0.47f, -0.5f, 0.5f, 0.12f, 0.5f, leather); b(sx, 0.78f, -0.8f, 0.5f, 0.55f, 0.12f, leather); b(sx, 1.02f, -0.86f, 0.3f, 0.2f, 0.1f, red) }
        // para-brisa baixo e arco de segurança atrás dos bancos
        b(0f, 0.95f, 0.5f, 1.5f, 0.4f, 0.04f, glass, 0.55f, 1f, -38f)
        b(0.78f, 0.9f, 0.52f, 0.06f, 0.5f, 0.06f, redD, 1f, 1f, -38f); b(-0.78f, 0.9f, 0.52f, 0.06f, 0.5f, 0.06f, redD, 1f, 1f, -38f)
        // faixas brancas de corrida
        b(0.13f, 0.718f, 1.3f, 0.15f, 0.014f, 1.5f, wht); b(-0.13f, 0.718f, 1.3f, 0.15f, 0.014f, 1.5f, wht)
        b(0.13f, 0.857f, -1.5f, 0.15f, 0.014f, 0.98f, wht); b(-0.13f, 0.857f, -1.5f, 0.15f, 0.014f, 0.98f, wht)
        // aerofólio
        b(0.55f, 0.92f, -1.95f, 0.08f, 0.3f, 0.1f, blk); b(-0.55f, 0.92f, -1.95f, 0.08f, 0.3f, 0.1f, blk)
        b(0f, 1.08f, -2.0f, 1.95f, 0.06f, 0.52f, redD)
        b(0.97f, 1.04f, -2.0f, 0.05f, 0.2f, 0.52f, redD); b(-0.97f, 1.04f, -2.0f, 0.05f, 0.2f, 0.52f, redD)
        // retrovisores e escapamentos
        b(0.93f, 0.9f, 0.4f, 0.16f, 0.1f, 0.2f, red); b(-0.93f, 0.9f, 0.4f, 0.16f, 0.1f, 0.2f, red)
        b(0.45f, 0.3f, -2.18f, 0.14f, 0.14f, 0.1f, 0x9AA0A6); b(-0.45f, 0.3f, -2.18f, 0.14f, 0.14f, 0.1f, 0x9AA0A6)
        // faróis, lanternas (acendem forte ao frear) e luz de ré
        b(0.6f, 0.56f, 2.17f, 0.42f, 0.12f, 0.04f, 0xFFF6C8, 1f, 1.5f); b(-0.6f, 0.56f, 2.17f, 0.42f, 0.12f, 0.04f, 0xFFF6C8, 1f, 1.5f)
        val tl = if (c.braking) 0xFF4A4A else 0xB01212
        val tm = if (c.braking) 1.9f else 1.1f
        b(0.6f, 0.6f, -2.17f, 0.5f, 0.1f, 0.04f, tl, 1f, tm); b(-0.6f, 0.6f, -2.17f, 0.5f, 0.1f, 0.04f, tl, 1f, tm)
        if (c.reversing) { b(0.22f, 0.5f, -2.18f, 0.14f, 0.07f, 0.04f, 0xFFFFFF, 1f, 1.6f); b(-0.22f, 0.5f, -2.18f, 0.14f, 0.07f, 0.04f, 0xFFFFFF, 1f, 1.6f) }
        // ----- TETO (hardtop): janelas sobem, colunas e vidros crescem e a tampa sai do porta-malas e desliza pra cima da cabine -----
        val rk = c.roof
        if (rk > 0.002f) {
            val hf = sm01((rk - 0.15f) / 0.85f)                       // altura das colunas/vidros
            val rp = sm01(rk / 0.85f)                                  // percurso da tampa do teto
            val glassA = 0.38f
            // coluna A + vidro superior do para-brisa (continuam o para-brisa baixo, mais em pé pra caber a cabeça); crescem a partir da base
            if (hf > 0.01f) {
                val la = 0.999f * hf; val ay = 1.108f + 0.441f * hf; val az = 0.377f - 0.2345f * hf
                b(0f, ay, az, 1.5f, la, 0.04f, glass, glassA, 1f, -28f)
                b(0.79f, ay, az, 0.07f, la, 0.08f, red, 1f, 1f, -28f); b(-0.79f, ay, az, 0.07f, la, 0.08f, red, 1f, 1f, -28f)
                // vidro traseiro (inclinado) e colunas C, crescem a partir do deck do porta-malas
                val lr = 1.164f * hf; val ry = 0.85f + 0.555f * hf; val rz = -1.5f + 0.175f * hf
                b(0f, ry, rz, 1.5f, lr, 0.04f, glass, glassA, 1f, 17.5f)
                b(0.8f, ry, rz, 0.1f, lr, 0.16f, red, 1f, 1f, 17.5f); b(-0.8f, ry, rz, 0.1f, lr, 0.16f, red, 1f, 1f, 17.5f)
                // vidros laterais: sobem de dentro da porta (3 fatias seguindo a coluna A)
                val wTop = 0.8f + 1.16f * hf
                val sl = arrayOf(floatArrayOf(0.8f, 1.1f, 0.45f), floatArrayOf(1.1f, 1.5f, 0.18f), floatArrayOf(1.5f, 1.96f, -0.03f))
                for (s in sl) {
                    val top = min(s[1], wTop)
                    if (top > s[0] + 0.01f) for (sx in floatArrayOf(0.81f, -0.81f)) b(sx, (s[0] + top) / 2f, (s[2] - 1.15f) / 2f, 0.03f, top - s[0], s[2] + 1.15f, glass, glassA, 1f)
                }
                if (hf > 0.99f) for (sx in floatArrayOf(0.8f, -0.8f)) b(sx, 1.0f, -0.35f, 0.05f, 0.04f, 1.6f, redD)   // friso inferior da janela
            }
            // tampa do teto: 4 vigas + teto solar de vidro; sai do porta-malas girando e pousa nas colunas
            val rm = FloatArray(16); System.arraycopy(base, 0, rm, 0, 16)
            val ty = 0.62f + 1.37f * rp + 0.25f * sin(rp * 3.14159f); val tz = -1.55f + 0.935f * rp
            Matrix.translateM(rm, 0, 0f, ty, tz); Matrix.rotateM(rm, 0, -14f * sin(rp * 3.14159f), 1f, 0f, 0f)
            fun rb(px: Float, py: Float, pz: Float, sx: Float, sy: Float, sz: Float, col: Int, a: Float = 1f, mul: Float = 1f) = box(rm, px, py, pz, 0f, 0f, sx, sy, sz, 0f, col, a, mul)
            rb(0f, 0f, 0.4f, 1.62f, 0.06f, 0.27f, red); rb(0f, 0f, -0.43f, 1.62f, 0.06f, 0.21f, red)
            rb(0.68f, 0f, -0.03f, 0.26f, 0.06f, 0.6f, red); rb(-0.68f, 0f, -0.03f, 0.26f, 0.06f, 0.6f, red)
            rb(0f, 0f, -0.03f, 1.1f, 0.03f, 0.6f, glass, 0.4f)
            rb(0f, 0.034f, 0.4f, 0.5f, 0.012f, 0.03f, wht, 1f, 1.1f)   // frisinho branco de corrida
        }
    }

    private fun drawBike(c: CarState) {
        val red = 0xD4141C; val redD = 0xA80F16; val blk = 0x15151A; val chrome = 0xB4B9C0
        // empinada: a moto inteira gira em volta do eixo da roda de trás
        val wd = c.wheelieDeg()
        if (wd > 0.01f) { Matrix.translateM(base, 0, 0f, 0.38f, -0.78f); Matrix.rotateM(base, 0, -wd, 1f, 0f, 0f); Matrix.translateM(base, 0, 0f, -0.38f, 0.78f) }
        fun b(px: Float, py: Float, pz: Float, sx: Float, sy: Float, sz: Float, col: Int, a: Float = 1f, mul: Float = 1f, rx: Float = 0f, ry: Float = 0f) =
            box(base, px, py, pz, rx, ry, sx, sy, sz, 0f, col, a, mul)
        val steerDeg = -c.steer * 24f
        // rodas
        for (front in booleanArrayOf(true, false)) {
            val wz = if (front) 0.78f else -0.78f
            val ry = if (front) steerDeg else 0f
            for (k in 0 until 3) box(base, 0f, 0.38f, wz, c.wheel + k * 30f, ry, 0.17f, 0.56f, 0.56f, 0f, 0x141414)
            box(base, 0f, 0.38f, wz, c.wheel, ry, 0.19f, 0.34f, 0.07f, 0f, 0xD9DDE2, 1f, 1.1f)
            box(base, 0f, 0.38f, wz, c.wheel, ry, 0.19f, 0.07f, 0.34f, 0f, 0xD9DDE2, 1f, 1.1f)
            box(base, 0f, 0.38f, wz, c.wheel + 45f, ry, 0.19f, 0.22f, 0.05f, 0f, chrome)
            box(base, 0f, 0.38f, wz, c.wheel + 45f, ry, 0.19f, 0.05f, 0.22f, 0f, chrome)
        }
        // garfo e guidão (giram com o volante)
        b(0.12f, 0.78f, 0.7f, 0.05f, 0.8f, 0.06f, chrome, 1f, 1f, -16f, steerDeg); b(-0.12f, 0.78f, 0.7f, 0.05f, 0.8f, 0.06f, chrome, 1f, 1f, -16f, steerDeg)
        b(0f, 1.18f, 0.55f, 0.72f, 0.05f, 0.05f, blk, 1f, 1f, 0f, steerDeg)
        b(0.38f, 1.18f, 0.55f, 0.1f, 0.07f, 0.07f, 0x222222, 1f, 1f, 0f, steerDeg); b(-0.38f, 1.18f, 0.55f, 0.1f, 0.07f, 0.07f, 0x222222, 1f, 1f, 0f, steerDeg)
        // carenagem da frente, farol e para-brisa
        b(0f, 1.0f, 0.68f, 0.36f, 0.36f, 0.22f, red, 1f, 1f, 0f, steerDeg)
        b(0f, 1.0f, 0.8f, 0.24f, 0.14f, 0.04f, 0xFFF6C8, 1f, 1.5f, 0f, steerDeg)
        b(0f, 1.3f, 0.6f, 0.28f, 0.28f, 0.04f, 0x2A4157, 0.55f, 1f, -30f, steerDeg)
        b(0f, 0.62f, 0.9f, 0.3f, 0.05f, 0.34f, redD, 1f, 1f, 0f, steerDeg)   // paralama dianteiro
        // corpo: motor, tanque, banco, rabeta e escapamento
        b(0f, 0.5f, 0.12f, 0.32f, 0.4f, 0.6f, 0x2B2B30)
        b(0f, 0.9f, 0.3f, 0.36f, 0.28f, 0.6f, red, 1f, 1.05f)
        b(0f, 1.045f, 0.3f, 0.12f, 0.014f, 0.58f, 0xF4F4F4)               // faixa branca no tanque
        b(0f, 0.84f, -0.3f, 0.32f, 0.1f, 0.7f, 0x1A1A1E)
        b(0f, 0.98f, -0.85f, 0.34f, 0.24f, 0.5f, red, 1f, 1f, 8f)
        b(0f, 0.68f, -0.9f, 0.3f, 0.05f, 0.4f, redD)
        b(0.14f, 0.3f, -0.45f, 0.1f, 0.13f, 1.0f, chrome); b(0.14f, 0.3f, -0.95f, 0.14f, 0.16f, 0.12f, 0x555A60)
        b(0.12f, 0.45f, -0.5f, 0.1f, 0.1f, 0.85f, 0x2B2B30); b(-0.12f, 0.45f, -0.5f, 0.1f, 0.1f, 0.85f, 0x2B2B30)   // balança traseira
        b(0.2f, 0.36f, -0.1f, 0.18f, 0.04f, 0.08f, chrome); b(-0.2f, 0.36f, -0.1f, 0.18f, 0.04f, 0.08f, chrome)   // pedaleiras
        // lanterna (acende forte ao frear) e luz de ré
        b(0f, 1.0f, -1.12f, 0.24f, 0.08f, 0.04f, if (c.braking) 0xFF4A4A else 0xB01212, 1f, if (c.braking) 1.9f else 1.1f)
        if (c.reversing) b(0f, 0.9f, -1.12f, 0.1f, 0.06f, 0.04f, 0xFFFFFF, 1f, 1.6f)
    }

    // ---------- skate v2: prancha detalhada (concavidade, bicos, lixa, gráfico embaixo, trucks, rodas redondas); gira/flipa por baixo do boneco ----------
    private fun drawSkate(s: SkateState) {
        if (!s.placed) return
        if (!seen(s.x, s.y + 0.4f, s.z, 70f)) return
        setBase(s.x, s.y + s.yOff, s.z, Math.toDegrees(s.h.toDouble()).toFloat() + s.spin)
        if (s.tumble != 0f) {
            Matrix.rotateM(base, 0, s.tumble, 0f, 0f, 1f)
            Matrix.rotateM(base, 0, s.tumble * 0.6f, 1f, 0f, 0f)
        }
        Matrix.rotateM(base, 0, s.lean, 0f, 0f, 1f)
        Matrix.translateM(base, 0, 0f, 0.185f + 0.3f * s.grabA - 0.5f * s.lift, 0f)
        val pitch = -s.pitchA - s.grabA * 22f + s.gPitch      // + = bico pra baixo
        if (pitch != 0f) Matrix.rotateM(base, 0, pitch, 1f, 0f, 0f)
        if (s.flipYaw + s.gYaw != 0f) Matrix.rotateM(base, 0, s.flipYaw + s.gYaw, 0f, 1f, 0f)
        if (s.flipRoll != 0f) Matrix.rotateM(base, 0, s.flipRoll, 0f, 0f, 1f)
        val wood = 0xD9A55B; val woodD = 0xB57F3A; val woodL = 0xEBC987; val grip = 0x1B1B20; val gripL = 0x2C2C35
        val metal = 0xB9BEC6; val metalD = 0x7C828C; val wheelC = 0xFFB03B; val hubC = 0xFFF2CF; val bush = 0xE8505B
        val gfxA = 0x23C9B8; val gfxB = 0xFFFFFF; val gfxC = 0xFF4D6D
        fun b(px: Float, py: Float, pz: Float, sx: Float, sy: Float, sz: Float, col: Int, rx: Float = 0f, mul: Float = 1f, a: Float = 1f) =
            box(base, px, py, pz, rx, 0f, sx, sy, sz, 0f, col, a, mul)
        // ---- deck: miolo + bordas levantadas (côncavo) ----
        b(0f, 0f, 0f, 0.29f, 0.034f, 0.5f, wood)
        b(0.138f, 0.01f, 0f, 0.028f, 0.02f, 0.5f, woodL); b(-0.138f, 0.01f, 0f, 0.028f, 0.02f, 0.5f, woodL)
        // lâminas de madeira aparecendo na lateral (camadas)
        b(0.146f, -0.006f, 0f, 0.006f, 0.008f, 0.5f, woodD); b(-0.146f, -0.006f, 0f, 0.006f, 0.008f, 0.5f, woodD)
        // ---- bicos (2 segmentos cada: sobe suave e depois mais forte); lixa por cima e gráfico por baixo, deslocados na normal do trecho ----
        fun seg(sg: Float, cy: Float, cz: Float, w: Float, len: Float, ang: Float) {
            val r = Math.toRadians(ang.toDouble()).toFloat(); val ca = cos(r); val sa = sin(r)
            b(0f, cy, cz * sg, w, 0.032f, len, wood, ang)
            b(0f, cy + 0.0195f * ca, cz * sg + 0.0195f * sa, w - 0.012f, 0.006f, len, grip, ang)
            b(0f, cy - 0.0195f * ca, cz * sg - 0.0195f * sa, w - 0.012f, 0.006f, len, gfxA, ang)
        }
        for (sg in floatArrayOf(1f, -1f)) {
            seg(sg, 0.032f, 0.33f, 0.29f, 0.17f, -24f * sg)
            seg(sg, 0.093f, 0.44f, 0.27f, 0.09f, -42f * sg)
        }
        // ---- lixa no miolo + linhas de textura ----
        b(0f, 0.02f, 0f, 0.272f, 0.006f, 0.5f, grip)
        for (k in -3..3) b(0f, 0.0232f, k * 0.068f, 0.27f, 0.002f, 0.012f, gripL)
        b(0f, 0.0235f, 0f, 0.012f, 0.002f, 0.5f, gripL)
        // adesivos nos bicos
        b(0f, 0.0245f, 0.18f, 0.2f, 0.002f, 0.035f, gfxC); b(0f, 0.0245f, -0.18f, 0.2f, 0.002f, 0.035f, gfxA)
        // ---- gráfico de baixo (aparece nos flips) ----
        b(0f, -0.02f, 0f, 0.272f, 0.006f, 0.5f, gfxA)
        b(0f, -0.0235f, 0f, 0.1f, 0.002f, 0.46f, gfxB)
        b(0f, -0.0245f, 0.1f, 0.2f, 0.002f, 0.06f, gfxC); b(0f, -0.0245f, -0.1f, 0.2f, 0.002f, 0.06f, gfxC)
        // ---- trucks ----
        for (zz in floatArrayOf(0.255f, -0.255f)) {
            b(0f, -0.034f, zz, 0.07f, 0.01f, 0.05f, metalD)                       // riser/pad
            b(0f, -0.046f, zz, 0.17f, 0.014f, 0.095f, metal)                       // base
            b(0f, -0.066f, zz, 0.2f, 0.028f, 0.05f, metal)                         // hanger
            b(0f, -0.1f, zz, 0.36f, 0.016f, 0.016f, metalD)                      // eixo
            b(0.05f, -0.056f, zz, 0.035f, 0.03f, 0.035f, bush); b(-0.05f, -0.056f, zz, 0.035f, 0.03f, 0.035f, bush)   // borrachas
            b(0f, -0.038f, zz + 0.035f * (if (zz > 0f) 1f else -1f), 0.03f, 0.03f, 0.03f, 0x4A4F57)   // parafuso rei
            for (xx in floatArrayOf(0.168f, -0.168f)) {
                // roda: vários quadrados girados = quase um cilindro redondo
                for (k in 0 until 6) box(base, xx, -0.1f, zz, s.wheel + k * 15f, 0f, 0.074f, 0.12f, 0.12f, 0f, wheelC, 1f, 1f)
                for (k in 0 until 3) box(base, xx, -0.1f, zz, s.wheel + k * 30f, 0f, 0.082f, 0.068f, 0.068f, 0f, hubC, 1f, 1f)
            }
        }
        // ---- luz embaixo (neon): acende com a velocidade e no grind ----
        val glow = max(s.speedK, if (s.grinding) 0.8f else 0f)
        if (glow > 0.12f) b(0f, -0.14f, 0f, 0.34f, 0.006f, 0.76f, 0x45E9FF, 0f, 2.2f, 0.18f + 0.3f * glow)
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

    private val SKIN: Int get() = Look.skin()
    private val SKIN_D: Int get() = shadeC(Look.skin(), 0.85f)
    private val SLEEVE: Int get() = when (Look.v[Look.TOP]) { 5 -> 0x4A5568; 6, 8, 15, 16 -> 0xF4F4F4; else -> Look.pal(Look.TOPC) }
    private val SLEEVE_L: Int get() = mixC(SLEEVE, 0xFFFFFF, 0.35f)
    private val showLeftHand = false   // mão esquerda vazia no canto, como no Minecraft (false = esconde)

    /** Base do braço: origem no ombro (sx,sy,sz), eixo Z apontando pra mão (hx,hy,hz), Y pra cima. Retorna o comprimento. */
    private fun armBasis(out: FloatArray, sx: Float, sy: Float, sz: Float, hx: Float, hy: Float, hz: Float): Float {
        var zx = hx - sx; var zy = hy - sy; var zz = hz - sz
        val len = sqrt(zx * zx + zy * zy + zz * zz); zx /= len; zy /= len; zz /= len
        var xx = zz; var xz = -zx                       // X = cima x Z
        val xl = sqrt(xx * xx + xz * xz); xx /= xl; xz /= xl
        val yx = zy * xz; val yy = zz * xx - zx * xz; val yz = -zy * xx   // Y = Z x X (xy = 0)
        out[0] = xx; out[1] = 0f; out[2] = xz; out[3] = 0f
        out[4] = yx; out[5] = yy; out[6] = yz; out[7] = 0f
        out[8] = zx; out[9] = zy; out[10] = zz; out[11] = 0f
        out[12] = sx; out[13] = sy; out[14] = sz; out[15] = 1f
        return len
    }

    /** braço estilo Minecraft: manga + punho de pele grande (cubo, sem dedos). Origem = ombro, Z = direção da mão; len = distância até o CENTRO do punho (onde fica o cabo). */
    private fun drawArm(m: FloatArray, len: Float, w: Float = 0.17f, fist: Boolean = true) {
        if (!fist) {   // ferramenta: só a manga; o punho é desenhado alinhado com o cabo (drawToolFist)
            val sl = len - 0.08f
            box(m, 0f, 0f, sl / 2f, 0f, 0f, w, w, sl, 0f, SLEEVE)
            return
        }
        val sl = len - 0.17f
        box(m, 0f, 0f, sl / 2f, 0f, 0f, w, w, sl, 0f, SLEEVE)                                   // manga
        box(m, 0f, 0f, sl + 0.02f, 0f, 0f, w * 1.14f, w * 1.14f, 0.05f, 0f, SLEEVE_L)             // barra da manga
        val fw = w * 1.25f; val fh = w * 1.15f
        box(m, 0f, 0f, len, 0f, 0f, fw, fh, 0.24f, 0f, SKIN)                                      // punho
        box(m, 0f, -fh * 0.5f + 0.013f, len, 0f, 0f, fw * 1.003f, 0.028f, 0.243f, 0f, SKIN_D)     // sombra por baixo
        box(m, 0f, fh * 0.5f - 0.01f, len + 0.02f, 0f, 0f, fw * 0.92f, 0.02f, 0.18f, 0f, mixC(SKIN, 0xFFFFFF, 0.35f)) // luz em cima
    }

    /** braço + punho RETOS, mesma seção, face com face. Origem = ponto fixo ATRÁS da câmera (nunca aparece), Z aponta pro punho; len = distância até o centro do punho. */
    private fun drawToolArm(m: FloatArray, len: Float) {
        val fz = 0.12f
        val sl = len - fz - 0.06f
        box(m, 0f, 0f, sl / 2f, 0f, 0f, 0.21f, 0.19f, sl, 0f, SLEEVE)                  // manga
        box(m, 0f, 0f, sl + 0.03f, 0f, 0f, 0.225f, 0.205f, 0.06f, 0f, SLEEVE_L)          // barra da manga
        box(m, 0f, 0f, len, 0f, 0f, 0.21f, 0.19f, 0.24f, 0f, SKIN)                       // punho
        box(m, 0f, -0.095f + 0.014f, len, 0f, 0f, 0.213f, 0.03f, 0.243f, 0f, SKIN_D)     // sombra de baixo
        box(m, 0f, 0.095f - 0.008f, len, 0f, 0f, 0.213f, 0.02f, 0.243f, 0f, mixC(SKIN, 0xFFFFFF, 0.35f))    // luz de cima
    }

    private val tmp2 = FloatArray(16); private val restM = FloatArray(16); private val restInv = FloatArray(16); private val camBlk = FloatArray(16)

    private val hp = FloatArray(4)
    private val va = FloatArray(3); private val ve = FloatArray(3)
    private fun nrm(v: FloatArray) { val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); if (l > 1e-6f) { v[0] /= l; v[1] /= l; v[2] /= l } }
    private fun rotXv(v: FloatArray, deg: Float) { val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat(); val y = v[1] * c - v[2] * s; val z = v[1] * s + v[2] * c; v[1] = y; v[2] = z }
    private fun rotYv(v: FloatArray, deg: Float) { val r = Math.toRadians(deg.toDouble()); val c = cos(r).toFloat(); val s = sin(r).toFloat(); val x = v[0] * c + v[2] * s; val z = -v[0] * s + v[2] * c; v[0] = x; v[2] = z }

    /** Monta a matriz da ferramenta direto no espaço da câmera (X direita, Y cima, -Z frente), com origem no punho.
     *  Eixo da ferramenta = pra cima, inclinado pra frente (lean) e levemente pra dentro; phi = giro do fio/cabeça ao redor do cabo
     *  (0 = fio pra frente, 90 = fio pra esquerda, mostrando o lado). Segue a animação de golpe. */
    private fun toolMatrix(out: FloatArray, px: Float, py: Float, pz: Float, lean: Float, phi: Float, sc: Float, delta: Float, yawSw: Float) {
        val lr = Math.toRadians(lean.toDouble())
        va[0] = -0.12f; va[1] = cos(lr).toFloat(); va[2] = -sin(lr).toFloat(); nrm(va)
        val d = va[2] * -1f   // F=(0,0,-1): F·A
        ve[0] = 0f - d * va[0]; ve[1] = 0f - d * va[1]; ve[2] = -1f - d * va[2]; nrm(ve)
        rotXv(va, -delta); rotXv(ve, -delta); rotYv(va, yawSw); rotYv(ve, yawSw)
        // L = A x E
        val lx = va[1] * ve[2] - va[2] * ve[1]; val ly = va[2] * ve[0] - va[0] * ve[2]; val lz = va[0] * ve[1] - va[1] * ve[0]
        val pr = Math.toRadians(phi.toDouble()); val cp = cos(pr).toFloat(); val sp = sin(pr).toFloat()
        val ex = cp * ve[0] + sp * lx; val ey = cp * ve[1] + sp * ly; val ez = cp * ve[2] + sp * lz
        // Z = A ; Y = -E ; X = Y x Z
        val yx = -ex; val yy = -ey; val yz = -ez
        val xx = yy * va[2] - yz * va[1]; val xy = yz * va[0] - yx * va[2]; val xz = yx * va[1] - yy * va[0]
        out[0] = xx * sc; out[1] = xy * sc; out[2] = xz * sc; out[3] = 0f
        out[4] = yx * sc; out[5] = yy * sc; out[6] = yz * sc; out[7] = 0f
        out[8] = va[0] * sc; out[9] = va[1] * sc; out[10] = va[2] * sc; out[11] = 0f
        out[12] = px; out[13] = py; out[14] = pz; out[15] = 1f
    }

    private fun tb(m: FloatArray, x: Float, y: Float, z: Float, sx: Float, sy: Float, sz: Float, col: Int, rx: Float = 0f, shine: Float = 1f) =
        box(m, x, y, z, rx, 0f, sx, sy, sz, 0f, col, 1f, shine)

    /** Ferramentas 3D na mão. Origem = centro do punho; Y = cabo pra cima; frente = -Z (a parte que corta/ponta fica virada pra frente). glow = brilho mágico (cajado). */
    private fun drawTool3D(m: FloatArray, id: Int, t: Float, glow: Float = 0f) {
        val iron = 0xC5CEDA; val ironD = 0x8B96A6; val ironL = 0xEEF3FA
        val wood = 0x9A6A3E; val woodD = 0x6F4A29; val gold = 0xFFD060
        val glint = 0.5f + 0.5f * sin(t * 3f)
        if (id != Items.SWORD && id != Items.STAFF) {
            tb(m, 0f, 0.36f, 0f, 0.08f, 0.84f, 0.08f, wood)                       // cabo de madeira
            tb(m, 0f, -0.06f, 0f, 0.095f, 0.07f, 0.095f, woodD); tb(m, 0f, 0.08f, 0f, 0.095f, 0.07f, 0.095f, woodD)   // empunhadura
        }
        when (id) {
            Items.PICK -> {
                tb(m, 0f, 0.72f, 0f, 0.11f, 0.11f, 0.11f, ironD)                  // encaixe no cabo
                for (sg in intArrayOf(-1, 1)) {                                   // -1 = ponta pra frente, +1 = pra trás
                    val s = sg.toFloat()
                    tb(m, 0f, 0.73f, s * 0.14f, 0.085f, 0.09f, 0.16f, iron)
                    tb(m, 0f, 0.715f, s * 0.29f, 0.075f, 0.075f, 0.17f, iron, rx = s * 25f)
                    tb(m, 0f, 0.635f, s * 0.40f, 0.06f, 0.06f, 0.13f, ironL, rx = s * 50f)
                }
            }
            Items.AXE -> {
                tb(m, 0f, 0.68f, 0.08f, 0.085f, 0.17f, 0.08f, ironD)              // nuca
                tb(m, 0f, 0.68f, 0f, 0.09f, 0.22f, 0.11f, iron)                   // cabeça
                tb(m, 0f, 0.68f, -0.10f, 0.075f, 0.28f, 0.10f, iron)              // lâmina
                tb(m, 0f, 0.68f, -0.17f, 0.06f, 0.36f, 0.05f, ironL)              // gume
                tb(m, 0f, 0.68f, -0.205f, 0.04f, 0.40f, 0.02f, 0xFFFFFF)          // fio virado pra frente
            }
            Items.SWORD -> {
                tb(m, 0f, -0.04f, 0f, 0.07f, 0.28f, 0.07f, woodD)                 // cabo
                tb(m, 0f, -0.2f, 0f, 0.09f, 0.07f, 0.09f, gold)                   // pomo
                tb(m, 0f, 0.12f, 0f, 0.08f, 0.05f, 0.30f, gold)                   // guarda
                tb(m, 0f, 0.55f, 0f, 0.03f, 0.84f, 0.12f, 0xC6E4FA, shine = 0.95f + 0.05f * glint)   // lâmina
                tb(m, 0f, 0.55f, 0f, 0.045f, 0.78f, 0.04f, 0x7FC8F5)              // nervura central
                tb(m, 0f, 0.55f, -0.065f, 0.02f, 0.84f, 0.02f, 0xFFFFFF)          // fio da frente
                tb(m, 0f, 1.0f, 0f, 0.03f, 0.08f, 0.08f, 0xC6E4FA)                // ponta
                tb(m, 0f, 1.06f, 0f, 0.03f, 0.05f, 0.04f, 0xFFFFFF)
            }
            Items.STAFF -> {
                tb(m, 0f, 0.4f, 0f, 0.07f, 1.3f, 0.07f, 0x7B54C4)                 // haste
                tb(m, 0f, 1.05f, 0f, 0.11f, 0.06f, 0.11f, gold)                   // anel
                val gs = 0.2f * (1f + 0.35f * glow)
                box(m, 0f, 1.2f, 0f, 45f + game.time * 60f * (0.3f + glow), 45f, gs, gs, gs, 0f, 0x7FE8FF, 1f, 0.9f + 0.1f * glint + 0.3f * glow)   // gema (gira mais rápido ao lançar)
                val hs = 0.3f * (1f + 0.9f * glow)
                G.glDepthMask(false)
                box(m, 0f, 1.2f, 0f, 0f, 0f, hs, hs, hs, 0f, 0xBFF6FF, 0.25f + 0.4f * glow, 1f)   // brilho
                G.glDepthMask(true)
            }
        }
    }

    /** partes que cortam, usadas no rastro (ghost) do golpe */
    private fun drawTrail(m: FloatArray, id: Int, a: Float) {
        G.glDepthMask(false)
        when (id) {
            Items.SWORD -> box(m, 0f, 0.55f, 0f, 0f, 0f, 0.03f, 0.9f, 0.18f, 0f, 0xCFEFFF, a)
            Items.AXE -> box(m, 0f, 0.68f, -0.12f, 0f, 0f, 0.05f, 0.4f, 0.22f, 0f, 0xFFFFFF, a)
            Items.PICK -> box(m, 0f, 0.72f, 0f, 0f, 0f, 0.06f, 0.07f, 0.95f, 0f, 0xFFFFFF, a)
        }
        G.glDepthMask(true)
    }

    // ---------- animações por ferramenta (keyframes) ----------
    // linha: [tempo 0..1, easing (0 suave, 1 acelera, 2 desacelera), offX, offY, offZ, pitch, yaw, roll, punhoPitch, punhoRoll, punhoYaw, brilho]
    
    
    
    
    
    private val KF_SWORD = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            floatArrayOf(0.1f, 2f, 0.02f, -0.03f, 0.05f, -4f, -1f, -1f, 10f, -2f, 0f, 0f),
            floatArrayOf(0.3f, 0f, 0.04f, 0.14f, 0.08f, 44f, -4f, -5f, -18f, -8f, 0f, 0f),
            floatArrayOf(0.37f, 0f, 0.04f, 0.15f, 0.09f, 48f, -4f, -5f, -22f, -8f, 0f, 0f),
            floatArrayOf(0.5f, 1f, 0.0f, 0.0f, -0.2f, 10f, 4f, 3f, -5f, 10f, 0f, 0f),
            floatArrayOf(0.6f, 2f, -0.07f, -0.12f, -0.24f, -26f, 14f, 10f, -50f, 30f, 0f, 0f),
            floatArrayOf(0.68f, 0f, -0.07f, -0.11f, -0.21f, -25f, 15f, 10f, -49f, 30f, 0f, 0f),
            floatArrayOf(0.8f, 0f, -0.07f, -0.1f, -0.2f, -24f, 16f, 10f, -48f, 30f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    private val KF_SWORD2 = arrayOf(
            floatArrayOf(0f, 0f, -0.07f, -0.1f, -0.2f, -24f, 16f, 10f, -48f, 30f, 0f, 0f),
            floatArrayOf(0.12f, 0f, -0.07f, -0.05f, -0.1f, -10f, 28f, 10f, -52f, 36f, 0f, 0f),
            floatArrayOf(0.4f, 1f, 0.0f, 0.0f, -0.3f, -4f, 0f, -2f, -66f, 0f, 0f, 0f),
            floatArrayOf(0.55f, 2f, 0.12f, 0.0f, -0.18f, -2f, -34f, -14f, -62f, -26f, 0f, 0f),
            floatArrayOf(0.7f, 0f, 0.13f, 0.0f, -0.12f, -2f, -38f, -16f, -58f, -30f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    private val KF_SWORD3 = arrayOf(
            floatArrayOf(0f, 0f, 0.13f, 0.0f, -0.12f, -2f, -38f, -16f, -58f, -30f, 0f, 0f),
            floatArrayOf(0.22f, 0f, 0.06f, -0.05f, 0.16f, 6f, -12f, -4f, -72f, -6f, 0f, 0f),
            floatArrayOf(0.34f, 0f, 0.06f, -0.06f, 0.21f, 8f, -10f, -4f, -76f, -4f, 0f, 0f),
            floatArrayOf(0.46f, 1f, 0.0f, -0.02f, -0.5f, 0f, 4f, 0f, -72f, 8f, 0f, 0f),
            floatArrayOf(0.56f, 2f, 0.0f, -0.02f, -0.56f, 0f, 4f, 0f, -72f, 8f, 0f, 0f),
            floatArrayOf(0.72f, 0f, 0.0f, -0.02f, -0.5f, 0f, 3f, 0f, -70f, 6f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    private val KF_AXE = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            floatArrayOf(0.07f, 0f, -0.0036f, -0.009f, -0.009f, -3.24f, 0.9f, 1.44f, -2.7f, 0.9f, -0f, 0f),
            floatArrayOf(0.36f, 2f, 0.02f, 0.05f, 0.05f, 18f, -5f, -8f, 15f, -5f, 0f, 0f),
            floatArrayOf(0.43f, 0f, 0.02f, 0.06f, 0.06f, 20f, -6f, -9f, 17f, -6f, 0f, 0f),
            floatArrayOf(0.55f, 1f, -0.04f, -0.16f, -0.3f, -32f, 6f, 6f, -20f, 8f, 0f, 0f),
            floatArrayOf(0.66f, 2f, -0.03f, -0.12f, -0.24f, -26f, 5f, 4f, -12f, 5f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    private val KF_PICK = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            floatArrayOf(0.07f, 0f, -0.0036f, -0.009f, -0.0072f, -2.88f, 0.9f, 1.08f, -2.34f, 0.72f, -0f, 0f),
            floatArrayOf(0.3f, 2f, 0.02f, 0.05f, 0.04f, 16f, -5f, -6f, 13f, -4f, 0f, 0f),
            floatArrayOf(0.36f, 0f, 0.02f, 0.06f, 0.05f, 18f, -6f, -7f, 15f, -5f, 0f, 0f),
            floatArrayOf(0.54f, 1f, -0.02f, -0.14f, -0.26f, -30f, 4f, 5f, -16f, 5f, 0f, 0f),
            floatArrayOf(0.64f, 2f, -0.02f, -0.11f, -0.2f, -22f, 3f, 3f, -9f, 3f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    private val KF_STAFF = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            floatArrayOf(0.07f, 0f, -0.0054f, 0.0072f, -0.0126f, -1.08f, 0.54f, 0.9f, -1.8f, 0.72f, -0f, 0f),
            floatArrayOf(0.28f, 2f, 0.03f, -0.04f, 0.07f, 6f, -3f, -5f, 10f, -4f, 0f, 0.4f),
            floatArrayOf(0.34f, 0f, 0.03f, -0.04f, 0.08f, 7f, -3f, -5f, 12f, -4f, 0f, 0.5f),
            floatArrayOf(0.5f, 1f, -0.02f, 0.05f, -0.32f, -12f, 0f, 0f, -36f, 2f, 0f, 1f),
            floatArrayOf(0.74f, 2f, -0.02f, 0.04f, -0.26f, -8f, 0f, 0f, -28f, 2f, 0f, 0.7f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    private val KF_BLOCK = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            floatArrayOf(0.35f, 2f, 0f, -0.05f, -0.16f, -14f, 0f, 0f, -6f, 0f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    private val KF_FIST = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            floatArrayOf(0.25f, 2f, 0.04f, -0.04f, 0.1f, 8f, -5f, -4f, 0f, 0f, 0f, 0f),
            floatArrayOf(0.5f, 1f, -0.1f, 0.02f, -0.4f, -6f, 6f, 3f, 0f, 0f, 0f, 0f),
            floatArrayOf(0.66f, 2f, -0.08f, 0.01f, -0.3f, -4f, 4f, 2f, 0f, 0f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    private val KF_SWORD_POWER = arrayOf(
            floatArrayOf(0f, 0f, 0.05f, 0.17f, 0.1f, 54f, -5f, -6f, -28f, -4f, 0f, 0f),
            floatArrayOf(0.06f, 0f, 0.05f, 0.19f, 0.11f, 58f, -5f, -6f, -22f, -4f, 0f, 0f),
            floatArrayOf(0.26f, 1f, 0.0f, 0.0f, -0.28f, 6f, 6f, 2f, -10f, 10f, 0f, 0f),
            floatArrayOf(0.36f, 2f, -0.09f, -0.18f, -0.36f, -34f, 16f, 10f, -62f, 32f, 0f, 0f),
            floatArrayOf(0.44f, 0f, -0.09f, -0.16f, -0.32f, -31f, 17f, 11f, -58f, 32f, 0f, 0f),
            floatArrayOf(0.62f, 0f, -0.08f, -0.12f, -0.2f, -22f, 15f, 10f, -48f, 30f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    private val KF_AXE_POWER = arrayOf(
            floatArrayOf(0f, 0f, 0.05f, 0.18f, 0.11f, 56f, -4f, -5f, -30f, -4f, 0f, 0f),
            floatArrayOf(0.07f, 0f, 0.05f, 0.2f, 0.12f, 60f, -4f, -5f, -26f, -4f, 0f, 0f),
            floatArrayOf(0.3f, 1f, 0.0f, 0.0f, -0.3f, 6f, 3f, 2f, -12f, 6f, 0f, 0f),
            floatArrayOf(0.4f, 2f, -0.07f, -0.2f, -0.42f, -38f, 10f, 8f, -60f, 20f, 0f, 0f),
            floatArrayOf(0.5f, 0f, -0.07f, -0.17f, -0.38f, -35f, 11f, 9f, -56f, 20f, 0f, 0f),
            floatArrayOf(0.68f, 0f, -0.05f, -0.12f, -0.24f, -24f, 8f, 6f, -44f, 16f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)
        )

    private fun sm01(x: Float): Float { val t = x.coerceIn(0f, 1f); return t * t * (3f - 2f * t) }
    private val CH_MID_S = floatArrayOf(0.04f, 0.15f, 0.09f, 48f, -4f, -5f, -24f, -4f, 0f, 0f)
    private val CH_FULL_S = floatArrayOf(0.05f, 0.17f, 0.10f, 54f, -5f, -6f, -28f, -4f, 0f, 0f)
    private val CH_MID_A = floatArrayOf(0.04f, 0.15f, 0.09f, 50f, -3f, -4f, -26f, -4f, 0f, 0f)
    private val CH_FULL_A = floatArrayOf(0.05f, 0.18f, 0.11f, 56f, -4f, -5f, -30f, -4f, 0f, 0f)

    /** segurando o ataque: braço sobe e a ferramenta fica pra trás; perto do máximo, treme de tanta força */
    private fun chargePose(out: FloatArray, id: Int, ch: Float, tt: Float) {
        val mid = if (id == Items.AXE) CH_MID_A else CH_MID_S; val full = if (id == Items.AXE) CH_FULL_A else CH_FULL_S
        val e1 = sm01(ch / 0.3f); val e2 = sm01((ch - 0.3f) / 0.7f)
        for (j in 0 until 10) out[j] = mid[j] * e1 + (full[j] - mid[j]) * e2
        val tr = sm01((ch - 0.75f) / 0.25f)
        out[0] += sin(tt * 70f) * 0.004f * tr; out[5] += sin(tt * 55f + 1f) * 1.2f * tr; out[3] += sin(tt * 63f) * 0.8f * tr
    }

    private val SWORD_COMBO = arrayOf(KF_SWORD, KF_SWORD2, KF_SWORD3)
    private val lastRaw = FloatArray(10); private val blendFrom = FloatArray(10); private var prevSwingR = 1f

    /** pose da espada com transição suave a partir da pose em que o golpe anterior parou (conecta os cortes do combo) */
    private fun evalSword(out: FloatArray, k: Array<FloatArray>, sp: Float) {
        evalPose(out, k, sp)
        if (sp < 0.16f) {
            val t = (sp / 0.16f).coerceIn(0f, 1f); val w = 1f - t * t * (3f - 2f * t)
            for (j in 0 until 10) out[j] += (blendFrom[j] - out[j]) * w
        }
    }

    private fun kfFor(id: Int) = when {
        id == Items.SWORD -> (if (game.powerSwing) KF_SWORD_POWER else SWORD_COMBO[game.combo % SWORD_COMBO.size]); id == Items.AXE -> (if (game.powerSwing) KF_AXE_POWER else KF_AXE); id == Items.PICK -> KF_PICK; id == Items.STAFF -> KF_STAFF
        Items.isBlock(id) -> KF_BLOCK; else -> KF_FIST
    }

    private fun evalPose(out: FloatArray, k: Array<FloatArray>, sp: Float) {
        var i = 0
        while (i < k.size - 2 && sp > k[i + 1][0]) i++
        val a = k[i]; val b = k[i + 1]
        val h = b[0] - a[0]
        val u = if (h <= 1e-5f) 1f else ((sp - a[0]) / h).coerceIn(0f, 1f)
        val u2 = u * u; val u3 = u2 * u
        val h00 = 2f * u3 - 3f * u2 + 1f; val h10 = u3 - 2f * u2 + u; val h01 = -2f * u3 + 3f * u2; val h11 = u3 - u2
        val pa = if (i > 0) k[i - 1] else null
        val nb = if (i + 2 < k.size) k[i + 2] else null
        for (j in 0 until 10) {
            val m0 = if (pa != null) (b[2 + j] - pa[2 + j]) / (b[0] - pa[0]) else 0f
            val m1 = if (nb != null) (nb[2 + j] - a[2 + j]) / (nb[0] - a[0]) else 0f
            out[j] = h00 * a[2 + j] + h10 * h * m0 + h01 * b[2 + j] + h11 * h * m1
        }
    }

    /** braço inteiro gira em volta do ombro (fora da tela) + deslocamento */
    private fun poseMatrix(out: FloatArray, p: FloatArray, ox: Float, oy: Float, sx: Float, sy: Float, sz: Float) {
        Matrix.setIdentityM(out, 0)
        Matrix.translateM(out, 0, ox + p[0], oy + p[1], p[2])
        Matrix.translateM(out, 0, sx, sy, sz)
        Matrix.rotateM(out, 0, p[4], 0f, 1f, 0f)
        Matrix.rotateM(out, 0, p[3], 1f, 0f, 0f)
        Matrix.rotateM(out, 0, p[5], 0f, 0f, 1f)
        Matrix.translateM(out, 0, -sx, -sy, -sz)
    }

    /** ferramenta: origem no centro do punho; o punho ainda gira (flick) em volta dela */
    private fun toolBase(out: FloatArray, p: FloatArray, hx: Float, hy: Float, hz: Float) {
        Matrix.setIdentityM(out, 0)
        Matrix.translateM(out, 0, hx, hy, hz)
        Matrix.rotateM(out, 0, p[6], 1f, 0f, 0f)
        Matrix.rotateM(out, 0, p[7], 0f, 0f, 1f)
        Matrix.rotateM(out, 0, p[8], 0f, 1f, 0f)
        Matrix.rotateM(out, 0, 22f, 0f, 1f, 0f)            // mostra a lateral
        Matrix.rotateM(out, 0, 5f, 1f, 0f, 0f)             // topo levemente pra trás: ferramenta em pé, sem cortar o punho
        Matrix.rotateM(out, 0, 3f, 0f, 0f, 1f)             // quase reta
    }

    private val fistIn = FloatArray(4); private val fistOut = FloatArray(4)
    private var lagP = 0f; private var lagR = 0f; private var prvP = 0f; private var prvY = 0f; private var prvR = 0f
    private val poseP = FloatArray(10); private val ghostP = FloatArray(10)
    private val poseM = FloatArray(16); private val ghostM = FloatArray(16); private val armM = FloatArray(16); private val toolM = FloatArray(16)

    /** desenha o sprite 16x16 como cubinhos: cada "run" horizontal vira uma caixa. Origem = célula de empunhadura. */
    private fun drawSprite(m: FloatArray, sp: ToolSprites.Sprite, t: Float) {
        val glint = 0.5f + 0.5f * sin(t * 3f)
        for (r in sp.runs) {
            val w = (r.x1 - r.x0 + 1).toFloat()
            val cx = (r.x0 + r.x1 + 1) / 2f - (sp.gx + 0.5f)
            val cy = -((r.y + 0.5f) - (sp.gy + 0.5f))
            val col = r.c and 0xFFFFFF
            val shine = if (col == 0xFFFFFF || col == 0xF2F7FF || col == 0x7FE8FF) 0.92f + 0.08f * glint else 1f
            box(m, cx, cy, 0f, 0f, 0f, w, 1f, 1.3f, 0f, col, 1f, shine)
        }
    }

    private fun drawHand(dt: Float) {
        G.glClear(G.GL_DEPTH_BUFFER_BIT)
        G.glUniformMatrix4fv(uVP, 1, false, proj, 0); G.glUniform3f(uCam, 0f, 0f, 0f)
        bindMesh(cubeVb, cubeIb)
        val id = game.cur()
        if (id != lastId) { lastId = id; equip = 0f }
        equip = min(1f, equip + dt * 3.2f)
        val e = 1f - (1f - equip) * (1f - equip) * (1f - equip)
        val inv = 1f - e
        val dyaw = game.yaw - prevYaw; val dp = game.pitch - prevPitch; prevYaw = game.yaw; prevPitch = game.pitch
        val k = min(1f, 10f * dt)
        swayX += ((dyaw / dt * 0.02f).coerceIn(-0.1f, 0.1f) - swayX) * k
        swayY += ((-dp / dt * 0.02f).coerceIn(-0.1f, 0.1f) - swayY) * k
        val wp = game.walkPhase; val wa = game.walkAmt; val tt = game.time
        val bobX = sin(wp) * 0.03f * wa; val bobY = abs(sin(wp)) * 0.03f * wa
        val breath = sin(tt * 1.8f) * 0.006f
        val ox = bobX + swayX; val oy = bobY + swayY + breath - inv * 0.55f
        val empty = id <= 0
        val isTool = id > 0 && !Items.isBlock(id)
        val armW = if (empty) 0.225f else 0.2f   // braço do Minecraft: 4 px = 0,225
        // punho (centro do punho = onde o cabo passa). Ferramenta: punho mais alto, inteiro na tela; mão vazia/bloco: poses das referências
        val hx = if (empty) 0.589f else if (isTool) 0.66f else 0.752f
        val hy = if (empty) -0.534f else if (isTool) -0.52f else -0.756f
        val hz = if (empty) -0.982f else if (isTool) -1.12f else -1.125f
        val sx0 = hx - 0.223f; val sy0 = hy - 0.138f; val sz0 = hz + 0.458f

        // ---- pose animada: golpe (keyframes da ferramenta) + respiração + andar + trocar de item ----
        val kfs = kfFor(id)
        if (game.charging) chargePose(poseP, id, game.charge, tt) else {
            if (game.swing < prevSwingR) System.arraycopy(lastRaw, 0, blendFrom, 0, 10)   // novo golpe: guarda de onde a espada vinha
            prevSwingR = game.swing
            if (id == Items.SWORD) evalSword(poseP, kfs, game.swing) else evalPose(poseP, kfs, game.swing)
        }
        System.arraycopy(poseP, 0, lastRaw, 0, 10)
        val p = poseP
        val flow = sin(tt * 1.5f)
        val idleRoll = when (id) { Items.SWORD -> flow * 1.8f; Items.STAFF -> sin(tt * 1.3f) * 2.2f; else -> flow * 0.9f }
        p[5] += idleRoll + sin(wp) * 1.8f * wa
        p[3] += -abs(sin(wp)) * 2f * wa + sin(tt * 1.1f) * 0.6f
        if (id == Items.STAFF) { p[1] += sin(tt * 2f) * 0.014f; p[7] += sin(tt * 1.7f) * 2.5f; p[9] = max(p[9], 0.25f + 0.2f * sin(tt * 3f)) }
        p[6] += inv * 42f; p[5] += -inv * 16f; p[3] += -inv * 12f   // ao trocar: a ferramenta sobe girando e se ajeita

        // arrasto: a ferramenta fica um pouco pra trás do braço e chicoteia ao passar (mola)
        val vP = (p[3] - prvP) / dt; val vY = (p[4] - prvY) / dt; val vR = (p[5] - prvR) / dt
        prvP = p[3]; prvY = p[4]; prvR = p[5]
        val kk = min(1f, 14f * dt)
        lagP += ((-vP * 0.05f).coerceIn(-22f, 22f) - lagP) * kk
        lagR += ((-vR * 0.05f - vY * 0.03f).coerceIn(-26f, 26f) - lagR) * kk
        p[6] += lagP; p[7] += lagR
        poseMatrix(poseM, p, ox, oy, sx0, sy0, sz0)
        val len = armBasis(fp, sx0, sy0, sz0, hx, hy, hz)
        Matrix.multiplyMM(armM, 0, poseM, 0, fp, 0)
        if (!isTool) drawArm(armM, len, armW, true)

        if (isTool) {
            // rastro do golpe: cópias translúcidas da parte que corta nos instantes anteriores (só aparece quando o golpe é rápido)
            if (game.swing < 0.85f && (id == Items.SWORD || (id == Items.AXE && game.powerSwing))) {
                for (i in 1..8) {
                    val gs = game.swing - i * 0.02f
                    if (gs <= 0f) break
                    if (id == Items.SWORD) evalSword(ghostP, kfs, gs) else evalPose(ghostP, kfs, gs)
                    val diff = abs(ghostP[3] - p[3]) + abs(ghostP[4] - p[4]) + abs(ghostP[5] - p[5]) + abs(ghostP[6] - p[6]) + abs(ghostP[7] - p[7])
                    val al = min(1f, diff / 30f) * (if (game.powerSwing) 0.5f else 0.3f) * (1f - i / 9f)
                    if (al < 0.02f) continue
                    poseMatrix(ghostM, ghostP, ox, oy, sx0, sy0, sz0)
                    toolBase(toolM, ghostP, hx, hy, hz)
                    Matrix.multiplyMM(tmp2, 0, ghostM, 0, toolM, 0)
                    drawTrail(tmp2, id, al)
                }
            }
            // punho + braço: presos num ponto fixo atrás da câmera e esticados até o punho (seguem o golpe sem mostrar a ponta de trás)
            fistIn[0] = hx; fistIn[1] = hy; fistIn[2] = hz; fistIn[3] = 1f
            Matrix.multiplyMV(fistOut, 0, poseM, 0, fistIn, 0)
            val flen = armBasis(fp, hx, hy, hz + 1.9f, fistOut[0], fistOut[1], fistOut[2])
            drawToolArm(fp, flen)
            toolBase(toolM, p, hx, hy, hz)
            Matrix.multiplyMM(tmp2, 0, poseM, 0, toolM, 0)
            drawTool3D(tmp2, id, tt, p[9])
        } else if (Items.isBlock(id)) {
            // bloco grande no canto inferior direito, topo e lateral aparecendo (preso ao braço no golpe)
            Matrix.setIdentityM(camBlk, 0)
            Matrix.translateM(camBlk, 0, 0.786f, -0.773f, -1.068f)
            Matrix.rotateM(camBlk, 0, p[6], 1f, 0f, 0f)
            Matrix.rotateM(camBlk, 0, 5.4f, 1f, 0f, 0f)
            Matrix.rotateM(camBlk, 0, 26.7f, 0f, 1f, 0f)
            Matrix.multiplyMM(tmp2, 0, poseM, 0, camBlk, 0)
            box(tmp2, 0f, 0f, 0f, 0f, 0f, 0.569f, 0.569f, 0.569f, 0f, 0xFFFFFF, 1f, 1f, null, id)
        }
        // mão esquerda: as referências só mostram a direita
        if (showLeftHand) {
            val lx = -bobX * 0.8f - swayX
            val ly = abs(sin(wp + 1.57f)) * 0.03f * wa + swayY + breath - inv * 0.55f
            val ll = armBasis(fp, -0.76f + lx, -1.05f + ly, -0.36f, -0.44f + lx, -0.68f + ly, -0.88f)
            drawArm(fp, ll)
        }
    }

    // ---------- culling: não desenha o que está fora da tela (grande ganho de desempenho) ----------
    private val fpl = FloatArray(24); private val chunkVis = BooleanArray(World.CX * World.CZ)
    private var cvx = 0f; private var cvy = 0f; private var cvz = 0f; private var cdx = 0f; private var cdy = 0f; private var cdz = 0f
    private fun updateFrustum(m: FloatArray) {
        for (i in 0 until 6) {
            val sg = if (i % 2 == 0) 1f else -1f; val r = i / 2
            var a = m[3] + sg * m[r]; var b = m[7] + sg * m[4 + r]; var c = m[11] + sg * m[8 + r]; var d = m[15] + sg * m[12 + r]
            val l = Math.sqrt((a * a + b * b + c * c).toDouble()).toFloat().coerceAtLeast(1e-6f)
            fpl[i * 4] = a / l; fpl[i * 4 + 1] = b / l; fpl[i * 4 + 2] = c / l; fpl[i * 4 + 3] = d / l
        }
        for (cz in 0 until World.CZ) for (cx in 0 until World.CX) {
            val x = cx * World.CH + World.CH / 2f; val y = World.SY / 2f; val z = cz * World.CH + World.CH / 2f
            var ok = true
            for (i in 0 until 6) if (fpl[i * 4] * x + fpl[i * 4 + 1] * y + fpl[i * 4 + 2] * z + fpl[i * 4 + 3] < -21f) { ok = false; break }
            chunkVis[cz * World.CX + cx] = ok
        }
    }
    /** ponto à frente da câmera e perto o bastante? */
    private fun seen(x: Float, y: Float, z: Float, maxD: Float = 34f): Boolean {
        val dx = x - cvx; val dy = y - cvy; val dz = z - cvz
        if (dx * dx + dy * dy + dz * dz > maxD * maxD) return false
        return dx * cdx + dy * cdy + dz * cdz > -1.5f
    }

    override fun onDrawFrame(gl: GL10?) {
        if (!iconsBaked) bakeIcons()
        if (ToolIcons.hasPending()) bakeDecoIcons()
        val now = System.nanoTime()
        val dt = ((now - last) / 1e9f).coerceIn(0.001f, 0.05f); last = now
        game.update(dt)
        run {   // FOV abre com a velocidade do skate (sensação de velocidade)
            val sv = game.skateV
            val fovT = if (game.skating) 70f + 16f * sv.speedK + (if (sv.grinding) 3f else 0f) + 5f * sv.drift else 70f
            if (abs(fovT - fovCur) > 0.04f && vh > 0) {
                fovCur += (fovT - fovCur) * min(1f, 5f * dt)
                Matrix.perspectiveM(proj, 0, fovCur, vw.toFloat() / vh, 0.15f, 200f)
            }
        }
        var built = 0
        for (cz in 0 until World.CZ) for (cx in 0 until World.CX) {
            val ci = cz * World.CX + cx
            if (!world.dirty[ci] || built >= 4) continue
            world.dirty[ci] = false; built++
            world.buildChunk(cx, cz, ob, wb)
            cnt[ci * 2] = upload(ob, vbo[ci * 2], ibo[ci * 2])
            cnt[ci * 2 + 1] = upload(wb, vbo[ci * 2 + 1], ibo[ci * 2 + 1])
        }

        G.glClear(G.GL_COLOR_BUFFER_BIT or G.GL_DEPTH_BUFFER_BIT or G.GL_STENCIL_BUFFER_BIT)
        G.glUseProgram(prog)
        G.glActiveTexture(G.GL_TEXTURE0); G.glUniform1i(uTex, 0)
        val sh = game.shake * 0.25f
        val ex = game.camX + (rnd.nextFloat() - 0.5f) * sh; val ey = game.camY + (rnd.nextFloat() - 0.5f) * sh
        val ez = game.camZ + (rnd.nextFloat() - 0.5f) * sh
        Matrix.setLookAtM(view, 0, ex, ey, ez, ex + game.lookDirX(), ey + game.lookDirY(), ez + game.lookDirZ(), 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        game.vpm = vp.clone()
        updateFrustum(vp)
        cvx = ex; cvy = ey; cvz = ez; cdx = game.lookDirX(); cdy = game.lookDirY(); cdz = game.lookDirZ()
        val uwy = floor(ey).toInt()
        val under = world.get(floor(ex).toInt(), uwy, floor(ez).toInt()) == B.WATER && (world.get(floor(ex).toInt(), uwy + 1, floor(ez).toInt()) != B.AIR || ey < uwy + 0.88f)
        val fr = if (under) 0.36f else fogR; val fg = if (under) 0.78f else fogG; val fb = if (under) 0.95f else fogB
        curFr = fr; curFg = fg; curFb = fb; curUnder = under; curVx = ex; curVy = ey; curVz = ez
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
        fillRip(); G.glUniform4fv(uRip, 4, ripArr, 0)
        G.glDisable(G.GL_BLEND)

        G.glUniformMatrix4fv(uModel, 1, false, ident, 0)
        tint(0xFFFFFF, 1f)
        for (i in 0 until World.CX * World.CZ) {
            if (cnt[i * 2] == 0 || !chunkVis[i]) continue
            bindMesh(vbo[i * 2], ibo[i * 2]); G.glDrawElements(G.GL_TRIANGLES, cnt[i * 2], G.GL_UNSIGNED_INT, 0)
        }
        G.glUniform1f(uWind, 0f)
        bindMesh(cubeVb, cubeIb)
        if (game.thirdPerson && game.deadTimer <= 0f) drawPlayer(game.time)
        G.glEnable(G.GL_BLEND)
        G.glDepthMask(false)
        for (s in game.slimes) shadow(s.x, s.y, s.z, 0.9f)
        if (game.thirdPerson && !game.driving) shadow(game.player.x, game.player.y, game.player.z, 0.8f)
        if (game.carV.placed) shadow(game.carV.x, game.carV.y, game.carV.z, 3.2f)
        if (game.bikeV.placed) shadow(game.bikeV.x, game.bikeV.y, game.bikeV.z, 1.6f)
        if (game.skateV.placed) shadow(game.skateV.x, game.skateV.y, game.skateV.z, 1.0f)
        drawPrints()
        G.glDepthMask(true)
        for (s in game.slimes) drawSlime(s)
        drawVeh(game.carV); drawVeh(game.bikeV); drawSkate(game.skateV)
        drawElevators()
        drawTvScreen()
        drawDebris(); drawTrees(); drawDrops()
        for (q in game.parts) {
            if (!seen(q.x, q.y, q.z, 28f)) continue
            if (q.dust) {   // poeira: nasce pequena, vai inchando devagar e some suave
                setBase(q.x, q.y, q.z, q.age * 55f + q.z * 90f)
                val ps = q.size * (1f + q.age * 2.4f)
                box(base, 0f, 0f, 0f, q.age * 30f, 0f, ps, ps * 0.8f, ps, 0f, q.color, min(0.5f, q.life * 0.9f) * min(1f, q.age * 12f), 1.05f)
                continue
            }
            setBase(q.x, q.y, q.z, game.time * 200f)
            val ps = q.size * min(1f, 0.35f + q.life * 1.6f)
            box(base, 0f, 0f, 0f, 0f, 0f, ps, ps, ps, 0f, q.color, min(1f, q.life * 2.2f))
        }
        drawWind()
        for (l in game.leafFall) {
            if (!seen(l.x, l.y, l.z, 30f)) continue
            val sp = if (l.spin > 0f) l.spin else 1f
            setBase(l.x, l.y, l.z, l.age * 50f * sp + l.ph * 57f)
            val tl = if (l.landed) 0f else sin(l.age * 3f * sp + l.ph) * 55f; val rl = if (l.landed) 0f else cos(l.age * 2.3f * sp + l.ph) * 45f
            val al = min(1f, min(l.age * (if (l.spin > 0f) 14f else 3f), l.life * 1.2f))
            drawLeafShape(l, tl, rl, al)
        }
        for (b in game.bolts) {
            setBase(b.x, b.y, b.z, 0f)
            box(base, 0f, 0f, 0f, game.time * 400f, game.time * 300f, 0.18f, 0.18f, 0.18f, 0f, 0xE8FBFF)
            G.glDepthMask(false)
            box(base, 0f, 0f, 0f, 0f, 0f, 0.4f, 0.4f, 0.4f, 0f, 0x5FD8FF, 0.35f)
            G.glDepthMask(true)
        }
        if (game.hasHit && game.deadTimer <= 0f) {
            val hb = game.world.boxOf(game.hx, game.hy, game.hz)
            if (hb != null) setBase(game.hx + (hb[0] + hb[3]) / 2f, game.hy + (hb[1] + hb[4]) / 2f, game.hz + (hb[2] + hb[5]) / 2f, 0f) else setBase(game.hx + 0.5f, game.hy + 0.5f, game.hz + 0.5f, 0f)
            G.glDepthMask(false)
            box(base, 0f, 0f, 0f, 0f, 0f, if (hb != null) hb[3] - hb[0] + 0.01f else 1.01f, if (hb != null) hb[4] - hb[1] + 0.01f else 1.01f, if (hb != null) hb[5] - hb[2] + 0.01f else 1.01f, 0f, 0xFFFFFF, 0.18f + 0.1f * sin(game.time * 6f))
            G.glDepthMask(true)
        }
        if (game.deadTimer <= 0f) {
            val pc = game.placeCheck()
            if (pc != 0) {   // prévia da colocação: verde pode, vermelho bloqueado, amarelo sem apoio embaixo
                setBase(game.px + 0.5f, game.py + 0.5f, game.pz + 0.5f, 0f)
                G.glDepthMask(false)
                box(base, 0f, 0f, 0f, 0f, 0f, 1.02f, 1.02f, 1.02f, 0f, if (pc == 1) 0x55E070 else if (pc == 2) 0xFF5555 else 0xFFD34A, 0.26f + 0.08f * sin(game.time * 6f))
                G.glDepthMask(true)
            }
            drawDamage(dt)
        }
        G.glUniform1f(uWind, 1f)
        G.glUniformMatrix4fv(uModel, 1, false, ident, 0)
        G.glDisable(G.GL_CULL_FACE)
        tint(0xFFFFFF, 1f)
        for (i in 0 until World.CX * World.CZ) {
            if (cnt[i * 2 + 1] == 0 || !chunkVis[i]) continue
            bindMesh(vbo[i * 2 + 1], ibo[i * 2 + 1]); G.glDrawElements(G.GL_TRIANGLES, cnt[i * 2 + 1], G.GL_UNSIGNED_INT, 0)
        }
        G.glUniform1f(uWind, 0f); bindMesh(cubeVb, cubeIb)
        
        G.glEnable(G.GL_CULL_FACE)
        G.glUniform1f(uWind, 0f)
        if (!game.thirdPerson && game.deadTimer <= 0f && game.tvZoomT < 0.02f) drawHand(dt)
    }
}
