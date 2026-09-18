package dev.fardavide.oltre.client.alliance.ui

import androidx.compose.material3.Surface
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.runDesktopComposeUiTest
import dev.fardavide.oltre.client.design.core.OltreTheme
import dev.fardavide.oltre.client.design.testing.SETTLED_MILLIS
import dev.fardavide.oltre.client.design.testing.oltreRoborazziOptions
import dev.fardavide.oltre.client.design.text.English
import dev.fardavide.oltre.client.design.text.Italian
import dev.fardavide.oltre.client.design.text.Strings
import dev.fardavide.oltre.client.design.text.TextRes
import dev.fardavide.oltre.client.design.text.Translations
import dev.fardavide.oltre.protocol.AllianceMemberId
import dev.fardavide.oltre.protocol.AllianceRole
import dev.fardavide.oltre.protocol.MarkPreset
import dev.fardavide.oltre.protocol.PlayerMark
import io.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test

// **Every state the permission table produces**, drawn from hand-built state with no gateway near it.
//
// Five pictures and not six: the table has six cells and four of them open no face at all, which is a
// roster the screen test already photographs rather than a face this one can. What is here is the two
// cells that open one, each on both of its steps, plus held — and Italian on the last step, which is
// the state with the longest sentence in the feature on it and the one that stacks at 320dp.
@OptIn(ExperimentalTestApi::class)
class MemberCommandsScreenshotTest {

    @Test
    fun `the founder on a member`() {
        capture("member_founder_on_member", commands(role = AllianceRole.MEMBER))
    }

    // The same face inverted: `ADMIN` on the caption ramp, `Demote to member` in the blue.
    @Test
    fun `the founder on an admin`() {
        capture("member_founder_on_admin", commands(role = AllianceRole.ADMIN))
    }

    // **One command, and the face is shorter because of it.** An admin may kick members and nothing
    // else, so there is no blue on this face at all and no note under it — the absences are absences.
    @Test
    fun `an admin on a member`() {
        capture("member_admin_on_member", commands(role = AllianceRole.MEMBER, viewer = AllianceRole.ADMIN))
    }

    // The card holds still, the reading is replaced by the two consequences with the first in red,
    // and the filled red is the second tap — the only one in the product besides the delete face's.
    @Test
    fun `the last step`() {
        capture("member_last_step", confirming())
    }

    // At 320dp the pair stacks. **Italian is the width test**: the first consequence runs to three
    // lines, which is where the face's height comes from.
    @Test
    fun `the last step in Italian, stacked`() {
        capture("member_last_step_narrow_it", confirming(), translations = Italian, compact = true, width = 320)
    }

    // **Held dims what acts and never what informs**: the card and the reading keep full strength
    // because reading them is not acting, and both commands drop and stop pressing.
    @Test
    fun `no network`() {
        capture(
            "member_held",
            commands(role = AllianceRole.MEMBER).copy(
                held = MemberHeldUiState(
                    lead = Strings.allianceMemberHeldLead(),
                    body = Strings.allianceMemberHeldBody(),
                ),
            ),
        )
    }

    private fun capture(
        name: String,
        uiState: MemberCommandsUiState,
        translations: Translations = English,
        compact: Boolean = false,
        width: Int = PHONE_WIDTH,
        height: Int = 500,
    ) {
        runDesktopComposeUiTest(width = width, height = height) {
            mainClock.autoAdvance = false
            setContent {
                OltreTheme(translations = translations) {
                    Surface {
                        MemberCommandsContent(
                            uiState = uiState,
                            compact = compact,
                            actions = MemberCommandsActions(),
                        )
                    }
                }
            }
            mainClock.advanceTimeBy(SETTLED_MILLIS)
            onRoot().captureRoboImage(
                filePath = "src/desktopTest/screenshots/$name.png",
                roborazziOptions = oltreRoborazziOptions(),
            )
        }
    }

    private fun commands(
        role: AllianceRole,
        viewer: AllianceRole = AllianceRole.FOUNDER,
    ): MemberCommandsUiState = MemberCommandsUiState(
        id = AllianceMemberId("seat-3"),
        member = card(role),
        step = MemberCommandsStepUiState.Commands(
            reading = Strings.allianceMemberLastSeen(TextRes("3d 04h")),
            role = when {
                viewer != AllianceRole.FOUNDER -> null
                role == AllianceRole.ADMIN -> RoleCommandUiState(
                    action = Strings.allianceMemberDemote(),
                    note = Strings.allianceMemberDemoteNote(),
                    role = AllianceRole.MEMBER,
                )
                else -> RoleCommandUiState(
                    action = Strings.allianceMemberPromote(),
                    note = Strings.allianceMemberPromoteNote(),
                    role = AllianceRole.ADMIN,
                )
            },
            kick = Strings.allianceMemberKickNamed(NAME),
        ),
        held = null,
    )

    private fun confirming(): MemberCommandsUiState = MemberCommandsUiState(
        id = AllianceMemberId("seat-3"),
        member = card(AllianceRole.MEMBER),
        step = MemberCommandsStepUiState.Confirm(
            consequence = Strings.allianceMemberKickFirstFact(NAME),
            aftermath = Strings.allianceMemberKickSecondFact(),
            kick = Strings.allianceMemberKick(),
            keep = Strings.allianceMemberKeep(),
        ),
        held = null,
    )

    private fun card(role: AllianceRole): MemberCardUiState = MemberCardUiState(
        mark = PlayerMark.Preset(MarkPreset.TERMINATOR),
        name = NAME,
        role = if (role == AllianceRole.ADMIN) Strings.allianceRoleAdmin() else null,
        level = Strings.levelBadge(9),
    )

    private companion object {
        val NAME = TextRes("Long Shadow")
        const val PHONE_WIDTH = 393
    }
}
