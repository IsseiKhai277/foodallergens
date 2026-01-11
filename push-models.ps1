# push-models.ps1
# Automated script to push GGUF model files to Android device via ADB

# CONFIGURATION - Update these paths
$MODEL_PATH = "C:\Users\HP VICTUS\Downloads\assets"
$PACKAGE = "edu.utem.ftmk.foodallergen"

# Model files to push
$models = @(
    "Llama-3.2-1B-Instruct-Q4_K_M.gguf",
    "Llama-3.2-3B-Instruct-Q4_K_M.gguf",
    "Phi-3-mini-4k-instruct-q4.gguf",
    "Phi-3.5-mini-instruct-Q4_K_M.gguf",
    "qwen2.5-1.5b-instruct-q4_k_m.gguf",
    "qwen2.5-3b-instruct-q4_k_m.gguf",
    "Vikhr-Gemma-2B-instruct-Q4_K_M.gguf"
)

Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "   AI Model Pusher for FoodAllergen App  " -ForegroundColor Cyan
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host ""

# Check if ADB command is available
if (-not (Get-Command adb -ErrorAction SilentlyContinue)) {
    Write-Host "[-] ERROR: 'adb.exe' not found in your system's PATH." -ForegroundColor Red
    Write-Host "    Please add the Android SDK platform-tools directory to your PATH environment variable." -ForegroundColor Yellow
    Write-Host "    Example: C:\Users\YourUser\AppData\Local\Android\Sdk\platform-tools" -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit
}

# Check if a device is connected
$deviceState = adb get-state
if ($deviceState -ne "device") {
    Write-Host "[-] ERROR: No device found or device not ready." -ForegroundColor Red
    Write-Host "    Please connect a device and ensure USB Debugging is enabled and authorized." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit
}

Write-Host "[*] Device found:" -ForegroundColor Green
adb devices -l
Write-Host ""

# Resolve the full path for the source directory
$fullSourcePath = (Resolve-Path -Path $MODEL_PATH).Path
Write-Host "[*] Model Source Folder: $fullSourcePath" -ForegroundColor Green

# Construct the target directory on the device
$targetDir = "/data/data/$PACKAGE/files"
Write-Host "[*] Target directory on device: $targetDir" -ForegroundColor Green
Write-Host ""

# Ensure the target directory exists on the device
Write-Host "[*] Ensuring target directory exists on device..." -ForegroundColor White
adb shell "run-as $PACKAGE mkdir -p $targetDir"
Write-Host ""

# Find all .gguf files
$modelFiles = Get-ChildItem -Path $fullSourcePath -Filter "*.gguf"

if ($modelFiles.Count -eq 0) {
    Write-Host "[!] WARNING: No .gguf files found in '$fullSourcePath'." -ForegroundColor Yellow
    Read-Host "Press Enter to exit"
    exit
}

Write-Host "--- Starting Model Push ---" -ForegroundColor Cyan

$successCount = 0
$failCount = 0

foreach ($file in $modelFiles) {
    $localFile = $file.FullName
    $fileName = $file.Name
    $fileSizeMB = [math]::Round((Get-Item $localFile).Length / 1MB, 2)

    Write-Host ""
    Write-Host "[>] Pushing model: $fileName ($($fileSizeMB) MB)" -ForegroundColor White

    # Step 1: Push to /sdcard/ (internal storage, accessible without root)
    Write-Host "    [1/3] Pushing to temporary location..." -ForegroundColor Gray
    $pushOutput = adb push "$localFile" "/sdcard/$fileName" 2>&1

    if ($LASTEXITCODE -eq 0) {
        # Step 2: Copy to /data/local/tmp with readable permissions
        Write-Host "    [2/3] Copying to app directory..." -ForegroundColor Gray
        $tmpCopy = adb shell "cp /sdcard/$fileName /data/local/tmp/$fileName && chmod 644 /data/local/tmp/$fileName" 2>&1
        
        # Copy from /data/local/tmp to app directory using run-as
        $copyOutput = adb shell "run-as $PACKAGE cp /data/local/tmp/$fileName /data/data/$PACKAGE/files/$fileName" 2>&1

        # Step 3: Verify the file exists and has correct size
        Write-Host "    [3/3] Verifying..." -ForegroundColor Gray
        $verifyOutput = adb shell "run-as $PACKAGE ls -lh /data/data/$PACKAGE/files/$fileName" 2>&1

        if ($verifyOutput -notmatch "No such file" -and $verifyOutput -match $fileName -and $verifyOutput -notmatch "^\s*0\s") {
            Write-Host "[+] SUCCESS: $fileName pushed successfully." -ForegroundColor Green
            $successCount++
        } else {
            Write-Host "[!] FAILED: Could not verify $fileName in app directory." -ForegroundColor Red
            Write-Host "    Error Details: $verifyOutput" -ForegroundColor Yellow
            $failCount++
        }

        # Clean up temporary files
        adb shell "rm -f /sdcard/$fileName /data/local/tmp/$fileName" 2>&1 | Out-Null
    } else {
        Write-Host "[!] FAILED: Could not push $fileName to temporary location." -ForegroundColor Red
        Write-Host "    Error Details: $pushOutput" -ForegroundColor Yellow
        $failCount++
    }
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Results: $successCount successful, $failCount failed" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Yellow" })
Write-Host "========================================" -ForegroundColor Cyan

if ($successCount -gt 0) {
    Write-Host ""
    Write-Host "[*] Files on device:" -ForegroundColor Green
    adb shell "run-as $PACKAGE sh -c 'ls -lh /data/data/$PACKAGE/files/*.gguf'" 2>&1
    Write-Host ""
    Write-Host "[+] Done! You can now run the app." -ForegroundColor Green
}

if ($failCount -gt 0) {
    Write-Host ""
    Write-Host "[!] Troubleshooting:" -ForegroundColor Yellow
    Write-Host "    - Make sure the app is installed (debug build)" -ForegroundColor White
    Write-Host "    - Check device has enough storage" -ForegroundColor White
    Write-Host "    - Ensure USB debugging is enabled" -ForegroundColor White
}

Write-Host ""
Read-Host "Press Enter to exit"

# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host "  Model Pusher for Food Allergen App  " -ForegroundColor Cyan
# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host ""
#
# # Check if ADB is available
# $adbCheck = Get-Command adb -ErrorAction SilentlyContinue
# if (-not $adbCheck) {
#     Write-Host "ERROR: ADB not found in PATH" -ForegroundColor Red
#     Write-Host "Make sure Android SDK platform-tools is installed" -ForegroundColor Yellow
#     exit 1
# }
#
# # Check if device is connected
# $devices = adb devices
# if ($devices -notmatch "device$") {
#     Write-Host "ERROR: No device connected" -ForegroundColor Red
#     Write-Host "Please connect your device via USB and enable USB debugging" -ForegroundColor Yellow
#     exit 1
# }
#
# Write-Host "Device connected" -ForegroundColor Green
# Write-Host ""
#
# # Verify app is installed
# $appCheck = adb shell pm list packages | Select-String $PACKAGE
# if (-not $appCheck) {
#     Write-Host "WARNING: App not found on device" -ForegroundColor Yellow
#     Write-Host "Install the app first with: .\gradlew.bat assembleDebug ; adb install -r app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Yellow
#     Write-Host ""
#     Read-Host "Press Enter to continue anyway, or Ctrl+C to exit"
# }
#
# # Check if model path exists
# if (-not (Test-Path $MODEL_PATH)) {
#     Write-Host "ERROR: Model path not found: $MODEL_PATH" -ForegroundColor Red
#     Write-Host "Please update MODEL_PATH in this script" -ForegroundColor Yellow
#     exit 1
# }
#
# Write-Host "Source folder: $MODEL_PATH" -ForegroundColor Cyan
# Write-Host "Target package: $PACKAGE" -ForegroundColor Cyan
# Write-Host ""
# Write-Host "Pushing models to device..." -ForegroundColor Cyan
# Write-Host ""
#
# $successCount = 0
# $failCount = 0
#
# foreach ($model in $models) {
#     $localFile = Join-Path $MODEL_PATH $model
#
#     if (Test-Path $localFile) {
#         $fileSize = (Get-Item $localFile).Length / 1MB+
#         $index = $models.IndexOf($model) + 1
#         $total = $models.Count
#         $sizeMB = [math]::Round($fileSize, 2)
#
#         Write-Host ('[' + $index + '/' + $total + '] Pushing ' + $model + ' (' + $sizeMB + ' MB)...') -ForegroundColor Cyan
#
#         # Push to sdcard first (accessible without root)
#         $pushResult = adb push "$localFile" /sdcard/ 2>&1
#
#         if ($LASTEXITCODE -eq 0) {
#             # Copy to app's private directory
#             $copyResult = adb shell "run-as $PACKAGE cp /sdcard/$model files/" 2>&1
#
#             # Verify
#             $verifyResult = adb shell "run-as $PACKAGE ls files/$model" 2>&1
#             if ($verifyResult -match $model) {
#                 Write-Host "  OK: $model pushed successfully" -ForegroundColor Green
#                 $successCount++
#             } else {
#                 Write-Host "  FAILED to verify $model" -ForegroundColor Red
#                 Write-Host "    Error: $verifyResult" -ForegroundColor Yellow
#                 $failCount++
#             }
#
#             # Clean up sdcard
#             adb shell "rm /sdcard/$model" 2>&1 | Out-Null
#         } else {
#             Write-Host "  FAILED to push $model to sdcard" -ForegroundColor Red
#             Write-Host "    Error: $pushResult" -ForegroundColor Yellow
#             $failCount++
#         }
#     } else {
#         Write-Host "  File not found: $model" -ForegroundColor Yellow
#         $failCount++
#     }
#     Write-Host ""
# }
#
# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host "Results: $successCount successful, $failCount failed" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Yellow" })
# Write-Host "========================================" -ForegroundColor Cyan
# Write-Host ""
#
# if ($successCount -gt 0) {
#     Write-Host "Files on device:" -ForegroundColor Green
#     adb shell "run-as $PACKAGE ls -lh files/" 2>&1
#     Write-Host ""
#     Write-Host "Done! You can now run the app." -ForegroundColor Green
#     Write-Host ""
#     Write-Host "To view app logs:" -ForegroundColor Cyan
#     Write-Host "  adb logcat -s MainActivity:I" -ForegroundColor White
# }
#
# if ($failCount -gt 0) {
#     Write-Host ""
#     Write-Host "Troubleshooting:" -ForegroundColor Yellow
#     Write-Host "  1. Make sure app is installed (debug build)" -ForegroundColor White
#     Write-Host "  2. Check device has enough storage" -ForegroundColor White
#     Write-Host "  3. Try pushing files one at a time" -ForegroundColor White
# }
