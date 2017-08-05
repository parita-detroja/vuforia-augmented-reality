package com.example.root.augmentedreality.activity

import android.app.Activity
import android.app.AlertDialog
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import android.view.animation.Animation
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.RelativeLayout
import com.example.root.augmentedreality.R
import com.example.root.augmentedreality.renderer.CloudRecognitionRenderer
import com.example.root.augmentedreality.utility.LoadingDialogHandler
import com.example.root.augmentedreality.utility.Texture
import com.example.root.augmentedreality.vuforia.ApplicationControl
import com.example.root.augmentedreality.vuforia.ApplicationException
import com.example.root.augmentedreality.vuforia.ApplicationSession
import com.example.root.textrecognitionar.utils.ApplicationGLView
import com.vuforia.*
import java.util.*

/**
 * Created by root on 5/8/17.
 * Cloud recognition uses target form cloud data base.
 */
class CloudRecognitionActivity : Activity(), ApplicationControl {

    override fun doStartTracker(): Boolean {
        // Indicate if the trackers were started correctly
        val result = true

        // Start the tracker:
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker
        objectTracker.start()

        // Start cloud based recognition if we are in scanning mode:
        val targetFinder = objectTracker.targetFinder
        targetFinder.startRecognition()
        scanlineStart()
        mFinderStarted = true

        return result
    }

    override fun doStopTracker(): Boolean {
        // Indicate if the trackers were stopped correctly
        var result = true

        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker?

        if (objectTracker != null) {
            objectTracker.stop()

            // Stop cloud based recognition:
            val targetFinder = objectTracker.targetFinder
            targetFinder.stop()
            scanlineStop()
            mFinderStarted = false

            // Clears the trackables
            targetFinder.clearTrackables()
        } else {
            result = false
        }

        return result
    }

    override fun doDeInitTrackerData(): Boolean {
        // Indicate if the trackers were deinitialized correctly
        val result = true

        val tManager = TrackerManager.getInstance()
        tManager.deinitTracker(ObjectTracker.getClassType())

        return result
    }

    private var vuforiaAppSession: ApplicationSession? = null

    // Our OpenGL view:
    private var mGlView: ApplicationGLView? = null

    // Our renderer:
    private var mRenderer: CloudRecognitionRenderer? = null

    private var mExtendedTracking = false
    private var mFinderStarted = false
    //private val mStopFinderIfStarted = false

    // The textures we will use for rendering:
    private var mTextures: Vector<Texture>? = null

    // View overlays to be displayed in the Augmented View
    private var mUILayout: RelativeLayout? = null

    // Error message handling:
    private var mlastErrorCode = 0
    private var mInitErrorCode = 0
    private var mFinishActivityOnError: Boolean = false

    // Alert Dialog used to display SDK errors
    private var mErrorDialog: AlertDialog? = null

    private var mGestureDetector: GestureDetector? = null

    private val loadingDialogHandler = LoadingDialogHandler(
            this)

    // declare scan line and its animation
    private var scanLine: View? = null
    private var scanAnimation: TranslateAnimation? = null

    private val mLastErrorTime: Double = 0.toDouble()

    private var mIsDroidDevice = false


    // Called when the activity first starts or needs to be recreated after
    // resuming the application or a configuration change.
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(LOGTAG, "onCreate")
        super.onCreate(savedInstanceState)

        vuforiaAppSession = ApplicationSession(this)

        startLoadingAnimation()

        vuforiaAppSession!!
                .initAR(this, ActivityInfo.SCREEN_ORIENTATION_PORTRAIT)

        // Creates the GestureDetector listener for processing double tap
        mGestureDetector = GestureDetector(this, GestureListener())

        mTextures = Vector<Texture>()
        loadTextures()

        mIsDroidDevice = android.os.Build.MODEL.toLowerCase().startsWith(
                "droid")

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
        mTextures!!.add(Texture.loadTextureFromApk("TextureTeapotRed.png",
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
            Log.e(LOGTAG, e.message)
        }

        // Resume the GL view:
        if (mGlView != null) {
            mGlView!!.visibility = View.VISIBLE
            mGlView!!.onResume()
        }

    }


    // Callback for configuration changes the activity handles itself
    override fun onConfigurationChanged(config: Configuration) {
        Log.d(LOGTAG, "onConfigurationChanged")
        super.onConfigurationChanged(config)

        vuforiaAppSession!!.onConfigurationChanged()
    }


    // Called when the system is about to start resuming a previous activity.
    override fun onPause() {
        Log.d(LOGTAG, "onPause")
        super.onPause()

        try {
            vuforiaAppSession!!.pauseAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.message)
        }

        // Pauses the OpenGLView
        if (mGlView != null) {
            mGlView!!.visibility = View.INVISIBLE
            mGlView!!.onPause()
        }
    }


    // The final call you receive before your activity is destroyed.
    override fun onDestroy() {
        Log.d(LOGTAG, "onDestroy")
        super.onDestroy()

        try {
            vuforiaAppSession!!.stopAR()
        } catch (e: ApplicationException) {
            Log.e(LOGTAG, e.message)
        }

        System.gc()
    }


    /*fun deinitCloudReco() {
        // Get the object tracker:
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker
        if (objectTracker == null) {
            Log.e(LOGTAG,
                    "Failed to destroy the tracking data set because the ObjectTracker has not" + " been initialized.")
            return
        }

        // Deinitialize target finder:
        val finder = objectTracker.targetFinder
        finder.deinit()
    }*/


    private fun startLoadingAnimation() {
        // Inflates the Overlay Layout to be displayed above the Camera View
        val inflater = LayoutInflater.from(this)
        mUILayout = inflater.inflate(R.layout.camera_overlay_with_scanline, null, false) as RelativeLayout

        mUILayout!!.visibility = View.VISIBLE
        mUILayout!!.setBackgroundColor(Color.BLACK)

        // By default
        loadingDialogHandler.mLoadingDialogContainer = mUILayout!!
                .findViewById(R.id.loading_indicator)
        loadingDialogHandler.mLoadingDialogContainer.visibility = View.VISIBLE

        scanLine = mUILayout!!.findViewById(R.id.scan_line)
        scanLine!!.visibility = View.GONE
        scanAnimation = TranslateAnimation(
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.ABSOLUTE, 0f,
                TranslateAnimation.RELATIVE_TO_PARENT, 0f,
                TranslateAnimation.RELATIVE_TO_PARENT, 1.0f)
        scanAnimation!!.duration = 4000
        scanAnimation!!.repeatCount = -1
        scanAnimation!!.repeatMode = Animation.REVERSE
        scanAnimation!!.interpolator = LinearInterpolator()

        addContentView(mUILayout, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT))

    }


    // Initializes AR application components.
    private fun initApplicationAR() {
        // Create OpenGL ES view:
        val depthSize = 16
        val stencilSize = 0
        val translucent = Vuforia.requiresAlpha()

        // Initialize the GLView with proper flags
        mGlView = ApplicationGLView(this)
        mGlView!!.init(translucent, depthSize, stencilSize)

        // Setups the Renderer of the GLView
        mRenderer = CloudRecognitionRenderer(vuforiaAppSession!!, this)
        mRenderer!!.setTextures(mTextures!!)
        mGlView!!.setRenderer(mRenderer)

    }


    // Returns the error message for each error code
    private fun getStatusDescString(code: Int): String {
        if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
            return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_DESC)
        if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
            return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_DESC)
        if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
            return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_DESC)
        if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
            return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_DESC)
        if (code == UPDATE_ERROR_UPDATE_SDK)
            return getString(R.string.UPDATE_ERROR_UPDATE_SDK_DESC)
        if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
            return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_DESC)
        if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
            return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_DESC)
        if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
            return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_DESC)
        else {
            return getString(R.string.UPDATE_ERROR_UNKNOWN_DESC)
        }
    }


    // Returns the error message for each error code
    private fun getStatusTitleString(code: Int): String {
        if (code == UPDATE_ERROR_AUTHORIZATION_FAILED)
            return getString(R.string.UPDATE_ERROR_AUTHORIZATION_FAILED_TITLE)
        if (code == UPDATE_ERROR_PROJECT_SUSPENDED)
            return getString(R.string.UPDATE_ERROR_PROJECT_SUSPENDED_TITLE)
        if (code == UPDATE_ERROR_NO_NETWORK_CONNECTION)
            return getString(R.string.UPDATE_ERROR_NO_NETWORK_CONNECTION_TITLE)
        if (code == UPDATE_ERROR_SERVICE_NOT_AVAILABLE)
            return getString(R.string.UPDATE_ERROR_SERVICE_NOT_AVAILABLE_TITLE)
        if (code == UPDATE_ERROR_UPDATE_SDK)
            return getString(R.string.UPDATE_ERROR_UPDATE_SDK_TITLE)
        if (code == UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE)
            return getString(R.string.UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE_TITLE)
        if (code == UPDATE_ERROR_REQUEST_TIMEOUT)
            return getString(R.string.UPDATE_ERROR_REQUEST_TIMEOUT_TITLE)
        if (code == UPDATE_ERROR_BAD_FRAME_QUALITY)
            return getString(R.string.UPDATE_ERROR_BAD_FRAME_QUALITY_TITLE)
        else {
            return getString(R.string.UPDATE_ERROR_UNKNOWN_TITLE)
        }
    }


    // Shows error messages as System dialogs
    fun showErrorMessage(errorCode: Int, errorTime: Double, finishActivityOnError: Boolean) {
        if (errorTime < mLastErrorTime + 5.0 || errorCode == mlastErrorCode)
            return

        mlastErrorCode = errorCode
        mFinishActivityOnError = finishActivityOnError

        runOnUiThread {
            if (mErrorDialog != null) {
                mErrorDialog!!.dismiss()
            }

            // Generates an Alert Dialog to show the error message
            val builder = AlertDialog.Builder(
                    this@CloudRecognitionActivity)
            builder
                    .setMessage(
                            getStatusDescString(this@CloudRecognitionActivity.mlastErrorCode))
                    .setTitle(
                            getStatusTitleString(this@CloudRecognitionActivity.mlastErrorCode))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK)
                    ) { dialog, _ ->
                        if (mFinishActivityOnError) {
                            finish()
                        } else {
                            dialog.dismiss()
                        }
                    }

            mErrorDialog = builder.create()
            mErrorDialog!!.show()
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
                    this@CloudRecognitionActivity)
            builder
                    .setMessage(errorMessage)
                    .setTitle(getString(R.string.INIT_ERROR))
                    .setCancelable(false)
                    .setIcon(0)
                    .setPositiveButton(getString(R.string.button_OK)
                    ) { _, _ -> finish() }

            mErrorDialog = builder.create()
            mErrorDialog!!.show()
        }
    }


    fun startFinderIfStopped() {
        if (!mFinderStarted) {
            mFinderStarted = true

            // Get the object tracker:
            val trackerManager = TrackerManager.getInstance()
            val objectTracker = trackerManager
                    .getTracker(ObjectTracker.getClassType()) as ObjectTracker

            // Initialize target finder:
            val targetFinder = objectTracker.targetFinder

            targetFinder.clearTrackables()
            targetFinder.startRecognition()
            scanlineStart()
        }
    }


    fun stopFinderIfStarted() {
        if (mFinderStarted) {
            mFinderStarted = false

            // Get the object tracker:
            val trackerManager = TrackerManager.getInstance()
            val objectTracker = trackerManager
                    .getTracker(ObjectTracker.getClassType()) as ObjectTracker

            // Initialize target finder:
            val targetFinder = objectTracker.targetFinder

            targetFinder.stop()
            scanlineStop()
        }
    }


    override fun doLoadTrackersData(): Boolean {
        Log.d(LOGTAG, "initCloudReco")

        // Get the object tracker:
        val trackerManager = TrackerManager.getInstance()
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker

        // Initialize target finder:
        val targetFinder = objectTracker.targetFinder

        // Start initialization:
        if (targetFinder.startInit(kAccessKey, kSecretKey)) {
            targetFinder.waitUntilInitFinished()
        }

        val resultCode = targetFinder.initState
        if (resultCode != TargetFinder.INIT_SUCCESS) {
            if (resultCode == TargetFinder.INIT_ERROR_NO_NETWORK_CONNECTION) {
                mInitErrorCode = UPDATE_ERROR_NO_NETWORK_CONNECTION
            } else {
                mInitErrorCode = UPDATE_ERROR_SERVICE_NOT_AVAILABLE
            }

            Log.e(LOGTAG, "Failed to initialize target finder.")
            return false
        }

        return true
    }


    override fun doUnloadTrackersData(): Boolean {
        return true
    }


    override fun onInitARDone(mApplicationException: ApplicationException?) {

        if (mApplicationException == null) {
            initApplicationAR()

            mRenderer!!.setActive(true)

            // Now add the GL surface view. It is important
            // that the OpenGL ES surface view gets added
            // BEFORE the camera is started and video
            // background is configured.
            addContentView(mGlView, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT))

            // Start the camera:
            try {
                vuforiaAppSession!!.startAR(CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT)
            } catch (e: ApplicationException) {
                Log.e(LOGTAG, e.message)
            }

            val result = CameraDevice.getInstance().setFocusMode(
                    CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO)

            if (!result)
                Log.e(LOGTAG, "Unable to enable continuous autofocus")

            mUILayout!!.bringToFront()

            // Hides the Loading Dialog
            loadingDialogHandler.sendEmptyMessage(HIDE_LOADING_DIALOG)

            mUILayout!!.setBackgroundColor(Color.TRANSPARENT)

        } else {
            Log.e(LOGTAG, mApplicationException.message)
            if (mInitErrorCode != 0) {
                showErrorMessage(mInitErrorCode, 10.0, true)
            } else {
                showInitializationErrorMessage(mApplicationException.message!!)
            }
        }
    }


    override fun onVuforiaUpdate(state: State) {
        // Get the tracker manager:
        val trackerManager = TrackerManager.getInstance()

        // Get the object tracker:
        val objectTracker = trackerManager
                .getTracker(ObjectTracker.getClassType()) as ObjectTracker

        // Get the target finder:
        val finder = objectTracker.targetFinder

        // Check if there are new results available:
        val statusCode = finder.updateSearchResults()

        // Show a message if we encountered an error:
        if (statusCode < 0) {

            val closeAppAfterError = statusCode == UPDATE_ERROR_NO_NETWORK_CONNECTION || statusCode == UPDATE_ERROR_SERVICE_NOT_AVAILABLE

            showErrorMessage(statusCode, state.frame.timeStamp, closeAppAfterError)

        } else if (statusCode == TargetFinder.UPDATE_RESULTS_AVAILABLE) {
            // Process new search results
            if (finder.resultCount > 0) {
                val result = finder.getResult(0)

                // Check if this target is suitable for tracking:
                if (result.trackingRating > 0) {
                    val trackable = finder.enableTracking(result)

                    if (mExtendedTracking)
                        trackable.startExtendedTracking()
                }
            }
        }
    }


    override fun doInitTrackers(): Boolean {
        val tManager = TrackerManager.getInstance()
        val tracker: Tracker?

        // Indicate if the trackers were initialized correctly
        var result = true

        tracker = tManager.initTracker(ObjectTracker.getClassType())
        if (tracker == null) {
            Log.e(
                    LOGTAG,
                    "Tracker not initialized. Tracker already initialized or the camera is already started")
            result = false
        } else {
            Log.i(LOGTAG, "Tracker successfully initialized")
        }

        return result
    }

    /*fun menuProcess(command: Int): Boolean {
        var result = true

        when (command) {
            CMD_BACK -> finish()

            CMD_EXTENDED_TRACKING -> {
                val trackerManager = TrackerManager.getInstance()
                val objectTracker = trackerManager
                        .getTracker(ObjectTracker.getClassType()) as ObjectTracker

                val targetFinder = objectTracker.targetFinder

                if (targetFinder.numImageTargets == 0) {
                    result = true
                }

                for (tIdx in 0..targetFinder.numImageTargets - 1) {
                    val trackable = targetFinder.getImageTarget(tIdx)

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
                                    "Successfully started extended tracking target")
                        }
                    }
                }

                if (result)
                    mExtendedTracking = !mExtendedTracking
            }
        }

        return result
    }*/

    private fun scanlineStart() {
        this.runOnUiThread {
            scanLine!!.visibility = View.VISIBLE
            scanLine!!.animation = scanAnimation
        }
    }

    private fun scanlineStop() {
        this.runOnUiThread {
            scanLine!!.visibility = View.GONE
            scanLine!!.clearAnimation()
        }
    }

    companion object {
        private val LOGTAG = "CloudRecognition"

        // These codes match the ones defined in TargetFinder in Vuforia.jar
        //internal val INIT_SUCCESS = 2
        //internal val INIT_ERROR_NO_NETWORK_CONNECTION = -1
        //internal val INIT_ERROR_SERVICE_NOT_AVAILABLE = -2
        internal val UPDATE_ERROR_AUTHORIZATION_FAILED = -1
        internal val UPDATE_ERROR_PROJECT_SUSPENDED = -2
        internal val UPDATE_ERROR_NO_NETWORK_CONNECTION = -3
        internal val UPDATE_ERROR_SERVICE_NOT_AVAILABLE = -4
        internal val UPDATE_ERROR_BAD_FRAME_QUALITY = -5
        internal val UPDATE_ERROR_UPDATE_SDK = -6
        internal val UPDATE_ERROR_TIMESTAMP_OUT_OF_RANGE = -7
        internal val UPDATE_ERROR_REQUEST_TIMEOUT = -8

        internal val HIDE_LOADING_DIALOG = 0
        //internal val SHOW_LOADING_DIALOG = 1

        private val kAccessKey = "b14c6765f43b5fbe3b693e5c48e590ce71984c32"
        private val kSecretKey = "cfc571b26db97842ebccf79ee722fd8dc3010807"

        //val CMD_BACK = -1
        //val CMD_EXTENDED_TRACKING = 1
    }

}
