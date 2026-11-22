# CONSOLE BANKING LEDGER
**Java Console Banking Application**

**Technologies:** Java 17+, Maven, Gson, JSON File Persistence, Object-Oriented Programming

## 1. Project Overview

The Console Banking Ledger is a Java-based command-line banking application designed to manage customers, bank accounts, financial transactions, and account statements.

The system supports two user roles: **Administrator** and **Customer**. Administrators manage customer records and banking operations, while customers can access their personal accounts and perform permitted financial transactions.

The application uses JSON files for persistent data storage, allowing customer information, account records, and transaction histories to remain available after the application closes.

## 2. Installation and Execution

### Requirements
- Java Development Kit (JDK) 17 or newer.
- Apache Maven.
- Gson dependency for JSON processing.

### Run the application

From the project root directory, execute:

```bash
mvn compile exec:java
```

To execute the project's automated tests:

```bash
mvn test
```

## 3. Customer Features and Commands

Customers can register, log in, manage their accounts, and perform financial transactions.

| Command | Description |
|---|---|
| `register <email> "<full name>"` | Register a new customer. |
| `login <email>` | Sign in using an email and password. |
| `logout` | End the current session. |
| `open-savings <initial-deposit> <daily-limit> <minimum-balance>` | Open a savings account. |
| `open-current <initial-deposit> <daily-limit> <overdraft-limit>` | Open a current account. |
| `accounts` | Display the customer's accounts and balances. |
| `balance <account>` | Check an account balance. |
| `deposit <account> <amount>` | Deposit money. |
| `withdraw <account> <amount>` | Withdraw money, subject to account rules. |
| `transfer <from-account> <to-account> <amount>` | Transfer money between accounts. |
| `statement <account> [<from-date> <to-date>]` | Generate an account statement, optionally for a date range. |
| `help` | Display commands available to the customer. |
| `exit` | Close the application. |

Passwords are entered through a separate prompt rather than being included in the command itself.

## 4. Administrator Features and Commands

The application includes an Administrator role with additional privileges for managing the banking system.

### Administrator capabilities

- Create customer accounts and register new customers.
- View all registered customers.
- Retrieve an individual customer's information.
- View accounts belonging to a particular customer.
- Open savings and current accounts for existing customers.
- Unlock users whose accounts have been locked following failed login attempts.
- Reset customer passwords.
- Close accounts with a zero balance.
- View the system-wide transaction ledger.

### Administrator commands

| Command | Description |
|---|---|
| `login <email>` | Sign in as administrator. |
| `customers` | List registered customers. |
| `create-customer <email> "<full name>"` | Create a customer account. |
| `customer <customer-id-or-email>` | View customer information. |
| `accounts-for <customer-id>` | View a customer's accounts. |
| `all-accounts` | View all bank accounts. |
| `open-savings-for <customer-id> <deposit> <daily-limit> <minimum-balance>` | Open a savings account for a customer. |
| `open-current-for <customer-id> <deposit> <daily-limit> <overdraft-limit>` | Open a current account for a customer. |
| `unlock <email>` | Unlock a customer's account. |
| `set-password <email>` | Reset a customer's password. |
| `close-account <account-number>` | Close an eligible account. |
| `ledger` | View all recorded financial transactions. |
| `change-password` | Change the administrator's password. |
| `logout` | End the administrator session. |

Commands are role-restricted, meaning customers cannot execute administrator-only operations.

## 5. Administrator Account Setup

On the first application startup, the system automatically creates a default administrator if no administrator account already exists.

**Default credentials:**

- Email: `admin@bank.local`
- Temporary password: `ChangeMe123!`

To log in:

```text
login admin@bank.local
```

The administrator must change the temporary password before accessing the other administrative functions.

```text
change-password
```

The system prompts for the current password and the new password.

## 6. Security and Authentication

The application implements persistent authentication and basic account protection.

Security features include:

- Passwords are never stored as plain text.
- Passwords are hashed using PBKDF2-HMAC-SHA256 with randomly generated salts.
- Authentication credentials are saved in `users.json`.
- Three consecutive failed login attempts lock a user account.
- Successful login resets the failed-login counter.
- Administrators can unlock locked customer accounts.
- Administrators and customers have different access permissions.
- First-time and administrator-issued temporary passwords must be changed before normal access.

## 7. Account Types and Financial Operations

The application supports two account types.

**Savings Account**
- Requires an initial deposit that meets the configured minimum balance.
- Enforces a minimum balance during withdrawals.
- Has a configurable daily withdrawal limit.
- Calculates interest using a savings account interest method.

**Current Account**
- Supports an overdraft facility.
- Enforces a configured overdraft limit.
- Has a configurable daily withdrawal limit.
- Does not currently calculate interest.

All monetary calculations use Java's `BigDecimal` rather than floating-point types to maintain decimal precision.

The application validates monetary inputs to ensure that amounts are positive where required and do not exceed two decimal places.

## 8. System Architecture and Design Patterns

The application follows a layered architecture to separate responsibilities.

| Package | Responsibility |
|---|---|
| `model` | Represents users, customers, administrators, accounts, and transactions. |
| `service` | Handles authentication, account management, deposits, withdrawals, transfers, and statements. |
| `repository` | Defines interfaces for accessing and storing application data. |
| `repository.json` | Implements JSON-based data persistence using Gson. |
| `repository.memory` | Provides in-memory repository implementations for testing and development. |
| `factory` / `model` | Provides account creation and restoration through `AccountFactory`. |
| `command` | Parses commands and controls command execution. |
| `cli` | Handles terminal input, output, and user interaction. |

The system applies the following software design patterns:

- **Factory Pattern:** Creates savings and current account objects and restores previously saved accounts.
- **Repository Pattern:** Separates business logic from data storage.
- **Strategy Pattern:** Provides interchangeable implementations for interest and withdrawal-fee calculations.
- **Object-Oriented Programming:** Uses inheritance, encapsulation, abstraction, and polymorphism to represent banking entities and their behaviour.

## 9. Data Persistence and File Management

The application uses local JSON files instead of a database server.

The `data/` directory contains:

| File | Purpose |
|---|---|
| `users.json` | Stores customer and administrator records, roles, and authentication information. |
| `accounts.json` | Stores account numbers, owners, balances, types, and account limits. |
| `transactions.json` | Stores transaction histories and references. |

Data is loaded when the application starts and updated after successful operations.

The persistence implementation includes:
- JSON serialization and deserialization using Gson.
- Validation of stored records during startup.
- Decimal monetary values stored as strings.
- ISO-8601 transaction timestamps.
- Temporary files during writes.
- Atomic file replacement where supported.
- Error reporting when stored JSON data is malformed or unreadable.

## 10. Transaction Management and Data Consistency

The application maintains transaction records for deposits, withdrawals, opening deposits, and transfers.

Each transaction contains a unique transaction ID, transaction type, amount, and timestamp.

A separate `TransactionRepository` handles transaction storage instead of placing all transaction persistence responsibilities inside `AccountRepository`.

For transfers:
- The source account is debited.
- The destination account is credited.
- Corresponding debit and credit transactions are created with a shared reference ID.
- Transaction data is staged before account records are committed.
- Account transaction counts act as commit markers to help recover from interrupted writes.

The application also uses an injected Java `Clock` in `TransactionService` to control transaction timestamps and daily withdrawal-limit calculations.

## 11. Existing Data Migration

The updated implementation supports existing customer records and account data.

Older customer records without persistent password hashes are recognised as customer accounts and remain linked to their existing bank accounts.

Because the earlier authentication implementation kept password hashes in memory, those hashes cannot be recovered after the application closes.

An administrator must therefore reset the passwords of existing customers using:

```text
set-password customer@email.com
```

Customers can then log in and change their temporary passwords.

Older transaction records are also supported through legacy-field defaults when loading historical data.

## 12. Current Implementation Scope and Remaining Improvements

The application implements administrator and customer authentication, account management, JSON persistence, financial operations, and transaction statements.

However, some requirements from the original project specification remain potential future improvements:

1. **PIN-based authentication:** The application currently uses email and password authentication instead of the specified four-to-six-digit numeric PIN.
2. **Transaction reversals:** Reversals with links to original transactions have not yet been implemented.
3. **Monthly interest posting:** Automatic monthly interest application remains outstanding.
4. **Withdrawal fees:** The fee strategy classes require further integration into withdrawal operations.
5. **Advanced transaction filtering:** Filtering by transaction type and minimum or maximum amount remains outstanding.
6. **Statement export:** Exporting statements to separate JSON files remains outstanding.
7. **Concurrency:** Further service-level locking and transaction coordination would strengthen protection against simultaneous operations.
8. **Custom exceptions:** The complete hierarchy of application-specific exceptions from the project specification remains outstanding.

## 13. Conclusion

The Console Banking Ledger demonstrates the practical application of Java object-oriented programming, software architecture, financial calculations, authentication, file handling, and data persistence.

It provides separate administrator and customer workflows, supports multiple account types, maintains financial transaction records, and preserves application data between executions.

The project also demonstrates how design patterns and layered architecture can be applied to organise a financial application into maintainable and reusable components.
