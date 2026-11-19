# Standalone application

## Why

Most learners won't run a server with PostgreSQL. A family or a teacher should be able to download Signaire, start it
with a double click on Linux, Windows or macOS, choose the signs to download, and use it, alone or with a few people
on the same computer, without installing Java or anything else.

## Design

- **The web application in another mode**: `StandaloneApplication` (in signaire-web; the `-standalone.jar`) starts it
  with the `standalone` profile (`application-standalone.properties`): H2 file database and log file in the state
  directory, `127.0.0.1:8180`, `standalone.properties` of the config directory imported (where the setup writes its
  choices). It opens the browser; when the port already answers, it only opens the browser.
- **`Standalone`** (a bean of `SecurityConfiguration`, so that web slices have it) knows the mode and whether the
  setup is done.
- **First run** (`SetupFilter` sends every page to `/setup` until then): alone (no account to create; the single user
  is the administrator) or several people (accounts, the first is the administrator), and the categories, languages
  and media to download from SignSuisse (a [synchronization](signsuisse-sync.md)).
- **Alone**: `SingleUserFilter` makes every request from this computer the single user, without login. Their account
  is an ordinary one, with a Signaire OIDC identity rather than a password. Requests from elsewhere stay anonymous.
- **Quit** (`/admin/quit`): there is no console after a double click.
- **Packaging** (`ci/package-standalone.sh`, run by CI on the three systems): the jar extracted
  (`-Djarmode=tools extract`), jpackage builds an application image with its own Java runtime (jlink, stripped), then
  a training run writes the JVM's AOT cache (`signaire.aot`), added to the launcher's options: the application starts
  in about 3 s instead of 13.
- **Never on servers**: `signaire.standalone.enabled` is false there.

## Decisions

- **The same web application, a profile and a few filters**: one codebase; everything a server has (lists, exports,
  administration) works in the standalone application.
- **Embedded H2 and Lucene**: nothing to install (see [Search](search.md) and [Accounts](accounts.md)).
- **Local only (127.0.0.1)**: a personal application isn't exposed to the network; sharing means accounts on the
  same computer.
- **No login when alone, as an ordinary account**: lists and requests work the same way, and switching to several
  people later keeps the data.
- **jpackage application image**: users need no Java; installers (deb, msi, dmg) can come later from the same image.
- **AOT cache from a training run**: a four times faster start for a double-click application; the JVM ignores a
  cache whose runtime or jars changed, so a stale cache costs nothing but speed.

## Open questions

- Tried on Linux only; Windows and macOS images are built by CI but not yet used by people.
- Installers (Debian package, …) are deferred.
- No signing of the packages (macOS Gatekeeper and Windows SmartScreen will warn).
