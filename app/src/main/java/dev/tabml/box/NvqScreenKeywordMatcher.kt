package dev.tabml.box

/**
 * Heuristic matcher for on-screen text that may relate to UK construction
 * competence / NVQ-style workflows. Deliberately imperfect — prompts the user
 * to opt in to observation, not automated grading.
 */
object NvqScreenKeywordMatcher {

    private val phrases: List<String> = listOf(
        "nvq",
        "svq",
        "national vocational",
        "work-based qualification",
        "construction nvq",
        "cskills",
        "city & guilds",
        "city and guilds",
        "bricklaying",
        "site carpentry",
        "groundworker",
        "ground worker",
        "steelfixing",
        "steel fixing",
        "masonry",
        "plastering",
        "dry lining",
        "demolition",
        "occupational competence",
        "workplace observation",
        "site observation",
        "witness testimony",
        "professional discussion",
        "e-portfolio",
        "eportfolio",
        "portfolio",
        "internal quality assurance",
        "iqa",
        "assessor visit",
        "assessment plan",
        "assessment criteria",
        "learning outcome",
        "mapping evidence",
        "osat",
        "experienced worker",
        "epa",
        "end-point assessment",
        "cscs",
        "skills card",
        "trades nvq",
    )

    fun matches(raw: String): Boolean {
        if (raw.length < 4) return false
        val t = raw.lowercase()
        for (p in phrases) {
            if (t.contains(p)) return true
        }
        return false
    }
}
