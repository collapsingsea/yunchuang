# Changelog

## 2.4.1

- Replaced the built-in sample websites with Bilibili and YouTube.
- Replaced the homepage image with a clean preview using public example websites.

## 2.4.0

- Added an English interface. A single APK follows the system language, with Chinese for Chinese locales and English as the fallback.
- Localized the home screen, website editor, fingerprint settings, proxy notes, floating menu, dialogs, download messages and error page.
- Localized the initial sample website names. Existing user-saved names remain unchanged.
- Introduced a dedicated production signing key. Existing debug builds must be uninstalled first; subsequent production builds support in-place updates.
- Published GPLv3 source, bilingual documentation and the surviving version history.

## 2.3.1

Recovered from the original project’s `REVIEW-v2.3.1.md`:

- Allowed custom page widths in mobile mode; resetting returns to the device viewport. Reloaded on User-Agent changes to clear old layout injections.
- Expanded the landscape menu by default, with five columns and scrolling.
- Added reset-to-default settings while retaining the website name, URL and identity.
- Preserved temporary page URLs and configuration when opening the editor.
- Used a successfully loaded page title for unnamed websites, avoiding error-page titles.
- Avoided duplicate saved URLs and preserved updated settings and newly added websites during reordering.
- Fixed unintended editor opening after a drag or cancelled drag, and retained explicit orientation choices.
- Used the device WebView User-Agent for mobile mode and stopped loading when proxy setup failed.
- Corrected floating-button bounds in small windows and escaped error-page text.

The original record reports a successful build, ten passing Robolectric tests and successful lint. It does not record comprehensive physical-device validation.

## 2.3.0 / 2.2.0

APKs survive. Separate release notes were not found, so individual changes are not reconstructed from the current feature set.

## 2.0 and earlier

The original README describes a single-site academic-portal wrapper that was rewritten into the multi-site Yunchuang app starting with 2.0. A complete early changelog was not found.
