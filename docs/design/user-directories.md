# User directories

## Why

Bundles, media, the index, the standalone database and configuration need a place on the user's computer. Writing
them next to the jar or in the current directory breaks as soon as Signaire is started from elsewhere (a double
click, a menu); each system has conventions for where applications keep their files.

## Design

`UserDirectories` (core) gives four directories:

| Kind | Linux (XDG) | macOS | Windows | Holds |
|---|---|---|---|---|
| data | `$XDG_DATA_HOME/signaire` (`~/.local/share/signaire`) | `~/Library/Application Support/Signaire` | `%LOCALAPPDATA%\Signaire` | bundles |
| cache | `$XDG_CACHE_HOME/signaire` (`~/.cache/signaire`) | `~/Library/Caches/Signaire` | `%LOCALAPPDATA%\Signaire\cache` | media cache |
| state | `$XDG_STATE_HOME/signaire` (`~/.local/state/signaire`) | `~/Library/Application Support/Signaire` | `%LOCALAPPDATA%\Signaire` | index, standalone database and log |
| config | `$XDG_CONFIG_HOME/signaire` (`~/.config/signaire`) | `~/Library/Application Support/Signaire` | `%APPDATA%\Signaire` | `application.properties`, `standalone.properties` |

Spring applications get them through `UserDirectoriesEnvironment` (registered in `META-INF/spring.factories`), as
Spring Boot default properties: `signaire.bundles.directory`, `signaire.media.cache`, `signaire.index.directory`,
`signaire.state.directory`, `signaire.config.directory`; `$XDG_CONFIG_HOME/signaire/application.properties` is read.
The CLI's options get them through `EnvironmentDefaults`.

## Decisions

- **XDG on Linux, native conventions elsewhere**: what users and backup tools expect; only absolute `$XDG_*_HOME`
  values count, as the specification says.
- **Default properties, not overrides**: any setting (command line, environment, file) wins, so servers keep their
  own paths.
- **Data, cache and state apart**: bundles are worth backing up, the media cache can be deleted and downloaded again,
  the index is rebuilt from bundles.
- **Tests never touch the user's home**: they set every directory under `target/`.

## Open questions

- None known.
