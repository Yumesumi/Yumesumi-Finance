# Yumesumi Finance Agent Handoff

This file is the fast-start guide for future coding agents. Read it before changing code, then read `README.md` and the relevant files under `docs/design/`.

## Project Snapshot

- Project: Yumesumi Finance / 梦澄金融
- Current version: 1.2.6
- Target: Minecraft Java Edition 1.21.1
- Loader: NeoForge 21.1.x
- Language: Java 21
- Currency: Yumesumi Dollar
- Currency code: YSD
- Symbol: S$
- Minor unit: cent, displayed as `¢`
- Internal money representation: `long cents`; never use `double` for financial math.

The mod adds a server-authoritative financial system: physical cash, bank cards, ATM access, bank accounts, deposits, withdrawals, player transfer, transaction history, bank admin controls, inbox messages, and first-pass enterprise accounts. Future versions expand this into payment, richer organization accounts, interest, loans, and wider economy systems.

## Roadmap Context

### v1.2: Stable Bank Core

Status: completed in 1.2.0. Focus was correctness and anti-duplication, not broad new gameplay.

- Harden account data.
- Route all deposit/withdraw/transfer mutations through `BankService`.
- Make operations fail atomically and roll back partial changes.
- Block double-click abuse, network replay, negative amounts, and overflow.
- Bind bank cards to player UUIDs.
- Improve transaction query performance.

Goal: money must not be wrong, ledgers must not drift.

### v1.3: Payment System

- POS machine / payment receiver block.
- Player-to-player payment flow.
- Shop receiving accounts.
- Recent payees / recent transfer targets.
- Configurable transfer fees.

Goal: move from "banking mod" to "payment mod".

### v1.4: Account Expansion

- Multiple accounts.
- Organization / guild accounts.
- Joint accounts.
- Account permission management.
- Frozen accounts and admin audit.

Goal: support server economic organizations.

### v1.5: Interest And Loans

- Demand deposit interest.
- Fixed deposit.
- Loans.
- Overdue penalty interest.
- Credit rating.

Goal: give players financial decisions.

### v1.6: Advanced Economy

- Wealth management products.
- Stocks / funds.
- Multiple currencies.
- Exchange rates.
- Taxes.
- Server central bank.

Goal: evolve into a server economy ecosystem if the server scale justifies it.

## Architecture Rules

- The server is the only authority for financial data.
- Client GUI only sends requests and displays server snapshots.
- GUI, item, block, and network code must not mutate balances directly.
- Business operations belong in `api.bank.BankService` and `bank.service.ServerBankService`.
- Personal balances and personal ledger records are owned by `bank.storage.BankSavedData`; enterprise accounts and enterprise ledgers are owned by `bank.storage.EnterpriseSavedData`.
- Physical cash inventory operations go through `api.money.CashWallet`.
- Player or profile resolution belongs in `bank.transfer`, not in screens.
- New features should extend interfaces first, then implementation.
- Saved data changes must be backward compatible.

## Important Packages

- `com.yumesumi.finance.api`: low-coupling interfaces.
- `api.bank`: banking use cases and account repository.
- `api.money`: physical cash wallet abstraction.
- `api.transaction`: ledger append/query abstraction.
- `bank.service`: server-side bank use cases.
- `bank.enterprise`: enterprise account, member role, and enterprise ledger models.
- `bank.inbox`: personal bank inbox message models.
- `bank.storage`: `SavedData` implementation for balances, transaction records, enterprises, inbox, and screen preference.
- `bank.transfer`: transfer target resolution.
- `bank.ui`: server-side GUI access service and snapshot sending.
- `client.gui`: client personal bank, enterprise bank, and admin bank screens.
- `client.gui.foundation`: shared native GUI theme, card rendering, and line-chart helpers.
- `money`: denominations, formatting, custom cash data.
- `money.cash`: player inventory cash implementation.
- `network`: NeoForge payload registration and handlers.
- `transaction`: transaction records, types, filters, sorting, and results.

## Current Implementation Notes

- `BankService` already exposes balance, deposit, deposit-all, withdraw, transfer, and recent transaction use cases.
- `ServerBankService` is the central mutation path today.
- `BankSavedData` currently implements both account storage and transaction storage.
- `EnterpriseSavedData` stores enterprise accounts, enterprise code index, inbox messages, and the player's last personal/enterprise preference.
- `EnterpriseAccount` is independent from personal `BankAccount`; do not model enterprises as a player's second balance.
- Enterprise codes are globally unique 3-4 character uppercase letter/digit IDs and are resolved through enterprise service logic.
- `ServerEnterpriseService` owns enterprise registration, invitations, role checks, enterprise transfers, settings, and disband flow.
- Enterprise roles are `OWNER`, `ADMIN`, and `MEMBER`; only `OWNER` can manage roles and disband, while `ADMIN` can manage money and invite members.
- `BankSavedData` indexes transaction records by owner for query performance; keep this index in sync with the persisted transaction list.
- Transfer writes two records: `TRANSFER_OUT` for sender and `TRANSFER_IN` for receiver.
- Enterprise transfers write enterprise ledger rows in `EnterpriseSavedData`; do not mix them into the personal transaction index unless a personal account is also a counterparty.
- Bank snapshots send balance, message, message key, success flag, access channel, frozen flag, inbox, enterprise summaries, preference, and up to 1000 transaction rows.
- `BankCardItem` binds itself to the first server-side user through the `bank_card` data component and rejects other players.
- A newly bound bank card gets a one-time custom name of `<player>的梦澄银行卡` and an enchantment glint override. Do not use the displayed name for authorization; players may rename the card with an anvil after binding.
- `MoneyData` is reserved for custom cash such as commemorative bills or event currency.
- Network bank requests carry a client requestId. The server stores a bounded per-player set of processed request IDs and rejects replays.
- Deposit/withdraw requests carry `BankAccessChannel`; ledger counterparty text uses it for entries such as `ATM存入`, `ATM取现`, `随身银行存入`, and `随身银行取现`.
- Transfers to resolvable UUIDs without an existing account fail with `message.yumesumi_finance.transfer.account_not_found` and must not create a receiver account.
- Transfers to `YB` are resolved through `bank.transfer` to the official treasury and credit `BankSavedData.treasuryBalance`.
- Personal-to-enterprise transfers are explicit and require `TransferRequestPayload.targetEnterprise() == true`; target lookup uses enterprise code.
- Enterprise-origin transfers use `EnterpriseRequestPayload` and `ServerEnterpriseService`, not `ServerBankService.transfer`.
- Frozen accounts cannot deposit, withdraw, or transfer; they can still view transaction history.
- Admin notes are visible/editable only in the bank admin console and are capped at 64 characters.
- Deleting an account does not delete ledger history. If a deleted account has a non-zero balance, that balance moves into the official treasury.
- `BankScreen` uses responsive sizing. History rows per page are calculated from available panel height, not a fixed constant.
- `BankScreen` is now the personal bank GUI and includes inbox and enterprise switching. `EnterpriseScreen` handles register/list/detail enterprise views. Both are first-pass native GUI implementations and still need visual polish.

When docs disagree, prefer the current code and `docs/design/bank-ledger-and-transfer.md`; `docs/design/transactions.md` still describes an older lightweight transaction stage.

## v1.2 Risk Checklist

Before marking v1.2 work complete, audit these areas:

- Amount validation rejects `<= 0` at every server entry point.
- Additions use `Math.addExact` or equivalent overflow-safe code.
- Balance mutation cannot create negative balances, including repository-level calls.
- Multi-step operations have rollback or are reordered so failed later steps do not duplicate or destroy money.
- Network handlers cannot be abused by repeated identical requests in the same tick/window.
- Replay protection uses requestId, not payload fingerprints. Any new client bank request payload must include requestId and call the same server replay guard.
- Client confirmation is treated only as UX; server still validates everything.
- Bank cards carry and enforce an owner UUID.
- Transaction history remains bounded per owner and query cost is acceptable for large servers.
- Ledger records are written only after successful balance/cash changes.
- SavedData load handles old/missing fields safely.
- If a feature adds another balance mutation path, it must still call `BankService` or a narrower service-level primitive, not `BankSavedData` directly.
- Do not create recipient accounts implicitly during transfer. Account creation must be an explicit future workflow.

## Known Follow-Up Targets

- Consider an operation result/transaction primitive for atomic balance updates and ledger append.
- If future account types arrive, do not overload the player UUID as the whole account identity. Introduce an `AccountId` model first.
- `BankAccountId` and `BankAccountKind` exist as a narrow future-facing hook. Current storage still supports only personal UUID accounts.
- Consider renaming or clarifying `createdGameTime`; current service writes wall-clock milliseconds via `System.currentTimeMillis()`.
- Split `BankSavedData` into account and ledger stores only when it removes real complexity; premature splitting would add migration risk today.

## Build And Verification

Use the repository Gradle wrapper.

```powershell
.\gradlew.bat build
```

For manual game checks:

```powershell
.\gradlew.bat runClient
.\gradlew.bat runServer
```

On this machine, Java 21 may be supplied by:

```powershell
$env:JAVA_HOME='C:\Program Files\Java\graalvm-community-openjdk-21.0.2+13.1'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
```

## 开发提示

- 回复必须使用中文，涉及代码标识符时保留原文。
- 如果本地有开发服务器且未占用默认端口，实现后应启动它并给出访问地址；如果被占用则改用其它端口。
- 并行读取多个本地文件时，优先使用 `multi_tool_use.parallel`，不要拆成多段 shell 命令。

## Suggested New-Session Prompt

Use this when opening a new vibe-coding session:

```text
You are maintaining Yumesumi Finance / 梦澄金融, a Minecraft NeoForge 1.21.1 Java 21 finance mod.

First read AGENTS.md, README.md, and docs/design/*.md. Treat AGENTS.md as the handoff guide and prefer current source code when older docs disagree.

Current version is 1.2.6. The next milestone is v1.3 Payment System: POS/payment receiver blocks, player-to-player payment workflow, shop receiving accounts, recent payees, and configurable transfer fees. The repo also contains first-pass enterprise accounts and native GUI refactors; do not weaken v1.2 bank-core protections while extending them.

Currency is Yumesumi Dollar, code YSD, symbol S$, cents as ¢. Internally all amounts are long cents.

Follow the architecture rule: server is authoritative; GUI and network are request/snapshot only; personal financial mutations belong in BankService/ServerBankService; enterprise mutations belong in ServerEnterpriseService; personal persistence belongs in BankSavedData; enterprise persistence belongs in EnterpriseSavedData; player target resolution belongs in bank.transfer.

Before editing, inspect the relevant current source files and preserve existing project style. After editing, run .\gradlew.bat build if feasible and report any failures.
```
