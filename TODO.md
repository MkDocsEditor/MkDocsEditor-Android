# TODO

The notes under "Where to start" were checked against the code on 2026-11-04 (master `553eec9`), so
re-check them before relying on them. Anything marked "Idea" is a suggestion, not a decision.

**New features have to work in MkDocsEditor-Web as well.** Shared data and logic belong on the backend, so that both
clients show the same thing.

| Area | Entry | Kind of work | Needs another repo |
|---|---|---|---|
| File browser | [File browser, editor and preview at once on a tablet](#file-browser-editor-and-preview-at-once-on-a-tablet) | Idea, low priority: a layout for large screens | No |
| Editor | [Code blocks as one box](#code-blocks-as-one-box) | Full-width background of fenced code | Probably KodeEditor |
| Staying in use | [What's going on page, pinned pages and notifications](#whats-going-on-page-pinned-pages-and-notifications) | Better summaries | Yes, the backend and the web client |
| App-wide | [Large downloads run out of memory](#large-downloads-run-out-of-memory) | Bug: stream downloads to a file | No |
| Project | [Code quality checks](#code-quality-checks) | CI setup, Android first | Yes, all six repositories |

Order that fits how they depend on each other (Idea):

- New screens follow the error handling spec in `docs/error-handling.md`.

---

# File browser

## File browser, editor and preview at once on a tablet

**Idea, low priority.** On a tablet, the file browser, the editor and the preview could all be open at once. That is a
niche case. The old two-pane layout for large windows (`MkDocsEditorListAndDocumentContent`) never showed a document,
and it was removed in #168.

A new layout has one constraint, which caused the bug fixed in #168: the file browser's navigator must stay at the same
place in the composition for every window size. Voyager restores a nested navigator by that place, so a layout that
moves it for another window size resets the file browser and closes the open document on a rotation or a resize. Put
the panes around the one navigator instead of choosing between two layouts that each have their own.

---

# Editor

## Code blocks as one box

In the editor, the background of a fenced code block only shades the text of each line, not the full width of the
editor. So the block looks ragged, unlike the preview, where it is one box.

The shading is a span: `EditorColorScheme.codeStyle` gives `MarkdownRules.FencedCode` (and inline code) a `SpanStyle`
with `background = palette.codeBackground`. A span background only covers the glyphs of a line. A full-width box needs
a background behind whole lines, and KodeEditor has none: its `overlay` draws on top of the text, and the only line
background is `foldedLineBackgroundColor` for folds. So this probably needs a line background (or a layer below the
text) in KodeEditor, a separate repository where only Markus merges. The rule book would then name the lines of a
block, for example from the same fences that `MkDocsFoldProvider` finds.

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

The app notifies about changes by polling (`WikiChangesWorker`, `CheckWikiChangesUseCase`): about once an hour, with
a network and enough battery, it reads `GET /feed/` of the selected server while signed in. Edits by other accounts to
pages pinned for everyone or for the user, and to pages the user edited, give one notification per page ("Anna and Ben
changed Shopping", with the headings of the summary), grouped per server. Edits without an account (`External change`)
don't notify. Tapping one opens the page in the editor. A mark per server and account keeps a change from notifying
twice; the first check only sets it, and runs right after signing in. The setting is under Notifications and on by default; the dashboard asks for the
permission once, turning the setting on asks again, and the setting says when Android blocks the notifications.

Push (backend `docs/push.md`): when the selected server lists `push` and the user is signed in, the app registers with
UnifiedPush using the server's VAPID key and posts the endpoint and keys to `/push/subscriptions/`
(`WikiChangesPushRegistration`), again after each sign-in, for a new endpoint and when another server is selected. Signing
out deletes the subscription first, then unregisters. A `feed-changed` push runs the same check as the polling
(`WikiChangesPushService`), so the mark keeps it from notifying twice. No Firebase: the distributor is an app like ntfy,
set to `https://ntfy.markusressel.de` for the family. Without a distributor, or with push off on the server, the hourly
polling is all there is, and the setting says that a UnifiedPush app is missing. With several distributors and no default,
the app takes ntfy, or else the first one. A subscription stays on a server that is no longer selected until its
session ends.

**What is left**

1. Better summaries, if the simple ones aren't enough.

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
- Web client: `DocumentView` and its history are the place to look for what can be reused. I didn't check this in detail.

**Push (decided, Markus, 2026-11-06)**

- The ntfy server at `https://ntfy.markusressel.de` is reachable on the LAN and over the family VPN, allows anonymous
  use, and only topics starting with `up`. The backend needs it in `server.push.allowedHosts`.

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

- Decide on per-folder or per-document following, and a setting for how often.
- Tapping a notification opens the document, but not yet at the change.

**Open questions**

- What counts as an important change? Is fixing a typo worth a notification?
- Does the feed also cover new, moved and deleted documents, and resources? Git sees these too.
- Is a change marked as read per account on the server, so the app and the web client agree? That seems right.
- Does the web client get notifications too (browser push), or only the page?
- Notifications help people remember the app, but not look things up faster. Worth checking separately: a home screen
  widget or shortcut to search or to pinned pages.

---

# App-wide

## Large downloads run out of memory

`IMkDocsRestClient.downloadResource` reads the whole file into a `ByteArray` before `DownloadResourceUseCase` writes it
to the file cache. A 400 MB file ended in an `OutOfMemoryError`, and the user only saw "Could not open big.bin". The
download should stream to the file instead, with the same progress and cancel. Found while testing #170.

---

# Project

## Code quality checks

**What I want**

- We change a lot quickly, across six repositories (this app, MkDocsEditor-Backend, MkDocsEditor-Web, KodeEditor,
  KodeHighlighter and KutePreferences), without always seeing the whole project. CI should show where the code gets
  worse.
- No AI-based tools: they cost tokens and need a subscription. Coding agents still have to read the results easily.
- Free on GitHub Actions; all six repositories are public, so minutes and CodeQL cost nothing. A server in the k3s
  cluster only if a tool really needs one.
- Spot degradations, but keep the style of each project, unless a style is bad for reasons we can verify.

**Plan (decided, Markus, 2026-11-07)**

- **Baseline and ratchet.** Each tool records today's findings once as accepted (a baseline file in the repository). CI
  fails only on new findings, and the counts may only go down. Pure style rules stay off unless they match the code as
  it is. What stays on is about bugs, security, dead code, complexity and architecture.
- **Tools per language:**

  | | Kotlin (this app, KodeEditor, KodeHighlighter, KutePreferences) | Go (backend) | TypeScript (web) |
  |---|---|---|---|
  | Bugs and smells | detekt with a baseline, Android Lint with `lint-baseline.xml` | golangci-lint with `new-from-rev` (staticcheck, errcheck, gosec, …) | ESLint with typescript-eslint, `tsc --noEmit` in strict mode |
  | Compose | compose-rules (detekt plugin) | | |
  | Architecture | Konsist: the rules of AGENTS.md as tests (dependency direction, `XUseCase`, `internal`, ViewModel shape, Timber instead of `android.util.Log`) | go-arch-lint or a small check | dependency-cruiser |
  | Dead code and dependencies | detekt's unused rules, the Gradle dependency-analysis plugin | `deadcode`, a `go mod tidy` check | knip |
  | Coverage | Kover, with a floor for the changed lines | `go test -cover` | Vitest coverage |
  | Security | CodeQL (java-kotlin), Dependabot or Renovate | CodeQL (go), govulncheck | CodeQL (javascript-typescript), `npm audit` |
  | Duplicates | jscpd | jscpd | jscpd |

- **For coding agents:**
  - Every tool writes SARIF or JSON into one folder, for example `build/reports/quality/`.
  - `make quality` runs the same checks locally as CI.
  - A PR comment lists only the new findings, as `file:line: rule: message`.
  - SARIF also goes to GitHub code scanning, so findings show inline in the PR.
  - AGENTS.md gets a section on how to run the checks and how to read them.
- **Trend:** every master build stores a small `quality.json` with the counts per tool and the coverage. A PR compares
  itself with the latest one. SonarQube Community in the k3s cluster would add a dashboard with history, but it doesn't
  comment on PRs, so it waits until the simple trend is not enough.
- **Rollout:**
  1. A pilot in this app: detekt with compose-rules, Lint, Konsist, Kover, CodeQL and jscpd, all with baselines. The
     checks are required, for new findings only.
  2. KodeEditor, KodeHighlighter and KutePreferences, with the same setup shared as a convention.
  3. The backend and the web client, each through its own agent, with the same principles.
