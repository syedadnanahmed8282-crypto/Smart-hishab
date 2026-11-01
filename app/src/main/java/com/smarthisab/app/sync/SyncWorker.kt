package com.smarthisab.app.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.smarthisab.app.App
import com.smarthisab.app.data.AuthState
import kotlinx.coroutines.flow.first

class SyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val repo = (applicationContext as App).repo
        val state = repo.authState.first { it != AuthState.Loading }
        if (state != AuthState.In) return Result.success()
        return if (repo.sync().isSuccess) Result.success() else Result.retry()
    }
}
