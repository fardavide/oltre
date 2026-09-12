package dev.fardavide.oltre.server

import dev.fardavide.oltre.protocol.AllianceId
import java.util.Arrays

internal data class AllianceSearchPosition(
    val experience: Long,
    val name: CanonicalAllianceName,
    val id: AllianceId,
) : Comparable<AllianceSearchPosition> {

    override fun compareTo(other: AllianceSearchPosition): Int {
        val byExperience = other.experience.compareTo(experience)
        if (byExperience != 0) return byExperience
        val byName = Arrays.compareUnsigned(name.value.toByteArray(Charsets.UTF_8), other.name.value.toByteArray(Charsets.UTF_8))
        if (byName != 0) return byName
        return Arrays.compareUnsigned(id.value.toByteArray(Charsets.UTF_8), other.id.value.toByteArray(Charsets.UTF_8))
    }

    companion object {

        fun from(alliance: StoredAlliance): AllianceSearchPosition = AllianceSearchPosition(
            alliance.experience, AllianceRules.normalise(alliance.alliance.name), alliance.alliance.id,
        )
    }
}
