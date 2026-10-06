# 如何打出 macOS DMG

這份說明只涵蓋在這台 Mac 上打出可安裝的 `.dmg`。對方不必另裝 JDK。目前不做 Apple 簽名與公證，適合自己這台電腦使用。

必須在 macOS 上執行。Compose 外掛用 JDK 的 `jpackage` 把程式與 JRE 打成 `.app`，再包進 `.dmg`。不支援交叉編譯。

## 打包

在專案根目錄執行：

```bash
./gradlew :composeApp:packageDmg
```

產物路徑：

```
composeApp/build/compose/binaries/main/dmg/Foldmerge-1.0.0.dmg
```

檔名裡的 `Foldmerge` 來自 `gradle.properties` 的 `app.rootName`，後面的數字來自套件版號。改名稱或版號請用 `./configure.sh`，見 `How-To-Change_Project_Name.md` 與 `How-To-Change_Version_number.md`。

Compose 的 `jpackage` 要求 DMG 的主版號大於 0。產品版號若是 `0.x`，About 仍顯示產品版號，安裝檔會把主版號改成 `1`。目前產品版號是 `1.0.0`，兩邊相同。

打包設定在 `composeApp/build.gradle.kts` 的 `nativeDistributions`。`targetFormats` 目前只有 DMG。`includeAllModules = true` 會把 JRE 模組帶齊，避免打包後才缺類別。

## 圖示

Dock、`.app` 與 DMG 用同一枚圖示。來源在：

```
composeApp/packaging/macos/foldmerge-icon-1024.png
composeApp/packaging/macos/Foldmerge.icns
```

`nativeDistributions.macOS.iconFile` 指向 `.icns`。開發時 `run` 另外用 PNG 當 `-Xdock:icon`，視窗圖示讀 `desktopMain/composeResources/drawable/app_icon.png`。換圖示時請同時更新這三份，再用 `sips` 與 `iconutil` 從 1024 PNG 重打 `.icns`。

## 安裝

1. 打開產生的 `.dmg`
2. 把裡面的 `Foldmerge.app` 拖到「應用程式」
3. 從「應用程式」或 Launchpad 啟動

這台機器是 Apple Silicon（arm64），打出來的套件給同架構的 MacBook 用。Intel Mac 要在 Intel 機器上再打一份。

## 先試跑打包結果

`./gradlew :composeApp:run` 是開發用，走完整 JDK，還沒包成 `.app`。要看安裝後的樣子，請用：

```bash
./gradlew :composeApp:runDistributable
```

這會先建立 `.app`（不必開 DMG），再用裡面的 JRE 啟動。視窗標題與 About 應顯示產品名稱與版號。

`.app` 本體在：

```
composeApp/build/compose/binaries/main/app/Foldmerge.app
```

## 本機未簽名

這份 DMG 沒有 Developer ID 簽名，也沒有送 Apple 公證。自己在這台電腦打出來、自己安裝即可。若以後要把檔案傳給別人，或放到網路上讓別人下載，macOS Gatekeeper 會擋未公證的應用程式，那時再接簽名與 notarization。
