# 企业账户与原生 GUI 设计

本文记录 v1.2.6 当前企业银行与原生 GUI 的实现状态，方便后续继续优化界面与补齐业务细节。

## 当前状态

企业账户已经不是玩家个人账户的“第二余额”。当前实现使用独立模型和独立存档：

```text
bank.enterprise.EnterpriseAccount
bank.storage.EnterpriseSavedData
bank.service.ServerEnterpriseService
client.gui.EnterpriseScreen
```

个人银行仍由 `BankScreen` 展示，企业银行由 `EnterpriseScreen` 展示。两个界面都只发送 payload 请求和展示服务端快照，余额变更必须走服务端服务。

## 企业注册

玩家通过 `EnterpriseRegistrationTerminalBlock` 打开企业注册界面。

注册规则：

- 每个玩家最多作为 `OWNER` 创建 10 个企业。
- 企业代码全服唯一，格式为 3-4 位大写英文字母或数字。
- 企业名称最长 32 字符。
- 企业简介最长 64 字符。
- 注册费为 `EnterpriseSavedData.REGISTRATION_FEE_CENTS`，当前是 1000 梦澄元。
- 注册费进入官方国库 `YB`。
- 创建成功后，创建者自动成为唯一 `OWNER`。

企业代码由 `EnterpriseAccount.normalizeCode` 统一校验和标准化。客户端按钮置灰只能作为 UX，服务端仍必须校验代码、余额和创建上限。企业注册只能从 `EnterpriseRegistrationTerminalBlock` 打开，网络层不允许随身打开注册页，创建请求也要求玩家先通过终端获得本次注册入口。

## 成员与权限

企业角色定义在 `EnterpriseRole`：

```text
OWNER   唯一所有者
ADMIN   管理员
MEMBER  普通成员
```

权限约束：

- `OWNER` 拥有全部权限，可以修改企业资料、设置成员角色、移出成员、注销企业。
- `ADMIN` 可以操控企业资金、邀请成员、查看企业流水。
- `ADMIN` 不能指派其他人成为 `ADMIN`，也不能注销企业。
- `MEMBER` 当前只能查看企业概览、成员与流水，不显示企业账户转账系统，但仍可使用成员列表的筛选、排序和搜索。

成员上限当前为 `EnterpriseAccount.MAX_MEMBERS = 64`。邀请通过个人银行收件箱发送，消息类型为 `InboxMessageType.ENTERPRISE_INVITE`。

## 企业流水

企业流水使用 `EnterpriseTransactionRecord`，保存在企业账户内部，最多保留 `EnterpriseAccount.MAX_LEDGER_RECORDS = 10_000` 条。

流水字段包括：

```java
UUID id;
UUID enterpriseId;
EnterpriseTransactionType type;
long amountCents;
long balanceAfterCents;
UUID operatorId;
String operatorName;
UUID counterpartyId;
String counterpartyName;
String note;
long createdAt;
```

这套流水和个人 `TransactionRecord` 分离。个人账户的明细仍保存在 `BankSavedData`，企业账户的明细保存在 `EnterpriseSavedData`。

## 转账路径

当前已实现的路径：

- 个人转个人：沿用 `ServerBankService.transfer`。
- 个人转国库：输入 `YB`，金额进入 `BankSavedData.treasuryBalance`。
- 个人转企业：个人转账界面切换到企业目标，输入企业代码。
- 企业转个人：企业概览转账面板选择个人目标，输入玩家名或 UUID。
- 企业转企业：企业概览转账面板选择企业目标，输入企业代码。
- 企业转国库：企业转个人路径中输入 `YB`。

企业余额不能为负数。官方国库余额使用 `BigInteger`，允许为负数。所有转账备注最多 64 字符。批量成员转账会先校验全部成员个人账户、总金额和溢出，任一目标失败则整批取消。

失败回滚原则：

- 目标不存在时不创建账户。
- 余额不足时不写流水。
- 转账金额必须大于 0。
- 加法使用 `Math.addExact` 或等效检查，避免溢出。

## 收件箱

个人银行快照 `BankSnapshotPayload` 现在会携带收件箱和企业摘要：

```text
List<InboxMessageView> inbox
List<EnterpriseSummaryView> enterprises
boolean preferEnterprise
```

收件箱状态保存在 `EnterpriseSavedData`，当前支持：

- 已读
- 星标
- 删除
- 接受企业邀请

后续如果加入付款通知、系统公告、贷款提醒等消息，可以继续扩展 `InboxMessageType`，不要让 GUI 自己推断消息语义。

## 原生 GUI 结构

当前没有引入额外 GUI 库，使用 Minecraft 原生 `Screen`、`Button`、`EditBox` 和 `GuiGraphics`。

共用视觉工具位于：

```text
client.gui.foundation.FinanceTheme
client.gui.foundation.FinanceRender
client.gui.foundation.FinanceLineChart
```

`FinanceLineChart` 使用原生矩形填充绘制简易折线，不依赖第三方库。后续 UI 优化应优先扩展 foundation 包，避免每个 Screen 复制颜色、卡片和图表逻辑。

## 已知后续优化

- 企业 GUI 的布局和视觉层次还需要精修，尤其是小窗口响应式布局。
- 折线图当前使用最近流水余额点，不是真正按 24 小时/7 天时间桶聚合。
- 企业注册终端模型暂时复用 ATM 材质，需要后续补正式贴图/模型。
- 企业注销当前服务端只认最终 confirmed 标记，三次确认和 5 秒倒计时主要由客户端 UX 承担；后续可以增加服务端短期确认票据。
- 企业代码修改会释放旧代码并占用新代码；需要继续在 UI 上强化二次确认文案。
- 企业成员面板当前支持多选、全选、反选、筛选、排序、切换管理员、批量移出和批量转账。
- 星标收件箱邮件不可删除；批量删除会自动跳过星标邮件。
