package com.example

import android.app.Application
import com.example.di.AppContainer

class StridePulseApplication : Application() {
    val appContainer: AppContainer by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        AppContainer(applicationContext)
    }
}
