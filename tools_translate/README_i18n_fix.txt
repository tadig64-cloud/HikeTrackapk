HikeTrack - i18n quick FIX (v7)

Run:
- tools_translate\03_sanitize_resources.bat

What it fixes:
- AAPT errors like "Invalid unicode escape sequence" caused by translated strings containing "\u{...}" etc.
- Incorrect non-localizable XML duplicates inside values-en / values-es / values-de / values-fr created by copy/translate.

Backup:
- tools_translate\_i18n_backup\TIMESTAMP\...

Note:
- It DOES NOT touch folders like values-night, values-v21, values-sw600dp, etc.
