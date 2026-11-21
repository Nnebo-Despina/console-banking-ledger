# Console Banking Ledger

A Java console application for opening savings and current accounts, moving money, and reading account statements.

## Run locally

Use JDK 17 or newer and Maven. Run this command from the project root.

```sh
mvn compile exec:java
```

Run the Maven test lifecycle with `mvn test`. Keep local build output under `target/` out of commits.

## Commands

Type `help` to see the commands available to your current session. Guests can register or sign in. Signed-in customers can manage their own accounts and transfer to another customer using the destination account number.

| Command | What it does |
| --- | --- |
| `register <email> "<full name>"` | Create a customer profile. The CLI prompts for a password. |
| `login <email>` | Sign in with the password supplied at the prompt. |
| `open-savings <initial-deposit> <daily-limit> <minimum-balance>` | Open a savings account. |
| `open-current <initial-deposit> <daily-limit> <overdraft-limit>` | Open a current account. |
| `accounts` | Show your accounts, their numbers, and their balances. |
| `balance <account>` | Show the balance of one of your accounts. |
| `deposit <account> <amount>` | Deposit into one of your accounts. |
| `withdraw <account> <amount>` | Withdraw from one of your accounts. |
| `transfer <from-account> <to-account> <amount>` | Move money from one of your accounts to another account. |
| `statement <account> [<from-date> <to-date>]` | Show a statement. Supply both dates for an inclusive date range. |
| `logout` | End the current session. |
| `exit` | Close the program. End of input also closes it. |

For example, enter `register ada@example.com "Ada Obi"`, then `login ada@example.com` in the same run. Use `open-savings 100.00 50.00 10.00` to open an account. Copy its account number from `accounts` when running `deposit` or `statement`. Use decimal amounts such as `25.50` and dates such as `2026-11-21`.

Use a terminal that provides Java with a console when entering passwords. Some IDE consoles and redirected input display what you type.

## Local data

Run one instance at a time from the project root. The application creates `data/` on the first save. Keep this directory out of commits because it contains customer and account data. Customer profiles go in `data/users.json`, account balances and limits in `data/accounts.json`, and transaction history in `data/transactions.json`. Money is written as decimal strings and timestamps use ISO-8601 local date and time. Each account entry has a `SAVINGS` or `CURRENT` type and keeps its original number when loaded.

Each save writes a temporary file beside the destination and then moves it into place. The application attempts an atomic move and falls back to a regular replacement when needed. For each account save, the account record's transaction count commits the corresponding history. A run interrupted between those writes reloads the last committed balance and history for that account. Startup validates the files before accepting commands. If a file is malformed, it exits with an error identifying the file so you can inspect it or restore it from a backup.

## Current integration limits

- `AuthenticationService` keeps password hashes in memory. Customer profiles, accounts, and transactions reload, but a customer can sign in only during the run in which they registered. Persistent sign-in needs a credential storage contract from the authentication service.
- The help menu supports guest and customer sessions. Additional roles need support from the user model and authentication service.
- Account creation uses `AccountFactory` through `AccountService`. Reloading uses the account constructors because the factory needs an overload that takes the saved account number.
- `TransactionService` saves history through `AccountRepository`; there is no separate transaction repository interface. `TransferService` saves its two accounts separately, so handling an interruption between those saves needs a service-level change.
