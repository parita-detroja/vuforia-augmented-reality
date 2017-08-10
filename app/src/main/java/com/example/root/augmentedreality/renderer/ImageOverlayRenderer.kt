package com.example.root.augmentedreality.renderer

import android.content.res.Configuration
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.example.root.augmentedreality.activity.ImageOverlayActivity
import com.example.root.augmentedreality.utility.*
import com.example.root.augmentedreality.vuforia.AppRenderer
import com.example.root.augmentedreality.vuforia.AppRendererControl
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.vuforia.*
import java.util.concurrent.atomic.AtomicInteger
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Created by root on 9/8/17.
 * The renderer class for the image overlay.
 */
class ImageOverlayRenderer(var mActivity: ImageOverlayActivity, internal var vuforiaAppSession: ApplicationSession)
    : GLSurfaceView.Renderer, AppRendererControl
{
    internal var mAppRenderer: AppRenderer = AppRenderer(this, mActivity, Device.MODE.MODE_AR, false, 10f, 5000f)

    private var mIsActive = false

    var scanningMode = false

    private var mShowAnimation3Dto2D = true

    private var mStartAnimation3Dto2D = false

    private var mStartAnimation2Dto3D = false

    // Initialize RenderState
    var renderState = RS_SCANNING

    internal var transition3Dto2D: Transition3Dto2D? = null
    internal var transition2Dto3D: Transition3Dto2D? = null

    internal var transitionDuration = 0.5f
    internal var mIsShowing2DOverlay = false

    // ----------------------------------------------------------------------------
    // Flag for deleting current product texture in the renderFrame Thread
    // ----------------------------------------------------------------------------
    internal var deleteCurrentProductTexture = false

    private var mScreenHeight: Int = 0

    private var mScreenWidth: Int = 0

    private var mProductTexture: Texture? = null

    private var mDPIScaleIndicator: Float = 0.toFloat()

    private var mScaleFactor: Float = 0.toFloat()

    private val framesToSkipBeforeRenderingTransition = AtomicInteger(
            10)

    private var mTrackingStarted = false

    private var shaderProgramID: Int = 0

    private var vertexHandle: Int = 0

    private var textureCoordHandle: Int = 0

    private var mvpMatrixHandle: Int = 0

    private var modelViewMatrix: FloatArray? = null

    private var pose: Matrix34F? = null

    private var mPlane: CustomImagePlane? = null


    fun setFramesToSkipBeforeRenderingTransition(framesToSkip: Int)
    {
        framesToSkipBeforeRenderingTransition.set(framesToSkip)
    }


    // Function for initializing the renderer.
    fun initRendering()
    {

        // Define clear color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, if (Vuforia.requiresAlpha())
            0.0f
        else
            1.0f)

        // OpenGL setup for 3D model
        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
                Shaders.cubeMeshVertexShader, Shaders.cubeFragmentShader)

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition")
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexTexCoord")
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix")

        mPlane = CustomImagePlane()

    }


    // Function to update the renderer.
    fun updateRendering(width: Int, height: Int)
    {
        // Update screen dimensions
        mScreenWidth = width
        mScreenHeight = height

        // get the current orientation
        val config = mActivity.resources.configuration

        val isActivityInPortraitMode: Boolean
        isActivityInPortraitMode = config.orientation != Configuration.ORIENTATION_LANDSCAPE

        // Initialize the 3D to 2D Transition
        transition3Dto2D = Transition3Dto2D(mScreenWidth, mScreenHeight,
                isActivityInPortraitMode, mDPIScaleIndicator, mScaleFactor, mPlane!!)
        transition3Dto2D!!.initializeGL(shaderProgramID)

        // Initialize the 2D to 3D Transition
        transition2Dto3D = Transition3Dto2D(mScreenWidth, mScreenHeight,
                isActivityInPortraitMode, mDPIScaleIndicator, mScaleFactor, mPlane!!)
        transition2Dto3D!!.initializeGL(shaderProgramID)
    }


    // Function to update the video background
    fun updateVideoBackground()
    {
        Vuforia.onSurfaceChanged(mScreenWidth, mScreenHeight)
        mAppRenderer.onConfigurationChanged(mIsActive)
    }


    // Called when the surface is created or recreated.
    override fun onSurfaceCreated(gl: GL10, config: EGLConfig)
    {
        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        Vuforia.onSurfaceCreated()

        mAppRenderer.onSurfaceCreated()

        // Call function to initialize rendering:
        initRendering()
    }


    // Called when the surface changed size.
    override fun onSurfaceChanged(gl: GL10, width: Int, height: Int)
    {
        mScreenHeight = height
        mScreenWidth = width

        // Call function to update rendering when render surface
        // parameters have changed:
        updateRendering(width, height)

        // Call Vuforia function to handle render surface size changes:
        Vuforia.onSurfaceChanged(width, height)

        // RenderingPrimitives to be updated when some rendering change is done
        mAppRenderer.onConfigurationChanged(mIsActive)

        // Call function to initialize rendering:
        initRendering()
    }


    fun setActive(active: Boolean)
    {
        mIsActive = active

        if (mIsActive)
            mAppRenderer.configureVideoBackground()
    }


    // The render function called from SampleAppRendering by using RenderingPrimitives views.
    // The state is owned by SampleAppRenderer which is controlling it's lifecycle.
    // State should not be cached outside this method.
    override fun renderFrame(state: State, projectionMatrix: FloatArray)
    {
        // Renders video background replacing Renderer.DrawVideoBackground()
        mAppRenderer.renderVideoBackground()

        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        if (deleteCurrentProductTexture)
        {
            // Deletes the product texture if necessary
            if (mProductTexture != null)
            {
                GLES20.glDeleteTextures(1, mProductTexture!!.mTextureID, 0)
                mProductTexture = null
            }

            deleteCurrentProductTexture = false
        }

        // If the render state indicates that the texture is generated it
        // generates
        // the OpenGL texture for start drawing the plane with the book data
        if (renderState == RS_TEXTURE_GENERATED)
        {
            generateProductTextureInOpenGL()
        }

        // Did we find any trackables this frame?
        if (state.numTrackableResults > 0)
        {
            mTrackingStarted = true

            // If we are already tracking something we don't need
            // to wait any frame before starting the 2D transition
            // when the target gets lost
            framesToSkipBeforeRenderingTransition.set(0)

            // Gets current trackable result
            val trackableResult = state.getTrackableResult(0) ?: return

            modelViewMatrix = Tool.convertPose2GLMatrix(
                    trackableResult.pose).data

            // Renders the Augmentation View with the 3D Book data Panel
            renderAugmentation(trackableResult, projectionMatrix)

        } else
        {
            // Manages the 3D to 2D Transition initialization
            if (!scanningMode && mShowAnimation3Dto2D
                    && renderState == RS_NORMAL
                    && framesToSkipBeforeRenderingTransition.get() == 0)
            {
                startTransitionTo2D()
            }

            // Reduces the number of frames to wait before triggering
            // the transition by 1
            if (framesToSkipBeforeRenderingTransition.get() > 0 && renderState == RS_NORMAL)
            {
                framesToSkipBeforeRenderingTransition.decrementAndGet()
            }

        }

        // Logic for rendering Transition to 2D
        if (renderState == RS_TRANSITION_TO_2D && mShowAnimation3Dto2D)
        {
            renderTransitionTo2D(projectionMatrix)
        }

        // Logic for rendering Transition to 3D
        if (renderState == RS_TRANSITION_TO_3D)
        {
            renderTransitionTo3D(projectionMatrix)
        }

        // Get the tracker manager:
        val trackerManager = TrackerManager.getInstance()

        // Get the object tracker:
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker

        // Get the target finder:
        val finder = objectTracker.targetFinder

        // Renders the current state - User process Feedback
        if (finder.isRequesting)
        {
            // Requesting State - Show Requesting text in Status Bar
            mActivity.setStatusBarText("Requesting")
            mActivity.showStatusBar()
        } else {
            // Hiding Status Bar
            mActivity.hideStatusBar()
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        Renderer.getInstance().end()
    }


    private fun renderTransitionTo3D(projectionMatrix: FloatArray)
    {
        if (mStartAnimation2Dto3D)
        {
            transitionDuration = 0.5f

            // Starts the Transition
            transition2Dto3D!!.startTransition(transitionDuration, true, true)

            // Initialize control state variables
            mStartAnimation2Dto3D = false

        } else
        {

            if (mProductTexture != null)
            {
                if (pose == null)
                {
                    pose = transition2Dto3D!!.finalPositionMatrix34F
                }

                // Renders the transition
                transition2Dto3D!!.render(projectionMatrix, pose!!, mProductTexture!!.mTextureID[0])

                // check if transition is finished
                if (transition2Dto3D!!.transitionFinished())
                {
                    // Updates state values
                    mIsShowing2DOverlay = false
                    mShowAnimation3Dto2D = true

                    // Updates current renderState when the transition is
                    // finished to go back to normal rendering
                    renderState = RS_NORMAL
                }
            }
        }
    }


    private fun renderTransitionTo2D(projectionMatrix: FloatArray)
    {
        if (mStartAnimation3Dto2D)
        {
            // Starts the Transition
            transition3Dto2D!!.startTransition(transitionDuration, false, true)

            // Initialize control state variables
            mStartAnimation3Dto2D = false

        } else
        {

            if (mProductTexture != null)
            {
                if (pose == null)
                {
                    pose = transition2Dto3D!!.finalPositionMatrix34F
                }

                transition3Dto2D!!.render(projectionMatrix, pose!!, mProductTexture!!.mTextureID[0])

                // check if transition is finished
                if (transition3Dto2D!!.transitionFinished())
                {
                    mIsShowing2DOverlay = true

                }
            }
        }
    }


    // private method, actually start the transition
    private fun startTransitionTo2D()
    {
        // Initialize the animation values when the book data
        // is displayed normally
        if (renderState == RS_NORMAL && mTrackingStarted)
        {
            transitionDuration = 0.5f

            // Updates Render State
            renderState = RS_TRANSITION_TO_2D
            mStartAnimation3Dto2D = true

        } else if (renderState == RS_NORMAL && !mTrackingStarted
                && mProductTexture != null)
        {
            // Triggers the transition in case you loose the target while the
            // loading process
            transitionDuration = 0.0f

            // Updates RenderState
            renderState = RS_TRANSITION_TO_2D
            mStartAnimation3Dto2D = true

        }

    }


    private fun renderAugmentation(trackableResult: TrackableResult, projectionMatrix: FloatArray)
    {
        val modelViewProjection = FloatArray(16)

        // Scales the plane relative to the target
        Matrix.scaleM(modelViewMatrix, 0, 430f * mScaleFactor,
                430f * mScaleFactor, 1.0f)

        // Applies 3d Transformations to the plane
        Matrix.multiplyMM(modelViewProjection, 0, projectionMatrix, 0, modelViewMatrix, 0)

        // Moves the trackable current position to a global variable used for
        // the 3d to 2D animation
        pose = trackableResult.pose

        // Shader Program for drawing
        GLES20.glUseProgram(shaderProgramID)

        // The 3D Plane is only drawn when the texture is loaded and generated
        if (renderState == RS_NORMAL)
        {
            GLES20.glVertexAttribPointer(vertexHandle, 3, GLES20.GL_FLOAT,
                    false, 0, mPlane!!.vertices)
            GLES20.glVertexAttribPointer(textureCoordHandle, 2,
                    GLES20.GL_FLOAT, false, 0, mPlane!!.texCoords)

            GLES20.glEnableVertexAttribArray(vertexHandle)
            GLES20.glEnableVertexAttribArray(textureCoordHandle)

            // Enables Blending State
            GLES20.glEnable(GLES20.GL_BLEND)

            // Drawing Textured Plane
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                    mProductTexture!!.mTextureID[0])
            GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false,
                    modelViewProjection, 0)
            GLES20.glDrawElements(GLES20.GL_TRIANGLES, 6,
                    GLES20.GL_UNSIGNED_SHORT, mPlane!!.getIndices())

            GLES20.glDisableVertexAttribArray(vertexHandle)
            GLES20.glDisableVertexAttribArray(textureCoordHandle)

            // Disables Blending State - Its important to disable the blending
            // state after using it for preventing bugs with the Camera Video
            // Background
            GLES20.glDisable(GLES20.GL_BLEND)

            // Handles target re-acquisition - Checks if the overlay2D is shown
        } else if (mIsShowing2DOverlay)
        {
            // Initialize the Animation to 3d variables
            mStartAnimation2Dto3D = true
            mIsShowing2DOverlay = false

            // Updates renderState
            renderState = RS_TRANSITION_TO_3D

        }

        SampleUtils.checkGLError("Books renderFrame")

    }


    private fun generateProductTextureInOpenGL()
    {
        val textureObject = mActivity.mBookDataTexture

        if (textureObject != null)
        {
            mProductTexture = textureObject
        }

        // Generates the Texture in OpenGL
        GLES20.glGenTextures(1, mProductTexture!!.mTextureID, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,
                mProductTexture!!.mTextureID[0])
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())

        // We create an empty power of two texture and upload a sub image.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, 1024,
                1024, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null)

        GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0,
                mProductTexture!!.mWidth, mProductTexture!!.mHeight, GLES20.GL_RGBA,
                GLES20.GL_UNSIGNED_BYTE, mProductTexture!!.mData)

        // Updates the current Render State
        renderState = RS_NORMAL
    }


    // Called to draw the current frame.
    override fun onDrawFrame(gl: GL10)
    {
        if (!mIsActive)
        {
            return
        }

        // Call our function to render content from SampleAppRenderer class
        mAppRenderer.render()
    }


    fun showAnimation3Dto2D(b: Boolean)
    {
        mShowAnimation3Dto2D = b

    }


    fun isShowing2DOverlay(b: Boolean)
    {
        mIsShowing2DOverlay = b

    }


    fun startTransition2Dto3D()
    {
        mStartAnimation2Dto3D = true
    }


    fun startTransition3Dto2D()
    {
        mStartAnimation3Dto2D = true
    }


    fun stopTransition2Dto3D()
    {
        mStartAnimation2Dto3D = true
    }


    fun stopTransition3Dto2D()
    {
        mStartAnimation3Dto2D = true
    }


    fun deleteCurrentProductTexture()
    {
        deleteCurrentProductTexture = true
    }


    fun setProductTexture(texture: Texture?)
    {
        mProductTexture = texture

    }


    fun setDPIScaleIndicator(dpiSIndicator: Float)
    {
        mDPIScaleIndicator = dpiSIndicator

    }


    fun setScaleFactor(f: Float)
    {
        mScaleFactor = f

    }


    fun resetTrackingStarted()
    {
        mTrackingStarted = false

    }

    companion object
    {

        // Texture is Generated and Target is Acquired - Rendering Book Data
        val RS_NORMAL = 0

        // Target has been lost - Rendering transition to 2D Overlay
        val RS_TRANSITION_TO_2D = 1

        // Target has been reacquired - Rendering transition to 3D
        val RS_TRANSITION_TO_3D = 2

        // New Target has been found - Loading book data and generating OpenGL
        // Textures
        val RS_LOADING = 3

        // Texture with book data has been generated in Java - Ready to be generated
        // in OpenGL in the renderFrame thread
        val RS_TEXTURE_GENERATED = 4

        // Books is active and scanning - Searching for targets.
        val RS_SCANNING = 5
    }
}
