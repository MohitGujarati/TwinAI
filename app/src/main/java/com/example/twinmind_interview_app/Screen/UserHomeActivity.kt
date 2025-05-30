package com.example.twinmind_interview_app.Screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.twinmind_interview_app.databinding.ActivityUserHomeBinding

import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.Scope
import com.google.android.material.tabs.TabLayout
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.util.ExponentialBackOff
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class UserHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityUserHomeBinding
    private lateinit var auth: FirebaseAuth
    private var accessToken: String? = null

    private val CALENDAR_SCOPES = listOf(
        Scope("https://www.googleapis.com/auth/calendar.readonly"),
        Scope("https://www.googleapis.com/auth/calendar.events.readonly")
    )

    private val ClientID = "824470750323-v549rhj8mvgnj0gh5e4gfl84el53hagp.apps.googleusercontent.com"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityUserHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        setupClickListeners()
        checkCalendarPermission()


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


        binding.tvCalendarEvents.visibility = View.GONE



    }

    private fun checkCalendarPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) {
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
        if (account == null || !GoogleSignIn.hasPermissions(account, *CALENDAR_SCOPES.toTypedArray())) {
            requestGoogleSignIn()
        } else {
            getAccessTokenAndLoadEvents(account)
        }
    }

    private fun requestGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestScopes(Scope("https://www.googleapis.com/auth/calendar.readonly"),
                Scope("https://www.googleapis.com/auth/calendar.events.readonly"))
            .requestIdToken(ClientID)
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
                Toast.makeText(this, "Sign-in failed: ${it.message}", Toast.LENGTH_LONG).show()
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
                accessToken = credential.token
                loadCalendarEvents()
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvCalendarEvents.text = "Error: ${e.localizedMessage}"
                }
            }
        }
    }

    private fun loadCalendarEvents() {
        CoroutineScope(Dispatchers.Main).launch {
            binding.tvCalendarEvents.visibility = View.VISIBLE
            binding.tvCalendarEvents.text = "Loading events..."
        }

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val events = fetchCalendarEvents()
                withContext(Dispatchers.Main) {
                    displayEvents(events)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    binding.tvCalendarEvents.text = "Failed to load events: ${e.localizedMessage}\nTap to retry."
                    binding.tvCalendarEvents.setOnClickListener { loadCalendarEvents() }
                }
            }
        }
    }


    private suspend fun fetchCalendarEvents(): List<CalendarEvent> {
        val token = accessToken ?: throw Exception("Missing access token")

        val now = Calendar.getInstance()
        val weekLater = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 7) }
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)

        val url = URL("https://www.googleapis.com/calendar/v3/calendars/primary/events" +
                "?timeMin=${sdf.format(now.time)}&timeMax=${sdf.format(weekLater.time)}&orderBy=startTime&singleEvents=true&maxResults=10")

        (url.openConnection() as HttpURLConnection).apply {
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Content-Type", "application/json")

            if (responseCode != 200) throw Exception("HTTP $responseCode")

            return JSONObject(inputStream.bufferedReader().use(BufferedReader::readText))
                .getJSONArray("items").let { items ->
                    (0 until items.length()).map { parseEvent(items.getJSONObject(it)) }
                }
        }
    }

    private fun parseEvent(json: JSONObject) = CalendarEvent(
        json.optString("summary", "No Title"),
        json.optString("description"),
        json.optString("location"),
        json.getJSONObject("start").optString("dateTime", json.getJSONObject("start").optString("date"))
    )

    private fun displayEvents(events: List<CalendarEvent>) {
        binding.tvCalendarEvents.text = events.joinToString("\n\n") { "📅 ${it.summary}\n⏰ ${it.startTime}" }
    }

    private fun setupClickListeners() {
        binding.btnSearch.setOnClickListener {
            Toast.makeText(this, "Search coming soon!", Toast.LENGTH_SHORT).show()
        }

        binding.btnCapture.setOnClickListener {
            Toast.makeText(this, "Meeting capture coming soon!", Toast.LENGTH_SHORT).show()
        }
    }

    data class CalendarEvent(val summary: String, val description: String, val location: String, val startTime: String)
}
