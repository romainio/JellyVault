# JellyVault-Hilfe

[English](en.md) · [Français](fr.md) · [Español](es.md) · **Deutsch** · [Zurück zur README](../../README.de.md)

- [1. Installation](#1-installation)
- [2. Bei Jellyfin anmelden](#2-bei-jellyfin-anmelden)
- [3. TMDB-Schlüssel hinzufügen](#3-tmdb-schlüssel-hinzufügen)
- [4. Radarr und Sonarr verbinden](#4-radarr-und-sonarr-verbinden)
- [5. Einen Film oder eine Serie anfragen](#5-einen-film-oder-eine-serie-anfragen)
- [6. TMDB-Konto verknüpfen (Bewertungen und Merkliste)](#6-tmdb-konto-verknüpfen-bewertungen-und-merkliste)
- [7. Sprache](#7-sprache)
- [8. Katalog-Einstellungen](#8-katalog-einstellungen)
- [9. Updates](#9-updates)
- [10. Fehlerbehebung](#10-fehlerbehebung)

## 1. Installation

1. Erlaube auf dem Fernseher Apps aus unbekannten Quellen für die App, mit der du installierst (zum Beispiel *Downloader* oder einen Dateimanager).
2. Lade `JellyVault.apk` von der Seite [Releases](https://github.com/romainio/JellyVault/releases) herunter, oder die APK, die zu deinem Gerät passt (`JellyVault-armeabi-v7a.apk` für die meisten Fernseher, `JellyVault-arm64-v8a.apk` für neuere Boxen).
3. Öffne die Datei, um sie zu installieren. Von einem Computer aus kannst du auch `adb install JellyVault.apk` ausführen.

JellyVault benötigt Android TV oder Fire TV mit Android 6.0 oder neuer und einen Jellyfin-Server `10.10.x` oder `10.11.x`.

Beim Start der App läuft kurz ein leuchtendes Logo, danach öffnet sich die Startseite.

## 2. Bei Jellyfin anmelden

Gib beim ersten Start die Adresse deines Jellyfin-Servers ein und melde dich mit deinem Jellyfin-Konto an. JellyVault merkt sich den Server und meldet dich beim nächsten Mal automatisch an (*Einstellungen › Sign in automatically*).

Auch ohne die folgenden Schritte ist JellyVault bereits ein vollständiger Jellyfin-Client: Bibliotheken, Wiedergabe, Untertitel, Live-TV und Musik.

## 3. TMDB-Schlüssel hinzufügen

TMDB liefert die Entdecken-Reihen (Trends, Genres, Studios, Empfehlungen) und die Titelseiten der Filme und Serien, die du nicht besitzt.

1. Erstelle ein kostenloses Konto auf [themoviedb.org](https://www.themoviedb.org), öffne dann <https://www.themoviedb.org/settings/api> und beantrage einen API-Schlüssel.
2. Öffne auf dem Fernseher *Einstellungen › TMDB & IMDb ratings* und wähle *TMDB API key*.
3. Gib den *API key* (32 Zeichen) oder das *API read access token* (ein langer Text, der mit `eyJ` beginnt) ein. Beides funktioniert.
4. Starte die App neu: Die Entdecken-Reihen werden beim nächsten Start geladen.

![TMDB-Schlüssel](../screenshots/tmdb-key.jpg)

Der Schlüssel bleibt auf dem Fernseher und wird nur an TMDB gesendet, sonst nirgendwohin. Ein Schlüssel mit einem Leerzeichen oder einem fehlenden Zeichen wird mit *This key looks invalid* abgelehnt. Um den Schlüssel zu entfernen, speichere ein leeres Feld.

*Built into this version* bedeutet, dass der Build bereits einen Schlüssel enthält (nur bei Builds, die mit `tmdb.readToken` in `local.properties` erstellt wurden). Ein von dir eingegebener Schlüssel ersetzt ihn.

Bestätige auf einer Fernseher-Tastatur mit der ✓-Taste der Tastatur und wähle dann *Speichern*.

![Den Schlüssel eingeben](../screenshots/tmdb-key-dialog.jpg)

## 4. Radarr und Sonarr verbinden

Anfragen sind optional. Sie benötigen [Radarr](https://radarr.video) für Filme und [Sonarr](https://sonarr.tv) für Serien.

1. Öffne *Einstellungen*. Der Abschnitt *Requests and ratings* listet *Radarr* und *Sonarr* auf.
2. Fülle bei jedem Eintrag Folgendes aus:
   - **Server URL**: die Adresse, die du im Browser öffnest, gegebenenfalls mit Basispfad, zum Beispiel `https://radarr.example.com` oder `http://192.168.1.10:7878`.
   - **API key**: in Radarr oder Sonarr unter *Settings › General › Security › API Key*.
   - **Access username / password** (optional): nur, wenn Radarr oder Sonarr hinter einem Reverse-Proxy mit HTTP-Basic-Authentifizierung laufen.
3. Wähle **Test connection**. Wenn alles stimmt, zeigt es die Radarr- bzw. Sonarr-Version mit ✓ an.
4. Wähle das **Quality profile** und den **Root folder**, die neue Anfragen verwenden sollen.

![Einstellungen](../screenshots/settings.jpg)

![Radarr-Einstellungen](../screenshots/radarr.jpg)

## 5. Einen Film oder eine Serie anfragen

Öffne einen beliebigen Titel, den du nicht besitzt, von der Startseite, einer Entdecken-Seite oder der Suche aus.

- **Film**: *Request* sendet ihn sofort an Radarr.
- **Serie**: *Request* fragt, was an Sonarr gesendet werden soll: *All seasons*, *Latest season* oder *Choose…* für bestimmte Staffeln.

![Eine Serie anfragen](../screenshots/request.jpg)

Danach verfolgt der Button den Download: *Requested*, *Downloading 42%*, *Available soon* und schließlich *Watch*, sobald die Datei in Jellyfin liegt. *Refresh* weist Jellyfin an, seine Bibliothek zu scannen, wenn ein Download abgeschlossen ist, der Titel aber noch nicht erscheint. Bei einer Serie, von der bereits einige Staffeln vorhanden sind, zeigt der Button *2/5 seasons · Request more*.

Wenn auf dem Button *Set up Radarr* oder *Set up Sonarr* steht, ist der Dienst noch nicht eingerichtet: Beim Auswählen öffnen sich seine Einstellungen.

## 6. TMDB-Konto verknüpfen (Bewertungen und Merkliste)

Die Verknüpfung deines TMDB-Kontos verbessert die Empfehlungen:

- deine Bewertungen fließen in die Reihen *Because you watched…*, *Movies for you* und *Series for you* ein,
- Titel, die du bewertet hast, werden als gesehen markiert,
- deine TMDB-Merkliste erscheint als *Your list* auf der Startseite.

Öffne *Einstellungen › TMDB & IMDb ratings › Link my TMDB account*, scanne den Code mit deinem Smartphone, melde dich bei TMDB an und tippe auf *Approve*; wähle dann auf dem Fernseher *Done, it's approved*. *Unlink* hebt die Verknüpfung auf.

*Rate from the TV* (in den *Einstellungen*) fügt auf den Titelseiten einen Button *Rate* hinzu. Ist die Option aus, stammen die Bewertungen nur aus deinem TMDB-Konto.

**IMDb-Bewertungen**: JellyVault liest Bewertungen von TMDB. Um deine IMDb-Bewertungen zu nutzen, kopiere sie mit einem IMDb-→-TMDB-Sync-Tool, das du selbst ausführst, in dein TMDB-Konto.

## 7. Sprache

*Einstellungen › User interface language* ändert die Sprache der App. TMDB-Titel, Inhaltsangaben, Genres, Logos und Trailer folgen ihr.

- Die JellyVault-Bildschirme sind auf Englisch und Französisch übersetzt. In anderen Sprachen zeigen diese Bildschirme Englisch.
- Die Beschreibungen der Titel, die du besitzt, stammen von deinem Jellyfin-Server, und zwar in der *Preferred metadata language* (bevorzugte Metadatensprache) der jeweiligen Bibliothek. Ändere sie auf dem Server und aktualisiere die Metadaten der Bibliothek, um die Beschreibungen in einer anderen Sprache zu erhalten.
- Das Entdecken verwendet Frankreich als Region: Trendlisten, Altersfreigaben und die französischen Regale.

## 8. Katalog-Einstellungen

*Einstellungen › Erweiterte Einstellungen › Catalog* legt fest, was die TMDB-Reihen vorschlagen (diese Optionen erscheinen auf Englisch):

- *Oldest titles suggested*: keine Vorschläge, die vor dem gewählten Jahr erschienen sind (1970 bis 2010). Suche und Filmreihen zeigen weiterhin alle Titel.
- *How well known*: *Balanced*, *Discovery: lesser known titles too* (auch weniger bekannte Titel) oder *Big hits only*.
- *Show « Continue watching »* und *Show « Recently added »* blenden diese Reihen auf der Startseite, den Seiten Movies und Series und in den Mediatheken aus.

## 9. Updates

Die APK von der Releases-Seite prüft beim Start auf GitHub, ob es neue Versionen gibt, und zeigt *Update available* an, wenn eine vorhanden ist. Der Bereich *Über diese App* in den *Einstellungen* zeigt ebenfalls, ob ein Update verfügbar ist. Die automatische Prüfung kannst du unter *Einstellungen › Erweiterte Einstellungen › Automatisch auf Aktualisierungen prüfen* ausschalten.

Wenn du eine neue Version über die alte installierst, bleiben deine Einstellungen erhalten, solange beide mit demselben Schlüssel signiert sind. Ein selbst erstellter Build (mit deinem eigenen Schlüssel signiert) lässt sich nicht mit der APK von der Releases-Seite aktualisieren: Deinstalliere ihn zuerst, was die Einstellungen der App löscht.

## 10. Fehlerbehebung

**Keine Entdecken-Reihen, oder "TMDB not configured: add your key in Settings › TMDB & IMDb ratings"**
Füge einen TMDB-Schlüssel hinzu ([Schritt 3](#3-tmdb-schlüssel-hinzufügen)) und starte die App neu.

**Radarr-/Sonarr-Fehler**

| Meldung | Was zu prüfen ist |
|---|---|
| *Radarr is unreachable* | Die Server-URL, und ob der Fernseher diese Adresse erreichen kann |
| *API key refused by Radarr* | Der API-Schlüssel |
| *Access refused by the server in front of Radarr* | Benutzername und Passwort deines Reverse-Proxys |
| *Invalid Radarr settings (URL or key)* | Ein Tippfehler in der URL (zum Beispiel `htts://`) oder im Schlüssel |
| *Not found in Sonarr* | Sonarr hat die Serie nicht gefunden, oft weil TMDB dafür keine TVDB-ID hat |

**Die Fernseher-Tastatur verschluckt Zeichen oder das D-Pad kommt nicht aus einem Textfeld heraus**
Manche Fernseher-Tastaturen verhalten sich so. Tippe langsam, prüfe das Feld vor dem Speichern und bestätige mit der ✓-Taste der Tastatur.

**Der Bildschirmschoner oder der Kanal "Zuletzt hinzugefügt" ist nach einem Update verschwunden**
Wähle den JellyVault-Bildschirmschoner in den Android-TV-Einstellungen erneut aus und erlaube den Kanal erneut in den Kanaleinstellungen des Startbildschirms.

**Logs für einen Fehlerbericht sammeln**
Siehe [DEVELOPMENT.md](../../DEVELOPMENT.md#gathering-app-logs) und [eröffne dann ein Issue](https://github.com/romainio/JellyVault/issues) mit den Logs, der App-Version (*Einstellungen › Installierte Version*) und deinem Fernseher-Modell.
