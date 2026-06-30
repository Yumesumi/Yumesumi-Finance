# 银行账户设计

## 账户类型

v1.2.8 账户类型由 `BankAccountType` 定义：

```text
PERSONAL    个人账户
ENTERPRISE  企业账户
OFFICIAL    官方国库
```

当前需要区分两套持久化模型：

- 个人账户与官方国库元数据保存在 `BankSavedData`。
- 企业账户保存在 `EnterpriseSavedData`，使用 `EnterpriseAccount`，不是个人账户的第二余额。

`BankAccountType.ENTERPRISE` 是账户体系和管理入口的类型标记。真实企业资金、成员、代码和企业流水以 `EnterpriseAccount` 为准。

## 个人账户模型

个人账户由 `BankAccount` 表示：

```java
UUID owner;
BankAccountType accountType;
String ownerName;
long balanceCents;
long createdAt;
long lastActiveAt;
String adminNote;
boolean frozen;
```

约束：

- `balanceCents` 使用 `long cents`，单位为分。
- 非官方账户余额不能为负数。
- `adminNote` 仅梦澄银行控制台可见、可编辑，最长 64 字符。
- 冻结账户不能存款、取款或转账，但仍可查看明细。
- 实体现金物品本身不受冻结限制。

个人账户存档文件：

```text
yumesumi_finance_bank.dat
```

`BankSavedData` 当前同时承担个人账户、官方国库、个人流水索引和管理员查询/操作职责。只有在能明显降低复杂度时再拆分 account store 与 ledger store。

## 官方国库

官方国库公开 ID 为：

```text
YB
```

内部 UUID 为：

```text
a0000000-0000-0000-0000-000000000001
```

国库余额由 `BigInteger treasuryBalance` 表示，允许为负数。`OFFICIAL` 类型的 `BankAccount` 只作为账户体系中的占位元数据，不应把它的 `balanceCents` 当作国库真实余额。

玩家可以直接向 `YB` 转账。该路径会扣除玩家个人账户余额，并把金额加入 `BankSavedData.treasuryBalance`。当前只写付款方个人流水；国库侧流水应在未来单独设计，避免把 `BigInteger` 余额强行塞进 `TransactionRecord.balanceAfterCents` 的 `long` 字段。

## 企业账户模型

企业账户由 `EnterpriseAccount` 表示：

```java
UUID id;
String code;
String name;
String description;
long balanceCents;
UUID ownerId;
long createdAt;
long lastActiveAt;
Map<UUID, EnterpriseMember> members;
List<EnterpriseTransactionRecord> transactions;
```

企业账户存档文件：

```text
yumesumi_finance_enterprises.dat
```

主要规则：

- 企业代码全服唯一，格式为 3-4 位大写英文字母或数字。
- 企业名称最长 32 字符。
- 企业简介最长 64 字符。
- 每个玩家最多作为 `OWNER` 创建 10 个企业。
- 注册费为 1000 梦澄元，进入官方国库。
- 企业余额不能为负数。
- 单个企业最多保留 10000 条流水。
- 企业注销后企业代码释放，剩余余额转入官方国库；当前企业流水不保留。

## 企业成员权限

企业角色定义在 `EnterpriseRole`：

```text
OWNER   唯一所有者
ADMIN   管理员
MEMBER  普通成员
```

权限：

- `OWNER` 拥有全部权限，包括成员角色管理和注销企业。
- `ADMIN` 可以操控企业资金、邀请成员、查看企业流水。
- `MEMBER` 当前只能查看企业信息。

邀请通过个人银行收件箱发送。收件箱状态也保存在 `EnterpriseSavedData`。

## 管理员操作

管理员操作通过 `BankAdminService` / `ServerBankAdminService` 暴露，网络入口必须进行 OP 权限校验。GUI 和网络包不能直接改余额。

当前支持：

- 查询个人或企业账户。
- 搜索玩家名、UUID、企业信息和管理员备注。
- 按名称、余额、开户时间、最后活跃时间排序。
- 设置余额、调整余额。
- 冻结或解冻账户。
- 编辑管理员备注。
- 删除账户。

删除个人账户时历史流水保留。若账户余额不为 0，客户端应弹出二次确认；确认后强制删除并把余额转入官方国库。零余额账户可以直接删除。

企业注销由 `ServerEnterpriseService.disband` 处理，只允许 `OWNER` 操作。注销会删除企业实体、释放企业代码、清理相关收件箱邀请，并将剩余余额转入官方国库。

## 冻结规则

冻结只限制银行账户操作：

- 玩家打开银行 GUI 时显示“账户被冻结”提示。
- 存款、取款、转账按钮禁用。
- 服务端仍在对应网络处理器中再次检查冻结状态，防止客户端绕过。
- 玩家仍然可以查看交易明细。
- 实体现金物品的持有、丢弃、交易等不在冻结限制范围内。

## 设计约束

- 账户数据只在服务端读写。
- 非官方账户余额不能为负数。
- 存款可以自然创建个人账户；转账不会隐式创建收款账户。
- 转账目标解析属于 `bank.transfer`，例如 `YB` 到官方国库的映射不应写在客户端 GUI。
- 企业代码解析属于 `EnterpriseAccount.normalizeCode` 和 `ServerEnterpriseService`。
- 所有余额变更必须经过 `BankService`、`ServerBankService`、`ServerEnterpriseService` 或更窄的服务层原语。
- 后续多账户、联合账户、组织权限或审计系统应先扩展模型和服务，再更新 GUI。
