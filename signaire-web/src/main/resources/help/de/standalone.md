# Eigenständige Anwendung

Die eigenständige Anwendung führt Signaire auf Ihrem Computer aus, ohne Server: weder Datenbank noch Suchmaschine
sind zu installieren. Starten Sie sie mit einem Doppelklick (`signaire-web-…-standalone.jar` mit Java 27, oder das
Programm Signaire, das sein eigenes Java mitbringt): sie öffnet Signaire in Ihrem Browser unter <http://localhost:8180>. Läuft Signaire bereits,
öffnet ein erneuter Start einfach den Browser.

Das Programm ist nicht signiert: beim ersten Start verlangt macOS, es mit einem Rechtsklick und «Öffnen» zu
öffnen, und Windows, unter «Weitere Informationen» zu bestätigen.

## Erster Start

Beim ersten Start fragt Signaire:

- **wer es benutzt**: nur Sie (kein Konto, kein Passwort: Sie sind auch der Administrator) oder mehrere Personen
  (jede erstellt ihr Konto; das erste, sofort erstellte, ist das des Administrators);
- **die von SignSuisse herunterzuladenden Gebärden**: wählen Sie einige Kategorien und Sprachen. Das Herunterladen
  dauert lange und läuft weiter, während Sie Signaire benutzen; den Fortschritt sehen Sie in der Administration, wo
  Sie später auch weitere Kategorien herunterladen können.

Nur dieser Computer kann Signaire benutzen: es ist vom Netzwerk aus nicht erreichbar.

## Beenden

«Signaire beenden», oben auf der Seite, beendet die Anwendung (für den Administrator). Den Browser zu schliessen
genügt nicht.

## Wo die Dateien liegen

Unter Linux liegen die Bundles in `~/.local/share/signaire/bundles`, die Datenbank und der Suchindex in
`~/.local/state/signaire` und die Einstellungen des ersten Starts in `~/.config/signaire/standalone.properties` (die
üblichen Ordner unter macOS und Windows). Löschen Sie diese letzte Datei, um die Einstellungen des ersten Starts neu
zu wählen.
