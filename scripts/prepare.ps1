$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$upstream = Join-Path $root 'upstream'
$pin = 'ae58109f3acdd53ec2d2b3fb439c2a2ef1886221'
if (!(Test-Path "$upstream/.git")) {
    & git clone https://github.com/BiliRoamingX/BiliRoamingX.git $upstream
    if ($LASTEXITCODE) { throw 'Clone failed' }
    & git -C $upstream checkout --detach $pin
    if ($LASTEXITCODE) { throw 'Checkout failed' }
}
if ((& git -C $upstream rev-parse HEAD) -ne $pin) { throw 'Unexpected upstream revision' }
$sdkPath = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } elseif ($env:ANDROID_SDK_ROOT) { $env:ANDROID_SDK_ROOT } else { Join-Path $env:LOCALAPPDATA 'Android/Sdk' }
foreach ($part in @('platforms/android-35/android.jar','build-tools/35.0.0/aapt2.exe','ndk/28.2.13676358/source.properties','cmake/3.22.1/bin/cmake.exe')) {
    if (!(Test-Path (Join-Path $sdkPath $part))) { throw "Required SDK component missing: $sdkPath/$part" }
}
[IO.File]::WriteAllText((Join-Path $upstream 'local.properties'), 'sdk.dir=' + $sdkPath.Replace('\','/'), [Text.UTF8Encoding]::new($false))
& git -C $upstream submodule update --init --recursive
if ($LASTEXITCODE) { throw 'Submodule checkout failed' }

function Replace-Once([string]$relative, [string]$before, [string]$after) {
    $path = Join-Path $upstream $relative
    $text = [IO.File]::ReadAllText($path)
    if ($text.Contains($after) -and ($after.Contains($before) -or !$text.Contains($before))) { return }
    if (!$text.Contains($before)) { throw "Source mismatch: $relative" }
    [IO.File]::WriteAllText($path, $text.Replace($before, $after), [Text.UTF8Encoding]::new($false))
}
$java = 'integrations/app/src/main/java/app/revanced/bilibili'
$logging = Join-Path $upstream 'integrations/libs/Dobby/external/logging/logging.c'
$loggingText = [IO.File]::ReadAllText($logging)
if (!$loggingText.Contains('/* BiliClean: Android headers belong at file scope. */')) {
    $loggingText = $loggingText.Replace('#include <android/log.h>', '')
    $loggingText = "/* BiliClean: Android headers belong at file scope. */`n#if defined(__ANDROID__)`n#include <android/log.h>`n#endif`n" + $loggingText
    [IO.File]::WriteAllText($logging,$loggingText,[Text.UTF8Encoding]::new($false))
}
Replace-Once 'build-logic/src/main/kotlin/Versions.kt' 'const val NDK = "26.3.11579264"' 'const val NDK = "28.2.13676358"'
Replace-Once 'build-logic/src/main/kotlin/Versions.kt' 'const val JVM_TARGET_PATCHES = 11' 'const val JVM_TARGET_PATCHES = 17'
Replace-Once 'build-logic/src/main/kotlin/Projects.kt' '        compileSdkVersion(Versions.COMPILE_SDK)' "        compileSdkVersion(Versions.COMPILE_SDK)`n        buildToolsVersion = `"35.0.0`""
Replace-Once 'patches/build.gradle.kts' 'implementation(libs.revanced.patcher)' 'implementation(files(rootProject.file("../local/revanced-cli.jar")))'
$target = Join-Path $upstream "$java/clean"
New-Item -ItemType Directory -Force $target | Out-Null
Copy-Item "$root/src/app/revanced/bilibili/clean/*" $target -Force
$delegate = Join-Path $upstream "$java/patches/main/ApplicationDelegate.java"
$delegateText = [IO.File]::ReadAllText($delegate)
$delegateText = $delegateText.Replace('System.loadLibrary("biliroamingx");', '// Native compatibility is a pinned static patch; no global exit hook.')
if (!$delegateText.Contains('BiliClean minimal startup')) {
    $pattern = '(?s)        long start = System.currentTimeMillis\(\);.*?Logger\.debug\(\(\) -> String\.format\("Initializing BiliRoamingX.*?;\r?\n'
    if ([regex]::Matches($delegateText,$pattern).Count -ne 1) { throw 'Startup source mismatch' }
    $delegateText = [regex]::Replace($delegateText,$pattern,"        // BiliClean minimal startup: retain lifecycle tracking only.`n        registerActivityLifecycleCallbacks(new ActivityLifecycleCallback());`n")
    [IO.File]::WriteAllText($delegate,$delegateText,[Text.UTF8Encoding]::new($false))
}
[IO.File]::WriteAllText($delegate,$delegateText,[Text.UTF8Encoding]::new($false))
Replace-Once 'patches/src/main/kotlin/app/revanced/patches/bilibili/misc/settings/patch/SettingsResourcePatch.kt' '"app.revanced.bilibili.settings.fragments.BiliRoamingSettingsFragment"' '"app.revanced.bilibili.clean.CleanSettingsFragment"'
Replace-Once 'patches/src/main/kotlin/app/revanced/patches/bilibili/misc/settings/patch/SettingsResourcePatch.kt' '"@string/biliroaming_settings_title"' '"去广告与空降助手"'

$settings = @'
object Settings {
    @JvmField val CleanAds = BooleanSetting(key = "clean_ads", defValue = true, onChange = { value, _ ->
        if (value) Utils.async { clearSplashConfigCache() }
    })
    @JvmField val CleanAirborne = BooleanSetting(key = "clean_airborne", defValue = true)
    @JvmField val CleanAutoSkip = BooleanSetting(key = "clean_auto_skip", defValue = true)
    @JvmField val CleanNotice = BooleanSetting(key = "clean_notice", defValue = true)
'@
Replace-Once "$java/settings/Settings.kt" 'object Settings {' $settings
Replace-Once "$java/settings/Setting.kt" 'Accounts.userBlocked || (dependency != null && !dependency.get())' 'dependency != null && !dependency.get()'

$patchTarget = Join-Path $upstream 'patches/src/main/kotlin/app/revanced/patches/bilibili/clean'
New-Item -ItemType Directory -Force $patchTarget | Out-Null
Copy-Item "$root/patches/*.kt" $patchTarget -Force
$mainPatch = Join-Path $upstream 'patches/src/main/kotlin/app/revanced/patches/bilibili/misc/integrations/patch/MainActivityPatch.kt'
$mainText = [IO.File]::ReadAllText($mainPatch)
$mainText = $mainText.Replace('invoke-static {p0},', 'invoke-static/range {p0 .. p0},').Replace('invoke-static {p0, p1},', 'invoke-static/range {p0 .. p1},')
[IO.File]::WriteAllText($mainPatch,$mainText,[Text.UTF8Encoding]::new($false))
Replace-Once 'patches/src/main/kotlin/app/revanced/patches/bilibili/misc/settings/patch/FixPreferenceManagerPatch.kt' 'val preferenceManagerDef = PreferenceManagerFingerprint.result?.classDef' @'
val preferenceManagerDef = PreferenceManagerFingerprint.result?.classDef
            ?: context.findClass("Landroidx/preference/Preference;")?.immutableClass?.fields
                ?.singleOrNull { it.name == "mPreferenceManager" }?.type?.let { context.findClass(it)?.immutableClass }
'@

$xmlPath = Join-Path $upstream 'patches/src/main/resources/bilibili/xml/biliroaming_settings.xml'
Copy-Item "$root/resources/clean-settings.xml" $xmlPath -Force
Replace-Once "$java/patches/json/PegasusPatch.java" 'var filterSet = Settings.FilterHomeRecommend.get();' @'
var filterSet = new java.util.HashSet<String>(Settings.FilterHomeRecommend.get());
        if (Settings.CleanAds.get()) filterSet.add("advertisement");
'@
Get-ChildItem (Join-Path $upstream "$java/patches") -Recurse -File | Where-Object Extension -In '.kt','.java' | ForEach-Object {
    $text = [IO.File]::ReadAllText($_.FullName)
    $changed = $text.Replace('Settings.PurifySplash()', 'Settings.CleanAds()').Replace('Settings.PurifySplash.get()', 'Settings.CleanAds.get()').Replace('Settings.BlockUpRcmdAds()', 'Settings.CleanAds()').Replace('Settings.BlockBangumiPageAds()', 'Settings.CleanAds()')
    if ($text -ne $changed) { [IO.File]::WriteAllText($_.FullName,$changed,[Text.UTF8Encoding]::new($false)) }
}
Write-Output "Prepared pinned upstream $pin; player binding remains unverified until AVD playback tests pass."
$jsonPath = Join-Path $upstream "$java/patches/json/JSONPatch.java"
$jsonText = [IO.File]::ReadAllText($jsonPath)
if (!$jsonText.Contains('BiliClean exact models')) {
    $pattern = '(?s)    public static Object parseObjectHook\(Object obj\) \{.*?\r?\n    \}\r?\n'
    if ([regex]::Matches($jsonText,$pattern).Count -ne 1) { throw 'JSON entry source mismatch' }
    $jsonText = [regex]::Replace($jsonText,$pattern,"    public static Object parseObjectHook(Object obj) {`n        // BiliClean exact models; do not resolve removed host classes.`n        return app.revanced.bilibili.clean.CleanJson.filter(obj);`n    }`n")
    [IO.File]::WriteAllText($jsonPath,$jsonText,[Text.UTF8Encoding]::new($false))
}
