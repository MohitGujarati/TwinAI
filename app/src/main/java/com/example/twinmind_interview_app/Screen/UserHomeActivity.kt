package com.example.twinmind_interview_app.Screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.twinmind_interview_app.Adapter.UserHomePagerAdapter
import com.example.twinmind_interview_app.BuildConfig
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Utils.navigateHandlers
import com.example.twinmind_interview_app.databinding.ActivityUserHomeBinding
import com.example.twinmind_interview_app.viewmodel.UserHomeViewModel
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.Scope
import com.google.android.material.tabs.TabLayoutMediator

class UserHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserHomeBinding
    private lateinit var navigation: navigateHandlers
    private val viewModel: UserHomeViewModel by viewModels()
    private var accessToken: String? = null
    val clientId = BuildConfig.CLIENT_ID

    private val CALENDAR_SCOPES = listOf(
        Scope("https://www.googleapis.com/auth/calendar.readonly"),
        Scope("https://www.googleapis.com/auth/calendar.events.readonly")
    )
    private var googleSignInAccount: GoogleSignInAccount? = null

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navigation = navigateHandlers()

        // Set up ViewPager2 and Tabs
        val pagerAdapter = UserHomePagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> getString(R.string.memories)
                1 -> getString(R.string.calendar)
                2 -> getString(R.string.questions)
                else -> ""
            }
        }.attach()

        // Profile section navigation
        binding.ivProfile.setOnClickListener {
            navigation.navigateMsgToAnotherActivity(
                this, "RecodingStart", "true", ProfileSectionaActivity::class.java
            )
        }

        // "Capture" button navigation
        binding.btnCapture.setOnClickListener {
            checkPermissionsAndNavigate()
        }

        // Search button
        binding.btnSearch.setOnClickListener {
            Toast.makeText(this, "Search coming soon!", Toast.LENGTH_SHORT).show()
        }

        // Calendar permission
        checkCalendarPermission()
    }

    // Provide token to fragments
    fun getGoogleCalendarToken(): String? {
        return accessToken
    }

    // ---- Permission Handling for Audio ----
    private fun checkPermissionsAndNavigate() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO), 100
            )
        } else {
            navigateToAudioRec()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                navigateToAudioRec()
            } else {
                Toast.makeText(this, "Microphone permission required!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun navigateToAudioRec() {
        navigation.navigateMsgToAnotherActivity(
            this, "RecodingStart", "true", AudioRecActivity::class.java
        )
        finish()
    }

    // ---- Calendar Permissions and Google Sign-In ----
    @RequiresApi(Build.VERSION_CODES.O)
    private fun checkCalendarPermission() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            ensureCalendarAccess()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            ensureCalendarAccess()
        } else {
            Toast.makeText(this, "Calendar permission required", Toast.LENGTH_SHORT).show()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun ensureCalendarAccess() {
        if (!isGooglePlayServicesAvailable()) {
            Toast.makeText(this, "Google Play Services not available", Toast.LENGTH_LONG).show()
            return
        }
        if (!isDeviceOnline()) {
            Toast.makeText(this, "No Internet connection", Toast.LENGTH_LONG).show()
            return
        }
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null || !GoogleSignIn.hasPermissions(
                account,
                *CALENDAR_SCOPES.toTypedArray()
            )
        ) {
            requestGoogleSignIn()
        } else {
            googleSignInAccount = account
            fetchCalendarEvents()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun requestGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/calendar.readonly"),
                Scope("https://www.googleapis.com/auth/calendar.events.readonly")
            )
            .requestIdToken(clientId)
            .requestServerAuthCode(clientId, false) // to enable code exchange for token
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        GoogleSignIn.getSignedInAccountFromIntent(result.data)
            .addOnSuccessListener { account ->
                googleSignInAccount = account
                fetchCalendarEvents()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Sign-in failed: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchCalendarEvents() {
        // This is where you get the accessToken for REST API (store it for Fragments)
        // You should use this token in your CalendarFragment via getGoogleCalendarToken()
        // Here's an example how to get token:
        if (googleSignInAccount == null) return
        Thread {
            try {
                val token = googleSignInAccount!!.account?.let {
                    GoogleAuthUtil.getToken(
                        this,
                        it,
                        "oauth2:https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/calendar.events.readonly"
                    )
                }
                // Store token for fragments to use:
                runOnUiThread {
                    accessToken = token
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Token error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ---- Utility: Check Play Services & Network ----
    private fun isGooglePlayServicesAvailable(): Boolean {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        return status == ConnectionResult.SUCCESS
    }
    private fun isDeviceOnline(): Boolean {
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        return (networkInfo != null && networkInfo.isConnected)
    }
}
