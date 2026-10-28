# Installing Margin APK

## Building and Installing the APK

### Option 1: Download from GitHub Actions (Easiest)

1. **Go to GitHub Actions**
   - Navigate to your repository on GitHub
   - Click on the "Actions" tab
   - Select the latest "Build APK" workflow run

2. **Download the APK**
   - Scroll down to "Artifacts" section
   - Download "margin-debug-apk"
   - This will download a `.zip` file containing `app-debug.apk`

3. **Install on Your Android Device**
   
   **Via Android Studio:**
   - Open Android Studio
   - Go to Build → Select Build Variant → Choose "debug"
   - Go to Run → Install and Run (or press Shift+F10)
   - Select your device

   **Via ADB (Command Line):**
   ```bash
   # Connect your Android device via USB
   adb install -r path/to/app-debug.apk
   ```

   **Via File Manager (Manual):**
   - Transfer the APK file to your Android device
   - Open the file manager on your device
   - Locate and tap the APK file
   - Follow the installation prompts

### Option 2: Manual Build

1. **Clone the repository**
   ```bash
   git clone <your-repo-url>
   cd margin-android
   ```

2. **Build the APK**
   ```bash
   ./gradlew assembleDebug
   ```

3. **Install via ADB**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

### Option 3: Release Builds (Tagged Versions)

1. **Create a GitHub Release**
   - Tag a commit: `git tag v1.0.0`
   - Push the tag: `git push origin v1.0.0`
   - GitHub will automatically build and attach the APK to the release

2. **Download from Releases**
   - Go to "Releases" section on GitHub
   - Download the APK from the latest release

## Prerequisites

- Android device with USB debugging enabled (for ADB installation)
- Or a file manager app to install from the APK file directly
- Minimum Android API level: Check your `build.gradle.kts` for `minSdk`

## Troubleshooting

**"App not installed" error:**
- Try uninstalling the previous version first: `adb uninstall com.yourpackage.margin`
- Or use `-r` flag to replace existing version: `adb install -r app-debug.apk`

**"File cannot be opened" on device:**
- Your device needs "Unknown Sources" enabled in Settings → Security
- Or enable "Install from Unknown Sources" for your file manager app

**ADB command not found:**
- Ensure Android SDK Platform Tools are installed
- Add SDK tools to PATH, or use full path to adb
