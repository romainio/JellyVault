package tv.jellyvault.app.catalog.arr

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject

class ArrSettingsSerializer
    @Inject
    constructor() : Serializer<ArrSettings> {
        override val defaultValue: ArrSettings = ArrSettings.getDefaultInstance()

        override suspend fun readFrom(input: InputStream): ArrSettings =
            try {
                ArrSettings.parseFrom(input)
            } catch (e: InvalidProtocolBufferException) {
                throw CorruptionException("Cannot read arr settings", e)
            }

        override suspend fun writeTo(
            t: ArrSettings,
            output: OutputStream,
        ) = t.writeTo(output)
    }
