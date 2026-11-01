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

So whenever the package name or the signing key changes (a release build, a new keystore, Play
App Signing), all three places need the new entry: the blog's `assetlinks.json`, the backend
config, and the web client's `MKDOCSEDITOR_ASSETLINKS_JSON`. Never replace `.ci/debug.keystore`
without that, and tell Markus. Today only the CI debug build exists:
`de.markusressel.mkdocseditor.debug`, fingerprint
`74:A7:45:FA:C2:4A:BF:BD:F0:A7:30:83:18:71:FE:A6:DE:24:6C:6B:D2:E3:F8:62:49:24:27:1F:C4:D3:70:48`.

The protocol and the account flows are specified in `docs/auth.md` and `docs/sync-protocol.md`
of MkDocsEditor-Backend.
