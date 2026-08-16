package com.zernex.sim3d

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * OpenGL ES 2.0 Sims 3 style demo
 * Character built from spheres + cylinders (organic, not cubes)
 * Inspired by official Sims 3 character art / CAS
 */
class SimRenderer : GLSurfaceView.Renderer {

    var orbitYaw = 32f
    var orbitPitch = 12f

    private var time = 0f
    private var stateTime = 0f

    private enum class State { IDLE, WALK, SIT, STAND_UP }
    private var state = State.IDLE

    private var simX = 0.85f
    private var simZ = 1.15f
    private var simYaw = 180f
    private var targetX = -1.0f
    private var targetZ = 0.55f

    private val vPMatrix = FloatArray(16)
    private val projection = FloatArray(16)
    private val view = FloatArray(16)
    private val model = FloatArray(16)
    private val mvp = FloatArray(16)

    private var program = 0
    private var posHandle = 0
    private var colorHandle = 0
    private var mvpHandle = 0

    // Skin / hair / clothes matching classic Sims 3 tones
    private val skin = floatArrayOf(0.96f, 0.80f, 0.68f, 1f)
    private val skinShadow = floatArrayOf(0.88f, 0.70f, 0.58f, 1f)
    private val hair = floatArrayOf(0.12f, 0.09f, 0.07f, 1f)
    private val hairHighlight = floatArrayOf(0.22f, 0.16f, 0.12f, 1f)
    private val shirt = floatArrayOf(0.55f, 0.58f, 0.35f, 1f)          // olive/camo base
    private val shirtDark = floatArrayOf(0.38f, 0.36f, 0.24f, 1f)
    private val shirtLight = floatArrayOf(0.65f, 0.68f, 0.42f, 1f)
    private val pants = floatArrayOf(0.28f, 0.36f, 0.52f, 1f)          // classic blue jeans
    private val pantsDark = floatArrayOf(0.20f, 0.26f, 0.40f, 1f)
    private val shoes = floatArrayOf(0.08f, 0.08f, 0.10f, 1f)
    private val plumbob = floatArrayOf(0.45f, 0.95f, 0.18f, 1f)
    private val plumbobDark = floatArrayOf(0.18f, 0.42f, 0.06f, 1f)
    private val floorC = floatArrayOf(0.76f, 0.58f, 0.36f, 1f)
    private val wallC = floatArrayOf(0.42f, 0.52f, 0.68f, 1f)
    private val wallC2 = floatArrayOf(0.38f, 0.48f, 0.62f, 1f)
    private val wood = floatArrayOf(0.50f, 0.34f, 0.18f, 1f)
    private val cushion = floatArrayOf(0.20f, 0.30f, 0.48f, 1f)
    private val white = floatArrayOf(0.97f, 0.97f, 0.97f, 1f)
    private val iris = floatArrayOf(0.22f, 0.38f, 0.55f, 1f)
    private val lip = floatArrayOf(0.72f, 0.38f, 0.32f, 1f)

    private lateinit var cube: Mesh
    private lateinit var sphere: Mesh
    private lateinit var cylinder: Mesh

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.50f, 0.58f, 0.70f, 1f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        val vs = "uniform mat4 uMVP; attribute vec4 aPos; void main(){ gl_Position = uMVP * aPos; }"
        val fs = "precision mediump float; uniform vec4 uColor; void main(){ gl_FragColor = uColor; }"
        program = linkProgram(vs, fs)
        posHandle = GLES20.glGetAttribLocation(program, "aPos")
        colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        mvpHandle = GLES20.glGetUniformLocation(program, "uMVP")

        cube = Mesh.unitCube()
        sphere = Mesh.sphere(16, 12)      // smoother than before
        cylinder = Mesh.cylinder(14)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.perspectiveM(projection, 0, 40f, width.toFloat() / height.coerceAtLeast(1), 0.1f, 40f)
    }

    override fun onDrawFrame(gl: GL10?) {
        val dt = 0.016f
        time += dt
        stateTime += dt
        updateAI(dt)

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val radY = Math.toRadians(orbitYaw.toDouble()).toFloat()
        val radP = Math.toRadians(orbitPitch.toDouble()).toFloat()
        val dist = 4.4f
        val eyeX = dist * cos(radP) * sin(radY)
        val eyeY = 1.40f + dist * sin(radP) * 0.85f
        val eyeZ = dist * cos(radP) * cos(radY)
        Matrix.setLookAtM(view, 0, eyeX, eyeY, eyeZ, simX * 0.2f, 1.05f, simZ * 0.2f, 0f, 1f, 0f)
        Matrix.multiplyMM(vPMatrix, 0, projection, 0, view, 0)

        drawRoom()
        drawChair(-1.0f, -0.35f)
        drawSim()
    }

    private fun updateAI(dt: Float) {
        when (state) {
            State.IDLE -> {
                if (stateTime > 1.8f) {
                    state = State.WALK
                    stateTime = 0f
                    targetX = -1.0f
                    targetZ = 0.55f
                }
            }
            State.WALK -> {
                val dx = targetX - simX
                val dz = targetZ - simZ
                val dist = sqrt(dx * dx + dz * dz)
                if (dist < 0.07f) {
                    simX = targetX
                    simZ = targetZ
                    simYaw = 180f
                    state = State.SIT
                    stateTime = 0f
                } else {
                    val speed = 0.95f
                    simX += dx / dist * speed * dt
                    simZ += dz / dist * speed * dt
                    simYaw = Math.toDegrees(atan2(dx.toDouble(), dz.toDouble())).toFloat()
                }
            }
            State.SIT -> {
                if (stateTime > 4.2f) {
                    state = State.STAND_UP
                    stateTime = 0f
                }
            }
            State.STAND_UP -> {
                if (stateTime > 0.65f) {
                    targetX = 0.80f
                    targetZ = 1.10f
                    state = State.WALK
                    stateTime = 0f
                }
            }
        }
    }

    private fun drawRoom() {
        drawShape(cube, 0f, 0f, 0f, 5.6f, 0.05f, 5.6f, 0f, floorC)
        drawShape(cube, 0f, 1.70f, -2.65f, 5.6f, 3.4f, 0.08f, 0f, wallC)
        drawShape(cube, -2.75f, 1.70f, 0f, 0.08f, 3.4f, 5.6f, 0f, wallC2)
        drawShape(cube, 2.75f, 1.70f, 0f, 0.08f, 3.4f, 5.6f, 0f, wallC2)
        // window
        drawShape(cube, -0.15f, 1.45f, -2.58f, 1.15f, 1.7f, 0.05f, 0f, floatArrayOf(0.72f, 0.74f, 0.78f, 1f))
        drawShape(cube, -0.15f, 1.45f, -2.54f, 1.00f, 1.55f, 0.04f, 0f, floatArrayOf(0.48f, 0.58f, 0.72f, 1f))
    }

    private fun drawChair(cx: Float, cz: Float) {
        val legH = 0.40f
        val legT = 0.065f
        drawShape(cube, cx - 0.22f, legH / 2f, cz - 0.22f, legT, legH, legT, 0f, wood)
        drawShape(cube, cx + 0.22f, legH / 2f, cz - 0.22f, legT, legH, legT, 0f, wood)
        drawShape(cube, cx - 0.22f, legH / 2f, cz + 0.22f, legT, legH, legT, 0f, wood)
        drawShape(cube, cx + 0.22f, legH / 2f, cz + 0.22f, legT, legH, legT, 0f, wood)
        drawShape(cube, cx, legH + 0.04f, cz, 0.50f, 0.07f, 0.50f, 0f, wood)
        drawShape(cube, cx, legH + 0.08f, cz, 0.46f, 0.04f, 0.46f, 0f, cushion)
        drawShape(cube, cx, legH + 0.42f, cz - 0.22f, 0.48f, 0.52f, 0.06f, 0f, wood)
        drawShape(cube, cx, legH + 0.42f, cz - 0.18f, 0.40f, 0.40f, 0.04f, 0f, cushion)
    }

    private fun drawSim() {
        val sitting = state == State.SIT || (state == State.STAND_UP && stateTime < 0.38f)
        val walking = state == State.WALK

        val walkPhase = if (walking) time * 8.5f else 0f
        val legSwing = if (walking) sin(walkPhase) * 0.38f else 0f
        val armSwing = if (walking) sin(walkPhase) * 0.30f else 0f
        val bob = if (walking) kotlin.math.abs(sin(walkPhase)) * 0.035f
                  else 0.012f * sin(time * 2.0f)
        val bodyY = if (sitting) 0.50f else 0f
        val yawRad = Math.toRadians(simYaw.toDouble()).toFloat()

        fun localShape(
            mesh: Mesh,
            lx: Float, ly: Float, lz: Float,
            sx: Float, sy: Float, sz: Float,
            color: FloatArray,
            rotX: Float = 0f
        ) {
            val cosY = cos(yawRad)
            val sinY = sin(yawRad)
            val wx = simX + lx * cosY + lz * sinY
            val wz = simZ - lx * sinY + lz * cosY
            val wy = ly + bodyY + bob

            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, wx, wy, wz)
            Matrix.rotateM(model, 0, simYaw, 0f, 1f, 0f)
            if (rotX != 0f) Matrix.rotateM(model, 0, rotX, 1f, 0f, 0f)
            Matrix.scaleM(model, 0, sx, sy, sz)
            Matrix.multiplyMM(mvp, 0, vPMatrix, 0, model, 0)
            GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
            GLES20.glUniform4fv(colorHandle, 1, color, 0)
            mesh.draw(posHandle)
        }

        if (sitting) {
            // Legs folded
            localShape(cylinder, -0.13f, 0.40f, 0.12f, 0.095f, 0.11f, 0.40f, pants, 78f)
            localShape(cylinder,  0.13f, 0.40f, 0.12f, 0.095f, 0.11f, 0.40f, pants, 78f)
            localShape(cylinder, -0.13f, 0.18f, 0.36f, 0.085f, 0.28f, 0.085f, pants, 0f)
            localShape(cylinder,  0.13f, 0.18f, 0.36f, 0.085f, 0.28f, 0.085f, pants, 0f)
            localShape(sphere,   -0.13f, 0.05f, 0.42f, 0.12f, 0.07f, 0.17f, shoes)
            localShape(sphere,    0.13f, 0.05f, 0.42f, 0.12f, 0.07f, 0.17f, shoes)

            // Torso (more Sims-like volume)
            localShape(sphere, 0f, 0.78f, 0.04f, 0.30f, 0.38f, 0.20f, shirt)
            localShape(sphere, 0.06f, 0.82f, 0.15f, 0.09f, 0.09f, 0.04f, shirtDark)
            localShape(sphere, -0.07f, 0.72f, 0.15f, 0.07f, 0.07f, 0.04f, shirtLight)

            // Arms
            localShape(cylinder, -0.34f, 0.80f, 0.10f, 0.07f, 0.07f, 0.34f, skin, 45f)
            localShape(cylinder,  0.34f, 0.80f, 0.10f, 0.07f, 0.07f, 0.34f, skin, 45f)
            localShape(sphere,   -0.40f, 0.60f, 0.26f, 0.075f, 0.075f, 0.075f, skinShadow)
            localShape(sphere,    0.40f, 0.60f, 0.26f, 0.075f, 0.075f, 0.075f, skinShadow)

            // Head + face (Sims proportions)
            localShape(cylinder, 0f, 1.18f, 0.03f, 0.065f, 0.10f, 0.065f, skin) // neck
            localShape(sphere,   0f, 1.40f, 0.04f, 0.23f, 0.26f, 0.23f, skin)   // head
            // Hair volume
            localShape(sphere, 0f, 1.52f, 0.01f, 0.255f, 0.13f, 0.255f, hair)
            localShape(sphere, 0f, 1.46f, -0.10f, 0.21f, 0.12f, 0.14f, hair)
            localShape(sphere, -0.14f, 1.44f, 0.02f, 0.10f, 0.10f, 0.08f, hair)
            localShape(sphere,  0.14f, 1.44f, 0.02f, 0.10f, 0.10f, 0.08f, hair)
            // Ears
            localShape(sphere, -0.22f, 1.40f, 0.03f, 0.045f, 0.06f, 0.035f, skinShadow)
            localShape(sphere,  0.22f, 1.40f, 0.03f, 0.045f, 0.06f, 0.035f, skinShadow)
            // Nose
            localShape(sphere, 0f, 1.38f, 0.22f, 0.035f, 0.035f, 0.045f, skinShadow)
            // Eyes
            localShape(sphere, -0.085f, 1.44f, 0.20f, 0.048f, 0.038f, 0.025f, white)
            localShape(sphere,  0.085f, 1.44f, 0.20f, 0.048f, 0.038f, 0.025f, white)
            localShape(sphere, -0.085f, 1.44f, 0.22f, 0.028f, 0.028f, 0.018f, iris)
            localShape(sphere,  0.085f, 1.44f, 0.22f, 0.028f, 0.028f, 0.018f, iris)
            // Mouth
            localShape(sphere, 0f, 1.30f, 0.21f, 0.055f, 0.018f, 0.025f, lip)

            drawPlumbobAt(simX, 1.95f, simZ)
        } else {
            // Standing / walking – classic Sims proportions
            val legAmt = legSwing
            val armAmt = armSwing

            // Legs
            localShape(cylinder, -0.12f, 0.40f, 0.05f * sin(legAmt), 0.095f, 0.58f, 0.095f, pants, legAmt * 42f)
            localShape(cylinder,  0.12f, 0.40f, -0.05f * sin(legAmt), 0.095f, 0.58f, 0.095f, pants, -legAmt * 42f)
            // Lower leg / shin slight darker
            localShape(cylinder, -0.12f, 0.14f, 0.07f * sin(legAmt), 0.085f, 0.18f, 0.085f, pantsDark, legAmt * 20f)
            localShape(cylinder,  0.12f, 0.14f, -0.07f * sin(legAmt), 0.085f, 0.18f, 0.085f, pantsDark, -legAmt * 20f)
            // Shoes
            localShape(sphere, -0.12f, 0.06f, 0.09f * sin(legAmt), 0.12f, 0.07f, 0.18f, shoes)
            localShape(sphere,  0.12f, 0.06f, -0.09f * sin(legAmt), 0.12f, 0.07f, 0.18f, shoes)

            // Hips / waist
            localShape(sphere, 0f, 0.78f, 0.0f, 0.26f, 0.14f, 0.16f, pants)

            // Torso (shirt)
            localShape(sphere, 0f, 1.02f, 0.02f, 0.29f, 0.36f, 0.19f, shirt)
            // Camo detail spots
            localShape(sphere, 0.07f, 1.05f, 0.14f, 0.09f, 0.09f, 0.04f, shirtDark)
            localShape(sphere, -0.08f, 0.95f, 0.14f, 0.07f, 0.07f, 0.04f, shirtLight)
            localShape(sphere, 0.02f, 0.90f, 0.12f, 0.06f, 0.05f, 0.03f, shirtDark)

            // Arms
            localShape(cylinder, -0.34f, 0.98f, 0.04f * sin(armAmt), 0.07f, 0.48f, 0.07f, skin, armAmt * -42f)
            localShape(cylinder,  0.34f, 0.98f, -0.04f * sin(armAmt), 0.07f, 0.48f, 0.07f, skin, armAmt * 42f)
            localShape(sphere,   -0.36f, 0.60f, 0.07f * sin(armAmt), 0.075f, 0.075f, 0.075f, skinShadow)
            localShape(sphere,    0.36f, 0.60f, -0.07f * sin(armAmt), 0.075f, 0.075f, 0.075f, skinShadow)

            // Neck
            localShape(cylinder, 0f, 1.28f, 0.01f, 0.065f, 0.11f, 0.065f, skin)

            // Head
            localShape(sphere, 0f, 1.52f, 0.03f, 0.23f, 0.26f, 0.23f, skin)
            // Hair (short black – classic Sims)
            localShape(sphere, 0f, 1.64f, 0.00f, 0.255f, 0.13f, 0.255f, hair)
            localShape(sphere, 0f, 1.58f, -0.10f, 0.22f, 0.12f, 0.14f, hair)
            localShape(sphere, -0.15f, 1.56f, 0.02f, 0.10f, 0.10f, 0.08f, hair)
            localShape(sphere,  0.15f, 1.56f, 0.02f, 0.10f, 0.10f, 0.08f, hair)
            localShape(sphere, 0f, 1.60f, 0.05f, 0.18f, 0.08f, 0.12f, hairHighlight)
            // Ears
            localShape(sphere, -0.22f, 1.52f, 0.02f, 0.045f, 0.06f, 0.035f, skinShadow)
            localShape(sphere,  0.22f, 1.52f, 0.02f, 0.045f, 0.06f, 0.035f, skinShadow)
            // Nose
            localShape(sphere, 0f, 1.50f, 0.22f, 0.035f, 0.035f, 0.045f, skinShadow)
            // Eyes
            localShape(sphere, -0.085f, 1.56f, 0.20f, 0.048f, 0.038f, 0.025f, white)
            localShape(sphere,  0.085f, 1.56f, 0.20f, 0.048f, 0.038f, 0.025f, white)
            localShape(sphere, -0.085f, 1.56f, 0.22f, 0.028f, 0.028f, 0.018f, iris)
            localShape(sphere,  0.085f, 1.56f, 0.22f, 0.028f, 0.028f, 0.018f, iris)
            // Mouth
            localShape(sphere, 0f, 1.42f, 0.21f, 0.055f, 0.018f, 0.025f, lip)

            drawPlumbobAt(simX, 2.15f + bob, simZ)
        }
    }

    private fun drawPlumbobAt(x: Float, baseY: Float, z: Float = simZ) {
        val bobY = baseY + 0.045f * sin(time * 2.8f)
        // Outer diamond
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, bobY, z)
        Matrix.rotateM(model, 0, 45f, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, 45f + time * 28f, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, 0.14f, 0.23f, 0.14f)
        Matrix.multiplyMM(mvp, 0, vPMatrix, 0, model, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform4fv(colorHandle, 1, plumbob, 0)
        cube.draw(posHandle)

        // Inner darker
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, bobY, z)
        Matrix.rotateM(model, 0, 45f, 1f, 0f, 0f)
        Matrix.rotateM(model, 0, 45f + time * 28f, 0f, 1f, 0f)
        Matrix.scaleM(model, 0, 0.09f, 0.15f, 0.09f)
        Matrix.multiplyMM(mvp, 0, vPMatrix, 0, model, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform4fv(colorHandle, 1, plumbobDark, 0)
        cube.draw(posHandle)
    }

    private fun drawShape(
        mesh: Mesh,
        x: Float, y: Float, z: Float,
        sx: Float, sy: Float, sz: Float,
        rotX: Float,
        color: FloatArray
    ) {
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, x, y, z)
        if (rotX != 0f) Matrix.rotateM(model, 0, rotX, 1f, 0f, 0f)
        Matrix.scaleM(model, 0, sx, sy, sz)
        Matrix.multiplyMM(mvp, 0, vPMatrix, 0, model, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvp, 0)
        GLES20.glUniform4fv(colorHandle, 1, color, 0)
        mesh.draw(posHandle)
    }

    private fun linkProgram(vs: String, fs: String): Int {
        fun sh(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            return s
        }
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, sh(GLES20.GL_VERTEX_SHADER, vs))
        GLES20.glAttachShader(p, sh(GLES20.GL_FRAGMENT_SHADER, fs))
        GLES20.glLinkProgram(p)
        return p
    }
}

class Mesh(private val buf: FloatBuffer, private val vertexCount: Int) {
    fun draw(posHandle: Int) {
        buf.position(0)
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    companion object {
        fun unitCube(): Mesh {
            val v = floatArrayOf(
                -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f,
                -0.5f, -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
                0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
                0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f,
                0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f,
                0.5f, -0.5f, 0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f, 0.5f,
                -0.5f, -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f,
                -0.5f, -0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, -0.5f,
                -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f,
                -0.5f, 0.5f, 0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f,
                -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f,
                -0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f
            )
            val fb = ByteBuffer.allocateDirect(v.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(v)
            fb.position(0)
            return Mesh(fb, v.size / 3)
        }

        fun sphere(segments: Int, rings: Int): Mesh {
            val verts = mutableListOf<Float>()
            for (y in 0 until rings) {
                val v0 = y.toFloat() / rings
                val v1 = (y + 1).toFloat() / rings
                val y0 = cos(PI * v0).toFloat()
                val y1 = cos(PI * v1).toFloat()
                val r0 = sin(PI * v0).toFloat()
                val r1 = sin(PI * v1).toFloat()
                for (x in 0 until segments) {
                    val u0 = x.toFloat() / segments
                    val u1 = (x + 1).toFloat() / segments
                    val x00 = cos(u0 * 2 * PI).toFloat() * r0
                    val z00 = sin(u0 * 2 * PI).toFloat() * r0
                    val x10 = cos(u1 * 2 * PI).toFloat() * r0
                    val z10 = sin(u1 * 2 * PI).toFloat() * r0
                    val x01 = cos(u0 * 2 * PI).toFloat() * r1
                    val z01 = sin(u0 * 2 * PI).toFloat() * r1
                    val x11 = cos(u1 * 2 * PI).toFloat() * r1
                    val z11 = sin(u1 * 2 * PI).toFloat() * r1
                    verts.addAll(listOf(x00 * 0.5f, y0 * 0.5f, z00 * 0.5f))
                    verts.addAll(listOf(x10 * 0.5f, y0 * 0.5f, z10 * 0.5f))
                    verts.addAll(listOf(x11 * 0.5f, y1 * 0.5f, z11 * 0.5f))
                    verts.addAll(listOf(x00 * 0.5f, y0 * 0.5f, z00 * 0.5f))
                    verts.addAll(listOf(x11 * 0.5f, y1 * 0.5f, z11 * 0.5f))
                    verts.addAll(listOf(x01 * 0.5f, y1 * 0.5f, z01 * 0.5f))
                }
            }
            val arr = verts.toFloatArray()
            val fb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(arr)
            fb.position(0)
            return Mesh(fb, arr.size / 3)
        }

        fun cylinder(segments: Int): Mesh {
            val verts = mutableListOf<Float>()
            for (i in 0 until segments) {
                val u0 = i.toFloat() / segments
                val u1 = (i + 1).toFloat() / segments
                val x0 = cos(u0 * 2 * PI).toFloat() * 0.5f
                val z0 = sin(u0 * 2 * PI).toFloat() * 0.5f
                val x1 = cos(u1 * 2 * PI).toFloat() * 0.5f
                val z1 = sin(u1 * 2 * PI).toFloat() * 0.5f
                verts.addAll(listOf(x0, -0.5f, z0, x1, -0.5f, z1, x1, 0.5f, z1))
                verts.addAll(listOf(x0, -0.5f, z0, x1, 0.5f, z1, x0, 0.5f, z0))
            }
            val arr = verts.toFloatArray()
            val fb = ByteBuffer.allocateDirect(arr.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().put(arr)
            fb.position(0)
            return Mesh(fb, arr.size / 3)
        }
    }
}
