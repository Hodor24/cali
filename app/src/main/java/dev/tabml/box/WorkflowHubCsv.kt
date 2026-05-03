package dev.tabml.box

/** Shared CSV escaping/parsing for hub export & import. */
object WorkflowHubCsv {

    /**
     * Splits text into physical CSV lines respecting double-quoted fields (newlines inside quotes stay in the line).
     */
    fun splitCsvPhysicalLines(input: String): List<String> {
        if (input.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        val cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '\r' && !inQuotes -> {
                    if (i + 1 < input.length && input[i + 1] == '\n') i++
                    lines.add(cur.toString())
                    cur.clear()
                }
                c == '\n' && !inQuotes -> {
                    lines.add(cur.toString())
                    cur.clear()
                }
                c == '"' -> {
                    cur.append('"')
                    if (inQuotes) {
                        if (i + 1 < input.length && input[i + 1] == '"') {
                            cur.append('"')
                            i++
                        } else {
                            inQuotes = false
                        }
                    } else {
                        inQuotes = true
                    }
                }
                else -> cur.append(c)
            }
            i++
        }
        lines.add(cur.toString())
        return lines
    }

    fun escapeCell(s: String): String {
        val needs = s.contains(',') || s.contains('"') || s.contains('\n') || s.contains('\r')
        return if (!needs) s else "\"${s.replace("\"", "\"\"")}\""
    }

    fun parseLine(line: String): List<String> {
        val out = mutableListOf<String>()
        var i = 0
        val cur = StringBuilder()
        var inQuotes = false
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> when (c) {
                    '"' -> if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                    else -> cur.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    out.add(cur.toString())
                    cur.clear()
                }
                else -> cur.append(c)
            }
            i++
        }
        out.add(cur.toString())
        return out
    }
}
