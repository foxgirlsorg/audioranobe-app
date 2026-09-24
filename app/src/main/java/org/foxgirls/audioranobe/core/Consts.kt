package org.foxgirls.audioranobe.core

/** Input length limits mirroring lib/limits.ts (server is the source of truth). */
object Limits {
    const val username = 30
    const val password = 128
    const val displayName = 40
    const val bio = 10000
    const val socialUrl = 200
    const val socialsCount = 10
    const val email = 254
    const val commentBody = 5000
    const val dmBody = 5000
    const val dmImageUrl = 2000
    const val libraryNote = 2000
    const val collectionName = 100
    const val collectionDescription = 5000
    const val collectionNote = 1000
    const val narratorName = 100
    const val narratorBio = 10000
    const val narratorContact = 2000
    const val narratorSlug = 200
    const val titleName = 300
    const val titleAltName = 300
    const val titleAltNamesCount = 20
    const val titleDescription = 20000
    const val titleSlug = 200
    const val authorName = 120
    const val authorBio = 10000
    const val genreName = 40
    const val postTitle = 200
    const val postBody = 20000
    const val announcementTitle = 200
    const val announcementBody = 20000
    const val dmcaName = 200
    const val dmcaEmail = 200
    const val dmcaCountry = 100
    const val dmcaUrl = 5000
    const val dmcaDescription = 5000
}

object Support {
    const val EMAIL = "support@audioranobe.com"
    const val BOT = "https://t.me/audioranobesupportbot"
    const val CHANNEL = "https://t.me/audioranobecom"
    const val URL = BOT
}

object AppVersion {
    val VERSION: String get() = org.foxgirls.audioranobe.BuildConfig.VERSION_NAME
    const val NAME = "Aphelion"
}
