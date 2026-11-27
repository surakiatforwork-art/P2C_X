# Optional app updates

Open **อัปเดตแอป** on the main screen, then check, download and install.
No update checks run automatically. Cancelling an update does not block usage.
Downloads use Android DownloadManager and survive leaving the screen. Installation
always requires a separate tap and Android's installation confirmation.

Source: https://github.com/surakiatforwork-art/P2C_X/releases/latest

Publishing requirements:

- Publish a normal GitHub Release; drafts and prereleases are excluded.
- Use a numeric version tag such as `v0.10.0` and increase Android `versionCode`.
- Attach exactly one APK (a universal APK). Names containing `debug` are excluded.
- Keep the application ID and signing certificate unchanged. The updater checks
  both and rejects APKs whose versionCode is not greater than the installed app.
- Use the same signing key for every published APK; do not ask users to uninstall
  their existing app to work around a certificate mismatch.

The app uses the public GitHub API without embedded credentials. API rate limits,
missing releases and failed downloads are shown in the update screen. If a release
has multiple APKs, users can open the release page to choose manually.

Version tags provide the availability hint; the downloaded APK's versionCode and
certificate provide the final install validation. Downloaded files stay in the
app-specific downloads directory and are replaced on the next download.
