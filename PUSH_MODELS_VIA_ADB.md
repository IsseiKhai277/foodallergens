# Push Model Files to Device via ADB

## Quick Guide

This method lets you build a small APK (~27 MB) and push the large model files directly to your device.

## Prerequisites

1. **ADB installed** (comes with Android Studio)
2. **USB Debugging enabled** on your phone
3. **Phone connected** via USB

## Step-by-Step Instructions

### Step 1: Build and Install the App (Without Models)

```powershell
cd "c:\Users\HP VICTUS\Downloads\foodAllergen"

# Build the APK without models in assets
.\gradlew.bat assembleDebug

# Install on device
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Step 2: Find the App's Internal Storage Path

```powershell
# Launch the app once, then run:
adb shell run-as edu.utem.ftmk.foodallergen pwd
```

This will output something like:
```
/data/user/0/edu.utem.ftmk.foodallergen
```

The models should go in: `/data/user/0/edu.utem.ftmk.foodallergen/files/`

### Step 3: Push Model Files

```powershell
# Set your model file path
$MODEL_PATH = "C:\path\to\your\models"

# Push each model file
adb push "$MODEL_PATH\Llama-3.2-1B-Instruct-Q4_K_M.gguf" /sdcard/
adb push "$MODEL_PATH\Llama-3.2-3B-Instruct-Q4_K_M.gguf" /sdcard/
adb push "$MODEL_PATH\Phi-3-mini-4k-instruct-q4.gguf" /sdcard/
adb push "$MODEL_PATH\Phi-3.5-mini-instruct-Q4_K_M.gguf" /sdcard/
adb push "$MODEL_PATH\qwen2.5-1.5b-instruct-q4_k_m.gguf" /sdcard/
adb push "$MODEL_PATH\qwen2.5-3b-instruct-q4_k_m.gguf" /sdcard/
adb push "$MODEL_PATH\Vikhr-Gemma-2B-instruct-Q4_K_M.gguf" /sdcard/

# Move from sdcard to app's internal storage
adb shell "run-as edu.utem.ftmk.foodallergen cp /sdcard/Llama-3.2-1B-Instruct-Q4_K_M.gguf files/"
adb shell "run-as edu.utem.ftmk.foodallergen cp /sdcard/Llama-3.2-3B-Instruct-Q4_K_M.gguf files/"
adb shell "run-as edu.utem.ftmk.foodallergen cp /sdcard/Phi-3-mini-4k-instruct-q4.gguf files/"
adb shell "run-as edu.utem.ftmk.foodallergen cp /sdcard/Phi-3.5-mini-instruct-Q4_K_M.gguf files/"
adb shell "run-as edu.utem.ftmk.foodallergen cp /sdcard/qwen2.5-1.5b-instruct-q4_k_m.gguf files/"
adb shell "run-as edu.utem.ftmk.foodallergen cp /sdcard/qwen2.5-3b-instruct-q4_k_m.gguf files/"
adb shell "run-as edu.utem.ftmk.foodallergen cp /sdcard/Vikhr-Gemma-2B-instruct-Q4_K_M.gguf files/"

# Clean up sdcard copies
adb shell "rm /sdcard/*.gguf"
```

### Step 4: Verify Files Are Present

```powershell
# List files in app's directory
adb shell "run-as edu.utem.ftmk.foodallergen ls -lh files/"
```

You should see all your GGUF files listed.

## Automated Script

Create a PowerShell script to push all models at once:

```powershell
# push-models.ps1
$MODEL_PATH = "C:\path\to\your\models"
$PACKAGE = "edu.utem.ftmk.foodallergen"

$models = @(
    "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
    "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
    "Phi-3-mini-4k-instruct-q4.gguf",
    "Phi-3.5-mini-instruct-Q4_K_M.gguf",
    "qwen2.5-1.5b-instruct-q4_k_m.gguf",
    "qwen2.5-3b-instruct-q4_k_m.gguf",
    "Vikhr-Gemma-2B-instruct-Q4_K_M.gguf"
)

Write-Host "Pushing models to device..."

foreach ($model in $models) {
    $localFile = Join-Path $MODEL_PATH $model
    
    if (Test-Path $localFile) {
        Write-Host "Pushing $model..." -ForegroundColor Cyan
        
        # Push to sdcard first (accessible without root)
        adb push "$localFile" /sdcard/
        
        # Copy to app's private directory
        adb shell "run-as $PACKAGE cp /sdcard/$model files/"
        
        # Verify
        $result = adb shell "run-as $PACKAGE ls files/$model" 2>&1
        if ($result -match $model) {
            Write-Host "✓ $model pushed successfully" -ForegroundColor Green
        } else {
            Write-Host "✗ Failed to push $model" -ForegroundColor Red
        }
        
        # Clean up sdcard
        adb shell "rm /sdcard/$model"
    } else {
        Write-Host "✗ File not found: $localFile" -ForegroundColor Yellow
    }
}

Write-Host "`nDone! Listing all files:" -ForegroundColor Green
adb shell "run-as $PACKAGE ls -lh files/"
```

Save as `push-models.ps1` and run:
```powershell
.\push-models.ps1
```

## Quick Push (One Model for Testing)

For quick testing, push just one small model:

```powershell
# Push just qwen2.5-1.5b for testing
adb push "C:\path\to\qwen2.5-1.5b-instruct-q4_k_m.gguf" /sdcard/
adb shell "run-as edu.utem.ftmk.foodallergen cp /sdcard/qwen2.5-1.5b-instruct-q4_k_m.gguf files/"
adb shell "rm /sdcard/qwen2.5-1.5b-instruct-q4_k_m.gguf"

# Verify
adb shell "run-as edu.utem.ftmk.foodallergen ls -lh files/"
```

## Alternative: Push to External Storage (If Root Available)

If your device is rooted or you're using an emulator:

```powershell
# Push directly to app's directory (requires root)
adb root
adb push "C:\path\to\model.gguf" /data/data/edu.utem.ftmk.foodallergen/files/

# Set correct permissions
adb shell "chown -R u0_a### /data/data/edu.utem.ftmk.foodallergen/files/"
adb shell "chmod 644 /data/data/edu.utem.ftmk.foodallergen/files/*.gguf"
```

## Troubleshooting

### "run-as: Package 'edu.utem.ftmk.foodallergen' is not debuggable"
- Make sure you installed a debug build, not release
- Rebuild with: `.\gradlew.bat assembleDebug`

### "Permission denied"
- Use the two-step method: push to `/sdcard/` first, then copy to app directory

### "No space left on device"
- Check device storage: `adb shell df -h`
- Free up space on your phone
- Push models one at a time

### File transfer is slow
- Normal for large files (5GB can take 10-20 minutes)
- Use USB 3.0 cable for faster transfer
- Consider pushing smaller models first for testing

### Verify installation path
```powershell
# Check where the app expects models
adb shell "run-as edu.utem.ftmk.foodallergen pwd"
# Should show: /data/user/0/edu.utem.ftmk.foodallergen

# List files directory
adb shell "run-as edu.utem.ftmk.foodallergen ls -la files/"
```

## Advantages of ADB Push

✅ **No build size limits** - APK stays small (27 MB)
✅ **Faster development** - Don't rebuild when swapping models  
✅ **Flexible testing** - Easy to add/remove models
✅ **Works immediately** - No need for AAB or asset packs

## Complete Workflow

```powershell
# 1. Build and install app (once)
.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk

# 2. Push models (once, or when models change)
.\push-models.ps1

# 3. Launch and test
adb shell am start -n edu.utem.ftmk.foodallergen/.MainActivity

# 4. View logs
adb logcat -s MainActivity:I
```

Perfect for development and testing! 🚀
