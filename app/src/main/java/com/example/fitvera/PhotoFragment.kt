package com.example.fitvera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide

class PhotoFragment : Fragment() {
    companion object {
        fun newInstance(photoUrl: String) = PhotoFragment().apply {
            arguments = Bundle().apply {
                putString("photoUrl", photoUrl)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_photo, container, false)
        val photoUrl = arguments?.getString("photoUrl")
        val photoView: ImageView = view.findViewById(R.id.training_photo)

        if (!photoUrl.isNullOrEmpty()) {
            Glide.with(this).load(photoUrl).into(photoView)
        }
        return view
    }
}