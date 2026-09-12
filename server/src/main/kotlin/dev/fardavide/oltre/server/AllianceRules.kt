package dev.fardavide.oltre.server

import com.ibm.icu.lang.UCharacter
import dev.fardavide.oltre.protocol.AllianceName
import dev.fardavide.oltre.protocol.AllianceId
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.JoinRequestId
import dev.fardavide.oltre.protocol.JoinDecision
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
            is Affiliation.Petitioning -> FoundingVerdict.Refused(ApiError.AlreadyInAnAlliance)
            is Affiliation.Enlisted -> {
                val existing = affiliation.alliance
                if (normalise(existing.alliance.name) == normalise(name) && existing.alliance.tag == tag) {
                    FoundingVerdict.Retry(existing, affiliation.seat.role)
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

    fun petitioning(affiliation: Affiliation, target: StoredAlliance): PetitionVerdict = when (affiliation) {
        Affiliation.Unaffiliated -> PetitionVerdict.Proceed
        is Affiliation.Enlisted -> PetitionVerdict.Refused(ApiError.AlreadyInAnAlliance)
        is Affiliation.Petitioning -> if (affiliation.petition.alliance == target.alliance.id) {
            PetitionVerdict.Retry(affiliation)
        } else {
            PetitionVerdict.Refused(ApiError.AlreadyInAnAlliance)
        }
    }

    fun isActive(lastSyncedAt: Instant, now: Instant): Boolean = lastSyncedAt >= now - INACTIVITY

    fun detaching(affiliation: Affiliation, alliance: AllianceId): DetachVerdict = when (affiliation) {
        is Affiliation.Petitioning -> if (affiliation.petition.alliance == alliance) DetachVerdict.Withdraw(affiliation.petition)
            else DetachVerdict.Refused(ApiError.AllianceRoleTooLow)
        is Affiliation.Enlisted -> if (affiliation.seat.alliance != alliance) DetachVerdict.Refused(ApiError.AllianceRoleTooLow) else when (affiliation.seat.role) {
            AllianceRole.FOUNDER -> DetachVerdict.Refused(ApiError.AllianceRoleTooLow)
            AllianceRole.ADMIN, AllianceRole.MEMBER -> DetachVerdict.Leave(affiliation.seat)
        }
        Affiliation.Unaffiliated -> DetachVerdict.Refused(ApiError.NotInAnAlliance)
    }

    fun approval(caller: Affiliation, alliance: StoredAlliance, requests: List<Petition>, request: JoinRequestId, decision: JoinDecision, seated: Set<PlayerId>): ApprovalVerdict {
        when (caller) {
            Affiliation.Unaffiliated, is Affiliation.Petitioning -> return ApprovalVerdict.Refused(ApiError.NotInAnAlliance)
            is Affiliation.Enlisted -> {
                if (caller.seat.alliance != alliance.alliance.id) return ApprovalVerdict.Refused(ApiError.AllianceRoleTooLow)
                when (caller.seat.role) {
                    AllianceRole.MEMBER -> return ApprovalVerdict.Refused(ApiError.AllianceRoleTooLow)
                    AllianceRole.ADMIN, AllianceRole.FOUNDER -> Unit
                }
            }
        }
        val petition = requests.firstOrNull { it.id == request && it.alliance == alliance.alliance.id }
            ?: return ApprovalVerdict.Refused(ApiError.NoSuchAlliance)
        if (decision == JoinDecision.ADMITTED && petition.player in seated) return ApprovalVerdict.Refused(ApiError.AlreadyInAnAlliance)
        return when (decision) {
            JoinDecision.DECLINED -> ApprovalVerdict.Decline(petition)
            JoinDecision.ADMITTED -> if (alliance.alliance.seats.taken >= alliance.alliance.seats.cap) {
                ApprovalVerdict.Refused(ApiError.AllianceFull)
            } else {
                ApprovalVerdict.Admit(petition)
            }
        }
    }

    fun settingRole(caller: Affiliation, alliance: StoredAlliance, seats: List<Seat>, member: AllianceMemberId, role: AllianceRole): RoleVerdict {
        when (val authority = founderAuthority(caller, alliance.alliance.id)) {
            AllianceAuthority.Granted -> Unit
            is AllianceAuthority.Refused -> return RoleVerdict.Refused(authority.error)
        }
        val target = seats.firstOrNull { it.id == member && it.alliance == alliance.alliance.id }
            ?: return RoleVerdict.Refused(ApiError.NoSuchAlliance)
        if (target.role == AllianceRole.FOUNDER && role != AllianceRole.FOUNDER) return RoleVerdict.Refused(ApiError.AllianceRoleTooLow)
        return RoleVerdict.Change(when (role) {
            AllianceRole.FOUNDER -> transferLeadership(seats, target.id)
            AllianceRole.ADMIN, AllianceRole.MEMBER -> seats.map { if (it.id == target.id) it.copy(role = role) else it }
        })
    }

    fun removing(caller: Affiliation, alliance: StoredAlliance, seats: List<Seat>, member: AllianceMemberId): RemovalVerdict {
        val enlisted = when (caller) {
            Affiliation.Unaffiliated, is Affiliation.Petitioning -> return RemovalVerdict.Refused(ApiError.NotInAnAlliance)
            is Affiliation.Enlisted -> caller
        }
        if (enlisted.seat.alliance != alliance.alliance.id) return RemovalVerdict.Refused(ApiError.AllianceRoleTooLow)
        val target = seats.firstOrNull { it.id == member && it.alliance == alliance.alliance.id }
            ?: return RemovalVerdict.Refused(ApiError.NoSuchAlliance)
        return if (canRemove(enlisted.seat.role, target.role)) RemovalVerdict.Remove(target)
        else RemovalVerdict.Refused(ApiError.AllianceRoleTooLow)
    }

    fun renaming(caller: Affiliation, alliance: StoredAlliance, name: AllianceName, tag: AllianceTag, occupied: List<StoredAlliance>): AllianceAuthority {
        when (val authority = founderAuthority(caller, alliance.alliance.id)) {
            AllianceAuthority.Granted -> Unit
            is AllianceAuthority.Refused -> return authority
        }
        val others = occupied.filter { it.alliance.id != alliance.alliance.id }
        return when {
            others.any { normalise(it.alliance.name) == normalise(name) } -> AllianceAuthority.Refused(ApiError.AllianceNameTaken)
            others.any { it.alliance.tag == tag } -> AllianceAuthority.Refused(ApiError.AllianceTagTaken)
            else -> AllianceAuthority.Granted
        }
    }

    fun founderAuthority(caller: Affiliation, alliance: AllianceId): AllianceAuthority = when (caller) {
        Affiliation.Unaffiliated, is Affiliation.Petitioning -> AllianceAuthority.Refused(ApiError.NotInAnAlliance)
        is Affiliation.Enlisted -> if (caller.seat.alliance != alliance) AllianceAuthority.Refused(ApiError.AllianceRoleTooLow) else when (caller.seat.role) {
            AllianceRole.FOUNDER -> AllianceAuthority.Granted
            AllianceRole.ADMIN, AllianceRole.MEMBER -> AllianceAuthority.Refused(ApiError.AllianceRoleTooLow)
        }
    }

    fun successor(seats: List<Seat>, active: Set<PlayerId>): SuccessionChoice {
        if (seats.any { it.role == AllianceRole.FOUNDER && it.player in active }) return SuccessionChoice.Unchanged
        val admin = seats.filter { it.role == AllianceRole.ADMIN && it.player in active }
            .sortedWith(compareBy<Seat> { it.joinedAt }.thenBy { it.player.value })
            .firstOrNull()
        if (admin != null) return SuccessionChoice.Promote(admin)
        return seats.filter { it.role == AllianceRole.MEMBER && it.player in active }
            .sortedWith(compareByDescending<Seat> { it.contributed }.thenBy { it.player.value })
            .firstOrNull()?.let { SuccessionChoice.Promote(it) } ?: SuccessionChoice.Unchanged
    }

    fun transferLeadership(seats: List<Seat>, successor: AllianceMemberId): List<Seat> = seats.map { seat ->
        seat.copy(role = if (seat.id == successor) AllianceRole.FOUNDER else when (seat.role) {
            AllianceRole.FOUNDER, AllianceRole.ADMIN -> AllianceRole.ADMIN
            AllianceRole.MEMBER -> AllianceRole.MEMBER
        })
    }
}

@JvmInline
internal value class CanonicalAllianceName(val value: String)

internal sealed interface FoundingVerdict {

    data object Proceed : FoundingVerdict

    data class Retry(val alliance: StoredAlliance, val role: AllianceRole) : FoundingVerdict

    data class Refused(val error: ApiError) : FoundingVerdict
}

internal sealed interface SuccessionChoice {

    data object Unchanged : SuccessionChoice

    data class Promote(val seat: Seat) : SuccessionChoice
}

internal sealed interface PetitionVerdict {

    data object Proceed : PetitionVerdict

    data class Retry(val affiliation: Affiliation.Petitioning) : PetitionVerdict

    data class Refused(val error: ApiError) : PetitionVerdict
}

internal sealed interface DetachVerdict {

    data class Withdraw(val petition: Petition) : DetachVerdict

    data class Leave(val seat: Seat) : DetachVerdict

    data class Refused(val error: ApiError) : DetachVerdict
}

internal sealed interface ApprovalVerdict {

    data class Admit(val petition: Petition) : ApprovalVerdict

    data class Decline(val petition: Petition) : ApprovalVerdict

    data class Refused(val error: ApiError) : ApprovalVerdict
}

internal sealed interface RoleVerdict {

    data class Change(val seats: List<Seat>) : RoleVerdict

    data class Refused(val error: ApiError) : RoleVerdict
}

internal sealed interface RemovalVerdict {

    data class Remove(val seat: Seat) : RemovalVerdict

    data class Refused(val error: ApiError) : RemovalVerdict
}

internal sealed interface AllianceAuthority {

    data object Granted : AllianceAuthority

    data class Refused(val error: ApiError) : AllianceAuthority
}
