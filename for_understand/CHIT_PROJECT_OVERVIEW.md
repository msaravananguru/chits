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

## 16. Collection table (receipts)

- `chit_payments`: one row per receipt (chit, date, amount, remark). Single payers have one row a month, daily/part payers have many.
- A month's வரவு on the main sheet = sum of that chit's receipts dated in the month. It is calculated live while the month is open and frozen into `chit_collections` when the month is locked.
- A receipt belongs to the month of its date. A locked month rejects any add, change or delete dated in it.
- APIs: `/api/payments/add`, `update`, `delete`, `list` (see API_DOCUMENT section 5A). `/api/sheets/payment` was removed.
- Planned next: Collection screen (UI), editable taken-entry suggestions, import of the 42 chits from the Sep sheet, DLC grid report, month-end statement.

## 17. UI for receipts (step 2)

- **Collection page** (`#/collection`): pick a date, type the amount next to each member and press Add (Enter also works). The "Bal." button fills the balance due this month. Shows the day's total, the month so far, and the day's receipts (with delete). Replaces the DLC sheet.
- **Payments window** (button on the Collection page and on the Monthly Sheet): every receipt of one chit in the month, with add, edit and delete. In a locked month it is read-only.
- **Monthly Sheet**: the old "Enter payment" button is replaced by "Payments (n)". வரவு is calculated from the receipts.
- Delete asks twice ("Sure? Delete"). A date can be from the 1st of the month up to today.

## 18. Members page (month view)

- **Columns:** Chit, Member, Plan, Chit month ("Month 4 of 21" + bar), this month's payment (Paid / Part / Not paid), Total paid, Total to pay, Remaining. No joined date, no actions column.
- **Taken** is shown by colour: amber chit badge + amber left stripe + "Taken · month N" under the name. Not taken = green badge.
- **Month picker** (left of Edit member): default = newest sheet month. A locked month is **Final** (read from the saved master rows, never changes); the open month is **Live** (calculated now from receipts).
- In a past month the taken status and totals are what they were in that month (chit 17 taken in Oct shows "not taken" in Aug/Sep).
- Total to pay and Remaining are shown for every member. When a chit is marked taken they are recalculated.
- **Edit member** button: step 1 search by chit no or name, step 2 the edit form. "Mark taken..." lives inside the edit form. Add and edit work on the member record only, never on a locked month.
- **Add member:** chit number is pre-filled with last chit no + 1 (editable).
- Filters: All / Paid / Part / Not paid (for the selected month) and "Show closed".
- API: `/api/chits/overview` (API_DOCUMENT 4.6).

## 19. Pay style: Single or Daily

- Each member is **Single** (pays the full amount at once, default) or **Daily** (pays part by part, like the DLC sheet). Optional "usual daily amount" (a hint only).
- Set in Add member / Edit member ("How does this member pay?"), changeable any time. SQL: `day0.sql.txt` PART 9 (`chits.pay_mode`, `chits.daily_amount`); run it before starting the new backend. Existing members become Single.
- **Members page:** tag under the name ("Daily · ₹200" / "Single") and a filter All / Daily only / Single only.
- **Collection page:** same tag and filter; a Daily member's usual amount is pre-filled, so one tap on Add records it.
- It is a label for the collectors only. Calculations, receipts and locked months are not affected.

## 20. Collection page: Daily and Single tabs

- **Two tabs** (the last tab used is remembered): **Daily** = members who pay part by part (like the DLC sheet), **Single** = members who pay once a month. The tab labels show the member counts.
- **Month picker** at the top (default = open month). A locked month opens as **Final**: read only, nothing can be typed, Save buttons are hidden. Corrections go in the open month with a remark.
- **Daily (desktop):** grid with days 1 to 31 across, one line per Daily member, and the totals **Total / Month due / Balance**. The highlighted column is the day being entered; the usual daily amount is pre-filled; Enter jumps to the next member. "Review: 4 paid · Rs 700 | 1 with 0" and one **Save all for 25 Oct** button open a confirm pop-up that lists every line; saving writes the whole day at once. The bottom line **Total day Collection** (like the DLC sheet) updates live.
- **0 = nothing paid:** no receipt is kept, but the day is marked saved (`collection_days`), so the grid shows 0 for a saved day and blank for a day not entered yet.
- **Editing a day:** pick the date, or click a day number / any earlier cell. Changed boxes turn yellow; the pop-up lists only the changed lines as "was -> now" (0 = receipt removed). Typed but unsaved amounts ask "Discard changes?" before the date changes. A cell with 2 or more receipts (shown "150 x2") is changed in the Payments window.
- **Daily (phone):** "Enter day" = one line per member with an amount box, date arrows, fixed bottom bar "5 paid · Rs 800" + Save; "Month grid" = read-only grid, tap a day to jump to Enter day.
- **Single (desktop):** table Chit, Member, Due, Received, Last paid, Status, amount box with Bal. and Add, Payments. "Payment date" (default today) is used for every Add. Summary boxes, search and All / Paid / Part / Not paid filter, total line.
- **Single (phone):** one compact line per member (not-paid first), "+ Add" opens a sheet with a big amount box (balance pre-filled), Balance / Full buttons, Change date and Payments.
- SQL: `day0.sql.txt` PART 10. APIs: `/api/collection/daily`, `/save-day`, `/single` (API_DOCUMENT 5B).
- Planned later: Undo after Add, share receipt (WhatsApp text), amount warnings, "behind" marker, Excel/PDF export of the grid, overdue marker on Single.
- **Members added while a month is open** now join that month's sheet automatically. Before this fix they were missing from Collection (and payments for them were refused). Members added earlier: run `day0.sql.txt` PART 11 once.

### Collection page: All tab
Third tab next to Daily and Single. It lists every member of the month (Daily and Single) with Chit, Member, Pay style (Daily with usual amount / Single), Due, Received, Balance, Last paid, Status and a Payments button. View only (no amount box, no Add). Search, status filter (All/Paid/Part/Not paid) and pay-style filter. Previous months are read only. API: `POST /api/collection/all`. Amount boxes no longer show the up/down arrows.

### Previous balance (advance / arrears) and "To pay now"
Extra money paid in a month is an **advance**; a short payment is **arrears**. Both carry forward automatically. Previous balance = (opening paid + receipts before the month) - (dues of the months before it); positive = advance (green "+₹800 adv"), negative = arrears (red "-₹2,000 due"). **To pay now** = this month's due - previous balance - received this month (never below 0), e.g. due 5,000 with 800 advance = 4,200; due 5,000 with 2,000 arrears = 7,000. Shown as two columns on the Daily grid (replaces Balance), Single, All and Members; on the phone as a big "to pay" number. Status uses To pay now ("From advance" when the advance covers the whole due). Nothing is stored, so locked months and imported data are unchanged. No SQL.

