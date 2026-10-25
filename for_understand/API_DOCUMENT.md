# Sudar Chits - API Document (for testing)

Base URL: `http://localhost:8080`

## 1. How to call every API

- Method is always **POST**.
- All input goes in the **JSON body**. Nothing is sent in the URL path or query.
- Headers (set these once in Postman):

| Header | Value |
|---|---|
| `Content-Type` | `application/json` |
| `X-User-Id` | `1` (admin). Temporary sign-in until the real login is built. |

- Postman tip: create an environment with `baseUrl = http://localhost:8080` and `userId = 1`, then use
  `{{baseUrl}}/api/...` and `X-User-Id: {{userId}}`.
- Every API needs the `X-User-Id` header. Without it you get 401.

### Error format (all APIs)

```json
{
  "timestamp": "2026-10-25T12:10:45.123",
  "status": 409,
  "message": "Chit no 41 already exists"
}
```

| Status | Meaning |
|---|---|
| 400 | Missing or wrong input (the message says which) |
| 401 | `X-User-Id` missing, not a number, or user not found |
| 403 | User is INACTIVE |
| 404 | Chit not found |
| 409 | Conflict (already exists, already taken, month still DRAFT, ...) |

---

## 2. Auth

### 2.1 Who am I - `POST /api/auth/me`
Body: none.

Response 200:
```json
{ "id": 1, "username": "admin", "fullName": "Administrator", "role": "ADMIN" }
```
Errors: 401 `Header X-User-Id is required` / `Header X-User-Id must be a number` / `User not found: 9`; 403 `User is inactive`.

---

## 3. Plans

### 3.1 List plans - `POST /api/plans/list`
Body: none.

Response 200:
```json
[
  { "planAmount": 50000.00,  "totalMonths": 21, "monthlyBeforeTaken": 2500.00,  "monthlyAfterTaken": 3000.00 },
  { "planAmount": 100000.00, "totalMonths": 21, "monthlyBeforeTaken": 5000.00,  "monthlyAfterTaken": 6000.00 }
]
```
(The list also has 150000, 200000 and the unverified 25000 plan.)

---

## 4. Members (chits)

### 4.1 Add member - `POST /api/chits/create`
Body:
```json
{
  "chitNo": 41,
  "memberName": "Saravana Tailor",
  "memberNameTamil": "சரவணா டெய்லர்",
  "planAmount": 100000,
  "joinedDate": "2026-10-01",
  "takenMonth": 1,
  "openingPaid": 1500
}
```
- `memberNameTamil`, `takenMonth` and `openingPaid` are optional (`takenMonth` null = not taken).
- `openingPaid` = amount the member had already paid before the system started (default 0, never negative, max 2 decimals).
  It is added into "total paid" in the monthly sheet.
- `joinedDate` format: `yyyy-MM-dd`. The joined month is chit month 1.

Response **201**:
```json
{
  "chitNo": 41, "memberName": "Saravana Tailor", "memberNameTamil": "சரவணா டெய்லர்",
  "planAmount": 100000.00, "joinedDate": "2026-10-01", "takenMonth": 1,
  "status": "ACTIVE", "openingPaid": 1500.00, "createdBy": 1
}
```

| Test | Expected |
|---|---|
| Same `chitNo` again | 409 `Chit no 41 already exists` |
| `planAmount` 75000 | 400 `Plan not found: 75000` |
| `takenMonth` 25 | 400 `takenMonth must be between 1 and 21` |
| `openingPaid` -5 | 400 `openingPaid cannot be negative` |
| No `memberName` | 400 `memberName is required` |
| No `joinedDate` | 400 `joinedDate is required` |
| `joinedDate` "25-10-2026" | 400 `Invalid request body. Check the JSON and the date format (yyyy-MM-dd).` |
| No `X-User-Id` header | 401 `Header X-User-Id is required` |
| `payMode` "WEEKLY" | 400 `payMode must be SINGLE or DAILY` |
| `payMode` "SINGLE" with `dailyAmount` | 400 `dailyAmount can be set only when payMode is DAILY` |
| `dailyAmount` 0 | 400 `dailyAmount must be greater than 0` |

**Pay style (optional fields):** `"payMode": "DAILY", "dailyAmount": 200`. `payMode` is `SINGLE` (default, pays the full amount at once) or `DAILY` (pays part by part). `dailyAmount` is only a hint pre-filled in Collection and can be left out. The response includes `payMode` and `dailyAmount`.

**Open month:** if a month is open (DRAFT) when the member is added, the member also gets a row in that month right away (same rules as "generate": the chit must be running in that month). Audit action `ADD_CHIT_TO_OPEN_MONTH`. Members added before this change are fixed by `day0.sql.txt` PART 11.

### 4.2 Get one member - `POST /api/chits/get`
```json
{ "chitNo": 41 }
```
Response 200: same shape as 4.1. Errors: 404 `Chit no 99 not found`; 400 `chitNo is required`.

### 4.3 List members - `POST /api/chits/list`
Body is optional.
```json
{}
```
```json
{ "status": "ACTIVE" }
```
`status` can be `ACTIVE` or `CLOSED`. Response 200: array of members, ordered by `chitNo`.
Error: 400 `status must be ACTIVE or CLOSED`.

### 4.4 Update member - `POST /api/chits/update`
```json
{
  "chitNo": 41,
  "memberName": "Saravana Tailor",
  "memberNameTamil": "சரவணா டெய்லர்",
  "joinedDate": "2026-10-01",
  "status": "ACTIVE",
  "openingPaid": 1500
}
```
- A field you leave out stays unchanged. An empty `memberNameTamil` ("") clears it.
- `openingPaid` changes only the open (DRAFT) month's totals. A locked month keeps the values saved when it was locked.
- The plan cannot be changed.

Errors: 404 `Chit no ... not found`; 400 `memberName cannot be blank`; 400 `status must be ACTIVE or CLOSED`.

**Pay style in update:** send `"payMode": "DAILY"` (with or without `dailyAmount`) or `"payMode": "SINGLE"` (clears `dailyAmount`). When `payMode` is sent, `dailyAmount` is taken as sent (null = none). If `payMode` is left out, `dailyAmount` alone changes the amount of a DAILY member (400 for a SINGLE member).

### 4.5 Mark as taken - `POST /api/chits/taken`
```json
{ "chitNo": 42, "takenMonth": 3 }
```
Response 200: the member with `takenMonth` set.

| Test | Expected |
|---|---|
| Same chit again | 409 `Chit 42 is already marked as taken in month 3` |
| `takenMonth` 0 or 22 | 400 `takenMonth must be between 1 and 21` |
| `takenMonth` missing | 400 `takenMonth is required` |
| Chit is CLOSED | 409 `Chit 42 is not ACTIVE` |

---

### 4.6 Members page for one month - `POST /api/chits/overview`
Body is optional. Without `year`/`month` it returns the newest sheet month (or the current calendar month if no sheet exists yet).
```json
{}
```
```json
{ "year": 2026, "month": 9 }
```
Response 200:
```json
{
  "year": 2026, "month": 10,
  "source": "LIVE",
  "sheetStatus": "DRAFT",
  "months": [ { "id": 2, "year": 2026, "month": 10, "status": "DRAFT", "lockedAt": null } ],
  "summary": { "activeCount": 42, "takenCount": 7, "paidCount": 31, "totalPaid": 516850 },
  "rows": [
    {
      "chitNo": 2, "memberName": "...", "memberNameTamil": null, "planAmount": 100000,
      "status": "ACTIVE", "takenMonth": 3, "chitMonth": 5, "totalMonths": 21, "completed": false,
      "dueAmount": 6000, "paidAmount": 3000, "monthStatus": "PART",
      "totalPaid": 23800, "totalToPay": 123000, "remaining": 99200,
      "payMode": "DAILY", "dailyAmount": 200
    }
  ]
}
```
- `source = FINAL` when the month is LOCKED: figures are read from the saved master rows (`chit_collections`), nothing is recalculated. `source = LIVE` when the month is DRAFT (or has no sheet): calculated now with the same formulas as the sheet.
- `takenMonth` is the taken status **as it was in that month**: a chit taken in its month 3 shows `null` when you look at its month 1 or 2.
- `chitMonth` = which month of the chit it is running (joined month = 1). `completed = true` when it is beyond `totalMonths`.
- `monthStatus`: `PAID` (received >= due), `PART`, `NOT_PAID`, `NONE` (nothing due, e.g. completed).
- `totalToPay` and `remaining` are given for every chit (not taken = lower amount for all months).
- Rows: chits of the sheet, plus CLOSED chits, plus (live month only) chits added after the sheet was generated. A locked month never gets new members.
- `summary` counts ACTIVE chits only.
- `payMode` / `dailyAmount` are the member's current pay style. The same two fields are also in every row of `/api/sheets/get`.
- Errors: 400 `year (2000 to 2100) and month (1 to 12) are required together`; 404 `Sheet 2026-05 not found` (when sheets exist but not for that month).

## 5. Monthly sheet

### 5.1 Generate month - `POST /api/sheets/generate`
```json
{ "year": 2026, "month": 10 }
```
Response **201**:
```json
{ "id": 1, "year": 2026, "month": 10, "status": "DRAFT", "rowsCreated": 7, "skipped": 0 }
```

What it does:
- Creates the month as DRAFT with one row for every ACTIVE chit running in that month.
- Chit month number = months since `joinedDate` + 1 (joined October = month 1, November = month 2).
- Due amount: up to and including the taken month (or not taken) = `monthlyBeforeTaken`; after it = `monthlyAfterTaken`.
  Example: 1 lakh chit taken in month 1 pays 5000 in month 1, then 6000 from month 2.
- `skipped` = ACTIVE chits left out (not joined yet, or past the plan's last month).

| Test | Expected |
|---|---|
| Generate again (month still DRAFT) | 409 `A month is still in DRAFT. Lock it before generating the next month` |
| Month after the latest one, when the latest is LOCKED | 201 (testable after the lock API is built) |
| A month that is not the next one | 400 `The next month to generate is 2026-11` |
| `month` 13 | 400 `month is required (1 to 12)` |
| `year` missing | 400 `year is required (2000 to 2100)` |
| No chit runs in that month | 400 `No chits are running in 2026-10` |

### 5.2 Save payment - REMOVED
`POST /api/sheets/payment` no longer exists. Every receipt now goes into the collection table: see section 5A.

### 5.3 List months - `POST /api/sheets/list`
Body: none. Newest month first (for the month picker).

Response 200:
```json
[
  { "id": 2, "year": 2026, "month": 11, "status": "DRAFT",  "lockedAt": null },
  { "id": 1, "year": 2026, "month": 10, "status": "LOCKED", "lockedAt": "2026-11-05T10:15:00" }
]
```

### 5.4 Get sheet - `POST /api/sheets/get`
Body:
```json
{ "year": 2026, "month": 10 }
```
Response 200:
```json
{
  "id": 1, "year": 2026, "month": 10, "status": "DRAFT", "lockedAt": null,
  "rows": [
    {
      "chitNo": 41, "memberName": "Saravana Tailor", "memberNameTamil": "சரவணா டெய்லர்",
      "planAmount": 100000.00, "takenMonth": 1, "paidMonth": 1,
      "dueAmount": 5000.00, "paidAmount": 5000.00, "paidDate": "2026-10-05", "remark": "Paid in cash",
      "paymentCount": 1,
      "totalPaid": 6500.00, "totalDueAmount": 125000.00, "monthsPending": 19.75, "remainingAmount": 118500.00
    }
  ],
  "totals": {
    "rows": 1, "dueAmount": 5000.00, "paidAmount": 5000.00,
    "totalPaid": 6500.00, "totalDueAmount": 125000.00, "remainingAmount": 118500.00
  }
}
```

Row fields and the Excel column they match:

| Field | Excel column | How it is calculated |
|---|---|---|
| `paidMonth` | சீட்டு கட்டும் மாதம் | Chit month number (joined month = 1) |
| `dueAmount` | கட்ட வேண்டிய தொகை | Saved when the month was generated |
| `paidAmount` | வரவு | Sum of this chit's receipts dated in the month (section 5A). `paidDate` = latest receipt date, `remark` = receipt remarks joined with `;`, `paymentCount` = number of receipts |
| `totalPaid` | மொத்தம் செலுத்திய தொகை | `openingPaid` + all `paidAmount` of this and earlier months |
| `totalDueAmount` | மொத்தம் செலுத்த வேண்டிய தொகை | Taken in month t: `t x before + (months - t) x after`. Not taken: `months x before` (assumes it is never taken) |
| `remainingAmount` | BALANCE AMOUNT | `totalDueAmount - totalPaid` |
| `monthsPending` | Months Pending | `remainingAmount / after` when taken, `/ before` when not taken (2 decimals) |

- `totals` is the footer row (இந்த மாத வரவு): the sum of every column above.
- A DRAFT month is calculated live on every call. A LOCKED month returns the values saved at lock time.

Example check (chit 41, 1 lakh plan, taken in month 1, openingPaid 1500, paid 5000 this month):
total due = 5000 + 20 x 6000 = 125000, total paid = 6500, balance = 118500, months pending = 118500 / 6000 = 19.75.
A not-taken 1 lakh chit that has paid 10000: total due = 21 x 5000 = 105000, balance 95000, months pending 19.00.

| Test | Expected |
|---|---|
| Month not generated | 404 `Sheet 2026-10 not found` |
| `month` 13 or missing | 400 `year and month (1 to 12) are required` |
| No `X-User-Id` header | 401 |

Reading APIs are not audited.

### 5.5 Lock month - `POST /api/sheets/lock`
Body:
```json
{ "year": 2026, "month": 10 }
```
Response 200: the same shape as `/api/sheets/get`, with `"status": "LOCKED"` and `lockedAt` filled.

What it does:
1. Calculates every row (same formulas as `/api/sheets/get`).
2. Saves `total_paid`, `total_due_amount`, `months_pending`, `remaining_amount` into each row.
3. Sets the month to LOCKED with `locked_at` and `locked_by`.
4. Writes an audit entry `LOCK_MONTH`.

A locked month is final. There is no unlock. Mistakes are corrected in the next month with a remark.

| Test | Expected |
|---|---|
| Lock a DRAFT month | 200, status LOCKED |
| Lock the same month again | 409 `Month 2026-10 is already LOCKED` |
| `/api/sheets/payment` on the locked month | 409 `Month 2026-10 is LOCKED and cannot be edited...` |
| Month not generated | 404 `Sheet 2026-10 not found` |
| `/api/sheets/generate` for the next month after locking | 201 |
| `/api/sheets/get` after changing a member's `openingPaid` | Locked month keeps its old totals |

---

## 5A. Receipts (collection table) - `/api/payments/...`

Every payment is its own row: a member pays once, or pays part by part (daily). The month sheet's வரவு is the sum of the receipts dated in that month, so nobody types வரவு on the main sheet.

Rules:
- A receipt belongs to the **month of its date**.
- A receipt dated in a **LOCKED** month can never be added, changed or deleted (final). Enter it with a date in the open month and add a remark.
- A date in the **next month** (after the latest sheet) is accepted even before that month is generated.
- A date before the first sheet is rejected (older payments go into the member's `openingPaid`). A date in the future is rejected.
- Amount must be greater than 0, at most 2 decimals.
- Deleting a receipt removes the row; the old values stay in the audit log.

### 5A.1 Add - `POST /api/payments/add`  (201)
```json
{ "chitNo": 41, "paidDate": "2026-10-05", "amount": 3000, "remark": "Cash, part 1" }
```
Response:
```json
{ "id": 1, "chitNo": 41, "memberName": "Saravana Dealer", "memberNameTamil": "சரவணா டெய்லர்",
  "paidDate": "2026-10-05", "amount": 3000.00, "remark": "Cash, part 1", "createdBy": 1 }
```

### 5A.2 Update - `POST /api/payments/update`  (200)
```json
{ "id": 1, "amount": 3500, "paidDate": "2026-10-06", "remark": "Corrected" }
```
Fields left out stay unchanged. `remark` "" clears it. Returns the same shape as add.

### 5A.3 Delete - `POST /api/payments/delete`  (204, no body)
```json
{ "id": 1 }
```

### 5A.4 List - `POST /api/payments/list`  (200)
```json
{ "year": 2026, "month": 10 }
```
Optional filters: `"chitNo": 41` (one member), `"date": "2026-10-05"` (one day).
```json
{ "year": 2026, "month": 10, "status": "DRAFT", "count": 2, "total": 5000.00,
  "payments": [ { "id": 1, "chitNo": 41, "memberName": "...", "memberNameTamil": "...",
                  "paidDate": "2026-10-05", "amount": 3000.00, "remark": "Cash, part 1", "createdBy": 1 } ] }
```
`status` is DRAFT, LOCKED or NO_SHEET (month not generated yet). Oldest first. Reads are not audited.

| Test | Expected |
|---|---|
| Add 3000 for chit 41, date in the open month | 201 |
| Add 2000 more the same day or later | 201; `/api/sheets/get` shows paidAmount 5000, paymentCount 2 |
| Update id 1 to amount 3500 | 200; sheet shows 5500 |
| Delete id 1 | 204; sheet shows 2000 |
| Date in a LOCKED month | 409 `Month 2026-10 is LOCKED. Payments dated in it cannot be added, changed or deleted...` |
| Date in the future | 400 `paidDate cannot be in the future` |
| Date before the first sheet | 400 `There is no sheet for 2026-08 (it is before the first month)...` |
| Chit not in the open sheet | 404 `Chit 99 is not in the 2026-10 sheet` |
| Unknown chit | 404 `Chit 99 not found` |
| `amount` 0 or negative | 400 `amount must be greater than 0` |
| `amount` 100.555 | 400 `amount can have at most 2 decimal places` |
| Missing `paidDate` / `amount` / `chitNo` | 400 `paidDate is required` etc. |
| Update or delete an unknown id | 404 `Payment 99 not found` |
| No `X-User-Id` header | 401 |

Audit actions: `ADD_PAYMENT`, `UPDATE_PAYMENT_ENTRY`, `DELETE_PAYMENT` (old and new values). A rejected attempt is saved as FAILED.

Needs the `chit_payments` table (PART 8 of `day0.sql.txt`).

---

## 5B. Collection page - `/api/collection/...`

The Collection page has two tabs: **Daily** (members with `payMode` DAILY, shown as a DLC-style grid) and **Single** (members with `payMode` SINGLE). Money is still stored as receipts in `chit_payments`; these APIs read them grouped for the page and save a whole day at once.

**SQL first:** run `day0.sql.txt` PART 10 (table `collection_days`) before starting the new backend.

Both read APIs take an optional body `{ "year": 2026, "month": 10 }`. No body = the newest sheet (the open month). 404 if that month has no sheet.
`source` is `FINAL` for a locked month (read only, `editable` false) and `LIVE` for the open month.

### 5B.1 Daily grid - `POST /api/collection/daily`  (200)

Response:
```json
{
  "year": 2026, "month": 10, "source": "LIVE", "sheetStatus": "DRAFT", "editable": true,
  "today": "2026-10-25", "daysInMonth": 31, "dailyCount": 5, "singleCount": 37,
  "months": [ { "id": 2, "year": 2026, "month": 10, "status": "DRAFT", "lockedAt": null } ],
  "savedDays": [22, 23, 24],
  "rows": [
    { "chitNo": 2, "memberName": "...", "memberNameTamil": "...", "takenMonth": 3, "dailyAmount": 200,
      "dueAmount": 6000, "paidAmount": 4200, "balance": 1800,
      "days": [200, 0, 200, "... 31 numbers ..."],
      "counts": [1, 0, 1, "... receipts per day ..."] }
  ],
  "dayTotals": [400, 800, "... 'Total day Collection' for each day ..."],
  "monthTotal": 12050
}
```
- `days[i]` = total received by that member on day i+1 (0 when nothing). `counts[i]` = number of receipts that day; more than 1 means the grid does not edit that cell (use Payments).
- `savedDays` = days saved on the Daily page. A saved day shows 0 for members who paid nothing; a day not in the list stays blank (not entered yet).
- Only DAILY members are in `rows`. A locked month shows `dueAmount`/`paidAmount` saved when it was locked.

### 5B.2 Save a whole day - `POST /api/collection/save-day`  (200)

Request:
```json
{ "date": "2026-10-25", "remark": "optional", "entries": [ { "chitNo": 2, "amount": 200 }, { "chitNo": 40, "amount": 0 } ] }
```
Every entry becomes that member's **total for the day**:
- no receipt + amount > 0 -> a receipt is added;
- one receipt + a different amount -> that receipt is changed (amount > 0) or deleted (amount = 0);
- same total as now -> unchanged;
- amount 0 and no receipt -> nothing is kept (the day is still marked saved).
All entries are saved in one transaction (all or none). The day is then recorded in `collection_days`.

Response: `{ "date": "2026-10-25", "added": 3, "updated": 1, "removed": 1, "unchanged": 0, "dayTotal": 650 }`

Errors: 400 date missing / in the future / entries missing / negative or 3-decimal amount / chit listed twice; 404 chit not found; 409 month is LOCKED (nothing can be added, changed or deleted), chit is CLOSED, or the member has 2 or more receipts on that day with a different total (change them one by one in Payments).
Audit log: one `ADD_PAYMENT` / `UPDATE_PAYMENT_ENTRY` / `DELETE_PAYMENT` row per changed receipt (old and new values) and one `SAVE_COLLECTION_DAY` row for the day. A failed save is logged as FAILED.

### 5B.3 Single list - `POST /api/collection/single`  (200)

Response: same header fields as 5B.1 (`year, month, source, sheetStatus, editable, today, dailyCount, singleCount, months`) plus:
```json
{ "summary": { "count": 37, "paidCount": 21, "collected": 141500, "toCollect": 58000 },
  "rows": [ { "chitNo": 3, "memberName": "...", "memberNameTamil": "...", "takenMonth": null,
              "dueAmount": 5000, "paidAmount": 2500, "balance": 2500,
              "lastPaidDate": "2026-10-18", "paymentCount": 1, "status": "PART" } ] }
```
`status` is PAID / PART / NOT_PAID / NONE (nothing due). `balance` never goes below 0. A payment from this tab uses `/api/payments/add` (5A.1).

---

### 5B.4 All members - `POST /api/collection/all`  (200)
View-only list of every member of the month (Daily and Single together) for the **All** tab. Body is optional: `{ "year": 2026, "month": 10 }` (empty = the open month). Same month rules as 5B.3 (404 if that sheet does not exist; a locked month is returned as FINAL / `editable=false`).

Response: `year, month, source (LIVE/FINAL/NONE), sheetStatus, editable, today, dailyCount, singleCount, months[]`, `summary { count, paidCount, collected, toCollect, dailyCollected, singleCollected }`, and `rows[]` sorted by chit no, each: `chitNo, memberName, memberNameTamil, takenMonth, payMode (DAILY/SINGLE), dailyAmount (Daily members only), dueAmount, paidAmount, balance (never negative), lastPaidDate, paymentCount, status (PAID/PART/NOT_PAID/NONE)`.
No new SQL. Adding or changing money is still done from the Daily / Single tabs or the Payments pop-up.

### 5B.5 Previous balance and "to pay now" (added to 5B.1, 5B.3, 5B.4 and the Members overview)
Every row of `/api/collection/daily`, `/single`, `/all` and `/api/members/overview` now has two more fields:
- `previousBalance` = everything the member paid before the 1st of the month (`opening_paid` + receipts dated before it) minus everything that was due in the months before it. **Positive = advance (paid ahead), negative = arrears (left unpaid earlier)**. A month's due is the amount saved in that month's sheet row; a month with no sheet row uses the plan rule. Nothing is stored; it is worked out from receipts and due amounts, so locked months never change.
- `toPay` = `dueAmount - previousBalance - paidAmount`, never below 0. This is what the collector asks for now.
- `status` (single/all rows) and `monthStatus` (members) are judged on `toPay`: PAID when `toPay` is 0 (also when an advance covers the due; the UI shows "From advance" when nothing was received), PART when something was received, else NOT_PAID.
- `/single` summary `toCollect` and `/all` summary `toCollect` = sum of `toPay`. `/all` summary also has `dueTotal`, `arrears` and `advance`.
No new SQL.

## 6. Check the results in MySQL

```sql
USE sudardev;

SELECT * FROM chits ORDER BY chit_no;                 -- created_by should be 1
SELECT * FROM monthly_sheet;                          -- status DRAFT
SELECT chit_no, paid_month, due_amount, paid_amount   -- one row per chit
FROM chit_collections ORDER BY chit_no;

-- audit trail: one row per action, with old and new values
SELECT id, event_time, username, action, entity_id, result, error_message, old_values, new_values
FROM audit_log ORDER BY id DESC;
```

Audit actions written so far: `CREATE_CHIT`, `UPDATE_CHIT`, `MARK_TAKEN`, `GENERATE_MONTH`, `UPDATE_PAYMENT`.
A rejected attempt is saved with `result = FAILED` and the reason in `error_message`.
Reading APIs (get, list) are not audited.

## 7. Suggested test order

1. `/api/auth/me` with `X-User-Id: 1`, then with `9` (expect 401).
2. `/api/plans/list`.
3. `/api/chits/create` for one or two members (try the error cases too).
4. `/api/chits/list`, `/api/chits/get`.
5. `/api/chits/update`, then `/api/chits/taken` (twice, to see the 409).
6. `/api/sheets/generate` for the month, then again (expect 409).
7. `/api/payments/add` for a few chits (single and part payments; try the error cases too), then `/api/payments/list`.
8. `/api/sheets/get` and `/api/sheets/list`; compare the totals with your Excel sheet.
9. `/api/sheets/lock`, then try payment on the locked month (409) and generate the next month.
10. Run the SQL checks in section 6 after each step.

## 8. Reset test data (test database only)

If you want to repeat the sheet test from the start (this deletes the generated sheet rows):
```sql
DELETE FROM chit_collections;
DELETE FROM monthly_sheet;
```
