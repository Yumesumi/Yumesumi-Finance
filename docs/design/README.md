# 梦澄金融核心设计总览

本文描述当前核心模块的边界。目标是让每个功能独立演进，避免 GUI、网络包、账户存储和现金算法互相缠绕。

## 模块边界

| 模块 | 目录 | 职责 |
| --- | --- | --- |
| API | `com.yumesumi.finance.api` | 暴露低耦合接口，供实现层、接入层或未来附属模组使用 |
| 货币与现金 | `money`、`money.cash` | 面额定义、金额格式化、实体现金背包操作 |
| 个人银行账户 | `bank.account`、`bank.storage.BankSavedData` | 个人账户、官方国库、管理员账户查询、个人流水持久化 |
| 企业账户 | `bank.enterprise`、`bank.storage.EnterpriseSavedData` | 企业账户、成员角色、企业流水、收件箱和企业偏好持久化 |
| 银行管理 | `bank.admin`、`bank.service.ServerBankAdminService` | OP 控制台查询账户、冻结、备注、余额调整和删除账户 |
| 银行业务 | `bank.service` | 存款、取款、个人转账、企业转账等服务端用例 |
| 银行 GUI 接入 | `bank.ui`、`client.gui` | 打开界面、同步快照、客户端展示 |
| GUI 基础件 | `client.gui.foundation` | 原生 GUI 主题、卡片渲染、折线图工具 |
| 交易 | `transaction`、`bank.enterprise.EnterpriseTransactionRecord` | 个人交易流水与企业流水模型 |
| 网络 | `network`、`network.payload` | NeoForge 1.21.1 payload 注册和处理 |
| 银行卡与入口物品 | `bank.card`、`item` | 银行卡 UUID 绑定、管理卡、随身银行入口 |
| 方块入口 | `block` | ATM 与企业注册终端交互入口 |

## 依赖方向

接入层依赖业务接口，业务层依赖账户存储接口和现金钱包接口。底层存储不反向依赖 GUI 或网络。

```text
block / item / network
        |
        v
bank.ui.BankScreenAccessService
        |
        +--> api.bank.BankService / bank.service.ServerBankService
        |       |
        |       +--> api.bank.BankAccountRepository
        |       +--> api.money.CashWallet
        |       +--> api.transaction.TransactionRepository
        |
        +--> bank.service.ServerEnterpriseService
                |
                +--> bank.storage.EnterpriseSavedData
                +--> bank.storage.BankSavedData
```

客户端 GUI 只显示服务端快照并发送请求。任何余额、流水、成员权限或收件箱状态变更都必须在服务端完成。

## 扩展原则

- 新功能先定义清晰的数据模型和服务端入口，再接 GUI。
- 金融金额统一使用 `long cents`，单位为分；不要使用 `double`。
- 服务端是唯一权威，客户端确认弹窗只能作为 UX。
- GUI 不直接读写账户、企业、玩家背包或国库。
- 客户端 mutating 请求必须带 `requestId`，服务端按玩家拒绝重复请求。
- 存取款必须带 `BankAccessChannel`，用于区分 ATM 与随身银行来源。
- 转账不会隐式创建收款账户；账户创建必须是显式流程。
- 官方国库公开 ID 为 `YB`，真实余额使用 `BigInteger`，不要用普通账户的 `long balanceCents` 代替。
- 冻结账户的限制必须在服务端网络处理器中执行，客户端禁用按钮只作为 UX。
- 企业账户必须继续保持独立于个人账户，不要把企业余额塞进玩家 UUID 账户。
- 企业代码解析和校验属于服务端企业服务，不应写死在 Screen。

## 设计文档索引

- [银行账户设计](bank-accounts.md)
- [银行流水与转账设计](bank-ledger-and-transfer.md)
- [企业账户与原生 GUI 设计](enterprise-accounts-and-native-gui.md)
- [货币与实体现金设计](money-and-cash.md)
- [交易系统设计](transactions.md)
