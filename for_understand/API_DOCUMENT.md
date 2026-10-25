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

### 5.2 Save payment - `POST /api/sheets/payment`
Saves the வரவு (amount received), the paid date and a remark for one chit in a DRAFT month.
Body:
```json
{
  "year": 2026,
  "month": 10,
  "chitNo": 41,
  "paidAmount": 5000,
  "paidDate": "2026-10-05",
  "remark": "Paid in cash"
}
```
- `paidAmount` is the total received for this chit in this month (0 = nothing received). Sending it again replaces the old value.
- `paidDate` is required when `paidAmount` is greater than 0. When `paidAmount` is 0 the paid date is cleared.
- `remark` is optional (max 500 characters). Use it to explain a correction of an earlier locked month.

Response **200**:
```json
{
  "year": 2026, "month": 10, "chitNo": 41, "paidMonth": 1,
  "dueAmount": 5000.00, "paidAmount": 5000, "paidDate": "2026-10-05", "remark": "Paid in cash"
}
```

| Test | Expected |
|---|---|
| Month not generated | 404 `Sheet 2026-10 not found` |
| Chit not in that sheet | 404 `Chit 99 is not in the 2026-10 sheet` |
| Month is LOCKED | 409 `Month 2026-10 is LOCKED and cannot be edited. Correct it in the next month with a remark` (testable after the lock API is built) |
| `paidAmount` -1 | 400 `paidAmount cannot be negative` |
| `paidAmount` 5000.555 | 400 `paidAmount can have at most 2 decimal places` |
| `paidAmount` 5000 without `paidDate` | 400 `paidDate is required when paidAmount is greater than 0` |
| `paidAmount` missing | 400 `paidAmount is required` |
| `month` 13 | 400 `year and month (1 to 12) are required` |
| `chitNo` missing | 400 `chitNo is required` |
| `remark` over 500 characters | 400 `remark can have at most 500 characters` |

Audit action: `UPDATE_PAYMENT` (old and new paid amount, date and remark). A rejected attempt is saved as FAILED.

Needs the `remark` column (PART 6 of `day0.sql.txt`). The sheet get API needs the `opening_paid` column (PART 7).

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
| `paidAmount` | வரவு | Entered with `/api/sheets/payment` |
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
7. `/api/sheets/payment` for a few chits (try the error cases too), then check `chit_collections`.
8. `/api/sheets/get` and `/api/sheets/list`; compare the totals with your Excel sheet.
9. `/api/sheets/lock`, then try payment on the locked month (409) and generate the next month.
10. Run the SQL checks in section 6 after each step.

## 8. Reset test data (test database only)

If you want to repeat the sheet test from the start (this deletes the generated sheet rows):
```sql
DELETE FROM chit_collections;
DELETE FROM monthly_sheet;
```
