# TODO

The notes under "Where to start" were checked against the code on 2026-11-04 (master `553eec9`), so
re-check them before relying on them. Anything marked "Idea" is a suggestion, not a decision.

**New features have to work in MkDocsEditor-Web as well.** Shared data and logic belong on the backend, so that both
clients show the same thing.

| Area | Entry | Kind of work | Needs another repo |
|---|---|---|---|
| Editor | [Opening a document when the backend is unavailable](#opening-a-document-when-the-backend-is-unavailable) | Bug and small feature | No |
| Editor | [Author highlighting](#author-highlighting) | Feature | Probably KodeEditor |
| File browser | [Entries lack context](#file-browser-entries-lack-context) | Design and UI | Maybe the backend |
| File browser | [Transitions between sections](#transitions-between-sections) | Design and UI | No |
| Staying in use | [What's going on page, pinned pages and notifications](#whats-going-on-page-pinned-pages-and-notifications) | Feature | Yes, the backend and the web client |
| App-wide | [Error handling](#error-handling) | Decide on the spec, then UI | No |
| App-wide | [Settings](#settings) | Audit, then UI | Maybe KutePreferences |
| Accounts | [Backend configuration](#backend-configuration) | Redesign and refactor | No |
| Accounts | [Onboarding](#onboarding) | Feature | No |

Order that fits how they depend on each other (Idea):

- Settle the error handling spec first: the other entries will need to show errors.
- Do backend configuration before onboarding: onboarding leads into those screens.
- Do settings after author highlighting: that adds a setting.

---

# Editor

## Opening a document when the backend is unavailable

**What I want**

- Today a loading bar shows for a very long time. That is irritating, because the document can still be read
  when an offline copy is available.
- Showing the connection state is good, but it should be less distracting, at least when we already have a
  relatively up-to-date local copy to show and work with.
- Maybe also warn or inform the user when a document has been out of date for an unusually long time. That may
  indicate a problem that could be reported to the admin.

**Where to start**

- The bar is `LOADING_INDICATOR_TAG` in `CodeEditorScreenContent`, driven by `DocumentSession.State.loading`.
- #121 hides the bar while a local copy is showing (`DocumentSession.showsLoading`). `State.loading` itself is unchanged,
  and without a local copy the bar can still last long.
- `WebsocketConnectionHandler` now gives up on an upgrade that never gets an answer after 15 s (a proxy without a
  backend, for example). Not tried against a real proxy. Whether no network or a blocked route also wait long is still
  to check.

**Out-of-date warning**

- A local copy doesn't record when it last synced: `StoredDocument` and `StoredDocumentJson` have no timestamp. It
  needs a new optional field, because existing files don't have one.
- `serverHeads` against `heads` already tells whether a copy has unsynced edits, which is a different question.

**Open questions**

- What counts as "relatively up to date", and what is "unusually long"?
- What does "reported to the admin" mean? Just a hint to the user, or something sent to the server?

## Author highlighting

**What I want**

CodiMD can show who wrote which character, per line and within a line. I want the same, fitting our current UI and
what KodeEditor offers right now. It should be possible to turn it on and off, because it might be overwhelming if it
is on permanently. Three modes:

- a colored vertical strip for each line in the editor gutter
- that strip plus a slight background highlight, like a marker, on the text itself
- nothing

**Open questions**

- Which mode is the default? I'm leaning towards the most verbose one: CodiMD uses it, and it gives the most value
  for a use case like mine.
- Where is the toggle: in the editor, in the settings, or both?
- A strip for a line with several authors: split it, or show one color?

**Where to start**

- The backend has it: `GET /document/{id}/authorship/` returns `heads` and `runs` (`start`, `end`, `actorId`,
  `username`). See `docs/sync-protocol.md` section 7 in MkDocsEditor-Backend.
- **`start` and `end` are Unicode code points**, while the editor works in UTF-16, so they need converting. Emojis
  would shift everything otherwise.
- The app doesn't use this yet. `DocumentSyncSession` and the protocol messages (`ActorInfo`, `ActorMessage`) already
  carry the actor to user bindings. The protocol docs say to key per-user data on `accountId`.
- The server only sends the state at open. The client must then update the runs itself from local and remote changes,
  and fetch them again after a reconnect or when they get out of step.
- The first request replays the whole history, about 1.5 s for 3000 changes, so it must not block opening a document.
- Decide what offline shows, since the endpoint needs a connection. Nothing stores the runs today.
- The web client already does this: `src/editor/authorship.ts`, `src/sync/session.ts` and `DocumentView.vue` in
  MkDocsEditor-Web. It uses a background at 14 % alpha and a 2 px underline at 60 %.
- Colors: reuse `RemotePeerColor.kt`, so an author has the same color as their cursor.

**KodeEditor**

- Strip: `KodeEditorGutterColumn` and the `gutterColumns` parameter. The column draws per line, so check how it looks
  with wrapped lines (`lineWrapping = true`).
- Marker on text: `KodeEditorLineDecoration` only covers whole lines, so it doesn't fit per-character ranges. The
  `overlay` parameter is what `RemotePeersOverlay` uses and may work. Otherwise KodeEditor needs a new API.
- Only Markus merges in KodeEditor.
- Read the "Editor and document" rules in `AGENTS.md`: the runs have to move within the call that makes the edit, and
  must not come from comparing texts.

---

# File browser

## File browser entries lack context

**What I want**

- There is not much information to decide which folder might be interesting and why. I like how minimalistic it is,
  but the lack of context feels like bad UX.
- All the text is the same green, and the numbers are green too. That feels like bad UI.

**Where to start**

- `listentry/SectionListEntry.kt`, `DocumentListEntry.kt` and `ResourceListEntry.kt` in `feature/filebrowser/ui/compose`.
- Names use `onSurface`, and everything below them `onSurfaceVariant`.
- A folder shows its name, counts and last change in its subtree (`SectionData.lastChange`). A document shows its name,
  size and last change. Changes read as relative time, and aren't shown after a year (`LastModifiedDate.kt`).
- The API also has `path` and `index` on sections, plus `mimeType` and `thumbnail` on resources. I didn't check which
  of these the app already stores.
- Tests: `DocumentListEntryTest`, `FolderScrollPositionTest`.

**Open questions**

- Does the last changed date give enough context? Further ideas: who changed it, the first line of its index page, names
  of the newest documents. These may need backend or data work.

## Transitions between sections

**What I want**

We have no transitions when entering or exiting a section in the file browser, which looks a bit jarring. I'm not sure
which direction to take the animations. Maybe there are widely used patterns we can adopt.

**State**

Folder changes now use a shared axis on X with a fade, in `FileBrowserScreenContent`. A slow folder animates only when
its content has loaded. The path chips fade in and out with the same timing (`SectionPath`).

**Open questions**

- Should the back gesture preview the parent folder (predictive back)? That needs a `PredictiveBackHandler` and the
  parent's content on hand, so it's more work.

---

# Staying in use

## What's going on page, pinned pages and notifications

**What I want**

- It's still hard for users to keep using the app. Not because it's bad, but because it's just there and rarely needed.
  It's meant to be the one central place for everything relevant to many people in our home: both a way to communicate
  information, and mainly a store that can be looked up easily and quickly at any time, even offline.
- Notifications when someone changed something in a document. A quick summary of the change would be nice, but I'm not
  sure how easy that is with current Android, devices and local AI.
- A "What's going on" page, which might be the default instead of the full file list. It lets a user quickly see what
  has happened on the wiki, and where to find often referenced information.
- Its changes come from all over the document tree, aggregated and sorted by time. It highlights the important bits: who
  did what, when and where. Visually pleasing, but mostly practical and fast.
- Pinned pages, for the information people look up again and again.
- A little gamification here and there wouldn't hurt, but it might be hard to fit into an app like this.
- All of this has to work in the web client as well.

**Suggested approach (Idea)**

Build it in this order, because each step uses the one before:

1. Pinned pages, on the backend and in both clients. They are useful on their own and don't depend on the feed.
2. A feed of changes on the backend.
3. The "What's going on" page in both clients, showing pinned pages and the feed.
4. Make it the default page, once it's good enough to deserve that.
5. Notifications, which are "there are new entries in the feed".
6. Better summaries, if the simple ones aren't enough.

**The page**

- App: the start tab comes from `GetInitialTabUseCase` (`NavItem.FileBrowser` today), and the bottom bar from
  `MainViewModel`. A new `NavItem` and a new `feature/` folder would hold the page.
- Web: `/` is `SectionView` (`src/router/index.ts`), so the default page changes there too.
- It must open instantly and work offline: show what is stored, and update it in the background.
- The file list stays one tap away. Some people will want it as their default, so that may be a setting.

**Pinned pages**

- Store pins on the backend, so they are the same in the app and the web client.
- Two kinds, decided: pins for the whole family, which parents set, and personal pins.
- A pinned page must be available offline, so the app should keep a local copy of it.
- Pins must follow a document that moves. Ids change on a move, see `LocalCopyRedirects`.

**Gamification (Idea)**

Keep it small, and useful for the wiki. A home wiki has no need for points or rankings between family members.

- "3 new changes since your last visit", which needs read state.
- "This page hasn't changed in 2 years. Is it still right?" This fits best, because it keeps the wiki correct and gives
  people a reason to edit.
- A short thank-you or a small monthly recap: "12 pages were improved this month".

**Where to start**

- Backend, `docs/sync-protocol.md` section 11: history exists per document only, based on git, and needs `git.enabled`.
  - `GET /document/{id}/history/` returns revision, time, author, co-authors, message and path.
  - `GET /document/{id}/history/diff/?from=&to=` returns a unified diff.
- **A feed across all documents doesn't exist**, so it needs a new backend route. Asking every document on each refresh
  would repeat the problem of the per-copy moved lookups.
- Commits make a natural unit for a change: section 10 of the same doc commits after `git.commitAfterIdle` (10 minutes
  without a change). So one editing session is one entry rather than one per key press, but a notification comes
  that much later.
- `GET /events/ws/` pushes changes for the file browser, but only while the app has it open. I didn't check whether it
  could also serve the "What's going on" page live.
- The app has no notification code yet: no `POST_NOTIFICATIONS` permission and no `NotificationChannel`. There is no
  Firebase either.
- Background work already uses WorkManager (`ScheduleOfflineCacheUpdateUseCase`, `UnsyncedChangesScheduler`).
- Web client: `DocumentView` and its history are the place to look for what can be reused. I didn't check this in detail.

**Getting notifications to the phone**

| Way | For | Against |
|---|---|---|
| Periodic WorkManager check | No new infrastructure, works with a self-hosted server | At best every 15 minutes, and Android may delay it |
| UnifiedPush or ntfy | Fast, no Google | Family devices need a distributor app, the backend has to send pushes |
| Firebase Cloud Messaging | Fast and reliable | Adds Google and a Firebase project to an open source, self-hosted app, and the backend has to send pushes |

Idea: start with the periodic check. For a home wiki, a delay of 15 minutes or more is fine.

**Summaries**

- Idea: start without AI. From the diff we can build something like "Anna added 3 lines under 'Waste collection' in
  Household". The headings give the context. That is exact, fast, works offline and works on every device.
- On-device AI: as far as I know, models like Gemini Nano only run on some newer phones, so family devices would get
  different results. Check the current state before deciding.
- AI could also get a change wrong, which is bad in an app people trust for facts.
- A model on the server would work for all devices, but adds a dependency and sends private text to it unless it's
  self-hosted.

**Avoiding annoying notifications**

If notifications get annoying, people turn them off, and the goal is lost.

- Never notify about the user's own changes. Compare `accountId`.
- Group several changes into one notification.
- Decide on per-folder or per-document following, and a setting for how often. See [Settings](#settings).
- Pinned pages could be the default for what notifies.
- Tapping a notification opens the document, ideally at the change.

**Open questions**

- What counts as an important change? Is fixing a typo worth a notification?
- Does the feed also cover new, moved and deleted documents, and resources? Git sees these too.
- What about edits that aren't from an account (`:disk` and `:server` in authorship)?
- Is a change marked as read per account on the server, so the app and the web client agree? That seems right.
- Does the web client get notifications too (browser push), or only the page?
- Notifications help people remember the app, but not look things up faster. Worth checking separately: a home screen
  widget or shortcut to search or to pinned pages.

---

# App-wide

## Error handling

**What I want**

- We have a big error banner. It is not bad, but it is badly placed (below system bars on some systems with camera
  cutouts), and probably not always the best way to handle errors.
- Write a design spec of how we handle errors and why, based on what we have now and on what is considered good UX on
  Android in 2026.

**State**

- The spec is a proposal in `docs/error-handling.md`, waiting for a decision. Its migration list is what's left to do.
- #122 keeps `ErrorCard` clear of system bars and cutouts. That wasn't tried on a device with a cutout.
- The file browser no longer shows raw exception text (`Throwable.userMessage()`).

## Settings

**What I want**

- The settings are very rudimentary. Keep the library.
- Expand the options: check all our features and decide which behaviors deserve a setting, then categorize them
  properly.
- Rework the styling. It looks similar to the rest of the app, but doesn't fully fit.
- Changing [KutePreferences](https://github.com/markusressel/KutePreferences) is fine if needed. It's my own project,
  and it can adapt to more use cases.

**Where to start**

- All in `feature/preferences/data/KutePreferencesHolder.kt`. KutePreferences is pinned to `a7bcad2bae` in
  `gradle/libs.versions.toml`, and a checkout is next to this repo in `../KutePreferences`.
- What exists today:
  - theme
  - offline mode
  - always open in edit mode
  - force offline cache update
  - clear offline cache
  - last cache update
  - debug: demo mode, log network requests
- #123 removed the sync interval setting, which did nothing.
- Candidates (Idea):
  - author highlighting mode
  - default split mode and ratio
  - connect automatically (`connectsAutomatically` in `DocumentSessionEnvironment`)
  - editor text size, line numbers, line wrapping
  - rendered page zoom

---

# Accounts

## Backend configuration

**What I want**

Both the code and the UI deserve a rework. It has everything we need, but it looks clunky and crammed, and doesn't
behave very elegantly. Redesign the UX and UI, and the code of the UI layer.

**Where to start**

- `feature/backendconfig/edit/ui/BackendConfigEditViewModel.kt` is the big one, at 514 lines. The whole feature is about
  2700 lines.
- Edit screen: `BackendConfigEditScreenContent` with `MkDocsBackendSection`, `MkDocsWebSection`, `AuthConfigSection` and
  `BackendAccountCard`.
- `compose/auth/` has a composable for each button (`AuthAddButton`, `AuthSaveButton`, `AuthAbortButton`,
  `AuthDeleteButton`), plus `AuthConfigEditLayout` and `AuthConfigEmptyLayout`.
- List screen: `feature/backendconfig/list`, with `BackendSelectionViewModel`.
- Newer flows already exist that may replace part of this: `feature/auth/onboarding` (`ConnectServerScreen`,
  `ConnectLinkScreen`) and `feature/auth/account`. Decide what the manual form is still for, before redesigning it.
- Follow `AGENTS.md`: one `UiState`, one `onUiEvent`, a stateless `XScreenContent` with `@CombinedPreview`, and a
  `toString` override on state that holds a password or token.
- Read "Backend of the REST client" in `AGENTS.md` before touching how the selected backend works.

**Open questions**

- Does anyone still need a manual form with host and port, or is a server address plus sign-in enough?

## Onboarding

**What I want**

- We don't have one, and we should.
- Point to the web login as an easy way to get started, and explain that an admin has to give out invite links.
- It could also show off the features of the app. That's low priority. If we do it, keep it succinct but visually
  pleasing (fancy animations would be awesome).

**Where to start**

- A fresh install today: without a backend, the bottom bar has no file browser (`MainViewModel.updateNavItems`), and the
  start tab comes from `GetInitialTabUseCase`. So the user lands in the backend list.
- Reuse what exists:
  - `ConnectServerScreen`: enter an address. `ServerDiscovery` finds the server.
  - `ConnectLinkScreen`: a device link. A signed-in user creates it in the web client and scans its QR code.
  - `feature/auth/link`: setup, invite and reset links (the App Link and `mkdocseditor://`), then
    `feature/auth/login` and `feature/auth/passkeyprompt`.
- Invites: an admin creates them with `POST /admin/invites/`. See `docs/auth.md` in MkDocsEditor-Backend.
- Passkeys: a new package or signing key breaks them, see `AGENTS.md`. Nothing here should need one.

**Open questions**

- **Which web login do we point to?** A new user has no server yet. `ServerInfo.webClientUrl` is only known once a
  server is found, and `auth_web_client_url` is a build-time default for `edit.wiki.markusressel.de`. Is this app only
  for one instance, or do we ask for the server first?
- Where is "onboarding done" stored, and when does it show again (after removing the last backend)?
- Is the feature tour part of the first version? I'd leave it out.

---

# Small follow-ups

Left over from other work, not written by Markus.

- `LiveBackendSyncTest` relies on HTTP Basic with `legacyBasicAuth`, which the backend docs say is removed. It needs
  fixing before it can run.
