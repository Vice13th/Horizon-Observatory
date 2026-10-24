package android.database
interface Cursor
class MatrixCursor(val columns: Array<String>, cap: Int) : Cursor { constructor(columns: Array<String>) : this(columns, 16); val rows = ArrayList<Array<Any?>>(); fun addRow(v: Array<Any?>) { rows.add(v) } }
