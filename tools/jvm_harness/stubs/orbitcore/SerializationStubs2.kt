package kotlinx.serialization.descriptors
interface SerialDescriptor
object PrimitiveKind { val STRING: Any = Any() }
fun PrimitiveSerialDescriptor(serialName: String, kind: Any): SerialDescriptor = object : SerialDescriptor {}
