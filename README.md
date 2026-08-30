# Scrollwave

Persönliche Android-App für Reddit-Custom-Feeds und abonnierte Subreddits als Fullscreen-Medienfeed. Die vollständige Produktspezifikation und die Abnahmekriterien stehen in [PLAN.md](PLAN.md).

## Voraussetzung: Reddit-Zugang

Scrollwave verwendet ausschließlich Reddit OAuth mit den Leserechten `read` und `mysubreddits`. Es gibt keinen Server und kein Client-Secret in der App.

1. Beantrage beziehungsweise registriere deinen nicht-kommerziellen Data-API-Zugang bei Reddit:
   - <https://support.reddithelp.com/hc/en-us/articles/14945211791892-Developer-Platform-Accessing-Reddit-Data>
   - <https://www.reddit.com/prefs/apps>
2. Lege eine App vom Typ **installed app** an.
3. Trage als Redirect URI exakt `scrollwave://oauth` ein.
4. Kopiere die Client-ID, die unter dem App-Namen angezeigt wird.
5. Lege im Projektstamm eine nicht versionierte `local.properties` an:

```properties
sdk.dir=/Users/DEIN_NAME/Library/Android/sdk
REDDIT_CLIENT_ID=DEINE_CLIENT_ID
```

Alternativ kann `REDDIT_CLIENT_ID` als Umgebungsvariable gesetzt werden.

## Bauen und installieren

```sh
./gradlew testDebugUnitTest assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Die Debug-APK liegt danach unter `app/build/outputs/apk/debug/app-debug.apk`.

## Bedienung

- Einmal mit Reddit verbinden und den reinen Lesezugriff erlauben.
- Eigenen Custom Feed oder ein abonniertes Subreddit auswählen.
- Vertikal zum nächsten Beitrag, horizontal durch Galerien wischen.
- Tippen pausiert oder startet ein Video.
- Der Fortschrittsbalken liegt bündig am unteren Rand und kann gezogen werden.
- Feed, Sortierung, Qualität und Resume-Verhalten liegen in den oberen Bedienelementen.

## Bewusste V1-Grenzen

- Unterstützt werden Reddit-Bilder/-Galerien/-Videos, Redgifs und direkte Medienlinks.
- Eingebettete Webseiten wie YouTube, Vimeo oder Streamable werden übersprungen.
- Höchstens fünf Reddit-Seiten werden pro Feed-Lauf nach passenden Medien durchsucht.
- Redgifs' temporäre Token-Schnittstelle und Reddit-API-Zugangsregeln können sich ändern.
