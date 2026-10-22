package com.cuboland.app

import android.content.Context
import android.graphics.Bitmap
import android.opengl.GLES20 as G
import android.opengl.GLUtils
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.sin

/**
 * Renderiza uma skin PNG Java/Bedrock 64x64 como um avatar cúbico com UVs Minecraft.
 * É um modo alternativo: não modifica nem destrói o modelo customizado do CuboLand.
 */
class MinecraftSkinRenderer {
    private var program = 0
    private var aPos = 0
    private var aUv = 0
    private var uVp = 0
    private var uModel = 0
    private var uTex = 0
    private var texture = 0
    private var active = false
    private val model = FloatArray(16)
    private val temp = FloatArray(16)
    private val vertices: FloatBuffer = ByteBuffer.allocateDirect(36 * 5 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()

    private data class Part(val name: String, val w: Float, val h: Float, val d: Float,
                            val faces: Array<IntArray>)
    private val head = Part("head", .5f, .5f, .5f, arrayOf(
        intArrayOf(8,8,8,8), intArrayOf(24,8,8,8), intArrayOf(0,8,8,8),
        intArrayOf(16,8,8,8), intArrayOf(8,0,8,8), intArrayOf(16,0,8,8)))
    private val torso = Part("torso", .5f, .75f, .25f, arrayOf(
        intArrayOf(20,20,8,12), intArrayOf(32,20,8,12), intArrayOf(16,20,4,12),
        intArrayOf(28,20,4,12), intArrayOf(20,16,8,4), intArrayOf(28,16,8,4)))
    private val rightArm = Part("rightArm", .25f, .75f, .25f, arrayOf(
        intArrayOf(44,20,4,12), intArrayOf(52,20,4,12), intArrayOf(40,20,4,12),
        intArrayOf(48,20,4,12), intArrayOf(44,16,4,4), intArrayOf(48,16,4,4)))
    private val leftArm = Part("leftArm", .25f, .75f, .25f, arrayOf(
        intArrayOf(36,52,4,12), intArrayOf(44,52,4,12), intArrayOf(32,52,4,12),
        intArrayOf(40,52,4,12), intArrayOf(36,48,4,4), intArrayOf(40,48,4,4)))
    private val rightLeg = Part("rightLeg", .25f, .75f, .25f, arrayOf(
        intArrayOf(4,20,4,12), intArrayOf(12,20,4,12), intArrayOf(0,20,4,12),
        intArrayOf(8,20,4,12), intArrayOf(4,16,4,4), intArrayOf(8,16,4,4)))
    private val leftLeg = Part("leftLeg", .25f, .75f, .25f, arrayOf(
        intArrayOf(20,52,4,12), intArrayOf(28,52,4,12), intArrayOf(16,52,4,12),
        intArrayOf(24,52,4,12), intArrayOf(20,48,4,4), intArrayOf(24,48,4,4)))
    // Camada externa oficial com transparência PNG.
    private val headOuter = Part("headOuter", .518f, .518f, .518f, arrayOf(
        intArrayOf(40,8,8,8), intArrayOf(56,8,8,8), intArrayOf(32,8,8,8),
        intArrayOf(48,8,8,8), intArrayOf(40,0,8,8), intArrayOf(48,0,8,8)))
    private val torsoOuter = Part("torsoOuter", .516f, .766f, .266f, arrayOf(
        intArrayOf(20,36,8,12), intArrayOf(32,36,8,12), intArrayOf(16,36,4,12),
        intArrayOf(28,36,4,12), intArrayOf(20,32,8,4), intArrayOf(28,32,8,4)))
    private val rightArmOuter = Part("rightArmOuter", .266f, .766f, .266f, arrayOf(
        intArrayOf(44,36,4,12), intArrayOf(52,36,4,12), intArrayOf(40,36,4,12),
        intArrayOf(48,36,4,12), intArrayOf(44,32,4,4), intArrayOf(48,32,4,4)))
    private val leftArmOuter = Part("leftArmOuter", .266f, .766f, .266f, arrayOf(
        intArrayOf(52,52,4,12), intArrayOf(60,52,4,12), intArrayOf(48,52,4,12),
        intArrayOf(56,52,4,12), intArrayOf(52,48,4,4), intArrayOf(56,48,4,4)))
    private val rightLegOuter = Part("rightLegOuter", .266f, .766f, .266f, arrayOf(
        intArrayOf(4,36,4,12), intArrayOf(12,36,4,12), intArrayOf(0,36,4,12),
        intArrayOf(8,36,4,12), intArrayOf(4,32,4,4), intArrayOf(8,32,4,4)))
    private val leftLegOuter = Part("leftLegOuter", .266f, .766f, .266f, arrayOf(
        intArrayOf(4,52,4,12), intArrayOf(12,52,4,12), intArrayOf(0,52,4,12),
        intArrayOf(8,52,4,12), intArrayOf(4,48,4,4), intArrayOf(8,48,4,4)))

    fun init(context: Context?) {
        active = false
        if (context == null) return
        val bmp = MinecraftSkinLibrary.load(context) ?: return
        if (bmp.width != 64 || bmp.height != 64) { bmp.recycle(); return }
        val vs = """
            uniform mat4 uVP; uniform mat4 uModel;
            attribute vec3 aPos; attribute vec2 aUV; varying vec2 vUV;
            void main(){ vUV=aUV; gl_Position=uVP*uModel*vec4(aPos,1.0); }
        """
        val fs = """
            precision mediump float; uniform sampler2D uTex; varying vec2 vUV;
            void main(){ vec4 c=texture2D(uTex,vUV); if(c.a<0.08) discard; gl_FragColor=c; }
        """
        program = G.glCreateProgram()
        G.glAttachShader(program, shader(G.GL_VERTEX_SHADER, vs))
        G.glAttachShader(program, shader(G.GL_FRAGMENT_SHADER, fs))
        G.glLinkProgram(program)
        val status = IntArray(1); G.glGetProgramiv(program, G.GL_LINK_STATUS, status, 0)
        if (status[0] == 0) { G.glDeleteProgram(program); program = 0; bmp.recycle(); return }
        aPos = G.glGetAttribLocation(program, "aPos")
        aUv = G.glGetAttribLocation(program, "aUV")
        uVp = G.glGetUniformLocation(program, "uVP")
        uModel = G.glGetUniformLocation(program, "uModel")
        uTex = G.glGetUniformLocation(program, "uTex")
        val ids = IntArray(1); G.glGenTextures(1, ids, 0); texture = ids[0]
        G.glBindTexture(G.GL_TEXTURE_2D, texture)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MIN_FILTER, G.GL_NEAREST)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_MAG_FILTER, G.GL_NEAREST)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_S, G.GL_CLAMP_TO_EDGE)
        G.glTexParameteri(G.GL_TEXTURE_2D, G.GL_TEXTURE_WRAP_T, G.GL_CLAMP_TO_EDGE)
        GLUtils.texImage2D(G.GL_TEXTURE_2D, 0, bmp, 0)
        bmp.recycle()
        active = texture != 0
    }

    fun isActive(): Boolean = active

    fun draw(vp: FloatArray, root: FloatArray, phase: Float, moving: Float, running: Float) {
        if (!active || program == 0) return
        val blendWasEnabled = G.glIsEnabled(G.GL_BLEND)
        G.glEnable(G.GL_BLEND)
        G.glBindBuffer(G.GL_ARRAY_BUFFER, 0); G.glBindBuffer(G.GL_ELEMENT_ARRAY_BUFFER, 0)
        G.glUseProgram(program)
        G.glUniformMatrix4fv(uVp, 1, false, vp, 0)
        G.glActiveTexture(G.GL_TEXTURE0); G.glBindTexture(G.GL_TEXTURE_2D, texture)
        G.glUniform1i(uTex, 0)
        val swing = sin(phase) * (if (running > .5f) 48f else 30f) * moving
        drawPart(head, root, 0f, 1.625f, 0f, 0f, 0f)
        drawPart(torso, root, 0f, 1.0f, 0f, 0f, 0f)
        drawLimb(rightArm, root, -.375f, 1.375f, 0f, swing)
        drawLimb(leftArm, root, .375f, 1.375f, 0f, -swing)
        drawLimb(rightLeg, root, -.125f, .75f, 0f, -swing)
        drawLimb(leftLeg, root, .125f, .75f, 0f, swing)
        drawPart(headOuter, root, 0f, 1.625f, 0f, 0f, 0f)
        drawPart(torsoOuter, root, 0f, 1.0f, 0f, 0f, 0f)
        drawLimb(rightArmOuter, root, -.375f, 1.375f, 0f, swing)
        drawLimb(leftArmOuter, root, .375f, 1.375f, 0f, -swing)
        drawLimb(rightLegOuter, root, -.125f, .75f, 0f, -swing)
        drawLimb(leftLegOuter, root, .125f, .75f, 0f, swing)
        if (!blendWasEnabled) G.glDisable(G.GL_BLEND)
    }

    private fun drawLimb(p: Part, root: FloatArray, x: Float, pivotY: Float, z: Float, angle: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, pivotY, z)
        Matrix.rotateM(model, 0, angle, 1f, 0f, 0f)
        Matrix.translateM(model, 0, 0f, -p.h * .5f, 0f)
        Matrix.scaleM(model, 0, p.w, p.h, p.d)
        Matrix.multiplyMM(temp, 0, root, 0, model, 0)
        drawGeometry(p, temp)
    }

    private fun drawPart(p: Part, root: FloatArray, x: Float, y: Float, z: Float, rx: Float, ry: Float) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        Matrix.rotateM(model, 0, ry, 0f, 1f, 0f)
        Matrix.rotateM(model, 0, rx, 1f, 0f, 0f)
        Matrix.scaleM(model, 0, p.w, p.h, p.d)
        Matrix.multiplyMM(temp, 0, root, 0, model, 0)
        drawGeometry(p, temp)
    }

    private fun drawGeometry(p: Part, matrix: FloatArray) {
        val pos = floatArrayOf(
            -.5f,-.5f,.5f,  .5f,-.5f,.5f,  .5f,.5f,.5f,  -.5f,.5f,.5f,
            .5f,-.5f,-.5f, -.5f,-.5f,-.5f, -.5f,.5f,-.5f, .5f,.5f,-.5f,
            -.5f,-.5f,-.5f, -.5f,-.5f,.5f, -.5f,.5f,.5f, -.5f,.5f,-.5f,
            .5f,-.5f,.5f, .5f,-.5f,-.5f, .5f,.5f,-.5f, .5f,.5f,.5f,
            -.5f,.5f,.5f, .5f,.5f,.5f, .5f,.5f,-.5f, -.5f,.5f,-.5f,
            -.5f,-.5f,-.5f, .5f,-.5f,-.5f, .5f,-.5f,.5f, -.5f,-.5f,.5f
        )
        vertices.clear()
        for (face in 0 until 6) {
            val r = p.faces[face]
            val u0 = r[0] / 64f; val u1 = (r[0] + r[2]) / 64f
            val vTop = 1f - r[1] / 64f; val vBottom = 1f - (r[1] + r[3]) / 64f
            val uv = floatArrayOf(u0,vBottom, u1,vBottom, u1,vTop, u0,vTop)
            val order = intArrayOf(0,1,2,0,2,3)
            for (k in order) {
                vertices.put(pos[face*12+k*3]); vertices.put(pos[face*12+k*3+1]); vertices.put(pos[face*12+k*3+2])
                vertices.put(uv[k*2]); vertices.put(uv[k*2+1])
            }
        }
        vertices.position(0)
        G.glUniformMatrix4fv(uModel, 1, false, matrix, 0)
        vertices.position(0)
        G.glEnableVertexAttribArray(aPos); G.glEnableVertexAttribArray(aUv)
        G.glVertexAttribPointer(aPos, 3, G.GL_FLOAT, false, 20, vertices)
        vertices.position(3)
        G.glVertexAttribPointer(aUv, 2, G.GL_FLOAT, false, 20, vertices)
        vertices.position(0)
        G.glDrawArrays(G.GL_TRIANGLES, 0, 36)
    }

    private fun shader(type: Int, source: String): Int {
        val id = G.glCreateShader(type)
        G.glShaderSource(id, source); G.glCompileShader(id)
        val status = IntArray(1); G.glGetShaderiv(id, G.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) { G.glDeleteShader(id); return 0 }
        return id
    }
}
