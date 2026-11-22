# FluidCloudRedirect

ColorOS 智能服务（`com.oplus.metis`）流体云链接重定向 Xposed 模块（LibXposed API 102），支持将以下平台链接重定向到对应第三方客户端并同步替换卡片与胶囊图标：

| 平台 | 官方包名 | 重定向目标 |
| :--- | :--- | :--- |
| 酷安 | `com.coolapk.market` | `app.awavenue.c001apk.coui` ([c001apk](https://github.com/Costben/c001apk)) |
| 微博 | `com.sina.weibo` | `com.cobo.app` ([Cobo](https://github.com/Costben/COBO)) |
| 哔哩哔哩 | `tv.danmaku.bili` | `erl.bilico` ([BiliCO](https://github.com/Costben/BiliCO)) |

每个平台均可在模块内单独开启或关闭；关闭或未安装目标应用时恢复官方客户端跳转。

## 作用域

- 仅需在 LSPosed 中勾选 `com.oplus.metis`（智能服务）。

## 编译与安装

```bash
./gradlew assembleRelease
SERIAL=<设备序列号> scripts/install-module.sh <已签名.apk> cc.csxcl.fluidredirect
```

## 开源协议

[GPL-3.0](LICENSE)
