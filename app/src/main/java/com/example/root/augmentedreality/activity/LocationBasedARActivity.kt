package com.example.root.augmentedreality.activity

import android.Manifest
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.*
import android.location.Location
import android.location.LocationManager
import android.opengl.Matrix
import android.os.Build
import android.os.Bundle
import android.support.annotation.RequiresApi
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.*
import android.widget.*
import com.example.root.augmentedreality.R
import com.example.root.augmentedreality.locationhelper.ARCamera
import com.example.root.augmentedreality.locationhelper.LocationHelper
import com.example.root.augmentedreality.locationhelper.LocationPhoto
import com.example.root.locationbasedardemo.model.AttributedPhoto
import com.example.root.locationbasedardemo.utility.PlaceIconURI
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.places.Place
import com.google.android.gms.location.places.Places
import com.squareup.picasso.Picasso
import kotlinx.android.synthetic.main.activity_location_based_ar.*
import kotlinx.android.synthetic.main.location_expanded_layout.*
import kotlinx.android.synthetic.main.location_expanded_layout.view.*
import java.text.DateFormat
import java.util.*

class LocationBasedARActivity : AppCompatActivity(), SensorEventListener,
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        com.google.android.gms.location.LocationListener
{

    val TAG: String = "LocationBasedARActivity"

    //var mAROverlayByView: AROverlayByView? = null

    var mAROverlayByViewGroup: RelativeLayout? = null

    @Suppress("DEPRECATION")
    var camera: Camera? = null

    var mARCamera: ARCamera? = null

    lateinit var sensorManager: SensorManager

    private val REQUEST_CAMERA_PERMISSIONS_CODE = 11

    val REQUEST_LOCATION_PERMISSIONS_CODE = 0

    private lateinit var mGoogleApiClient: GoogleApiClient

    private lateinit var mLocationRequest: LocationRequest

    private lateinit var mLocationManager: LocationManager

    private var mRequestingLocationUpdates: Boolean = false

    private var mLastUpdateTime: String = ""

    private val UPDATE_INTERVAL_IN_MILLISECONDS: Long = 10000

    private val FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS = UPDATE_INTERVAL_IN_MILLISECONDS / 2

    private var mArayPois: ArrayList<Place>? = ArrayList()

    private var mPointArray: MutableList<String>? = ArrayList()

    private var mCurrentLocation : Location? = null

    val cameraCoordinateVector: FloatArray = kotlin.FloatArray(4)

    var rotatedProjectionMatrix:FloatArray?=null

    var xCoords: IntArray = kotlin.IntArray(100)

    var yCoords: IntArray = kotlin.IntArray(100)

    companion object
    {
        var count: Int = 0
    }


    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_location_based_ar)
        requestLocationPermission()

        sensorManager = this.getSystemService(android.content.Context.SENSOR_SERVICE) as SensorManager

        mAROverlayByViewGroup =  RelativeLayout(this)//AROverlayByViewGroup(applicationContext)

        val mFrameLL: FrameLayout.LayoutParams= FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT)

        mAROverlayByViewGroup!!.layoutParams=mFrameLL

        initAROverlayByViewGroup()

        initLocation()
    }

    override fun onDestroy()
    {
        super.onDestroy()
    }

    override fun onResume()
    {
        super.onResume()
        requestCameraPermission()
        registerSensors()
        if(mGoogleApiClient.isConnected)
        {
            startLocationUpdates()
        }

    }

    override fun onStart()
    {
        super.onStart()
        mGoogleApiClient.connect()
    }

    override fun onPause()
    {
        super.onPause()
        // Stop location updates to save battery, but don't disconnect the GoogleApiClient object.
        if (mGoogleApiClient.isConnected)
        {
            stopLocationUpdates()
        }
    }

    override fun onStop()
    {
        mGoogleApiClient.disconnect()
        releaseCamera()
        super.onStop()
    }

    @Synchronized private fun getPointOfInterest()
    {
        Log.w("LOG_TAG", "getPointOfInterest--->Called")

        val result = Places.PlaceDetectionApi
                .getCurrentPlace(mGoogleApiClient, null)

        Log.e(TAG, result.toString())

        result.setResultCallback { likelyPlaces ->
            mArayPois = ArrayList<Place>()

            Log.e(TAG,"On result")

            Log.e(TAG, likelyPlaces.count.toString())

            for (placeLikelihood in likelyPlaces) {

                Log.i(TAG, String.format("Place '%s' has likelihood: %g",
                        placeLikelihood.place.name,
                        placeLikelihood.likelihood))
                Log.w("LOG_TAG", "Website url is" + placeLikelihood.place.websiteUri
                        + "Get Image" + placeLikelihood.place.websiteUri + "Get" +
                        placeLikelihood.place.placeTypes)

                if (placeLikelihood.place.name != null && placeLikelihood.place.address != null) {

                    val location: Location = Location(placeLikelihood.place.name.toString())
                    location.latitude = placeLikelihood.place.latLng.latitude
                    location.longitude = placeLikelihood.place.latLng.longitude
                    val place: Place = placeLikelihood.place.freeze()
                    mArayPois!!.add(place)
                    mPointArray!!.add(place.name.toString())

                }

            }

            if (mArayPois!!.size > 0) {

                // By using canvas from view
                /*mAROverlayByView = AROverlayByView(applicationContext)
                mAROverlayByView!!.updateARPoints(mArayPois!!)*/

                //By using layout from relative layout
                // mAROverlayByViewGroup!!.updateARPoints(mArayPois!!)
                updateRlView()

                updateLatestLocation()
                //initAROverlayByView()

                // initAROverlayByViewGroup()
            }

            likelyPlaces.release()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent)
    {

        if (resultCode == Activity.RESULT_OK)
        {
            // Make sure the app is not already connected or attempting to connect
            if (!mGoogleApiClient.isConnecting && !mGoogleApiClient.isConnected)
            {
                mGoogleApiClient.connect()
            }
        }

        super.onActivityResult(requestCode, resultCode, data)
    }

    /**
     * Requests location updates from the FusedLocationApi.
     */
    private fun startLocationUpdates()
    {
        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        Log.e(TAG,"start location updates")
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this)
    }

    /**
     * Removes location updates from the FusedLocationApi.
     */
    private fun stopLocationUpdates()
    {
        // It is a good practice to remove location requests when the activity is in a paused or
        // stopped state. Doing so helps battery performance and is especially
        // recommended in applications that request frequent location updates.

        // The final argument to {@code requestLocationUpdates()} is a LocationListener
        // (http://developer.android.com/reference/com/google/android/gms/location/LocationListener.html).
        Log.e(TAG,"stop location updates")
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this)
    }

    override fun onConnected(p0: Bundle?)
    {
        // Because we cache the value of the initial location in the Bundle, it means that if the
        // user launches the activity,
        // moves to a new location, and then changes the device orientation, the original location
        // is displayed as the activity is re-created.
        if (mCurrentLocation == null)
        {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient)
            mLastUpdateTime = DateFormat.getTimeInstance().format(Date())
            //updateUI();

            if (mCurrentLocation != null)
            {

                Log.w("LOG_TAG", "Last Location is+ Latitude" + mCurrentLocation!!.latitude +
                        "Longitude" + mCurrentLocation!!.longitude)

                getPointOfInterest()
            }
        }


        // If the user presses the Start Updates button before GoogleApiClient connects, we set
        // mRequestingLocationUpdates to true (see startUpdatesButtonHandler()). Here, we check
        // the value of mRequestingLocationUpdates and if it is true, we start location updates.

        startLocationUpdates()


    }

    override fun onConnectionSuspended(p0: Int)
    {
        Log.i(TAG, "Connection suspended")
        mGoogleApiClient.connect()
    }

    override fun onConnectionFailed(p0: ConnectionResult)
    {
        Log.i(TAG, "Connection failed")
    }

    fun requestCameraPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                this.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
        {
            this.requestPermissions(arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSIONS_CODE)
        } else
        {
            initARCameraView()
        }
    }

    fun requestLocationPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED)
        {
            this.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    REQUEST_LOCATION_PERMISSIONS_CODE)
        } else
        {
            //initLocationService()
        }
    }

    //using view for element
    /*fun initAROverlayByView()
    {
        if (mAROverlayByView!!.parent != null)
        {
            (mAROverlayByView!!.parent as ViewGroup).removeView(mAROverlayByView)
        }
        frame_layout_container.addView(mAROverlayByView,2)
    }*/

    //using view group for element
    fun initAROverlayByViewGroup()
    {
        activity_ar.addView(mAROverlayByViewGroup)
    }

    fun initARCameraView()
    {
        reloadSurfaceView()

        if (mARCamera == null)
        {
            mARCamera = ARCamera(this, surface_view)
        }
        if (mARCamera!!.parent != null)
        {
            (mARCamera!!.parent as ViewGroup).removeView(mARCamera)
        }
        frame_layout_container.addView(mARCamera)
        mARCamera!!.keepScreenOn = true
        initCamera()
    }

    @Suppress("DEPRECATION")
    private fun initCamera()
    {
        val numCams = Camera.getNumberOfCameras()
        if (numCams > 0)
        {
            try
            {
                camera = Camera.open()
                camera!!.startPreview()
                mARCamera!!.setCamera(camera!!)
            } catch (ex: RuntimeException)
            {
                Toast.makeText(this, "Camera not found", Toast.LENGTH_LONG).show()
            }

        }
    }

    private fun reloadSurfaceView()
    {
        if (surface_view.parent != null)
        {
            (surface_view.parent as ViewGroup).removeView(surface_view)
        }

        frame_layout_container.addView(surface_view)
    }

    private fun releaseCamera()
    {
        if (camera != null)
        {
            /*camera!!.setPreviewCallback(null)
            camera!!.stopPreview()
            mARCamera!!.setCamera(null)*/
            camera!!.release()
            camera = null
        }
    }

    private fun registerSensors()
    {
        sensorManager.registerListener(this,
                sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR),
                SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun onSensorChanged(sensorEvent: SensorEvent)
    {
        if (sensorEvent.sensor.type == Sensor.TYPE_ROTATION_VECTOR)
        {
            val rotationMatrixFromVector = FloatArray(16)
            var projectionMatrix = FloatArray(16)
            rotatedProjectionMatrix= FloatArray(16)

            SensorManager.getRotationMatrixFromVector(rotationMatrixFromVector, sensorEvent.values)

            if(mARCamera != null)
            {
                projectionMatrix = mARCamera!!.projectionMatrix
            }

            Matrix.multiplyMM(rotatedProjectionMatrix, 0, projectionMatrix, 0, rotationMatrixFromVector, 0)

            /*if(mAROverlayByView != null)
            {
                this.mAROverlayByView!!.updateRotatedProjectionMatrix(rotatedProjectionMatrix!!)
            }*/
            /* if(mAROverlayByViewGroup != null)
             {
                 this.mAROverlayByViewGroup!!.updateRotatedProjectionMatrix(rotatedProjectionMatrix!!)
             }*/

        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int)
    {
        //nothing to change.
    }

    private fun updateLatestLocation()
    {
        text_view_current_location.text = String.format("lat: %s \nlon: %s \naltitude: %s \n",
                mCurrentLocation!!.latitude, mCurrentLocation!!.longitude, mCurrentLocation!!.altitude)

        /*if(mAROverlayByView != null)
        {
            mAROverlayByView!!.updateCurrentLocation(mCurrentLocation!!)
        }*/

        updateRlView()

        /*if(mAROverlayByViewGroup != null)
        {
            mAROverlayByViewGroup!!.updateCurrentLocation(mCurrentLocation!!)
        }*/
    }


    private fun updateRlView() {

        mAROverlayByViewGroup!!.removeAllViews()

        count = 0

        for (point in mArayPois!!) {
            val currentLocationInECEF: FloatArray = LocationHelper.WSG84toECEF(mCurrentLocation!!)
            val location: Location = Location(point.name.toString())
            location.latitude = point.latLng.latitude
            location.longitude = point.latLng.longitude
            val pointInECEF: FloatArray = LocationHelper.WSG84toECEF(location)
            val pointInENU: FloatArray = LocationHelper.ECEFtoENU(mCurrentLocation!!,
                    currentLocationInECEF, pointInECEF)

            Matrix.multiplyMV(cameraCoordinateVector, 0, rotatedProjectionMatrix, 0, pointInENU, 0)

            Log.e("cameracoordinatevector", cameraCoordinateVector[0].toString() + " " +
                    cameraCoordinateVector[1].toString() + " " +
                    cameraCoordinateVector[2].toString() + " " +
                    cameraCoordinateVector[3].toString())


            // cameraCoordinateVector[2] is z, that always less than 0 to display on right position
            // if z > 0, the point will display on the opposite
            if (cameraCoordinateVector[2] < 0) {

                Log.i("Value","Width is"+ frame_layout_container.width)
                Log.e("value", (cameraCoordinateVector[0] / cameraCoordinateVector[3]).toString())
                val x: Float = (0.5f + cameraCoordinateVector[0] / cameraCoordinateVector[3]) *
                        frame_layout_container.width
                val y: Float = (0.5f - cameraCoordinateVector[1] / cameraCoordinateVector[3]) *
                        frame_layout_container.height

                val mLocationView = layoutInflater.inflate(R.layout.location_layout, null)
                mLocationView.tag = point.id
                val mImageView = mLocationView.findViewById(R.id.image_place_type_icon) as ImageView
                val mTextViewPlaceName = mLocationView.findViewById(R.id.text_place_name) as TextView
                val mTextViewPlaceNumber = mLocationView.findViewById(R.id.text_place_number) as TextView

                var url:String = "http://www.freeiconspng.com/uploads/blue-location-icon-png-19.png"
                var typeString: String = "null"
                val uriList: MutableMap<Int, String> = PlaceIconURI().getIconURIList()
                Log.e(TAG,"size : " + (uriList.size).toString())
                for(type in point.placeTypes)
                {
                    Log.e(TAG,"type : "+ type.toString()+" result : " + (uriList.containsKey(type)).toString())
                    if(uriList.containsKey(type))
                    {
                        Log.e(TAG,"type : " + type.toString())
                        url = uriList[type]!!
                        typeString = type.toString()
                        break
                    }
                }

                Picasso.with(applicationContext)
                        .load(url)
                        .into(mImageView)

                mTextViewPlaceName.text = (point.name.toString() + typeString)
                mTextViewPlaceNumber.text = point.phoneNumber

                Log.e(TAG, "x : " + x.toString() + " " + x.toInt().toString())
                Log.e(TAG, "y : " + y.toString() + " " + y.toInt().toString())

                val params = RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT)
                mLocationView.layoutParams = params

                /*
                * handled location over lapping
                */
                xCoords[count] = x.toInt()
                yCoords[count] = y.toInt()

                checkAvailabilityOfCoords()

                params.leftMargin = xCoords[count] - (mLocationView.width/2)
                params.topMargin = yCoords[count] - (mLocationView.height/2)

                Log.d(TAG,"Count value = "+ count.toString())

                count++

                Log.e("AROverlayByViewGroup", "arpoint " + point.name + "text added")
                mAROverlayByViewGroup!!.addView(mLocationView)

                mLocationView.setOnClickListener{

                    //Toast.makeText(applicationContext, mLocationView.tag as String,Toast.LENGTH_SHORT).show()

                    var place: Place? = null

                    for(selectedPlace in mArayPois!!)
                    {
                        if(selectedPlace.id == mLocationView.tag)
                        {
                            place = selectedPlace
                        }
                    }

                    Log.e("PlaceId", place!!.id)

                    //openDrawer(place)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        generateInfoDialog(place, x.toInt() - (mLocationView.width/2), y.toInt() - (mLocationView.height/2))
                    }

                }

            }
        }
    }

    private fun checkAvailabilityOfCoords()
    {
        for(x in xCoords)
        {
            if(Math.abs(x - xCoords[count]) < 100)
            {
                Log.d(TAG,"Find diff x" + (x - xCoords[count]).toString())
                xCoords[count] += (100 - (x - xCoords[count]))
                for(y in yCoords)
                {
                    if(Math.abs(y - yCoords[count]) < 100)
                    {
                        Log.d(TAG,"Find diff y" + (y - yCoords[count]).toString())
                        yCoords[count] += (100 - (y - yCoords[count]))
                    }
                }
            }
        }
    }

    override fun onLocationChanged(location: Location?)
    {
        updateLatestLocation()
        mCurrentLocation = location!!
    }

    private fun initLocation()
    {

        mGoogleApiClient = GoogleApiClient.Builder(this)
                .addApi(Places.GEO_DATA_API)
                .addApi(Places.PLACE_DETECTION_API).addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()

        createLocationRequest()

        mRequestingLocationUpdates = false
        mLastUpdateTime = ""

        mLocationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    }

    private fun createLocationRequest()
    {
        mLocationRequest = LocationRequest()
        mLocationRequest.interval = UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.fastestInterval = FASTEST_UPDATE_INTERVAL_IN_MILLISECONDS
        mLocationRequest.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    @RequiresApi(Build.VERSION_CODES.JELLY_BEAN)
    private fun generateInfoDialog(place: Place, x: Int, y: Int)
    {
        val dialog: Dialog = Dialog(this)

        dialog.setContentView(R.layout.location_expanded_layout)

        val layoutParams: WindowManager.LayoutParams = dialog.window.attributes

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = x
        layoutParams.y = y

        Log.e(TAG, "width "+ dialog.image_place_photo.maxWidth.toString())
        Log.e(TAG, "height "+ dialog.image_place_photo.maxHeight.toString())

        object : LocationPhoto(dialog.image_place_photo.maxWidth, dialog.image_place_photo.maxHeight, mGoogleApiClient) {
            override fun onPreExecute() {
                // Display a temporary image to show while bitmap is loading.
                dialog.image_place_photo.setImageResource(R.drawable.empty_photo)
            }

            override fun onPostExecute(attributedPhoto: AttributedPhoto?) {
                if (attributedPhoto != null) {
                    // Photo has been loaded, display it.
                    dialog.image_place_photo.setImageBitmap(attributedPhoto.bitmap)
                }
            }
        }.execute(place.id)

        dialog.text_place_name.text = place.name.toString()

        dialog.text_address.text = ("Address : " + "\n" +
                place.address.toString())

        dialog.text_rating.text = ("Rating : " + place.rating.toString())

        dialog.text_number.text = ("Contact no. : " + place.phoneNumber.toString())

        dialog.text_uri.text = (place.websiteUri?.toString() ?: "No website provided")

        dialog.window.attributes.windowAnimations = R.style.DialogAnimation

        dialog.text_attribution.text = place.attributions

        dialog.show()

    }

    private fun openDrawer(place: Place)
    {
        activity_ar.openDrawer(Gravity.END)

        val infoView: View = LayoutInflater.from(applicationContext).inflate(R.layout.location_expanded_layout, null)

        object : LocationPhoto(infoView.image_place_photo.maxWidth, infoView.image_place_photo.maxHeight, mGoogleApiClient) {
            override fun onPreExecute() {
                // Display a temporary image to show while bitmap is loading.
                infoView.image_place_photo.setImageResource(R.drawable.empty_photo)
            }

            override fun onPostExecute(attributedPhoto: AttributedPhoto?) {
                if (attributedPhoto != null) {
                    // Photo has been loaded, display it.
                    infoView.image_place_photo.setImageBitmap(attributedPhoto.bitmap)
                }
            }
        }.execute(place.id)

        infoView.text_place_name.text = place.name.toString()

        infoView.text_address.text = ("Address : " + "\n" +
                place.address.toString())

        infoView.text_rating.text = ("Rating : " + place.rating.toString())

        infoView.text_number.text = ("Contact no. : " + place.phoneNumber.toString())

        infoView.text_uri.text = (place.websiteUri?.toString() ?: "No website provided")

        infoView.text_attribution.text = place.attributions

        drawer_layout.addView(infoView)
    }

}