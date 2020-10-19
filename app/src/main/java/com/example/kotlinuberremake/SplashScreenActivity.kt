package com.example.kotlinuberremake

import android.app.Activity
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.example.kotlinuberremake.model.DriverInfoModel
import com.firebase.ui.auth.AuthMethodPickerLayout
import com.firebase.ui.auth.AuthUI
import com.firebase.ui.auth.IdpResponse
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import io.reactivex.Completable
import io.reactivex.android.schedulers.AndroidSchedulers
import kotlinx.android.synthetic.main.activity_splash_screen.*
import kotlinx.android.synthetic.main.layout_register.*
import kotlinx.android.synthetic.main.layout_register.view.*
import java.util.*
import java.util.concurrent.TimeUnit

class SplashScreenActivity : AppCompatActivity()
{

    private lateinit var providers :List<AuthUI.IdpConfig>
    private lateinit var firebaseAuth :FirebaseAuth
    private lateinit var listener :FirebaseAuth.AuthStateListener

    private lateinit var database :FirebaseDatabase
    private lateinit var driverInfoRef : DatabaseReference


    companion object
    {
        private val  LOGIN_REQUEST_CODE = 7171
        private const val TAG = "SplashScreenActivity"
    }

    override fun onStart()
    {
        super.onStart()
        delaySplashScreen();
    }

    override fun onStop()
    {
        if (firebaseAuth != null && listener != null)firebaseAuth.removeAuthStateListener(listener)

        super.onStop()

    }

    private fun delaySplashScreen()
    {
       Completable.timer(10 , TimeUnit.SECONDS, AndroidSchedulers.mainThread() )
        .subscribe({
      firebaseAuth.addAuthStateListener(listener)
    })
//        firebaseAuth.addAuthStateListener(listener)

    }


    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)

        init()

    }

    private fun init()
    {

        database = FirebaseDatabase.getInstance()
        driverInfoRef = database.getReference(Common.DRIVER_INFO_REFERENCE)

        providers = Arrays.asList(
            AuthUI.IdpConfig.PhoneBuilder().build() ,
            AuthUI.IdpConfig.GoogleBuilder().build()
        )

        firebaseAuth = FirebaseAuth.getInstance()
        listener = FirebaseAuth.AuthStateListener {  myFirebaseAuth ->
            val user = myFirebaseAuth.currentUser

            if (user != null)
            {
//                Toast.makeText(this@SplashScreenActivity , "Welcome"+ user .uid , Toast.LENGTH_LONG).show()

                checkUserFromFirebase()
                Log.d(TAG, "init :  listener")
            }
            else
                showLoginLayout()
            Log.d(TAG, "init : showLoginLayout ")

        }

    }

    private fun checkUserFromFirebase()
    {
        driverInfoRef
            .child(FirebaseAuth.getInstance().currentUser!!.uid)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {

                    if (snapshot.exists()) {
                       /* Toast.makeText(
                            this@SplashScreenActivity,
                            "register is already !",
                            Toast.LENGTH_LONG
                        ).show()*/

                        val model = snapshot.getValue(DriverInfoModel::class.java)
                        goToHomeActivity(model)

                        Log.d(TAG, "onDataChange:  snapshot.exists ")
                    } else {
                        showRegisterLayout()
                        Log.d(TAG, "onDataChange:  no has snapshot.exists ")
                    }

                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@SplashScreenActivity, error.message, Toast.LENGTH_LONG)
                        .show()
                }

            })

    }

    private fun goToHomeActivity(model: DriverInfoModel?)
    {
        Common.currentUser = model

        startActivity(Intent(this , DriverHomeActivity::class.java))
        finish()
    }

    private fun showRegisterLayout()
    {

        var builder = AlertDialog.Builder(this , R.style.DialogTheme)
        var itemView = LayoutInflater.from(this).inflate(R.layout.layout_register , null)

        val edtFirstName = itemView.edt_first_name
        val edtLastName = itemView.edt_last_name
        val edtPhoneNumber = itemView.edt_phone_namber

        val btnContinue = itemView.btn_register

        // set data
        if (FirebaseAuth.getInstance().currentUser!!.phoneNumber != null &&
                !TextUtils.isDigitsOnly(FirebaseAuth.getInstance().currentUser!!.phoneNumber ) )
            edtPhoneNumber.setText(FirebaseAuth.getInstance().currentUser!!.phoneNumber )

        // set View
        builder.setView(itemView)
        val dialog = builder.create()
        dialog.show()

        // Event
        btnContinue.setOnClickListener { v: View? ->

            if (TextUtils.isDigitsOnly(edtFirstName.text.toString()) )
            {
                Toast.makeText(this@SplashScreenActivity , "please enter first name" , Toast.LENGTH_SHORT).show()
                return@setOnClickListener

            }
            else if (TextUtils.isDigitsOnly(edtLastName.text.toString()) )
            {
                Toast.makeText(this@SplashScreenActivity , "please enter last name" , Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            else if (TextUtils.isDigitsOnly(edtPhoneNumber.text.toString()) )
            {
                Toast.makeText(this@SplashScreenActivity , "please enter phone number" , Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            else
            {
                val model = DriverInfoModel()
                model.firstName = edtFirstName.text.toString()
                model.lastName = edtLastName.text.toString()
                model.phoneNumber = edtPhoneNumber.text.toString()
                model.rating = 0.0

                driverInfoRef
                    .child(FirebaseAuth.getInstance().currentUser!!.uid)
                    .setValue(model)
                    .addOnFailureListener { exception ->
                        Toast.makeText(this@SplashScreenActivity , "" + exception.message , Toast.LENGTH_SHORT).show()
                        dialog.dismiss()

                        Log.d(TAG, "showRegisterLayout: addOnFailureListener ")
                        
                        progress_bar.visibility = View.GONE

                    }
                    .addOnSuccessListener {
                        Toast.makeText(this@SplashScreenActivity , "Register successfully"  , Toast.LENGTH_SHORT).show()
                        dialog.dismiss()

                        goToHomeActivity(model)
                        Log.d(TAG, "showRegisterLayout: addOnSuccessListener")
                        
                        progress_bar.visibility = View.GONE
                    }
            }
        }


    }

    private fun showLoginLayout()
    {

        val authMethodPickerLayout  = AuthMethodPickerLayout.Builder(R.layout.layout_sign_in)
            .setPhoneButtonId(R.id.btn_phone_sign_in)
            .setGoogleButtonId(R.id.btn_google_sign_in)
            .build()

        startActivityForResult(
            AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAuthMethodPickerLayout(authMethodPickerLayout)
                .setTheme(R.style.LoginTheme)
                .setAvailableProviders(providers)
                .setIsSmartLockEnabled(false)
                .build()
        ,
            LOGIN_REQUEST_CODE

        )


    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LOGIN_REQUEST_CODE)
        {
            val response = IdpResponse.fromResultIntent(data)
            Log.d(TAG, "onActivityResult: ")
            if (requestCode == Activity.RESULT_OK)
            {
                val user = FirebaseAuth.getInstance().currentUser
            }
            else
                Toast.makeText(this@SplashScreenActivity , ""+ response!!.error!!.message , Toast.LENGTH_SHORT).show()

        }
    }

    /*override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == LOGIN_REQUEST_CODE)
        {
            val response = IdpResponse.fromResultIntent(data)

            if (requestCode == Activity.RESULT_OK)
            {
                val user = FirebaseAuth.getInstance().currentUser
            }
            else
                Toast.makeText(this@SplashScreenActivity , ""+ response!!.error!!.message , Toast.LENGTH_SHORT).show()

        }
    }*/
}