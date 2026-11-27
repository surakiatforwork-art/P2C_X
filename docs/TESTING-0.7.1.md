# 0.7.1-gallery-test

Build, Android Lint and full on-device instrumentation passed on e3b20843. Installed the final APK successfully.

Regression checks cover legacy adjustment inheritance, saved target preference precedence and isolation, hidden recommendation without metadata, compact recommendation placement directly after Free, applying a known ratio, and existing gallery/editor/capture callback contracts.

Editor preferences continue to persist only when Use image confirms the result. Existing target preferences are retained. No target shutter or check-in actions were performed. No push or release.
