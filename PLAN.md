# Scrollwave — Umsetzungsplan

## Ziel

Eine persönliche native Android-App, die eigene Reddit-Custom-Feeds und abonnierte Subreddits als vertikal einrastenden Fullscreen-Medienfeed darstellt. Genau ein sichtbares Video läuft mit Ton; Textbeiträge und nicht unterstützte Medien werden übersprungen.

## Aktueller Stand

- Phasen 1 bis 4 sind implementiert.
- Unit-Tests, Android-Lint und Debug-APK-Build laufen erfolgreich.
- Die APK wurde in einem Android-36-Emulator aus Android Studio installiert und bis zum OAuth-Einstieg getestet; Start, Portrait-Layout, deaktivierter Login ohne Client-ID und fehlerhafte OAuth-Callbacks verhalten sich stabil.
- Die reale Reddit-Anmeldung sowie Feed-, Scroll-, Ton- und Seek-Tests mit echten Inhalten warten auf eine konfigurierte `REDDIT_CLIENT_ID` und den Login des Nutzers.
- Der Reddit-Data-API-Antrag wurde am 30. August 2026 mit dem öffentlichen Quellcode eingereicht; die Freigabe steht noch aus.
- Als Zwischenlösung ist ein experimenteller, rein lokaler Webmodus implementiert und ausgeloggt im Android-36-Emulator geprüft: Reddit selbst rendert und lädt die Seite; die App filtert auf Medienbeiträge und ergänzt Fullscreen-Snapping sowie die Steuerung sichtbarer HTML5-Videos. Reddit-Werbung bleibt gemäß Plattformregel sichtbar. Es gibt keinen Proxy und keine separate Datensammlung. Login und echte Feed-/Ton-Tests stehen noch aus.

## Festgelegter Umfang

- Android-App `Scrollwave`, Paket `de.laurenz.scrollwave`, Hochformat, Installation als APK.
- Einmaliger Reddit-OAuth-Login im Systembrowser mit ausschließlich `read` und `mysubreddits`.
- Auswahl zwischen eigenen Custom Feeds und einzelnen abonnierten Subreddits.
- Sortierungen `Hot`, `New` und `Top` mit Stunde, Tag, Woche, Monat, Jahr und Gesamt; Auswahl wird je Feed lokal gespeichert.
- Reddit sortiert zuerst, danach filtert die App nicht unterstützte Beiträge.
- Unterstützte Medien:
  - Reddit-Bilder und -Galerien
  - animierte GIFs
  - Reddit-Videos (`v.redd.it`) über HLS/DASH mit Ton
  - Redgifs-Clips und -Galerien mit Ton
  - direkte Bild-, MP4- und HLS-Links
  - Medien aus Crossposts
- Externe eingebettete Webseiten wie YouTube, Vimeo oder Streamable werden übersprungen.
- NSFW-Inhalte ohne zusätzliche Warnung; Reddit-Kontoeinstellungen bleiben maßgeblich.
- Vertikaler Fullscreen-Pager, Medien proportional auf schwarzem Hintergrund, kein Strecken oder Beschneiden.
- Bilder bleiben bis zum Wischen stehen; Galerien wechseln horizontal.
- Sichtbares Video startet mit Ton, vorheriges pausiert, Videos laufen in Schleife.
- Tap pausiert/startet; Wischgesten dürfen kein Tap auslösen.
- Wiedergabepositionen bleiben während der Sitzung erhalten; Einstellung `Fortsetzen` oder `Neu starten`.
- TikTok-artiger, ziehbarer Fortschrittsbalken bündig am unteren Rand mit größerer unsichtbarer Touchfläche.
- Overlay: ausreichend große Feed-, Sortier- und Aktualisieren-Bedienung sowie Reddit-Nutzername und relative Beitragszeit.
- Qualitätswahl `Auto` oder `Hoch`; aktuelles und nächstes Medium werden vorgeladen.
- Duplikate werden innerhalb einer Sitzung anhand Medien-URL beziehungsweise Redgifs-ID entfernt.
- Maximal fünf Reddit-Seiten werden automatisch nach passenden Medien durchsucht.
- Abmelden entfernt OAuth-Token und lokalen Sitzungszustand.

## Technische Leitplanken

- Kotlin, Jetpack Compose und Media3 ExoPlayer.
- Android Custom Tabs/App Link für OAuth; Refresh-Token verschlüsselt im Android Keystore.
- Reddit Data API direkt aus der App, kein eigener Server.
- Kleine, konkrete Struktur ohne spekulative Abstraktionen:
  - `MainActivity` für App- und OAuth-Einstieg
  - `MainViewModel` für Feed-Zustand und Paging
  - `RedditClient` für OAuth und Listings
  - `MediaResolver` für Reddit-, Redgifs- und Direktmedien
  - Compose-Screens für Login, Auswahl und Feed
- Netzwerkzugriffe mit OkHttp; JSON mit `org.json`, um ein zusätzliches Serialisierungs-Framework zu vermeiden.
- Persistenz mit `SharedPreferences`; keine Datenbank für den V1-Sitzungszustand.

## Phasen

### 1. Projekt und Konfiguration

- Gradle-/Android-Projekt anlegen.
- `REDDIT_CLIENT_ID` aus nicht versionierter lokaler Konfiguration in `BuildConfig` übernehmen.
- Manifest, Internetzugriff, OAuth-Redirect und Portrait-Modus konfigurieren.
- README mit Reddit-App-Registrierung, Build und APK-Installation ergänzen.

### 2. OAuth und Reddit-Daten

- Authorization-Code-Flow für installierte Apps implementieren.
- State validieren, Token sicher speichern und erneuern.
- Eigene Multireddits und abonnierte Subreddits laden.
- Listings mit Sortierung, Zeitfenster und `after`-Pagination abrufen.
- Authentifizierungs- und Parserlogik mit fokussierten Unit-Tests absichern.

### 3. Medienauflösung

- Reddit-Bilder, Galerien, GIFs, HLS/DASH-Videos und Crossposts auflösen.
- Redgifs-ID erkennen, temporäres Token abrufen und Clip/Galerie auflösen.
- Direkte Medienlinks erkennen.
- Nicht unterstützte oder defekte Medien überspringen, Duplikate entfernen.
- Resolver mit repräsentativen JSON-Fixtures testen.

### 4. Fullscreen-Feed

- Vertikal einrastenden Compose-Pager umsetzen.
- Horizontale Galerien, proportionale Bilder und Media3-Player integrieren.
- Nur aktives Video abspielen; Audio-Fokus, Loop, Pause/Resume und Sitzungspositionen verwalten.
- Fortschrittsbalken bündig am unteren Rand mit sicherer Gestenerkennung umsetzen.
- Feed-/Sortier-/Refresh-Overlay, relative Zeit, Qualität und Fehlzustände ergänzen.

### 5. Verifikation und Übergabe

- Unit-Tests und Lint ausführen.
- Debug-APK bauen.
- Falls ein Gerät per ADB verbunden ist: installieren und OAuth-, Feed-, Scroll-, Ton- und Seek-Fluss prüfen.
- Bekannte API-Risiken dokumentieren.

## Abnahmekriterien

1. Login führt zurück in die App und überlebt einen Neustart.
2. Eigene Custom Feeds und abonnierte Subreddits erscheinen vollständig paginiert.
3. `Hot`, `New` und alle vereinbarten `Top`-Zeiträume liefern die Reddit-Reihenfolge, gefiltert auf unterstützte Medien.
4. Jeder vertikale Swipe landet exakt auf einem Fullscreen-Beitrag.
5. Höchstens ein Video spielt; das sichtbare startet mit Ton und pausiert per Tap.
6. Weiterwischen pausiert das vorherige Video; Zurückwischen folgt der gewählten Resume-Einstellung.
7. Reddit- und Redgifs-Videos spielen Bild und Ton; Bilder und Galerien bleiben unverzerrt.
8. Der Fortschrittsbalken liegt bündig am unteren Rand, ist bedienbar und verursacht keine versehentlichen Pausen beim Wischen.
9. Nicht unterstützte, doppelte oder defekte Medien blockieren den Feed nicht.
10. Eine installierbare Debug-APK wird erfolgreich erzeugt.

## Bekannte Risiken

- Reddit kann Data-API-Zugang, Freigabe und Rate Limits ändern.
- Redgifs' temporäre Token-Schnittstelle ist weniger stabil dokumentiert und kann sich ändern.
- Gelöschte, quarantänisierte oder durch Kontoeinstellungen verborgene Inhalte können trotz Feed-Mitgliedschaft fehlen.
- Exakte Metadatenformen variieren bei älteren Reddit-Posts und Crossposts; Resolver-Fallbacks müssen defensiv bleiben.
