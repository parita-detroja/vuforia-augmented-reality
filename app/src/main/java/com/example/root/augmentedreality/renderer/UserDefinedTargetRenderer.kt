package com.example.root.augmentedreality.renderer

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import android.widget.Toast
import com.example.root.augmentedreality.activity.UserDefinedTargetsActivity
import com.example.root.augmentedreality.utility.*
import com.example.root.augmentedreality.vuforia.AppRenderer
import com.example.root.augmentedreality.vuforia.AppRendererControl
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.vuforia.*
import java.io.File
import java.io.FileOutputStream
import java.nio.IntBuffer
import java.util.*
import javax.microedition.khronos.opengles.GL10

/**
 * Created by root on 30/6/17.
 * The main activity for the UserDefinedTargetsActivity sample.
 */
class UserDefinedTargetRenderer(// Reference to main activity
        private val mActivity: UserDefinedTargetsActivity,
        private val vuforiaAppSession: ApplicationSession) : GLSurfaceView.Renderer, AppRendererControl {

    private var mViewWidth = 0
    private var mViewHeight = 0

    override fun onSurfaceCreated(gl: GL10?, config: javax.microedition.khronos.egl.EGLConfig?) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated")

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated()

        mSampleAppRenderer.onSurfaceCreated()
    }

    private val mSampleAppRenderer: AppRenderer = AppRenderer(this, mActivity, Device.MODE.MODE_AR, false, 10f, 5000f)

    private var mIsActive = false

    private var mTextures: Vector<Texture>? = null
    private var shaderProgramID: Int = 0
    private var vertexHandle: Int = 0
    private var textureCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var texSampler2DHandle: Int = 0

    private var mCustomImagePlane: CustomImagePlane? = null

    var scaleFactor: Float = 1.toFloat()

    fun setScalFactor(scaleFactor: Float)
    {
        this.scaleFactor = scaleFactor
    }

    fun getAngle(): Float {
        return mSampleAppRenderer.mAngle
    }

    fun setAngle(angle: Float) {
        mSampleAppRenderer.mAngle = angle
    }

    init {

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
    }

    // Called when the surface changed size.
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged")

        // Call function to update rendering when render surface
        // parameters have changed:
        mActivity.updateRendering()

        mViewWidth = width
        mViewHeight = height

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height)

        // RenderingPrimitives to be updated when some rendering change is done
        mSampleAppRenderer.onConfigurationChanged(mIsActive)

        // Call function to initialize rendering:
        initRendering()
    }

    fun updateConfiguration() {
        mSampleAppRenderer.onConfigurationChanged(mIsActive)
    }

    fun setActive(active: Boolean) {
        mIsActive = active

        if (mIsActive)
            mSampleAppRenderer.configureVideoBackground()
    }


    // Called to draw the current frame.
    override fun onDrawFrame(gl: GL10) {
        if (!mIsActive)
            return

        // Call our function to render content from SampleAppRenderer class
        mSampleAppRenderer.render()
        GLES20.glFinish()

        if(mActivity.objectStatus && mActivity.count != 0)
        {
            saveScreenShot(0, 0, Calendar.getInstance().timeInMillis.toString() + ".jpeg")
            Log.e("User defined target","screen shot")
            mActivity.count = 0
        }

    }


    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    override fun renderFrame(state: State, projectionMatrix: FloatArray) {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground()

        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        // Render the RefFree UI elements depending on the current state
        //mActivity.refFreeFrame!!.render()

        // Did we find any trackables this frame?
        for (tIdx in 0..state.numTrackableResults - 1) {
            // Get the trackable:
            val trackableResult = state.getTrackableResult(tIdx)
            val modelViewMatrix_Vuforia = Tool
                    .convertPose2GLMatrix(trackableResult.pose)
            val modelViewMatrix = modelViewMatrix_Vuforia.data

            val modelViewProjection = FloatArray(16)
            Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f, kObjectScale)
            Matrix.scaleM(modelViewMatrix, 0, kObjectScale*scaleFactor, kObjectScale*scaleFactor,
                    kObjectScale*scaleFactor)
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0)

            GLES20.glUseProgram(shaderProgramID)

            GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                    false, 0, mCustomImagePlane!!.vertices)
            GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                    GLES20.GL_FLOAT, false, 0, mCustomImagePlane!!.texCoords)

            GLES20.glEnableVertexAttribArray(vertexHandle)
            GLES20.glEnableVertexAttribArray(textureCoordHandle)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                    mTextures!![0].mTextureID[0])
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                    modelViewProjection, 0)
            GLES20.glUniform1i(texSampler2DHandle, 0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                    mCustomImagePlane!!.numObjectIndex, GLES20.GL_UNSIGNED_SHORT,
                    mCustomImagePlane!!.indices)

            GLES20.glDisableVertexAttribArray(vertexHandle)
            GLES20.glDisableVertexAttribArray(textureCoordHandle)

            SampleUtils.checkGLError("UserDefinedTargetsActivity renderFrame")
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_BLEND)

        Renderer.getInstance().end()

        // Render the RefFree UI elements depending on the current state
        mActivity.refFreeFrame!!.render()
    }


    private fun initRendering() {
        Log.d(LOGTAG, "initRendering")

        mCustomImagePlane = CustomImagePlane()

        // Define clear color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, if (Vuforia.requiresAlpha())
            0.0f
        else
            1.0f)

        // Now generate the OpenGL texture objects and add settings
        for (t in mTextures!!) {
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

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
                CubeShaders.CUBE_MESH_VERTEX_SHADER,
                CubeShaders.CUBE_MESH_FRAGMENT_SHADER)

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition")
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexTexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix")
        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "texSampler2D")
    }


    fun setTextures(textures: Vector<Texture>) {
        mTextures = textures

    }

    companion object {
        private val LOGTAG = "UDTRenderer"

        // Constants:
        internal val kObjectScale = 3f
    }

    fun saveScreenShot(x: Int, y: Int, fileName: String)
    {
        /*val egl = EGLContext.getEGL() as EGL10
        val gl = egl.eglGetCurrentContext().getGL() as GL10
        val bitMap: Bitmap = createBitmapFromGLSurface(x, y, mViewWidth, mViewHeight, gl)*/

        val bitMap:Bitmap? = grabPixels(x, y, mViewWidth, mViewHeight)

        try {
            val path: String = (mActivity.applicationContext).getExternalFilesDir(null)!!.path + "/" + fileName
            Log.d("User Defined Target",path)

            val file: File = File(path)
            file.createNewFile()

            val fileOutputStream: FileOutputStream = FileOutputStream(file)
            bitMap!!.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream)

            fileOutputStream.flush()

            fileOutputStream.close()

            mActivity.loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG)

            Toast.makeText(mActivity.applicationContext,"Snapshot captured",Toast.LENGTH_SHORT).show()

        }catch (e: Exception) {
            Log.d("User defined target",e.message)
        }
    }

    private fun grabPixels(x: Int, y: Int, w :Int, h: Int): Bitmap?
    {
        val b: IntArray = kotlin.IntArray(w * (y + h))
        val bt: IntArray = kotlin.IntArray(w * h)
        val intBuffer: IntBuffer = IntBuffer.wrap(b)

        intBuffer.position(0)

        GLES20.glReadPixels(x, 0, w, y + h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, intBuffer)

        var i = 0
        var k = 0
        while (i < h) {
            for (j in 0..w - 1) {
                val pix = b[i * w + j]
                val pb = (pix shr 16) and 0xff
                val pr = (pix shl 16) and 0x00ff0000
                val pix1 = (pix and 0xff00ff00.toInt()) or pr or pb
                bt[(h - k - 1) * w + j] = pix1
            }
            i++
            k++
        }

        val sb: Bitmap = Bitmap.createBitmap(bt,w,h, Bitmap.Config.ARGB_8888)
        return sb
    }

    /*@Throws(OutOfMemoryError::class)
    private fun createBitmapFromGLSurface(x: Int, y: Int, w: Int, h: Int, gl: GL10): Bitmap {
        val bitmapBuffer = IntArray(w * h)
        val bitmapSource = IntArray(w * h)
        val intBuffer = IntBuffer.wrap(bitmapBuffer)
        intBuffer.position(0)

        try {
            gl.glReadPixels(x, y, w, h, GL10.GL_RGBA, GL10.GL_UNSIGNED_BYTE, intBuffer)
            var offset1: Int
            var offset2: Int
            for (i in 0..h - 1) {
                offset1 = i * w
                offset2 = (h - i - 1) * w
                for (j in 0..w - 1) {
                    val texturePixel = bitmapBuffer[offset1 + j]
                    val blue = texturePixel shr 16 and 0xff
                    val red = texturePixel shl 16 and 0x00ff0000
                    val pixel = texturePixel and 0xff00ff00.toInt() or red or blue
                    bitmapSource[offset2 + j] = pixel
                }
            }
        } catch (e: GLException) {
            Log.e("User defined target",e.message)
        }

        return Bitmap.createBitmap(bitmapSource, w, h, Bitmap.Config.ARGB_8888)
    }*/

}