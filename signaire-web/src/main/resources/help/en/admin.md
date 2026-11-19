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

Choose categories and sign languages (none checked: all), and whether to download the videos and images. The
synchronization runs in the background; each category is loaded as soon as it is ready. Without media, count a few
minutes per category; with the media, much longer. SignSuisse gives only 100 results per search: finding all the
signs of a category takes hundreds of searches, spaced out to spare the site. The page shows how many; no sign is
extracted before the end of this step.

## Sign requests

For each open request: **Search SignSuisse** then **Import** the right sign (or **link** it if it is already in the
lexicon), **Create the sign** yourself, or **Reject** the request. The user sees the outcome in their requests.
