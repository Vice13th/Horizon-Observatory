package horizon.observatory.astronomy.identity

import android.content.Context
import java.io.File

/** Persists the imported CSV text in app-private storage so the table survives restarts. */
object IdentityTableStore {
    private fun file(context: Context): File = File(File(context.filesDir, "identity").apply { mkdirs() }, "gnss_identity.csv")

    fun load(context: Context): IdentityTableParseResult? =
        file(context).takeIf { it.isFile }?.let { IdentityTableCsv.parse(it.readText()) }

    fun save(context: Context, text: String) {
        val f = file(context)
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(text)
        tmp.copyTo(f, overwrite = true)
        tmp.delete()
    }
}
