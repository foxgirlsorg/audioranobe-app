# AudioRanobe Android app
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Android CI](https://github.com/foxgirlsorg/audioranobe-app/actions/workflows/android.yml/badge.svg)](https://github.com/foxgirlsorg/audioranobe-app/actions/workflows/android.yml)

> **The native Android client for the AudioRanobe audiobook platform.**

This repository contains the source code for the AudioRanobe Android app — a Jetpack Compose client for browsing, listening to and tracking voiced light novel and book translations. It follows the design rules of the site ([foxgirlsorg/audioranobe-frontend](https://github.com/foxgirlsorg/audioranobe-frontend)) and talks to the same API.

The API powering the content is maintained separately: [foxgirlsorg/audioranobe-backend](https://github.com/foxgirlsorg/audioranobe-backend). Audio transcoding is handled by [foxgirlsorg/audioranobe-convertor](https://github.com/foxgirlsorg/audioranobe-convertor).

## 🛠️ Technical Overview

* **Language:** [Kotlin](https://kotlinlang.org/) 2.2
* **UI:** [Jetpack Compose](https://developer.android.com/compose) 1.9 + Material 3, single-activity, [Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
* **Playback:** [Media3 / ExoPlayer](https://developer.android.com/media/media3) with a `MediaSessionService` (lock-screen and headphone controls, background playback)
* **Networking:** [OkHttp](https://square.github.io/okhttp/) + [kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization), cookie-based session, `X-Me` viewer header decoding
* **Images:** [Coil](https://coil-kt.github.io/coil/) (with SVG badges), [vanniktech image cropper](https://github.com/CanHub/Android-Image-Cropper), [zoomable](https://github.com/usuiat/Zoomable) viewer
* **Markdown:** [multiplatform-markdown-renderer](https://github.com/mikepenz/multiplatform-markdown-renderer)
* **Charts:** [compose-charts](https://github.com/ehsannarmani/ComposeCharts)
* **Icons:** [Lucide](https://lucide.dev/) (generated `ImageVector`s from `lucide-static`)
* **Localization:** Russian only, like the site
* **Min SDK:** 26 (Android 8.0) · **Target SDK:** 36

## ✨ Highlights

* **Account-gated** — the app opens on the sign-in / registration screens (email, OAuth providers, TOTP) and shows nothing else until you are signed in.
* **Audio player** — persistent mini bar above the dock with a full-screen stage: playback speed (0.5–3×), sleep timer, volume, chapter illustrations, progress saved server-side while you listen, resume of the last-open chapter. Swipe the mini bar to skip ±10 s, swipe the full player down to close it.
* **Catalog** — title grid with live search, filters and sorting; title pages with volumes, chapters, illustrations, ratings and comments. Swipe horizontally to switch tabs.
* **Offline library** (app only) — download a chapter, a volume or the whole book together with its info, covers and illustrations; play without a network from the «Загрузки» dock tab. Progress made offline is kept on the device and synced when a connection is back — and never overwrites server progress that is already further in the book.
* **Community** — profiles, library shelves, favorites, listening history, friends, direct messages with animated bubbles, collections, news, narrator blogs, monthly and yearly recaps.
* **Creator tools** — add books, narrators and authors; edit titles (info, artwork, illustrations); manage volumes, chapters and chunked audio uploads; alternate narrations; bulk upload.
* **Moderation panel** — `/mod/*` tools gated by the backend permission system: queue, reports, review, comments, word filter, reserved names, trash, titles import, users, narrators, authors, tags, badges, DMCA, banners, donations, audit log and jobs.
* **Motion** — the dock and mini player fold while you scroll and unfold on the first scroll up; screens, sheets and the player slide in and out.
* **In-app updates** — the app polls the GitHub Releases of the repository it was built from, shows a popup with the release notes, downloads the APK and hands it to the system installer. «Позже» hides it until the next check, «Пропустить версию» silences that release. A manual check lives under «Другое».

## 🚀 Local Development

### Prerequisites

* Android Studio (Ladybug or newer) or a JDK 17 + Android SDK (compile SDK 36) setup
* A running instance of the [AudioRanobe backend](https://github.com/foxgirlsorg/audioranobe-backend), or the public API

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/foxgirlsorg/audioranobe-app.git
   cd audioranobe-app
   ```

2. **Configure the API URL** (optional)

   The base URL is baked into `BuildConfig` at build time. Pass it as a Gradle property or an environment variable:
   ```bash
   ./gradlew assembleDebug -PapiUrl=http://10.0.2.2:8080/api -PsiteUrl=http://10.0.2.2:3000
   ```

   | Property / variable | Required | Description |
   |---|---|---|
   | `apiUrl` / `API_URL` | No | Backend API URL, including `/api`. Defaults to `https://back.audioranobe.com/api`. |
   | `siteUrl` / `SITE_URL` | No | Public site URL, used for share links, OAuth and the captcha widget. Defaults to `https://audioranobe.com`. |
   | `updateRepo` / `UPDATE_REPO` | No | `owner/name` of the GitHub repository whose Releases the in-app updater polls. Empty (the default for local builds) disables the updater. CI passes its own repository. |
   | `versionName` / `VERSION_NAME` | No | Version string (`v` prefix is stripped). Defaults to `0.1.0`; the release workflow passes the tag. |
   | `versionCode` / `VERSION_CODE` | No | Integer version code. Defaults to `1`; CI passes the run number. |

3. **Build and install**
   ```bash
   ./gradlew installDebug
   ```

> ### ⚠️ `API_URL` is baked in at build time
> Changing it requires a **rebuild**, not just a restart — set it before building the release APK.

## 📦 Building & Deployment

```bash
./gradlew assembleRelease
```

The release build type minifies and shrinks resources. Signing is read from the environment: `SIGNING_KEYSTORE` (path), `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS`, `SIGNING_KEY_PASSWORD`. Without them the release APK stays unsigned.

Two GitHub Actions workflows are included:

* **Android CI** (`.github/workflows/android.yml`) — builds a debug APK on every push and uploads it as the `audioranobe-debug` artifact. No keys or secrets are needed.
* **Release** (`.github/workflows/release.yml`) — on a `v*` tag builds a signed release APK and publishes it as a GitHub Release, which is what the in-app updater picks up. It needs the secrets `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEYSTORE_PASSWORD`, `SIGNING_KEY_ALIAS` and `SIGNING_KEY_PASSWORD`. Use the same key for every release: Android refuses to install an update signed with a different key.

```bash
git tag v1.0.0 && git push origin v1.0.0
```

## 📂 Project Structure

```text
app/src/main/java/org/foxgirls/audioranobe/
├── App.kt / MainActivity.kt   # Application, deep links, splash
├── core/                      # Api (OkHttp + X-Me), cookies, prefs, formatting, limits, Updater (OTA)
├── data/                      # Serializable models, Auth / badges / config stores
├── offline/                   # OfflineStore (manifests, download queue, progress sync) + DownloadService
├── player/                    # PlaybackService (Media3) + PlayerController
└── ui/
    ├── AppShell.kt            # Auth gate, NavHost, dock, full player, sheets
    ├── Gestures.kt            # Swipe tabs, swipe back, swipe-down-to-dismiss, dock scroll
    ├── components/            # Kit (buttons, fields, tabs…), cards, dialogs, markdown, pickers, social
    ├── nav/                   # Routes, AppNav, dock, account menu, search sheet, banners
    ├── player/                # Mini player, full player
    ├── screens/
    │   ├── auth/              # Login, register, forgot / reset, verify, setup, OAuth, TOTP
    │   ├── catalog/ title/    # Catalog grid + filters, title page
    │   ├── content/           # Collections, news, posts, narrator, author, donate, DMCA, legal, «Другое»
    │   ├── offline/           # Downloads tab, per-chapter download controls, download sheet
    │   ├── editing/           # Add content, title / narrator / author edit, uploads, illustrations
    │   ├── me/                # Profile, friends, history, notifications, requests, settings, chat, recap
    │   └── mod/               # Moderation panel
    └── theme/                 # Design tokens from the site's globals.css
```

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

Copyright © 2026 **foxgirls.org**. All rights reserved.
