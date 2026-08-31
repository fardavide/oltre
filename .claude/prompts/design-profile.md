# Claude Design call — the player sets their name and their mark

Ready to paste into the Oltre Claude Design project. The prose record of what comes back goes into
[`../docs/profile-sheet.md`](../docs/profile-sheet.md) §6, which is the durable half.

```
Oltre — the player chooses a name and a face.

Davide's call, 2026-08-30: "I want to allow users to set their name and picture." Four things are
settled and are not yours to reopen; everything drawn is yours and none of it may be invented at the
keyboard by the build.

SETTLED (Davide, 2026-08-30)
1. The "picture" is a MARK THE PLAYER PICKS FROM A SET YOU DRAW. Never an uploaded photo, never the
   Apple/Google profile picture. Every icon in this app is a Canvas path because a bitmap rasterises
   differently on the machine that records a screenshot baseline and the machine that verifies it.
2. Both are held server-side on the account, not on the device. The wire and the table are built and
   green already; nothing you decide here is blocked on them.
3. THE EDITOR IS OPENED BY TAPPING THE MARK OR THE NAME IN THE PLAYER STRIP — not by a row in the
   settings sheet. Davide chose this over the settings route explicitly.
4. The name is free text and NOT unique. Two commanders may share one. No blocklist, no
   taken/available check, no error state for a name somebody else has.

WHAT EXISTS TODAY
- The player strip is chrome above the resource rail, behind every destination. One row, 38dp, over a
  2dp accent bottom edge that is the experience gauge. Contents, left to right: a 20dp mark
  (`PlayerMark` — a world, and a trajectory that has already left it, 24-unit box, stroke 1.6,
  accent), the name at 13.5sp SemiBold mono in `text`, an `LV n` badge at 10sp on white 9% at 4dp
  radius, and a 38dp gear in a `PressableFace`. Padding: 11dp leading, 2dp trailing, 7dp gaps.
- The name today is one constant for everybody: `Dead Reckoning`. It stays as the default for a
  player who has not chosen — so your design has to say what an unchosen name looks like next to a
  chosen one, if anything.
- The gear opens `OltreBottomSheet` wearing one of four faces: settings, changelog, delete-warn,
  delete-confirm.
- THE APP HAS NO TEXT INPUT ANYWHERE. No TextField, no BasicTextField, no keyboard handling, no IME
  inset handling, nothing. This is the first one.
- There is no disabled state anywhere in this design system, and no hover. When something cannot act
  because there is no signal, the app goes amber and says so — that vocabulary is *Nothing Is Local
  Now*, shipped at 0.21.0.
- Widths: 393dp is the reference phone, 320dp is the narrowest supported, content caps at 560dp.
  Both English and Italian; Italian is longer at every width.

WHAT I NEED FROM THE FRAME
1. THE MARK SET. How many, and what each one is. The existing mark is one drawing and its own file
   says "the day identity earns variation it should be drawn for it, not derived from a number nobody
   picked" — this is that day. Give me the paths in the icon set's 24-unit box at its stroke. Say
   whether colour is a second axis the player picks or whether the set is the whole choice; if it is
   an axis, which tokens. Every variant costs a screenshot baseline, so the count is a real cost and
   I would rather have six good ones than sixteen.
2. THE EDITOR'S FORM. A fifth face on the existing bottom sheet, its own sheet, or a destination?
   Whichever it is, the whole thing: the layout, the mark grid or row and how the chosen one is
   marked, where the name field sits relative to it, the spacing, the header, the way out.
3. THE TEXT FIELD, as a design-system component from nothing. Resting, focused, and holding text.
   The caret, the selection colour, the clear affordance if there is one, whether there is a
   character counter and what it does as it approaches the 24-character bound, and what happens when
   the software keyboard covers the field. Type scale and the field's own height.
4. THE STRIP BECOMING TAPPABLE. Which region is the target — the mark alone, the mark plus the name,
   the whole left cluster — and how it says it can be pressed, given there is no hover and no
   disabled state and the gear beside it is currently the only control on the bar. The strip's 38dp
   height is the most expensive number in this design; a target that grows it costs every
   destination below, so say plainly if your answer does.
5. THE HELD FACE. What the editor says and looks like when there is no signal. A rename cannot be
   queued — there is nothing on the server to replay it against — so this is the amber "can't do that
   right now" language rather than a red refusal.
6. THE SAVE AFFORDANCE. Every control on the settings face commits on tap. A text field is the first
   control in this app where "commits on tap" and "commit when you say so" genuinely differ. Which,
   and what the button says if there is one.
7. WHAT AN EMPTY FIELD MEANS. My recommendation, which you may overturn: clearing the name puts it
   back to `Dead Reckoning` rather than being refused, because that is the only way out of a name a
   player regrets and it is one fewer error state to draw.

CONSTRAINTS THAT ARE NOT NEGOTIABLE
- Tokens only. `OltreColors`, `oltreMono`, the existing radii and the white-9% fill. No new colour
  that is not derived from what is there.
- Marks are Canvas paths in the 24-unit icon box. No bitmaps, no gradients that a baseline cannot
  reproduce byte for byte.
- 320dp has to work, in Italian, without the strip growing taller.
- "Coming soon", "Under construction" and "Oops" are on the Never-written list. The one sanctioned
  use of "Coming soon" was the settings gear before it had a sheet, and that is gone.

Return the frames plus the numbers — every dp, sp, radius and duration — because the implementation
takes them from you rather than choosing them.
```
