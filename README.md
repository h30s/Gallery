# Gallary

A modern Android photo & video gallery that treats **local files, a NAS over SMB,
WebDAV and Google Drive** as one library. Everything — tags, star ratings, faces,
search — works the same on a phone photo and on a file sitting on your NAS.

Built with Kotlin, Jetpack Compose (Material 3), Room, Media3/ExoPlayer, Coil,
ML Kit face detection and the Google Cast SDK.

---

## Features

| Area | What you get |
| --- | --- |
| **Local media** | MediaStore scan of photos and videos, month-grouped grid, video duration badges |
| **SMB / NAS** | jcifs-ng SMB2/SMB3 client — host, share, folder, domain, credentials |
| **Cloud storage** | WebDAV (Nextcloud, ownCloud, Synology, Box) and Google Drive (read-only OAuth) |
| **Remote browsing** | In-app folder browser with "Import this folder" (recursive, up to 5 000 items) |
| **Video player** | Media3/ExoPlayer with full controls; streams remote files without downloading first |
| **Chromecast** | Standard Cast button + device picker; local and remote media are re-served over LAN HTTP |
| **Tags** | Custom coloured labels, create/rename/delete, bulk tagging, filter by tag |
| **Ratings** | 1–5 stars, tap the active star to clear, bulk rating, "★ 4+" filter |
| **Faces** | ML Kit on-device detection, cropped face thumbnails, named people, "group similar faces" suggestions |
| **Search** | File name, album, tag name and person name; filters for type, rating, favourites, tag, person, local/remote, plus 5 sort orders |
| **UI** | Material 3 dark-first theme, optional dynamic colour, 2–5 column grid, full-screen zoomable viewer |

---

## Building

The project targets **AGP 8.5.2 / Kotlin 2.0.20 / compileSdk 34 / minSdk 26 (Android 8.0)**.

1. Open the `Gallary` folder in **Android Studio (Koala or newer)** and let it sync.
   Android Studio supplies the Gradle wrapper; if you prefer the command line, run
   `gradle wrapper` once and then `./gradlew assembleDebug`.
2. Build & run on a device or emulator running Android 8.0+.

> This repository was authored without an Android SDK available on the build
> machine, so it has not been compiled here. Open it in Android Studio; any
> dependency-version drift will surface as a normal Gradle sync message.

### Optional: Google Drive

Drive uses the standard Google Sign-In flow:

1. Create an OAuth client (Android) in the Google Cloud console for package
   `com.oxlo.gallary` with your signing SHA-1.
2. Enable the **Google Drive API**.
3. Add the client to the project (no `google-services.json` is required — Google
   Sign-In only needs the OAuth client to exist for the package + SHA-1).

Without this setup the Drive source shows a clear "sign in" error; SMB and
WebDAV work out of the box.

---

## Project layout

```
app/src/main/java/com/oxlo/gallary/
├── MainActivity.kt                 Compose host, media permission flow
├── GallaryApp.kt                   Application + Coil image loader
├── di/AppContainer.kt              Manual dependency graph
├── data/
│   ├── MediaRepository.kt          Single façade for the UI
│   ├── db/                         Room entities, DAOs, projections
│   ├── local/                      MediaStore sync + remote thumbnail cache
│   ├── remote/                     SMB, WebDAV, Drive, import & streaming
│   ├── playback/                   Media3 DataSource for remote:// URIs
│   ├── face/                       ML Kit detection, cropping, clustering
│   ├── cast/                       Cast options, LAN HTTP server, session manager
│   ├── security/CredentialStore.kt EncryptedSharedPreferences
│   └── settings/                   DataStore preferences
├── domain/                         MediaFilter → SQL, remote models
└── ui/                             theme, components, screens, view models
```

### How remote media is stored

Imported remote files become ordinary rows in `media_items` with
`sourceId`/`remotePath` set and a `remote://<sourceId><path>` URI. The rest of the
app never needs to care where a file lives:

* thumbnails download once into a 250 MB self-trimming cache,
* playback goes through a custom Media3 `DataSource` that streams from the
  backend and supports seeking,
* casting goes through a tiny NanoHTTPD server on the phone's LAN address,
  because a Chromecast cannot read `content://` URIs.

### Credentials

Passwords and OAuth tokens are stored in `EncryptedSharedPreferences`
(AES-256-GCM), keyed by source id, and are excluded from cloud backup. They are
never written to the Room database.

---

## Notes & limits

* **Face recognition** (automatically grouping the same person across photos) is
  heuristic — it uses an 8×8 luminance fingerprint to *suggest* groups. Naming is
  always manual, so a wrong suggestion never silently mislabels anyone.
* Google Drive is read-only; the app never uploads or modifies remote files.
* "Remove from library" deletes the index row only. Nothing is deleted from your
  device or your server.
