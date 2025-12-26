package com.example.demo

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide

class ImageViewActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_view)

        val imageView: ImageView = findViewById(R.id.fullImage)
        val imageUriString = intent.getStringExtra("imageUri")

        if (imageUriString != null) {
            Glide.with(this)
                .load(Uri.parse(imageUriString))
                .into(imageView)
        } else {
            imageView.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }
}
