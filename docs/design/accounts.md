# Accounts and authentication

## Why

Word lists, sign requests and administration belong to people: Signaire needs accounts. Deployments differ: a server
for a school or an association (several users, perhaps an existing identity provider), or the standalone application
on a family computer (no server, perhaps no login at all). Searching and reading signs stays public.

## Design

- **Storage** (`signaire-accounts`): JPA over PostgreSQL on servers, H2 (file, PostgreSQL mode) in the standalone
  application. Flyway owns the schema; Hibernate only validates it. Migrations are in `db/migration/common`, except
  those that differ per database (`db/migration/{postgresql,h2}`). Tables: accounts, word lists and entries, sign
  overrides, sign requests.
- **Services**: `AccountService`, `WordListService`, `SignOverrideService`, `SignRequestService` expose records and
  take the id of the acting account; entities and repositories are package-private (ArchUnit).
- **Local accounts**: Spring Security form login, bcrypt hashes, registration open or closed
  (`signaire.accounts.registration-enabled`).
- **OpenID Connect**: when `spring.security.oauth2.client.registration.*` is configured; `OidcAccounts` creates the
  account on the first login, keyed by issuer and subject.
- **Principal**: controllers get `@AuthenticationPrincipal AccountPrincipal` (`LocalUser` or `OidcAccountUser`).
- **Roles**: `ADMIN` for the usernames in `signaire.accounts.admins` (`AdminUsers`). `/admin/**` needs it;
  `/lists/**`, `/signs/*/lists/**` and `/requests/**` need a login; everything else is public.
- **Standalone**: see [Standalone application](standalone.md) (setup, the single user without login).

## Decisions

- **A separate module**: the CLI needs no database; only the web application depends on accounts.
- **Records out, entities in**: services return records and check ownership with the acting account's id, so a list
  of someone else is "not found" rather than "forbidden" (no information leak), and no lazy-loading leaks into the
  web layer.
- **Flyway, Hibernate validates**: the schema is reviewed SQL, the same on every installation; a migration already
  applied never changes, even when moved between directories.
- **H2 for the standalone application**: no server to install. The service tests run on H2 too (fast, no Docker);
  PostgreSQL and its migrations are tested by `signaire-it`, whose web application runs on it.
- **Administrators by configuration**: a list of usernames, no role management UI; small deployments have one or
  two administrators.
- **OIDC accounts keyed by issuer + subject**, not by email: emails change and aren't unique across providers.

## Open questions

- No password reset by email: there is no mail configuration; administrators of a server reset passwords in the
  database.
