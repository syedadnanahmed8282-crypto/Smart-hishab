package com.smarthisab.app

import android.app.Application
import androidx.room.Room
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.smarthisab.app.data.AppDb
import com.smarthisab.app.data.MIGRATION_1_2
import com.smarthisab.app.data.Repo
import com.smarthisab.app.data.WeatherService
import com.smarthisab.app.data.buildSupabase
import com.smarthisab.app.sync.SyncWorker
import java.util.concurrent.TimeUnit

class App : Application() {
    lateinit var repo: Repo
        private set
    lateinit var weather: WeatherService
        private set

    override fun onCreate() {
        super.onCreate()
        val db = Room.databaseBuilder(this, AppDb::class.java, "smarthisab.db").addMigrations(MIGRATION_1_2).build()
        repo = Repo(db, buildSupabase())
        weather = WeatherService(this, repo)
        val request = PeriodicWorkRequestBuilder<SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork("sync", ExistingPeriodicWorkPolicy.KEEP, request)
    }
}
