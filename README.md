# GameLauncher

Launcher Android bertema game dengan tampilan ala Steam Deck, dibangun dengan Jetpack Compose dan Material 3.

## Fitur

- **Tampilan ala Steam Deck**: latar biru gelap, bar atas dengan tab, jam, dan baterai, kartu game potret yang membesar saat dipilih, serta tombol hijau **MAINKAN**
- Mendeteksi game terpasang secara otomatis (kategori game atau flag game)
- Beranda dengan baris **Terakhir Dimainkan**, **Favorit**, dan **Semua Game**
- Ketuk sekali untuk memilih game, ketuk lagi (atau tombol MAINKAN) untuk bermain, tahan untuk opsi
- Koleksi dengan filter favorit, pencarian cepat
- Tambah atau hapus app secara manual; daftar otomatis bisa dikembalikan kapan saja
- Daftar app dimuat ulang otomatis saat kembali ke launcher (game baru langsung muncul)
- Tombol Back dari tab mana pun kembali ke Beranda

Paling nyaman dipakai dalam mode landscape.

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
| `DeckUi.kt` | Palet warna `Deck`, bar atas, jam, baterai, dan tombol |
| `Screens.kt` | Kartu game dan layar (Beranda, Koleksi, Cari, Atur) |
| `MainActivity.kt` | Root `LauncherApp` dan activity |

## Catatan

`applicationId` masih memakai `com.contoh.gamelauncher`. Ganti sebelum dirilis.
