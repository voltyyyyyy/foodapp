package sg.foodbridge.navigator

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/** In-process observer for catalogue invalidations received from the push service/worker. */
object CatalogueObservers {
    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes = mutableChanges.asSharedFlow()
    fun changed() { mutableChanges.tryEmit(Unit) }
}

object PushRegistration {
    fun registerIfConfigured(context: Context, repository: Repository, scope: CoroutineScope) {
        if (FirebaseApp.getApps(context).isEmpty()) return
        runCatching { FirebaseMessaging.getInstance().token }
            .onSuccess { task ->
                task.addOnSuccessListener { token ->
                    if (token.isNullOrBlank()) return@addOnSuccessListener
                    scope.launch(Dispatchers.IO) {
                        if (!repository.sessionValid()) return@launch
                        repository.accountId = repository.sessionStore.read().optJSONObject("account")?.optString("id")
                        runCatching { repository.registerPushDevice(token) }
                    }
                }
            }
    }

    internal fun rememberAndRegister(context: Context, token: String) {
        val repository = Repository(context.applicationContext)
        repository.sessionStore.savePushToken(token)
        if (!repository.sessionValid()) return
        repository.accountId = repository.sessionStore.read().optJSONObject("account")?.optString("id")
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { repository.registerPushDevice(token) }
        }
    }
}

class CatalogueMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        PushRegistration.rememberAndRegister(applicationContext, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["type"] != "catalogue_changed") return
        val foreground = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        if (foreground) {
            CatalogueObservers.changed()
            return
        }
        val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val work = OneTimeWorkRequestBuilder<CatalogueRefreshWorker>()
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
            CatalogueRefreshWorker.WORK_NAME, ExistingWorkPolicy.REPLACE, work
        )
    }
}

class CatalogueRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repository = Repository(applicationContext)
        if (!repository.sessionValid()) return Result.success()
        repository.accountId = repository.sessionStore.read().optJSONObject("account")?.optString("id")
        return try {
            val (_, servicesOffline) = repository.all()
            val (_, providersOffline) = repository.providers()
            if (servicesOffline || providersOffline) Result.retry()
            else {
                CatalogueObservers.changed()
                Result.success()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: SessionExpired) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object { const val WORK_NAME = "foodbridge-catalogue-refresh" }
}
