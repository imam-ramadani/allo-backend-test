# Allo Bank - Split Bill API

Spring Boot REST API to manage shared expenses and compute settlements for a group (trip, shared apartment, etc.).

Key features:
- Create bill groups with participants
- Add expenses (who paid, amount, split participants)
- Retrieve settlement summary with optimized transactions
- Service charge computed per your GitHub username (see below)

## Prerequisites
- JDK 17+
- Maven 3.6+ (or use the included Maven wrapper `mvnw`)

## Build & Run

Run with Maven wrapper (recommended):

```bash
cd allobank
./mvnw clean package
./mvnw spring-boot:run
```

Or using system Maven:

```bash
cd allobank
mvn clean package
mvn spring-boot:run
```

Docker (multi-stage build): see `allobank/Dockerfile.template` for guidance when building a production image.

## API Endpoints

Base path: `/api/v1/bill-groups` (application context `/api`, full base URL `http://localhost:4110/api/v1/bill-groups` when running with default settings)

- Create a bill group
	- POST `/api/v1/bill-groups`
	- Body example:

```json
{
	"name": "Trip to Bali",
	"participants": [
		{"name": "Alice", "email": "alice@example.com"},
		{"name": "Bob", "email": "bob@example.com"}
	]
}
```

- Get a bill group
	- GET `/api/v1/bill-groups/{id}`

- Add an expense to a group
	- POST `/api/v1/bill-groups/{id}/expenses`
	- Body example (equal split):

```json
{
	"description": "Dinner",
	"amount": "120.00",
	"paidByParticipantId": 1,
	"splitType": "EQUAL",
	"splitBetweenParticipantIds": [1,2]
}

Split strategies
- The API supports multiple split strategies. You can either specify the textual `splitType` (EQUAL, PERCENTAGE, EXACT_AMOUNT) or use the numeric `splitStrategy` param:
	- `1` = Equal split (default): divide the total evenly among `splitBetweenParticipantIds`.
	- `2` = Percentage: provide `splitPercentages` as a map of `participantId` -> percentage (sum ideally 100).
	- `3` = Exact amount: provide `splitAmounts` as a map of `participantId` -> amount (sum ideally equals `amount`).

Examples:
- Equal (numeric):

```json
{ "description": "Taxi", "amount": "50.00", "paidByParticipantId": 1, "splitStrategy": 1, "splitBetweenParticipantIds": [1,2] }
```

- Percentage:

```json
{ "description": "Dinner", "amount": "200.00", "paidByParticipantId": 2, "splitStrategy": 2, "splitBetweenParticipantIds": [1,2,3], "splitPercentages": {"1":40, "2":30, "3":30} }
```

- Exact amounts:

```json
{ "description": "Gift", "amount": "90.00", "paidByParticipantId": 3, "splitStrategy": 3, "splitBetweenParticipantIds": [1,2,3], "splitAmounts": {"1":"30.00","2":"30.00","3":"30.00"} }
```
```

- Get settlement summary
	- GET `/api/v1/bill-groups/{id}/settlement`
	- Response includes `service_charge_pct` and `service_charge_amount`.

- Record a payment (mark debt as paid)
	- POST `/api/v1/bill-groups/{id}/payments`
	- Body example:

```json
{
  "fromParticipantId": 2,
  "toParticipantId": 1,
  "amount": "25.00"
}
```

	- Effect: creates a `Payment` record, sets `isPaid=true` and records `paidAt`. Outstanding balances used by settlement will reflect recorded payments.

- Category summary
	- GET `/api/v1/bill-groups/{id}/categories-summary`
	- Returns a JSON map of category -> total unpaid amount, e.g. `{ "FOOD": 120.00, "TRANSPORT": 30.00 }`

## Example curl commands

Create a bill group

```bash
curl -s -X POST http://localhost:4110/api/v1/bill-groups \
	-H 'Content-Type: application/json' \
	-d '{
		"name":"Trip",
		"participants":[
			{"name":"Alice","email":"a@x.com"},
			{"name":"Bob","email":"b@x.com"}
		]
	}'
```

Add an expense to a bill group

Endpoint: `POST /api/v1/bill-groups/{billGroupId}/expenses`

Headers: `Content-Type: application/json`

Examples (all requests accept either `splitType` text values or numeric `splitStrategy`):

- Equal split (textual):

```json
{
	"description": "Hotel",
	"amount": "300.00",
	"paidByParticipantId": 1,
	"splitType": "EQUAL",
	"splitBetweenParticipantIds": [1,2,3]
}
```

- Equal split (numeric shortcut `splitStrategy` = 1):

```json
{
	"description": "Taxi",
	"amount": "50.00",
	"paidByParticipantId": 1,
	"splitStrategy": 1,
	"splitBetweenParticipantIds": [1,2]
}
```

- Percentage split (`splitStrategy` = 2) — provide `splitPercentages` as `{"participantId":percentage}` (percent values):

```json
{
	"description": "Dinner",
	"amount": "200.00",
	"paidByParticipantId": 2,
	"splitStrategy": 2,
	"splitBetweenParticipantIds": [1,2,3],
	"splitPercentages": {"1":40, "2":30, "3":30}
}
```

- Exact amount split (`splitStrategy` = 3) — provide `splitAmounts` as `{"participantId":amount}`:

```json
{
	"description": "Gift",
	"amount": "90.00",
	"paidByParticipantId": 3,
	"splitStrategy": 3,
	"splitBetweenParticipantIds": [1,2,3],
	"splitAmounts": {"1":"30.00","2":"30.00","3":"30.00"}
}
```

Get settlement summary

Endpoint: `GET /api/v1/bill-groups/{billGroupId}/settlement`

```bash
curl -s http://localhost:4110/api/v1/bill-groups/1/settlement | jq
```

Response fields (JSON):
- `billGroupId`: numeric id of the group
- `billGroupName`: group's name
- `totalExpenses`: total unpaid expenses (BigDecimal)
- `serviceChargePct`: service charge percent applied (e.g., 9 for `imam-ramadani`)
- `serviceChargeAmount`: computed service charge amount
- `participantBalances`: array of `{ name, balance }` where positive balance = participant is owed money
- `transactions`: optimized list of settlement transfers `{ fromParticipant, toParticipant, amount }`

## Personalization (service charge)

- GitHub username: `imam-ramadani`
- `service_charge_pct` is computed from your username (unicode sum % 10). For `imam-ramadani` this equals `9`.

Every settlement response includes:

- `service_charge_pct`: e.g., `9`
- `service_charge_amount`: `total_expenses * service_charge_pct / 100`

## Testing

Run unit tests:

```bash
cd allobank
./mvnw test
```

The project includes unit tests covering settlement calculation logic.

## Design notes (short)

- Data modeling: core entities are `BillGroup`, `Participant`, `Expense`, `Payment`. `BillGroup` owns participants and expenses; expenses reference payer and split targets. Monetary values use `BigDecimal`.
- API design: RESTful endpoints under `/api/v1/bill-groups` with clear resource-oriented routes.
- Monetary handling: `BigDecimal` everywhere, scale/rounding applied when dividing amounts to avoid precision loss.
- Code structure: services contain business logic (`SettlementService`, `BillGroupService`), controllers are thin, DTOs for API boundaries.
- Testing: unit tests focus on settlement correctness, edge cases (paid expenses excluded, multiple split strategies), and service-charge inclusion.

## Submission Question
```
"What was the hardest design decision you made while building this, and what trade-off did you accept?"
```

## Submission Answer

**Hardest decision**: balancing settlement correctness (totalPaid, totalOwed, balance consistency) with flexible API design while keeping the domain model and calculations performant.

**Core challenge**: The system needed to:
1. Track what each participant *paid* (as payer of expenses)
2. Track what each participant *owes* (their share of unpaid expenses)
3. Track their *current balance* (after payments are applied)
4. Ensure *consistency*: if a transaction exists in the settlement, balance must reflect it; if balance = 0, no outstanding transaction should exist

**Trade-offs accepted**:

1. **Split storage strategy**: Store per-expense split amounts as JSON (`Expense.splitDetailsJson`) rather than a separate normalized `expense_split` table.
   - *Why*: keeps model and queries simple, avoids extra joins, maintains expense atomicity
   - *Cost*: reduced relational integrity; I mitigated via server-side validation and clear migration path

2. **Payment integration design**: Represent payments as separate `Payment` records instead of mutating expense records.
   - *Why*: cleaner separation of concerns, audit trail support, flexible payment scheduling
   - *Cost*: requires settlement logic to merge expenses + payments; I handled this by applying payments after calculating balances

3. **API flexibility vs. magic numbers**: Support both enum-style (`"EQUAL"`) and numeric (`1`) splitStrategy formats.
   - *Why*: backward compatibility + self-documenting API
   - *Cost*: added parsing logic; mitigated via `resolveSplitType()` method with clear error messages

4. **Calculation approach**: Compute `totalPaid` and `totalOwed` separately, then derive `balance = totalPaid - totalOwed + paymentAdjustments`.
   - *Why*: transparent financial summary; easier to debug and audit
   - *Cost*: three passes over data; negligible at group scale, but clear optimization path exists (single aggregation query)

**Result**: Settlement is now logically consistent, API is self-documenting, and the system is production-ready with clear extension paths (relational split normalization, advanced reporting, audit history).


