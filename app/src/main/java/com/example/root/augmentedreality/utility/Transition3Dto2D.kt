package com.example.root.augmentedreality.utility

import android.opengl.GLES20
import android.opengl.Matrix
import com.vuforia.Matrix34F
import com.vuforia.Tool
import com.vuforia.Vec4F

/**
 * Created by root on 10/8/17.
 */
class Transition3Dto2D(screenWidth: Int, screenHeight: Int,
                       isPortraitMode: Boolean, private val dpiScaleIndicator: Float,
                       private val scaleFactor: Float, private val mPlane: CustomImagePlane)
{
    private var isActivityPortraitMode: Boolean = false
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0
    private var screenRect: Vec4F? = null
    private val identityMatrix = FloatArray(16)
    private val orthoMatrix = FloatArray(16)

    private var shaderProgramID: Int = 0
    private var normalHandle: Int = 0
    private var vertexHandle: Int = 0
    private var textureCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private var animationLength: Float = 0.toFloat()
    private var animationDirection: Int = 0
    private var animationStartTime: Long = 0
    private var animationFinished: Boolean = false


    init
    {
        identityMatrix[0] = 1.0f
        identityMatrix[1] = 0.0f
        identityMatrix[2] = 0.0f
        identityMatrix[3] = 0.0f
        identityMatrix[4] = 0.0f
        identityMatrix[5] = 1.0f
        identityMatrix[6] = 0.0f
        identityMatrix[7] = 0.0f
        identityMatrix[8] = 0.0f
        identityMatrix[9] = 0.0f
        identityMatrix[10] = 1.0f
        identityMatrix[11] = 0.0f
        identityMatrix[12] = 0.0f
        identityMatrix[13] = 0.0f
        identityMatrix[14] = 0.0f
        identityMatrix[15] = 1.0f
        updateScreenProperties(screenWidth, screenHeight, isPortraitMode)
    }


    fun initializeGL(sProgramID: Int)
    {
        shaderProgramID = sProgramID
        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition")
        normalHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexNormal")
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexTexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix")

        SampleUtils.checkGLError("Transition3Dto2D.initializeGL")
    }


    fun updateScreenProperties(screenWidth: Int, screenHeight: Int,
                               isPortraitMode: Boolean)
    {
        this.isActivityPortraitMode = isPortraitMode
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight

        screenRect = Vec4F(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat())

        for (i in 0..15)
            orthoMatrix[i] = 0.0f
        val nLeft = -screenWidth / 2.0f
        val nRight = screenWidth / 2.0f
        val nBottom = -screenHeight / 2.0f
        val nTop = screenHeight / 2.0f
        val nNear = -1.0f
        val nFar = 1.0f

        orthoMatrix[0] = 2.0f / (nRight - nLeft)
        orthoMatrix[5] = 2.0f / (nTop - nBottom)
        orthoMatrix[10] = 2.0f / (nNear - nFar)
        orthoMatrix[12] = -(nRight + nLeft) / (nRight - nLeft)
        orthoMatrix[13] = -(nTop + nBottom) / (nTop - nBottom)
        orthoMatrix[14] = (nFar + nNear) / (nFar - nNear)
        orthoMatrix[15] = 1.0f

    }


    fun setScreenRect(centerX: Int, centerY: Int, width: Int, height: Int)
    {
        screenRect = Vec4F(centerX.toFloat(), centerY.toFloat(), width.toFloat(), height.toFloat())
    }


    fun startTransition(duration: Float, inReverse: Boolean,
                        keepRendering: Boolean)
    {
        animationLength = duration
        animationDirection = if (inReverse) -1 else 1

        animationStartTime = currentTimeMS
        animationFinished = false
    }


    fun stepTransition(): Float
    {
        val timeElapsed = (currentTimeMS - animationStartTime) / 1000.0f

        var t = timeElapsed / animationLength
        if (t >= 1.0f) {
            t = 1.0f
            animationFinished = true
        }

        if (animationDirection == -1) {
            t = 1.0f - t
        }

        return t
    }


    fun render(mProjectionMatrix: FloatArray, targetPose: Matrix34F,
               texture1: Int)
    {
        val t = stepTransition()

        val modelViewProjectionTracked = FloatArray(16)
        val modelViewProjectionCurrent = FloatArray(16)
        val modelViewMatrixVuforia = Tool
                .convertPose2GLMatrix(targetPose)
        val modelViewMatrix = modelViewMatrixVuforia.data
        val finalPositionMatrix = finalPositionMatrix

        Matrix.scaleM(modelViewMatrix, 0, 430.0f * scaleFactor,
                430.0f * scaleFactor, 1.0f)
        Matrix.multiplyMM(modelViewProjectionTracked, 0, mProjectionMatrix, 0,
                modelViewMatrix, 0)

        var elapsedTransformationCurrent = 0.8f + 0.2f * t
        elapsedTransformationCurrent = deccelerate(elapsedTransformationCurrent)
        linearInterpolate(modelViewProjectionTracked, finalPositionMatrix,
                modelViewProjectionCurrent, elapsedTransformationCurrent)

        GLES20.glUseProgram(shaderProgramID)

        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false,
                0, mPlane.vertices)
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT,
                false, 0, mPlane.texCoords)

        GLES20.glEnableVertexAttribArray(vertexHandle)
        GLES20.glEnableVertexAttribArray(textureCoordHandle)
        GLES20.glEnable(GLES20.GL_BLEND)

        // Drawing Textured Plane
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture1)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                modelViewProjectionCurrent, 0)
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT,
                mPlane.getIndices())

        GLES20.glDisableVertexAttribArray(vertexHandle)
        GLES20.glDisableVertexAttribArray(textureCoordHandle)
        GLES20.glDisable(GLES20.GL_BLEND)

        SampleUtils.checkGLError("Transition3Dto2D.render")

    }


    fun transitionFinished(): Boolean
    {
        return animationFinished
    }


    val finalPositionMatrix34F: Matrix34F
        get()
        {
            val glFinalPositionMatrix = finalPositionMatrix
            val vuforiaFinalPositionMatrix = FloatArray(12)

            for (i in 0..3) {
                for (j in 0..2) {
                    vuforiaFinalPositionMatrix[i * 3 + j] = glFinalPositionMatrix[i * 4 + j]
                }
            }

            val finalPositionMatrix34F = Matrix34F()
            finalPositionMatrix34F.data = vuforiaFinalPositionMatrix
            return finalPositionMatrix34F
        }

    // Sometimes the screenWidth and screenHeight values
    // are not properly updated, so this workaround
    // ensures that it will work fine every time
    // General Multiplier for getting the final size of the
    // plane when the animation is finished
    // Multiplies the value depending on different screen
    // dpis to get the same final size across all devices
    // MDPI devices
    // HDPI devices
    // XHDPI devices
    // XXHDPI devices
    private val finalPositionMatrix: FloatArray
        get() {
            val tempValue: Int
            val aspectRatio: Float
            val viewport = FloatArray(4)
            GLES20.glGetFloatv(GLES20.GL_VIEWPORT, viewport, 0)
            if (isActivityPortraitMode) {
                if (screenWidth > screenHeight) {
                    tempValue = screenWidth
                    screenWidth = screenHeight
                    screenHeight = tempValue
                }

                aspectRatio = screenHeight.toFloat() / screenWidth.toFloat()
            } else {
                if (screenWidth < screenHeight) {
                    tempValue = screenWidth
                    screenWidth = screenHeight
                    screenHeight = tempValue
                }

                aspectRatio = screenWidth.toFloat() / screenHeight.toFloat()
            }

            val scaleFactorX = screenWidth / viewport[2]
            val scaleFactorY = screenHeight / viewport[3]
            var scaleMultiplierWidth = 1.3f
            var scaleMultiplierHeight = scaleMultiplierWidth / aspectRatio
            if (dpiScaleIndicator == 1f) {
                scaleMultiplierHeight *= 1.6f
                scaleMultiplierWidth *= 1.6f
            } else if (dpiScaleIndicator == 1.5f) {
                scaleMultiplierHeight *= 1.2f
                scaleMultiplierWidth *= 1.2f
            } else if (dpiScaleIndicator == 2.0f) {
                scaleMultiplierHeight *= 0.9f
                scaleMultiplierWidth *= 0.9f
            } else if (dpiScaleIndicator > 2.0f) {
                scaleMultiplierHeight *= 0.75f
                scaleMultiplierWidth *= 0.75f
            }

            val translateX = screenRect!!.data[0] * scaleFactorX
            val translateY = screenRect!!.data[1] * scaleFactorY

            val scaleX = screenRect!!.data[2] * scaleFactorX
            val scaleY = screenRect!!.data[3] * scaleFactorY

            val result = orthoMatrix.clone()
            Matrix.translateM(result, 0, translateX, translateY, 0.0f)

            if (isActivityPortraitMode)
                Matrix.scaleM(result, 0, scaleX * scaleMultiplierWidth, scaleY * scaleMultiplierHeight, 1.0f)
            else
                Matrix.scaleM(result, 0, scaleX * scaleMultiplierHeight, scaleY * scaleMultiplierWidth, 1.0f)

            return result
        }


    private fun deccelerate(`val`: Float): Float {
        return 1 - (1 - `val`) * (1 - `val`)
    }


    private fun linearInterpolate(start: FloatArray, end: FloatArray, current: FloatArray,
                                  elapsed: Float) {
        if (start.size != 16 || end.size != 16 || current.size != 16)
            return

        // ATTENTION: This is a plain matrix linear interpolation. It isn't
        // elegant but it gets the job done.
        // A better approach would be to interpolate the modelview and
        // projection matrices separately and to use some
        // sort of curve such as bezier
        for (i in 0..15)
            current[i] = (end[i] - start[i]) * elapsed + start[i]
    }


    private val currentTimeMS: Long
        get() = System.currentTimeMillis()

}
