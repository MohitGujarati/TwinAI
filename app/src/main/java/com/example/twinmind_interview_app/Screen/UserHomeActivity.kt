package com.example.twinmind_interview_app.Screen

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.twinmind_interview_app.BuildConfig
import com.example.twinmind_interview_app.Utils.navigateHandlers
import com.example.twinmind_interview_app.databinding.ActivityUserHomeBinding
import com.example.twinmind_interview_app.model.CalendarEvent
import com.example.twinmind_interview_app.network.GoogleCalendarService
import com.example.twinmind_interview_app.network.RetrofitBuilder
import com.example.twinmind_interview_app.viewmodel.UserHomeViewModel
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.Scope
import com.google.android.material.tabs.TabLayout
import io.opencensus.stats.View
import kotlinx.coroutines.*
import java.time.Instant
import java.time.format.DateTimeFormatter

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

        setupClickListeners()
        setupTabListener()
        setupObservers()
        checkCalendarPermission()

        val clientId = BuildConfig.CLIENT_ID
        Log.d("ClientIDKey", "Client ID: $clientId")


      //  binding.tvCalendarEvents.visibility = View.GONE

        binding.ivProfile.setOnClickListener {
            navigation.navigateMsgToAnotherActivity(
                this, "RecodingStart", "true", ProfileSectionaActivity::class.java
            )
        }

        binding.btnCapture.setOnClickListener {
            checkPermissionsAndNavigate()
        }
    }

    private fun setupObservers() {
        viewModel.calendarEvents.observe(this) { events ->
            displayEvents(events)
        }
        viewModel.loading.observe(this) { isLoading ->
            if (isLoading) {
                binding.tvCalendarEvents.text = "Loading events..."
               // binding.tvCalendarEvents.visibility = View.VISIBLE
            }
        }
        viewModel.error.observe(this) { errorMsg ->
            errorMsg?.let {
                binding.tvCalendarEvents.text = "Error: $it"
                //binding.tvCalendarEvents.visibility = View.VISIBLE
            }
        }
    }

    private fun setupTabListener() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            @RequiresApi(Build.VERSION_CODES.O)
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    1 -> {
                        binding.tvCalendarEvents.visibility = android.view.View.VISIBLE
                        ensureCalendarAccess()
                    }
                    else -> binding.tvCalendarEvents.visibility = android.view.View.GONE
                }
            }
            override fun onTabUnselected(tab: TabLayout.Tab?) {}
            override fun onTabReselected(tab: TabLayout.Tab?) {}
        })
    }
    private fun setupClickListeners() {
        binding.btnSearch.setOnClickListener {
            Toast.makeText(this, "Search coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    // ----- Permission Handling for Audio -----
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
    // ----- Calendar Permissions and Google Sign-In -----
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
                binding.tvCalendarEvents.text = "Sign-in failed: ${it.message}"
                binding.tvCalendarEvents.visibility = android.view.View.VISIBLE
            }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun fetchCalendarEvents() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Get access token for REST API
                val accessToken = googleSignInAccount?.account?.let {
                    GoogleAuthUtil.getToken(
                        this@UserHomeActivity,
                        it,
                        "oauth2:https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/calendar.events.readonly"
                    )
                }
                val retrofit = accessToken?.let { RetrofitBuilder.getCalendarRetrofit(it) }
                val service = retrofit?.create(GoogleCalendarService::class.java)
                val now = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                val eventsResponse = service?.getEvents(timeMin = now)
                val calendarEvents = eventsResponse?.items

                withContext(Dispatchers.Main) {
                    if (calendarEvents != null) {
                        displayEvents(calendarEvents)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvCalendarEvents.text = "Error: ${e.localizedMessage}"
                    binding.tvCalendarEvents.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    // ----- Utility: Check Play Services & Network -----
    private fun isGooglePlayServicesAvailable(): Boolean {
        val status = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
        return status == ConnectionResult.SUCCESS
    }
    private fun isDeviceOnline(): Boolean {
        val connMgr = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connMgr.activeNetworkInfo
        return (networkInfo != null && networkInfo.isConnected)
    }

    // ----- Display Events -----
    private fun displayEvents(events: List<CalendarEvent>) {
        if (events.isEmpty()) {
            binding.tvCalendarEvents.text = "No events found."
        } else {
            binding.tvCalendarEvents.text =
                events.joinToString("\n\n") { "📅 ${it.summary}\n⏰ ${it.start?.dateTime ?: it.start?.date}" }
        }
        binding.tvCalendarEvents.visibility = android.view.View.VISIBLE
    }
}
