# Verwaltung

Verwalter sind die in der Konfiguration genannten Benutzer (`signaire.accounts.admins`). Sie sehen «Verwaltung» in
der Kopfzeile.

## Gebärden ändern

- **Neue Gebärde**: um eine Gebärde hinzuzufügen, die SignSuisse nicht hat.
- Auf der Seite einer Gebärde: **Gebärde bearbeiten** und **Gebärde löschen**.
- **Geänderte Gebärden** listet die Änderungen; «Rückgängig machen» stellt die Gebärde von SignSuisse wieder her.

Die Gebärdendaten können jederzeit neu geladen werden: Die Änderungen der Verwaltung werden separat aufbewahrt und
nach jedem Laden erneut angewendet.

## Bundles

Die Gebärden kommen in **Bundles**, einer `.zip`-Datei pro SignSuisse-Kategorie. Die Seite **Bundles** listet sie
auf, nimmt ein neues an (es ersetzt jenes derselben Kategorie), lädt neue oder geänderte Bundles oder indexiert alles
neu (nach einem Signaire-Update, das den Index ändert).

## Synchronisation mit SignSuisse

Wählen Sie Kategorien und Gebärdensprachen (keine gewählt: alle) und die herunterzuladenden Medien: Videos und Bilder,
nur Videos, nur Bilder oder keine. Nicht heruntergeladene Medien werden von SignSuisse angezeigt, fehlen aber in den
Exporten und ohne Verbindung. Die Synchronisation läuft im Hintergrund; jede Kategorie wird geladen, sobald sie bereit
ist. Ohne Medien dauert es einige Minuten pro Kategorie, mit Medien viel länger. SignSuisse liefert nur 100 Ergebnisse
pro Suche: alle Gebärden einer Kategorie zu finden braucht Hunderte von Suchen, mit Pausen, um die Website zu schonen.
Die Seite zeigt ihre Anzahl; vor dem Ende dieses Schritts wird keine Gebärde extrahiert.

## Kontakt für SignSuisse

Jede Anfrage an SignSuisse sagt, dass sie von Signaire kommt, mit einem Link zum Projekt. Fügen Sie Ihre
E-Mail-Adresse hinzu: Belastet Signaire die Website zu stark, kann SignSuisse Ihnen schreiben, statt Ihre Downloads
zu sperren. Tragen Sie sie in die Datei `application.properties` des Konfigurationsverzeichnisses von Signaire ein
(`~/.config/signaire/` unter Linux), die auch die Befehlszeile liest:

```properties
signaire.signsuisse.contact=sie@beispiel.org
```

**Datenschutz**: Die Adresse wird mit jeder Anfrage an SignSuisse gesendet, im Klartext im Header `User-Agent`.
SignSuisse (der Schweizerische Gehörlosenbund) und sein Hoster sehen sie in ihren Protokollen und bewahren sie nach
ihren Regeln auf; wer diese Anfragen sieht, kann sie ebenfalls lesen. Verwenden Sie eine Adresse, die Sie teilen
möchten, zum Beispiel eine eigene dafür. `signaire.signsuisse.user-agent` ersetzt den ganzen Header.

## Gebärdenanfragen

Für jede offene Anfrage: **Auf SignSuisse suchen** und die richtige Gebärde **importieren** (oder sie **verknüpfen**,
wenn sie schon im Lexikon ist), die **Gebärde selbst erstellen** oder die Anfrage **ablehnen**. Der Benutzer sieht
das Ergebnis in seinen Anfragen.
