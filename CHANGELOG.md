# Yumesumi Finance / 梦澄金融 更新日志

## v1.2.7 — 个人银行查询页紧凑化与悬浮信息修复

### 变更

- 版本号升至 1.2.7
- 将个人银行查询页调整为更紧凑的流水表格布局，压缩顶部信息区、行高、列间距和单元格内边距
- 查询页移除当前时间显示，保留用户、余额、筛选、排序、搜索和分页信息

### 修复

- 修复个人银行查询页企业往来悬浮无法查看企业代码的问题
- 修复查询页“操作人”文本悬浮不显示玩家 UUID 或企业代码的问题
- 个人流水快照增加企业代码字段，服务端按企业 UUID 解析后发送给客户端

## v1.2.6 — 个人银行 GUI 与国库流水修复

### 变更

- 版本号升至 1.2.6
- 重构个人银行主界面、存款、取款、转账、查询和收件箱 GUI，统一为黑底白线像素风
- 个人银行查询页改为全屏流水表格，备注最多显示 24 字，悬浮显示完整备注
- 个人银行中显示玩家名称的位置支持悬浮查看 UUID
- 国库流水页改为与企业流水接近的两行表格样式，并保留国库转账入口

### 修复

- 修复部分国库金额变更路径缺少清晰流水备注的问题
- 修复企业修改信息失败后输入框被服务端快照重置的问题
- 修复企业代码非法和代码重复反馈混淆的问题
- 修复梦澄银行控制台编辑单个账户备注时旧备注不稳定保留的问题

## v1.2.5 — 管理员控制台、账户体系与企业银行原生 GUI

### 新增

- 梦澄银行管理卡物品 (`bank_admin_card`)，仅 OP 可使用，右键打开梦澄银行控制台
- 梦澄银行控制台 GUI (`BankAdminScreen`)，包含：
  - 主菜单：个人账户管理、企业账户管理、官方国库三个入口
  - 账户列表页：表格展示所有账户，支持搜索、排序、全选、反选、分页
  - 选项菜单：设余额、调余额、冻结/解冻、编辑备注、删除账户
  - 国库详情页：查看国库余额，管理员调账
- 账户类型体系：`PERSONAL`（个人）、`ENTERPRISE`（企业）、`OFFICIAL`（官方国库）
- 账户元数据字段：开户时间、最后活跃时间、管理员备注（最长64字符）、冻结状态
- 梦澄银行官方国库账户（ID: `YB`），允许负数，使用 `BigInteger` 存储余额
- 删除账户时余额转入国库（需二次确认），历史流水保留
- 冻结账户功能：被冻结玩家打开银行 GUI 显示红字提示，存取转按钮禁用，明细仍可查看
- 转账至不存在的账户时，加载结束后正确返回转账页面（保留金额和备注，清空收款人）
- 企业注册终端方块 (`enterprise_registration_terminal`)，用于打开企业注册 GUI
- 企业账户独立模型与存档 (`EnterpriseAccount` / `EnterpriseSavedData`)，不复用玩家个人余额
- 企业注册流程：企业名称、企业代码校验、企业简介、1000 梦澄元注册费、创建者自动成为 `OWNER`
- 企业成员角色：`OWNER`、`ADMIN`、`MEMBER`
- 企业账户 GUI (`EnterpriseScreen`)：企业列表、概览、成员、流水、设置页
- 企业成员邀请通过个人银行收件箱发送，收件箱支持已读、星标、删除和接受邀请
- 企业流水模型，单个企业最多保留 10000 条流水
- 原生 GUI foundation：统一主题、卡片渲染和原生折线图绘制工具

### 变更

- `BankAccount` 从不可变 record 改为可变 class，新增账户类型、元数据字段
- `BankSavedData` 实现 `BankAdminService` 接口，支持管理员操作
- `BankSnapshotPayload` 新增 `accountFrozen` 字段
- `ServerPayloadHandlers` 新增管理员请求处理和冻结检查
- `ClientPayloadHandlers` 新增管理员快照处理，修复 `TransferLoadingScreen` 返回逻辑
- `BankScreen` 新增冻结账户 UI：红字提示、禁用存取转按钮
- `BankScreenAccessService` 新增 `sendFrozenSnapshot` 方法
- `BankScreen` 重构为个人银行主页，加入收件箱入口、企业切换入口和个人/企业转账目标切换
- `TransferRequestPayload` 新增 `targetEnterprise` 字段，个人转账界面可按企业代码转账
- `BankSnapshotPayload` 新增收件箱、企业摘要和最后停留偏好字段
- `BankScreenAccessService` 会按玩家偏好打开个人银行或企业账户列表
- 版本号升至 1.2.5

### 修复

- 修复零余额账户删除只返回成功消息但未真正删除的问题
- 修复读取存档时 `last_active_at` 被错误刷新为当前时间的问题
- 修复冻结账户打开银行 GUI 时未立即显示冻结状态的问题
- 修复冻结账户仍可能停留在存取款/转账二级页面并继续触发客户端请求的问题
- 修复管理控制台状态栏显示翻译 key 而不是本地化文本的问题
- 修复英文语言文件缺少 1.2.5 新增翻译键的问题
- 修复专用服务器注册网络包时加载客户端 GUI 类，导致 IDEA / `runServer` 启动崩溃的问题
- 修复英文环境下银行卡绑定名、银行标题和货币单位仍显示中文的问题
- 修复银行二级页面按 ESC 直接关闭 GUI 的问题，现在会优先返回上一级
- 修复个人银行收件箱分页按钮在无下一页时仍可点击的问题
- 修复企业注册终端检查企业代码后清空输入框的问题
- 修复企业折线图把“分”当作“元”压缩显示，导致金额数量级错误的问题
- 支持玩家直接向官方国库 ID `YB` 转账，金额进入 `BigInteger` 国库余额
- 调整国库扣款逻辑，官方国库余额允许为负数
- 企业代码规则改为 3-4 位大写字母或数字
- 所有转账备注限制为 64 字符
- 管理员备注限制为 64 字符，列表默认显示截断文本，悬浮查看全文
- 个人银行收件箱支持多选、批量已读、批量删除；星标邮件不可删除，批量删除会跳过星标邮件
- 企业注册只能通过企业注册终端打开和创建，网络层拒绝随身注册
- 企业成员面板支持搜索、排序、全选、反选、多选、批量移出和批量转账
- 企业 OWNER 注销流程改为确认、5 秒倒计时、最终不可撤销确认
- 国库增加独立流水记录，支持最近 10000 条调账流水和直接设置余额
- 提亮原生银行 GUI 主题文字与面板对比度，降低游戏菜单背景和深色叠层对可读性的影响
- 企业批量转账弹窗改为金额与备注两个独立输入框，并使用独立不透明背景
- 修复企业批量转账目标列表网络字段过短，选择多名成员时 UUID 列表可能被截断或发送失败的问题
- 修复个人银行首页与明细列表文字过暗、卡片和列表行背景过透明导致内容难以阅读的问题
- 修复银行操作成功/失败提示在切换到其他面板后仍残留显示的问题
- 将个人银行、企业银行、梦澄银行控制台及确认/输入弹窗统一重置为两层 UI：深灰背景层 + 文字/原生控件层，移除额外面板、卡片、表格行底色和调色叠层
- 移除所有银行 GUI 左上角退出按钮，保留原有返回按钮和 ESC 行为
- 统一个人银行首页“切换企业”和“收件箱”按钮尺寸

### 新增文件

| 文件 | 说明 |
| --- | --- |
| `bank/account/BankAccountType.java` | 账户类型枚举 |
| `bank/admin/BankAccountQuery.java` | 账户查询参数 |
| `bank/admin/BankAccountSortField.java` | 账户排序字段枚举 |
| `bank/admin/BankAdminAction.java` | 管理员操作类型枚举 |
| `bank/admin/BankAdminService.java` | 管理员服务接口 |
| `bank/service/ServerBankAdminService.java` | 管理员服务实现 |
| `network/payload/BankAdminRequestPayload.java` | 管理员请求网络包 |
| `network/payload/BankAdminSnapshotPayload.java` | 管理员快照网络包 |
| `item/BankAdminCardItem.java` | 管理卡物品 |
| `client/gui/BankAdminScreen.java` | 管理员控制台 GUI |
| `bank/enterprise/*.java` | 企业账户、成员、权限和企业流水模型 |
| `bank/inbox/*.java` | 个人银行收件箱消息模型 |
| `bank/storage/EnterpriseSavedData.java` | 企业账户、收件箱和企业偏好存档 |
| `bank/service/ServerEnterpriseService.java` | 企业账户服务端用例 |
| `block/EnterpriseRegistrationTerminalBlock.java` | 企业注册终端方块 |
| `client/gui/EnterpriseScreen.java` | 企业银行 GUI |
| `client/gui/foundation/*.java` | 原生 GUI 主题、渲染和折线图工具 |
| `network/payload/Enterprise*.java` | 企业请求和快照网络包 |
| `network/payload/InboxMessageView.java` | 收件箱消息快照 |
| `models/item/bank_admin_card.json` | 管理卡物品模型 |
| `textures/item/bank_admin_card.png` | 管理卡贴图 |
| `blockstates/enterprise_registration_terminal.json` | 企业注册终端方块状态 |
| `models/block/enterprise_registration_terminal.json` | 企业注册终端方块模型 |
| `models/item/enterprise_registration_terminal.json` | 企业注册终端物品模型 |

### 修改文件

| 文件 | 变更 |
| --- | --- |
| `bank/account/BankAccount.java` | 从 record 改为 class，新增元数据字段 |
| `bank/storage/BankSavedData.java` | 重写，支持账户管理、国库、管理员操作 |
| `network/ServerPayloadHandlers.java` | 新增管理员处理和冻结检查 |
| `network/ClientPayloadHandlers.java` | 新增管理员快照处理，修复转账加载 |
| `network/ModPayloads.java` | 注册管理员网络包 |
| `network/payload/BankSnapshotPayload.java` | 新增 `accountFrozen`、收件箱、企业摘要和偏好字段 |
| `network/payload/TransferRequestPayload.java` | 新增个人转企业目标标记 |
| `bank/ui/BankScreenAccessService.java` | 新增冻结快照方法 |
| `client/gui/BankScreen.java` | 重构为个人银行原生 GUI，新增收件箱和企业切换 |
| `registry/ModBlocks.java` | 注册企业注册终端方块 |
| `registry/ModItems.java` | 注册管理卡物品 |
| `registry/ModCreativeTabs.java` | 管理卡和企业注册终端加入创造模式标签页 |
| `lang/zh_cn.json` | 新增所有翻译键 |
| `lang/en_us.json` | 新增企业账户、收件箱和原生 GUI 翻译键 |
| `gradle.properties` | 版本号 1.2.1 -> 1.2.5 |

### 待处理

- 企业账户 GUI 视觉细节继续打磨，尤其是小窗口响应式布局和交互位置
- 企业折线图当前基于最近余额点，后续需要改为 24 小时 / 7 天时间桶统计
- 企业注册终端模型暂时复用 ATM 材质，需要补正式美术
- 管理员控制台样式优化（参考新的银行 GUI 风格）
