# Stage 22 — Streaming Queue Intelligence

Stage 22 adds bounded priority aging to both leaf and parent CPU mesh queues. The scheduler evaluates effective priority when selecting work and when deciding which queued job to evict under capacity pressure. Older jobs receive a gradually reduced score, capped at 120 seconds, while newly refreshed priorities retain the original enqueue age. This reduces starvation without allowing old work to permanently outrank newly visible work.

The queue remains bounded (128 leaf jobs and 48 parent jobs), deduplicates requests by section/node identity, and keeps revision checks for stale results. The feature is CPU-only and does not require compute shaders, SSBOs, or a new user setting.

Build blockers discovered from the supplied GitHub Actions logs are also addressed in this baseline: project repositories are no longer prohibited by `settings.gradle`, Fabric Maven is declared at project level, and `ClientPlayConnectionEvents` uses the Fabric API networking package.

Validation: dependency-free Stage 22 policy smoke test is included. A full Gradle/Fabric build and Android Zalith/MobileGlues runtime test must still be run in GitHub Actions and on-device.
