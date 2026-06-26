# 银行账户设计

## 当前模型

当前持久化账户由 `BankAccount` 表示：

```java
UUID owner;
long balanceCents;
```

`owner` 是玩家 UUID，`balanceCents` 是账户余额，单位为分。

为了给未来组织账户、多账户和联名账户预留入口，API 层还提供：

```java
BankAccountId;
BankAccountKind;
```

v1.2.0 中 `BankAccountId` 只作为扩展钩子，实际存储仍然以玩家 UUID 为主键。

## 存储

账户存储由接口 `BankAccountRepository` 定义：

```java
Optional<BankAccount> find(UUID owner);
Optional<BankAccount> find(BankAccountId accountId);
long balanceOf(UUID owner);
long balanceOf(BankAccountId accountId);
void setBalance(UUID owner, long balanceCents);
```

当前实现是 `BankSavedData`，数据保存到主世界的 `SavedData`：

```text
yumesumi_finance_bank.dat
```

## 设计约束

- 账户数据只在服务端读写。
- 余额不能为负数。
- 当前无开户/注销流程，首次存款会通过 `setBalance` 自然创建账户数据。
- 转账目标必须已有账户，转账不会隐式创建收款账户。
- 仓储层拒绝负余额和空 owner；读取旧存档时会跳过明显损坏的记录。
- 未来如果恢复实名开户、冻结账户或多银行账户，应扩展仓储接口，而不是让 GUI 直接操作 `SavedData`。
