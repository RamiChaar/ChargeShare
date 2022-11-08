package com.example.evchargingapp.ui.map

import androidx.fragment.app.Fragment
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.evchargingapp.R
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.evchargingapp.FilterActivity
import com.example.evchargingapp.NearestStations
import com.google.android.gms.maps.model.*
import com.google.gson.GsonBuilder
import okhttp3.*
import java.io.IOException

class MapsFragment : Fragment(), GoogleMap.OnMarkerClickListener {

    private lateinit var googleMap: GoogleMap

    private var mapReady = false
    private val defaultLat = 34.2407
    private val defaultLng = -118.5300
    private val defaultLocation = LatLng(defaultLat, defaultLng)
    private val zoomLevel = 12.0f

    private var searchRadius = 4.0

    private val allLevels = mutableListOf("ev_level1_evse_num", "ev_level2_evse_num", "ev_dc_fast_num")
    private val allConnectors = mutableListOf("J1772", "J1772COMBO", "TESLA", "CHADEMO", "NEMA1450", "NEMA515", "NEMA520")
    private var levels = mutableListOf("ev_level1_evse_num", "ev_level2_evse_num", "ev_dc_fast_num")
    private var connectors = mutableListOf("J1772", "J1772COMBO", "TESLA", "CHADEMO", "NEMA1450", "NEMA515", "NEMA520")

    private val callback = OnMapReadyCallback { googleMap ->
        this.googleMap = googleMap
        mapReady = true
        Log.i("MapsFragment", "onMapReadyCallback")
        // Add a marker at CSUN and move the camera there
        googleMap.addMarker(MarkerOptions().position(defaultLocation).title("Your Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, zoomLevel))
        //load stations
        loadNearestStations(defaultLat, defaultLng, searchRadius, levels, connectors)
        googleMap.setOnMarkerClickListener(this)
    }

    override fun onResume() {
        super.onResume()
        if(mapReady){
            Log.i("MapsFragment", "onResume")
            googleMap.clear()
            googleMap.addMarker(MarkerOptions().position(defaultLocation).title("Your Location").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)))
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLocation, zoomLevel))
            loadNearestStations(defaultLat, defaultLng, searchRadius, levels, connectors)
        }
    }

    private fun loadNearestStations(latitude: Double, longitude: Double, radius: Double, levels: MutableList<String>, connectors: MutableList<String>) {
        val url = "https://developer.nrel.gov/api/alt-fuel-stations/v1/nearest.json?api_key=atG74JTz1BziqwmY0hecm8a9J14qTnbUb5SOvjPs&fuel_type=ELEC&latitude=$latitude&longitude=$longitude&radius=$radius&limit=all"
        val request = Request.Builder().url(url).build()
        val client = OkHttpClient()

        //make API call to client
        client.newCall(request).enqueue(object: Callback {
            override fun onResponse(call: Call, response: Response) {

                //retrieve json body and use gson to create nearest station object
                val body = response.body?.string()
                val gson = GsonBuilder().create()
                val nearestStations = gson.fromJson(body, NearestStations::class.java)

                //add the filters to the object
                nearestStations.levelFilter = levels
                nearestStations.connectorFilter = connectors

                //load markers on the main thread
                activity?.runOnUiThread {
                    loadMarkers(nearestStations)
                }

            }
            override fun onFailure(call: Call, e: IOException) {
                println("Failed to execute request")
            }
        })
    }

    private fun loadMarkers(nearestStations: NearestStations) {
        val defaultMarker = context?.let { bitmapDescriptorFromVector(it, R.drawable.ic_resource_default_marker) }
        val inactiveMarker = context?.let { bitmapDescriptorFromVector(it, R.drawable.ic_resource_inactive_marker) }
        val privateMarker = context?.let { bitmapDescriptorFromVector(it, R.drawable.ic_resource_private_marker) }
        val levelOneMarker = context?.let { bitmapDescriptorFromVector(it, R.drawable.ic_resource_level1_marker) }
        val levelTwoMarker = context?.let { bitmapDescriptorFromVector(it, R.drawable.ic_resource_level2_marker) }
        val levelThreeMarker = context?.let { bitmapDescriptorFromVector(it, R.drawable.ic_resource_level3_marker) }

        var marker : Marker?
        for(station in nearestStations.fuel_stations) {
            var hasOneLevel = false
            var hasOneConnector = false
            val levelsInStation = mutableListOf("")
            if(station.ev_level1_evse_num > 0){
                levelsInStation.add("ev_level1_evse_num")
            }
            if(station.ev_level2_evse_num > 0){
                levelsInStation.add("ev_level2_evse_num")
            }
            if(station.ev_dc_fast_num > 0){
                levelsInStation.add("ev_dc_fast_num")
            }

            for(level in levelsInStation){
                if(nearestStations.levelFilter.contains(level)) {
                    hasOneLevel = true
                }
            }
            for(connector in station.ev_connector_types){
                if(nearestStations.connectorFilter.contains(connector)) {
                    hasOneConnector = true
                }
            }

            if(!hasOneLevel || !hasOneConnector){
                continue
            }

            marker = googleMap.addMarker(MarkerOptions().position(LatLng(station.latitude, station.longitude)).title(station.station_name))
            marker?.tag = station.id
            marker?.setIcon(defaultMarker)
            if(station.status_code != "E") {
                marker?.setIcon(inactiveMarker)
            } else if(station.access_code == "private") {
                marker?.setIcon(privateMarker)
            } else if(station.ev_dc_fast_num > 0) {
                marker?.setIcon(levelThreeMarker)
            } else if(station.ev_level2_evse_num > 0) {
                marker?.setIcon(levelTwoMarker)
            } else if(station.ev_level1_evse_num > 0) {
                marker?.setIcon(levelOneMarker)
            } else {
                marker?.setIcon(defaultMarker)
            }
        }
    }

    override fun onMarkerClick(marker : Marker): Boolean {
        val id = marker.tag as? Int
        println("Marker $id has been clicked on.")
        marker.showInfoWindow()
        openFilterActivity()
        return true
    }

    private fun openFilterActivity() {
        var binaryLevels = ""
        var binaryConnectors = ""
        if(levels.contains("ev_level1_evse_num")) {
            binaryLevels += '1'
        } else {
            binaryLevels += '0'
        }
        if(levels.contains("ev_level2_evse_num")) {
            binaryLevels += '1'
        } else {
            binaryLevels += '0'
        }
        if(levels.contains("ev_dc_fast_num")) {
            binaryLevels += '1'
        } else {
            binaryLevels += '0'
        }

        if(connectors.contains("J1772")) {
            binaryConnectors += '1'
        } else {
            binaryConnectors += '0'
        }
        if(connectors.contains("J1772COMBO")) {
            binaryConnectors += '1'
        } else {
            binaryConnectors += '0'
        }
        if(connectors.contains("TESLA")) {
            binaryConnectors += '1'
        } else {
            binaryConnectors += '0'
        }
        if(connectors.contains("CHADEMO")) {
            binaryConnectors += '1'
        } else {
            binaryConnectors += '0'
        }
        if(connectors.contains("NEMA1450")) {
            binaryConnectors += '1'
        } else {
            binaryConnectors += '0'
        }
        if(connectors.contains("NEMA515")) {
            binaryConnectors += '1'
        } else {
            binaryConnectors += '0'
        }
        if(connectors.contains("NEMA520")) {
            binaryConnectors += '1'
        } else {
            binaryConnectors += '0'
        }

        val intent = Intent(context, FilterActivity::class.java)
        intent.putExtra("levels", binaryLevels)
        intent.putExtra("connectors", binaryConnectors)
        intent.putExtra("radius", searchRadius)
        resultLauncher.launch(intent)
        //overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left)
    }

    private var resultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == 1) {
            // There are no request codes
            val data: Intent? = result.data
            val binaryLevels = data?.getStringExtra("levels")
            val binaryConnectors = data?.getStringExtra("connectors")
            val newRadius = data?.getDoubleExtra("radius", 4.0)

            levels.clear()
            connectors.clear()

            for(i in 0..2){
                if(binaryLevels?.get(i)  == '1'){
                    levels.add(allLevels[i])
                }
            }
            for(i in 0..6){
                if(binaryConnectors?.get(i)  == '1'){
                    connectors.add(allConnectors[i])
                }
            }

            if (newRadius != null) {
                searchRadius = newRadius
            }
        }
    }

    private fun bitmapDescriptorFromVector(context : Context, vectorResId : Int): BitmapDescriptor {
        val vectorDrawable : Drawable = ContextCompat.getDrawable(context, vectorResId)!!
        val width = vectorDrawable.intrinsicWidth
        val height = vectorDrawable.intrinsicHeight
        val bitmap : Bitmap = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        vectorDrawable.setBounds(0, 0, width, height)
        vectorDrawable.draw(canvas)
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        Log.i("MapsFragment", "onCreateView")
        return inflater.inflate(R.layout.fragment_maps, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.i("MapsFragment", "onViewCreated")
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(callback)
    }
}