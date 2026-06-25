# 交易系统设计

## 当前范围

当前交易模块只提供轻量结果模型，不保存历史流水。

```java
TransactionType
TransactionResult
```

业务服务执行存款/取款后返回 `TransactionResult`，GUI 接入层根据结果同步余额和消息。

## 交易类型

当前类型：

```text
DEPOSIT
WITHDRAW
```

未来可扩展：

```text
TRANSFER
INTEREST
PRODUCT_BUY
PRODUCT_REDEEM
STOCK_BUY
STOCK_SELL
EXCHANGE
FEE
```

## 未来流水

建议未来新增 `TransactionRecord`：

```java
UUID id;
UUID owner;
TransactionType type;
long amountCents;
long balanceAfterCents;
long createdGameTime;
String note;
```

流水可以保存在 `SavedData` 中，也可以按玩家拆分，避免大型服务器单文件过大。
