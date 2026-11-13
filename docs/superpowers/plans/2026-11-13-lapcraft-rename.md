# Rename Pocket Sim Engineer to LapCraft Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rename the app, its Kotlin packages, `applicationId`, release assets, and repository to LapCraft, with the library-transfer bridge (already shipped) carrying the user's laps across.

**Architecture:** A mechanical identity change: move four source roots, rewrite package/class/brand strings across ~170 files, switch `namespace`/`applicationId` to `io.github.jlai403.lapcraft`, then point the updater and CI at the renamed repo. No behavior changes; the existing test suite is the gate.

**Tech Stack:** Gradle (Kotlin DSL), Android manifest, GitHub Actions, `gh` CLI.

**Spec:** this plan (decisions confirmed 2026-11-13: label `LapCraft`, repo renamed, `versionName` 0.2.0).

## Global Constraints

- New `applicationId` = `io.github.jlai403.lapcraft`; the old package stays installable, so the two apps coexist until the old one is uninstalled.
- Launcher label is exactly `LapCraft`. Theme/application classes become `LapCraftTheme` / `LapCraftApp`.
- `versionName = "0.2.0"`; `versionCode = 1000 + commit count` is untouched.
- Release URLs move to `github.com/jlai403/lapcraft`; GitHub redirects the old paths, so installed old builds keep checking successfully.
- Dated documents under `docs/specs/` and `docs/superpowers/plans/` are historical records: do not rewrite them.
- No behavior, storage-layout, or telemetry changes in this plan.

## Review Focus

- **Fresh install after rename:** LapCraft starts empty, imports the exported zip, and every lap opens with charts and a playable replay.
- **Old app still installed:** both apps coexist; the old one is uninstallable without touching LapCraft's data.
- **Updater:** a LapCraft build downloads its APK from the renamed repo and verifies the SHA-256; an old build following the redirect resolves `latest.json`.
- **Share sheet:** the target label reads LapCraft, not the old name.
- **Leftover identity:** no `pocketsimengineer` in compiled classes, manifest, or release asset names.

---

### Task 1: Move source roots and rewrite package identity

**Files:**
- Move: `app/src/{main,test}/kotlin/io/github/jlai403/pocketsimengineer` → `.../lapcraft`
- Move: `core/src/{main,test}/kotlin/io/github/jlai403/pocketsimengineer` → `.../lapcraft`
- Rename: `app/.../app/PocketSimEngineerApp.kt` → `LapCraftApp.kt`
- Modify: all ~170 files mentioning `pocketsimengineer`, `PocketSimEngineer`, `Pocket Sim Engineer`, `pocket-sim-engineer`
- Modify: `app/build.gradle.kts` (`namespace`, `applicationId`, `versionName`)
- Modify: `settings.gradle.kts` (`rootProject.name`)

- [ ] **Step 1: Branch**

```bash
git checkout -b rename-to-lapcraft
```

- [ ] **Step 2: Move the roots and the app class**

```bash
for root in app/src/main/kotlin app/src/test/kotlin core/src/main/kotlin core/src/test/kotlin; do
  git mv "$root/io/github/jlai403/pocketsimengineer" "$root/io/github/jlai403/lapcraft"
done
git mv app/src/main/kotlin/io/github/jlai403/lapcraft/app/PocketSimEngineerApp.kt \
       app/src/main/kotlin/io/github/jlai403/lapcraft/app/LapCraftApp.kt
```

- [ ] **Step 3: Rewrite strings in tracked files**

Replace in this order (longest first), skipping `.git`:

1. `Pocket Sim Engineer` → `LapCraft`
2. `PocketSimEngineer` → `LapCraft`
3. `io.github.jlai403.pocketsimengineer` → `io.github.jlai403.lapcraft`
4. `pocketsimengineer` → `lapcraft`
5. `pocket-sim-engineer` → `lapcraft`
6. `"PSE"` → `"LapCraft"` (log tags), and `PSE →` → `LapCraft →` (README workflow)

Do not touch `docs/specs/2026-11-10-*` or `docs/superpowers/plans/2026-11-1[012]-*`.

- [ ] **Step 4: Build config**

`app/build.gradle.kts`: `namespace = "io.github.jlai403.lapcraft"`, `applicationId = "io.github.jlai403.lapcraft"`, `versionName = "0.2.0"`. `settings.gradle.kts`: `rootProject.name = "LapCraft"`.

- [ ] **Step 5: Verify no leftovers outside history**

```bash
rg -n "pocketsimengineer|PocketSimEngineer|Pocket Sim Engineer|pocket-sim-engineer|\"PSE\"" \
  -g '!docs/specs/**' -g '!docs/superpowers/plans/2026-11-1*' -g '!.git'
```

Expected: no output.

- [ ] **Step 6: Full check**

Run: `ANDROID_HOME=/opt/android-sdk ./gradlew :core:test :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "refactor: rename the app and package to LapCraft"
```

---

### Task 2: Point the updater and CI at the renamed repository

**Files:**
- Modify: `app/src/main/kotlin/io/github/jlai403/lapcraft/app/updater/UpdateManager.kt`
- Modify: `.github/workflows/android.yml`
- Modify: `README.md`, `AGENTS.md`, `DESIGN.md`, `GLOSSARY.md`

- [ ] **Step 1:** Confirm the URL constants read `https://github.com/jlai403/lapcraft/releases/...` (Task 1's rewrite handles them) and that `ACTION_INSTALL_STATUS` uses the new package.
- [ ] **Step 2:** Confirm CI stages `dist/LapCraft-*.apk`, uploads artifacts named `lapcraft-debug-<sha>`, and passes the new asset names to `gh release`.
- [ ] **Step 3:** Check docs: README title/architecture tree/build commands, `AGENTS.md` module paths, DESIGN.md intro, GLOSSARY title; verify no stale `PocketSimEngineer-preview.apk` references.
- [ ] **Step 4:** Commit any follow-up doc/CI edits with `git commit -m "docs: finish the LapCraft rename"`.

---

### Task 3: Verify the rename end to end

- [ ] **Step 1:** `ANDROID_HOME=/opt/android-sdk ./gradlew :app:assembleDebug`, then confirm the APK's identity:
  `aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head -3` → `package: name='io.github.jlai403.lapcraft'` and `application-label:'LapCraft'`.
- [ ] **Step 2:** Merge to `main` and push (this publishes the preview under the new asset names).
- [ ] **Step 3:** Stop for the user: install LapCraft beside the old app → Settings → Library → **Import library…** → pick the zip exported from the old build → confirm the lap count matches, open a lap, play its replay.
- [ ] **Step 4:** After the user confirms, uninstall the old package and delete `Downloads/lapcraft-library-*.zip` once a fresh backup exists.

---

### Task 4: Rename the GitHub repository

- [ ] **Step 1:** `gh repo rename lapcraft --repo jlai403/pocket-sim-engineer` (confirm with the user first: it changes the public URL).
- [ ] **Step 2:** `git remote set-url origin git@github.com:jlai403/lapcraft.git`, then `git fetch` and confirm `main` tracks `origin/main`.
- [ ] **Step 3:** Verify the updater end to end: a preview build's Settings → Updates reaches `latest.json`, downloads the APK, and verifies its hash.
- [ ] **Step 4:** Confirm an old-package build still resolves `releases/latest/download/latest.json` through GitHub's redirect.

---

## Known behavior after the rename

Old-package installs keep checking the preview channel and will offer the LapCraft APK; installing it creates the second app rather than replacing the old one. That is expected — the old app is uninstalled by hand after the import.
