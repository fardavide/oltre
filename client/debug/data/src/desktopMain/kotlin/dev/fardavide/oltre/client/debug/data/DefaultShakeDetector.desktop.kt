package dev.fardavide.oltre.client.debug.data

import kotlinx.coroutines.flow.emptyFlow

// A laptop has no accelerometer, and shaking a desk is not a gesture. Desktop is also the dev loop
// — the platform where the menu is *most* wanted — so it is opened by a keyboard shortcut instead,
// wired at the composition root where the key events already arrive.
//
// A flow that never emits rather than an absent implementation: the composition root then wires one
// detector on every platform and the shortcut is an addition rather than a special case.
actual fun defaultShakeDetector(): ShakeDetector = ShakeDetector { emptyFlow() }
