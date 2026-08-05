package dev.fardavide.oltre.client

import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController

@Suppress("unused", "FunctionName") // Entry point for the iosApp Xcode wrapper.
fun MainViewController(): UIViewController = ComposeUIViewController { App() }
