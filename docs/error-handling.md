# Error handling

How the app tells users that something failed, and why. Status: proposal, for Markus to decide. Only the first rule is
partly in place (see [Migration](#migration)).

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
| Background failure, stored content on screen | Refresh fails, no connection | Connection icon only |
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

## Web client

The kinds and their places apply to MkDocsEditor-Web too, with the same wording where possible. Texts stay in each
client, because each has its own translation resources.

## Migration

Done:

- `FileBrowserViewModel`, `ItemActions` and `UploadFilesUseCase` no longer show exception text.

To do, in this order:

1. `FileBrowserScreenContent`: replace `ErrorCard` with the "nothing to show" state and a snackbar. A failed refresh with
   a folder on screen only updates the connection state. Then remove `ErrorCard`.
2. `MoveItemUseCase` still passes raw exception text when the backend sent no message.
3. `FileBrowserEvent.Toast` has hardcoded English strings. Move them to `strings.xml`.
4. Go through the other ViewModels (`grep -E "error = |setError\("`, about 50 places) and sort each into the table.
5. Toasts in `BackendConfigEditScreen` and `BackendConfigSelectionScreen`: wait for the
   [backend configuration](../TODO.md#backend-configuration) redesign, which replaces those screens.

## Open questions

- Release plants no Timber tree, so caught failures leave no trace on family devices. Is a small local log, which a user
  can share with the admin, worth having?
- Should a retry happen on its own when the connection comes back, instead of a button?
