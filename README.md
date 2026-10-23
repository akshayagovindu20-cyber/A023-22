# Distributed Event Seat Booking System

A beginner-friendly Java 21 college mini project. A Swing desktop client talks to a concurrent TCP server; the server authenticates users and uses MySQL transactions to keep event inventory and booking records consistent.

## Features

- Customer registration, login, event browsing, seat booking, booking history, and cancellation.
- Administrator login, event create/edit/delete, system-wide booking history, and administrator cancellation.
- Unique UUID booking IDs and password hashing with salted PBKDF2-HMAC-SHA-256.
- Line-delimited JSON request/response messages over TCP.
- Concurrent client handlers in a bounded Java thread pool.
- InnoDB row locks, conditional seat-count updates, foreign keys, and database checks. A booking is returned to the client only after its transaction commits.
- Duplicate cancellation is rejected under a booking row lock. A user can cancel only their own booking; administrators can cancel any booking.

## Software requirements

- Windows 10/11 (commands below use PowerShell), JDK 21, and Maven 3.9 or newer.
- MySQL 8.0 or newer with the MySQL command-line client.
- Visual Studio Code with the Java Extension Pack and Maven for Java. The workspace recommends these in `.vscode/extensions.json`.
- TCP port 5050 open locally; for a LAN demo, permit the port only on the private network.

The Java Extension Pack and Maven for Java VS Code extensions are installed/recommended for this workspace. Maven downloads the MySQL JDBC driver, Jackson JSON/date support, and JUnit when the project is first built. No Spring or application framework is used.

## Architecture

```text
┌────────────────────┐       TCP / newline-delimited JSON      ┌─────────────────────────┐
│ Swing client A     │ ──────────────────────────────────────► │                         │
│ Swing client B     │ ──────────────────────────────────────► │ Java server + thread pool│
│ Swing admin client │ ──────────────────────────────────────► │                         │
└────────────────────┘                                          └────────────┬────────────┘
                                                                             │ JDBC
                                                                  ┌──────────▼──────────┐
                                                                  │ MySQL / InnoDB      │
                                                                  │ users, events,      │
                                                                  │ bookings            │
                                                                  └─────────────────────┘
```

- **Client-server architecture:** clients never connect to MySQL. They send one JSON request per line to the server and receive one JSON response per line. The server binds to `SERVER_PORT` (default `5050`).
- **Concurrency:** each accepted socket is handled by a worker from a bounded thread pool. Multiple desktop clients can stay connected at once.
- **Authorization:** successful login creates a random, server-side session token. The client sends the token on later requests. A user ID or role in a client message is ignored; the server derives the principal and role from its session table. Sessions expire after eight hours of inactivity.
- **Transactions and consistency:** booking locks its event row (`SELECT ... FOR UPDATE`), conditionally decrements available seats, inserts the booking, and commits both changes together. Cancellation locks the booking and event, restores the exact seat count, changes status, and commits both changes together. A failure rolls the transaction back. Java synchronization is not used as the database consistency mechanism.
- **Database constraints:** InnoDB foreign keys protect references, unique email and booking IDs prevent duplicates, and `CHECK` constraints prohibit invalid seat counts. Event deletion is refused if any booking history exists; capacity cannot be reduced below active seats.

## Database design

`database/schema.sql` creates the `distributed_event_booking` database and tables, indexes, constraints, and three sample events. All application SQL is parameterized with JDBC prepared statements.

| Table | Purpose |
|---|---|
| `users` | Name, unique email, password hash, and `USER`/`ADMIN` role |
| `events` | Event name, local date/time, venue, total seats, and currently available seats |
| `bookings` | UUID booking ID, user/event foreign keys, number of seats, status, and database booking time |

Booking status is `CONFIRMED` or `CANCELLED`. Historical records are retained. Available seats start equal to total seats, are reduced when a booking commits, and are restored exactly once when cancellation commits.

## Project structure

```text
.
├── .vscode/extensions.json
├── database/schema.sql
├── pom.xml
├── README.md
└── src
    ├── main/java/com/eventbooking
    │   ├── client/       # TCP client and client entry point
    │   ├── dao/          # JDBC data access and transaction boundaries
    │   ├── model/        # User, Event, Booking records
    │   ├── network/      # Request/response envelopes
    │   ├── server/       # Listener, worker handler, server-side sessions
    │   ├── service/      # Authentication, booking, and admin logic
    │   ├── ui/           # Swing login and dashboards
    │   └── util/         # Database connection, hashing, input validation
    └── test/java/com/eventbooking
        ├── dao/           # Opt-in MySQL concurrency integration tests
        └── util/          # Password and validation unit tests
```

## Setup

### 1. Install and check tools

JDK 21 is installed at `%USERPROFILE%\.jdk\jdk-21.0.10` and Maven 3.9.15 at `%USERPROFILE%\.maven\maven-3.9.15`. In each new VS Code PowerShell terminal, add those per-user installations to that terminal's environment:

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdk\jdk-21.0.10"
$env:PATH = "$env:JAVA_HOME\bin;$env:USERPROFILE\.maven\maven-3.9.15\bin;$env:PATH"
java -version
mvn -version
```

The Java version should report 21. These commands only affect the current terminal. VS Code can install the recommended extensions from **Extensions: Show Recommended Extensions**.

### 2. Create the database

Start MySQL Server, then run the schema from the project root in PowerShell (replace `root` with your MySQL account if necessary):

```powershell
$mysql = 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe'
Get-Content .\database\schema.sql | & $mysql -u root -p
```

The MySQL server and command-line client are installed on the current machine; this command uses the client at its default installation path. The `-p` option prompts for the database administrator password; it is not saved in this project. Confirm the database is present:

```sql
SHOW DATABASES LIKE 'distributed_event_booking';
USE distributed_event_booking;
SELECT event_id, event_name, total_seats, available_seats FROM events;
```

### 3. Configure credentials securely

The server reads database settings from the process environment; no password is stored in source control.

```powershell
$env:DB_USER = 'root'
$secureDbPassword = Read-Host 'MySQL password' -AsSecureString
$env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $secureDbPassword).Password
$env:DB_URL = 'jdbc:mysql://localhost:3306/distributed_event_booking?serverTimezone=UTC'
$env:SERVER_PORT = '5050'
```

If your local MySQL account has no password, set `$env:DB_PASSWORD = ''`. Prefer a separate least-privilege MySQL account for a real deployment. Do not put passwords in `pom.xml`, `schema.sql`, source files, shell history, or a committed `.env` file. `DB_USE_SSL=true` enables JDBC SSL; configure certificates and server requirements for non-local deployments.

### 4. Create the administrator account

Choose an administrator email and password using environment variables before starting the server:

```powershell
$env:EVENTBOOKING_ADMIN_EMAIL = 'admin@college.example'
$secure = Read-Host 'New administrator password' -AsSecureString
$env:EVENTBOOKING_ADMIN_PASSWORD = [System.Net.NetworkCredential]::new('', $secure).Password
```

At startup the server hashes the password and inserts an `ADMIN` account if that email is not already present. If both admin variables are omitted, the server starts without creating an administrator; if only one is set, startup fails with a clear configuration error. Clear the values from the current shell when finished:

```powershell
Remove-Item Env:EVENTBOOKING_ADMIN_PASSWORD -ErrorAction SilentlyContinue
Remove-Item Env:EVENTBOOKING_ADMIN_EMAIL -ErrorAction SilentlyContinue
```

### 5. Build and test

From the project root:

```powershell
mvn clean test
```

The password-hashing and input-validation unit tests run without MySQL. The three real database integration tests are skipped by default, not represented as passing, unless explicitly enabled after configuring MySQL:

```powershell
$env:RUN_DB_INTEGRATION_TESTS = 'true'
mvn -Dtest=BookingConcurrencyIntegrationTest test
Remove-Item Env:RUN_DB_INTEGRATION_TESTS
```

Those integration tests create uniquely named users/events and clean up their test data. They cover invalid credentials, IDs, and seat counts in addition to concurrency and cancellation. Run them only against a development/test database, never a production database.

## Run the server and clients

Open two PowerShell terminals in the project directory. Run the server in the first terminal; enter the MySQL username and password when prompted:

```powershell
.\run-server.ps1
```

Then run the client in the second terminal:

```powershell
.\run-client.ps1
```

Run `.\run-client.ps1` from additional terminals to start more clients. The launcher scripts use the JDK and Maven paths described above when those installations are present. The server launcher uses the default local database URL and port `5050`; set `DB_URL` or `SERVER_PORT` in that terminal first to override them.

Alternatively, start the programs directly with Maven:

**Terminal 1 — server:** set `DB_USER`, `DB_PASSWORD`, `DB_URL`, and optionally the two administrator variables as above, then:

```powershell
mvn exec:java
```

Expected startup messages include `Database connection successful.` and `Event booking server listening on port 5050.` The server logs an error and exits if MySQL cannot be reached or the database credentials are invalid.

**Terminal 2 — desktop client:**

```powershell
mvn "-Deventbooking.mainClass=com.eventbooking.client.ClientMain" exec:java
```

Repeat the client command from additional terminals to demonstrate multiple simultaneous users. The default endpoint is `127.0.0.1:5050`; configure another endpoint before launching the client if needed:

```powershell
$env:SERVER_HOST = '127.0.0.1'
$env:SERVER_PORT = '5050'
```

For a client on another computer, set `SERVER_HOST` to the server computer's private LAN IP and allow inbound TCP `SERVER_PORT` in Windows Firewall for the **Private** profile only. This demonstration protocol is not encrypted; do not expose it to the public internet.

Register a customer from the login window. The administrator logs in using the configured `EVENTBOOKING_ADMIN_EMAIL` and the password chosen above. The customer screen provides event browsing, booking, history, and cancellation. The administrator screen provides event management, all bookings, and cancellation.

## Protocol overview

Each UTF-8 TCP line is one JSON object:

```json
{"action":"EVENTS","token":"","payload":{}}
```

The server responds with `success`, `message`, and `data`. Supported actions include `REGISTER`, `LOGIN`, `EVENTS`, `BOOK`, `MY_BOOKINGS`, `CANCEL`, `LOGOUT`, `ADMIN_CREATE_EVENT`, `ADMIN_UPDATE_EVENT`, `ADMIN_DELETE_EVENT`, and `ADMIN_BOOKINGS`. The server validates each payload, session, and authorization; unknown actions and malformed JSON receive error responses. Login returns an opaque session token; subsequent customer and admin requests require that token. `EVENTS` is public so events can be read before login.

## Concurrency demonstration

Use a test event with one seat left and two registered customer clients:

1. From both clients, select that same event and try to book one seat at nearly the same time.
2. The server serializes inventory changes using the MySQL event-row lock and conditional update. Exactly one transaction should commit; the other receives “Not enough seats are available for this booking.”
3. Refresh both event lists. Available seats must be zero and never negative. Only the successful client should see a confirmed booking.
4. Cancel the confirmed booking, refresh, and verify availability returns to one. Try cancelling it a second time; the server rejects the duplicate and the count remains one.

For a repeatable automated version, set `RUN_DB_INTEGRATION_TESTS=true` and run the integration test command above. It tests two clients booking different seats, attempts exceeding the remaining capacity, simultaneous competition for the last seat, cancellation, and duplicate-cancellation protection with a real MySQL database.

### Additional negative-path checks

- **Invalid login:** from a client login screen, enter a registered email with a wrong password. It should show the same generic credential error as an unknown email; no client should enter the dashboard.
- **Invalid event ID / seat count:** these are server-side checked even if a client is modified or bypassed. The opt-in integration test sends a nonexistent positive event ID and a zero seat count through `BookingService` and confirms inventory remains unchanged.
- **Database connection failure:** in a separate terminal, set `DB_URL` to an unused local port (for example `jdbc:mysql://127.0.0.1:1/distributed_event_booking`) and start the server. It must exit with a connection error before listening. Restore the correct URL afterward.
- **Client disconnect:** start two clients, close one client window, and verify the other can still refresh. Stop the server with Ctrl+C and verify the remaining client reports a connection error on its next request.
- **Server/client networking:** check reachability with `Test-NetConnection 127.0.0.1 -Port 5050` while the server is running. The check should succeed only after the listener starts.

## Test procedures and expected behavior

| Scenario | Expected result |
|---|---|
| Two clients book available seats | Both succeed only when total capacity allows; seat count reflects both commits |
| Two clients race for the last seat | Exactly one succeeds; availability stays at zero, never below zero |
| Cancellation | Status changes once and exactly the booked seats are restored |
| Duplicate cancellation | Error; seat count does not change again |
| Wrong password | Generic “Email or password is incorrect” response |
| Unknown event / invalid seats | Clear error; no booking or inventory change |
| Event deletion after booking history exists | Rejected to preserve references and audit history |
| MySQL down / invalid DB credentials | Server reports connection/configuration failure at startup; no success-shaped booking response |
| Client disconnects | Server closes that handler; other client connections continue |
| Server shutdown | Listening and connected sockets close; worker pool is stopped gracefully |

Example user-facing confirmations:

```text
Login successful.
Booking confirmed.
Booking ID: 550e8400-e29b-41d4-a716-446655440000
Seats: 2
Booking cancelled and seats restored.
```

## Limitations and future enhancements

- TCP JSON is plain text and intended for localhost/classroom LAN demonstrations only. Production use needs TLS, rate limits, stronger operational secrets management, monitoring, and deployment hardening.
- The Swing client uses a shared TCP connection and one request at a time per client. The server still processes separate clients concurrently.
- This mini project does not include payment processing, seat maps/seat-level selection, email notifications, database migration tooling, or deployment packaging.
- MySQL integration tests require a separately configured MySQL 8+ server and a dedicated test schema.
- Future work could add TLS, per-seat reservations, waitlists, admin audit logs, role management, database connection pooling, and a web/mobile client.

## Distributed-system concepts for viva

**Client-server architecture:** the server is the single authority for authentication, event inventory, and bookings. Clients send requests over TCP and never access shared database tables directly.

**Concurrency:** many socket handler threads can process different users at once. Work in a single client connection is sequential, and requests from other clients proceed concurrently.

**Synchronization:** Java threads are coordinated by the worker pool, but seat safety is enforced at the database row level. Synchronizing one JVM alone would not protect against multiple server processes and is therefore insufficient.

**Transaction management:** a booking uses a database transaction for inventory decrement plus booking insert. Cancellation uses a transaction for inventory restoration plus status update. A rollback undoes partial work on failure.

**Consistency:** database constraints, row locks, conditional updates, and ownership checks maintain valid inventory and booking history. The server only sends a booking confirmation after commit succeeds.

## Sample viva questions and answers

1. **Why use TCP sockets?** TCP provides a reliable ordered byte stream for request/response communication between separate client processes and the server.
2. **Why is `synchronized` not enough?** It coordinates only threads sharing one JVM. Database row locks and transactions remain correct when clients and server workers run concurrently.
3. **How is double booking prevented?** The server locks the event row, checks and conditionally decrements available seats, inserts the booking, then commits both changes together.
4. **What happens if booking insertion fails after the seat decrement?** The transaction rolls back, so the seat decrement is not committed.
5. **How is a booking ID generated?** The server creates a random UUID for each booking.
6. **How are passwords protected?** A random salt and PBKDF2-HMAC-SHA-256 derive the stored hash; plaintext passwords are never saved in the database.
7. **How does the server authorize an admin operation?** It looks up the opaque login token in the server-side session map and checks the database-derived user's role.
8. **Why retain cancelled booking rows?** They provide booking history and make duplicate cancellation detectable; the event is protected from deletion while any booking record exists.
9. **How does cancellation avoid restoring seats twice?** It locks the booking row and requires `CONFIRMED` status before restoring seats and setting `CANCELLED` in the same transaction.
10. **What is the role of the thread pool?** It bounds concurrent request handlers and avoids creating an unmanaged new thread for every client.
