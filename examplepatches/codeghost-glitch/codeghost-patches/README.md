<div align="center">
<img src="patches-bundle.png" alt="CodeGhost Patches" width="128" />

[![Website badge](https://img.shields.io/badge/Website-gray?style=for-the-badge)](https://morphe.software) [![Documentation badge](https://img.shields.io/badge/Documentation-gray?style=for-the-badge&logo=github)](https://github.com/MorpheApp/morphe-documentation#readme) [![Release badge](https://img.shields.io/github/v/release/codeghost-glitch/codeghost-patches?style=for-the-badge&logo=github&color=gray)](https://github.com/codeghost-glitch/codeghost-patches/releases/latest) [![License badge](https://img.shields.io/badge/License-GPL--3.0-gray?style=for-the-badge&logo=gnu)](LICENSE)
<br>
</div>

&nbsp;
<p align="center">
  <a href="https://morphe.software/add-source?github=codeghost-glitch/codeghost-patches" title="Add to Morphe">
    <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" width="240"/>
  </a>
</p>
&nbsp;

# 🧩 Patches
CodeGhost Patches

## ❓ About

CodeGhost Patches is a third-party patch bundle for [Morphe](https://morphe.software). It is not authored, endorsed or maintained by the Morphe project; see the [NOTICE](NOTICE).

Every patch forces a branch the app already has - the "entitled" or "enabled" path - instead of adding behaviour, so the rest of the app keeps working exactly as shipped. Fingerprints are pinned to the app version the analysis was done on, because obfuscators rename everything on every release.

&nbsp;
## 🩹 Patches list

<!-- PATCHES_START -->
> **[v1.0.0](https://github.com/codeghost-glitch/codeghost-patches/releases/tag/v1.0.0)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details>
<summary>📦 MIDI Clef&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;9.2.6 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock remove-ads subscription](#unlock-remove-ads-subscription) | Removes all ads: banners, native ads and interstitials. |  |

#### Unlock remove-ads subscription

MIDI Clef's only purchase is ad removal. The app behaves like a buyer: no banner,
native or interstitial ads, and the ad-partner section of the GDPR consent screen
is skipped.

</details>

<details>
<summary>📦 Mega Karaoke Songbook&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**🎯 Supported versions:**

| 🧪&nbsp;2.59 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock subscription](#unlock-subscription) | Unlocks the subscription: no ads, unlimited PDF pages, unlimited songbooks and the hidden functions. |  |

#### Unlock subscription

Unlocks everything the subscription includes: no ads, unlimited PDF pages (a free
install stops at 20), unlimited songbooks and the hidden functions (Title case and
Category buttons, table view, PDF export).

</details>

<!-- PATCHES_END -->

## Contributing

Pull requests for fixes and for new apps are welcome. Each app lives in its own package under `patches/src/main/kotlin/app/morphe/patches/<app>/`, with a `Compatibility` target pinned to the exact version the fingerprints were taken from.

- **Build:** `./gradlew :patches:buildAndroid -Pgpr.user=local -Pgpr.key=local` produces `patches/build/libs/patches-*.mpp`. The registry credentials must be present but are unused: the patcher and the Gradle plugin are pinned to versions that resolve without GitHub Packages access.
- **Verify before opening a PR:** `./gradlew :patches:runPatchHarness -Papk=<apk>` must report `exception=null`, and the dex diff must contain only the intended instructions.
- **Document in the patch KDoc:** product ids, offsets, what was verified and what was not. There are no per-app notes files.
- **Commit messages:** `feat:`, `fix:` or `chore:`. The release notes are generated from them, in the same conventionalcommits layout the Morphe patches repo uses.
- **Releases:** `tools/release.sh <version>` bumps the version, builds, publishes the asset and rewrites `patches-bundle.json`, which is the metadata Morphe Manager reads.

## 📜 License

CodeGhost Patches are licensed under the [GNU General Public License v3.0](LICENSE), with additional conditions under GPLv3 Section 7:

- **Attribution (7b):** Any use of this code, including derivative works, must preserve all original notices and disclaimers.
- **Name & Branding Restrictions (7c & 7e):** Derivative works must use their own distinct branding. The **"Morphe"** name, logos, and trademarks may not be used for the branding or title of derivative works.

See the [LICENSE](LICENSE) file for full GPLv3 terms and the [NOTICE](NOTICE) file for full conditions of GPLv3 Section 7.
