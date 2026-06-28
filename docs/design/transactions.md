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

玩家向官方国库 `YB` 转账是一个特例：当前只写付款方 `TRANSFER_OUT`，并把金额加入 `BankSavedData.treasuryBalance`。国库余额使用 `BigInteger`，而 `TransactionRecord.balanceAfterCents` 仍是 `long`，因此国库侧流水需要未来单独设计。

## 企业流水

企业流水不使用 `TransactionRecord`，而是保存在 `EnterpriseSavedData` 内的 `EnterpriseTransactionRecord`。单个企业最多保留 10000 条记录。

当前企业流水类型：

```text
REGISTER_FEE
TRANSFER_IN
TRANSFER_OUT
ADJUST
DISBAND
```

企业流水额外记录操作者：

```java
UUID operatorId;
String operatorName;
```

后续营收统计、手续费、利息或税务进入企业账户时，应优先扩展 `EnterpriseTransactionType`，不要把企业流水写进个人 `transactionsByOwner` 索引。
