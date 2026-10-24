package kotlinx.serialization
import kotlin.reflect.KClass
annotation class Serializable(val with: KClass<*> = Nothing::class)
annotation class SerialName(val value: String)
annotation class EncodeDefault(val mode: Mode = Mode.ALWAYS) { enum class Mode { ALWAYS, NEVER } }
@RequiresOptIn(level = RequiresOptIn.Level.WARNING) annotation class ExperimentalSerializationApi
interface KSerializer<T> { val descriptor: kotlinx.serialization.descriptors.SerialDescriptor
    fun serialize(encoder: kotlinx.serialization.encoding.Encoder, value: T)
    fun deserialize(decoder: kotlinx.serialization.encoding.Decoder): T }
