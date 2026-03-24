package com.example.fitvera

import Rutina
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class RutinaViewModel : ViewModel() {
    val rutinaSeleccionada = MutableLiveData<Rutina?>()
}