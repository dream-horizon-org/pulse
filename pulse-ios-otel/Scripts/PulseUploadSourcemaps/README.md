# Upload dSYMs with PulseKit

PulseKit bundles **`pulse-upload-dsym.sh`** inside **`PulseKit.framework`**. It uploads your app’s **dSYM** to your Pulse backend so crashes can be symbolicated.

**Requirements:** `bash`, `curl`, and **`/usr/bin/zip`** (dSYMs are folders). You also need your **upload URL** and **API key** from Pulse.

---

## Where the script lives

| Integration | Where to run it from |
|-------------|----------------------|
| **SPM** (or any setup that **embeds** `PulseKit.framework` in the app) | `${BUILT_PRODUCTS_DIR}/${FRAMEWORKS_FOLDER_PATH}/PulseKit.framework/pulse-upload-dsym.sh` |
| **CocoaPods** (`use_frameworks!`, xcframework) | Usually the **same** embedded path. If that file is missing during **archive**, use **`${PODS_XCFRAMEWORKS_BUILD_DIR}/PulseKit/PulseKit.framework/pulse-upload-dsym.sh`** |

Build your app **once** in Xcode so `PulseKit.framework` exists; the script sits next to the other framework resources.

---

## Xcode: Run Script build phase

1. Open your app target → **Build Phases** → **+** → **New Run Script Phase**.
2. Place it **after** “Embed Frameworks” (or late in the list).
3. Shell: **`/bin/bash`**.

**Recommended body** (works for **SPM** and **CocoaPods** when the framework is embedded):

```bash
UPLOAD_SCRIPT="${BUILT_PRODUCTS_DIR}/${FRAMEWORKS_FOLDER_PATH}/PulseKit.framework/pulse-upload-dsym.sh"
if [[ ! -f "${UPLOAD_SCRIPT}" && -n "${PODS_XCFRAMEWORKS_BUILD_DIR:-}" ]]; then
  UPLOAD_SCRIPT="${PODS_XCFRAMEWORKS_BUILD_DIR}/PulseKit/PulseKit.framework/pulse-upload-dsym.sh"
fi

bash "${UPLOAD_SCRIPT}" \
  --url="https://YOUR_HOST/v1/symbolicate/file/upload" \
  --api-key="${PULSE_API_KEY}" \
  --dsym="${DWARF_DSYM_FOLDER_PATH}/${DWARF_DSYM_FILE_NAME}" \
  --app-version="${MARKETING_VERSION}" \
  --version-code="${CURRENT_PROJECT_VERSION}" \
  --type=dsym
```

- Replace the **`--url`** value with the URL Pulse gave you.
- Define **`PULSE_API_KEY`** for the build (Xcode scheme **Environment Variables**, CI secret, or **User-Defined** build setting). Avoid committing real keys in the project file.

**Optional:** instead of `--url` / `--api-key` / `--dsym`, you can set **`PULSE_UPLOAD_API_URL`**, **`PULSE_UPLOAD_API_KEY`**, **`PULSE_UPLOAD_DSYM_PATH`**. CLI flags win if both are set.

**Skip uploads for Debug** (optional wrapper):

```bash
if [[ "${CONFIGURATION}" != "Release" ]]; then exit 0; fi
# …then the bash "${UPLOAD_SCRIPT}" … block above
```

---

## Terminal: manual upload

Use this for one-off uploads or CI that already has a **`.dSYM`** on disk.

**1. Locate the script** (after a local build):

```bash
find ~/Library/Developer/Xcode/DerivedData -path "*PulseKit.framework/pulse-upload-dsym.sh" 2>/dev/null | grep -v Index.noindex | head -5
```

Pick a path that matches the same **configuration** (e.g. **Release**) and **platform** as the dSYM you are uploading.

**2. Run:**

```bash
bash "/full/path/to/PulseKit.framework/pulse-upload-dsym.sh" \
  --url="https://YOUR_HOST/v1/symbolicate/file/upload" \
  --api-key="YOUR_API_KEY" \
  --dsym="/full/path/to/YourApp.app.dSYM" \
  --app-version="1.0.0" \
  --version-code="123" \
  --type=dsym
```

**Help:** `bash "/path/to/pulse-upload-dsym.sh" --help`

---

## If something fails

| Symptom | What to check |
|--------|----------------|
| **No script path** | Build the app target that links PulseKit; confirm **`PulseKit.framework`** is inside the built **`.app`** (SPM/CocoaPods both embed it for typical iOS app targets). |
| **Script not found in Run Script** | For CocoaPods archives, rely on the **`PODS_XCFRAMEWORKS_BUILD_DIR`** fallback above. |
| **Errors when running** | Invoke with **`bash …`**, not `sh`. |
