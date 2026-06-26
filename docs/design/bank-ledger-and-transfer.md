# Bank Ledger and Transfer Design

This document describes the bank features that sit on top of the basic account balance model.

## Module Boundaries

| Module | Package | Responsibility |
| --- | --- | --- |
| Account storage | `bank.storage` | Owns persistent balances and transaction records through `SavedData`. |
| Account API | `api.bank` | Exposes bank use cases to UI, items, blocks, and networking. |
| Ledger API | `api.transaction` | Defines append/query behavior for transaction records. |
| Ledger model | `transaction` | Defines transaction types, records, filters, and sort options. |
| Transfer resolution | `bank.transfer` | Resolves a player name or UUID into a transfer target. |
| Server use cases | `bank.service` | Applies deposits, withdrawals, and transfers, then writes ledger records. |
| Server UI access | `bank.ui` | Converts service results into client snapshots. |
| Network payloads | `network.payload` | Carries requests and read-only bank snapshots. |
| Client GUI | `client.gui` | Displays snapshots and sends requests only. It does not mutate balances. |

## Transaction Records

Each persisted record is represented by `TransactionRecord`:

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

Records are owned by a single player. A player can view up to the latest 1000 records after client-side filters and sorting. Transfer operations write two records:

- `TRANSFER_OUT` for the sender.
- `TRANSFER_IN` for the receiver.

## Transfer Rules

Transfers are server-authoritative. The client opens a vanilla confirmation screen and sends a transfer request only after the player confirms.

Transfers may include an optional short note. The note is stored on both the sender and receiver records so either side can review the same context later.

The server accepts recipients by:

- UUID, even when the player is offline.
- Online player name.
- Cached profile name from the server profile cache.

If a name cannot be resolved, the transfer fails with a normal bank snapshot message. Future online-name lookup or account-directory features should extend `bank.transfer` rather than the GUI.

## Snapshot Flow

`BankSnapshotPayload` contains:

- Current balance.
- User-facing message.
- Message translation key, so the client can react to specific failures without parsing display text.
- Bank access channel, used by future requests to preserve ATM/card context.
- Up to 1000 recent ledger rows.

The GUI filters and sorts the received rows locally for fast interaction. History search intentionally matches only counterparty name, counterparty UUID, note text, and the transaction amount. It does not search transaction type labels or timestamps.

## Maintenance Notes

- Keep money values in `long cents`.
- Keep GUI and payload code read-only: they should never write balances directly.
- Every mutating request payload must carry a positive `requestId`; server handlers reject replayed IDs per player.
- Deposit and withdraw requests must carry `BankAccessChannel`, currently `ATM` or `CARD`.
- Transfers to resolved UUIDs without existing accounts must fail with `message.yumesumi_finance.transfer.account_not_found`; do not create receiver accounts implicitly.
- Add new business operations to `BankService` first, then implement them in `ServerBankService`.
- Add new persisted transaction fields through `BankSavedData` load/save methods with backwards-compatible defaults.
- Keep player or profile resolution in `bank.transfer`, not in the screen.
