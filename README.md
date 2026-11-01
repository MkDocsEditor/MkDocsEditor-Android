<p align="center" style="font-size: xx-large;">
  <img alt="Logo" src="app/src/main/ic_launcher-web.png" width="144"/> </br>
</p>

# MkDocsEditor

A realtime collaborative editor for MkDocs projects.

# :warning: Work In Progress

This project is still a work in progress and is **not yet ready for production**.

![File-Browser](screenshots/FileBrowser-Wide.jpg)

![Code Editor](screenshots/CodeEditor.jpg)

# What is this?

MkDocsEditor aims to provide an easy way for you to manage your existing [MkDocs](https://www.mkdocs.org/) projects:
* [ ] Section management (folders)
  * [x] create sections
  * [x] rename sections
  * [ ] move an entire section to another location
  * [x] delete a section and all the content inside it
* [ ] Document management (`*.md` files)
  * [x] create documents
  * [x] rename documents
  * [x] edit documents in realtime
    * [x] Quick formatting toolbar
    * [x] Syntax highlighting
    * [x] Markdown preview
  * [x] see the result on your hosted website right away
  * [ ] move a document
    * [ ] move all resources that are referenced in it, if possible
  * [x] delete documents
    * [ ] delete unused resources that are referenced in it
* [ ] Resource file management (any file that is not a markdown file really)
  * [x] upload resource files
  * [x] download linked resources
  * [x] open/share linked resources (if supported by another app on your device)
  * [x] delete resource files
    * [ ] automatically replace any reference to this resource in a document with a "deleted" note
* [x] Search
* [x] Full Offline Cache

## Realtime collaborative editing

MkDocsEditor is built around the famous [Differential Synchronization](https://neil.fraser.name/writing/sync/)
algorithm developed by [Neil Fraser](https://neil.fraser.name/) which allows
you to edit a document simultaniously while someone else is also editing.

**TODO: insert gif here**

# How to use

## Server setup

To use this app an instance of [MkDocsEditor-Backend](https://github.com/MkDocsEditor/MkDocsEditor-Backend) needs to run on the server
where your [MkDocs](https://www.mkdocs.org/) project source files are located.
Have a look at the documentation on it's [project site](https://github.com/MkDocsEditor/MkDocsEditor-Backend) to learn more
on how to do this.

## Installation

The app needs Android 16 or newer on a 64-bit device.

Every build of `master` is published twice on the
[releases page](https://github.com/MkDocsEditor/MkDocsEditor-Android/releases). The two are
separate apps and can be installed at the same time, with separate data:

| | Production | Debug |
|---|---|---|
| Package | `de.markusressel.mkdocseditor` | `de.markusressel.mkdocseditor.debug` |
| Launcher name | MkDocsEditor | MkDocsEditor Debug |
| GitHub release | normal release `release-<n>` | pre-release `build-<n>` |
| Size | about 18 MB, R8, `arm64-v8a` | about 110 MB, no R8, `arm64-v8a` and `x86_64` |
| Signed with | the release key | `.ci/debug.keystore` |

The easiest way to install and update them is [Obtainium](https://github.com/ImranR98/Obtainium).
Add one app in Obtainium for each package you want. Both use the same address,
`https://github.com/MkDocsEditor/MkDocsEditor-Android`, and the settings tell them apart:

| Setting in Obtainium | Production | Debug |
|---|---|---|
| Include prereleases | **off** | **on** |
| Filter release titles by regex | empty | `^Build ` (recommended) |
| Fallback to older releases | on | on |
| Sort method | Release date | Release date |

Everything else stays at its default. In **Add App**, enter the address, set the switches and tap
**Add**, then **Install**. Obtainium checks for new builds and offers the update.

An existing debug source keeps working without changes, because the debug build is always
published last and so is the newest release it can see. The title filter makes that independent of
the order; without the filter, a failed publish could show a "package ID does not match" error
until the next build, and nothing gets replaced.

All builds of a package are signed with the same key and get a higher version code from CI, so
they update each other. The app's version name does not change between builds (the release tags
carry the number), so if Obtainium reports a version mismatch, turn off its version detection for
this app.

A device with a debug build older than this change has to update that first. The old one claims a
fixed FileProvider authority, and Android refuses to install production beside it.

Links: `https://<web client>/…` opens the app whose App Link is verified. If both packages are
verified, Android opens one without asking (the production app in our test), so the other one needs
**Open by default** turned off in the Android settings of the first. `mkdocseditor://…` links show
a chooser when both are installed.

## Client Setup

**Scan the QR code on the web client's account page.** Sign in on the web client, open your
account page and choose **Connect the Android app**. Scan the QR code with the phone's camera
(or open the link on the phone). The app adds the server, signs you in and opens your
documents. The code works once and for 5 minutes.

Without the web client at hand, open the app and enter the **Server address**: the address of
the web client (like `edit.wiki.example.com`) or of the backend. The app finds the backend,
the server's name and the wiki on its own; name, description and a login for the published
wiki are under **Advanced**. Then sign in with a passkey or your password; password managers
like Bitwarden fill in and save the login. Invite, setup and reset links from the web client
open in the app, too.

For the links to open the app directly (Android App Links), the web client's host must serve
`/.well-known/assetlinks.json` for the app, see `docs/auth.md` of the backend.

**Passkeys in the app** need `https://<rpId>/.well-known/assetlinks.json` naming the app (package
and signing certificate) with the relations `handle_all_urls` (Bitwarden checks only this one) and
`get_login_creds` (Google Password Manager). Password managers like Bitwarden
and Google Password Manager do not read the file from the phone: Google's Digital Asset Links
service fetches it, so the RP ID domain must be reachable **from the internet**. A server that
is only reachable at home needs an RP ID on a public parent domain (see `docs/auth.md` of the
backend). Otherwise Bitwarden shows "Passkey not supported for this app".

# Developer Instructions

## How to build

Building this project should be straight forward. Just check out the repo and let Android Studio
handle the rest.

## Build variants

- `./gradlew app:assembleDebug`: signed with `.ci/debug.keystore`.
- `./gradlew app:assembleRelease`: R8 without obfuscation, `arm64-v8a` only, and **not signed**. CI
  signs it in the `Publish` job on `master`, never with the debug key.
  `-Pmkdocs.releaseAbis=x86_64` builds it for an emulator; sign that copy with a throwaway key.

The version code is the CI run number and 1 for local builds, so a local build only installs over
a CI build after uninstalling it, or with `adb install -d` for debug.

Stack traces of a production build carry the real names, but R8 merges classes, so some frames can
name the wrong class. Each `release-<n>` has its `mapping.txt` for `retrace`.

## Release signing

CI reads the key from the repository secrets `RELEASE_KEYSTORE_BASE64`,
`RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS` and `RELEASE_KEY_PASSWORD`. If one is missing, the
`Publish` job leaves a notice, skips the release, and publishes the debug build as usual. The same
happens when the unit tests failed. Never commit the keystore.

The certificate fingerprint is public. It is in the notes of every `release-<n>`, in the job summary,
and in the APK:

```shell
gh release download release-<n> --pattern '*.apk'
$ANDROID_HOME/build-tools/<version>/apksigner verify --print-certs MkDocsEditor-release_*.apk
```

## Gradle

Since this project uses a Gradle version >= 4.6 and the Android Gradle Plugin only supports "
configuration on demand" on lower versions you have to manually disable it in your IntelliJ/Android
Studio project settings. See this page for more info: https://stackoverflow.com/a/49994951/1941623

## Connecting to the Host from the Emulator

The emulator host PC can be accessed from inside the emulator via IP `10.0.2.2`.

# Contributing

GitHub is for social coding: if you want to write code, I encourage contributions through pull
requests from forks of this repository. Create GitHub tickets for bugs and new features and comment
on the ones that you are interested in.

# License

**AGPLv3+**
