package com.example.root.locationbasedardemo.utility

import com.google.android.gms.location.places.Place
import java.util.*

/**
 * Created by root on 12/7/17.
 * provide map constant for icon type pair for location.
 */
class PlaceIconURI
{
    var ICON_URI: MutableMap<Int, String> = TreeMap<Int, String>()

    init
    {
        ICON_URI.put(Place.TYPE_AIRPORT, "https://maps.gstatic.com/mapfiles/place_api/icons/airport-71.png")
        ICON_URI.put(Place.TYPE_AMUSEMENT_PARK, "https://maps.gstatic.com/mapfiles/place_api/icons/amusement-71.png")
        ICON_URI.put(Place.TYPE_AQUARIUM, "https://maps.gstatic.com/mapfiles/place_api/icons/aquarium-71.png")
        ICON_URI.put(Place.TYPE_ART_GALLERY, "https://maps.gstatic.com/mapfiles/place_api/icons/art_gallery-71.png")
        ICON_URI.put(Place.TYPE_ATM, "https://maps.gstatic.com/mapfiles/place_api/icons/atm-71.png")
        ICON_URI.put(Place.TYPE_BANK, "https://maps.gstatic.com/mapfiles/place_api/icons/bank_dollar-71.png")
        ICON_URI.put(Place.TYPE_BAR, "https://maps.gstatic.com/mapfiles/place_api/icons/bar-71.png")
        ICON_URI.put(Place.TYPE_BICYCLE_STORE, "https://maps.gstatic.com/mapfiles/place_api/icons/bicycle-71.png")
        ICON_URI.put(Place.TYPE_BUS_STATION, "https://maps.gstatic.com/mapfiles/place_api/icons/bus-71.png")
        ICON_URI.put(Place.TYPE_BOWLING_ALLEY, "https://maps.gstatic.com/mapfiles/place_api/icons/baseball-71.png")
        ICON_URI.put(Place.TYPE_CAFE, "https://maps.gstatic.com/mapfiles/place_api/icons/cafe-71.png")
        ICON_URI.put(Place.TYPE_CAMPGROUND, "https://maps.gstatic.com/mapfiles/place_api/icons/camping-71.png")
        ICON_URI.put(Place.TYPE_CAR_DEALER, "https://maps.gstatic.com/mapfiles/place_api/icons/car_dealer-71.png")
        ICON_URI.put(Place.TYPE_CAR_RENTAL, "https://maps.gstatic.com/mapfiles/place_api/icons/car_rental-71.png")
        ICON_URI.put(Place.TYPE_CAR_REPAIR, "https://maps.gstatic.com/mapfiles/place_api/icons/car_repair-71.png")
        ICON_URI.put(Place.TYPE_CASINO, "https://maps.gstatic.com/mapfiles/place_api/icons/casino-71.png")
        ICON_URI.put(Place.TYPE_CONVENIENCE_STORE, "https://maps.gstatic.com/mapfiles/place_api/icons/convenience-71.png")
        ICON_URI.put(Place.TYPE_COURTHOUSE, "https://maps.gstatic.com/mapfiles/place_api/icons/courthouse-71.png")
        ICON_URI.put(Place.TYPE_DENTIST, "https://maps.gstatic.com/mapfiles/place_api/icons/dentist-71.png")
        ICON_URI.put(Place.TYPE_DOCTOR, "https://maps.gstatic.com/mapfiles/place_api/icons/doctor-71.png")
        ICON_URI.put(Place.TYPE_ELECTRONICS_STORE, "https://maps.gstatic.com/mapfiles/place_api/icons/electronics-71.png")
        ICON_URI.put(Place.TYPE_FLORIST, "https://maps.gstatic.com/mapfiles/place_api/icons/flower-71.png")
        ICON_URI.put(Place.TYPE_GAS_STATION, "https://maps.gstatic.com/mapfiles/place_api/icons/gas_station-71.png")
        ICON_URI.put(Place.TYPE_GEOCODE,"https://maps.gstatic.com/mapfiles/place_api/icons/geocode-71.png")
        ICON_URI.put(Place.TYPE_JEWELRY_STORE,"https://maps.gstatic.com/mapfiles/place_api/icons/jewelry-71.png")
        ICON_URI.put(Place.TYPE_LIBRARY,"https://maps.gstatic.com/mapfiles/place_api/icons/library-71.png")
        ICON_URI.put(Place.TYPE_MOVIE_THEATER,"https://maps.gstatic.com/mapfiles/place_api/icons/movies-71.png")
        ICON_URI.put(Place.TYPE_MUSEUM,"https://maps.gstatic.com/mapfiles/place_api/icons/museum-71.png")
        ICON_URI.put(Place.TYPE_POLICE,"https://maps.gstatic.com/mapfiles/place_api/icons/police-71.png")
        ICON_URI.put(Place.TYPE_POST_OFFICE,"https://maps.gstatic.com/mapfiles/place_api/icons/post_office-71.png")
        ICON_URI.put(Place.TYPE_RESTAURANT,"https://maps.gstatic.com/mapfiles/place_api/icons/restaurant-71.png")
        ICON_URI.put(Place.TYPE_SCHOOL,"https://maps.gstatic.com/mapfiles/place_api/icons/school-71.png")
        ICON_URI.put(Place.TYPE_SHOPPING_MALL,"https://maps.gstatic.com/mapfiles/place_api/icons/shopping-71.png")
        ICON_URI.put(Place.TYPE_STADIUM,"https://maps.gstatic.com/mapfiles/place_api/icons/stadium-71.png")
        ICON_URI.put(Place.TYPE_GROCERY_OR_SUPERMARKET,"https://maps.gstatic.com/mapfiles/place_api/icons/supermarket-71.png")
        ICON_URI.put(Place.TYPE_TAXI_STAND,"https://maps.gstatic.com/mapfiles/place_api/icons/taxi-71.png")
        ICON_URI.put(Place.TYPE_TRAIN_STATION,"https://maps.gstatic.com/mapfiles/place_api/icons/train-71.png")
        ICON_URI.put(Place.TYPE_TRAVEL_AGENCY,"https://maps.gstatic.com/mapfiles/place_api/icons/travel_agent-71.png")
        ICON_URI.put(Place.TYPE_UNIVERSITY,"https://maps.gstatic.com/mapfiles/place_api/icons/university-71.png")
        ICON_URI.put(Place.TYPE_HINDU_TEMPLE,"https://maps.gstatic.com/mapfiles/place_api/icons/worship_hindu-71.png")
        ICON_URI.put(Place.TYPE_CHURCH,"https://maps.gstatic.com/mapfiles/place_api/icons/worship_christian-71.png")
        ICON_URI.put(Place.TYPE_ZOO,"https://maps.gstatic.com/mapfiles/place_api/icons/zoo-71.png")
        ICON_URI.put(Place.TYPE_CLOTHING_STORE,"https://maps.gstatic.com/mapfiles/place_api/icons/shopping-71.png")
        ICON_URI.put(Place.TYPE_ESTABLISHMENT,"https://maps.gstatic.com/mapfiles/place_api/icons/generic_recreational-71.png")
        ICON_URI.put(Place.TYPE_STORE,"https://maps.gstatic.com/mapfiles/place_api/icons/shopping-71.png")
        ICON_URI.put(Place.TYPE_POINT_OF_INTEREST,"https://maps.gstatic.com/mapfiles/place_api/icons/generic_business-71.png")
    }

    fun getIconURIList() : MutableMap<Int, String>
    {
        return ICON_URI
    }
}