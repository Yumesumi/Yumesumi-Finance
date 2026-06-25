# 银行账户设计

## 当前模型

账户由 `BankAccount` 表示：

```java
UUID owner;
long balanceCents;
```

`owner` 是玩家 UUID，`balanceCents` 是账户余额，单位为分。

## 存储

账户存储由接口 `BankAccountRepository` 定义：

```java
Optional<BankAccount> find(UUID owner);
long balanceOf(UUID owner);
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
- 未来如果恢复实名开户、冻结账户或多银行账户，应扩展仓储接口，而不是让 GUI 直接操作 `SavedData`。
