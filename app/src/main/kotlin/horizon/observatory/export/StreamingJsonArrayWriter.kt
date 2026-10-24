package horizon.observatory.export

import org.json.JSONObject
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File

/**
 * Bounded-memory JSON array output and readback counting for large evidence exports.
 * The writer emits one compact object at a time; the counter validates array/string structure
 * and counts top-level objects without parsing the complete archive entry into memory.
 */
internal object StreamingJsonArrayWriter {
    fun open(file: File): ArrayWriter = ArrayWriter(file)

    /** Incremental array sink for records obtained from suspend/paged data sources. */
    class ArrayWriter internal constructor(file: File) : Closeable {
        private val writer: BufferedWriter = file.bufferedWriter()
        var count: Int = 0
            private set
        private var finished = false

        init {
            writer.append("[\n")
        }

        fun append(item: JSONObject) {
            check(!finished) { "JSON array writer already finished" }
            if (count > 0) writer.append(",\n")
            writer.append("  ")
            writer.append(item.toString())
            count += 1
        }

        fun finish() {
            check(!finished) { "JSON array writer already finished" }
            writer.append("\n]\n")
            writer.flush()
            finished = true
        }

        override fun close() {
            writer.close()
        }
    }

    fun write(file: File, objects: Sequence<JSONObject>): Int {
        var count = 0
        file.bufferedWriter().use { writer ->
            writer.append("[\n")
            objects.forEach { item ->
                if (count > 0) writer.append(",\n")
                writer.append("  ")
                writer.append(item.toString())
                count += 1
            }
            writer.append("\n]\n")
        }
        return count
    }

    fun countObjects(file: File): Int = file.bufferedReader().use { reader ->
        var bracketDepth = 0
        var objectDepth = 0
        var inString = false
        var escaped = false
        var started = false
        var ended = false
        var count = 0
        while (true) {
            val code = reader.read()
            if (code < 0) break
            val ch = code.toChar()
            if (inString) {
                if (escaped) escaped = false
                else if (ch.code == 92) escaped = true
                else if (ch.code == 34) inString = false
                continue
            }
            if (ended && !ch.isWhitespace()) error("Unexpected content after JSON array")
            when (ch) {
                '"' -> inString = true
                '[' -> {
                    if (!started) {
                        check(bracketDepth == 0) { "JSON array did not start at root" }
                        started = true
                    }
                    bracketDepth += 1
                }
                ']' -> {
                    bracketDepth -= 1
                    check(bracketDepth >= 0) { "Unbalanced JSON array" }
                    if (bracketDepth == 0) ended = true
                }
                '{' -> {
                    if (bracketDepth == 1 && objectDepth == 0) count += 1
                    objectDepth += 1
                }
                '}' -> {
                    objectDepth -= 1
                    check(objectDepth >= 0) { "Unbalanced JSON object" }
                }
            }
        }
        check(started && ended && bracketDepth == 0 && objectDepth == 0 && !inString) {
            "Incomplete JSON array"
        }
        count
    }
}
