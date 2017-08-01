package com.example.root.augmentedreality.vuforia

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.AsyncTask
import android.os.Build
import android.util.Log
import android.view.OrientationEventListener
import android.view.WindowManager
import com.example.root.augmentedreality.R
import com.vuforia.*

/**
 * Created by root on 25/5/17.
 * Handle Vuforia application session.
 */
class ApplicationSession(private val mApplicationControl: ApplicationControl) : Vuforia.UpdateCallbackInterface {

    private var mActivity: Activity? = null

    // Returns true if Vuforia is initialized, the trackers started and the
    // tracker data loaded
    private var isARRunning: Boolean = false
    private var mCameraRunning: Boolean = false

    private var mInitVuforiaTask: InitVuforiaTask? = null
    private var mLoadTrackerTask: LoadTrackerTask? = null

    private val mShutDownTask = Any()

    private var mVuforiaFlags: Int = 0

    private var mCameraConfig = CameraDevice.CAMERA_DIRECTION.CAMERA_DIRECTION_DEFAULT

    fun initAR(mActivity: Activity, screenOrientation: Int) {
        var screenOrientationLocal = screenOrientation
        var mApplicationException: ApplicationException? = null
        this.mActivity = mActivity

        if (screenOrientationLocal == ActivityInfo.SCREEN_ORIENTATION_SENSOR && Build.VERSION.SDK_INT > Build.VERSION_CODES.FROYO) {
            screenOrientationLocal = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        }

        /**
         * Use an OrientationChangeListener here to capture all orientation changes.  Android
         * will not send an Activity.onConfigurationChanged() callback on a 180 degree rotation,
         * ie: Left Landscape to Right Landscape.  Vuforia needs to react to this change and the
         * SampleApplicationSession needs to update the Projection Matrix.
         */
        val orientationEventListener = object : OrientationEventListener(mActivity) {
            override fun onOrientationChanged(i: Int) {
                val activityRotation = mActivity.windowManager.defaultDisplay.rotation
                if (mLastRotation != activityRotation) {
                    mLastRotation = activityRotation
                }
            }

            internal var mLastRotation = -1
        }

        if (orientationEventListener.canDetectOrientation()) {
            orientationEventListener.enable()
        }

        mActivity.requestedOrientation = screenOrientationLocal


        mActivity.window.setFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        mVuforiaFlags = INIT_FLAGS.GL_20

        if (mInitVuforiaTask != null) {
            val logMessage = "Cannot initialize SDK twice"
            mApplicationException = ApplicationException(ApplicationException.VUFORIA_ALREADY_INITIALIZED, logMessage)
            Log.e(TAG, logMessage)
        }

        if (mApplicationException == null) {
            try {
                mInitVuforiaTask = InitVuforiaTask()
                mInitVuforiaTask!!.execute()
            } catch (e: Exception) {
                val logMessage = "Initializing Vuforia SDK failed"
                mApplicationException = ApplicationException(
                        ApplicationException.INITIALIZATION_FAILURE,
                        logMessage)
                Log.e(TAG, logMessage)
            }

        }

        if (mApplicationException != null) {
            mApplicationControl.onInitARDone(mApplicationException)
        }

    }

    @Throws(ApplicationException::class)
    fun startAR(cameraConfig: Int) {
        val error: String
        if (mCameraRunning) {
            error = "Camera already running, unable to open it again"
            Log.e(TAG, error)
            throw ApplicationException(ApplicationException.CAMERA_INITIALIZATION_FAILURE, error)
        }

        mCameraConfig = cameraConfig

        if (!CameraDevice.getInstance().init(cameraConfig)) {
            error = "Unable to open camera device : " + cameraConfig
            Log.e(TAG, error)
            throw ApplicationException(ApplicationException.CAMERA_INITIALIZATION_FAILURE, error)
        }

        if (!CameraDevice.getInstance().selectVideoMode(CameraDevice.MODE.MODE_DEFAULT)) {
            error = "Unable to set video mode"
            Log.e(TAG, error)
            throw ApplicationException(ApplicationException.CAMERA_INITIALIZATION_FAILURE, error)
        }

        if (!CameraDevice.getInstance().start()) {
            error = "Unable to start camera device: " + cameraConfig
            Log.e(TAG, error)
            throw ApplicationException(ApplicationException.CAMERA_INITIALIZATION_FAILURE, error)
        }

        mApplicationControl.doStartTracker()

        mCameraRunning = true

        if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_CONTINUOUSAUTO)) {
            if (!CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_TRIGGERAUTO)) {
                CameraDevice.getInstance().setFocusMode(CameraDevice.FOCUS_MODE.FOCUS_MODE_NORMAL)
            }
        }
    }

    @Throws(ApplicationException::class)
    fun stopAR() {
        if (mInitVuforiaTask != null && mInitVuforiaTask!!.status != AsyncTask.Status.FINISHED) {
            mInitVuforiaTask!!.cancel(true)
            mInitVuforiaTask = null
        }

        if (mLoadTrackerTask != null && mLoadTrackerTask!!.status != AsyncTask.Status.FINISHED) {
            mLoadTrackerTask!!.cancel(true)
            mLoadTrackerTask = null
        }

        mInitVuforiaTask = null
        mLoadTrackerTask = null

        isARRunning = false

        stopCamera()

        synchronized(mShutDownTask) {
            val unloadTrackersResult: Boolean = mApplicationControl.doUnloadTrackersData()
            val deInitTrackersResult: Boolean = mApplicationControl.doDeInitTrackerData()

            Vuforia.deinit()

            if (!unloadTrackersResult) {
                throw ApplicationException(ApplicationException.UNLOADING_TRACKERS_FAILURE,
                        "Failed to unload trackers data")
            }

            if (!deInitTrackersResult) {
                throw ApplicationException(ApplicationException.TRACKERS_DEINITIALIZATION_FAILURE,
                        "Failed to deinitialize tracker")
            }

        }
    }

    @Throws(ApplicationException::class)
    fun resumeAR() {
        Vuforia.onResume()

        if (isARRunning) {
            startAR(mCameraConfig)
        }
    }

    @Throws(ApplicationException::class)
    fun pauseAR() {
        if (isARRunning) {
            stopCamera()
        }

        Vuforia.onPause()
    }

    fun onResume() {
        Vuforia.onResume()
    }


    fun onPause() {
        Vuforia.onPause()
    }


    fun onSurfaceChanged(width: Int, height: Int) {
        Vuforia.onSurfaceChanged(width, height)
    }


    fun onSurfaceCreated() {
        Vuforia.onSurfaceCreated()
    }

    override fun Vuforia_onUpdate(state: State) {
        mApplicationControl.onVuforiaUpdate(state)
    }

    fun onConfigurationChanged() {
        Device.getInstance().setConfigurationChanged()
    }

    private inner class InitVuforiaTask : AsyncTask<Void, Int, Boolean>() {

        private var mProgressValue = -1

        override fun doInBackground(vararg params: Void): Boolean? {

            synchronized(mShutDownTask) {
                Vuforia.setInitParameters(mActivity, mVuforiaFlags, "AfVhAyX/////AAAAGeyXaHJ1dUL+gXEcBjc4CEgEIo65tH0qqxY7Y1noTJ3/QdfTMhlfBYvidv0jGsp+Q9fLZYpBCukxpsmG4hwMZRqgKcdkKUHdFpWcp9FsUWqp2a5CdPMSgd66muTLPHXCEv2E+KmMonT/Ky7CaOPCLh2/iLWiLLobkfAiaewOOQA7wnYph0syCqX/R97vioLc7fA27UpNCX+opmfLoCLWz0E4M52NZZnXZo7bxO9JYiqfYFc/9ZTaP+QNEAgnR6mDZk/VsjCXJrOGH+XElYUcFgzS9JYgGiJeoWVBEqcPVhgairFTfX+jcYkZAwsgSpC4zoJ73NlTeKQ4w8E/w4/ZeOKMvhVnSKj+NtXEPsAH1fH/")

                do {

                    mProgressValue = Vuforia.init()

                    publishProgress(mProgressValue)


                } while (!isCancelled && mProgressValue >= 0 && mProgressValue < 100)
            }


            return mProgressValue > 0
        }



        override fun onPostExecute(result: Boolean?) {
            // Done initializing Vuforia, proceed to next application
            // initialization status:

            val applicationException: ApplicationException?

            if (result!!) {
                Log.d(TAG, "InitVuforiaTask.onPostExecute: Vuforia " + "initialization successful")

                val initTrackersResult: Boolean = mApplicationControl.doInitTrackers()

                if (initTrackersResult) {
                    try {
                        mLoadTrackerTask = LoadTrackerTask()
                        mLoadTrackerTask!!.execute()
                    } catch (e: Exception) {
                        val logMessage = "Loading tracking data set failed"
                        applicationException = ApplicationException(
                                ApplicationException.LOADING_TRACKERS_FAILURE,
                                logMessage)
                        Log.e(TAG, logMessage)
                        mApplicationControl.onInitARDone(applicationException)
                    }

                } else {
                    applicationException = ApplicationException(
                            ApplicationException.TRACKERS_INITIALIZATION_FAILURE,
                            "Failed to initialize trackers")
                    mApplicationControl.onInitARDone(applicationException)
                }
            } else {
                val logMessage: String = getInitializationErrorString(mProgressValue)

                // NOTE: Check if initialization failed because the device is
                // not supported. At this point the user should be informed
                // with a message.

                // Log error:
                Log.e(TAG, "InitVuforiaTask.onPostExecute: " + logMessage
                        + " Exiting.")

                // Send Vuforia Exception to the application and call initDone
                // to stop initialization process
                applicationException = ApplicationException(
                        ApplicationException.INITIALIZATION_FAILURE,
                        logMessage)
                mApplicationControl.onInitARDone(applicationException)
            }
        }
    }

    private inner class LoadTrackerTask : AsyncTask<Void, Int, Boolean>() {

        override fun doInBackground(vararg params: Void): Boolean? {
            synchronized(mShutDownTask) {
                return mApplicationControl.doLoadTrackersData()
            }

        }

        override fun onPostExecute(result: Boolean?) {
            var mApplicationException: ApplicationException? = null

            Log.d(TAG, "LoadTrackerTask.onPostExecute: execution " + if (result!!) "successful" else "failed")

            if ((!result)) {
                val logMessage = "Failed to load tracker data."

                Log.e(TAG, logMessage)

                mApplicationException = ApplicationException(ApplicationException.LOADING_TRACKERS_FAILURE, logMessage)
            } else {
                // Hint to the virtual machine that it would be a good time to
                // run the garbage collector:
                //
                // NOTE: This is only a hint. There is no guarantee that the
                // garbage collector will actually be run.
                System.gc()
                Log.e(TAG, "register call back")
                Vuforia.registerCallback(this@ApplicationSession)
                Log.e(TAG, "is ARRunning")

                isARRunning = true
            }

            // Done loading the tracker, update application status, send the
            // exception to check errors
            Log.e(TAG, "on Init ar done")

            mApplicationControl.onInitARDone(mApplicationException)
            Log.e(TAG, "on Init ar done after")

        }
    }

    private fun getInitializationErrorString(code: Int): String {
        if (code == INIT_ERRORCODE.INIT_DEVICE_NOT_SUPPORTED)
            return mActivity!!.getString(R.string.INIT_ERROR_DEVICE_NOT_SUPPORTED)
        if (code == INIT_ERRORCODE.INIT_NO_CAMERA_ACCESS)
            return mActivity!!.getString(R.string.INIT_ERROR_NO_CAMERA_ACCESS)
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_MISSING_KEY)
            return mActivity!!.getString(R.string.INIT_LICENSE_ERROR_MISSING_KEY)
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_INVALID_KEY)
            return mActivity!!.getString(R.string.INIT_LICENSE_ERROR_INVALID_KEY)
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT)
            return mActivity!!.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_TRANSIENT)
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT)
            return mActivity!!.getString(R.string.INIT_LICENSE_ERROR_NO_NETWORK_PERMANENT)
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_CANCELED_KEY)
            return mActivity!!.getString(R.string.INIT_LICENSE_ERROR_CANCELED_KEY)
        if (code == INIT_ERRORCODE.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH)
            return mActivity!!.getString(R.string.INIT_LICENSE_ERROR_PRODUCT_TYPE_MISMATCH)
        else {
            return mActivity!!.getString(R.string.INIT_LICENSE_ERROR_UNKNOWN_ERROR)
        }
    }


    fun stopCamera() {
        if (mCameraRunning) {
            mApplicationControl.doStopTracker()
            mCameraRunning = false
            CameraDevice.getInstance().stop()
            CameraDevice.getInstance().deinit()
        }
    }

    companion object {

        private val TAG = "ApplicationSession"
    }
}
