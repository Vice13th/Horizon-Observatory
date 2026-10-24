package horizon.observatory.export

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

class StreamingJsonArrayWriterTest {
    @Test
    fun streamsAndCountsLongObservationArrayWithoutChangingObjects() {
        val file = File.createTempFile("horizon-observations-", ".json")
        try {
            val expectedCount = 48_915
            val count = StreamingJsonArrayWriter.write(
                file,
                (1..expectedCount).asSequence().map { sequence ->
                    JSONObject()
                        .put("sequenceNumber", sequence)
                        .put("payload", JSONObject().put("note", "value } ] inside"))
                }
            )
            assertEquals(expectedCount, count)
            assertEquals(expectedCount, StreamingJsonArrayWriter.countObjects(file))
            // Full parsing is limited to this regression fixture, not production export.
            val parsed = JSONArray(file.readText())
            assertEquals(expectedCount, parsed.length())
            assertEquals(1, parsed.getJSONObject(0).getInt("sequenceNumber"))
            assertEquals(expectedCount, parsed.getJSONObject(expectedCount - 1).getInt("sequenceNumber"))
            assertEquals("value } ] inside", parsed.getJSONObject(0).getJSONObject("payload").getString("note"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun incrementalSinkWritesRowsWithoutMaterializingASequence() {
        val file = File.createTempFile("horizon-paged-", ".json")
        try {
            val expectedCount = 48_915
            val writer = StreamingJsonArrayWriter.open(file)
            writer.use { array ->
                for (sequence in 1..expectedCount) {
                    array.append(JSONObject().put("sequenceNumber", sequence).put("payload", "page-stream"))
                }
                assertEquals(expectedCount, array.count)
                array.finish()
            }
            assertEquals(expectedCount, StreamingJsonArrayWriter.countObjects(file))
            val first = JSONObject(file.useLines { it.drop(1).first() })
            assertEquals(1, first.getInt("sequenceNumber"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsTruncatedJsonArray() {
        val file = File.createTempFile("horizon-truncated-", ".json")
        try {
            file.writeText("""[{"sequenceNumber":1}""")
            assertThrows(IllegalStateException::class.java) {
                StreamingJsonArrayWriter.countObjects(file)
            }
        } finally {
            file.delete()
        }
    }
}
