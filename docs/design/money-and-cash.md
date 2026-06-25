# 货币与实体现金设计

## 金额单位

梦澄元内部使用 `long cents` 表示金额，1 梦澄元等于 100 分。

示例：

```text
1 分 = 1
1 元 = 100
1000 元 = 100000
```

避免使用 `double` 计算金融金额。

## 面值

预制硬币：

```text
1分、5分、10分、25分、50分、1元
```

预制纸币：

```text
1元、2元、5元、10元、20元、50元、100元、500元、1000元
```

面值定义集中在 `CashDenominations`。

## 实体现金钱包

实体现金钱包接口是 `CashWallet`：

```java
long count(ServerPlayer player);
boolean remove(ServerPlayer player, long amountCents);
void give(ServerPlayer player, long amountCents);
```

当前实现 `PlayerCashWallet` 直接操作玩家背包。

指定金额存款时，算法会：

1. 统计背包现金总额。
2. 若总额不足则失败。
3. 若足够，先收走所有现金。
4. 将差额按标准面值找零返还。

这样可以支持玩家用大额纸币存小额金额。

## 自定义现金预留

`MoneyData` 数据组件用于未来纪念钞、活动券或非标准面值：

```java
long valueCents;
String displayName;
CashKind kind;
```

例如 7 元纪念钞可以用 `valueCents = 700` 表示。
