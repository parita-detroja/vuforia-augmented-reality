package com.example.root.augmentedreality.activity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.DisplayMetrics
import android.util.Log
import android.view.*
import android.view.ViewGroup.LayoutParams
import android.widget.RelativeLayout
import com.example.root.augmentedreality.R
import com.example.root.augmentedreality.renderer.UserDefinedTargetRenderer
import com.example.root.augmentedreality.utility.Constant
import com.example.root.augmentedreality.utility.LoadingDialogHandler
import com.example.root.augmentedreality.utility.RefFreeFrame
import com.example.root.augmentedreality.utility.Texture
import com.example.root.augmentedreality.vuforia.ApplicationControl
import com.example.root.augmentedreality.vuforia.ApplicationException
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.example.root.textrecognitionar.utils.ApplicationGLView
import com.vuforia.*
import kotlinx.android.synthetic.main.camera_overlay_udt.*
import java.util.*

class UserDefinedTargetsActivity : Activity(), ApplicationControl {

    var objectStatus: Boolean = false
    var count = 0

    override fun doDeInitTrackerData(): Boolean {
        // Indicate if the trackers were deinitialized correctly
        val result = true

        if (refFreeFrame != null)
            refFreeFrame!!.deInit()

        val tManager = TrackerManager.getInstance()
        tManager.deinitTracker(ObjectTracker.getClassType())

        return result
    }

    override fun doStopTracker(): Boolean {
        // Indicate if the trackers were stopped correctly
        val result = true

        val objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType())
        objectTracker?.stop()

        return result
    }

    override fun doStartTracker(): Boolean {
        // Indicate if the trackers were started correctly
        val result = true

        val objectTracker = TrackerManager.getInstance().getTracker(
                ObjectTracker.getClassType())
        objectTracker?.start()

        return result
    }

    private var vuforiaAppSession: ApplicationSession? = null

    // Our OpenGL view:
    private var mGlView: ApplicationGLView? = null

    // Our renderer:
    private var mRenderer: UserDefinedTargetRenderer? = null

    // The textures we will use for rendering:
    private var mTextures: Vector<Texture>? = null

    // View overlays to be displayed in the Augmented View
    private var mUILayout: RelativeLayout? = null
    private var mBottomBar: View? = null
    private var mCameraButton: View? = null
    private var mClearButton: View? = null
    private var mScaleButton: View? = null
    private var mRotateButton: View? = null

    // Alert dialog for displaying SDK errors
    private var mDialog: AlertDialog? = null

    internal var targetBuilderCounter = 1

    internal var dataSetUserDef: DataSet? = null

    private var mGestureDetector: GestureDetector? = null

    //private var mSettingsAdditionalViews: ArrayList<View>? = null

    private var mExtendedTracking = false

    val loadingDialogHandler = LoadingDialogHandler(
            this)

    internal var refFreeFrame: RefFreeFrame? = null

    // Alert Dialog used to display SDK errors
    private var mErrorDialog: AlertDialog? = null

    internal var mIsDroidDevice = false

    private val TOUCH_SCALE_FACTOR = 180.0f / 320
    private var mPreviousX: Float = 0.toFloat()
    private var mPreviousY: Float = 0.toFloat()

    private var mScaleDetector: ScaleGestureDetector? = null
    private var mScaleFactor = 1f

    // Called when the activity first starts or needs to be recreated after
    // resuming the application or a configuration change.
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOGTAG, "onCreate")
        super.onCreate(savedInstanceState)

        setContentView(R.layout.camera_overlay_udt)

        mScaleDetector = ScaleGestureDetector(applicationContext, ScaleListener())

        requestPermission()

        vuforiaAppSession = ApplicationSession(this)

        vuforiaAppSession!!
                .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        // Load any sample specific textures:
        mTextures = Vector<Texture>()

        loadTextures()

        mGestureDetector = GestureDetector(this, GestureListener())

        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
                "droid")

    }

    inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            mScaleFactor *= detector.scaleFactor

            // Don't let the object get too small or too large.
            mScaleFactor = Math.max(0.1f, Math.min(mScaleFactor, 5.0f))

            Log.e("Image target","scale factor : $mScaleFactor")

            mRenderer!!.setScalFactor(mScaleFactor)

            return true
        }
    }

    fun requestPermission()
    {
        if (ContextCompat.checkSelfPermission(this@UserDefinedTargetsActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@UserDefinedTargetsActivity,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), 0)
        }
    }

    // Process Single Tap event to trigger autofocus
    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        // Used to set autofocus one second after a manual focus is triggered
        private val autofocusHandler = Handler()


        override fun onDown(e: MotionEvent): Boolean {
            return true
        }


        override fun onSingleTapUp(e: MotionEvent): Boolean {
            // Generates a Handler to trigger autofocus
            // after 1 second
            autofocusHandler.postDelayed({
                val result = CameraDevice.getInstance().setFocusMode(
                        CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO)

                if (!result)
                    Log.e("SingleTapUp", "Unable to trigger focus")
            }, 1000L)

            return true
        }
    }


    // We want to load specific textures from the APK, which we will later use
    // for rendering.
    private fun loadTextures() {
        mTextures!!.add(Texture.loadTextureFromApk("moving_target.png",
                assets))
    }


    // Called when the activity will start interacting with the user.
    override fun onResume() {
        Log.d(LOGTAG, "onResume")
        super.onResume()

        // This is needed for some Droid devices to force portrait
        if (mIsDroidDevice) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }

        try {
            vuforiaAppSession!!.resumeAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.string)
        }

        // Resume the GL view:
        if (mGlView != null) {
            mGlView!!.visibility = View.VISIBLE
            mGlView!!.onResume()
        }

    }


    // Called when the system is about to start resuming a previous activity.
    override fun onPause() {
        Log.d(LOGTAG, "onPause")
        super.onPause()

        if (mGlView != null) {
            mGlView!!.visibility = View.INVISIBLE
            mGlView!!.onPause()
        }

        try {
            vuforiaAppSession!!.pauseAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.string)
        }

    }


    // The final call you receive before your activity is destroyed.
    override fun onDestroy() {
        Log.d(LOGTAG, "onDestroy")
        super.onDestroy()

        Constant.rotateScaleIndicatorFlag = -1

        try {
            vuforiaAppSession!!.stopAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.string)
        }

        // Unload texture:
        mTextures!!.clear()
        mTextures = null

        System.gc()
    }


    // Callback for configuration changes the activity handles itself
    override fun onConfigurationChanged(config: Configuration) {
        Log.d(LOGTAG, "onConfigurationChanged")
        super.onConfigurationChanged(config)

        vuforiaAppSession!!.onConfigurationChanged()

        mRenderer!!.updateConfiguration()
        // Removes the current layout and inflates a proper layout
        // for the new screen orientation

        if (mUILayout != null) {
            mUILayout!!.removeAllViews()
            (mUILayout!!.parent as ViewGroup).removeView(mUILayout)

        }

        addOverlayView(false)
    }


    // Shows error message in a system dialog box
    private fun showErrorDialog() {
        if (mDialog != null && mDialog!!.isShowing)
            mDialog!!.dismiss()

        mDialog = AlertDialog.Builder(this@UserDefinedTargetsActivity).create()
        val clickListener = DialogInterface.OnClickListener { dialog, _ -> dialog.dismiss() }

        mDialog!!.setButton(DialogInterface.BUTTON_POSITIVE,
                getString(R.string.button_OK), clickListener)

        mDialog!!.setTitle(getString(R.string.target_quality_error_title))

        val message = getString(R.string.target_quality_error_desc)

        // Show dialog box with error message:
        mDialog!!.setMessage(message)
        mDialog!!.show()
    }


    // Shows error message in a system dialog box on the UI thread
    internal fun showErrorDialogInUIThread() {
        runOnUiThread { showErrorDialog() }
    }


    // Initializes AR application components.
    private fun initApplicationAR() {
        // Do application initialization
        refFreeFrame = RefFreeFrame(this, vuforiaAppSession!!)
        refFreeFrame!!.init()

        // Create OpenGL ES view:
        val depthSize = 16
        val stencilSize = 0
        val translucent = Vuforia.requiresAlpha()

        mGlView = ApplicationGLView(this)
        mGlView!!.init(translucent, depthSize, stencilSize)

        mRenderer = UserDefinedTargetRenderer(this, vuforiaAppSession!!)
        mRenderer!!.setTextures(mTextures!!)
        mGlView!!.setRenderer(mRenderer)
        addOverlayView(true)

    }


    // Adds the Overlay view to the GLView
    private fun addOverlayView(initLayout: Boolean) {
        // Inflates the Overlay Layout to be displayed above the Camera View
        val inflater = LayoutInflater.from(this)
        mUILayout = inflater.inflate(
                R.layout.camera_overlay_udt, null, false) as RelativeLayout

        mUILayout!!.visibility = View.VISIBLE

        // If this is the first time that the application runs then the
        // uiLayout background is set to BLACK color, will be set to
        // transparent once the SDK is initialized and camera ready to draw
        if (initLayout) {
            mUILayout!!.setBackgroundColor(Color.BLACK)
        }

        // Adds the inflated layout to the view
        addContentView(mUILayout, LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT))

        // Gets a reference to the bottom navigation bar
        mBottomBar = mUILayout!!.findViewById(R.id.bottom_bar)

        // Gets a reference to the Camera button
        mCameraButton = mUILayout!!.findViewById(R.id.camera_button)

        mClearButton = mUILayout!!.findViewById(R.id.btn_clear)
        mScaleButton = mUILayout!!.findViewById(R.id.btn_scale_object)
        mRotateButton = mUILayout!!.findViewById(R.id.btn_rotate_object)

        // Gets a reference to the loading dialog container
        loadingDialogHandler.mLoadingDialogContainer = mUILayout!!
                .findViewById(R.id.loading_layout)

        startUserDefinedTargets()
        initializeBuildTargetModeViews()

        mUILayout!!.bringToFront()
    }


    // Button Camera clicked
    fun onCameraClick(v: View) {
        if (isUserDefinedTargetsRunning) {
            // Shows the loading dialog
            loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG)

            // Builds the new target
            startBuild()
        }
    }

    // Button screen shot clicked
    fun onScreenshotClick(v: View) {
        count = 1
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.SHOW_LOADING_DIALOG)
    }

    fun onClearClick(v: View)
    {
        Constant.rotateScaleIndicatorFlag = -1
    }

    fun onRotateClick(v: View)
    {
        Constant.rotateScaleIndicatorFlag = Constant.ROTATIONFLAG
    }

    fun onScaleClick(v: View)
    {
        Constant.rotateScaleIndicatorFlag = Constant.SCALEFLAG
    }

    // Creates a texture given the filename
    internal fun createTexture(nName: String): Texture {
        return Texture.loadTextureFromApk(nName, assets)!!
    }


    // Callback function called when the target creation finished
    internal fun targetCreated() {
        // Hides the loading dialog
        loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG)

        if (refFreeFrame != null) {
            refFreeFrame!!.reset()
        }

    }


    // Initialize views
    private fun initializeBuildTargetModeViews() {
        // Shows the bottom bar
        mBottomBar!!.visibility = View.VISIBLE
        mCameraButton!!.visibility = View.VISIBLE
    }

    internal fun startUserDefinedTargets(): Boolean {
        Log.d(LOGTAG, "startUserDefinedTargets")

        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker?
        if (objectTracker != null) {
            val targetBuilder = objectTracker
                    .imageTargetBuilder

            if (targetBuilder != null) {
                // if needed, stop the target builder
                if (targetBuilder.frameQuality != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE)
                    targetBuilder.stopScan()

                objectTracker.stop()

                targetBuilder.startScan()

            }
        } else
            return false

        return true
    }


    internal val isUserDefinedTargetsRunning: Boolean
        get() {
            val trackerManager = TrackerManager.getInstance()
            val objectTracker = trackerManager
                    .getTracker(ObjectTracker.getClassType()) as ObjectTracker?

            if (objectTracker != null) {
                val targetBuilder = objectTracker
                        .imageTargetBuilder
                if (targetBuilder != null) {
                    Log.e(LOGTAG, "Quality> " + targetBuilder.frameQuality)
                    return targetBuilder.frameQuality != ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_NONE
                }
            }

            return false
        }


    internal fun startBuild() {
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker?

        if (objectTracker != null) {
            val targetBuilder = objectTracker
                    .imageTargetBuilder
            if (targetBuilder != null) {
                if (targetBuilder.frameQuality == ImageTargetBuilder.FRAME_QUALITY.FRAME_QUALITY_LOW) {
                    showErrorDialogInUIThread()
                }

                var name: String
                do {
                    name = "UserTarget-" + targetBuilderCounter
                    Log.d(LOGTAG, "TRYING " + name)
                    targetBuilderCounter++
                } while (!targetBuilder.build(name, 320.0f))

                refFreeFrame!!.setCreating()
            }

            objectStatus = true
        }
    }


    internal fun updateRendering() {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        refFreeFrame!!.initGL(metrics.widthPixels, metrics.heightPixels)
    }


    override fun doInitTrackers(): Boolean {
        // Indicate if the trackers were initialized correctly
        var result = true

        // Initialize the image tracker:
        val trackerManager = TrackerManager.getInstance()
        val tracker = trackerManager.initTracker(ObjectTracker
                .getClassType())
        if (tracker == null) {
            Log.d(LOGTAG, "Failed to initialize ObjectTracker.")
            result = false
        } else {
            Log.d(LOGTAG, "Successfully initialized ObjectTracker.")
        }

        return result
    }


    override fun doLoadTrackersData(): Boolean {
        // Get the image tracker:
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker?
        if (objectTracker == null) {
            Log.d(
                    LOGTAG,
                    "Failed to load tracking data set because the ObjectTracker has not been initialized.")
            return false
        }

        // Create the data set:
        dataSetUserDef = objectTracker.createDataSet()
        if (dataSetUserDef == null) {
            Log.d(LOGTAG, "Failed to create a new tracking data.")
            return false
        }

        if (!objectTracker.activateDataSet(dataSetUserDef)) {
            Log.d(LOGTAG, "Failed to activate data set.")
            return false
        }

        Log.d(LOGTAG, "Successfully loaded and activated data set.")
        return true
    }

    override fun doUnloadTrackersData(): Boolean {
        // Indicate if the trackers were unloaded correctly
        var result = true

        // Get the image tracker:
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker?
        if (objectTracker == null) {
            result = false
            Log.d(
                    LOGTAG,
                    "Failed to destroy the tracking data set because the ObjectTracker has not been initialized.")
        }

        if (dataSetUserDef != null) {
            if (objectTracker!!.getActiveDataSet(0) != null && !objectTracker.deactivateDataSet(dataSetUserDef)) {
                Log.d(
                        LOGTAG,
                        "Failed to destroy the tracking data set because the data set could not be deactivated.")
                result = false
            }

            if (!objectTracker.destroyDataSet(dataSetUserDef)) {
                Log.d(LOGTAG, "Failed to destroy the tracking data set.")
                result = false
            }

            Log.d(LOGTAG, "Successfully destroyed the data set.")
            dataSetUserDef = null
        }

        return result
    }

    override fun onInitARDone(mApplicationException: ApplicationException?) {

        if (mApplicationException == null) {
            initApplicationAR()

            mRenderer!!.setActive(true)

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, LayoutParams(LayoutParams.MATCH_PARENT,
                    LayoutParams.MATCH_PARENT))

            // Sets the UILayout to be drawn in front of the camera
            mUILayout!!.bringToFront()

            // Hides the Loading Dialog
            loadingDialogHandler
                    .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG)

            // Sets the layout background to transparent
            mUILayout!!.setBackgroundColor(Color.TRANSPARENT)

            try {
                vuforiaAppSession!!.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT)
            } catch (e: ApplicationException) {
                Log.e(LOGTAG, e.string)
            }

            val result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO)

            if (!result)
                Log.e(LOGTAG, "Unable to enable continuous autofocus")

            val mOnTouchListener = View.OnTouchListener { view, motionEvent ->

                // MotionEvent reports input details from the touch screen
                // and other input controls. In this case, you are only
                // interested in events where the touch position changed.

                Log.e("Image Target","on touch called")
                Log.e("Flag","value : ${Constant.rotateScaleIndicatorFlag}")

                if(Constant.rotateScaleIndicatorFlag == Constant.SCALEFLAG)
                {
                    mScaleDetector!!.onTouchEvent(motionEvent)
                }
                else if(Constant.rotateScaleIndicatorFlag == Constant.ROTATIONFLAG)
                {
                    val x = motionEvent.x
                    val y = motionEvent.y

                    when (motionEvent.action) {
                        MotionEvent.ACTION_MOVE -> {

                            Log.e("Image Target","on motion event")

                            var dx = x - mPreviousX
                            var dy = y - mPreviousY

                            // reverse direction of rotation above the mid-line
                            if (y > view.height / 2) {
                                dx *= +1
                            }

                            // reverse direction of rotation to left of the mid-line
                            if (x < view.width / 2) {
                                dy *= +1
                            }

                            mRenderer!!.setAngle(
                                    mRenderer!!.getAngle() + (dx + dy) * TOUCH_SCALE_FACTOR)

                            Log.e("Image Target","Angle Changed")

                            mGlView!!.requestRender()
                        }
                    }

                    mPreviousX = x
                    mPreviousY = y

                }
                true
            }

            mGlView!!.setOnTouchListener(mOnTouchListener)

        } else {
            Log.e(LOGTAG, mApplicationException.string)
            showInitializationErrorMessage(mApplicationException.string)
        }
    }


    // Shows initialization error messages as System dialogs
    fun showInitializationErrorMessage(message: String) {
        val errorMessage = message
        runOnUiThread {
            if (mErrorDialog != null) {
                mErrorDialog!!.dismiss()
            }

            // Generates an Alert Dialog to show the error message
            val builder = AlertDialog.Builder(
                    this@UserDefinedTargetsActivity)
            builder
                    .setMessage(errorMessage)
                    .setTitle(getString(R.string.INIT_ERROR))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK),
                            { _, _ -> finish() })

            mErrorDialog = builder.create()
            mErrorDialog!!.show()
        }
    }


    override fun onVuforiaUpdate(state: State) {
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker

        if (refFreeFrame!!.hasNewTrackableSource()) {
            Log.d(LOGTAG,
                    "Attempting to transfer the trackable source to the dataset")

            // Deactivate current dataset
            objectTracker.deactivateDataSet(objectTracker.getActiveDataSet(0))

            // Clear the oldest target if the dataset is full or the dataset
            // already contains five user-defined targets.
            if (dataSetUserDef!!.hasReachedTrackableLimit() || dataSetUserDef!!.numTrackables >= 5)
                dataSetUserDef!!.destroy(dataSetUserDef!!.getTrackable(0))

            if (mExtendedTracking && dataSetUserDef!!.numTrackables > 0) {
                // We need to stop the extended tracking for the previous target
                // so we can enable it for the new one
                val previousCreatedTrackableIndex = dataSetUserDef!!.numTrackables - 1

                objectTracker.resetExtendedTracking()
                dataSetUserDef!!.getTrackable(previousCreatedTrackableIndex)
                        .stopExtendedTracking()
            }

            // Add new trackable source
            val trackable = dataSetUserDef!!
                    .createTrackable(refFreeFrame!!.getNewTrackableSource())

            // Reactivate current dataset
            objectTracker.activateDataSet(dataSetUserDef)

            if (mExtendedTracking) {
                trackable.startExtendedTracking()
            }

        }
    }

    // This method sets the additional views to be moved along with the GLView
    /*private fun setSampleAppMenuAdditionalViews() {
        mSettingsAdditionalViews = ArrayList<View>()
        mSettingsAdditionalViews!!.add(mBottomBar!!)
    }

    fun menuProcess(command: Int): Boolean {
        var result = true

        when (command) {
            CMD_BACK -> finish()

            CMD_EXTENDED_TRACKING -> {
                if (dataSetUserDef!!.numTrackables > 0) {
                    val lastTrackableCreatedIndex = dataSetUserDef!!.numTrackables - 1

                    val trackable = dataSetUserDef!!
                            .getTrackable(lastTrackableCreatedIndex)

                    if (!mExtendedTracking) {
                        if (!trackable.startExtendedTracking()) {
                            Log.e(LOGTAG,
                                    "Failed to start extended tracking target")
                            result = false
                        } else {
                            Log.d(LOGTAG,
                                    "Successfully started extended tracking target")
                        }
                    } else {
                        if (!trackable.stopExtendedTracking()) {
                            Log.e(LOGTAG,
                                    "Failed to stop extended tracking target")
                            result = false
                        } else {
                            Log.d(LOGTAG,
                                    "Successfully stopped extended tracking target")
                        }
                    }
                }

                if (result)
                    mExtendedTracking = !mExtendedTracking
            }
        }

        return result
    }*/

    companion object {
        private val LOGTAG = "UserDefinedTargets"

        //val CMD_BACK = -1
        //val CMD_EXTENDED_TRACKING = 1
    }

}
