package dev.fardavide.oltre.client.net.data

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin

// `NSURLSession`, and it is the only sensible choice on this platform rather than one of several:
// it is what the system gives cellular policy, proxy configuration and App Transport Security to,
// and a socket opened around it gets none of them.
//
// **Nothing in this repository can run this**, and CI compiles rather than runs it — see the
// `iOS framework` job. The first device install is the first time this line is executed.
actual fun oltreHttpClient(): HttpClient = HttpClient(Darwin) { oltreDefaults() }
