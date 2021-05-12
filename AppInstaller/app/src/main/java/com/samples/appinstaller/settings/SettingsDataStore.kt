package com.samples.appinstaller.settings

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.dataStore
import com.google.protobuf.InvalidProtocolBufferException
import com.samples.appinstaller.AppSettings
import java.io.InputStream
import java.io.OutputStream

const val PROTO_STORE_FILE_NAME = "app_settings.pb"

object AppSettingsSerializer : Serializer<AppSettings> {

    override val defaultValue: AppSettings = AppSettings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): AppSettings {
        try {
            return AppSettings.parseFrom(input)
        } catch (ipbe: InvalidProtocolBufferException) {
            throw CorruptionException("Cannot read proto.", ipbe)
        }
    }

    override suspend fun writeTo(t: AppSettings, output: OutputStream) = t.writeTo(output)
}

val Context.appSettings: DataStore<AppSettings> by dataStore(
    fileName = PROTO_STORE_FILE_NAME,
    serializer = AppSettingsSerializer
)