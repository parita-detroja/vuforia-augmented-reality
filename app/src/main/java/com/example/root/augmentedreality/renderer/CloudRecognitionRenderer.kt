package com.example.root.augmentedreality.renderer

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.root.augmentedreality.activity.CloudRecognitionActivity
import com.example.root.augmentedreality.utility.CubeShaders
import com.example.root.augmentedreality.utility.SampleUtils
import com.example.root.augmentedreality.utility.Teapot
import com.example.root.augmentedreality.utility.Texture
import com.example.root.augmentedreality.vuforia.AppRenderer
import com.example.root.augmentedreality.vuforia.AppRendererControl
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.vuforia.*
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Created by root on 4/8/17.
 * Uses target from cloud data base.
 */
class CloudRecognitionRenderer(private val vuforiaAppSession: ApplicationSession,
                               private val mActivity: CloudRecognitionActivity) :
        GLSurfaceView.Renderer, AppRendererControl {
    private val mSampleAppRenderer: AppRenderer = AppRenderer(this, mActivity, Device.MODE.MODE_AR, false, 10f, 5000f)

    private var shaderProgramID: Int = 0
    private var vertexHandle: Int = 0
    private var textureCoordHandle: Int = 0
    private var mvpMatrixHandle: Int = 0
    private var texSampler2DHandle: Int = 0

    private var mTextures: Vector<Texture>? = null

    private var mTeapot: Teapot? = null

    private var mIsActive = false

    init {

        // SampleAppRenderer used to encapsulate the use of RenderingPrimitives setting
        // the device mode AR/VR and stereo mode
    }


    // Called when the surface is created or recreated.
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig) {
        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated()

        mSampleAppRenderer.onSurfaceCreated()
    }


    // Called when the surface changed size.
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int) {
        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height)

        // RenderingPrimitives to be updated when some rendering change is done
        mSampleAppRenderer.onConfigurationChanged(mIsActive)

        // Call function to initialize rendering:
        initRendering()
    }


    // Called to draw the current frame.
    override fun onDrawFrame(gl: GL10) {
        // Call our function to render content from SampleAppRenderer class
        mSampleAppRenderer.render()
    }


    fun setActive(active: Boolean) {
        mIsActive = active

        if (mIsActive)
            mSampleAppRenderer.configureVideoBackground()
    }


    // Function for initializing the renderer.
    private fun initRendering() {
        // Define clear color
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
        mTeapot = Teapot()
    }


    // The render function.
    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    override fun renderFrame(state: State, projectionMatrix: FloatArray) {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mSampleAppRenderer.renderVideoBackground()

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        // Did we find any trackables this frame?
        if (state.numTrackableResults > 0) {
            // Gets current trackable result
            val trackableResult = state.getTrackableResult(0) ?: return

            mActivity.stopFinderIfStarted()

            // Renders the Augmentation View with the 3D Book data Panel
            renderAugmentation(trackableResult, projectionMatrix)

        } else {
            mActivity.startFinderIfStopped()
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        Renderer.getInstance().end()
    }


    private fun renderAugmentation(trackableResult: TrackableResult, projectionMatrix: FloatArray) {
        val modelViewMatrix_Vuforia = Tool
                .convertPose2GLMatrix(trackableResult.pose)
        val modelViewMatrix = modelViewMatrix_Vuforia.data

        val textureIndex = 0

        // deal with the modelview and projection matrices
        val modelViewProjection = FloatArray(16)
        Matrix.translateM(modelViewMatrix, 0, 0.0f, 0.0f, OBJECT_SCALE_FLOAT)
        Matrix.scaleM(modelViewMatrix, 0, OBJECT_SCALE_FLOAT,
                OBJECT_SCALE_FLOAT, OBJECT_SCALE_FLOAT)
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0)

        // activate the shader program and bind the vertex/normal/tex coords
        GLES20.glUseProgram(shaderProgramID)
        GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT, false,
                0, mTeapot!!.vertices)
        GLES20.glVertexAttribPointer(textureCoordHandle, 2, GLES20.GL_FLOAT,
                false, 0, mTeapot!!.texCoords)

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
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mTeapot!!.numObjectIndex,
                GLES20.GL_UNSIGNED_SHORT, mTeapot!!.indices)

        // disable the enabled arrays
        GLES20.glDisableVertexAttribArray(vertexHandle)
        GLES20.glDisableVertexAttribArray(textureCoordHandle)

        SampleUtils.checkGLError("CloudReco renderFrame")
    }


    fun setTextures(textures: Vector<Texture>) {
        mTextures = textures
    }

    companion object {

        private val OBJECT_SCALE_FLOAT = 3.0f
    }

}
