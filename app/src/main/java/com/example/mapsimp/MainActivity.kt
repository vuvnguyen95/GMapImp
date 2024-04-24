package com.example.mapsimp

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import android.content.Intent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import android.content.ComponentName
import android.content.Context
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.android.whileinuselocation.ForegroundOnlyLocationService
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationRequest.Builder
import com.google.android.gms.location.Priority



class MainActivity : AppCompatActivity(), OnMapReadyCallback {
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var locationRequest: LocationRequest
    private lateinit var locationCallback: LocationCallback
    private lateinit var locationTextView: TextView
    private lateinit var locationButton: Button
    private var currentLocation: Location? = null
    private var foregroundOnlyLocationService: ForegroundOnlyLocationService? = null
    private lateinit var foregroundOnlyBroadcastReceiver: ForegroundOnlyBroadcastReceiver
    private var isServiceBound = false
    private var isTrackingLocation = false
    private var mGoogleMap:GoogleMap?= null

    // Provides callbacks for service binding, passed to bindService()
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, service: IBinder) {
            val binder = service as ForegroundOnlyLocationService.LocalBinder
            foregroundOnlyLocationService = binder.service
            isServiceBound = true
            // Now you can access methods within ForegroundOnlyLocationService
        }

        override fun onServiceDisconnected(name: ComponentName) {
            foregroundOnlyLocationService = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        locationTextView = findViewById(R.id.viewLocation)
        foregroundOnlyBroadcastReceiver = ForegroundOnlyBroadcastReceiver()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)


        checkPermissionsAndInitialize()
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        locationButton = findViewById<Button>(R.id.locationButton)
        locationButton.setOnClickListener {
            if (foregroundPermissionApproved()) {
                isTrackingLocation = !isTrackingLocation
                if (isTrackingLocation) {
                    subscribeToLocationUpdates()
                    startForegroundLocationUpdates()
                    updateButtonState(isTrackingLocation)
                } else {
                    unsubscribeToLocationUpdates()
                    stopForegroundLocationUpdates()
                    updateButtonState(isTrackingLocation)
                }
            } else {
                requestPermissions()
            }
        }

        initializeLocationComponents()
    }

    @SuppressLint("MissingPermission")
    override fun onMapReady(googleMap: GoogleMap) {
        mGoogleMap = googleMap
        mGoogleMap?.uiSettings?.isZoomControlsEnabled = true

        // If there's a last known location, move the camera to it

        fusedLocationProviderClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLocation = LatLng(location.latitude, location.longitude)
                mGoogleMap?.animateCamera(CameraUpdateFactory.newLatLngZoom(userLocation, 12f))
            }
        }
    }

    override fun onStart() {
        super.onStart()
        updateButtonState(isTrackingLocation)

        Intent(this, ForegroundOnlyLocationService::class.java).also { intent ->
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    override fun onResume() {
        super.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(
            foregroundOnlyBroadcastReceiver,
            IntentFilter(ForegroundOnlyLocationService.ACTION_FOREGROUND_ONLY_LOCATION_BROADCAST)
        )
    }

    override fun onPause() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(foregroundOnlyBroadcastReceiver)
        super.onPause()
    }


    override fun onStop() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        super.onStop()
    }

    private fun startForegroundLocationUpdates() {
        if (isServiceBound) {
            foregroundOnlyLocationService?.subscribeToLocationUpdates()
        } else {
            // Service is not bound yet. Operations can be queued or handled differently.
        }
    }

    private fun stopForegroundLocationUpdates() {
        if (isServiceBound) {
            foregroundOnlyLocationService?.unsubscribeToLocationUpdates()
        }
    }

    private fun checkPermissionsAndInitialize() {
        if (foregroundPermissionApproved()) {
            initializeLocationComponents()
        } else {
            requestPermissions()
        }
    }

    private fun foregroundPermissionApproved(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_LOCATION_PERMISSION)
    }

    private fun initializeLocationComponents() {
        locationRequest = Builder(Priority.PRIORITY_HIGH_ACCURACY, 100)
            .setIntervalMillis(10000L)
            .setMinUpdateIntervalMillis(5000L)
            .setWaitForAccurateLocation(false)
            .setMaxUpdateDelayMillis(100)
            .build()


        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                Log.d("MainActivity", "Location: ${locationResult.lastLocation}")
                for (location in locationResult.locations) {
                    updateUIWithLocation(location)
                }
            }
        }
    }

    private fun updateUIWithLocation(location: Location) {
        currentLocation = location // Update the current location

        if (isTrackingLocation) {
            val newLocation = LatLng(location.latitude, location.longitude)

            // Clear existing markers
            mGoogleMap?.clear()

            // Add a marker at the new location and move the camera
            mGoogleMap?.addMarker(MarkerOptions().position(newLocation).title("Current Location"))
            mGoogleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(newLocation, 12f))

            // Update text view as before
            val newLocationString = "\t\tLat: ${location.latitude}, Lng: ${location.longitude}"
            val existingText = locationTextView.text.toString()
            locationTextView.text = if (existingText.isEmpty()) {
                newLocationString
            } else {
                "$existingText\n$newLocationString"
            }
        }
    }




    private fun subscribeToLocationUpdates() {
        try {
            fusedLocationProviderClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } catch (unlikely: SecurityException) {
            Toast.makeText(this, "Location permission was lost.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun unsubscribeToLocationUpdates() {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        Toast.makeText(this, "Location updates were removed.", Toast.LENGTH_SHORT).show()
    }
    companion object {
        private const val REQUEST_CODE_LOCATION_PERMISSION = 1
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission was granted. If location updates were previously started, continue.

                    subscribeToLocationUpdates()

            } else {
                // Permission was denied.
                Toast.makeText(this, "Location permission was denied.", Toast.LENGTH_SHORT).show()
                isTrackingLocation = false
                updateButtonState(isTrackingLocation)
            }
        }
    }
    private fun updateButtonState(trackingLocation: Boolean) {
        if (trackingLocation) {
            locationButton.text = getString(R.string.stop_location_updates_button_text)
        } else {
            locationButton.text = getString(R.string.start_location_updates_button_text)
        }
    }

    private inner class ForegroundOnlyBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val location = intent.getParcelableExtra<Location>(ForegroundOnlyLocationService.EXTRA_LOCATION)
            if (location != null) {
                updateUIWithLocation(location)
            }
        }
    }
}