# Yumesumi Finance / 梦澄金融

Yumesumi Finance 是一个基于 **Minecraft NeoForge 1.21.1** 的金融系统模组。它为游戏加入梦澄元、实体现金、ATM、银行卡、银行账户、交易明细和玩家转账，并为后续利息、理财、股票、多币种等系统预留扩展空间。

当前版本：**1.2.0**

## 功能

- 梦澄元货币系统，内部统一使用 `long cents`，以“分”为最小单位。
- 实体现金物品：
  - 硬币：1 分、5 分、10 分、25 分、50 分、1 元
  - 纸币：1 元、2 元、5 元、10 元、20 元、50 元、100 元、500 元、1000 元
- ATM 方块：放置后右键打开银行界面。
- 银行卡物品：首次使用绑定玩家 UUID，绑定后随身右键打开银行界面；他人无法使用。
- 存款与取款：
  - 支持所有现金面额快捷操作。
  - 支持自定义金额。
  - 支持全部存入和全部取出，并带二次确认。
  - 指定金额存款会自动找零。
- 玩家转账：
  - 支持输入玩家名称或 UUID。
  - 支持离线转账到可解析的 UUID 或服务器缓存玩家。
  - 转账前二次确认。
  - 二次确认后显示处理界面，服务端返回后关闭。
  - 目标账户不存在时终止交易，不会自动创建收款账户。
  - 支持转账备注。
- 交易明细：
  - 最多显示最近 1000 条记录。
  - 支持收入/支出筛选、按时间/金额排序和升降序切换。
  - 支持按玩家名、UUID、备注、金额搜索。
  - 明细显示交易类型、金额增减、备注、时间、余额和来源/去向。
  - ATM 与随身银行存取款会记录资金来源/去向，例如“ATM存入”“随身银行取现”。
  - 悬浮玩家名显示 UUID，悬浮备注显示全文。
- 服务端权威账户数据，基于 `SavedData` 保存。
- 使用 NeoForge 1.21.1 payload 网络系统。
- 银行请求带 `requestId`，服务端记录已处理请求，防止网络重放。
- 银行界面消息使用客户端本地语言渲染，专用服务器不会把提示文本固定成英文。
- 预留自定义现金数据组件，可扩展纪念钞或活动货币。

## 开发环境

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.1 |
| NeoForge | 21.1.200 或更高 |
| Java | 21 |
| Gradle | 仓库内 Wrapper |

## 构建与运行

推荐使用 NeoForge **21.1.200+** 的 Minecraft 1.21.1 环境。若服务器核心使用更高的 21.1.x 版本，通常无需重新构建。

Windows 构建：

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
│  ├─ bank             # 银行业务接口
│  ├─ money            # 实体现金钱包接口
│  └─ transaction      # 交易流水仓储接口
├─ bank
│  ├─ account          # 账户模型
│  ├─ card             # 银行卡绑定数据
│  ├─ service          # 服务端银行业务
│  ├─ storage          # SavedData 持久化
│  ├─ transfer         # 转账目标解析
│  └─ ui               # 银行 GUI 服务端接入
├─ block               # ATM 方块
├─ client/gui          # 客户端银行 GUI
├─ item                # 现金、银行卡物品
├─ money               # 金额、面值、自定义现金数据
│  └─ cash             # 玩家背包现金实现
├─ network             # NeoForge payload 网络
├─ registry            # 方块、物品、数据组件、创造栏注册
└─ transaction         # 交易类型、记录、筛选与排序模型
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

- [核心设计总览](docs/design/README.md)
- [银行账户设计](docs/design/bank-accounts.md)
- [货币与实体现金设计](docs/design/money-and-cash.md)
- [交易系统设计](docs/design/transactions.md)
- [银行流水与转账设计](docs/design/bank-ledger-and-transfer.md)

## 设计原则

- 服务端是金融数据的唯一权威。
- 客户端 GUI 只发送请求和展示服务端快照。
- 金额统一使用 `long cents`。
- 业务逻辑不直接依赖 GUI 或网络包。
- 新功能优先通过接口扩展，保持模块之间低耦合。

## 未来计划

- 支付系统、POS 机、玩家/商店收款。
- 多账户或组织账户。
- 活期/定期存款利息。
- 理财产品。
- 股票或基金市场。
- 多币种与汇率系统。
- 数据驱动的纪念钞和活动货币。

## License

All Rights Reserved.
