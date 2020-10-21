package com.example.kotlinuberremake.ui.home

import android.Manifest
import android.app.Activity
import android.content.IntentSender
import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.kotlinuberremake.Common
import com.example.kotlinuberremake.R
import com.firebase.geofire.GeoFire
import com.firebase.geofire.GeoLocation
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import java.lang.ClassCastException

class HomeFragment : Fragment(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var homeViewModel: HomeViewModel

    private  lateinit var mapFragment: SupportMapFragment

    // location
    private lateinit var locationRequest:LocationRequest
    private lateinit var locationCallback:LocationCallback
    private lateinit var fusedLocationProviderClient: FusedLocationProviderClient
    private lateinit var  builder: LocationSettingsRequest.Builder
    private lateinit var  result: Task<LocationSettingsResponse>
    private lateinit var  resolvableApiException:ResolvableApiException

    // online system
     private  lateinit var onlineRef: DatabaseReference
     private  lateinit var currentUserRef: DatabaseReference
     private  lateinit var driversLocationRef: DatabaseReference
     private  lateinit var geoFire: GeoFire

    private  val onlineValueEventListener = object:ValueEventListener
    {
        override fun onDataChange(snapshot: DataSnapshot)
        {
            if (snapshot.exists())
            {
                currentUserRef.onDisconnect().removeValue()
            }
        }

        override fun onCancelled(error: DatabaseError)
        {
            mapFragment.view?.let { Snackbar.make(it, error.message , Snackbar.LENGTH_LONG).show() }
        }

    }

      companion object
      {
        private const val REQUSET_CKECKE_CODE =8989
    }

    override fun onDestroy()
    {
        fusedLocationProviderClient.removeLocationUpdates(locationCallback)
        geoFire.removeLocation(FirebaseAuth.getInstance().currentUser!!.uid)
        onlineRef.removeEventListener(onlineValueEventListener)
        super.onDestroy()
    }

    override fun onResume()
    {
        super.onResume()
        registerOnlineSystem()
    }

    private fun registerOnlineSystem()
    {
      onlineRef.addValueEventListener(onlineValueEventListener)
    }

    override fun onCreateView(inflater: LayoutInflater,container: ViewGroup?,savedInstanceState: Bundle?): View?
    {
        homeViewModel = ViewModelProvider(this).get(HomeViewModel::class.java)
        val root = inflater.inflate(R.layout.fragment_home, container, false)

        init()

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        mapFragment = childFragmentManager .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)
        return root
    }

    private fun init()
    {
        onlineRef = FirebaseDatabase.getInstance().reference.child(".info/connected")
        driversLocationRef= FirebaseDatabase.getInstance().reference.child(Common.DRIVERS_LOCATION_REFERENCE)
        currentUserRef= FirebaseDatabase.getInstance().reference.child(Common.DRIVERS_LOCATION_REFERENCE)
            .child(FirebaseAuth.getInstance().currentUser!!.uid)

        geoFire= GeoFire(driversLocationRef)

        registerOnlineSystem()

        createLocationRequest()

        locationCallback = object:LocationCallback()
        {
            override fun onLocationResult(locationRequest: LocationResult?)
            {
                super.onLocationResult(locationRequest)

                val newPos = LatLng(
                    locationRequest!!.lastLocation.latitude,
                    locationRequest!!.lastLocation.longitude
                )
                mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(newPos, 18f))

                // Update location
                geoFire.setLocation(
                    FirebaseAuth.getInstance().currentUser!!.uid ,
                    GeoLocation(    locationRequest!!.lastLocation.latitude, locationRequest!!.lastLocation.longitude)
                ){
                    key: String?, error: DatabaseError? ->
                    if (error != null)
                    {
                        Snackbar.make(mapFragment.requireView() , error.message , Snackbar.LENGTH_LONG).show()
                    }
                    else
                    {

                        Snackbar.make(mapFragment.requireView() , "you're online !" , Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        fusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(requireContext())

        startLocationUpdates()

        // for enable to turn on Gbs
        builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)

       result = LocationServices.getSettingsClient(requireContext()).checkLocationSettings(builder.build())

        result.addOnCompleteListener { task: Task<LocationSettingsResponse> ->

            try {

            task.getResult(ApiException::class.java)
            }
            catch (e:ApiException)
            {
                when(e.statusCode)
                {

                   LocationSettingsStatusCodes.RESOLUTION_REQUIRED ->

                     try {
                         resolvableApiException = e as ResolvableApiException
                         resolvableApiException.startResolutionForResult(requireActivity(), REQUSET_CKECKE_CODE)
                     }
                     catch ( ex :IntentSender.SendIntentException)
                     {
                         ex.printStackTrace()
                     }catch (ex: ClassCastException  )
                     {

                     }

                    LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE  -> null

                }
            }
        }

    }

    override fun onMapReady(googleMap: GoogleMap?)
    {
        mMap = googleMap!!

        // Request permission
        Dexter.withContext(requireContext())
            .withPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(p0: PermissionGrantedResponse?) {

                    Toast.makeText(context!!, "Clicked Button", Toast.LENGTH_LONG).show()
                    // Enable Button
                    if(ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED){
                        ActivityCompat.requestPermissions(requireContext() as Activity, arrayOf( Manifest.permission.ACCESS_FINE_LOCATION),100)
                    }else{
                        mMap.isMyLocationEnabled = true

                    }
                    mMap.uiSettings.isMyLocationButtonEnabled = true
                    mMap.setOnMyLocationButtonClickListener {
                      getLocation()

                        true
                    }

                    // layout

                    val locationButton = (mapFragment.requireView()
                        .findViewById<View>("1".toInt())!!
                        .parent!! as View).findViewById<View>("2".toInt())
                    val params = locationButton.layoutParams as RelativeLayout.LayoutParams
                    params.addRule(RelativeLayout.ALIGN_TOP, 0)
                    params.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM, RelativeLayout.TRUE)
                    params.bottomMargin = 50


                }

                override fun onPermissionDenied(p0: PermissionDeniedResponse?) {
                    Toast.makeText(
                        context!!,
                        "Permissoin" + p0!!.permissionName + "was denied",
                        Toast.LENGTH_LONG
                    ).show()
                }

                override fun onPermissionRationaleShouldBeShown(
                    p0: PermissionRequest?,
                    p1: PermissionToken?
                ) {
                    TODO("Not yet implemented")
                }

            }).check()


        try {
            val success = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    context,
                    R.raw.uber_maps_style
                )
            )

            if (!success) {
                Log.e("EDMT_ERROR", "Style parsing error")
            }

        } catch (e: Resources.NotFoundException) {
            e.message?.let { Log.e("EDMT_ERROR", it) }
        }

    }

    private fun getLocation()
    {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 100
            )
            getLocation()
        } else {
            fusedLocationProviderClient.lastLocation
                .addOnSuccessListener { location ->
                    val userLatLng = LatLng(location.latitude, location.longitude)
                    mMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            userLatLng,
                            18f
                        )
                    )
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        requireContext(),
                        e.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
        }
    }

    private fun startLocationUpdates()
    {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
            &&
            ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ), 100
            )
            getLocation()
        } else {
            fusedLocationProviderClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )

        }
    }

    private fun createLocationRequest()
    {
        locationRequest = LocationRequest()
        locationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
        locationRequest.setFastestInterval(3000)
        locationRequest.interval = 5000
        locationRequest.setSmallestDisplacement(10f)
    }

}