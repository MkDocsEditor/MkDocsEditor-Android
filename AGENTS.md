# AGENTS.md

Notes for AI coding agents working on this repository. Human-oriented docs live in `README.md`.

## Passkeys and the signing key

Passkeys in the app only work when the passkey RP ID domain serves
`/.well-known/assetlinks.json` with the app's package name and the SHA-256 fingerprint of its
signing certificate, with both relations `delegate_permission/common.handle_all_urls` (the only
one Bitwarden checks) and `delegate_permission/common.get_login_creds` (Google Password
Manager). Google's Digital
Asset Links service fetches that file from the internet, so password managers like Bitwarden
reject the app ("Passkey not supported for this app") when the domain is not publicly
reachable, even if the phone can open it. Google caches its answer for up to an hour, so a fixed
file can take that long to be accepted.

Markus' deployment (the family wiki) uses the RP ID `markusressel.de`, because the wiki hosts are
only reachable from his home network. The file is in the repository `markusressel/blog`
(`public/.well-known/assetlinks.json`, published by Netlify), and the backend config has the same
package and fingerprint in `server.auth.webauthn.android`. The web client serves a second copy on
its own host for App Links (`MKDOCSEDITOR_ASSETLINKS_JSON`).

So whenever the package name or the signing key changes (a new keystore, Play App Signing), all
three places need the new entry: the blog's `assetlinks.json`, the backend config, and the web
client's `MKDOCSEDITOR_ASSETLINKS_JSON`. Never replace `.ci/debug.keystore` or the release key
without that, and tell Markus. There are two packages:

- `de.markusressel.mkdocseditor.debug`, fingerprint
  `74:A7:45:FA:C2:4A:BF:BD:F0:A7:30:83:18:71:FE:A6:DE:24:6C:6B:D2:E3:F8:62:49:24:27:1F:C4:D3:70:48`.
- `de.markusressel.mkdocseditor`, fingerprint in the notes of every `release-<n>`; the command is in
  the README.

## Build variants, signing and publishing

README sections "Installation", "Build variants" and "Release signing" describe them. For changes:

- Debug is what Markus' family has installed, through Obtainium with pre-releases on. It has to keep
  its package and key and must not need new Obtainium settings.
- Never sign release with the debug key. `.ci/sign-release.sh` refuses it, and Gradle has no signing
  config for release on purpose. Missing secrets are not an error: CI stays green and skips the release.
- In the `Publish` job the debug pre-release comes last, because Obtainium picks the newest release
  by publish date and an unfiltered debug source also sees `release-<n>`.
- R8 builds fail silently: release plants no Timber tree, so caught exceptions leave no trace. The
  missing Automerge keep rule only showed as an empty document. When changing dependencies or keep
  rules, run an R8 build on an emulator (`-Pmkdocs.releaseAbis=x86_64`, throwaway key) against a local
  backend, and open and edit a document. For logs, plant the tree temporarily.

The protocol and the account flows are specified in `docs/auth.md` and `docs/sync-protocol.md`
of MkDocsEditor-Backend.

## The editor and the document

`EditorTextBinding` (in `rest`) is the only connection between the text field and the Automerge
document; read its class comment before changing either side. Two rules come out of real bugs:

- Never compare the text of the field with the current document to find out what the user typed.
  The field takes a value from outside only in the next frame, so its text can be older than the
  document, and the comparison deletes remote changes. An edit goes in at the document state the
  field shows (`startTransactionAt`), and Automerge merges it.
- Giving the field a new text commits the composition and restarts the input method, and key presses
  in that moment are lost (Compose `EditProcessor.reset`). So remote changes wait for a pause of
  typing and for the end of a composition. Keep `TextFieldValue.composition` when handing a value back.
