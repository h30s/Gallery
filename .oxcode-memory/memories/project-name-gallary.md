---
title: Project name: Gallary
slug: project-name-gallary
tags: 
scope: project
updated_at: 2026-11-11T06:38:57.773Z
source: live
hook: Project name: Gallary
sessions: s_1794378381541_rqg26i
---

- Project name: Gallary
- Target platform: Android (minSdk 26)
- Tech stack: Kotlin, Jetpack Compose, Material 3
- Build system: Gradle with AGP 8.5.2, Kotlin 2.0.20, Compose plugin, KSP/Room
- Data layer: Room database with DAOs for media, tags, people, faces, sources; MediaFilter for unified filtering/sorting
- Storage support: Local (MediaStore), SMB (jcifs-ng), WebDAV (Nextcloud/ownCloud/Synology), Cloud (Google Drive)
- Video playback: Media3/ExoPlayer with custom DataSource for remote URIs
- Chromecast support: CastManager, MediaRouteButton, LocalMediaServer for LAN streaming
- Face detection and tagging: ML Kit integration, face cropping, luminance fingerprint grouping, manual naming
- UI components: Media grid, full-screen viewer, multi-select, star ratings, tag chips, cast button, search, albums, people, tags, sources, settings
- Security: EncryptedSharedPreferences for credentials
- File structure: Standard Android project layout with modular Kotlin packages
- Not built: Compilation not attempted due to missing JDK/Gradle/SDK on host machine
