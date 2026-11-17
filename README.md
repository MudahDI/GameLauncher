# GameLauncher

Launcher Android bertema game, dibangun dengan Jetpack Compose dan Material 3.

## Fitur

- Mendeteksi game terpasang secara otomatis (kategori game atau flag game)
- Beranda dengan baris **Terakhir Dimainkan**, **Favorit**, dan **Semua Game**
- Pencarian cepat dan tab Koleksi dengan filter favorit
- Tambah atau hapus app secara manual; daftar otomatis bisa dikembalikan kapan saja
- Daftar app dimuat ulang otomatis saat kembali ke launcher (game baru langsung muncul)
- Tombol Back dari tab mana pun kembali ke Beranda

## Kebutuhan

- Android 8.0 (API 26) ke atas
- JDK 17 dan Gradle 8.9

## Build

```bash
gradle assembleDebug
```

APK ada di `app/build/outputs/apk/debug/`. Setiap push juga membangun APK lewat GitHub Actions (artifact `GameLauncher-debug`).

## Struktur

| File | Isi |
|------|-----|
| `Data.kt` | Model `AppItem`, pemuat daftar app, dan penyimpanan `Store` |
| `Screens.kt` | Komponen UI dan layar (Beranda, Cari, Koleksi, Atur) |
| `MainActivity.kt` | Root `LauncherApp` dan activity |

## Catatan

`applicationId` masih memakai `com.contoh.gamelauncher`. Ganti sebelum dirilis.
