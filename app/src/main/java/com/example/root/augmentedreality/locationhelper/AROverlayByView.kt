package com.example.root.augmentedreality.locationhelper

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.opengl.Matrix
import android.util.Log
import android.view.View
import com.google.android.gms.location.places.Place

/**
 * Created by root on 5/7/17.
 * extends view for location.
 * not used
 */
class AROverlayByView(context: Context) : View(context)
{

    var rotatedProjectionMatrix: FloatArray = kotlin.FloatArray(16)
    private var currentLocation: Location? = null
    var arPoints: MutableList<Place> = ArrayList<Place>()

    fun updateARPoints(arPoints: MutableList<Place>)
    {
        this.arPoints = arPoints
    }

    fun updateRotatedProjectionMatrix(rotatedProjectionMatrix: FloatArray)
    {
        this.rotatedProjectionMatrix = rotatedProjectionMatrix
    }

    fun updateCurrentLocation(location: Location)
    {
        this.currentLocation = location
        this.invalidate()
    }

    val cameraCoordinateVector: FloatArray = kotlin.FloatArray(4)
    val paint: Paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas)
    {

        Log.e("AROverlayByView","onDraw")
        super.onDraw(canvas)

        if(currentLocation == null)
        {
            Log.e("AROverlayByView","null location")
            return
        }

        val radius: Float = 15F
        paint.style = Paint.Style.FILL
        paint.color = Color.GREEN
        paint.typeface = Typeface.create(Typeface.DEFAULT,Typeface.NORMAL)
        paint.textSize = 50F

        Log.e("AROverlayByView","Near by location size : "+arPoints.size.toString())
        Log.e("AROverlayByView","height" + canvas.height + "width" + canvas.width)

        for(point in arPoints)
        {
            val currentLocationInECEF: FloatArray = LocationHelper.WSG84toECEF(currentLocation!!)
            val location: Location = Location(point.name.toString())
            location.latitude = point.latLng.latitude
            location.longitude = point.latLng.longitude
            val pointInECEF: FloatArray = LocationHelper.WSG84toECEF(location)
            val pointInENU: FloatArray = LocationHelper.ECEFtoENU(currentLocation!!, currentLocationInECEF, pointInECEF)

            Matrix.multiplyMV(cameraCoordinateVector, 0, rotatedProjectionMatrix, 0, pointInENU, 0)

            Log.e("cameracoordinatevector",cameraCoordinateVector[0].toString()+" "+
                    cameraCoordinateVector[1].toString()+" "+
                    cameraCoordinateVector[2].toString()+" "+
                    cameraCoordinateVector[3].toString())

            // cameraCoordinateVector[2] is z, that always less than 0 to display on right position
            // if z > 0, the point will display on the opposite
            if(cameraCoordinateVector[2] < 0)
            {
                Log.e("AROverlayByView","arpoint " + point.name.toString() + "text added")
                Log.e("value",(cameraCoordinateVector[0] / cameraCoordinateVector[3]).toString())
                val x: Float = (0.5f + cameraCoordinateVector[0] / cameraCoordinateVector[3]) * canvas.width
                val y:Float = (0.5f - cameraCoordinateVector[1]/cameraCoordinateVector[3]) * canvas.height

                canvas.drawCircle(x, y, radius, paint)
                Log.e("AROverlayByView","x" + x.toString() + "y" + y.toString())
                Log.e("AROverlayByView","x" + (x - (30 * point.name.toString().length / 2)).toString() + "y" + (y-80).toString())
                canvas.drawText(point.name.toString(), x - (30 * point.name.toString().length / 2), y - 40, paint)

            }
        }
    }
}