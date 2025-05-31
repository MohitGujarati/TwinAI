package com.example.twinmind_interview_app.Screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.twinmind_interview_app.Screen.AudioRecActivity
import com.example.twinmind_interview_app.Screen.ProfileSectionaActivity
import com.example.twinmind_interview_app.databinding.ActivityUserHomeBinding
import com.example.twinmind_interview_app.model.CalendarEvent
import com.example.twinmind_interview_app.viewmodel.UserHomeViewModel
import com.example.twinmind_interview_app.Utils.navigateHandlers
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.Scope
import com.google.android.material.tabs.TabLayout
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import kotlinx.coroutines.*
import java.util.*

class UserHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserHomeBinding
    private lateinit var navigation: navigateHandlers
    private val viewModel: UserHomeViewModel by viewModels()
    private var accessToken: String? = null

    private val CALENDAR_SCOPES = listOf(
        Scope("https://www.googleapis.com/auth/calendar.readonly"),
        Scope("https://www.googleapis.com/auth/calendar.events.readonly")
    )
    private val CLIENT_ID =
        "824470750323-fm7ldk425ri1n3qaq88p848jj0vjcgfk.apps.googleusercontent.com"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navigation = navigateHandlers()

        setupClickListeners()
        setupTabListener()
        setupObservers()
        checkCalendarPermission()

        binding.tvCalendarEvents.visibility = View.GONE

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
                binding.tvCalendarEvents.visibility = View.VISIBLE
            }
        }
        viewModel.error.observe(this) { errorMsg ->
            errorMsg?.let {
                binding.tvCalendarEvents.text = "Error: $it"
                binding.tvCalendarEvents.visibility = View.VISIBLE
            }
        }
    }

    private fun setupTabListener() {
        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab?) {
                when (tab?.position) {
                    1 -> {
                        binding.tvCalendarEvents.visibility = View.VISIBLE
                        ensureCalendarAccess()
                    }
                    else -> binding.tvCalendarEvents.visibility = View.GONE
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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            ensureCalendarAccess()
        } else {
            Toast.makeText(this, "Calendar permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun ensureCalendarAccess() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account == null || !GoogleSignIn.hasPermissions(
                account,
                *CALENDAR_SCOPES.toTypedArray()
            )
        ) {
            requestGoogleSignIn()
        } else {
            getAccessTokenAndLoadEvents(account)
        }
    }

    private fun requestGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(
                Scope("https://www.googleapis.com/auth/calendar.readonly"),
                Scope("https://www.googleapis.com/auth/calendar.events.readonly")
            )
            .requestIdToken(CLIENT_ID)
            .build()

        val googleSignInClient = GoogleSignIn.getClient(this, gso)
        signInLauncher.launch(googleSignInClient.signInIntent)
    }

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        GoogleSignIn.getSignedInAccountFromIntent(result.data)
            .addOnSuccessListener(this::getAccessTokenAndLoadEvents)
            .addOnFailureListener {
                binding.tvCalendarEvents.text = "Sign-in failed: ${it.message}"
                binding.tvCalendarEvents.visibility = View.VISIBLE
            }
    }

    private fun getAccessTokenAndLoadEvents(account: GoogleSignInAccount) {
        CoroutineScope(Dispatchers.IO).launch {
            val credential = GoogleAccountCredential.usingOAuth2(
                this@UserHomeActivity,
                CALENDAR_SCOPES.map { it.scopeUri }
            ).setBackOff(ExponentialBackOff()).apply {
                selectedAccount = account.account
            }

            try {
                val token = credential.token
                withContext(Dispatchers.Main) {
                    accessToken = token
                    viewModel.loadCalendarEvents(token)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvCalendarEvents.text = "Error: ${e.localizedMessage}"
                    binding.tvCalendarEvents.visibility = View.VISIBLE
                }
            }
        }
    }

    // ----- Display Events -----
    private fun displayEvents(events: List<CalendarEvent>) {
        if (events.isEmpty()) {
            binding.tvCalendarEvents.text = "No events found."
        } else {
            binding.tvCalendarEvents.text = events.joinToString("\n\n") { "📅 ${it.summary}\n⏰ ${it.startTime}" }
        }
        binding.tvCalendarEvents.visibility = View.VISIBLE
    }
}
