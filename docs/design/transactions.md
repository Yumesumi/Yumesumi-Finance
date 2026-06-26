# 交易系统设计

## 当前范围

当前交易模块提供操作结果、交易类型、查询条件和持久化流水模型。

```java
TransactionType
TransactionResult
TransactionRecord
TransactionQuery
```

业务服务执行存款、取款或转账后返回 `TransactionResult`，GUI 接入层根据结果同步余额、消息、消息键和最近流水。

## 交易类型

当前类型：

```text
DEPOSIT
WITHDRAW
TRANSFER_IN
TRANSFER_OUT
```

未来可扩展：

```text
INTEREST
PRODUCT_BUY
PRODUCT_REDEEM
STOCK_BUY
STOCK_SELL
EXCHANGE
FEE
```

## 持久化流水

每条流水是 `TransactionRecord`：

```java
UUID id;
UUID owner;
TransactionType type;
long amountCents;
long balanceAfterCents;
long createdGameTime;
String counterpartyName;
UUID counterpartyId;
String note;
```

流水当前保存在 `BankSavedData`，并按 owner 建内存索引以优化查询。每个玩家最多保留 1000 条可见记录。

存取款流水使用 `counterpartyName` 表示资金来源或去向：

- `ATM存入`
- `ATM取现`
- `随身银行存入`
- `随身银行取现`

转账会写两条流水：

- 付款方 `TRANSFER_OUT`
- 收款方 `TRANSFER_IN`
