# FluidCloudRedirect

专为 ColorOS「智能服务 / 流体云 (`com.oplus.metis`)」打造的轻量级 Xposed 模块（LibXposed API 102），用于将系统识别到的口令/链接流体云卡片重定向到自制第三方客户端，并同步替换卡片与胶囊上的应用图标。

## 支持的重定向规则

| 平台 | 官方包名 | 重定向目标 | 目标应用 |
| :--- | :--- | :--- | :--- |
| 酷安 | `com.coolapk.market` | `app.awavenue.c001apk.coui` | [c001apk](https://github.com/Costben/c001apk) |
| 微博 | `com.sina.weibo` | `com.cobo.app` | [Cobo](https://github.com/Costben/COBO) |
| 哔哩哔哩 | `tv.danmaku.bili` | `erl.bilico` | [BiliCO](https://github.com/Costben/BiliCO) |

每条规则均可在模块设置页单独开启或关闭：
- **开启**（且已安装对应第三方客户端）：伪装安装状态为「打开」、替换流体云卡片/胶囊图标、并将普通拉起 / 小窗拉起 / 首页兜底跳转重定向到第三方客户端（酷安同步归一 `coolmarket://` URI）。
- **关闭**（或未安装对应第三方客户端）：完全放行系统原逻辑，恢复官方客户端跳转与图标。

## 作用域与运行环境

- **框架要求**：LSPosed（支持 LibXposed API 102）
- **作用域**：仅需勾选 `com.oplus.metis`（智能服务）

## 编译

本工程界面使用 [coui17](https://github.com/Costben/coui17)（默认从同级目录 `../coui/build/repository/local` 读取 `1.2.0-SNAPSHOT`）：

```bash
./gradlew assembleRelease
```

产物位于 `app/build/outputs/apk/release/app-release-unsigned.apk`，使用你自己的签名证书或 debug keystore 签名后安装。

## 安装（必须非增量）

```bash
SERIAL=<设备序列号> scripts/install-module.sh <已签名.apk> cc.csxcl.fluidredirect
```

## 开源协议

本项目基于 [GNU General Public License v3.0](LICENSE) 开源。
