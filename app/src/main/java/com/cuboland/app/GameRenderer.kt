package com.cuboland.app

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

class GameRenderer(val game: Game) : GLSurfaceView.Renderer {
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

    private val VS = """
        uniform mat4 uVP; uniform mat4 uModel; uniform vec3 uCam; uniform float uTime; uniform float uWind; uniform float uUnder;
        attribute vec3 aPos; attribute vec3 aCol; attribute vec2 aUV;
        varying vec3 vCol; varying float vFog; varying vec2 vUV; varying vec3 vWP; varying float vTile; varying vec3 vView;
        void main() {
            vec4 wp = uModel * vec4(aPos, 1.0);
            float tile = floor(aUV.x * 32.0);
            if (uWind > 0.5) {
                float gust = 0.6 + 0.4 * sin(wp.x * 0.12 + wp.z * 0.08 + uTime * 0.7);
                if (tile >= 16.0 && tile < 20.0) {
                    float w = clamp((0.99 - aUV.y) / 0.98, 0.0, 1.0); w = w * w;
                    wp.x += (sin(uTime * 2.3 + wp.x * 0.9 + wp.z * 0.6) * 0.07 + 0.04) * w * gust * 1.4;
                    wp.z += cos(uTime * 1.8 + wp.x * 0.5 + wp.z * 0.9) * 0.05 * w * gust * 1.4;
                } else if (tile == 8.0 || tile == 20.0 || tile == 21.0 || tile == 22.0) {
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
            vCol = aCol; vUV = aUV; vWP = wp.xyz; vTile = tile; vView = uCam - wp.xyz;
            vFog = clamp((length(wp.xyz - uCam) - mix(38.0, 1.0, uUnder)) / mix(52.0, 30.0, uUnder), 0.0, 1.0);
            vFog = vFog * vFog * (3.0 - 2.0 * vFog);
        }"""
    private val FS = """
        #ifdef GL_FRAGMENT_PRECISION_HIGH
        precision highp float;
        #else
        precision mediump float;
        #endif
        uniform vec3 uTint; uniform vec3 uFog; uniform float uAlpha; uniform sampler2D uTex; uniform float uTime; uniform float uUnder; uniform float uWind; uniform vec4 uRip[4];
        varying vec3 vCol; varying float vFog; varying vec2 vUV; varying vec3 vWP; varying float vTile; varying vec3 vView;

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
            bool isLeaf = (vTile > 7.5 && vTile < 8.5) || (vTile > 21.5 && vTile < 22.5);
            vec4 t = texture2D(uTex, uv);
            if (t.a < 0.4) discard;
            float l = clamp((vCol.r - 0.3) / 0.7, 0.0, 1.0);
            vec3 light = mix(vec3(0.80, 0.84, 1.0), vec3(1.05, 1.02, 0.95), l);
            vec3 c = t.rgb * vCol * uTint * light * 0.9;
            float alpha = uAlpha * t.a;
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
                vec3 n = normalize(vec3(-g.x * 1.25, 1.0, -g.y * 1.25));
                vec3 V = normalize(vView);
                if (dot(n, V) < 0.0) n = -n;
                vec3 L = normalize(vec3(0.55, 0.5, 0.65));
                float depth = clamp(vCol.g, 0.0, 1.0);
                vec3 shallow = vec3(0.38, 0.86, 0.84);
                vec3 deep = vec3(0.10, 0.48, 0.78);
                vec3 base = mix(shallow, deep, smoothstep(0.12, 0.95, depth) * 0.6);
                float ndv = max(dot(n, V), 0.0);
                float fres = 0.02 + 0.98 * pow(1.0 - ndv, 5.0);
                vec3 R = reflect(-V, n);
                vec3 sky = mix(uFog, vec3(0.42, 0.66, 0.97), pow(clamp(R.y, 0.0, 1.0), 0.45));
                float rl = max(dot(R, L), 0.0);
                base = mix(base, vec3(0.58, 0.86, 0.86), 0.2);   // água turva, meio leitosa
                vec3 wc = base * (0.88 + 0.22 * dot(n, L));
                wc = mix(wc, sky, clamp(fres * 1.05 + 0.05, 0.0, 0.85));
                wc += vec3(0.6) * ripGlow * 0.16;
                // brilho do sol: halo suave + cintilado delicado
                wc += vec3(1.0, 0.97, 0.88) * (pow(rl, 40.0) * 0.20);
                // espuma macia na beirada
                float foam = smoothstep(0.17, 0.0, depth) * (0.55 + 0.45 * sin(P.x * 4.0 + P.y * 3.5 + tm * 1.4 + sin(P.y * 2.5 - tm) * 2.0));
                wc = mix(wc, vec3(1.0), clamp(foam, 0.0, 1.0) * 0.45);
                float wa = mix(0.62, 0.88, smoothstep(0.0, 0.45, depth));
                wa = clamp(wa + fres * 0.30 + foam * 0.3, 0.0, 1.0);
                if (uUnder > 0.5) {   // vista de baixo: lisa, degradê suave com brilho do sol e um balanço bem leve
                    float win = smoothstep(0.52, 0.80, ndv);
                    float sh = 0.5 + 0.5 * sin(P.x * 0.45 + P.y * 0.35 + tm * 0.5);
                    vec3 mir = mix(vec3(0.16, 0.50, 0.72), vec3(0.24, 0.62, 0.82), sh);
                    float sun = pow(max(dot(-V, L), 0.0), 8.0);
                    vec3 winc = mix(vec3(0.58, 0.88, 0.98), vec3(0.76, 0.96, 1.0), sh) + vec3(0.18) * ripGlow + vec3(1.0, 0.98, 0.88) * sun * 0.40;
                    wc = mix(mir, winc, win);
                    wa = mix(0.90, 0.62, win);
                }
                c = wc; alpha = wa;
            } else if (uWind > 0.5 && vWP.y < 9.86) {   // tudo que está debaixo d'água: azulado, escuro com a profundidade e luz dançando
                float dep = clamp((9.88 - vWP.y) / 6.0, 0.0, 1.0);
                c *= mix(vec3(0.95, 1.0, 1.0), vec3(0.82, 0.95, 1.0), dep * 0.5);
                c = mix(c, vec3(0.40, 0.76, 0.84), 0.18 + 0.40 * dep);   // turbidez: fundo meio enevoado
            }
            if (vTile > 14.5 && vTile < 15.5) c = t.rgb * 1.25;
            if (uUnder > 0.5 && !isWater) {
                c *= vec3(0.88, 1.02, 1.10);
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
        G.glEnable(G.GL_DEPTH_TEST); G.glEnable(G.GL_CULL_FACE); G.glCullFace(G.GL_BACK); G.glFrontFace(G.GL_CCW)
        G.glBlendFunc(G.GL_SRC_ALPHA, G.GL_ONE_MINUS_SRC_ALPHA)
        // textura (atlas)
        val tex = IntArray(1); G.glGenTextures(1, tex, 0); atlasTex = tex[0]; iconsBaked = false
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
            val tile = if (id == 0) 0 else if (id >= 14) 9 + id else if (a == 1) (if (s > 0) B.tTop[id] else B.tBot[id]) else B.tSide[id]
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
        vw = w; vh = h
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
        G.glDrawElements(G.GL_TRIANGLES, 36, G.GL_UNSIGNED_SHORT, cube * 72)
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
        G.glDrawElements(G.GL_TRIANGLES, 36, G.GL_UNSIGNED_SHORT, 0)
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
                    mb.vert(p[0], p[1], p[2], sh, sh, sh, (tile + 0.01f + tu * 0.98f) / Atlas.NT.toFloat(), 0.01f + tv * 0.98f)
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
        G.glDrawElements(G.GL_TRIANGLES, d.icnt, G.GL_UNSIGNED_SHORT, 0)
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
            val rad = 0.25f + r.age * 1.7f
            val al = (1f - r.age / 3.2f) * min(1f, r.amp + 0.25f) * 0.75f
            if (al <= 0.02f) continue
            val n = (10 + rad * 5f).toInt().coerceAtMost(20)
            val seg = 6.2832f * rad / n * 1.08f
            for (k in 0 until n) {
                val a = k * 6.2832f / n
                val x = r.x + cos(a) * rad; val z = r.z + sin(a) * rad
                if (world.get(floor(x).toInt(), World.WATER_Y, floor(z).toInt()) != B.WATER) continue
                setBase(x, sy, z, a * 57.29578f - 90f)
                box(base, 0f, 0f, 0f, 0f, 0f, seg, 0.012f, 0.05f, 0f, 0xEAF8FF, al)
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
    private fun drawTrees() {
        for (f in game.trees) {
            Matrix.setIdentityM(base2, 0); Matrix.translateM(base2, 0, f.px, f.py, f.pz)
            Matrix.rotateM(base2, 0, f.ang * 57.29578f, f.kx, 0f, f.kz)
            for (i in 0 until f.n) box(base2, f.bl[i * 4].toFloat(), f.bl[i * 4 + 1] + f.yo, f.bl[i * 4 + 2].toFloat(), 0f, 0f, 1f, 1f, 1f, 0f, 0xFFFFFF, 1f, 1f, null, f.bl[i * 4 + 3])
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

    /** Modelos 3D próprios das ferramentas (pastel, camadas, gemas e brilho). Origem = centro do punho; o eixo da ferramenta é +Z; o fio/cabeça fica em -Y. */
    private fun drawItem(m: FloatArray, id: Int, t: Float) {
        val glint = 0.5f + 0.5f * sin(t * 3f)
        when {
            id in 1..13 -> {
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

    private fun drawPlayer(t: Float) {
        val p = game.player
        val wp = game.walkPhase; val wa = game.walkAmt
        val swm = game.swimAmt
        val sw = if (swm > 0.2f) sin(t * 8f) * 28f else sin(wp) * 38f * wa
        val air = if (p.onGround) 0f else 1f
        val idle = sin(t * 2f) * 3f * (1f - wa)
        val bob = if (wa < 0.1f) sin(t * 2f) * 0.012f else abs(sin(wp)) * 0.05f * wa
        setBase(p.x, p.y + bob, p.z, Math.toDegrees(game.bodyYaw.toDouble()).toFloat())
        if (swm > 0.01f) {   // nadando: o corpo deita na água (mais inclinado ao mergulhar)
            Matrix.translateM(base, 0, 0f, 0.9f, 0f)
            Matrix.rotateM(base, 0, swm * (78f - Math.toDegrees(game.pitch.toDouble()).toFloat() * 0.35f).coerceIn(40f, 110f), 1f, 0f, 0f)
            Matrix.translateM(base, 0, 0f, -0.9f, 0f)
        }
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
        if (id !in 1..13) Matrix.scaleM(base2, 0, 0.78f, 0.78f, 0.78f)
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

    private val SKIN = 0xF2C29B; private val SKIN_D = 0xD9A07A
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
            box(m, 0f, 0f, sl / 2f, 0f, 0f, w, w, sl, 0f, 0x7FD9C8)
            return
        }
        val sl = len - 0.17f
        box(m, 0f, 0f, sl / 2f, 0f, 0f, w, w, sl, 0f, 0x7FD9C8)                                   // manga
        box(m, 0f, 0f, sl + 0.02f, 0f, 0f, w * 1.14f, w * 1.14f, 0.05f, 0f, 0xA6EBDD)             // barra da manga
        val fw = w * 1.25f; val fh = w * 1.15f
        box(m, 0f, 0f, len, 0f, 0f, fw, fh, 0.24f, 0f, SKIN)                                      // punho
        box(m, 0f, -fh * 0.5f + 0.013f, len, 0f, 0f, fw * 1.003f, 0.028f, 0.243f, 0f, SKIN_D)     // sombra por baixo
        box(m, 0f, fh * 0.5f - 0.01f, len + 0.02f, 0f, 0f, fw * 0.92f, 0.02f, 0.18f, 0f, 0xF8D9BC) // luz em cima
    }

    /** braço + punho RETOS, mesma seção, face com face. Origem = ponto fixo ATRÁS da câmera (nunca aparece), Z aponta pro punho; len = distância até o centro do punho. */
    private fun drawToolArm(m: FloatArray, len: Float) {
        val fz = 0.12f
        val sl = len - fz - 0.06f
        box(m, 0f, 0f, sl / 2f, 0f, 0f, 0.21f, 0.19f, sl, 0f, 0x7FD9C8)                  // manga
        box(m, 0f, 0f, sl + 0.03f, 0f, 0f, 0.225f, 0.205f, 0.06f, 0f, 0xA6EBDD)          // barra da manga
        box(m, 0f, 0f, len, 0f, 0f, 0.21f, 0.19f, 0.24f, 0f, SKIN)                       // punho
        box(m, 0f, -0.095f + 0.014f, len, 0f, 0f, 0.213f, 0.03f, 0.243f, 0f, SKIN_D)     // sombra de baixo
        box(m, 0f, 0.095f - 0.008f, len, 0f, 0f, 0.213f, 0.02f, 0.243f, 0f, 0xF8D9BC)    // luz de cima
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
        id in 1..13 -> KF_BLOCK; else -> KF_FIST
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
        val isTool = id > 0 && id !in 1..13
        val armW = if (empty) 0.2f else 0.17f
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
        } else if (id in 1..13) {
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
        updateFrustum(vp)
        cvx = ex; cvy = ey; cvz = ez; cdx = game.lookDirX(); cdy = game.lookDirY(); cdz = game.lookDirZ()
        val uwy = floor(ey).toInt()
        val under = world.get(floor(ex).toInt(), uwy, floor(ez).toInt()) == B.WATER && (world.get(floor(ex).toInt(), uwy + 1, floor(ez).toInt()) != B.AIR || ey < uwy + 0.88f)
        val fr = if (under) 0.36f else fogR; val fg = if (under) 0.78f else fogG; val fb = if (under) 0.95f else fogB
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
            bindMesh(vbo[i * 2], ibo[i * 2]); G.glDrawElements(G.GL_TRIANGLES, cnt[i * 2], G.GL_UNSIGNED_SHORT, 0)
        }
        G.glUniform1f(uWind, 0f)
        bindMesh(cubeVb, cubeIb)
        if (game.thirdPerson && game.deadTimer <= 0f) drawPlayer(game.time)
        G.glEnable(G.GL_BLEND)
        G.glDepthMask(false)
        for (s in game.slimes) shadow(s.x, s.y, s.z, 0.9f)
        if (game.thirdPerson) shadow(game.player.x, game.player.y, game.player.z, 0.8f)
        drawPrints()
        G.glDepthMask(true)
        for (s in game.slimes) drawSlime(s)
        drawDebris(); drawTrees(); drawDrops(); drawLeafBreaks()
        for (q in game.parts) {
            if (!seen(q.x, q.y, q.z, 28f)) continue
            setBase(q.x, q.y, q.z, game.time * 200f)
            val ps = q.size * min(1f, 0.35f + q.life * 1.6f)
            box(base, 0f, 0f, 0f, 0f, 0f, ps, ps, ps, 0f, q.color, min(1f, q.life * 2.2f))
        }
        for (l in game.leafFall) {
            if (!seen(l.x, l.y, l.z, 30f)) continue
            val sp = if (l.spin > 0f) l.spin else 1f
            setBase(l.x, l.y, l.z, l.age * 50f * sp + l.ph * 57f)
            val tl = if (l.landed) 0f else sin(l.age * 3f * sp + l.ph) * 55f; val rl = if (l.landed) 0f else cos(l.age * 2.3f * sp + l.ph) * 45f
            val al = min(1f, min(l.age * 3f, l.life * 1.2f))
            val edge = mixCol(l.color, 0xE6F59A, 0.35f)
            box(base, 0f, 0f, 0f, tl, 0f, l.size * 0.95f, 0.01f, l.size * 1.15f, 0f, l.color, al, 1f, null, 0, rl)
            box(base, 0f, 0.003f, l.size * 0.28f, tl, 0f, l.size * 0.55f, 0.011f, l.size * 0.55f, 0f, edge, al * 0.9f, 1.05f, null, 0, rl)
            box(base, 0f, 0.006f, 0f, tl, 0f, l.size * 0.09f, 0.012f, l.size * 1.9f, 0f, 0x2F6B1E, al * 0.85f, 1f, null, 0, rl)
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
        if (game.deadTimer <= 0f) drawDamage(dt)
        G.glUniform1f(uWind, 1f)
        G.glUniformMatrix4fv(uModel, 1, false, ident, 0)
        G.glDisable(G.GL_CULL_FACE)
        tint(0xFFFFFF, 1f)
        for (i in 0 until World.CX * World.CZ) {
            if (cnt[i * 2 + 1] == 0 || !chunkVis[i]) continue
            bindMesh(vbo[i * 2 + 1], ibo[i * 2 + 1]); G.glDrawElements(G.GL_TRIANGLES, cnt[i * 2 + 1], G.GL_UNSIGNED_SHORT, 0)
        }
        G.glUniform1f(uWind, 0f); bindMesh(cubeVb, cubeIb)
        
        G.glEnable(G.GL_CULL_FACE)
        G.glUniform1f(uWind, 0f)
        if (!game.thirdPerson && game.deadTimer <= 0f) drawHand(dt)
    }
}
