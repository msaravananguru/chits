# Sudar Chits - Project Overview

How the chit project works, and what has been built so far.
Database: `sudardev` (MySQL). Backend: Spring Boot (project folder `chits`). Script: `day0.sql.txt`.

## 1. Purpose

Replace the Excel monthly collection sheet ("SUDAR CHITS") with a system that
generates the sheet each month, records payments, calculates the totals, and
locks the month once the figures are verified.

## 2. Business rules

- Every chit belongs to a **plan** (25K, 50K, 100K, 150K, 200K). Each plan runs **21 months**.
- Before the chit is taken, the member pays the plan's lower monthly amount (1 lakh: 5000).
- After the chit is taken, the member pays the higher monthly amount (1 lakh: 6000).
  Taken in month 3 means 5000 up to month 3 and 6000 from month 4 onward.
- Chit month number is counted from the member's joined date.
  Joined in October = month 1, November = month 2, and so on.
- If a member takes the chit in month N, the amount received is in `chit_plan_receivable` (கிடைக்கும் தொகை).

| Plan | Before taking | After taking |
|---|---|---|
| 50,000 | 2,500 | 3,000 |
| 100,000 | 5,000 | 6,000 |
| 150,000 | 7,500 | 9,000 |
| 200,000 | 10,000 | 12,000 |
| 25,000 | 1,250 (unverified) | 1,500 (unverified) |

## 3. Tables

| Table | One row per | Purpose |
|---|---|---|
| `chit_plans` | plan | Rate card: months, monthly amount before and after taking |
| `chit_plan_receivable` | plan and month | Amount received if taken in that month |
| `chits` | member's chit | Name, Tamil name (`member_name_tamil`, optional), plan, `joined_date`, `taken_month` (NULL = not taken), status |
| `monthly_sheet` | month | Year, month, status DRAFT or LOCKED, `locked_at` |
| `chit_collections` | chit and month | `paid_month`, `due_amount`, `paid_amount` (வரவு), `paid_date`, locked snapshot values |

Relations: `chit_plan_receivable` and `chits` point to `chit_plans`;
`chit_collections` points to `monthly_sheet` and `chits`.
The old `sudar_chits` table is no longer used.

## 4. Monthly flow

```
GENERATE  ->  DRAFT (editable)  ->  verify  ->  LOCK (read-only)
```

1. **Generate:** creates the month's `monthly_sheet` row (DRAFT) and one `chit_collections`
   row for every active chit. `paid_month` comes from `joined_date`;
   `due_amount` comes from the plan (before or after taking).
2. **Draft:** enter வரவு and `paid_date`. Totals are calculated live.
3. **Lock:** validates, saves the calculated values into the rows, sets LOCKED.
   Locked months cannot be edited.

## 5. Calculations (as understood so far)

| Value | How |
|---|---|
| Total to pay (taken chit) | `taken_month x before + (21 - taken_month) x after` |
| Total paid | Sum of `paid_amount` for the chit up to this month |
| Balance | Total to pay - total paid |
| Months pending | Balance / monthly amount after taking |

Check with chit 41 (1 lakh, taken in month 1): total = 5000 + 20 x 6000 = 125000;
paid 6500; balance 118500; 118500 / 6000 = 19.75 months pending.

To confirm later: what the sheet shows for chits that are not taken yet.

## 6. Progress

- [x] Database connection (`application.properties`)
- [x] JPA dependency; `Dbentity` test entity fixed
- [x] Tables created (`day0.sql.txt`, PART 1)
- [x] Plan data loaded (PART 2). The 25K plan is unverified
- [x] Added `member_name_tamil` to `chits` (PART 4)
- [x] Added `users`, `created_by` / `updated_by` columns and `audit_log` (PART 5)
- [x] Backend: add new member API (`POST /api/chits/create`) with created_by and audit
- [x] Backend: sheet and collection entities and repositories
- [ ] Backend: receivable entity (only when needed)
- [x] Backend: chit master APIs (`/api/chits/` create, update, taken, get, list)
- [x] Backend: generate month (`POST /api/sheets/generate`)
- [x] Backend: save payment (`POST /api/sheets/payment`)
- [x] Backend: lock month (`POST /api/sheets/lock`)
- [x] Backend: get monthly sheet with totals (`POST /api/sheets/get`) and month list (`POST /api/sheets/list`)
- [x] UI foundation in `frontend/` (React + Vite, mobile and desktop layouts): sign-in, Members page (list, add, edit, mark taken)
- [x] Frontend: Monthly Sheet page (month picker, enter payment, generate, lock, totals)

Work is done one step at a time, each after approval.

## 7. Users, created_by and audit

- Every API is POST. The caller is identified by the header `X-User-Id` (temporary, until the real login is built).
  Only `CurrentUserService` reads this header, so the login change will touch one class.
- Passwords are stored as plain text for now; hash them after the project is complete.
- `created_by` / `updated_by` are saved on `chits`, `monthly_sheet` and `chit_collections`.
- `audit_log` gets one row per action: who, when, what, old and new values (JSON), endpoint, IP, result.
  Successful actions are saved with the change (rolled back together). Failed attempts are saved as FAILED in their own transaction.
- Reads (viewing the sheet) are not audited.

## 8. Frontend (`frontend/` folder)

- React + Vite. One project: `npm run build` writes the UI into `src/main/resources/static`, so Spring Boot serves both the API and the UI.
- While developing: `npm run dev` (http://localhost:5173); `/api` calls are forwarded to Spring Boot on 8080.
- Phone layout (under 800px): cards, bottom menu, floating "+" button, forms open as bottom sheets.
  Desktop layout: sidebar, table, centered dialogs.
- Temporary sign-in by user ID (sent as `X-User-Id`). Extra APIs added for the UI: `POST /api/plans/list`, `POST /api/auth/me`.
- Each new backend feature gets its page in `frontend/src/pages` and a link in `components/Layout.jsx`.

## 9. Generate month (`POST /api/sheets/generate`)

Body: `{"year": 2026, "month": 10}`.

- Only one DRAFT month can exist; it must be locked before the next month is generated.
- The new month must be exactly the month after the latest sheet (no gaps, no going back). The first sheet can be any month.
- One row is created for every ACTIVE chit that is running in that month:
  chit month = months since `joined_date` + 1 (joined in October = month 1).
  Chits not joined yet, or past the plan's last month, are skipped (count returned as `skipped`).
- Due amount: up to and including `taken_month` (or not taken) = plan's lower amount; after it = higher amount.
- `paid_amount` starts at 0. The audit log records `GENERATE_MONTH`.

## 10. Package structure (by feature)

```
sudar.chits
 |- member   controller / service / repository / entity / dto   (chit master: create, update, taken, get, list)
 |- plan     controller / service / repository / entity / dto   (plan rate card)
 |- sheet    controller / service / repository / entity / dto   (monthly sheet and collections)
 |- user     controller / service / repository / entity / dto   (users, current user, sign-in check)
 |- audit    entity / repository / service                      (audit log)
 `- common   exception                                          (ApiException, error handler)
```

- A new feature gets its own package with the same sub-folders.
- All APIs are POST; every input goes in the JSON body, nothing in the URL path or query.
- Class names were kept (for example `Chit`, `ChitService`) and only moved into their feature package.
- The old `db` test package (`Dbentity` and its controller and repository) was removed.

## 11. Lock rule and remark

- A locked month is final. There is no unlock.
- A mistake found after locking is corrected in the next month (while it is DRAFT), with a remark explaining it.
- `chit_collections.remark` is editable only while the month is DRAFT.

## 12. Not-taken chits in the sheet (decided: option B)

The sheet shows total to pay, balance and months pending for every chit, not only taken ones.
For a chit that is not taken yet the figures assume it will never be taken
(for 1 lakh: 21 x 5,000 = 105,000; months pending = balance / 5,000).
When the chit is marked as taken, the figures switch to the taken calculation.

## 13. Documents in this folder

- `API_DOCUMENT.md` - every API with request, response, errors and a test order. Updated with each new API.
- `day0.sql.txt` - every SQL script run on the database, in order.

## 14. Totals and opening paid

- `chits.opening_paid` = amount a member had paid before the system started (default 0). It is part of "total paid".
- Total paid = opening paid + every வரவு of this and earlier months.
- Total to pay = taken in month t: `t x lower + (months - t) x higher`; not taken: `months x lower`.
- Balance = total to pay - total paid. Months pending = balance / higher amount (taken) or lower amount (not taken).
- A DRAFT month is calculated live; a LOCKED month shows the values saved when it was locked.

## 15. Monthly Sheet page (UI)

- Month picker (from `/api/sheets/list`) with an Open/Locked badge.
- Open month: "Enter payment" per chit (amount, date, remark), "Generate next month" and "Lock month" (asks for confirmation).
- Locked month: read-only, shows the values saved at lock time.
- Footer / total card = இந்த மாத வரவு. Search by name or chit no, and an "Unpaid" filter.
- Desktop: table like the Excel sheet. Phone: one card per chit with the two main buttons above the bottom bar.
- Member form has the "Already paid before this system" (opening paid) field.
