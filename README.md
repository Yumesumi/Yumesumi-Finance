# Yumesumi Finance / 梦澄金融

Yumesumi Finance 是一个基于 **Minecraft NeoForge 1.21.1** 的金融系统模组。它为游戏加入梦澄元、实体现金、ATM、银行卡和银行账户，并为未来的利息、理财产品、股票市场、多币种等功能预留扩展空间。

## 当前功能

- 梦澄元货币系统，内部以“分”为最小单位，避免浮点数精度问题。
- 实体现金物品：
  - 硬币：1分、5分、10分、25分、50分、1元
  - 纸币：1元、2元、5元、10元、20元、50元、100元、500元、1000元
- ATM 方块，可放置并打开银行 GUI。
- 银行卡物品，可随身右键打开银行 GUI。
- 存款和取款二级菜单：
  - 预设金额：1、5、10、20、50、100、500、1000 梦澄元
  - 支持自定义金额
- 指定金额存款会自动找零：例如使用 100 元纸币存入 20 元，会返还 80 元现金。
- 服务端权威账户数据，基于 `SavedData` 保存。
- 使用 NeoForge 1.21.1 新 payload 网络系统。
- 自定义现金数据组件预留，未来可制作类似 7 元纪念钞的特殊现金。

## 开发环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.234 |
| Java | 21 |
| Gradle | 使用仓库内 Wrapper |

## 构建

Windows:

```powershell
.\gradlew.bat build
```

运行客户端开发环境：

```powershell
.\gradlew.bat runClient
```

运行服务端开发环境：

```powershell
.\gradlew.bat runServer
```

构建产物位于：

```text
build/libs/
```

## 目录结构

```text
src/main/java/com/yumesumi/finance
├─ api                 # 模块接口，降低耦合
│  ├─ bank             # 银行账户与业务接口
│  └─ money            # 实体现金钱包接口
├─ bank
│  ├─ account          # 账户模型
│  ├─ service          # 服务端银行业务
│  ├─ storage          # SavedData 持久化
│  └─ ui               # 银行 GUI 服务端接入
├─ block               # ATM 方块
├─ client/gui          # 客户端银行 GUI
├─ item                # 现金、银行卡物品
├─ money               # 金额、面值、自定义现金数据
│  └─ cash             # 玩家背包现金实现
├─ network             # NeoForge payload 网络
├─ registry            # 方块、物品、数据组件、创造栏注册
└─ transaction         # 交易类型与结果模型
```

资源目录：

```text
src/main/resources/assets/yumesumi_finance
├─ blockstates
├─ lang
├─ models
└─ textures
```

## 设计文档

核心系统设计文档位于：

- [核心设计总览](docs/design/README.md)
- [银行账户设计](docs/design/bank-accounts.md)
- [货币与实体现金设计](docs/design/money-and-cash.md)
- [交易系统设计](docs/design/transactions.md)

## 设计原则

- 服务端是金融数据的唯一权威。
- 客户端 GUI 只发送请求和展示服务端快照。
- 业务逻辑不直接依赖 GUI 或网络包。
- 金额统一使用 `long cents`。
- 新功能优先通过接口扩展，而不是直接修改接入层。

## 未来计划

- 银行交易流水。
- 多账户或组织账户。
- 活期/定期存款利息。
- 理财产品。
- 股票或基金市场。
- 多币种与汇率系统。
- 数据驱动的纪念钞和活动货币。

## License

All Rights Reserved.
