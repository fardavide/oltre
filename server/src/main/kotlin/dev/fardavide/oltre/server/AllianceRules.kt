package dev.fardavide.oltre.server

import com.ibm.icu.lang.UCharacter
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.AllianceTag
import dev.fardavide.oltre.protocol.ApiError
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

internal object AllianceRules {

    const val SEAT_CAP = 20

    private val INACTIVITY = 30.days

    fun normalise(name: AllianceName): CanonicalAllianceName = CanonicalAllianceName(
        UCharacter.foldCase(name.value.trim().replace(Regex("(?U)\\s+"), " "), true),
    )

    fun founding(affiliation: Affiliation, name: AllianceName, tag: AllianceTag, occupied: List<StoredAlliance>): FoundingVerdict =
        when (affiliation) {
            is Affiliation.Enlisted -> {
                val existing = affiliation.alliance
                if (normalise(existing.alliance.name) == normalise(name) && existing.alliance.tag == tag) {
                    FoundingVerdict.Retry(existing)
                } else {
                    FoundingVerdict.Refused(ApiError.AlreadyInAnAlliance)
                }
            }
            Affiliation.Unaffiliated -> when {
                occupied.any { normalise(it.alliance.name) == normalise(name) } -> FoundingVerdict.Refused(ApiError.AllianceNameTaken)
                occupied.any { it.alliance.tag == tag } -> FoundingVerdict.Refused(ApiError.AllianceTagTaken)
                else -> FoundingVerdict.Proceed
            }
        }

    fun canRemove(caller: AllianceRole, target: AllianceRole): Boolean = when (caller) {
        AllianceRole.FOUNDER -> when (target) {
            AllianceRole.FOUNDER -> false
            AllianceRole.ADMIN, AllianceRole.MEMBER -> true
        }
        AllianceRole.ADMIN -> when (target) {
            AllianceRole.FOUNDER, AllianceRole.ADMIN -> false
            AllianceRole.MEMBER -> true
        }
        AllianceRole.MEMBER -> false
    }

    fun isActive(lastSyncedAt: Instant, now: Instant): Boolean = lastSyncedAt >= now - INACTIVITY
}

@JvmInline
internal value class CanonicalAllianceName(val value: String)

internal sealed interface FoundingVerdict {

    data object Proceed : FoundingVerdict

    data class Retry(val alliance: StoredAlliance) : FoundingVerdict

    data class Refused(val error: ApiError) : FoundingVerdict
}
