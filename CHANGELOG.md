# Changelog

## 1.2.1

- Improved the bank GUI with responsive panel sizing.
- Transaction history now dynamically adjusts visible rows based on the player's window height.

## 1.2.0

v1.2.0 focuses on stabilizing the bank core rather than adding broad new gameplay.

- Added UUID-bound bank cards. A card binds on first use, gains a one-time default name, and uses UUID ownership rather than display name for authorization.
- Added request replay protection. Client bank requests now carry `requestId`, and the server keeps a bounded processed-ID cache per player.
- Hardened account and transaction storage against negative balances, malformed saved records, and unbounded ledger scans.
- Added transaction indexing by owner for faster history queries.
- Added explicit ATM/card access channel context so deposit and withdraw ledger rows can show `ATM存入`, `ATM取现`, `随身银行存入`, or `随身银行取现`.
- Added transfer account existence checks. Transfers to resolvable players without bank accounts now fail instead of implicitly creating receiver accounts.
- Added a transfer loading screen after confirmation. Failed missing-target/account transfers clear only the target field while preserving amount and note.
- Added small future-facing account identity types, `BankAccountId` and `BankAccountKind`, as a hook for personal and organization accounts.
- Fixed a creative tab crash by only adding resolved item instances to the finance creative tab.

Build:

```powershell
.\gradlew.bat build
```
