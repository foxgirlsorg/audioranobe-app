package org.foxgirls.audioranobe.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// Port of lib/types.ts. Strings are used for the union types; label maps live in Labels.

object Labels {
    val releaseStatus = linkedMapOf(
        "ongoing" to "Продолжается", "completed" to "Завершён", "abandoned" to "Заброшен", "frozen" to "Заморожен",
    )
    val narrationStatus = linkedMapOf(
        "ongoing" to "Продолжается", "completed" to "Завершена", "abandoned" to "Заброшена", "frozen" to "Заморожена",
    )
    val statusValues = listOf("ongoing", "completed", "abandoned", "frozen")
    val country = linkedMapOf("china" to "Китай", "korea" to "Корея", "japan" to "Япония", "other" to "Другое")
    val countryValues = listOf("china", "korea", "japan", "other")
    val libraryStatus = linkedMapOf(
        "planning" to "В планах", "in_progress" to "Слушаю", "completed" to "Прослушано", "dropped" to "Брошено",
    )
    val libraryValues = listOf("planning", "in_progress", "completed", "dropped")
}

@Serializable
data class Badge(val id: Int, val slug: String = "", val name: String = "", val svg: String = "")

@Serializable
data class UserBrief(
    val id: Int,
    val username: String = "",
    val display_name: String = "",
    val avatar_url: String? = null,
    val avatar_thumb_url: String? = null,
    val role: String = "user",
    val role_name: String = "",
    val badges: List<Badge> = emptyList(),
    val is_banned: Boolean = false,
    val presence: String = "offline",
    val last_seen_at: String? = null,
) {
    val shownName get() = display_name.ifBlank { username }
}

@Serializable
data class NotificationPrefs(
    val new_chapter: Boolean = true,
    val narrator_release: Boolean = true,
    val narrator_post: Boolean = true,
    val comment_reply: Boolean = true,
    val narrator_comment: Boolean = true,
    val mention: Boolean = true,
    val friend_request: Boolean = true,
    val request_reviewed: Boolean = true,
    val entity_modified: Boolean = true,
    val entity_deleted: Boolean = true,
    val narration_ready: Boolean = true,
    val dm: Boolean = true,
)

@Serializable
data class UserPublic(
    val id: Int,
    val username: String = "",
    val display_name: String = "",
    val bio: String = "",
    val socials: List<String> = emptyList(),
    val avatar_url: String? = null,
    val avatar_thumb_url: String? = null,
    val cover_url: String? = null,
    val role: String = "user",
    val role_name: String = "",
    val badges: List<Badge> = emptyList(),
    val is_banned: Boolean = false,
    val created_at: String = "",
    val presence: String = "offline",
    val last_seen_at: String? = null,
) {
    val shownName get() = display_name.ifBlank { username }
}

@Serializable
data class ProviderInfo(val id: String, val name: String = "", val icon_svg: String = "")

@Serializable
data class Identity(
    val provider: String,
    val email: String? = null,
    val display_name: String = "",
    val avatar_url: String? = null,
    val created_at: String = "",
)

@Serializable
data class ContentPrefs(val hide_nsfw: Boolean = true, val hidden_genres: List<Int> = emptyList())

/** Viewer + Me merged: the X-Me header only ships the slim fields, GET /me fills the rest. */
@Serializable
data class Me(
    val id: Int,
    val username: String = "",
    val display_name: String = "",
    val avatar_url: String? = null,
    val avatar_thumb_url: String? = null,
    val cover_url: String? = null,
    val role: String = "user",
    val role_name: String = "",
    val permissions: List<String> = emptyList(),
    val badges: List<Badge> = emptyList(),
    val created_at: String = "",
    val email: String? = null,
    val has_password: Boolean = false,
    val needs_setup: Boolean = false,
    val is_banned: Boolean = false,
    val ban_reason: String? = null,
    val skip_moderation: Boolean = false,
    val accepted_cookies: Boolean = false,
    val email_verified: Boolean = false,
    val dm_privacy: String = "all",
    // heavy fields (GET /me only)
    val bio: String = "",
    val socials: List<String> = emptyList(),
    val identities: List<Identity> = emptyList(),
    val notification_prefs: NotificationPrefs? = null,
    val content_prefs: ContentPrefs? = null,
    val narrators_count: Int = 0,
    val email_verification: Boolean? = null,
    val auth_providers: List<ProviderInfo>? = null,
    val totp_enabled: Boolean = false,
    val blur_unlistened_illustrations: Boolean = false,
    val auto_add_to_library: Boolean = false,
) {
    val shownName get() = display_name.ifBlank { username }
    fun can(perm: String) = permissions.contains("*") || permissions.contains(perm)
    val isMod get() = can("mod.panel")
    fun asBrief() = UserBrief(id, username, display_name, avatar_url, avatar_thumb_url, role, role_name, badges, is_banned, "online", null)
}

@Serializable
data class ChatReplyPreview(val id: Int, val author: String = "", val excerpt: String = "", val is_deleted: Boolean = false, val mine: Boolean = false)

@Serializable
data class ChatMessage(
    val id: Int,
    val conversation_id: Int = 0,
    val sender_id: Int? = null,
    val mine: Boolean = false,
    val body: String = "",
    val image_url: String = "",
    val format: String = "plain",
    val can_toggle_format: Boolean = false,
    val plain_text: Boolean = false,
    val is_deleted: Boolean = false,
    val edited: Boolean = false,
    val reply_to: ChatReplyPreview? = null,
    val created_at: String = "",
)

@Serializable
data class ChatLastMessage(val body: String = "", val image_url: String = "", val mine: Boolean = false, val is_deleted: Boolean = false, val created_at: String = "")

@Serializable
data class ChatConversation(
    val user: UserBrief,
    val last_message: ChatLastMessage? = null,
    val last_message_at: String? = null,
    val unread: Int = 0,
)

@Serializable
data class ChatThread(
    val user: UserBrief,
    val me_id: Int = 0,
    val can_send: Boolean = true,
    val block_reason: String = "",
    val their_last_read_id: Int = 0,
    val messages: List<ChatMessage> = emptyList(),
    val has_more: Boolean = false,
)

@Serializable
data class Notification(
    val id: Int,
    val type: String = "system",
    val body: String = "",
    val link: String = "",
    val is_read: Boolean = false,
    val created_at: String = "",
)

@Serializable
data class Genre(val id: Int, val slug: String = "", val name: String = "", val titles_count: Int = 0, val is_sensitive: Boolean = false)

@Serializable
data class GenreTag(val id: Int, val slug: String = "", val name: String = "", val is_sensitive: Boolean = false)

@Serializable
data class AuthorBrief(val id: Int, val slug: String = "", val name: String = "")

@Serializable
data class TitleCard(
    val id: Int,
    val slug: String = "",
    val name: String = "",
    val author: AuthorBrief? = null,
    val year: Int? = null,
    val cover_url: String? = null,
    val cover_thumb_url: String? = null,
    val release_status: String = "ongoing",
    val country: String = "other",
    val avg_rating: Double? = null,
    val rating_count: Int = 0,
    val listens: Long = 0,
    val updated_at: String? = null,
    val chapters_count: Int = 0,
    val genres: List<GenreTag> = emptyList(),
    val age_rating: String? = null,
    val my_favorite: Boolean = false,
    val is_deleted: Boolean = false,
    val is_hidden: Boolean = false,
    val is_nsfw: Boolean = false,
    val is_ai: Boolean = false,
    val has_sensitive_genre: Boolean = false,
    val is_restricted: Boolean = false,
) {
    val thumb get() = cover_thumb_url ?: cover_url
}

@Serializable
data class NarratorRef(val id: Int, val slug: String = "", val name: String = "")

@Serializable
data class ChapterRow(
    val id: Int,
    val volume_id: Int = 0,
    val number: Double = 0.0,
    val number_end: Double? = null,
    val name: String = "",
    val duration_seconds: Double = 0.0,
    val audio_status: String = "none",
    val mod_status: String = "approved",
    val my_position: Double? = null,
    val narrators: List<NarratorRef> = emptyList(),
    val is_deleted: Boolean = false,
    // Only on the editor listing:
    val is_hidden: Boolean = false,
    val audio_url: String? = null,
    val position: Int? = null,
) {
    val playable get() = audio_status == "ready"
}

@Serializable
data class Volume(
    val id: Int,
    val number: Int = 0,
    val name: String = "",
    val cover_url: String? = null,
    val cover_thumb_url: String? = null,
    val chapters: List<ChapterRow> = emptyList(),
) {
    val liveChapters get() = chapters.filter { !it.is_deleted }
}

@Serializable
data class TitleVersion(val id: Int, val name: String = "", val sort: Int = 0)

@Serializable
data class NarratorCard(
    val id: Int,
    val slug: String = "",
    val name: String = "",
    val avatar_url: String? = null,
    val avatar_thumb_url: String? = null,
    val titles_count: Int = 0,
    val is_ai: Boolean = false,
    val is_verified: Boolean = false,
    val is_deleted: Boolean = false,
    // Present on TitleFull.narrators
    val narration_status: String? = null,
    // Present on /panel/narrators
    val mod_status: String? = null,
)

@Serializable
data class NarratorFull(
    val id: Int,
    val slug: String = "",
    val name: String = "",
    val avatar_url: String? = null,
    val avatar_thumb_url: String? = null,
    val titles_count: Int = 0,
    val is_ai: Boolean = false,
    val is_verified: Boolean = false,
    val is_deleted: Boolean = false,
    val bio: String = "",
    val socials: List<String> = emptyList(),
    val cover_url: String? = null,
    val cover_thumb_url: String? = null,
    val mod_status: String = "approved",
    val created_at: String = "",
    val titles: List<TitleCard> = emptyList(),
    val subscribers_count: Int = 0,
    val seconds_narrated: Long = 0,
    val my_subscription: Boolean = false,
    val is_self: Boolean = false,
    val admin_contact: String? = null,
    val can_edit: Boolean = false,
    val my_role: String? = null,
    val comments: Paginated<Comment>? = null,
    val members: List<NarratorMember>? = null,
    val owner: UserBrief? = null,
)

@Serializable
data class NarratorMember(val user: UserBrief, val role: String = "editor", val created_at: String = "")

@Serializable
data class IllustrationChapter(val id: Int, val number: Double = 0.0, val number_end: Double? = null, val name: String = "", val volume_number: Int = 0)

@Serializable
data class Illustration(
    val id: Int,
    val title_id: Int = 0,
    val chapter_id: Int? = null,
    val chapter: IllustrationChapter? = null,
    val url: String = "",
    val thumb_url: String = "",
    val width: Int = 1,
    val height: Int = 1,
    val caption: String = "",
    val position: Int = 0,
    val blurred: Boolean = false,
)

@Serializable
data class TitleInfoBanner(val enabled: Boolean = false, val title: String = "", val text: String = "", val url: String = "")

@Serializable
data class LibraryEntryBrief(val status: String = "planning", val note: String = "")

@Serializable
data class TitleFull(
    val id: Int,
    val slug: String = "",
    val name: String = "",
    val author: AuthorBrief? = null,
    val year: Int? = null,
    val cover_url: String? = null,
    val cover_thumb_url: String? = null,
    val release_status: String = "ongoing",
    val country: String = "other",
    val avg_rating: Double? = null,
    val rating_count: Int = 0,
    val listens: Long = 0,
    val chapters_count: Int = 0,
    val genres: List<GenreTag> = emptyList(),
    val age_rating: String? = null,
    val my_favorite: Boolean = false,
    val is_deleted: Boolean = false,
    val is_hidden: Boolean = false,
    val is_nsfw: Boolean = false,
    val is_ai: Boolean = false,
    val has_sensitive_genre: Boolean = false,
    val is_restricted: Boolean = false,
    val alt_names: List<String> = emptyList(),
    val description: String = "",
    val bg_url: String? = null,
    val views_count: Long = 0,
    val translator: String = "",
    val mod_status: String = "approved",
    val created_at: String = "",
    val updated_at: String = "",
    val favorites_count: Int = 0,
    val narrators: List<NarratorCard> = emptyList(),
    val my_rating: Int? = null,
    val my_library: LibraryEntryBrief? = null,
    @Serializable(with = IntMap::class) val rating_distribution: Map<String, Int> = emptyMap(),
    val similar: List<TitleCard> = emptyList(),
    val volumes: List<Volume> = emptyList(),
    val source_url: String? = null,
    val can_edit: Boolean = false,
    val nsfw_restricted: Boolean = false,
    val narration_pending: Boolean = false,
    val is_imported: Boolean = false,
    val info_banner: TitleInfoBanner? = null,
    val volume_label: String = "Том",
    val volume_label_plural: String = "Тома",
    val illustrations: List<Illustration> = emptyList(),
    val comment_subscribed: Boolean = false,
    val comments: Paginated<Comment>? = null,
    val version_name: String = "Основная",
    val versions: List<TitleVersion> = emptyList(),
    val selected_version_id: Int = 0,
    @Serializable(with = ChapterRowsMap::class) val alt_chapters: Map<String, List<ChapterRow>> = emptyMap(),
    val narrator_ids: List<Int>? = null,
) {
    fun asCard() = TitleCard(id, slug, name, author, year, cover_url, cover_thumb_url, release_status, country, avg_rating, rating_count, listens, updated_at, chapters_count, genres, age_rating, my_favorite, is_deleted, is_hidden, is_nsfw, is_ai, has_sensitive_genre, is_restricted)
}

@Serializable
data class ChapterPlayVolume(val id: Int, val number: Int = 0, val name: String = "", val cover_url: String? = null, val cover_thumb_url: String? = null)

@Serializable
data class ChapterPlayTitle(val id: Int, val slug: String = "", val name: String = "", val cover_url: String? = null, val volume_label: String = "Том")

@Serializable
data class ChapterPlay(
    val id: Int,
    val number: Double = 0.0,
    val number_end: Double? = null,
    val name: String = "",
    val duration_seconds: Double = 0.0,
    val audio_url: String = "",
    val my_position: Double? = null,
    val volume: ChapterPlayVolume,
    val title: ChapterPlayTitle,
    val prev_id: Int? = null,
    val next_id: Int? = null,
    val narrator: NarratorRef? = null,
    val illustrations: List<Illustration> = emptyList(),
) {
    val coverUrl get() = volume.cover_url ?: title.cover_url
}

@Serializable
data class Comment(
    val id: Int,
    val user: UserBrief? = null,
    val narrator: CommentNarrator? = null,
    val target_type: String = "title",
    val target_id: Int = 0,
    val parent_id: Int? = null,
    val body: String = "",
    val score: Int = 0,
    val my_vote: Int = 0,
    val is_deleted: Boolean = false,
    val edited_by_staff: Boolean = false,
    val created_at: String = "",
    val updated_at: String? = null,
    // UserComment / ModComment extras
    val target: CommentTarget? = null,
    val mod_reviewed: Boolean = false,
)

@Serializable
data class CommentNarrator(val id: Int, val slug: String = "", val name: String = "", val avatar_url: String? = null, val is_verified: Boolean = false)

@Serializable
data class CommentTarget(val type: String = "", val id: Int = 0, val name: String = "", val link: String = "")

@Serializable
data class LibraryEntry(
    val title: TitleCard,
    val status: String = "planning",
    val note: String = "",
    val rating: Int? = null,
    val is_favorite: Boolean = false,
    val updated_at: String = "",
)

@Serializable
data class ContinueTitle(val id: Int, val slug: String = "", val name: String = "", val cover_url: String? = null)

@Serializable
data class ContinueChapter(val id: Int, val name: String = "", val number: Double = 0.0, val duration_seconds: Double = 0.0)

@Serializable
data class ContinueItem(val title: ContinueTitle, val chapter: ContinueChapter, val position_seconds: Double = 0.0, val updated_at: String = "")

typealias HistoryItem = ContinueItem

@Serializable
data class CollectionCard(
    val id: Int,
    val name: String = "",
    val description: String = "",
    val is_public: Boolean = true,
    val user: UserBrief,
    val items_count: Int = 0,
    val likes_count: Int = 0,
    val cover_urls: List<String> = emptyList(),
    val created_at: String = "",
    val updated_at: String = "",
)

@Serializable
data class CollectionItem(val position: Int = 0, val note: String = "", val title: TitleCard)

@Serializable
data class CollectionFull(
    val id: Int,
    val name: String = "",
    val description: String = "",
    val is_public: Boolean = true,
    val user: UserBrief,
    val items_count: Int = 0,
    val likes_count: Int = 0,
    val cover_urls: List<String> = emptyList(),
    val created_at: String = "",
    val updated_at: String = "",
    val my_like: Boolean = false,
    val items: List<CollectionItem> = emptyList(),
    val can_edit: Boolean = false,
)

@Serializable
data class AnnouncementAuthor(val id: Int, val username: String = "")

@Serializable
data class Announcement(
    val id: Int,
    val slug: String = "",
    val title: String = "",
    val body: String = "",
    val author: AnnouncementAuthor? = null,
    val is_published: Boolean = true,
    val is_hidden: Boolean = false,
    val created_at: String = "",
    val comments: Paginated<Comment>? = null,
    val comments_count: Int? = null,
)

@Serializable
data class PostNarrator(val id: Int, val slug: String = "", val name: String = "", val avatar_url: String? = null)

@Serializable
data class NarratorPost(
    val id: Int,
    val narrator: PostNarrator? = null,
    val title: String = "",
    val body: String = "",
    val is_hidden: Boolean = false,
    val created_at: String = "",
    val updated_at: String = "",
    val can_edit: Boolean = false,
    val comments_count: Int? = null,
    val comments: Paginated<Comment>? = null,
)

@Serializable
data class ModRequest(
    val id: Int,
    val entity_type: String = "",
    val entity_id: Int? = null,
    val action: String = "",
    val payload: JsonObject = JsonObject(emptyMap()),
    val status: String = "pending",
    val review_note: String = "",
    val submitted_by: UserBrief? = null,
    val entity: JsonObject? = null,
    val transfer: JsonObject? = null,
    val created_at: String = "",
    val reviewed_at: String? = null,
    val retry_count: Int = 0,
    val can_retry: Boolean = false,
)

@Serializable
data class UserSearchHit(val id: Int, val username: String = "", val display_name: String = "", val avatar_url: String? = null, val avatar_thumb_url: String? = null)

@Serializable
data class Job(
    val id: Int,
    val chapter_id: Int = 0,
    val chapter_name: String = "",
    val title_name: String = "",
    val status: String = "queued",
    val attempts: Int = 0,
    val error: String = "",
    val created_at: String = "",
    val finished_at: String? = null,
)

@Serializable
data class JobsPage(val items: List<Job> = emptyList(), val page: Int = 1, val per_page: Int = 20, val total: Int = 0, val active: Int = 0)

@Serializable
data class Paginated<T>(
    val items: List<T> = emptyList(),
    val page: Int = 1,
    val per_page: Int = 20,
    val total: Int = 0,
    // Optional extras some endpoints attach:
    val external: List<RequestableTitle>? = null,
) {
    val pages get() = maxOf(1, (total + maxOf(1, per_page) - 1) / maxOf(1, per_page))
}

@Serializable
data class HomeCatalog(val items: List<TitleCard> = emptyList(), val total: Int = 0)

@Serializable
data class HomeData(
    val announcements: List<Announcement> = emptyList(),
    @SerialName("continue") val continueItems: List<ContinueItem> = emptyList(),
    val new_titles: List<TitleCard> = emptyList(),
    val banners: List<Banner> = emptyList(),
    val catalog: HomeCatalog = HomeCatalog(),
)

@Serializable
data class RequestableTitle(val ref: String, val name: String = "", val cover_url: String? = null, val year: Int? = null, val status: String = "")

@Serializable
data class SuggestTitle(val id: Int, val slug: String = "", val name: String = "", val author: AuthorBrief? = null, val cover_url: String? = null, val cover_thumb_url: String? = null)

@Serializable
data class SuggestNarrator(val id: Int, val slug: String = "", val name: String = "", val avatar_url: String? = null)

@Serializable
data class SuggestAuthor(val id: Int, val slug: String = "", val name: String = "", val titles_count: Int = 0)

@Serializable
data class SuggestCollection(val id: Int, val name: String = "", val items_count: Int = 0)

@Serializable
data class SearchSuggest(
    val titles: List<SuggestTitle> = emptyList(),
    val narrators: List<SuggestNarrator> = emptyList(),
    val authors: List<SuggestAuthor> = emptyList(),
    val collections: List<SuggestCollection> = emptyList(),
    val external: List<RequestableTitle> = emptyList(),
)

@Serializable
data class OrderStatus(val authenticated: Boolean = false, val enabled: Boolean? = null, val can_order: Boolean = false, val next_at: String? = null)

@Serializable
data class Banner(val id: Int, val image_url: String = "", val url: String = "", val is_enabled: Boolean? = null, val is_public: Boolean? = null, val sort_order: Int? = null)

@Serializable
data class Friendship(val status: String = "none", val friends_count: Int = 0)

@Serializable
data class FriendRequestItem(val user: UserBrief, val created_at: String = "")

@Serializable
data class FriendsData(val friends: List<UserBrief> = emptyList(), val incoming: List<FriendRequestItem> = emptyList(), val outgoing: List<FriendRequestItem> = emptyList())

@Serializable
data class UserStats(
    val planning: Int = 0,
    val in_progress: Int = 0,
    val completed: Int = 0,
    val dropped: Int = 0,
    val comments: Int = 0,
    val favorites: Int = 0,
    val seconds_listened: Long = 0,
)

@Serializable
data class ListeningHeatmapData(val year: Int = 0, val years: List<Int> = emptyList(), @Serializable(with = IntMap::class) val days: Map<String, Int> = emptyMap())

@Serializable
data class ScoreRow(val score: Int = 0, val titles: Int = 0, val hours: Double = 0.0)

@Serializable
data class CountryRow(val country: String = "", val count: Int = 0)

@Serializable
data class ScoreStats(val scores: List<ScoreRow> = emptyList(), val countries: List<CountryRow> = emptyList())

@Serializable
data class UserProfile(
    val user: UserPublic,
    val stats: UserStats = UserStats(),
    val score_stats: ScoreStats = ScoreStats(),
    val activity: ListeningHeatmapData = ListeningHeatmapData(),
    val friendship: Friendship = Friendship(),
    val can_message: Boolean = false,
)

@Serializable
data class NarratorStatsTitle(val id: Int, val slug: String = "", val name: String = "", val listens: Long = 0, val avg_rating: Double? = null, val rating_count: Int = 0, val favorites_count: Int = 0, val chapters_count: Int = 0)

@Serializable
data class NarratorStatsTotals(val listens: Long = 0, val favorites: Int = 0, val ratings: Int = 0)

@Serializable
data class NarratorStats(val subscribers_count: Int = 0, val totals: NarratorStatsTotals = NarratorStatsTotals(), val titles: List<NarratorStatsTitle> = emptyList())

@Serializable
data class Author(val id: Int, val name: String = "", val slug: String = "", val titles_count: Int = 0)

@Serializable
data class AuthorFull(
    val id: Int,
    val name: String = "",
    val slug: String = "",
    val titles_count: Int = 0,
    val bio: String = "",
    val links: List<String> = emptyList(),
    val titles: List<TitleCard> = emptyList(),
    val created_at: String = "",
    val can_edit: Boolean = false,
)

@Serializable
data class RecapTitle(val slug: String = "", val name: String = "", val cover_url: String? = null, val seconds: Long = 0)

@Serializable
data class RecapNarrator(val id: Int, val slug: String = "", val name: String = "", val seconds: Long = 0)

@Serializable
data class RecapDesign(val html: String = "", val css: String = "")

@Serializable
data class Recap(
    val scope: String = "month",
    val period_label: String = "",
    val username: String = "",
    val display_name: String = "",
    val total_seconds: Long = 0,
    val files_count: Int = 0,
    val titles_count: Int = 0,
    val books_finished: Int = 0,
    val active_days: Int = 0,
    val top_titles: List<RecapTitle> = emptyList(),
    val top_narrators: List<RecapNarrator> = emptyList(),
    val design: RecapDesign? = null,
    val year: Int? = null,
)

@Serializable
data class DonateGoal(val enabled: Boolean = false, val title: String = "", val period: String = "once", val target_cents: Long = 0, val raised_cents: Long = 0, val pct: Double = 0.0)

@Serializable
data class DonateConfig(val kofi: String = "", val boosty: String = "", val badge_min: Int = 0, val goal: DonateGoal = DonateGoal())

@Serializable
data class RecentDonation(val name: String = "", val service: String = "", val amount: Double = 0.0, val currency: String = "", val created_at: JsonElement? = null)

@Serializable
data class CaptchaConfig(val enabled: Boolean = false, val site_key: String = "", val script_url: String = "", val widget_var: String = "")

@Serializable
data class AppConfig(
    val email_verification: Boolean = false,
    val auth_providers: List<ProviderInfo> = emptyList(),
    val captcha: CaptchaConfig = CaptchaConfig(),
)

@Serializable
data class Badges(val messages: Int = 0, val notifications: Int = 0, val friend_requests: Int = 0)

@Serializable
data class UploadSession(val id: Int, val filename: String = "", val size: Long = 0, val received: Long = 0, val complete: Boolean = false, val chunk_size: Long = 0)

@Serializable
data class LegalDoc(val title: String = "", val body: String = "", val updated_at: String? = null)
