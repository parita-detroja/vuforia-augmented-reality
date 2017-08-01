package com.example.root.augmentedreality.renderer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.example.root.augmentedreality.activity.TextRecognitionActivity
import com.example.root.augmentedreality.utility.LineShaders
import com.example.root.augmentedreality.utility.SampleUtils
import com.example.root.augmentedreality.vuforia.AppRenderer
import com.example.root.augmentedreality.vuforia.AppRendererControl
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.vuforia.*
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Created by root on 26/5/17.
 */

class TextRecognitionRenderer(// Reference to main activity *
        var mTextRecognitionActivity: TextRecognitionActivity, private val vuforiaAppSession: ApplicationSession) : GLSurfaceView.Renderer, AppRendererControl {
    private val mAppRenderer: AppRenderer

    private var mROIVerts: ByteBuffer? = null
    private var mROIIndices: ByteBuffer? = null

    private var mIsActive = false

    private var shaderProgramID: Int = 0
    private var vertexHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var mRenderer: Renderer? = null
    private var lineOpacityHandle: Int = 0
    private var lineColorHandle: Int = 0

    private val mWords = ArrayList<WordDesc>()
    var ROICenterX: Float = 0.toFloat()
    var ROICenterY: Float = 0.toFloat()
    var ROIWidth: Float = 0.toFloat()
    var ROIHeight: Float = 0.toFloat()
    private var viewportPosition_x: Int = 0
    private var viewportPosition_y: Int = 0
    private var viewportSize_x: Int = 0
    private var viewportSize_y: Int = 0
    private var mQuadVerts: ByteBuffer? = null
    private var mQuadIndices: ByteBuffer? = null

    init {

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
        mAppRenderer = AppRenderer(this, mTextRecognitionActivity, Device.MODE.MODE_AR, false, 10f, 5000f)
    }


    // Called when the surface is created or recreated.
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated")

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated()

        mAppRenderer.onSurfaceCreated()
    }


    // Called to draw the current frame.
    override fun onDrawFrame(gl: GL10) {
        var words: List<WordDesc>? = null
        if (!mIsActive) {
            mWords.clear()
            mTextRecognitionActivity.updateWordListUI(mWords)
            return
        }

        // Call our function to render content from SampleAppRenderer class
        mAppRenderer.render()

        synchronized(mWords) {
            words = ArrayList(mWords)
        }

        Collections.sort(words!!)

        // update UI - we copy the list to avoid concurrent modifications
        mTextRecognitionActivity.updateWordListUI(ArrayList(words!!))
    }


    // Called when the surface changed size.
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        Log.d(LOGTAG, "onSurfaceChanged")

        mTextRecognitionActivity.configureVideoBackgroundROI()

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height)

        // RenderingPrimitives to be updated when some rendering change is done
        mAppRenderer.onConfigurationChanged(mIsActive)

        // Call function to initialize rendering:
        initRendering()
    }


    fun setActive(active: Boolean) {
        mIsActive = active

        if (mIsActive)
            mAppRenderer.configureVideoBackground()
    }


    fun updateConfiguration() {
        mAppRenderer.onConfigurationChanged(mIsActive)
    }


    internal var modelLoaded = false
    // Function for initializing the renderer.
    private fun initRendering() {
        if (!modelLoaded) {
            // init the vert/inde buffers
            mROIVerts = ByteBuffer.allocateDirect(4 * ROIVertices.size)
            mROIVerts!!.order(ByteOrder.LITTLE_ENDIAN)
            updateROIVertByteBuffer()

            mROIIndices = ByteBuffer.allocateDirect(2 * ROIIndices.size)
            mROIIndices!!.order(ByteOrder.LITTLE_ENDIAN)
            for (s in ROIIndices)
                mROIIndices!!.putShort(s)
            mROIIndices!!.rewind()

            mQuadVerts = ByteBuffer.allocateDirect(4 * quadVertices.size)
            mQuadVerts!!.order(ByteOrder.LITTLE_ENDIAN)
            for (f in quadVertices)
                mQuadVerts!!.putFloat(f)
            mQuadVerts!!.rewind()

            mQuadIndices = ByteBuffer.allocateDirect(2 * quadIndices.size)
            mQuadIndices!!.order(ByteOrder.LITTLE_ENDIAN)
            for (s in quadIndices)
                mQuadIndices!!.putShort(s)
            mQuadIndices!!.rewind()

            mRenderer = Renderer.getInstance()
            modelLoaded = true
        }

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, if (Vuforia.requiresAlpha())
            0.0f
        else
            1.0f)

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
                LineShaders.LINE_VERTEX_SHADER, LineShaders.LINE_FRAGMENT_SHADER)

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix")

        lineOpacityHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "opacity")
        lineColorHandle = GLES20.glGetUniformLocation(shaderProgramID, "color")

    }


    private fun updateROIVertByteBuffer() {
        mROIVerts!!.rewind()
        for (f in ROIVertices)
            mROIVerts!!.putFloat(f)
        mROIVerts!!.rewind()
    }


    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    override fun renderFrame(state: State, projectionMatrix: FloatArray) {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mAppRenderer.renderVideoBackground()

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        // enable blending to support transparency
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,
                GLES20.GL_ONE_MINUS_CONSTANT_ALPHA)

        // clear words list
        mWords.clear()

        // did we find any trackables this frame?
        for (tIdx in 0..state.numTrackableResults - 1) {
            // get the trackable
            val result = state.getTrackableResult(tIdx)

            var wordBoxSize: Vec2F? = null

            if (result.isOfType(WordResult.getClassType())) {
                val wordResult = result as WordResult
                val word = wordResult.trackable as Word
                val obb = wordResult.obb
                wordBoxSize = word.size

                val wordU = word.stringU
                if (wordU != null) {
                    // in portrait, the obb coordinate is based on
                    // a 0,0 position being in the upper right corner
                    // with :
                    // X growing from top to bottom and
                    // Y growing from right to left
                    //
                    // we convert those coordinates to be more natural
                    // with our application:
                    // - 0,0 is the upper left corner
                    // - X grows from left to right
                    // - Y grows from top to bottom
                    val wordx = -obb.center.data[1]
                    val wordy = obb.center.data[0]

                    if (mWords.size < MAX_NB_WORDS) {
                        mWords.add(WordDesc(wordU,
                                (wordx - wordBoxSize!!.data[0] / 2).toInt(),
                                (wordy - wordBoxSize.data[1] / 2).toInt(),
                                (wordx + wordBoxSize.data[0] / 2).toInt(),
                                (wordy + wordBoxSize.data[1] / 2).toInt()))
                    }

                }
            } else {
                Log.d(LOGTAG, "Unexpected Detection : " + result.type)
                continue
            }

            val mvMat44f = Tool.convertPose2GLMatrix(result.getPose())
            val mvMat = mvMat44f.data
            val mvpMat = FloatArray(16)
            Matrix.translateM(mvMat, 0, 0f, 0f, 0f)
            Matrix.scaleM(mvMat, 0, wordBoxSize!!.data[0] - TEXTBOX_PADDING,
                    wordBoxSize.data[1] - TEXTBOX_PADDING, 1.0f)
            Matrix.multiplyMM(mvpMat, 0, projectionMatrix, 0, mvMat, 0)

            GLES20.glUseProgram(shaderProgramID)
            GLES20.glLineWidth(3.0f)
            GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                    false, 0, mQuadVerts)
            GLES20.glEnableVertexAttribArray(vertexHandle)
            GLES20.glUniform1f(lineOpacityHandle, 1.0f)
            GLES20.glUniform3f(lineColorHandle, 1.0f, 0.447f, 0.0f)
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMat, 0)
            GLES20.glDrawElements(GLES20.GL_LINES, NUM_QUAD_OBJECT_INDICES,
                    GLES20.GL_UNSIGNED_SHORT, mQuadIndices)
            GLES20.glDisableVertexAttribArray(vertexHandle)
            GLES20.glLineWidth(1.0f)
            GLES20.glUseProgram(0)
        }

        // Draw the region of interest
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        drawRegionOfInterest(ROICenterX, ROICenterY, ROIWidth, ROIHeight)

        GLES20.glDisable(GLES20.GL_BLEND)

        mRenderer!!.end()
    }


    fun setROI(center_x: Float, center_y: Float, width: Float, height: Float) {
        ROICenterX = center_x
        ROICenterY = center_y
        ROIWidth = width
        ROIHeight = height
    }


    fun setViewport(vpX: Int, vpY: Int, vpSizeX: Int, vpSizeY: Int) {
        viewportPosition_x = vpX
        viewportPosition_y = vpY
        viewportSize_x = vpSizeX
        viewportSize_y = vpSizeY
    }


    private fun drawRegionOfInterest(center_x: Float, center_y: Float,
                                     width: Float, height: Float) {
        // assumption is that center_x, center_y, width and height are given
        // here in screen coordinates (screen pixels)
        val orthProj = FloatArray(16)
        setOrthoMatrix(0.0f, viewportSize_x.toFloat(), viewportSize_y.toFloat(),
                0.0f, -1.0f, 1.0f, orthProj)

        // compute coordinates
        val minX = center_x - width / 2
        val maxX = center_x + width / 2
        val minY = center_y - height / 2
        val maxY = center_y + height / 2

        // Update vertex coordinates of ROI rectangle
        ROIVertices[0] = minX - viewportPosition_x
        ROIVertices[1] = minY - viewportPosition_y
        ROIVertices[2] = 0f

        ROIVertices[3] = maxX - viewportPosition_x
        ROIVertices[4] = minY - viewportPosition_y
        ROIVertices[5] = 0f

        ROIVertices[6] = maxX - viewportPosition_x
        ROIVertices[7] = maxY - viewportPosition_y
        ROIVertices[8] = 0f

        ROIVertices[9] = minX - viewportPosition_x
        ROIVertices[10] = maxY - viewportPosition_y
        ROIVertices[11] = 0f

        updateROIVertByteBuffer()

        GLES20.glUseProgram(shaderProgramID)
        GLES20.glLineWidth(3.0f)

        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false,
                0, mROIVerts)
        GLES20.glEnableVertexAttribArray(vertexHandle)

        GLES20.glUniform1f(lineOpacityHandle, 1.0f) // 0.35f);
        GLES20.glUniform3f(lineColorHandle, 0.0f, 1.0f, 0.0f)// R,G,B
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, orthProj, 0)

        // Then, we issue the render call
        GLES20.glDrawElements(GLES20.GL_LINES, NUM_QUAD_OBJECT_INDICES,
                GLES20.GL_UNSIGNED_SHORT, mROIIndices)

        // Disable the vertex array handle
        GLES20.glDisableVertexAttribArray(vertexHandle)

        // Restore default line width
        GLES20.glLineWidth(1.0f)

        // Unbind shader program
        GLES20.glUseProgram(0)
    }

    inner class WordDesc(var text: String, internal var Ax: Int, internal var Ay: Int, internal var Bx: Int, internal var By: Int) : Comparable<WordDesc> {


        override fun compareTo(w2: WordDesc): Int {
            val w1 = this
            val ret: Int

            // We split the screen into 100 bins so that words on a line
            // are roughly kept together.
            var bins = viewportSize_y / 100
            if (bins == 0) {
                // Not expected, but should make sure we don't divide by 0.
                bins = 1
            }

            // We want to order words starting from the top left to the bottom right.
            // We therefore use the top-middle point of the word obb and bin the word
            // to an address that is consistently comparable to other word locations.
            val w1mx = w1.Ax / bins
            val w1my = (w1.By + w1.Ay) / 2 / bins

            val w2mx = w2.Ax / bins
            val w2my = (w2.By + w2.Ay) / 2 / bins

            ret = (w1my * viewportSize_x + w1mx).compareTo(w2my * viewportSize_x + w2mx)

            return ret
        }

        override fun toString(): String {
            return "$text [$Ax, $Ay, $Bx, $By]"
        }
    }


    private fun setOrthoMatrix(nLeft: Float, nRight: Float, nBottom: Float,
                               nTop: Float, nNear: Float, nFar: Float, _ROIOrthoProjMatrix: FloatArray) {
        for (i in 0..15)
            _ROIOrthoProjMatrix[i] = 0.0f

        _ROIOrthoProjMatrix[0] = 2.0f / (nRight - nLeft)
        _ROIOrthoProjMatrix[5] = 2.0f / (nTop - nBottom)
        _ROIOrthoProjMatrix[10] = 2.0f / (nNear - nFar)
        _ROIOrthoProjMatrix[12] = -(nRight + nLeft) / (nRight - nLeft)
        _ROIOrthoProjMatrix[13] = -(nTop + nBottom) / (nTop - nBottom)
        _ROIOrthoProjMatrix[14] = (nFar + nNear) / (nFar - nNear)
        _ROIOrthoProjMatrix[15] = 1.0f

    }

    companion object {

        private val LOGTAG = "TextRecoRenderer"

        private val MAX_NB_WORDS = 132
        private val TEXTBOX_PADDING = 0.0f

        private val ROIVertices = floatArrayOf(-0.5f, -0.5f, 0.0f, 0.5f, -0.5f, 0.0f, 0.5f, 0.5f, 0.0f, -0.5f, 0.5f, 0.0f)

        private val NUM_QUAD_OBJECT_INDICES = 8
        private val ROIIndices = shortArrayOf(0, 1, 1, 2, 2, 3, 3, 0)

        private val quadVertices = floatArrayOf(-0.5f, -0.5f, 0.0f, 0.5f, -0.5f, 0.0f, 0.5f, 0.5f, 0.0f, -0.5f, 0.5f, 0.0f)

        private val quadIndices = shortArrayOf(0, 1, 1, 2, 2, 3, 3, 0)


        internal fun fromShortArray(str: ShortArray): String {
            val result = StringBuilder()
            for (c in str)
                result.appendCodePoint(c.toInt())
            return result.toString()
        }
    }
}
