# Error handling

How the app tells users that something failed, and why. Status: accepted. The screens follow it (see
[Migration](#migration)).

## Rules

1. **Say what happened and what to do, never the exception.** Exception texts are for developers and aren't translated.
   `Throwable.userMessage()` turns a failure into a string resource, and the exception goes to the log.
2. **Content comes first.** The wiki is meant to be looked up, also offline. A failure never hides or covers something
   that can still be read.
3. **The kind of failure picks the place.** The table below decides, not the screen that happens to catch it.
4. **The less a user can do about it, the quieter it is.** A failed background refresh with a stored copy on screen
   doesn't need attention.
5. **Unsaved work is the exception to quiet.** Saying that edits are stored only on this device is worth interrupting for.

## Where a failure shows

| Kind | Example | Place |
|---|---|---|
| Background failure, stored content on screen | Automatic load fails, no connection | Connection icon where the screen has one, otherwise nothing |
| Refresh the user asked for fails, stored content on screen | Pull to refresh | Snackbar, with retry |
| Nothing to show | Folder without a stored copy, server unreachable | In place of the content, with a retry button |
| Action failed | Download, delete | Snackbar, with retry if a retry can work |
| Action inside a dialog failed | Move, backlinks | Inline in the dialog, which stays open |
| Input is not valid | Name already exists | Below the field |
| Lasting state the user has to act on | Sign-in needed, read-only, conflict | Banner on the screen (`EditorBanners`, `SignInRequiredBanner`) |
| Only the admin can fix it | Server too old for a feature | Explain it where the feature would be, no error style |

Only the snackbar and the banner are new to a screen's layout. Both sit inside the scaffold, so they stay clear of the
system bars without extra work.

### Why not

- **A floating error card (`ErrorCard`).** It covers the content and has to be dismissed, even when the content below is
  fine. Its message and retry split into the "nothing to show" and snackbar cases above.
- **Toast for failures.** It can't offer a retry, disappears on its own, and Android may limit it. Toast stays fine for a
  short confirmation that has no scaffold nearby ("Link copied").
- **Dialogs for failures.** They block the screen for something the user often can't change.

## Unreachable server and long actions

There is no offline mode to switch on. Editing works without a connection, and the app never makes the user wait for a
server that doesn't answer. `RequestKind` in `RequestManager` holds the timeouts:

| Kind | For | Gives up after |
|---|---|---|
| Quick | Reads a screen shows or that run in the background: tree, page, dashboard, pins, feed, server info, lookups | 3 s for the connection, 3 s for the answer |
| Long action | Anything that changes the wiki: move and its dry run, rename, delete, create, pin | 3 s for the connection, 10 minutes for the answer |
| Transfer | Uploads and downloads | 3 s for the connection, 60 s without data |

- A real error fails at once with every kind: an HTTP error, a refused or reset connection, no network, a server that
  doesn't take the connection.
- A quick read that times out counts as an unreachable server. The stored copy stays, and the next try comes later. The
  sync connection keeps trying in the background, with backoff.
- A long action never fails for taking long. Its dialog shows that it is working, and after 5 s it says that the server
  is still working on it. Closing the dialog doesn't stop the server: the end still shows, as a message, and a dialog
  opened in the meantime stays open.
- A transfer shows the bytes so far of the whole file, and can be cancelled. It only times out when no data moves.

## Web client

The kinds and their places apply to MkDocsEditor-Web too, with the same wording where possible. Texts stay in each
client, because each has its own translation resources.

## Migration

Done:

- The file browser: `ErrorCard` is gone, and its failures follow the table. Its hints and confirmations are in
  `strings.xml`.
- `UploadFilesUseCase` and `MoveItemUseCase` no longer show exception text.
- The server screens: no toasts, and a snackbar for what fails or finishes.
- The auth screens: failures show inline, also in dialogs, and `toAuthMessage` no longer shows exception text. Toasts
  there are confirmations or hints before a screen changes.
- The editor: a search of its code finds no exception text in `SnackbarData` or `EditorBanners`. Not tried on a device.

Nothing is left to migrate.

## Open questions

- Release plants no Timber tree, so caught failures leave no trace on family devices. Is a small local log, which a user
  can share with the admin, worth having?
- Should a retry happen on its own when the connection comes back, instead of a button?
