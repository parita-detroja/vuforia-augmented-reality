package com.example.root.augmentedreality.locationhelper

import android.graphics.Bitmap
import android.os.AsyncTask
import com.example.root.locationbasedardemo.model.AttributedPhoto
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.places.PlacePhotoMetadata
import com.google.android.gms.location.places.PlacePhotoMetadataBuffer
import com.google.android.gms.location.places.PlacePhotoMetadataResult
import com.google.android.gms.location.places.Places

/**
 * Created by root on 14/7/17.
 * Class provide photo according to place Id.
 */
open class LocationPhoto(height: Int, width: Int, googleApiClient: GoogleApiClient) : AsyncTask<String, Void, AttributedPhoto>()
{
    var googleApiClient: GoogleApiClient = googleApiClient

    var width: Int = width

    var height: Int = height

    var attributedPhoto: AttributedPhoto? = null

    override fun doInBackground(vararg params: String): AttributedPhoto?
    {
        val placeId: String = params[0]

        val result: PlacePhotoMetadataResult = Places.GeoDataApi.getPlacePhotos(googleApiClient, placeId).await()

        if(result.status.isSuccess)
        {
            val placePhotoMetadataBuffer: PlacePhotoMetadataBuffer = result.photoMetadata

            if(placePhotoMetadataBuffer.count > 0 && !isCancelled)
            {
                val photo: PlacePhotoMetadata = placePhotoMetadataBuffer.get(0)

                val attribution: CharSequence = photo.attributions

                val imageBitmap: Bitmap = photo.getScaledPhoto(googleApiClient, width, height).await().bitmap

                attributedPhoto = AttributedPhoto(attribution, imageBitmap)
            }

            placePhotoMetadataBuffer.release()
        }

        return attributedPhoto
    }



}