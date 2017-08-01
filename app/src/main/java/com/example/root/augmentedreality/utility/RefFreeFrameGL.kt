package com.example.root.augmentedreality.utility

import android.content.res.Configuration
import android.opengl.GLES20
import android.opengl.Matrix
import android.util.Log
import com.example.root.augmentedreality.activity.UserDefinedTargetsActivity
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.vuforia.Matrix44F
import com.vuforia.Renderer
import com.vuforia.Vec4F
import com.vuforia.VideoBackgroundConfig
import java.nio.Buffer
import java.nio.ByteBuffer
import java.nio.ByteOrder


class RefFreeFrameGL(activity: UserDefinedTargetsActivity, session: ApplicationSession)
{
    private val TAG: String = "RefFreeFrameGL"

    private var mActivity: UserDefinedTargetsActivity

    private var mVuforiaAppSession: ApplicationSession

    private class TEXTURE_NAME
    {
        internal val TEXTURE_VIEWFINDER_MARKS_PORTRAIT = 0
        internal val TEXTURE_VIEWFINDER_MARKS = 1
        internal val TEXTURE_COUNT = 2
    }

    private var shaderProgramID: Int = 0
    private var vertexHandle: Int = 0
    private var textureCoordHandle: Int = 0
    private var colorHandle: Int = 0
    private var mvpMatrixHandle: Int = 0

    private lateinit var projectionOrtho: Matrix44F
    private lateinit var modelView: Matrix44F

    private lateinit var color:Vec4F

    private val textureNames: Array<String> = arrayOf(
            "UserDefinedTargets/viewfinder_crop_marks_portrait.png",
            "UserDefinedTargets/viewfinder_crop_marks_landscape.png" )

    private lateinit var textures: Array<Texture?>

    val NUM_FRAME_VERTEX_TOTAL = 4
    val NUM_FRAME_INDEX = 1 + NUM_FRAME_VERTEX_TOTAL

    var frameVertices_viewfinder = FloatArray(NUM_FRAME_VERTEX_TOTAL * 3)
    var frameTexCoords = FloatArray(NUM_FRAME_VERTEX_TOTAL * 2)
    var frameIndices = ShortArray(NUM_FRAME_INDEX)

    var isActivityPortrait: Boolean = false

    var frameVertexShader: String = " \n" + "attribute vec4 vertexPosition; \n" +
            "attribute vec2 vertexTexCoord; \n" + "\n" +
            "varying vec2 texCoord; \n" + "\n" +
            "uniform mat4 modelViewProjectionMatrix; \n" + "\n"+
            "void main() \n" + "{ \n"+
            "gl_Position = modelViewProjectionMatrix * vertexPosition; \n"+
            "texCoord = vertexTexCoord; \n" + "} \n"

    var frameFragmentShader: String = " \n" + "precision mediump float; \n" + "\n" +
            "varying vec2 texCoord; \n" + "\n" +
            "uniform sampler2D texSampler2D; \n" + "uniform vec4 keyColor; \n" +
            "\n" + "void main() \n" + "{ \n" +
            "vec4 texColor = texture2D(texSampler2D, texCoord); \n" +
            "gl_FragColor = keyColor * texColor; \n" + "} \n" + ""

    init
    {
        mActivity = activity
        mVuforiaAppSession = session

        Log.d(TAG,"RefFreeFrameGL Constructor")

        textures = Array(TEXTURE_NAME().TEXTURE_COUNT,{ i -> null })

        color = Vec4F()

    }

    private fun setColor(r: Float, g: Float, b:Float, a:Float)
    {
        val tempColor: FloatArray = floatArrayOf(r, g, b, a)
        color.data = tempColor
    }

    fun setColor(c: FloatArray)
    {
        if(c.size != 4)
        {
            throw IllegalArgumentException("Color length must be 4 floats length")
        }
        color.data = c
    }

    fun setModelViewScale(scale: Float)
    {
        modelView.data[14] = scale
    }

    fun init(screenWidth: Int, screenHeight: Int) : Boolean
    {
        val tempMatrix44Array: FloatArray = kotlin.FloatArray(16, {i -> 0f})

        modelView = Matrix44F()

        tempMatrix44Array[0] = 1.0f
        tempMatrix44Array[5] = 1.0f
        tempMatrix44Array[10] = 1.0f
        tempMatrix44Array[15] = 1.0f

        val tempColor: FloatArray = floatArrayOf(1.0f, 1.0f, 1.0f, 6.0f)
        color.data = tempColor

        val config: Configuration = mActivity.resources.configuration


        isActivityPortrait = (config.orientation == Configuration.ORIENTATION_PORTRAIT)

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(frameVertexShader,frameFragmentShader)

        if(shaderProgramID == 0) return false

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,"vertexPosition")

        if(vertexHandle == -1) return false

        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,"vertexTexCoord")

        if(textureCoordHandle == -1) return false

        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,"modelViewProjectionMatrix")

        if(mvpMatrixHandle == -1)return false

        colorHandle = GLES20.glGetUniformLocation(shaderProgramID,"keyColor")

        if(colorHandle == -1) return false

        val renderer: Renderer = Renderer.getInstance()
        val videoBackgroundConfig : VideoBackgroundConfig = renderer.videoBackgroundConfig

        projectionOrtho = Matrix44F()

        val tempVC: IntArray = videoBackgroundConfig.size.data

        if(tempVC[0] == 0)
        {
            return false
        }
        tempMatrix44Array[0] = 2.0f / (tempVC[0]).toFloat()
        tempMatrix44Array[5] = 2.0f / (tempVC[1]).toFloat()
        tempMatrix44Array[10] = 1.0f / (-10.0f)
        tempMatrix44Array[11] = -5.0f / (-10.0f)
        tempMatrix44Array[15] = 1.0f

        val sizeH_viewFinder: Float = (screenWidth / tempVC[0]).toFloat() * (2.0f / tempMatrix44Array[0])
        val sizeV_viewFinder: Float = (screenHeight / tempVC[1]).toFloat() * (2.0f / tempMatrix44Array[5])

        Log.d(TAG, "Viewfinder Size:  $sizeH_viewFinder $sizeV_viewFinder")

        var cnt: Int = 0
        var tCnt: Int = 0

        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeH_viewFinder
        frameVertices_viewfinder[cnt++] = (1.0f) * sizeV_viewFinder
        frameVertices_viewfinder[cnt++] = 0.0f
        frameTexCoords[tCnt++] = 0.0f
        frameTexCoords[tCnt++] = 1.0f

        frameVertices_viewfinder[cnt++] = (1.0f) * sizeH_viewFinder
        frameVertices_viewfinder[cnt++] = (1.0f) * sizeV_viewFinder
        frameVertices_viewfinder[cnt++] = 0.0f
        frameTexCoords[tCnt++] = 1.0f
        frameTexCoords[tCnt++] = 1.0f

        frameVertices_viewfinder[cnt++] = (1.0f) * sizeH_viewFinder
        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeV_viewFinder
        frameVertices_viewfinder[cnt++] = 0.0f
        frameTexCoords[tCnt++] = 1.0f
        frameTexCoords[tCnt++] = 0.0f

        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeH_viewFinder
        frameVertices_viewfinder[cnt++] = (-1.0f) * sizeV_viewFinder
        frameVertices_viewfinder[cnt] = 0.0f
        frameTexCoords[tCnt++] = 0.0f
        frameTexCoords[tCnt] = 0.0f
        cnt = 0

        for (i in 0..NUM_FRAME_VERTEX_TOTAL - 1)
            frameIndices[cnt++] = i.toShort()
        frameIndices[cnt] = 0

        // loads the texture
        for (t in textures) {
            if (t != null) {
                GLES20.glGenTextures(1, t.mTextureID, 0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0])
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
                GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                        t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                        GLES20.GL_UNSIGNED_BYTE, t.mData)
            }
        }

        return true

    }

    private fun fillBuffer(array: FloatArray): Buffer {
        // Convert to floats because OpenGL doesnt work on doubles, and manually
        // casting each input value would take too much time.
        val bb = ByteBuffer.allocateDirect(4 * array.size) // each
        // float
        // takes 4
        // bytes
        bb.order(ByteOrder.LITTLE_ENDIAN)
        for (d in array)
            bb.putFloat(d.toFloat())
        bb.rewind()

        return bb
    }


    private fun fillBuffer(array: ShortArray): Buffer {
        val bb = ByteBuffer.allocateDirect(2 * array.size) // each
        // short
        // takes 2
        // bytes
        bb.order(ByteOrder.LITTLE_ENDIAN)
        for (s in array)
            bb.putShort(s)
        bb.rewind()

        return bb

    }


    fun getTextures() {
        for (i in 0..TEXTURE_NAME().TEXTURE_COUNT - 1)
            textures[i] = mActivity.createTexture(textureNames[i])
    }


    // / Renders the viewfinder
    fun renderViewfinder() {
        if (textures == null)
            return

        // Set GL flags
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)

        // Set the shader program
        GLES20.glUseProgram(shaderProgramID)

        // Calculate the Projection * ModelView matrix and pass to shader
        val mvp = FloatArray(16)
        Matrix.multiplyMM(mvp, 0, projectionOrtho.data, 0, modelView.getData(), 0)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvp, 0)

        // Set the vertex handle
        val verticesBuffer = fillBuffer(frameVertices_viewfinder)
        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false,
                0, verticesBuffer)

        // Set the Texture coordinate handle
        val texCoordBuffer = fillBuffer(frameTexCoords)
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT,
                false, 0, texCoordBuffer)

        // Enable the Vertex and Texture arrays
        GLES20.glEnableVertexAttribArray(vertexHandle)
        GLES20.glEnableVertexAttribArray(textureCoordHandle)

        // Send the color value to the shader
        GLES20.glUniform4fv(colorHandle, 1, color.data, 0)

        // Depending on if we are in portrait or landsacape mode,
        // choose the proper viewfinder texture
        if (isActivityPortrait && textures[TEXTURE_NAME().TEXTURE_VIEWFINDER_MARKS_PORTRAIT] != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20
                    .glBindTexture(
                            GLES20.GL_TEXTURE_2D,
                            textures[TEXTURE_NAME().TEXTURE_VIEWFINDER_MARKS_PORTRAIT]!!.mTextureID[0])
        } else if (!isActivityPortrait && textures[TEXTURE_NAME().TEXTURE_VIEWFINDER_MARKS] != null) {
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                    textures[TEXTURE_NAME().TEXTURE_VIEWFINDER_MARKS]!!.mTextureID[0])
        }

        // Draw the viewfinder
        val indicesBuffer = fillBuffer(frameIndices)
        GLES20.glDrawElements(GLES20.GL_TRIANGLE_STRIP, NUM_FRAME_INDEX,
                GLES20.GL_UNSIGNED_SHORT, indicesBuffer)

        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

    }



}
