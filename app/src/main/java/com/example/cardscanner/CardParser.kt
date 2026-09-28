package com.example.cardscanner

data class CardInfo(
    var name: String = "",
    var company: String = "",
    var title: String = "",
    var phone: String = "",
    var phone2: String = "",
    var email: String = "",
    var website: String = "",
    var address: String = ""
)

/** Heuristic parser that turns raw OCR text from a business card into fields. */
object CardParser {
    private val emailRe = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val phoneRe = Regex("(\\+?\\(?\\d[\\d\\s().-]{7,}\\d)")
    private val webRe = Regex(
        "((https?://|www\\.)\\S+)|(\\b[a-z0-9-]+\\.(com|in|net|org|io|co|biz|info)\\b(/\\S*)?)",
        RegexOption.IGNORE_CASE
    )
    private val titleWords = listOf(
        "manager", "director", "ceo", "cto", "cfo", "coo", "founder", "co-founder", "owner",
        "engineer", "developer", "designer", "consultant", "executive", "president", "partner",
        "head", "lead", "officer", "proprietor", "analyst", "architect", "sales", "marketing",
        "proprietor", "specialist", "associate", "administrator"
    )
    private val companyWords = listOf(
        "pvt", "ltd", "llp", "inc", "corp", "llc", "solutions", "technologies", "technology",
        "services", "enterprises", "group", "studio", "agency", "systems", "labs", "industries",
        "traders", "associates", "consultancy", "company", "co.", "digital", "media", "infotech"
    )
    private val addressWords = listOf(
        "road", "rd.", "street", "st.", "nagar", "floor", "sector", "lane", "avenue", "plaza",
        "building", "block", "near", "opp", "india", "pin", "dist", "colony", "market", "tower"
    )

    // Whole-word matching so "Prince" doesn't match "inc" or "Headway" match "head".
    private fun wordRegex(words: List<String>) = Regex(
        words.joinToString("|", "(?<![a-z])(?:", ")(?![a-z])") { Regex.escape(it) },
        RegexOption.IGNORE_CASE
    )
    private val titleRe = wordRegex(titleWords)
    private val companyRe = wordRegex(companyWords)
    private val addressRe = wordRegex(addressWords)

    fun parse(raw: String): CardInfo {
        val card = CardInfo()
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val used = BooleanArray(lines.size)

        // Email
        lines.forEachIndexed { i, l ->
            val m = emailRe.find(l)
            if (m != null) {
                if (card.email.isEmpty()) card.email = m.value
                used[i] = true
            }
        }

        // Phones (skip email lines)
        val phones = mutableListOf<String>()
        lines.forEachIndexed { i, l ->
            if (l.contains('@')) return@forEachIndexed
            phoneRe.findAll(l).forEach { m ->
                val digits = m.value.count { it.isDigit() }
                if (digits in 8..15) phones.add(m.value.trim())
            }
            if (phoneRe.containsMatchIn(l) && l.count { it.isDigit() } >= 8 &&
                l.count { it.isLetter() } <= 12) used[i] = true
        }
        card.phone = phones.getOrElse(0) { "" }
        card.phone2 = phones.getOrElse(1) { "" }

        // Website (skip email lines)
        lines.forEachIndexed { i, l ->
            if (l.contains('@')) return@forEachIndexed
            val m = webRe.find(l)
            if (m != null) {
                if (card.website.isEmpty()) card.website = m.value
                used[i] = true
            }
        }

        // Title
        lines.forEachIndexed { i, l ->
            if (!used[i] && card.title.isEmpty() &&
                titleRe.containsMatchIn(l)) {
                card.title = l; used[i] = true
            }
        }

        // Company
        lines.forEachIndexed { i, l ->
            if (!used[i] && card.company.isEmpty() &&
                companyRe.containsMatchIn(l)) {
                card.company = l; used[i] = true
            }
        }

        // Name: first unused line that looks like a person's name (2-4 alphabetic words)
        lines.forEachIndexed { i, l ->
            if (!used[i] && card.name.isEmpty()) {
                val words = l.split(Regex("\\s+"))
                val looksLikeName = words.size in 2..4 &&
                    words.all { w -> w.all { it.isLetter() || it == '.' || it == '\'' } }
                if (looksLikeName) { card.name = l; used[i] = true }
            }
        }
        if (card.name.isEmpty()) {
            val i = used.indexOfFirst { !it }
            if (i >= 0) { card.name = lines[i]; used[i] = true }
        }

        // Address
        val addr = mutableListOf<String>()
        lines.forEachIndexed { i, l ->
            if (!used[i] && (l.any { it.isDigit() } ||
                    addressRe.containsMatchIn(l))) {
                addr.add(l); used[i] = true
            }
        }
        card.address = addr.joinToString(", ")

        // Company fallback: any remaining line
        if (card.company.isEmpty()) {
            val i = used.indexOfFirst { !it }
            if (i >= 0) { card.company = lines[i]; used[i] = true }
        }
        return card
    }
}
