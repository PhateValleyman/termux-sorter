# Termux Sorter

Android aplikace pro automatické třídění souborů přijatých přes **Otevřít v...**.

## Features

- Automatické třídění podle přípon
- Android launcher konfigurace
- JSON pravidla
- Integrace s Termux
- Build přímo v Termuxu
- Vlastní podepsané APK

## Default rules

Minecraft:

.mcworld .mcaddon .mcpack

→ `~/downloads/Minecraft`

Android packages:

.apk .apks .xapk .apkm

→ `~/downloads/APKS`

Archives:

.zip .rar .7z .tar.gz .gz .xz .bz2

→ `~/downloads/archives`

## Structure

TermuxSorter/ ├── app/ ├── assets/ │   └── default.json ├── build.gradle ├── settings.gradle └── gradle.properties

## Build

Install:

```bash
pkg install openjdk-17 gradle

Release:

gradle assembleRelease

Output:

app/build/outputs/apk/release/app-release.apk

Signing

Keystore:

/data/data/com.termux/files/home/.config/keys/my.bks

Alias:

valleyman

Usage

1. Spustit Termux Sorter


2. Nastavit pravidla


3. Ve file manageru:



Otevřít v → Termux Sorter

Configuration

assets/default.json

Example:

{
  "rules": [
    {
      "extensions": ["mcworld","mcaddon","mcpack"],
      "destination": "Minecraft"
    }
  ],
  "default": "Other"
}

Roadmap

v0.1

Launcher aplikace

JSON konfigurace

Zobrazení pravidel


v0.2

Editace pravidel

Přidávání pravidel

Mazání pravidel


v0.3 ✅

ACTION_SEND receiver

Přímé třídění přes Android intent

Poznámka: na Androidu 6.0–8.1 (API 23–28) aplikace při prvním použití
požádá o oprávnění k zápisu do úložiště (na novějších verzích to díky
MediaStore není potřeba).


v0.4

Historie přesunů

Export/import konfigurace


Package

com.umbrellacorp.termuxsorter

License

Personal Umbrella project
