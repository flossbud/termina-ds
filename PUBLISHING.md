# Publishing This Fork

This fork contains changes in the main repository and in the `libultraship` submodule. Publish the submodule fork first so the main repository can point at a commit that GitHub users can actually clone.

## 1. Create The libultraship Fork

Create a fork of:

https://github.com/Waterdish/libultraship

Then push the local Android fork branch:

```sh
cd libultraship
git remote add fork https://github.com/YOUR_GITHUB_USERNAME/libultraship.git
git push -u fork android-fork/termina-tango-1.2.0
```

The Android fork commit is:

```text
86e93fd4 Apply Android fork fixes
```

## 2. Point The Main Repo At Your libultraship Fork

After the `libultraship` branch is pushed, update `.gitmodules`:

```ini
[submodule "libultraship"]
	path = libultraship
	url = https://github.com/YOUR_GITHUB_USERNAME/libultraship.git
```

Then sync and commit the submodule pointer from the main repository:

```sh
git submodule sync libultraship
git add .gitmodules libultraship
git commit -m "Point libultraship at Android fork"
```

## 3. Publish The Main Repository

Create a fork or new repository for this Android app, then push the main `android` branch:

```sh
git remote add fork https://github.com/YOUR_GITHUB_USERNAME/2ship2harkinian-Android.git
git push -u fork android
```

Before creating a GitHub Release, confirm no ROM, O2R/OTR file, extracted game asset, or local SDK file is committed.

The release APK can be attached to GitHub Releases, but should not be committed to the repository.
