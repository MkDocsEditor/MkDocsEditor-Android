# TODO

The notes under "Where to start" were checked against the code on 2026-11-04 (master `553eec9`), so
re-check them before relying on them. Anything marked "Idea" is a suggestion, not a decision.

**New features have to work in MkDocsEditor-Web as well.** Shared data and logic belong on the backend, so that both
clients show the same thing.

| Area | Entry | Kind of work | Needs another repo |
|---|---|---|---|
| File browser | [Transitions between sections](#transitions-between-sections) | Path chips during the back gesture | No |
| Staying in use | [What's going on page, pinned pages and notifications](#whats-going-on-page-pinned-pages-and-notifications) | Feature | Yes, the backend and the web client |
| App-wide | [Settings](#settings) | Card color left | Maybe KutePreferences |

Order that fits how they depend on each other (Idea):

- New screens follow the error handling spec in `docs/error-handling.md`.

---

# File browser

## Transitions between sections

Folders change with a shared axis, and the back gesture previews the parent folder. What is left:

- The path chips do not follow the back gesture. They change once it is released, so the last chip only starts to fade
  out then.

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

**The index page (Idea from Markus)**

MkDocs always has an `index` document, which the web page opens by default. It could serve as the dashboard, or a part
of it: the family already edits it like any other page, it works offline like any other page, and it needs no new
backend storage. It could hold what pinned pages would otherwise do, at least for the whole family, with the feed below
or next to it.

**Decided (Markus, 2026-11-06)**

- The page is a dashboard. Pins show nowhere before it exists, so the dashboard comes first, not pinned pages on their own.
- It shows the `index` page, then the pins, then the feed of changes.
- It is the start page by default. A setting switches the start back to the file list.
- Family pins can be set and removed by admins and editors. Personal pins by every account, for itself.

**State**

The dashboard ("What's going on") is built in the app (`feature/dashboard`), the web client and the backend
(`docs/dashboard.md` there): the `index` page, family and personal pins, the feed of changes with paging, the start page
setting and the sync indicator. It shows what is stored at once, also offline, and follows `pins-changed` and
`feed-changed` while visible. Pages are pinned from their dialog in the file list and from the editor menu.

**What is left**

1. Notifications, which are "there are new entries in the feed".
2. Better summaries, if the simple ones aren't enough.

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
- `GET /events/ws/` pushes tree, pin and feed changes; the file browser and the dashboard follow them while visible
  (`ObserveServerEventsUseCase`).
- The events socket ignores `items-moved`. A move in another client drops the offline copy under the old id with the next
  fetch, and a renamed open folder sends the user to its parent. Any refresh does that today, but it now happens at once.
- Opening the file browser fetches the tree twice: once for the folder and once after the socket connects.
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

## Settings

**What I want**

- The settings are very rudimentary. Keep the library.
- Expand the options: check all our features and decide which behaviors deserve a setting, then categorize them
  properly.
- Rework the styling. It looks similar to the rest of the app, but doesn't fully fit.
- Changing [KutePreferences](https://github.com/markusressel/KutePreferences) is fine if needed. It's my own project,
  and it can adapt to more use cases.

**State**

- Categories are Offline, Editor and Appearance. Offline mode has its setting, and the editor has line wrapping, line
  numbers and text size.
- The category cards are flat, using `KutePreferencesTheme` colors. `Category.kt` in KutePreferences hardcodes its
  container color, which KutePreferences would have to change.
