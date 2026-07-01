# POS 机与企业收款入口设计

本文记录 v1.2.9 的 POS 机实现状态。当前 POS 机是“个人银行转账给企业”的便捷入口，不是自动扣款终端；真实余额变更仍然由个人银行转账请求进入服务端银行/企业服务。

## 定位

POS 机用于把一个可放置方块绑定到企业代码。其他玩家右键该方块时，客户端会打开个人银行的“转账给企业”界面，并把目标企业代码预填到输入框中。

当前边界：

- POS 机不直接修改玩家余额、企业余额或流水。
- POS 机不绕过个人银行的二次确认、金额校验、账户冻结校验和服务端转账校验。
- POS 机只预填企业代码；最终转账仍然由 `TransferRequestPayload.targetEnterprise() == true` 进入 `ServerEnterpriseService.transferPersonalToEnterprise`。
- 如果企业代码后来失效，POS 机仍可打开预填界面，但服务端转账会按企业转账规则失败。

## 数据模型

物品绑定数据位于：

```text
bank.pos.PosMachineData
registry.ModDataComponents.POS_MACHINE
```

`PosMachineData` 当前只保存 `enterpriseCode`。代码通过 `EnterpriseAccount.normalizeCode` 标准化和校验，规则为 3-4 位大写字母或数字。

方块放置后，绑定数据会从物品复制到方块实体：

```text
block.entity.PosMachineBlockEntity
registry.ModBlockEntities.POS_MACHINE
```

方块实体把企业代码保存到 NBT 字段 `enterprise_code`。生存模式破坏 POS 机时会掉落一个 POS 机物品，并把方块实体内的企业代码写回物品组件，避免拆下重放后丢失绑定。

## 交互流程

手持 POS 机蹲下右键：

1. `PosMachineItem` 拦截交互。
2. 服务端向玩家发送 `PosConfigPayload`。
3. 客户端打开 `PosMachineConfigScreen`。
4. 玩家输入企业代码并点击绑定，客户端发送 `PosBindRequestPayload`。
5. 服务端确认玩家手中仍是 POS 机，校验企业代码合法性并确认企业存在。
6. 绑定成功后写入 `ModDataComponents.POS_MACHINE`；再次打开配置界面时按钮变为取消绑定。

放置后的 POS 机右键：

1. `PosMachineBlock` 读取 `PosMachineBlockEntity.enterpriseCode()`。
2. 调用 `BankScreenAccessService.openFromPos(player, enterpriseCode)`。
3. `BankSnapshotPayload` 携带 `openEnterpriseTransfer` 与 `enterpriseTransferTarget`。
4. `BankScreen` 切换到转账页，启用企业目标模式，并把目标输入框设置为该企业代码。

## 视觉与方块行为

- POS 机是小型长方体，朝向跟随玩家放置方向，短边朝向玩家。
- 当前材质使用梦澄银行卡风格的青绿色、金色描边与深色像素风。
- 顶面贴图使用 64x128 的长条比例，匹配方块顶面 6x12 的模型比例，避免正方形贴图被错误拉伸。
- 方块硬度接近木板，生存模式下可较快破坏。
- 物品悬浮提示会在已绑定时显示 `已绑定：<企业代码>`。

## 后续扩展

v1.3 继续扩展支付系统时，建议把 POS 机升级为明确的支付入口模型，而不是在方块或 GUI 中直接写余额：

- 引入支付请求模型，区分“打开转账页”和“发起待确认支付”。
- 增加收款金额、订单备注、最近付款人/收款方等字段。
- 服务端仍需把最终扣款放在银行/企业服务中，并且保留 requestId 防重放与二次确认。
- 如需支持店铺或多收款账户，不要把企业代码硬编码为唯一收款身份，应引入独立的收款账户标识。
