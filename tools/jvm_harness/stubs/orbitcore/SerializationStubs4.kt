package kotlinx.serialization.json
sealed class JsonElement
class JsonBuilder { var encodeDefaults: Boolean = false }
class Json { inline fun <reified T> encodeToString(value: T): String = value.toString() }
fun Json(builderAction: JsonBuilder.() -> Unit): Json = Json()
