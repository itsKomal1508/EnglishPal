package com.englishpal.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Main Application class for EnglishPal.
 * @HiltAndroidApp triggers Hilt's code generation, creating a top-level
 * dependency injection container attached to the application's lifecycle.
 */
@HiltAndroidApp
class EnglishPalApp : Application()
