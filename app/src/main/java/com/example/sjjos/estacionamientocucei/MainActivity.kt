package com.example.sjjos.estacionamientocucei

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.support.v7.app.AppCompatActivity
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.ToggleButton
import com.example.sjjos.estacionamientocucei.data.Spot
import com.github.kittinunf.fuel.android.core.Json
import com.github.kittinunf.fuel.android.extension.responseJson
import com.github.kittinunf.fuel.core.*
import com.github.kittinunf.result.Result
import com.github.kittinunf.fuel.gson.responseObject
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.httpPost
import com.github.sumimakito.awesomeqr.AwesomeQRCode
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import org.nield.kotlinstatistics.simpleRegression
import kotlin.math.absoluteValue

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private val nip = 209615458
    private val plate = "NGE-3311"
    private val secondParkingPosition = LatLng(20.655177, -103.321406)

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    companion object {
        private val REQUEST_ACCESS_FINE_LOCATION = 0
    }

    private lateinit var parkingArea : ArrayList<LatLng>
    private lateinit var userIcon : BitmapDescriptor
    private lateinit var otherUserIcon : BitmapDescriptor
    private lateinit var obstacleIcon : BitmapDescriptor

    private lateinit var spots: ArrayList<Spot>
    private var markers: ArrayList<Marker> = ArrayList()
    private var parkingMarker: Marker? = null

    private var obstacleMode : Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val qrView: ImageView = findViewById(R.id.qr)
        val contents = "codigo:$nip,placas:$plate"
        qrView.setImageBitmap(AwesomeQRCode.Renderer().contents(contents).size(800).margin(20).render())

        setUpIcons()
        startMap()

        val toggle: ToggleButton = findViewById(R.id.parkingButton)
        toggle.setOnCheckedChangeListener{_, isChecked: Boolean -> handleParkingToggle(isChecked)}
        val obstacleButton: ToggleButton = findViewById(R.id.obstacleButton)
        obstacleButton.setOnCheckedChangeListener { _, isChecked -> handleObstacleToggle(isChecked)}

        FuelManager.instance.basePath = "https://us-central1-estacionamiento-cucei-216705.cloudfunctions.net"
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        setUpMap()
    }

    private fun startMap() {
        val mapFragment = supportFragmentManager
                .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
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

            val secondParkingMarker = MarkerOptions()
                    .title("¡Segundo estacionamiento! :)")
                    .position(secondParkingPosition)
            mMap.addMarker(secondParkingMarker)

            mMap.isMyLocationEnabled = true

            mMap.setOnMapClickListener{ position: LatLng ->
                if (obstacleMode) {
                    val body = """
                        {
                            "lat": ${position.latitude},
                            "lng": ${position.longitude},
                            "nip": $nip
                        }
                    """.trimIndent()
                    "/newObstacleMarker".httpPost()
                        .header(mapOf("Content-Type" to "application/json"))
                        .body(body)
                        .response{ request: Request, response: Response, _ ->
                            Log.d("HTTP_REQ", request.toString())
                            when (response.statusCode){
                                200 -> {
                                    populateMap()

                                    val obstacleButton: ToggleButton = findViewById(R.id.obstacleButton)
                                    obstacleButton.isChecked = false
                                }
                            }
                        }
                }
            }

            mMap.setOnInfoWindowLongClickListener { marker: Marker? ->
                if (marker?.title == "obstacle" || marker?.title == "user") {
                    val body = """
                        {
                            "lat": ${marker.position.latitude},
                            "lng": ${marker.position.longitude},
                            "nip": $nip,
                            "type": "${marker.title}"
                        }
                    """.trimIndent()
                    "/reportMarker".httpPost()
                        .header(mapOf("Content-Type" to "application/json"))
                        .body(body)
                        .response{ request: Request, response: Response, _ ->
                            Log.d("HTTP_REQ", request.toString())
                            when (response.statusCode){
                                200 -> {
                                    populateMap()
                                }
                            }
                        }
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

    private fun handleParkingToggle(isChecked: Boolean) {
        if (isChecked) {
            parkHere()
        } else {
            unpark()
        }
    }

    private fun handleObstacleToggle(isChecked: Boolean) {
        if (isChecked) {
            enterObstacleMode()
        } else {
            exitObstacleMode()
        }
    }

    fun handleWaitButton(view: View) {
        "getRecentDepartures".httpGet().responseJson { _, response: Response, result: Result<Json, FuelError> ->
            when (response.statusCode){
                200 -> {
                    val departures = result.get().array()

                    val wait = if (departures.length() < 2) {
                        "no se pudo calcular la espera"
                    } else {
                        val times = ArrayList<Pair<Number, Number>>()

                        for (i in 0 until (departures.length() - 1)){
                            times.add(Pair(i, (departures[i+1] as Long) - (departures[i] as Long)))
                        }

                        val timesAsSequence = Sequence { times.iterator() }
                        val prediction = timesAsSequence.simpleRegression().predict(times.size*1.0)
                        Log.d("PREDICTION", prediction.toString())
                        "Pronóstico de espera: ${(prediction/1000/60).absoluteValue} minutos"
                    }

                    val remainingTimeTextView: TextView = findViewById(R.id.remainingTimeTextView)
                    remainingTimeTextView.text = wait
                }
            }
        }
    }

    fun handleShowParkingButton(view: View) {
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(secondParkingPosition, 18f))
    }

    fun handleShowQr(view: View) {
        val qrView: ImageView = findViewById(R.id.qr)
        qrView.visibility = View.VISIBLE
    }

    fun handleHideQr(view: View) {
        val qrView: ImageView = findViewById(R.id.qr)
        qrView.visibility = View.GONE
    }

    private fun populateMap() {
        "/getMarkers".httpGet().responseObject { _, response: Response, result: Result<ArrayList<Spot>, FuelError> ->
            when (response.statusCode){
                200 -> {
                    clearMap()
                    spots = result.get()
                    spots.forEach {spot: Spot ->
                        val position = LatLng(spot.lat, spot.lng)
                        val icon: BitmapDescriptor = if (spot.type == "user") {
                            otherUserIcon
                        } else {
                            obstacleIcon
                        }

                        if (position != parkingMarker?.position) {
                            val markerOptions = MarkerOptions()
                                    .position(position)
                                    .icon(icon)
                                    .title(spot.type)
                                    .snippet("Mantén presionado para reportar")
                            markers.add(mMap.addMarker(markerOptions))
                        }
                    }
                    val placesTextView: TextView = findViewById(R.id.places)
                    val places = if (parkingMarker != null) {
                        markers.size + 1
                    } else {
                        markers.size
                    }
                    placesTextView.visibility = View.VISIBLE
                    placesTextView.text = "${places}/393 lugares"
                }
            }
        }
    }

    private fun clearMap() {
        markers.forEach {marker: Marker ->
            marker.remove()
        }
        markers.clear()
    }

    private fun moveToParking() {
        val coordinates = LatLng(20.653900, -103.324630)
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(coordinates, 18f))
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
        val nip = 209615459
        if (mayRequestLocation()) {
            fusedLocationClient.lastLocation
                    .addOnSuccessListener { location: Location? ->
                        if (location != null) {
                            val body = """
                                {
                                    "lat": ${location.latitude},
                                    "lng": ${location.longitude},
                                    "nip": $nip
                                }
                            """.trimIndent()
                            "/newParkingMarker".httpPost()
                                    .header(mapOf("Content-Type" to "application/json"))
                                    .body(body)
                                    .response{ request: Request, response: Response, _ ->
                                Log.d("HTTP_REQ", request.toString())
                                when (response.statusCode){
                                    200 -> {
                                        val coordinates = LatLng(location.latitude, location.longitude)
                                        val markerOptions = MarkerOptions()
                                                .position(coordinates)
                                                .icon(userIcon)
                                        parkingMarker = mMap.addMarker(markerOptions)
                                    }
                                }
                            }
                        }
                    }
        }
    }

    private fun unpark() {
        val nip = 209615459
        val position = parkingMarker?.position
        val body = """
            {
                "lat": ${position?.latitude},
                "lng": ${position?.longitude},
                "nip": $nip
            }
        """.trimIndent()
        "/unpark".httpPost()
                .header(mapOf("Content-Type" to "application/json"))
                .body(body)
                .response { _, response: Response, _ ->
            when (response.statusCode) {
                200 -> {
                    parkingMarker?.remove()
                    parkingMarker = null
                }
            }
        }
    }

    private fun enterObstacleMode() {
        obstacleMode = true
    }

    private fun exitObstacleMode() {
        obstacleMode = false
    }
}
