# 👋🧩 Viper Patches

Morphe patches for apps I like.

## ❓ About

- **Unlock SAI Premium** — activates SAI Premium (com.mtv.sai 2.4.3) by forcing the local RevenueCat entitlement check to true in all three places that read it.
- **Remove ads (APKS Installer)** — disables all ads in APKS Installer (com.inwepo.apkinstaller 2.8-Google) by forcing the master ads flag off and making the ad initializer a no-op.

### How to use these patches

Click the button below, or manually add this repo url to Morphe Manager: https://github.com/sabbirdj/viper-patches

<a href="https://morphe.software/add-source?github=sabbirdj/viper-patches">
  <img src=".github/assets/add-to-morphe.svg" alt="Add to Morphe" height="48">
</a>

## 🩹 Patches list

<!-- PATCHES_START EXPANDED -->
> **[v1.2.5](https://github.com/sabbirdj/viper-patches/releases/tag/v1.2.5)**&nbsp;&nbsp;•&nbsp;&nbsp;`main`&nbsp;&nbsp;•&nbsp;&nbsp;2 patches total
<details open>
<summary>📦 APKS Installer&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.inwepo.apkinstaller`

**🎯 Supported versions:**

| 2.8-Google |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Remove ads (APKS Installer)](#remove-ads-apks-installer) | Disables all ads by forcing the master ads flag MyApplication.m to false and making the ad initializer a no-op. Verified on APKS Installer 2.8-Google. |  |

</details>

<details open>
<summary>📦 SAI&nbsp;&nbsp;•&nbsp;&nbsp;1 patch</summary>
<br>

**Package name:** `com.mtv.sai`

**🎯 Supported versions:**

| 2.4.3 |
| :---: |

| 💊&nbsp;Patch | 📜&nbsp;Description | ⚙️&nbsp;Options |
|----------|----------------|-----------|
| [Unlock SAI Premium](#unlock-sai-premium) | Activates SAI Premium by forcing the local entitlement check to true in all three places that read it (ck0, fk0, dk0). Verified on SAI 2.4.3. |  |

</details>

<!-- PATCHES_END -->

### 🛠️ Building locally

- Run `./gradlew buildAndroid`
- The built patches .mpp file is found in `patches/build/libs/patches-*.mpp`
- Patch the mpp file using [Morphe-Desktop](https://github.com/MorpheApp/morphe-desktop)
  like any other patch bundle.

See the [Morphe documentation](https://github.com/MorpheApp/morphe-documentation) for more information.

## 📜 License

UserXYZ Patches are licensed under the [GNU General Public License v3.0](LICENSE)
