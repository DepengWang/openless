# OpenLess iOS

原生 iPhone App 与 Keyboard Extension，最低支持 iOS 16；录音 Widget 需要 iOS 18。
主 App 使用 SwiftUI 与共享 Rust Core，键盘使用 KeyboardKit 9.9.0 和 Rime。

## 构建

需要 Xcode 27、Swift 6、Rustup、CMake 和 Git。从仓库根目录执行：

```sh
cd openless-all/ios
./scripts/bootstrap.sh
./scripts/bootstrap-local-speech.sh
./scripts/build-rime.sh
./scripts/build-core.sh
./scripts/build.sh simulator Debug
# 无签名设备包：
./scripts/build.sh device Personal
```

`project.yml` 是 XcodeGen 配置，`OpenLess.xcodeproj` 是对应工程。
`OpenLess` 为普通 Scheme，`OpenLessPersonal` 为个人诊断 Scheme。
Rust Host 的依赖锁定在 `CoreHost/Cargo.lock`，本地路径依赖指向
`../app/crates/openless-core`。

真机签名时，将 `Config/Local.xcconfig.example` 复制为
`Config/Local.xcconfig`，配置自己的 Team ID、Bundle ID 和 App Group。
主 App、键盘和 Widget 必须使用相同的开发团队及 App Group。
无签名设备包不能直接安装到 iPhone。

## 构建输入与生成内容

保留 `App/`、`Keyboard/`、`RecordingWidget/`、`PlatformShared/`、
`Packages/`、`CoreHost/`、`RimeBridge/`、`Config/`、`Resources/` 和构建脚本。
`Resources/Licenses/` 中的许可证及来源信息随产品分发，必须保留。
模型和原生依赖由 bootstrap 脚本按固定版本与校验值获取。

`.build*/`、`.build-tools/`、`.vendor/`、`Frameworks/`、`Artifacts/`、
`DerivedData*/`、编译词库、模型下载和个人签名配置不进入版本库。
测试源码与固定输入保留；测试生成的截图、日志、报告和安装包不提交。
