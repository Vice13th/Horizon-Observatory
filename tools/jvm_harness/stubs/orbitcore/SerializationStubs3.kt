package kotlinx.serialization.encoding
interface Decoder { fun decodeString(): String }
interface Encoder { fun encodeString(value: String) }
