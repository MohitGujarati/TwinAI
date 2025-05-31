package com.example.twinmind_interview_app.Screen

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.example.twinmind_interview_app.MainActivity
import com.example.twinmind_interview_app.R
import com.example.twinmind_interview_app.Utils.navigateHandlers
import com.example.twinmind_interview_app.databinding.ActivityProfileSectionBinding
import com.google.firebase.auth.FirebaseAuth

class ProfileSectionaActivity : AppCompatActivity() {
    lateinit var binding: ActivityProfileSectionBinding
    lateinit var navigation:navigateHandlers
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityProfileSectionBinding.inflate(layoutInflater)
        setContentView(binding.root)
        navigation = navigateHandlers()

        // Inside onCreate, after setContentView...
        val currentUser = FirebaseAuth.getInstance().currentUser
        val displayName = currentUser?.displayName ?: "UserName" // Fallback if null

        binding.userName.text = displayName


        binding.btnback.setOnClickListener {
            navigation.navigateToAnotherActivity(this, UserHomeActivity::class.java)
            finish()
        }

        binding.signOutButton.setOnClickListener {
            FirebaseAuth.getInstance().signOut()
            navigation.navigateToAnotherActivity(this, MainActivity::class.java)
            finish()
        }


    }
}