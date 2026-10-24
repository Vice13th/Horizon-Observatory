package horizon.observatory.astronomy.identity

data class IdentityTableIssue(val line: Int, val message: String)
data class IdentityTableParseResult(
    val entries: List<SatelliteIdentityEntry>,
    val issues: List<IdentityTableIssue>,
    val dataRows: Int
)

/**
 * Operator-supplied GNSS → NORAD identity table (CSV). The app never ships or invents mappings:
 * each row must carry its source, evidence and (optionally) a validity window. Invalid rows are
 * skipped and reported, never repaired. Rule: only OPERATOR_SUPPLIED_TABLE rows can be VERIFIED.
 * Fields are single-line; quotes ("") are supported for commas, multi-line cells are not.
 */
object IdentityTableCsv {
    val COLUMNS = listOf(
        "constellation_type", "svid", "norad_cat_id", "source", "method", "confidence",
        "evidence", "valid_from_utc_ms", "valid_to_utc_ms"
    )
    val HEADER: String = COLUMNS.joinToString(",")

    /** Import size cap: a mapping table is small; refuse anything larger instead of exhausting memory. */
    const val MAX_BYTES = 2 * 1024 * 1024

    fun readBounded(input: java.io.InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8192)
        var total = 0
        while (true) {
            val n = input.read(chunk)
            if (n < 0) break
            total += n
            check(total <= MAX_BYTES) { "File too large (max ${MAX_BYTES / 1024} KB)" }
            out.write(chunk, 0, n)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    internal fun splitCsv(line: String): List<String> {
        val out = ArrayList<String>(); val sb = StringBuilder(); var quoted = false; var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                quoted && ch == '"' && i + 1 < line.length && line[i + 1] == '"' -> { sb.append('"'); i++ }
                ch == '"' -> quoted = !quoted
                ch == ',' && !quoted -> { out.add(sb.toString()); sb.setLength(0) }
                else -> sb.append(ch)
            }
            i++
        }
        out.add(sb.toString())
        return out
    }

    fun parse(text: String): IdentityTableParseResult {
        val issues = ArrayList<IdentityTableIssue>()
        val entries = ArrayList<SatelliteIdentityEntry>()
        var headerSeen = false
        var rows = 0
        text.removePrefix("\uFEFF").lines().forEachIndexed { idx, raw ->
            val n = idx + 1
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEachIndexed
            val cells = splitCsv(line).map { it.trim() }
            if (!headerSeen) {
                if (cells.map { it.lowercase() } != COLUMNS) {
                    issues.add(IdentityTableIssue(n, "first non-comment line must be exactly: $HEADER"))
                    return IdentityTableParseResult(emptyList(), issues, 0)
                }
                headerSeen = true
                return@forEachIndexed
            }
            rows++
            fun bad(msg: String) { issues.add(IdentityTableIssue(n, msg)) }
            if (cells.size != COLUMNS.size) return@forEachIndexed bad("expected ${COLUMNS.size} columns, found ${cells.size}")
            val constellation = cells[0].toIntOrNull()
            val svid = cells[1].toIntOrNull()
            val norad = cells[2].toLongOrNull()
            val method = MappingMethod.values().firstOrNull { it.name == cells[4] }
            val confidence = MappingConfidence.values().firstOrNull { it.name == cells[5] }
            val from = cells[7].takeIf { it.isNotEmpty() }?.let { it.toLongOrNull() ?: return@forEachIndexed bad("valid_from_utc_ms is not an integer") }
            val to = cells[8].takeIf { it.isNotEmpty() }?.let { it.toLongOrNull() ?: return@forEachIndexed bad("valid_to_utc_ms is not an integer") }
            when {
                constellation == null || constellation !in 1..7 -> bad("constellation_type must be an Android GnssStatus constant 1..7")
                svid == null || svid <= 0 -> bad("svid must be a positive integer")
                norad == null || norad <= 0L -> bad("norad_cat_id must be a positive integer")
                cells[3].isEmpty() -> bad("source is required")
                cells[6].isEmpty() -> bad("evidence is required (where does this mapping come from?)")
                method == null -> bad("method must be one of ${MappingMethod.values().joinToString { it.name }}")
                confidence == null -> bad("confidence must be one of ${MappingConfidence.values().joinToString { it.name }}")
                method != MappingMethod.OPERATOR_SUPPLIED_TABLE && confidence == MappingConfidence.VERIFIED ->
                    bad("only OPERATOR_SUPPLIED_TABLE rows can be VERIFIED; re-enter an inferred/hint row as OPERATOR_SUPPLIED_TABLE with your own evidence")
                from != null && to != null && from >= to -> bad("valid_from_utc_ms must be < valid_to_utc_ms")
                else -> entries.add(
                    SatelliteIdentityEntry(
                        GnssSatelliteId(constellation, svid), norad,
                        MappingProvenance(cells[3], method, confidence, cells[6], from, to)
                    )
                )
            }
        }
        if (!headerSeen) issues.add(IdentityTableIssue(0, "no header found"))
        return IdentityTableParseResult(entries, issues, rows)
    }
}

/** Resolver whose table can be replaced at runtime (e.g. after importing a CSV). Starts empty ⇒ all UNMATCHED. */
class ReloadableIdentityResolver : SatelliteIdentityResolver {
    @Volatile private var delegate: SatelliteIdentityResolver = TableSatelliteIdentityResolver(emptyList())
    @Volatile var entryCount: Int = 0
        private set

    fun replace(entries: List<SatelliteIdentityEntry>) {
        delegate = TableSatelliteIdentityResolver(entries)
        entryCount = entries.size
    }

    override fun resolve(id: GnssSatelliteId, at: horizon.observatory.astronomy.time.PropagationTime): SatelliteIdentityMapping =
        delegate.resolve(id, at)
}
