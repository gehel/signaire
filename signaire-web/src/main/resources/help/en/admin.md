# Administration

Administrators are the users named in the configuration (`signaire.accounts.admins`). They see "Administration" in
the header.

## Changing signs

- **New sign**: to add a sign that SignSuisse doesn't have.
- On the page of a sign: **Edit the sign** and **Delete the sign**.
- **Changed signs** lists the changes; "Undo" brings back the sign of SignSuisse.

The data of the signs can be reloaded at any time: the changes of administrators are kept apart and applied again
after each load.

## Bundles

Signs come in **bundles**, a `.zip` file per SignSuisse category. The **Bundles** page lists them, accepts a new one
(it replaces the bundle of the same category), loads the new or changed bundles, or reindexes everything (after an
update of Signaire that changes the index).

## Synchronization with SignSuisse

Choose categories and sign languages (none checked: all), and the media to download: videos and images, videos only,
images only, or none. Media not downloaded are shown from SignSuisse, but are missing from exports and without a
connection. The synchronization runs in the background; each category is loaded as soon as it is ready.

Signaire spares SignSuisse: requests are at least half a second apart, and signs already extracted are reused from the
existing bundles rather than read again; check **Read the signs already extracted again** to get the corrections made
on SignSuisse since. The signs found are read, and their media downloaded, while the search goes on. The page shows
where the current category is (searches, signs, media: done, left, failed), what is being done right now, an estimate
of the time left, and the categories left. Without media, a category takes from a few minutes to half an hour; with
the media, much longer. A media that fails is still shown from SignSuisse; the next synchronization tries it again.

## Contact for SignSuisse

Every request to SignSuisse says that it comes from Signaire, with a link to the project. Add your email address: if
Signaire puts too much load on its site, SignSuisse can write to you rather than block your downloads. Write it in the
`application.properties` file of the Signaire configuration directory (`~/.config/signaire/` on Linux), which the
command line reads too:

```properties
signaire.signsuisse.contact=you@example.org
```

**Privacy**: the address is sent to SignSuisse with every request, in clear in the `User-Agent` header. SignSuisse
(the Swiss Federation of the Deaf) and its host will see it in their logs and keep it according to their rules;
anyone who sees these requests go by can read it too. Use an address you are willing to share, a dedicated one for
example. `signaire.signsuisse.user-agent` replaces the whole header.

## Sign requests

For each open request: **Search SignSuisse** then **Import** the right sign (or **link** it if it is already in the
lexicon), **Create the sign** yourself, or **Reject** the request. The user sees the outcome in their requests.
