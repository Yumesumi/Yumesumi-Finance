# 梦澄金融核心设计总览

本文档描述当前核心模块的边界。目标是让每个功能独立演进，避免 GUI、网络包、账户存储和现金算法互相缠绕。

## 模块边界

| 模块 | 目录 | 职责 |
| --- | --- | --- |
| API | `com.yumesumi.finance.api` | 暴露低耦合接口，供实现层、接入层或未来附属模组使用 |
| 货币与现金 | `money`、`money.cash` | 面值定义、金额格式化、实体现金背包操作 |
| 银行账户 | `bank.account`、`bank.storage` | 账户模型与服务端存档 |
| 银行业务 | `bank.service` | 存款、取款、转账等服务端用例 |
| 银行 GUI 接入 | `bank.ui`、`client.gui` | 打开界面、同步快照、客户端展示 |
| 交易 | `transaction` | 交易类型、结果模型、持久化流水模型 |
| 网络 | `network` | NeoForge 1.21.1 payload 注册和处理 |
| 银行卡 | `bank.card`、`item` | 银行卡 UUID 绑定、首次命名和随身银行入口 |

## 依赖方向

接入层依赖业务接口，业务层依赖账户仓储接口和现金钱包接口，底层实现不反向依赖 GUI 或网络。

```text
block / item / network
        |
        v
bank.ui.BankScreenAccessService
        |
        v
api.bank.BankService
        |
        +--> api.bank.BankAccountRepository
        +--> api.money.CashWallet
        +--> api.transaction.TransactionRepository
```

## 扩展原则

- 新功能先定义接口，再添加实现。
- 金融金额统一使用 `long cents`，单位为分。
- 服务端是唯一权威，客户端只发请求和展示快照。
- GUI 不直接读写账户或玩家背包。
- 客户端请求必须带 `requestId`，服务端按玩家拒绝已处理请求。
- 存取款必须带 `BankAccessChannel`，用于区分 ATM 与随身银行来源。
- 转账不会隐式创建收款账户；账户创建应是未来显式流程。
