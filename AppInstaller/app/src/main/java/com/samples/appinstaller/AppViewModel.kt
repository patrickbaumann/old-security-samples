/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.samples.appinstaller

import android.app.Application
import android.view.View
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.samples.appinstaller.AppSettings.AutoUpdateSchedule
import com.samples.appinstaller.AppSettings.UpdateAvailabilityPeriod
import com.samples.appinstaller.apps.AppPackage
import com.samples.appinstaller.apps.AppRepository
import com.samples.appinstaller.apps.AppStatus
import com.samples.appinstaller.apps.SampleStoreDB
import com.samples.appinstaller.library.DATABASE_NAME
import com.samples.appinstaller.settings.appSettings
import com.samples.appinstaller.settings.toDuration
import com.samples.appinstaller.workers.InstallWorker
import com.samples.appinstaller.workers.InstallWorker.Companion.PACKAGE_ID_KEY
import com.samples.appinstaller.workers.InstallWorker.Companion.PACKAGE_LABEL_KEY
import com.samples.appinstaller.workers.InstallWorker.Companion.PACKAGE_NAME_KEY
import com.samples.appinstaller.workers.PackageInstallerUtils
import com.samples.appinstaller.workers.UPGRADE_WORKER_TAG
import com.samples.appinstaller.workers.UpgradeWorker
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch

typealias LibraryEntries = Map<String, AppPackage>

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val appContext: AppInstallerApplication
        get() = getApplication()

    private val appRepository: AppRepository by lazy { AppRepository(appContext) }

    val isPermissionGranted: Boolean
        get() = appRepository.canRequestPackageInstalls()

    val appSettings: LiveData<AppSettings> = appContext.appSettings.data.asLiveData()

    val appDB = Room.databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME).build()

    private val _library = MutableLiveData<LibraryEntries>(emptyMap())
    val library: LiveData<LibraryEntries> = _library

    val autoUpdateSchedule =
        appContext.appSettings.data.mapLatest { it.autoUpdateSchedule }.asLiveData()

    val updateAvailabilityPeriod =
        appContext.appSettings.data.mapLatest { it.updateAvailabilityPeriod }.asLiveData()

    init {
        viewModelScope.launch {
            loadLibrary()
            appContext.syncEvents.collect(::syncLibrary)
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            val updatedLibrary = appRepository.getInstalledPackages()
                .mapNotNull {
                    SampleStoreDB[it.packageName]?.copy(
                        status = AppStatus.INSTALLED,
                        updatedAt = it.lastUpdateTime
                    )
                }
                .map { it.name to it }
                .toMap()

            val ongoingInstallSessions = appDB.sessionDao()
                .getAll()
                .filter { !it.isExpired }
                .mapNotNull {
                    SampleStoreDB[it.packageName]?.copy(
                        status = AppStatus.INSTALLING
                    )
                }
                .map { it.name to it }
                .toMap()

            _library.value = SampleStoreDB + updatedLibrary + ongoingInstallSessions
        }
    }

    /**
     * Sync app library and check if apps have been installed since last sync
     */
    private fun syncLibrary(event: SyncEvent) {
        val library = library.value ?: return
        val app = library[event.packageName] ?: return

        val updatedLibrary = mapOf(
            when (event.type) {
                SyncEventType.INSTALLING -> app.name to app.copy(status = AppStatus.INSTALLING)
                SyncEventType.INSTALL_SUCCESS -> app.name to app.copy(
                    status = AppStatus.INSTALLED,
                    updatedAt = System.currentTimeMillis()
                )
                SyncEventType.INSTALL_FAILURE -> app.name to app.copy(status = AppStatus.UNINSTALLED)
                SyncEventType.UNINSTALL_SUCCESS -> app.name to app.copy(
                    status = AppStatus.UNINSTALLED,
                    updatedAt = -1
                )
                SyncEventType.UNINSTALL_FAILURE -> app.name to app.copy(status = AppStatus.INSTALLED)
            }
        )

        _library.value = library + updatedLibrary
    }

    /**
     * Open an app by its id.
     */
    fun openApp(appPackage: AppPackage) {
        appRepository.openApp(appPackage.name)
    }

    /**
     * Uninstall an app by its id.
     */
    fun uninstallApp(appPackage: AppPackage) {
        viewModelScope.launch {
            if (appRepository.isAppInstalled(appPackage.name)) {
                PackageInstallerUtils.uninstallApp(appContext, appPackage.name)
            }
        }
    }

    /**
     * Install app by creating an install session and write the app's apk in it.
     */
    fun installApp(app: AppPackage) {
        val installWorkRequest = OneTimeWorkRequestBuilder<InstallWorker>()
            .setInputData(
                workDataOf(
                    PACKAGE_ID_KEY to app.id,
                    PACKAGE_NAME_KEY to app.name,
                    PACKAGE_LABEL_KEY to app.label
                )
            )
            .build()

        WorkManager.getInstance(appContext).enqueue(installWorkRequest)
    }

    /**
     * Upgrade app by creating an install session with a different intent filter from the normal
     * install flow and write the app's apk in it
     *
     * TODO: This method should be a WorkManager worker running in the foreground
     */
//    @Suppress("BlockingMethodInNonBlockingContext")
//    fun upgradeApp(app: AppPackage) {
//        viewModelScope.launch {
//            val sessionInfo = appRepository.getCurrentInstallSession(app.name)
//                ?: appRepository.getSessionInfo(
//                    appRepository.createInstallSession(
//                        app.label,
//                        app.name
//                    )
//                )
//                ?: return@launch
//
//            // We're updating the library entry to show a progress bar
//            appContext.emitSyncEvent(SyncEvent(SyncEventType.INSTALLING, app.name))
//
//            // We fake a delay to show active work. This would be replaced by real APK download
//            delay(DOWNLOADING_DELAY)
//
//            appRepository.writeAndCommitSession(
//                sessionId = sessionInfo.sessionId,
//                apkInputStream = appContext.assets.open("${app.name}.apk"),
//                isUpgrade = true
//            )
//        }
//    }

    /**
     * Setter for the [AutoUpdateSchedule] setting
     */
    fun setAutoUpdateSchedule(value: Int) {
        viewModelScope.launch {
            val autoUpdateSchedule = appContext.appSettings
                .updateData { currentSettings ->
                    currentSettings.toBuilder().setAutoUpdateScheduleValue(value).build()
                }
                .autoUpdateSchedule

            // Cancel previous periodic task
            WorkManager.getInstance(appContext).cancelAllWorkByTag(UPGRADE_WORKER_TAG)

            if (autoUpdateSchedule != AutoUpdateSchedule.MANUAL) {
                // Schedule new one based on schedule
                PeriodicWorkRequestBuilder<UpgradeWorker>(autoUpdateSchedule.toDuration())
                    .addTag(UPGRADE_WORKER_TAG)
                    .build()
            }
        }
    }

    /**
     * Setter for the [UpdateAvailabilityPeriod] setting
     */
    fun setUpdateAvailabilityPeriod(value: Int) {
        viewModelScope.launch {
            appContext.appSettings.updateData { currentSettings ->
                currentSettings.toBuilder()
                    .setUpdateAvailabilityPeriodValue(value)
                    .build()
            }
        }
    }

    /**
     * Trigger auto updating manually
     */
    fun triggerAutoUpdating(@Suppress("UNUSED_PARAMETER") view: View) {
        val upgradeWorkRequest = OneTimeWorkRequestBuilder<UpgradeWorker>().build()
        WorkManager.getInstance(appContext).enqueue(upgradeWorkRequest)
    }
}
