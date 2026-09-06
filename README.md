# Termux Sorter

Automatické třídění souborů pomocí Android Share/Open-With intentů ("Otevřít v...").

## Jak to funguje

1. V libovolné aplikaci zvol soubor → Sdílet / Otevřít v... → **Termux Sorter**.
2. Aplikace podle přípony souboru najde odpovídající pravidlo v `default.json`
   (nebo v uložené konfiguraci, pokud sis pravidla upravil/a v appce) a soubor
   přesune do `Download/<cílová složka>/`.
3. Podle pravidla se pak spustí jedna z akcí:
   - **bez `actionType`** → otevře se nová Termux session rovnou v cílové složce
   - `actionType: "APP"` → spustí se aplikace podle `actionValue` (package name)
   - `actionType: "TERMUX_RUN"` → na pozadí se spustí skript z `actionValue`
     s cestou k přesunutému souboru jako argumentem
   - `actionType: "NONE"` → nic se nedělá

## Formát pravidla (`default.json` / uložená konfigurace)

```json
{
  "extensions": ["zip", "rar"],
  "destination": "archives",
  "actionType": "TERMUX_RUN",
  "actionValue": "/data/data/com.termux/files/home/bin/unpack.sh"
}
```

`actionType`/`actionValue` jsou nepovinné.

## Nutná jednorázová příprava v Termuxu

Aby appka mohla otevírat Termux / spouštět v něm příkazy (`RUN_COMMAND` API),
musí mít Termux povolené externí appky:

```bash
mkdir -p ~/.termux
echo "allow-external-apps = true" >> ~/.termux/termux.properties
termux-reload-settings
```

Bez toho Termux žádost od Sorteru potichu odmítne.

Pokud plánuješ třídit na Androidu 10+ (API 29+), appka zapisuje přes
`MediaStore` a žádné další oprávnění není potřeba. Na starších verzích (API
23–28) si appka při prvním použití vyžádá `WRITE_EXTERNAL_STORAGE`.

## Build

```bash
pkg install openjdk-17 gradle
gradle assembleRelease
```

Pro podepsaný release build vytvoř `keystore.properties` (viz
`keystore.properties.example`) - bez něj se release sestaví s debug klíčem,
aby build alespoň prošel.

## Stav

- [x] Zobrazení a editace pravidel v appce (persistentně, `Config`)
- [x] `ACTION_SEND` / `ACTION_SEND_MULTIPLE` / `ACTION_VIEW` handler (`SortActivity`)
- [x] Ochrana proti přepsání souboru se stejným jménem
- [x] Volitelná akce po přesunu (otevření Termuxu ve složce / spuštění appky / skriptu)
- [ ] Editace `actionType`/`actionValue` přímo v UI (zatím jen přes JSON)
- [ ] Historie přesunů
