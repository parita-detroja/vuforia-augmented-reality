package com.example.root.augmentedreality.vuforia

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Point
import android.opengl.GLES20
import android.opengl.Matrix
import android.os.Build
import android.util.Log
import com.example.root.augmentedreality.utility.SampleUtils
import com.example.root.textrecognitionar.utils.VideoBackgroundShader
import com.vuforia.*

class AppRenderer(renderingInterface: AppRendererControl, activity: Activity, deviceMode: Int,
                  stereo: Boolean, nearPlane: Float, farPlane: Float) {

    private var mRenderingPrimitives: RenderingPrimitives? = null
    private var mRenderingInterface: AppRendererControl? = null
    private var mActivity: Activity? = null

    private var mRenderer: Renderer? = null
    private var currentView = VIEW.VIEW_SINGULAR
    private var mNearPlane = -1.0f
    private var mFarPlane = -1.0f

    private var videoBackgroundTex: GLTextureUnit? = null

    // Shader user to render the video background on AR mode
    private var vbShaderProgramID = 0
    private var vbTexSampler2DHandle = 0
    private var vbVertexHandle = 0
    private var vbTexCoordHandle = 0
    private var vbProjectionMatrixHandle = 0

    // Display size of the device:
    private var mScreenWidth = 0
    private var mScreenHeight = 0

    // Stores orientation
    private var mIsPortrait = false

    var mAngle: Float = 0.toFloat()

    //var scaleFactor: Float = 1.toFloat()

    init {
        mActivity = activity

        mRenderingInterface = renderingInterface
        mRenderer = Renderer.getInstance()

        if (farPlane < nearPlane) {
            Log.e(LOGTAG, "Far plane should be greater than near plane")
            throw IllegalArgumentException()
        }

        setNearFarPlanes(nearPlane, farPlane)

        if (deviceMode != Device.MODE.MODE_AR && deviceMode != Device.MODE.MODE_VR) {
            Log.e(LOGTAG, "Device mode should be Device.MODE.MODE_AR or Device.MODE.MODE_VR")
            throw IllegalArgumentException()
        }

        val device = Device.getInstance()
        device.isViewerActive = stereo // Indicates if the app will be using a viewer, stereo mode and initializes the rendering primitives
        device.mode = deviceMode // Select if we will be in AR or VR mode
    }

    fun onSurfaceCreated() {
        initRendering()
    }

    fun onConfigurationChanged(isARActive: Boolean) {
        updateActivityOrientation()
        storeScreenDimensions()

        if (isARActive)
            configureVideoBackground()

        mRenderingPrimitives = Device.getInstance().renderingPrimitives
    }

    internal fun initRendering() {
        vbShaderProgramID = SampleUtils.createProgramFromShaderSrc(VideoBackgroundShader.VB_VERTEX_SHADER,
                VideoBackgroundShader.VB_FRAGMENT_SHADER)

        // Rendering configuration for video background
        if (vbShaderProgramID > 0) {
            // Activate shader:
            GLES20.glUseProgram(vbShaderProgramID)

            // Retrieve handler for texture sampler shader uniform variable:
            vbTexSampler2DHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "texSampler2D")

            // Retrieve handler for projection matrix shader uniform variable:
            vbProjectionMatrixHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "projectionMatrix")

            vbVertexHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexPosition")
            vbTexCoordHandle = GLES20.glGetAttribLocation(vbShaderProgramID, "vertexTexCoord")
            vbProjectionMatrixHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "projectionMatrix")
            vbTexSampler2DHandle = GLES20.glGetUniformLocation(vbShaderProgramID, "texSampler2D")

            // Stop using the program
            GLES20.glUseProgram(0)
        }

        videoBackgroundTex = GLTextureUnit()
    }

    // Main rendering method
    // The method setup state for rendering, setup 3D transformations required for AR augmentation
    // and call any specific rendering method
    fun render() {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        val state: State = TrackerManager.getInstance().stateUpdater.updateState()
        // Get our current state
        mRenderer!!.begin(state)

        // We must detect if background reflection is active and adjust the
        // culling direction.
        // If the reflection is active, this means the post matrix has been
        // reflected as well,
        // therefore standard counter clockwise face culling will result in
        // "inside out" models.
        if (Renderer.getInstance().videoBackgroundConfig.reflection == VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES20.glFrontFace(GLES20.GL_CW)  // Front camera
        else
            GLES20.glFrontFace(GLES20.GL_CCW)   // Back camera

        // We get a list of views which depend on the mode we are working on, for mono we have
        // only one view, in stereo we have three: left, right and postprocess
        val viewList = mRenderingPrimitives!!.renderingViews

        // Cycle through the view list
        for (v in 0..viewList.numViews - 1) {
            // Get the view id
            val viewID = viewList.getView(v.toInt())

            val viewport: Vec4I
            // Get the viewport for that specific view
            viewport = mRenderingPrimitives!!.getViewport(viewID)

            // Set viewport for current view
            GLES20.glViewport(viewport.data[0], viewport.data[1], viewport.data[2], viewport.data[3])

            // Set scissor
            GLES20.glScissor(viewport.data[0], viewport.data[1], viewport.data[2], viewport.data[3])

            // Get projection matrix for the current view. COORDINATE_SYSTEM_CAMERA used for AR and
            // COORDINATE_SYSTEM_WORLD for VR
            val projMatrix = mRenderingPrimitives!!.getProjectionMatrix(viewID, COORDINATE_SYSTEM_TYPE.COORDINATE_SYSTEM_CAMERA)

            // Create GL matrix setting up the near and far planes
            val rawProjectionMatrixGL = Tool.convertPerspectiveProjection2GLMatrix(
                    projMatrix,
                    mNearPlane,
                    mFarPlane)
                    .data

            // Apply the appropriate eye adjustment to the raw projection matrix, and assign to the global variable
            val eyeAdjustmentGL = Tool.convert2GLMatrix(mRenderingPrimitives!!
                    .getEyeDisplayAdjustmentMatrix(viewID)).data

            val projectionMatrix = FloatArray(16)

            // Create a rotation for the object
            Matrix.rotateM(eyeAdjustmentGL, 0, mAngle, 0f, 0f, -1.0f)

            //scale image according to scale factor
            /*Matrix.scaleM(projectionMatrix,0,scaleFactor,scaleFactor,1f)
            Log.e("From image target","scaleing applied")*/

            // Apply the adjustment to the projection matrix
            Matrix.multiplyMM(projectionMatrix, 0, rawProjectionMatrixGL, 0, eyeAdjustmentGL, 0)

            currentView = viewID

            // Call renderFrame from the app renderer class which implements SampleAppRendererControl
            // This will be called for MONO, LEFT and RIGHT views, POSTPROCESS will not render the
            // frame
            if (currentView != VIEW.VIEW_POSTPROCESS)
                mRenderingInterface!!.renderFrame(state, projectionMatrix)
        }

        mRenderer!!.end()
    }

    fun setNearFarPlanes(near: Float, far: Float) {
        mNearPlane = near
        mFarPlane = far
    }

    fun renderVideoBackground() {
        if (currentView == VIEW.VIEW_POSTPROCESS)
            return

        val vbVideoTextureUnit = 0
        // Bind the video bg texture and get the Texture ID from Vuforia
        videoBackgroundTex!!.textureUnit = vbVideoTextureUnit
        if (!mRenderer!!.updateVideoBackgroundTexture(videoBackgroundTex)) {
            Log.e(LOGTAG, "Unable to update video background texture")
            return
        }

        val vbProjectionMatrix = Tool.convert2GLMatrix(
                mRenderingPrimitives!!.getVideoBackgroundProjectionMatrix(currentView, COORDINATE_SYSTEM_TYPE.COORDINATE_SYSTEM_CAMERA)).data

        // Apply the scene scale on video see-through eyewear, to scale the video background and augmentation
        // so that the display lines up with the real world
        // This should not be applied on optical see-through devices, as there is no video background,
        // and the calibration ensures that the augmentation matches the real world
        if (Device.getInstance().isViewerActive) {
            val sceneScaleFactor = sceneScaleFactor.toFloat()
            Matrix.scaleM(vbProjectionMatrix, 0, sceneScaleFactor, sceneScaleFactor, 1.0f)
        }

        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDisable(GLES20.GL_CULL_FACE)
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST)

        val vbMesh = mRenderingPrimitives!!.getVideoBackgroundMesh(currentView)
        // Load the shader and upload the vertex/texcoord/index data
        GLES20.glUseProgram(vbShaderProgramID)
        GLES20.glVertexAttribPointer(vbVertexHandle, 3, GLES20.GL_FLOAT, false, 0, vbMesh.positions.asFloatBuffer())
        GLES20.glVertexAttribPointer(vbTexCoordHandle, 2, GLES20.GL_FLOAT, false, 0, vbMesh.uVs.asFloatBuffer())

        GLES20.glUniform1i(vbTexSampler2DHandle, vbVideoTextureUnit)

        // Render the video background with the custom shader
        // First, we enable the vertex arrays
        GLES20.glEnableVertexAttribArray(vbVertexHandle)
        GLES20.glEnableVertexAttribArray(vbTexCoordHandle)

        // Pass the projection matrix to OpenGL
        GLES20.glUniformMatrix4fv(vbProjectionMatrixHandle, 1, false, vbProjectionMatrix, 0)

        // Then, we issue the render call
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, vbMesh.numTriangles * 3, GLES20.GL_UNSIGNED_SHORT,
                vbMesh.triangles.asShortBuffer())

        // Finally, we disable the vertex arrays
        GLES20.glDisableVertexAttribArray(vbVertexHandle)
        GLES20.glDisableVertexAttribArray(vbTexCoordHandle)

        SampleUtils.checkGLError("Rendering of the video background failed")
    }

    internal // Get the y-dimension of the physical camera field of view
            // Get the y-dimension of the virtual camera field of view
            // The scene-scale factor represents the proportion of the viewport that is filled by
            // the video background when projected onto the same plane.
            // In order to calculate this, let 'd' be the distance between the cameras and the plane.
            // The height of the projected image 'h' on this plane can then be calculated:
            //   tan(fov/2) = h/2d
            // which rearranges to:
            //   2d = h/tan(fov/2)
            // Since 'd' is the same for both cameras, we can combine the equations for the two cameras:
            //   hPhysical/tan(fovPhysical/2) = hVirtual/tan(fovVirtual/2)
            // Which rearranges to:
            //   hPhysical/hVirtual = tan(fovPhysical/2)/tan(fovVirtual/2)
            // ... which is the scene-scale factor
    val sceneScaleFactor: Double
        get() {
            val fovVector = CameraDevice.getInstance().cameraCalibration.fieldOfViewRads
            val cameraFovYRads = fovVector.data[1]
            val virtualFovYRads = VIRTUAL_FOV_Y_DEGS * M_PI / 180
            return Math.tan((cameraFovYRads / 2).toDouble()) / Math.tan((virtualFovYRads / 2).toDouble())
        }

    // Configures the video mode and sets offsets for the camera's image
    fun configureVideoBackground() {
        val cameraDevice = CameraDevice.getInstance()
        val vm = cameraDevice.getVideoMode(CameraDevice.MODE.MODE_DEFAULT)

        val config = VideoBackgroundConfig()
        config.enabled = true
        config.position = Vec2I(0, 0)

        var xSize = 0
        var ySize = 0
        // We keep the aspect ratio to keep the video correctly rendered. If it is portrait we
        // preserve the height and scale width and vice versa if it is landscape, we preserve
        // the width and we check if the selected values fill the screen, otherwise we invert
        // the selection
        if (mIsPortrait) {
            xSize = (vm.height * (mScreenHeight / vm
                    .width.toFloat())).toInt()
            ySize = mScreenHeight

            if (xSize < mScreenWidth) {
                xSize = mScreenWidth
                ySize = (mScreenWidth * (vm.width / vm
                        .height.toFloat())).toInt()
            }
        } else {
            xSize = mScreenWidth
            ySize = (vm.height * (mScreenWidth / vm
                    .width.toFloat())).toInt()

            if (ySize < mScreenHeight) {
                xSize = (mScreenHeight * (vm.width / vm
                        .height.toFloat())).toInt()
                ySize = mScreenHeight
            }
        }

        config.size = Vec2I(xSize, ySize)

        Log.i(LOGTAG, "Configure Video Background : Video (" + vm.width
                + " , " + vm.height + "), Screen (" + mScreenWidth + " , "
                + mScreenHeight + "), mSize (" + xSize + " , " + ySize + ")")

        Renderer.getInstance().videoBackgroundConfig = config

    }


    // Stores screen dimensions
    private fun storeScreenDimensions() {
        // Query display dimensions:
        val size = Point()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mActivity!!.windowManager.defaultDisplay.getRealSize(size)
        }
        mScreenWidth = size.x
        mScreenHeight = size.y
    }


    // Stores the orientation depending on the current resources configuration
    private fun updateActivityOrientation() {
        val config = mActivity!!.resources.configuration

        when (config.orientation) {
            Configuration.ORIENTATION_PORTRAIT -> mIsPortrait = true
            Configuration.ORIENTATION_LANDSCAPE -> mIsPortrait = false
        }

        Log.i(LOGTAG, "Activity is in " + if (mIsPortrait) "PORTRAIT" else "LANDSCAPE")
    }

    companion object {

        private val LOGTAG = "AppRenderer"


        internal val VIRTUAL_FOV_Y_DEGS = 85.0f
        internal val M_PI = 3.14159f
    }
}
