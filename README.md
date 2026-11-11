# TV 影视浏览器（通用版 + ExoPlayer）

一个**用户自己录入网址**的安卓电视浏览器。首页大卡片，点开进站，自动净化网页广告/弹窗；播放视频时按站点设定的播放方式接管：**ExoPlayer（m3u8/mp4 直链）**、**系统 MediaPlayer** 或 **WebView 全屏兜底**。所有网址由使用者录入，工具不内置任何影视源。

## 功能
- 首页：录入 / 保存常用网址，做成遥控器好按的大卡片（DPAD 导航、OK 打开、长按删除）
- 扫码添加：电视端起本地 HTTP 服务并显示二维码，手机扫码后在手机上填名称/网址/播放方式，提交即同步到电视（需同一 Wi-Fi）
- 进站：全屏 WebView 加载用户网址
- 净化：注入 JS 隐藏广告位、浮层、弹窗，放大剧集/播放链接
- 三种播放方式（添加站点时选择）：
  - **ExoPlayer**：JS 抓到 `<video>` 的 `.m3u8` / `.mp4` 直链后，交给 ExoPlayer 原生播放（铺满、拖进度、暂停、切上下集、选集、倍速、控制条自动隐藏、左上角返回）。播放失败自动回退 WebView。
  - **MediaPlayer**：同上，用系统 MediaPlayer 承接（轻量、兼容性好）。
  - **WebView**：加密源 / 直播等无法拿到直链的站点，用网页自身播放 + 系统全屏兜底。
- 适配：强制横屏、保持屏幕常亮，兼容 Android 6（API 23）

## 项目结构
```
tvbrowser/
  app/
    build.gradle                 Gradle 应用模块（ExoPlayer 2.19.1 依赖）
    debug.keystore               debug 签名（公开测试密钥）
    src/main/
      AndroidManifest.xml
      java/com/tvbrowser/
        MainActivity.java        首页：录入网址 + 大卡片 + 扫码入口
        BrowserActivity.java     WebView 进站 + 净化注入 + 播放方式路由
        ExoPlayerView.java       ExoPlayer 播放层（7 控件）
        MediaView.java           系统 MediaPlayer 播放层（7 控件）
        Sites.java               网址存储（SharedPreferences）
        LocalServer.java         电视端 8080 端口，收手机扫码提交
      assets/
        purify.js                通用净化 + 视频接管脚本
        qr.html                  电视端二维码 / 手机端添加表单
      res/drawable/ic_launcher.xml
  settings.gradle
  build.gradle                   AGP 7.4.2
  .github/workflows/build.yml    GitHub Actions 在线打包（Gradle）
```

## 在线打包（GitHub Actions，推荐）
本地无需装 Gradle/Android SDK。推到 GitHub 后 Actions 自动用 Gradle 构建并上传 APK：
```
git init
git add .
git commit -m "tvbrowser + exoplayer"
git remote add origin <你的仓库地址>
git push -u origin main
```
在仓库 `Actions` 标签下找到 `Build TVBrowser APK` 运行记录，下载 `TVBrowser-apk` 产物（`app-debug.apk`）。

## 本地打包（可选）
需本机已装 Android SDK（platforms;android-33、build-tools;33.0.1）、JDK 17 与 Gradle 7.5+：
```
gradle assembleDebug
```
产物：`app/build/outputs/apk/debug/app-debug.apk`

## 安装与测试
- 小米电视（MiTV4-ANSM0，Android 6）或手机对照：开启「未知来源」后安装 `app-debug.apk`
- 首页添加一个网址（如 `huashijie.tv`），播放方式选 ExoPlayer 或 WebView，点进去验证：页面净化、剧集链接放大、点视频是否进入原生播放界面
- 加密/直播源（如 `tv.cctv.com/live/cctv1`）选 WebView，避免 ExoPlayer 拿不到直链

## 边界（已知限制）
- **ExoPlayer 需要可直连的 `.m3u8` / `.mp4` 地址**。网页用 `blob:`、MSE 自绘、或加密 DRM 时拿不到直链，会自动回退 WebView；CCTV 这类加密直播源请直接选 WebView。
- 抓地址靠注入 JS 读 `document.querySelector('video').currentSrc`，页面加载后延迟 3 秒抓取；若站点播放器懒加载更慢，可能一次抓不到（当前实现不做轮询重试）。
- 净化是通用规则（按 class/id 关键词 + 小浮层），不同网站结构差异大，个别站点仍可能有残留广告或误伤布局。
- 本版不含内置影视源，全部网址由用户自己录入。
- 扫码添加要求电视 App 处于前台（本地服务随 App 运行），且手机与电视同一网段。
