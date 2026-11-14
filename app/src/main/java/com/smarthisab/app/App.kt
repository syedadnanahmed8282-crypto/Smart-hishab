package com.smarthisab.app

import android.app.Application
import androidx.room.Room
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.russhwolf.settings.SettingsInitializer
import com.smarthisab.app.data.AppDb
import com.smarthisab.app.data.MIGRATION_1_2
import com.smarthisab.app.data.MIGRATION_2_3
import com.smarthisab.app.data.Repo
import com.smarthisab.app.data.WeatherService
import com.smarthisab.app.data.buildSupabase
import com.smarthisab.app.sync.SyncWorker
import java.util.concurrent.TimeUnit

class App : Application(), Configuration.Provider {
    lateinit var repo: Repo
        private set
    lateinit var weather: WeatherService
        private set

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    override fun onCreate() {
        super.onCreate()
        SettingsInitializer().create(this)
        val db = Room.databaseBuilder(this, AppDb::class.java, "smarthisab.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()
        repo = Repo(db, buildSupabase(this))
        weather = WeatherService(this, repo)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
