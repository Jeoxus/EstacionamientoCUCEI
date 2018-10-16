package com.example.sjjos.estacionamientocucei

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.widget.TextView
import android.widget.ToggleButton
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import org.nield.kotlinstatistics.simpleRegression

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var parkingMarker: Marker
    companion object {
        private val REQUEST_ACCESS_FINE_LOCATION = 0
    }

    private lateinit var parkingArea : ArrayList<LatLng>
    private lateinit var userIcon : BitmapDescriptor
    private lateinit var otherUserIcon : BitmapDescriptor
    private lateinit var obstacleIcon : BitmapDescriptor

    private var obstacleMode : Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        setUpIcons()

        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val toggle: ToggleButton = findViewById(R.id.parkingButton)
        toggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                parkHere()
            } else {
                unpark()
            }
        }

        val obstacleButton: ToggleButton = findViewById(R.id.obstacleButton)
        obstacleButton.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enterObstacleMode()
            } else {
                exitObstacleMode()
            }
        }

        val remainingTimeTextView: TextView = findViewById(R.id.remainingTimeTextView)
        remainingTimeTextView.text = sequenceOf(1 to 3, 2 to 6, 3 to 9).simpleRegression().predict(4.0).toString()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        setUpMap()
    }

    private fun setUpIcons() {
        userIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
        otherUserIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
        obstacleIcon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
    }

    @SuppressLint("MissingPermission")
    private fun setUpMap() {
        if (mayRequestLocation()) {
            setUpParkingArea()
            populateMap()
            moveToParking()

            mMap.isMyLocationEnabled = true

            mMap.setOnMapClickListener{ position: LatLng ->
                if (obstacleMode) {
                    val markerOptions = MarkerOptions()
                            .position(position)
                            .icon(obstacleIcon)
                            .title("Obstáculo")
                            .snippet("Mantén presionado para reportar")
                    mMap.addMarker(markerOptions)

                    val obstacleButton: ToggleButton = findViewById(R.id.obstacleButton)
                    obstacleButton.isChecked = false
                }
            }

            mMap.setOnInfoWindowLongClickListener { marker: Marker? ->
                if (marker?.title == "Obstáculo" || marker?.title == "Usuario") {
                    marker.remove()
                }
            }
        }

    }

    private fun setUpParkingArea() {
        parkingArea = arrayListOf(
                LatLng(20.654238, -103.324803),
                LatLng(20.655111, -103.325158),
                LatLng(20.655995, -103.324966),
                LatLng(20.656371, -103.324415),
                LatLng(20.656404, -103.323997),
                LatLng(20.656159, -103.324003),
                LatLng(20.655938, -103.324615),
                LatLng(20.655402, -103.324860),
                LatLng(20.654893, -103.324825),
                LatLng(20.654360, -103.324499)
        )
        val parkingOptions = PolygonOptions()
                .addAll(parkingArea)
        mMap.addPolygon(parkingOptions)
    }

    private fun populateMap() {
        parkingArea.forEach {position: LatLng ->
            val markerOptions = MarkerOptions()
                    .position(position)
                    .icon(otherUserIcon)
                    .title("Usuario")
                    .snippet("Mantén presionado para reportar")
            mMap.addMarker(markerOptions)
        }
    }

    private fun moveToParking() {
        val coordinates = LatLng(20.655181, -103.324981)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(coordinates, 20f))
    }

    private fun mayRequestLocation() : Boolean {
        val permissionCheck = ContextCompat.checkSelfPermission(this@MainActivity,
                Manifest.permission.ACCESS_FINE_LOCATION)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED){
            return true
        }

        ActivityCompat.requestPermissions(this@MainActivity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                MainActivity.REQUEST_ACCESS_FINE_LOCATION)

        return false
    }

    @SuppressLint("MissingPermission")
    private fun parkHere() {
        if (mayRequestLocation()) {
            fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        val coordinates = LatLng(location?.latitude!!,location.longitude)
                        val markerOptions = MarkerOptions()
                            .position(coordinates)
                            .icon(userIcon)
                        parkingMarker = mMap.addMarker(markerOptions)
                    }
        }
    }

    private fun unpark() {
        parkingMarker.remove()
    }

    private fun enterObstacleMode() {
        obstacleMode = true
    }

    private fun exitObstacleMode() {
        obstacleMode = false
    }
}
