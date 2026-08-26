# The Oltre server, as Cloud Run wants it.
#
# **`#111`'s scope line, verbatim: a JRE over `installDist`.** The `application` plugin already
# produces a directory holding a start script and every jar on the classpath, so there is nothing for
# an image build to work out — and doing it in two steps rather than one multi-stage build is the
# whole point: `./gradlew :server:installDist` runs in the deploy workflow, where Gradle's cache is
# warm and the Kotlin compiler has already been paid for, and this file copies the answer. A
# self-contained image would re-download the world on every deploy, inside a builder with no cache.
#
# The consequence, stated so it is not discovered: **`docker build .` on its own will fail.** The
# `COPY` below needs `server/build/install/server` to exist, and the error names the path. See
# `.github/workflows/deploy-server.yml`, which is the only thing that builds this.
#
# **Listening on `$PORT` is Cloud Run's entire contract**, which is why every other host on `#106`
# §6's shortlist stays a redeploy away rather than a rewrite. `Main.kt` reads it and falls back to
# 8080; nothing else here is Cloud Run specific.
FROM eclipse-temurin:21-jre

# **Not Alpine, and it is a choice rather than the default.** The musl images are about a hundred
# megabytes smaller, which would shave something off the pull on a cold instance — and a JVM on musl
# is a different libc with its own occasional surprises, on the one machine in this project nobody can
# attach a debugger to. Worth revisiting with a measurement if the cold start recorded on `#111` ever
# stops being good enough; not worth guessing at now.

# **Not root.** Cloud Run runs whatever the image says, and there is nothing here that needs to be
# able to write to the filesystem: the colony lives in Postgres and the secrets are mounted read-only.
RUN useradd --system --create-home --uid 10001 oltre
USER oltre

WORKDIR /opt/oltre
COPY --chown=oltre:oltre server/build/install/server/ ./

# **The JVM's own default would give this a 128 MiB heap.** It sizes the heap at a quarter of what the
# container has, which is right on a machine shared with other things and wrong on a container that
# holds one process — and the failure mode is a colony that replays a long absence and dies of
# `OutOfMemoryError` rather than a colony that is slow. Seventy-five per cent of 512 MiB leaves the
# JVM's non-heap footprint the rest, which is comfortable for Netty and one connection pool.
#
# `UseSerialGC` because there is one vCPU and one request at a time on it. The JVM picks it by itself
# below two CPUs — but **startup CPU boost temporarily gives the instance more**, which is exactly
# when the ergonomics are read, so a boosted instance would otherwise choose G1 and pay for a
# concurrent collector it never needs. Spelled out so the boost cannot change a decision behind us.
#
# **`-XX:+UseContainerSupport` is not here** because it has been on by default since 10 and adding it
# would suggest somebody had reason to doubt it.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# Documentation rather than configuration — Cloud Run routes to `$PORT` and ignores this. It is here
# so that `docker run -P` locally does the obvious thing.
EXPOSE 8080

ENTRYPOINT ["/opt/oltre/bin/server"]
