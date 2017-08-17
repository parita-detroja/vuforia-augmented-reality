package com.example.root.augmentedreality.activity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.support.v7.app.AppCompatActivity
import android.widget.Toast
import com.example.root.augmentedreality.R
import com.example.root.augmentedreality.utility.Constant
import kotlinx.android.synthetic.main.activity_home.*

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        relative_layout_location_based_ar.setOnClickListener {
            requestLocationPermission()
        }

        relative_layout_text_recognition.setOnClickListener {
            val intent: Intent = Intent(this, TextRecognitionActivity::class.java)
            startActivity(intent)
        }

        relative_layout_image_target.setOnClickListener {
            val intent: Intent = Intent(this, ImageTargetActivity::class.java)
            startActivity(intent)
        }

        relative_layout_moving_target.setOnClickListener {
            val intent: Intent = Intent(this, MovingTargetActivity::class.java)
            startActivity(intent)
        }

        relative_layout_user_defined_target.setOnClickListener {
            Constant.activityFlag = Constant.USERDEFINEDTARGETACTIVITY
            requestCameraPermission()
        }

        relative_layout_cloud_recognition.setOnClickListener {
            /*Constant.activityFlag = Constant.IMAGEACTIVITY
            requestCameraPermission()*/
            val intent: Intent = Intent(this, CloudRecognitionActivity::class.java)
            startActivity(intent)
        }
    }

    fun requestCameraPermission()
    {
        if (ContextCompat.checkSelfPermission(this@HomeActivity, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@HomeActivity,
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE), 1)
        }
        else
        {
            var intent: Intent? = null
            if (Constant.activityFlag == Constant.IMAGEACTIVITY)
            {
                intent = Intent(this, ImageActivity::class.java)
            }
            else if(Constant.activityFlag == Constant.USERDEFINEDTARGETACTIVITY) {
                intent = Intent(this, UserDefinedTargetsActivity::class.java)
            }
            startActivity(intent!!)
        }
    }

    fun requestLocationPermission()
    {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
                this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED)
        {
            this.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 0)
        } else
        {
            val intent: Intent = Intent(this, LocationBasedARActivity::class.java)
            startActivity(intent)
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(this@HomeActivity, UserDefinedTargetsActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(applicationContext, getString(R.string.txt_error_permission_needed),
                        Toast.LENGTH_LONG).show()
            }
        }
        if (requestCode == 0) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(this@HomeActivity, LocationBasedARActivity::class.java)
                startActivity(intent)
            } else {
                Toast.makeText(applicationContext, getString(R.string.txt_error_permission_needed),
                        Toast.LENGTH_LONG).show()
            }
        }
    }
}
