package com.example.root.augmentedreality.renderer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.util.Log
import com.example.root.augmentedreality.activity.ImageTargetActivity
import com.example.root.augmentedreality.utility.*
import com.example.root.augmentedreality.vuforia.AppRenderer
import com.example.root.augmentedreality.vuforia.AppRendererControl
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.vuforia.*
import java.io.IOException
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

// The renderer class for the ImageTargets sample.
class ImageTargetRenderer(private val mImageTargetActivity: ImageTargetActivity, private val vuforiaAppSession: ApplicationSession) : GLSurfaceView.Renderer, AppRendererControl {
    private val mSampleAppRenderer: AppRenderer = AppRenderer(this, mImageTargetActivity, Device.MODE.MODE_AR, false, 0.01f, 5f)

    private var mTextures: Vector<Texture>? = null

    private var shaderProgramID: Int = 0
    private var vertexHandle: Int = 0
    private var textureCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var texSampler2DHandle: Int = 0

    private var mTeapot: Teapot? = null

    private val kBuildingScale = 0.012f
    private var mBuildingsModel: Application3DModel? = null

    private var mIsActive = false
    private var mModelIsLoaded = false


    init {
        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
    }

    fun getAngle(): Float {
        return mSampleAppRenderer.mAngle
    }

    fun setAngle(angle: Float) {
        mSampleAppRenderer.mAngle = angle
    }


    // Called to draw the current frame.
    override fun onDrawFrame(gl: GL10) {
        if (!mIsActive)
            return

        // Call our function to render content from SampleAppRenderer class
        mSampleAppRenderer.render()
    }


    fun setActive(active: Boolean) {
        mIsActive = active

        if (mIsActive)
            mSampleAppRenderer.configureVideoBackground()
    }


    // Called when the surface is created or recreated.
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated")

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated()

        mSampleAppRenderer.onSurfaceCreated()
    }


    // Called when the surface changed size.
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged")

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height)

        // RenderingPrimitives to be updated when some rendering change is done
        mSampleAppRenderer.onConfigurationChanged(mIsActive)

        initRendering()
    }


    // Function for initializing the renderer.
    private fun initRendering() {
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, if (Vuforia.requiresAlpha())
            0.0f
        else
            1.0f)

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

        if (!mModelIsLoaded) {
            mTeapot = Teapot()

            try {
                mBuildingsModel = Application3DModel()
                mBuildingsModel!!.loadModel(mImageTargetActivity.resources.assets,
                        "ImageTargets/Buildings.txt")
                mModelIsLoaded = true
            } catch (e: IOException) {
                Log.e(LOGTAG, "Unable to load buildings")
            }

            // Hide the Loading Dialog
            mImageTargetActivity.loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG)
        }

    }

    /*fun updateConfiguration() {
        mSampleAppRenderer.onConfigurationChanged(mIsActive)
    }*/

    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    override fun renderFrame(state: State, projectionMatrix: FloatArray) {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground()

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        // handle face culling, we need to detect if we are using reflection
        // to determine the direction of the culling
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        // Did we find any trackables this frame?
        for (tIdx in 0..state.numTrackableResults - 1) {
            val result = state.getTrackableResult(tIdx)
            val trackable = result.trackable
            printUserData(trackable)
            val modelViewMatrix_Vuforia = Tool
                    .convertPose2GLMatrix(result.pose)
            val modelViewMatrix = modelViewMatrix_Vuforia.data

            var textureIndex = if (trackable.name.equals("stones", ignoreCase = true))
                0
            else
                1
            textureIndex = if (trackable.name.equals("tarmac", ignoreCase = true))
                2
            else
                textureIndex

            // deal with the modelview and projection matrices
            val modelViewProjection = FloatArray(16)

            if (!mImageTargetActivity.isExtendedTrackingActive()) {
                Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f,
                        OBJECT_SCALE_FLOAT)
                Matrix.scaleM(modelViewMatrix, 0, OBJECT_SCALE_FLOAT,
                        OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT)
            } else {
                Matrix.rotateM(modelViewMatrix, 0, 90.0f, 1.0f, 0f, 0f)
                Matrix.scaleM(modelViewMatrix, 0, kBuildingScale,
                        kBuildingScale, kBuildingScale)
            }
            Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0)

            // activate the shader program and bind the vertex/normal/tex coords
            GLES20.glUseProgram(shaderProgramID)

            if (!mImageTargetActivity.isExtendedTrackingActive()) {
                GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                        false, 0, mTeapot!!.vertices)
                GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                        GLES20.GL_FLOAT, false, 0, mTeapot!!.texCoords)

                GLES20.glEnableVertexAttribArray(vertexHandle)
                GLES20.glEnableVertexAttribArray(textureCoordHandle)

                // activate texture 0, bind it, and pass to shader
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                        mTextures!![textureIndex].mTextureID[0])
                GLES20.glUniform1i(texSampler2DHandle, 0)

                // pass the model view matrix to the shader
                GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                        modelViewProjection, 0)

                // finally draw the teapot
                GLES20.glDrawElements(GLES20.GL_TRIANGLES,
                        mTeapot!!.numObjectIndex, GLES20.GL_UNSIGNED_SHORT,
                        mTeapot!!.indices)

                // disable the enabled arrays
                GLES20.glDisableVertexAttribArray(vertexHandle)
                GLES20.glDisableVertexAttribArray(textureCoordHandle)
            } else {
                GLES20.glDisable(GLES20.GL_CULL_FACE)
                GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                        false, 0, mBuildingsModel!!.vertices)
                GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                        GLES20.GL_FLOAT, false, 0, mBuildingsModel!!.texCoords)

                GLES20.glEnableVertexAttribArray(vertexHandle)
                GLES20.glEnableVertexAttribArray(textureCoordHandle)

                GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                        mTextures!![3].mTextureID[0])
                GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                        modelViewProjection, 0)
                GLES20.glUniform1i(texSampler2DHandle, 0)
                GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0,
                        mBuildingsModel!!.numObjectVertex)

                SampleUtils.checkGLError("Renderer DrawBuildings")
            }

            SampleUtils.checkGLError("Render Frame")

        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

    }

    private fun printUserData(trackable: Trackable) {
        val userData = trackable.userData as String
        Log.d(LOGTAG, "UserData:Retreived User Data	\"" + userData + "\"")
    }


    fun setTextures(textures: Vector<Texture>) {
        mTextures = textures

    }

    companion object {
        private val LOGTAG = "ImageTargetRenderer"

        private val OBJECT_SCALE_FLOAT = 0.003f
    }

}
