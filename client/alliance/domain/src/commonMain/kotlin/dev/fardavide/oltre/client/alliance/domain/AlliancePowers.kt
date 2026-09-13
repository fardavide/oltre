package dev.fardavide.oltre.client.alliance.domain

import dev.fardavide.oltre.protocol.AllianceRole

fun AllianceRole.canAnswerJoinRequests(): Boolean = when (this) {
    AllianceRole.FOUNDER, AllianceRole.ADMIN -> true
    AllianceRole.MEMBER -> false
}

fun AllianceRole.canDisband(): Boolean = when (this) {
    AllianceRole.FOUNDER -> true
    AllianceRole.ADMIN, AllianceRole.MEMBER -> false
}

fun AllianceRole.canLeave(): Boolean = when (this) {
    AllianceRole.FOUNDER -> false
    AllianceRole.ADMIN, AllianceRole.MEMBER -> true
}

fun AllianceRole.canRemove(target: AllianceRole): Boolean = when (this) {
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

fun AllianceRole.canRename(): Boolean = when (this) {
    AllianceRole.FOUNDER -> true
    AllianceRole.ADMIN, AllianceRole.MEMBER -> false
}

fun AllianceRole.canSetRole(target: AllianceRole, role: AllianceRole): Boolean = when (this) {
    AllianceRole.FOUNDER -> when (target) {
        AllianceRole.FOUNDER -> when (role) {
            AllianceRole.FOUNDER -> true
            AllianceRole.ADMIN, AllianceRole.MEMBER -> false
        }
        AllianceRole.ADMIN, AllianceRole.MEMBER -> true
    }
    AllianceRole.ADMIN, AllianceRole.MEMBER -> false
}
