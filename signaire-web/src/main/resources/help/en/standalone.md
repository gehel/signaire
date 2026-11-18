# Standalone application

The standalone application runs Signaire on your computer, without a server: no database nor search engine to
install. Start it with a double click (`signaire-web-…-standalone.jar` with Java 27, or the Signaire program, which brings its own Java): it opens
Signaire in your browser, at <http://localhost:8180>. If Signaire already runs, starting it again just opens the
browser.

The program isn't signed: on its first run, macOS asks to open it with a right click then "Open", and Windows to
confirm under "More info".

## First run

On its first run, Signaire asks:

- **who uses it**: only you (no account nor password: you are also the administrator) or several people (each one
  creates an account; the first one, created right away, is the administrator's);
- **the signs to download** from SignSuisse: choose a few categories and languages. Downloading takes long and goes
  on while you use Signaire; you follow it in the administration, where you can also download other categories
  later.

Only this computer can use Signaire: it can't be reached from the network.

## Quitting

"Quit Signaire", at the top of the page, stops the application (for the administrator). Closing the browser isn't
enough.

## Where the files are

On Linux, the bundles are in `~/.local/share/signaire/bundles`, the database and the search index in
`~/.local/state/signaire`, and the choices of the first run in `~/.config/signaire/standalone.properties` (the usual
folders on macOS and Windows). Delete this last file to make the choices of the first run again.
