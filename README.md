# Console Banking Ledger

**Java 17+ | Maven | Gson | JSON persistence | Command-line application**

## 1. Overview

Console Banking Ledger is a Java command-line application for managing customer profiles, savings and current accounts, financial transactions, and account statements. It has two roles:

- **Administrator:** manages customers and accounts, unlocks users, resets PINs, views the system ledger, posts monthly savings interest, and reverses eligible transactions.
- **Customer:** manages their own accounts, deposits and withdraws money, transfers funds, filters transaction history, and exports statements.

The application stores records locally in JSON files. It is a teaching project and demonstration ledger, not a production banking platform.

## 2. Requirements and running the application

Requirements:

- JDK 17 or newer.
- Apache Maven.
- Gson (already used by the project's JSON repositories).

Run from the project root:

```bash
mvn clean compile exec:java
```

Run automated tests:

```bash
mvn test
```

The source targets Java 17. In `pom.xml`, prefer the Maven compiler setting `<maven.compiler.release>17</maven.compiler.release>` rather than separately setting `source` and `target`.

## 3. Authentication and PIN security

Users sign in with a user ID or email address and a **numeric PIN containing 4–6 digits**. The PIN is entered at a separate prompt and is never stored as plain text.

The authentication service uses:

- A randomly generated salt for each user.
- PBKDF2-HMAC-SHA256 to derive a stored PIN hash.
- Three consecutive failed attempts before the user is locked.
- Automatic failed-attempt reset after a successful login.
- Administrator-only unlocking and PIN resets.
- Forced PIN changes for first-time administrator login and administrator-issued temporary PINs.

### First administrator login

If there is no administrator in `data/users.json`, the application creates one on first startup:

| Field | Default |
|---|---|
| Email | `admin@bank.local` |
| Temporary PIN | `482916` |

Set `BANK_ADMIN_EMAIL` and `BANK_ADMIN_PIN` environment variables before the first run to use different initial administrator credentials. The PIN must contain 4–6 digits. Change the temporary PIN immediately after signing in:

```text
login admin@bank.local
change-pin
```

The CLI prompts for the current PIN and then the new PIN.

When no terminal console is available (for example, some IDE consoles), PIN input may be visible while typing. Use a terminal with a Java console for hidden PIN input.

## 4. Customer commands

Type `help` to see the commands available to the current role.

| Command | Description |
|---|---|
| `register <email> "<full name>"` | Register a customer; the CLI prompts for a 4–6 digit PIN. |
| `login <user-id-or-email>` | Sign in using the PIN prompt. |
| `logout` | End the current session. |
| `change-pin` | Change the current user's PIN. |
| `open-savings <initial-deposit> <daily-limit> <minimum-balance>` | Open a savings account. |
| `open-current <initial-deposit> <daily-limit> <overdraft-limit>` | Open a current account. |
| `accounts` | List the customer's accounts and balances. |
| `balance <account>` | Show an account balance. |
| `deposit <account> <amount>` | Deposit money. |
| `withdraw <account> <amount>` | Withdraw money, subject to account rules and fees. |
| `transfer <from-account> <to-account> <amount>` | Transfer money to another existing account. |
| `statement <account> [<from-date> <to-date>]` | Display a statement, optionally for an inclusive date range. |
| `transactions <account> [<type|->] [<min|->] [<max|->] [<from-date> <to-date>]` | Filter transaction history using any combination of type, amount bounds, and date range. |
| `export-statement <account> [<from-date> <to-date>]` | Export a statement as a JSON file under `statements/`. |
| `help` | Show commands available to the current role. |
| `exit` | Close the application. |

Use `-` as a placeholder when omitting a filter but supplying a later filter. For example:

```text
transactions 8ab... DEPOSIT 100.00 5000.00 2026-01-01 2026-01-31
transactions 8ab... - 100.00 -
transactions 8ab... - - - 2026-01-01 2026-01-31
export-statement 8ab... 2026-01-01 2026-01-31
```

Dates use `YYYY-MM-DD`. Amounts must be positive where required and have no more than two decimal places.

## 5. Administrator commands

The administrator has additional privileges. The command parser and service layer both enforce role access.

| Command | Description |
|---|---|
| `customers` | List registered customers. |
| `create-customer <email> "<full name>"` | Create a customer; the CLI prompts for a temporary PIN. |
| `customer <customer-id-or-email>` | View customer details and their accounts. |
| `accounts-for <customer-id>` | List one customer's accounts. |
| `all-accounts` | List all accounts. |
| `open-savings-for <customer-id> <deposit> <daily-limit> <minimum-balance>` | Open a savings account for an existing customer. |
| `open-current-for <customer-id> <deposit> <daily-limit> <overdraft-limit>` | Open a current account for an existing customer. |
| `unlock <user-id-or-email>` | Unlock a locked user. |
| `set-pin <user-id-or-email>` | Reset a user's PIN; the user must change it after login. |
| `close-account <account-number>` | Close an account only when its balance is exactly zero. |
| `ledger` | View the system-wide transaction ledger. |
| `reverse-transaction <account> <transaction-id>` | Create a compensating entry for an eligible transaction. A transfer reversal compensates both transfer sides. |
| `post-monthly-interest` | Post the current month's savings interest, without duplicating an existing posting. |

Customer creation and PIN resets prompt for a PIN rather than accepting it as a command-line argument.

## 6. Account rules, interest, and withdrawal fees

### Savings accounts

- Enforce the configured minimum balance.
- Enforce the account's daily withdrawal cap.
- Earn interest at **2% per annum**, calculated as one-twelfth of the annual rate and rounded to two decimal places using `HALF_UP`.
- Monthly interest is recorded as an `INTEREST` transaction.

Monthly interest posting is attempted automatically when the application starts. An account receives no more than one `INTEREST` entry per calendar month. Administrators can also run `post-monthly-interest`; repeating it in the same month does not duplicate the credit. The application must be run at least once during a month for that month's automatic posting to occur; it does not currently calculate backdated interest for months in which the application was never run.

### Current accounts

- Permit an overdraft up to the configured limit.
- Enforce the configured daily withdrawal cap.
- Do not earn interest.
- The first **five cash withdrawals per calendar month are free**. A flat fee is applied to the sixth and subsequent cash withdrawals in that month. The fee is recorded as a separate `FEE` ledger entry.

The default flat fee is **₦50.00** because the assignment brief does not specify a fee amount. Configure a different non-negative fee (with at most two decimal places) using `BANK_CURRENT_WITHDRAWAL_FEE` before starting the application. Transfers are not counted as cash withdrawals for this fee rule.

All monetary calculations use `BigDecimal`; `double` and `float` are not used for account balances or transaction amounts.

## 7. Reversals and ledger integrity

Transactions are append-only. A reversal does not edit or delete the original entry; it creates a new `REVERSAL` entry that references the original transaction ID.

The administrator can reverse eligible deposits, opening deposits, withdrawals, fees, and interest entries. A transfer reversal finds the matching debit and credit entries and compensates both accounts in one coordinated operation. A transaction cannot be reversed more than once. A reversal is rejected when its compensating operation would violate the account's minimum-balance or overdraft rules.

Every ledger record contains a transaction ID, type, amount, timestamp, account number, resulting balance, description, and reference ID. Transfer debit and credit entries share a transfer reference.

## 8. Filtering and statement export

Transaction history can be filtered by:

- Transaction type (for example, `DEPOSIT`, `WITHDRAWAL`, `TRANSFER_IN`, `FEE`, `INTEREST`, or `REVERSAL`).
- Minimum amount.
- Maximum amount.
- Inclusive start and end dates.

Filters can be combined in one `transactions` command. `export-statement` writes a JSON document containing the account details, statement period, export timestamp, current balance, and selected transaction records. Exported files are saved in the `statements/` directory, which is separate from the application's live data files.

## 9. Architecture and design patterns

The project separates responsibilities into packages:

| Package | Responsibility |
|---|---|
| `model` | Users, roles, customers, accounts, and immutable transaction records. |
| `service` | Authentication, customer/admin operations, account management, transfers, interest, fees, reversals, and statements. |
| `repository` | Storage contracts for users, accounts, and transactions. |
| `repository.json` | Gson-based JSON persistence. |
| `repository.memory` | In-memory repositories for demonstrations and tests. |
| `strategy` | Interchangeable interest and withdrawal-fee calculation policies. |
| `command` | Command parsing, access rules, and dispatch. |
| `cli` | Terminal input and output. |
| `exception` | Specific exceptions for expected banking and authentication failures. |

The implementation demonstrates:

- **Factory Pattern:** `AccountFactory` creates and reconstructs the correct account subtype while retaining saved account numbers.
- **Repository Pattern:** services depend on repository interfaces rather than JSON implementation classes.
- **Strategy Pattern:** `SavingsInterestStrategy` and `CurrentAccountFeeStrategy` implement changeable financial rules.
- **Object-oriented design:** inheritance and polymorphism represent savings/current account differences; encapsulation keeps domain state within model objects.

## 10. Concurrency and consistency

Financial operations are coordinated in the service layer. A fair ledger lock serializes commits to the shared ledger, and per-account locks protect the affected accounts. When more than one account is involved, locks are acquired in ascending account-number order and released in reverse order to avoid lock-order deadlocks.

Transfers and transfer reversals are treated as single logical operations. The JSON repository writes the ledger first and then replaces all affected account snapshots in one account-file update. Each account's `transactionCount` is a commit marker: during startup recovery, ledger entries beyond the committed count are discarded if the process stopped between the two file writes.

This coordination protects operations within one running application process. It is not a distributed lock and does not make it safe to run multiple application processes against the same `data/` directory simultaneously. Run one instance at a time.

## 11. JSON data files

Application data is stored under `data/`:

| File | Contents |
|---|---|
| `users.json` | User IDs, names, email addresses, roles, salted PIN hashes, failed-attempt counts, lock status, and forced-PIN-change status. |
| `accounts.json` | Account numbers, owners, account type, balances, account limits, closed status, and transaction-count commit markers. |
| `transactions.json` | The system-wide append-only transaction ledger. |

Money values are stored as decimal strings. Timestamps use ISO-8601 local date-time strings. JSON writes use a temporary file and attempt an atomic replacement. Startup reports malformed or unreadable records instead of silently replacing them.

Keep `data/` out of source control because it contains customer and financial information. Keep `statements/` out of source control if exported customer statements are present.

## 12. Migration from the earlier email/password version

The new user repository still reads the existing `users.json` user records, and old account numbers and transaction histories are retained.

- Existing credential records from the previous patch are recognised as legacy credentials. Their old password can be used for one migration login; the user is then required to run `change-pin` and choose a 4–6 digit PIN.
- Older customer records that never had a persisted password hash cannot authenticate directly. An administrator must use `set-pin <user-id-or-email>` to create a temporary PIN for them.
- Existing account and transaction data remain linked to their owners. Back up the `data/` directory before replacing source files.

## 13. Custom exceptions

The `exception` package includes named exceptions for the main expected failure cases:

- `InsufficientFundsException`
- `AccountNotFoundException`
- `AccountLockedException`
- `DailyLimitExceededException`
- `InvalidAmountException`
- `AuthenticationException`
- `UnauthorizedActionException`
- `AccountClosedException`
- `TransactionNotReversibleException`
- `StatementExportException`

These extend `IllegalArgumentException` or `IllegalStateException` so the CLI can present clear messages without terminating the command loop.

## 14. Demo and testing notes

`ServicesDemo.java` demonstrates the service layer with in-memory repositories. It uses the updated service constructors and a numeric demo PIN. Its data disappears when the demo process ends.

The main CLI uses the JSON repositories and retains data between runs. Before submission, run `mvn clean compile`, `mvn test`, and the end-to-end administrator/customer workflows. In particular, verify restart persistence, failed-login locking/unlocking, fee threshold behaviour, monthly interest idempotency, transfer/reversal consistency, filtering, and exported JSON contents.
