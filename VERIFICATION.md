# 验证方法与发布范围

本仓库保留可复用的源码测试；不发布设备诊断日志、截图、网络环境记录、原始 APK 或签名私钥。

## 构建与安装包

运行 `powershell -ExecutionPolicy Bypass -File .\build.ps1 -Sdk <Android SDK 路径> -Jdk <JDK 路径>`，需要 Android Platform 34、Build Tools 34.0.0 及 JDK。构建脚本包含 APK 签名校验步骤，产物位于 `dist/`。

构建脚本在本机生成测试签名；不同机器生成的证书不同，无法直接覆盖另一证书签名的安装包。请私下备份用于持续发布的签名文件，勿提交到 GitHub。

## 自动检查

`tests/` 包括图源地址限制、HTML 解析、章节去重、搜索排序、本地书目索引、图片代理回退及缓存等测试。Java 测试位于 `app.jingman.reader` 包中，需与其依赖的生产类一同编译；涉及 Android API 的类需使用 SDK 的 `android.jar`。`node tests/reader-window.cjs` 检查阅读脚本的窗口加载行为。

离线测试和 APK 签名校验不能代替真机、实时网络或所有漫画章节的验证。源站访问限制、失效图片和本地索引过期仍可能导致检索结果无法阅读。
