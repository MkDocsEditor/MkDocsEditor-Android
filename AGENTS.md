# AGENTS.md

Android client of MkDocsEditor: edit a MkDocs wiki together in real time, also offline. Kotlin,
Jetpack Compose, Hilt, Voyager, ObjectBox and Automerge. Android 16 and newer only (`minSdk` 36),
64-bit only. Human docs: `README.md`. Protocol and account flows: `docs/sync-protocol.md` and
`docs/auth.md` in MkDocsEditor-Backend.

## Commands

| What | Command |
|---|---|
| All tests | `make test` |
| JUnit tests (CI) | `make unitTests` (`./gradlew testDebugUnitTest`) |
| One module or test | `./gradlew :rest:testDebugUnitTest --tests '*EditorTextBinding*'` |
| Debug and release APK (CI) | `./gradlew app:assembleDebug app:assembleRelease` |
| Install and start debug | `make run` |
| R8 build for an x86_64 emulator | `./gradlew app:assembleRelease -Pmkdocs.releaseAbis=x86_64` |

There is no linter or formatter config, so match the surrounding code. An emulator reaches the host
at `10.0.2.2`. `LiveBackendSyncTest` needs a backend on a test wiki and is skipped unless
`-Pmkdocs.backend.url` and `-Pmkdocs.backend.users` are set; CI does not run it.

## Structure

| Module | Holds |
|---|---|
| `app` | UI, features, Hilt setup, the app's use cases |
| `auth` | Accounts: API client, server discovery, links, tokens. Has test fixtures. |
| `data` | ObjectBox persistence (`data/persistence`, entities, managers). Exposes `rest` as `api`. |
| `rest` | REST client, Automerge sync session, `EditorTextBinding` |
| `build-logic` | Gradle convention plugins; SDK levels in `KotlinAndroid.kt` |

Dependencies point one way: `app` uses `data` and `auth`, `data` uses `rest`. Versions belong in
`gradle/libs.versions.toml`, shared build setup in a convention plugin.

Features are in `app/.../feature/<name>/`, split into:

- `data/`: models and repositories.
- `domain/`: use cases, usually `class XUseCase @Inject constructor(...)` with `operator fun invoke`.
- `ui/`: `XViewModel`, `XEvent`.
- `ui/compose/`: `XScreen` and `XScreenContent`.

## Code style

- **Screens:** a Voyager `Screen` gets the ViewModel with `getViewModel` and collects its events. The
  `XScreenContent` composable is stateless, takes `uiState` and `onUiEvent`, and has a
  `@CombinedPreview`.
- **ViewModels:** `@HiltViewModel`, one `MutableStateFlow<UiState>` exposed read-only, one
  `onUiEvent(UiEvent)`, one-off events through a `Channel`. `UiState` and `UiEvent` are nested types
  or next to the ViewModel.
- **Secrets:** a state or event holding a password, token or link overrides `toString`, so it can't
  reach a log.
- Keep things `internal` unless another module needs them. Inject with Hilt, never build
  dependencies by hand.
- Log with Timber, not `android.util.Log`. User-facing text goes into `strings.xml`; only debug-only
  UI may hardcode text (behind `BuildConfig.DEBUG`).
- Comments are short and say why, in plain English. KDoc on classes whose role is not obvious.

## Testing

JUnit 5 with `kotlin.test` and `kotlinx-coroutines-test`. Tests live in `src/test` of each module, with
names in backticks that read as sentences. Prefer fakes to mocks. A bug fix comes with a test that
fails without it. Sync tests use real Automerge, not stubs.

UI and other Robolectric tests stay on JUnit 4 (`@RunWith(AndroidJUnit4::class)`, `org.junit.Test`) and
run on the vintage engine, because Robolectric and the Compose test rule only exist for JUnit 4. Never
use `kotlin.test.Test` in such a class: it is a Jupiter test then, and runs without Robolectric.

## Pull requests

- Branch and PR, never directly on `master`. PRs are squash-merged, and the title becomes the commit:
  a plain sentence in the imperative ("Show search results again"), no conventional-commit prefix.
- Required checks: `Unit Tests` and `Build APK`. No `--admin`.
- Every master build publishes `build-<n>` and `release-<n>`, so master has to stay installable.
- KodeEditor and KodeHighlighter are separate repositories; only Markus merges there.

## Rules from real bugs

Read before changing these areas.

**Editor and document.** `EditorTextBinding` is the only connection between the text field and the
Automerge document; read its class comment first.

- Never compare the field's text with the current document to find what the user typed. The field
  takes outside values only in the next frame, so its text can be older than the document, and the
  comparison deletes remote changes. An edit goes in at the document state the field shows
  (`startTransactionAt`), and Automerge merges it.
- Giving the field new text commits the composition and restarts the input method, and key presses
  in that moment are lost (Compose `EditProcessor.reset`). So remote changes wait for a typing pause
  and the end of a composition. Keep `TextFieldValue.composition` when handing a value back.

**Backend of the REST client.** `IMkDocsRestClient` has no host setters: `RequestManager` asks for the
base URL with every request, and `createSelectedBackendRestClient` answers from the database. Android
can restore any screen after process death, so a step that has to run first does not run in time.
Don't bring back a default host or such a step. Without a backend, a request fails with
`BackendNotConfiguredException` and is not sent.

**R8.** Release plants no Timber tree, so caught exceptions leave no trace: the missing Automerge keep
rule only showed as an empty document. After changing dependencies or keep rules, run an R8 build on
an emulator (throwaway key) against a local backend, then open and edit a document. For logs, plant
the tree temporarily.

## Packages, signing and passkeys

Debug is `de.markusressel.mkdocseditor.debug` (`.ci/debug.keystore`), production is
`de.markusressel.mkdocseditor` (release key in CI secrets). Details: README sections "Build variants",
"Release signing" and "Client Setup".

- Family devices install debug through Obtainium: it keeps its package and key, and needs no new
  Obtainium settings. The `Publish` job publishes it last, because Obtainium picks the newest release
  by date.
- Never sign release with the debug key, and never commit a keystore.
- **A new package or signing key breaks passkeys** ("Passkey not supported for this app") until all
  three have the entry, so tell Markus: `public/.well-known/assetlinks.json` in `markusressel/blog`
  with both relations (`handle_all_urls` for Bitwarden, `get_login_creds` for Google),
  `server.auth.webauthn.android` in the backend config, and `MKDOCSEDITOR_ASSETLINKS_JSON` of the web
  client. Google fetches the file from the internet, so the RP ID is the public `markusressel.de`, and
  it caches its answer for up to an hour.
