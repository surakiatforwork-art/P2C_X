# 0.7.0-gallery-test validation

- Debug APK and instrumentation APK compile; Android Lint passes with existing warnings.
- Device tests on e3b20843 passed: separate caller profiles; album saved without selecting a photo; reopen restores album and grid position/offset; direct gallery-to-editor; original/reset/crop/resize; modern framework settings; shipped callback/file contracts for Image Keeper, GhostShift, Nera.
- Final device instrumentation passed for rotated crops, portrait output, configured ratios and viewport precedence.
- Image Keeper exposes ResolutionInfo. GhostShift 1.29 and Nera 2691.1.0 remove it; adapters read the viewport, bound stream size, crop ratio and relative rotation used by their shipped takePicture implementations. Raw DEX field/method names were checked.
- After the phone reconnected, the final APK and test APK were installed successfully on e3b20843. The complete instrumentation suite passed, including the renamed viewport/stream/rotation contracts for GhostShift and Nera. Live capture-to-gallery recommendation checks with actual bound target cameras still require user testing. No target shutter, check-in or saved business data was touched.
- No push or release performed.
