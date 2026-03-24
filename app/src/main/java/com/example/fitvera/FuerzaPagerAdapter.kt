// FuerzaPagerAdapter.kt
package com.example.fitvera

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class FuerzaPagerAdapter(fa: FragmentActivity) : FragmentStateAdapter(fa) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> FragmentCrearRutina() // Debes crear estos fragmentos con los XML anteriores
            else -> FragmentListarRutinas()
        }
    }
}