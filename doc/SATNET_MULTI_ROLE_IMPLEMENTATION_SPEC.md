# SATNET Multi-Role Implementation Spec

## Status
Draft implementation specification for the existing SATNET role, voucher, merchant, and verifier codepaths.

## Scope
This spec turns the current multi-role concept into an implementation plan tied to:

- `app/src/main/java/org/servalproject/satnet/SatnetRoleManager.java`
- `app/src/main/java/org/servalproject/satnet/SatnetRoleConflictPolicy.java`
- `app/src/main/java/org/servalproject/voucher/VoucherLedger.java`
- `app/src/main/java/org/servalproject/voucher/VoucherParticipantSnapshot.java`
- `app/src/main/java/org/servalproject/satnet/ui/SatnetRoleSetupActivity.java`
- `app/src/main/java/org/servalproject/satnet/ui/BitcoinWalletActivity.java`
- `app/src/main/java/org/servalproject/satnet/ui/AgentVoucherActivity.java`
- `app/src/main/java/org/servalproject/satnet/ui/VoucherRedemptionActivity.java`
- `app/src/main/java/org/servalproject/satnet/ui/MerchantLightningActivity.java`
- `app/src/main/java/org/servalproject/satnet/ui/VerifierDashboardActivity.java`

It preserves the current role mask model while upgrading enforcement from `activeRole`-only behavior to `capability + context + conflict policy` behavior.

---

## 1. Goals

### Functional goals
- Support one participant holding multiple roles at once, including:
  - `user + agent`
  - `user + merchant`
  - `agent + merchant`
  - `user + verifier`
  - tightly controlled `merchant + verifier` or `agent + verifier`
- Keep one device-local wallet custody model.
- Preserve current SATNET offline-first and Rhizome-audited behavior.
- Prevent self-dealing across role combinations.

### Non-functional goals
- censorship resistance
- decentralisation and distributed auditability
- scam resistance
- privacy and minimal linkability
- high-integrity verifier flows
- scalability under intermittent connectivity
- robust staged rollout

### Non-goals for this phase
- server-mandated centralized role approval
- replacing the current role bitmask with an account system
- introducing cloud-only dispute handling

---

## 2. Current Baseline

### Already present in the project
- `SatnetRoleManager` stores:
  - registered role bitmask
  - active role
  - participant subject id
  - role-specific display data for agent and merchant
- `SatnetRoleConflictPolicy` blocks verifier/dispute actions when the same subject is already a voucher participant.
- `VoucherLedger` stores:
  - agent subject id
  - redeemer subject id
  - settlement verifier subject id
  - dispute resolver subject id
  - verifier audit history
- `BitcoinWalletActivity` routes the primary action based largely on `activeRole`.
- `SatnetRoleSetupActivity` already supports registering multiple roles over time.

### Gap to close
The codebase supports multi-role registration, but still treats role execution mostly as a single active-role switch. This spec upgrades the system so sensitive actions are checked against:
1. role capability
2. role status
3. runtime readiness
4. conflict-of-interest rules
5. risk and velocity limits

---

## 3. Target Architecture

### 3.1 Identity model
Use a two-layer identity model:

1. **participant root id**
   - local-only stable id
   - stored only on-device
   - used for internal conflict checks and local risk correlation

2. **role subject id**
   - one pseudonymous subject per role
   - used on vouchers, redemption records, verifier actions, and merchant flows
   - reduces public cross-role linkability

### 3.2 Enforcement model
- `activeRole` remains a UX convenience.
- Actual authorization comes from `RoleCapability` checks plus contextual policy checks.
- Every sensitive action returns an `AuthorizationResult` with:
  - allowed/denied
  - reason code
  - user-facing message
  - optional mitigation instruction

### 3.3 Data principles
- wallet keys remain device-local
- sensitive data stays encrypted or minimized
- ledger remains locally authoritative for the device
- Rhizome sync continues to carry signed audit data, not a central truth source

---

## 4. Data Model Changes

## 4.1 `SatnetRoleManager` preference model changes
Keep `SharedPreferences` for role profile state in this phase.

### Existing keys
- `active_role`
- `registered_roles`
- `participant_subject_id`
- `role_data_*`

### New keys
- `participant_root_subject_id`
- `role_profile_version`
- `role_subject_id_<role>`
- `role_status_<role>`
- `role_risk_tier_<role>`
- `role_reputation_score_<role>`
- `role_registered_at_<role>`
- `role_last_reviewed_at_<role>`
- `role_region_scope_<role>`
- `role_daily_limit_sats_<role>`
- `role_monthly_limit_sats_<role>`
- `role_requires_step_up_<role>`
- `role_display_name_<role>`
- `role_descriptor_<role>`
- `role_suspension_reason_<role>`

### New logical object: `RoleProfile`
Add a nested static value class or standalone class with fields:
- `int role`
- `String roleSubjectId`
- `String participantRootSubjectId`
- `String displayName`
- `String descriptor`
- `String regionScope`
- `long registeredAt`
- `long lastReviewedAt`
- `int status`
- `int riskTier`
- `int reputationScore`
- `long dailyLimitSats`
- `long monthlyLimitSats`
- `boolean requiresStepUpForHighValue`

### Status constants
- `ROLE_STATUS_ACTIVE = 1`
- `ROLE_STATUS_LIMITED = 2`
- `ROLE_STATUS_SUSPENDED = 3`
- `ROLE_STATUS_REVIEW_REQUIRED = 4`

### Risk tier constants
- `RISK_TIER_LOW = 1`
- `RISK_TIER_MEDIUM = 2`
- `RISK_TIER_HIGH = 3`
- `RISK_TIER_RESTRICTED = 4`

---

## 4.2 `VoucherLedger` schema changes
Bump `DB_VERSION` from `9` to `10`.

### 4.2.1 Add columns to `vouchers`
Add the following columns via `ensureVoucherSchema()`:
- `agent_participant_root_id TEXT`
- `redeemer_participant_root_id TEXT`
- `merchant_subject_id TEXT`
- `merchant_participant_root_id TEXT`
- `settlement_verifier_participant_root_id TEXT`
- `dispute_resolver_participant_root_id TEXT`
- `risk_state INTEGER NOT NULL DEFAULT 0`
- `risk_score INTEGER NOT NULL DEFAULT 0`
- `risk_flags_json TEXT`
- `dispute_status INTEGER NOT NULL DEFAULT 0`
- `required_verifier_quorum INTEGER NOT NULL DEFAULT 1`
- `achieved_verifier_quorum INTEGER NOT NULL DEFAULT 0`
- `last_policy_decision_code TEXT`
- `last_policy_decision_at INTEGER`

### 4.2.2 New table: `voucher_disputes`
Create a dedicated dispute timeline table:

Columns:
- `dispute_id TEXT PRIMARY KEY`
- `voucher_id TEXT NOT NULL`
- `opened_by_subject_id TEXT`
- `opened_by_role INTEGER NOT NULL`
- `opened_by_root_id TEXT`
- `opened_at INTEGER NOT NULL`
- `reason_code TEXT NOT NULL`
- `status INTEGER NOT NULL`
- `assigned_resolver_subject_id TEXT`
- `assigned_resolver_root_id TEXT`
- `resolution_code TEXT`
- `resolution_message TEXT`
- `resolved_at INTEGER`
- `evidence_bundle_ref TEXT`
- `sync_state INTEGER NOT NULL DEFAULT 0`

Indexes:
- `(voucher_id)`
- `(status, opened_at)`

### 4.2.3 New table: `voucher_risk_events`
Create append-only risk and policy events:

Columns:
- `event_id TEXT PRIMARY KEY`
- `voucher_id TEXT`
- `subject_id TEXT`
- `participant_root_id TEXT`
- `actor_role INTEGER NOT NULL`
- `event_type TEXT NOT NULL`
- `risk_score_delta INTEGER NOT NULL DEFAULT 0`
- `rule_code TEXT NOT NULL`
- `event_message TEXT`
- `created_at INTEGER NOT NULL`
- `origin TEXT NOT NULL DEFAULT 'local'`
- `source_node TEXT`
- `exported_to_mesh INTEGER NOT NULL DEFAULT 0`
- `exported_at INTEGER`

Indexes:
- `(voucher_id, created_at)`
- `(participant_root_id, created_at)`
- `(event_type, created_at)`

### 4.2.4 Update `VoucherParticipantSnapshot`
Expand the snapshot object to include:
- `String agentParticipantRootId`
- `String merchantSubjectId`
- `String merchantParticipantRootId`
- `String redeemerParticipantRootId`
- `String settlementVerifierParticipantRootId`
- `String disputeResolverParticipantRootId`
- `int riskState`
- `int disputeStatus`
- `String riskFlagsJson`

### 4.2.5 Risk state constants in `VoucherLedger`
- `RISK_STATE_NONE = 0`
- `RISK_STATE_MONITOR = 1`
- `RISK_STATE_HOLD = 2`
- `RISK_STATE_BLOCK = 3`

### 4.2.6 Dispute status constants in `VoucherLedger`
- `DISPUTE_STATUS_NONE = 0`
- `DISPUTE_STATUS_OPEN = 1`
- `DISPUTE_STATUS_UNDER_REVIEW = 2`
- `DISPUTE_STATUS_RESOLVED = 3`
- `DISPUTE_STATUS_ESCALATED = 4`

---

## 5. Permission Matrix

## 5.1 Role-to-capability model
Introduce a new enum or integer constant set in `SatnetRoleManager`:

- `CAP_WALLET_VIEW`
- `CAP_WALLET_BACKUP`
- `CAP_VOUCHER_REDEEM`
- `CAP_VOUCHER_ISSUE`
- `CAP_MERCHANT_ACCEPT_LIGHTNING`
- `CAP_MERCHANT_SETTLEMENT_VIEW`
- `CAP_VERIFIER_INSPECT`
- `CAP_VERIFIER_APPROVE_SETTLEMENT`
- `CAP_VERIFIER_RESOLVE_DISPUTE`
- `CAP_ROLE_MANAGE`
- `CAP_RISK_REVIEW_LOCAL`

## 5.2 Permission matrix

| Capability / action | User | Agent | Merchant | Verifier | Extra gates |
|---|---:|---:|---:|---:|---|
| View wallet | Y | Y | Y | Y | wallet unlocked |
| Backup wallet | Y | Y | Y | Y | wallet unlocked + secure window |
| Redeem voucher | Y | Y | Y | Y | runtime ready + no hold/block |
| Issue voucher | N | Y | N | N | role active/limited + issuance limits + step-up for high value |
| Accept Lightning payment | N | N | Y | N | Lightning feature enabled + merchant status active/limited |
| View merchant settlement status | N | N | Y | Y | merchant or verifier capability |
| Inspect voucher metadata | N | N | N | Y | verifier tools ready + conflict check |
| Approve SELL settlement | N | N | N | Y | quorum rules + conflict check + no hold/block |
| Resolve dispute | N | N | N | Y | resolver capability + stricter conflict check |
| Manage roles | Y | Y | Y | Y | local device only |
| Override local risk hold | N | N | N | N | not allowed in client-only phase |

## 5.3 Runtime dependencies by flow

| Flow | App/runtime dependencies |
|---|---|
| voucher QR scan / redemption scan | camera permission if scanning; manual paste remains available |
| SATNET maps / discovery | location permission only if maps enabled and user explicitly enters map flow |
| merchant payments | Lightning feature flag + runtime ready |
| verifier dashboard | verifier stage gate + startup ready |
| Rhizome audit export | mesh/Rhizome availability, but local audit must still persist even when sync is unavailable |

## 5.4 Denial reasons returned to UI
Standardize denial reason codes:
- `ROLE_NOT_REGISTERED`
- `ROLE_SUSPENDED`
- `ROLE_REVIEW_REQUIRED`
- `CAPABILITY_NOT_GRANTED`
- `STARTUP_NOT_READY`
- `VERIFIER_NOT_READY`
- `CONFLICT_SELF_PARTICIPATION`
- `CONFLICT_PREVIOUS_REVIEWER`
- `RISK_HOLD`
- `RISK_BLOCK`
- `LIMIT_DAILY_EXCEEDED`
- `LIMIT_MONTHLY_EXCEEDED`
- `STEP_UP_REQUIRED`
- `FEATURE_DISABLED`

---

## 6. UX Wireflow

## 6.1 Role onboarding / management
### Screen: `SatnetRoleSetupActivity`
1. Show current registered roles and per-role status badges.
2. Present role cards instead of a single “primary role” framing.
3. User can:
   - activate an existing role
   - add another role
   - inspect status/limits/reputation for a role
4. If user chooses a new role:
   - collect role-specific metadata
   - generate per-role subject id
   - save default limits/status
   - set role active for UX only
5. If user chooses an existing role:
   - switch active role only
   - do not re-register or overwrite role profile metadata

### Required UI changes
- Replace “Select your primary role” copy with “Choose a SATNET role to use now or add another role”.
- Show chips or rows for:
  - role name
  - status
  - risk tier
  - daily limit summary
- Add read-only explanation block for blocked/suspended roles.

## 6.2 Wallet home
### Screen: `BitcoinWalletActivity`
1. Wallet opens as usual.
2. Top area shows:
   - active role badge
   - registered role summary
   - status of active role
3. Primary action button becomes **task launcher for active role**, but a new secondary control opens a role/task sheet.
4. Role/task sheet lists all tasks the participant is allowed to do now.
5. If a task is blocked, show inline reason and remediation.

### Task sheet entries
- `Redeem Voucher`
- `Issue Voucher`
- `Accept Lightning Payment`
- `Verify SELL Settlements`
- `Manage SATNET Roles`

## 6.3 Agent flow
### Screen: `AgentVoucherActivity`
Pre-checks before entering issuance:
- role capability `CAP_VOUCHER_ISSUE`
- role status not suspended
- risk state not `BLOCK`
- daily/monthly issuance limits not exceeded
- step-up auth if amount exceeds configured threshold

Post-issuance:
- store agent role subject id and agent participant root id
- create local risk event for issuance
- queue mesh sync as usual

## 6.4 Merchant flow
### Screen: `MerchantLightningActivity`
Pre-checks:
- role capability `CAP_MERCHANT_ACCEPT_LIGHTNING`
- Lightning feature enabled
- merchant role status active or limited
- no unresolved risk hold for merchant role

On settlement or merchant-linked voucher handling:
- persist merchant subject and root correlation fields where applicable
- block merchant self-approval paths

## 6.5 Redemption flow
### Screen: `VoucherRedemptionActivity`
Before redemption:
- verify voucher not blocked or under hard dispute hold
- persist redeemer role subject id and redeemer root id
- run self-loop fraud rule checks

If flagged but not blocked:
- allow queued/manual review note
- continue only for low/medium risk tiers

## 6.6 Verifier flow
### Screen: `VerifierDashboardActivity`
Verifier actions split into two states:
1. inspect
2. approve / resolve

For every voucher:
- show participant conflict badge if same participant appears under another role
- show risk score, risk flags, dispute status, and prior audit history
- show explicit denial reason instead of generic toast when blocked

For high-value or flagged cases:
- require quorum count > 1
- UI shows “quorum pending” instead of “approved” until threshold met

## 6.7 Dispute wireflow
1. User or merchant opens dispute from voucher detail screen.
2. Create `voucher_disputes` row and append `voucher_risk_events` row.
3. Voucher state moves to `DISPUTE_STATUS_OPEN` and optionally `RISK_STATE_HOLD`.
4. Eligible verifier/resolver sees case in dashboard.
5. Resolver records signed decision summary and evidence reference.
6. Ledger updates:
   - dispute record status
   - voucher dispute status
   - dispute resolver ids
   - risk state normalization
7. Audit bundle sync exports non-secret dispute metadata.

---

## 7. Fraud / Risk Rules

## 7.1 Rule design principles
- append-only risk events
- deterministic local checks first
- scoped holds instead of global account shutdown when possible
- human-readable reason codes
- local enforcement continues when offline

## 7.2 Core rules

### MR-001 Self-verification block
If verifier root id matches:
- `agent_participant_root_id`
- `redeemer_participant_root_id`
- `merchant_participant_root_id`
then deny inspect/approve/resolve.

Action:
- block
- create risk event with `rule_code = MR-001`

### MR-002 Previous reviewer block
If resolver root id matches prior settlement verifier root id, deny dispute resolution.

Action:
- block
- record `MR-002`

### MR-003 Self-redemption loop monitor
If same participant root repeatedly issues and redeems through linked roles beyond threshold, flag.

Action:
- first threshold: monitor
- second threshold: hold
- repeated threshold: block issuance and require review

### MR-004 Circular merchant-agent inflation
If the same participant root alternates between agent issuance and merchant settlement in an anomalous loop, flag volume inflation.

Signals:
- repeated same-root interactions
- unusually short issue-to-redeem times
- repeated same amount bands

Action:
- risk score +20
- set `RISK_STATE_HOLD` at configured threshold

### MR-005 Velocity limits
Evaluate daily and monthly totals by role.

Action:
- deny issuance or merchant acceptance when limit exceeded
- code `MR-005-DAILY` / `MR-005-MONTHLY`

### MR-006 High-value step-up auth
Amounts above per-role threshold require fresh unlock/step-up.

Action:
- deny with `STEP_UP_REQUIRED` until refreshed

### MR-007 Replayed or disputed voucher hold
If voucher already has replay indicators, dispute open status, or mismatched audit chain:
- deny settlement approval
- optionally deny redemption depending on state

### MR-008 Suspended role containment
If one role is suspended:
- only that role loses sensitive capabilities
- wallet viewing and other safe roles remain available unless separately blocked

## 7.3 Risk scoring guidance
- informational anomaly: +5
- repeated velocity anomaly: +10
- self-loop pattern: +15
- circular role inflation: +20
- verifier conflict attempt: +25
- replay/dispute inconsistency: +30

Thresholds:
- `0-19`: normal
- `20-39`: monitor
- `40-59`: hold
- `60+`: block

## 7.4 Evidence and audit requirements
For blocked verifier/dispute actions, always persist:
- actor subject id
- actor root id
- role
- rule code
- message
- timestamp
- voucher id if applicable

---

## 8. Exact Updates for `SatnetRoleManager`

## 8.1 Keep current API behavior
Do not break:
- `registerAsUser()`
- `registerAsAgent(String, String)`
- `registerAsMerchant(String, String)`
- `registerAsVerifier()`
- `switchRole(int)`
- `getActiveRole()`

These remain as compatibility wrappers.

## 8.2 Add new enums / constants
Add:
- role status constants
- risk tier constants
- capability constants or `enum RoleCapability`
- `AuthorizationResult` value class
- `RoleProfile` value class

## 8.3 New methods
Add the following methods:

```java
public String getParticipantRootSubjectId();
public String getRoleSubjectId(int role);
public RoleProfile getRoleProfile(int role);
public java.util.List<RoleProfile> getRegisteredRoleProfiles();
public java.util.List<Integer> getRegisteredRoleList();
public boolean hasCapability(int capability);
public boolean hasCapability(int role, int capability);
public AuthorizationResult authorize(int capability);
public AuthorizationResult authorize(int capability, String reasonContext);
public void updateRoleStatus(int role, int status, String reason);
public void updateRoleRiskTier(int role, int riskTier);
public void updateRoleReputation(int role, int reputationScore);
public void updateRoleLimits(int role, long dailyLimitSats, long monthlyLimitSats);
public boolean requiresStepUpForAmount(int role, long amountSats);
```

## 8.4 Registration behavior changes
### `registerAsUser()`
- ensure participant root id exists
- ensure user role subject id exists
- initialize default low-risk profile

### `registerAsAgent(String agentName, String location)`
- preserve existing display fields
- initialize agent role subject id if missing
- set default daily/monthly issuance limits
- set `requiresStepUpForHighValue = true`

### `registerAsMerchant(String businessName, String businessType)`
- preserve existing merchant metadata
- initialize merchant role profile with merchant limits

### `registerAsVerifier()`
- create verifier role subject id
- initialize verifier profile with stricter status defaults
- do not automatically grant dispute override privileges beyond verifier capabilities

## 8.5 Active-role sanitization behavior
Keep `activeRole` as UI state, but change internal comments and method docs to clarify:
- active role is not authorization
- active role only selects default task routing and display copy

## 8.6 Capability mapping
Default capability mapping:
- `ROLE_USER`: wallet view, backup, redeem, manage roles
- `ROLE_AGENT`: user capabilities + issue voucher
- `ROLE_MERCHANT`: user capabilities + accept Lightning + merchant settlement view
- `ROLE_VERIFIER`: user capabilities + inspect + approve settlement + resolve dispute

---

## 9. Exact Updates for `SatnetRoleConflictPolicy`

## 9.1 Replace narrow verifier-only logic with action-aware policy
Keep existing methods for compatibility, but route them internally through a new action-aware policy engine.

## 9.2 Add action type constants
- `ACTION_REDEEM_VOUCHER`
- `ACTION_ISSUE_VOUCHER`
- `ACTION_ACCEPT_MERCHANT_PAYMENT`
- `ACTION_INSPECT_VOUCHER`
- `ACTION_VERIFY_SETTLEMENT`
- `ACTION_RESOLVE_DISPUTE`

## 9.3 Add result object expansion
Expand `ConflictCheck` to include:
- `boolean allowed`
- `String reasonCode`
- `String message`
- `int recommendedRiskState`
- `boolean shouldRecordRiskEvent`

## 9.4 New methods

```java
public static ConflictCheck authorizeAction(
        SatnetRoleManager roleManager,
        int actionType,
        int actorRole,
        VoucherParticipantSnapshot snapshot);

public static ConflictCheck authorizeAction(
        String actorRoleSubjectId,
        String actorRootSubjectId,
        int actionType,
        int actorRole,
        VoucherParticipantSnapshot snapshot);

public static ConflictCheck canIssueVoucher(
        String actorRootSubjectId,
        int actorRole,
        VoucherParticipantSnapshot snapshot);

public static ConflictCheck canRedeemVoucher(
        String actorRoleSubjectId,
        String actorRootSubjectId,
        int actorRole,
        VoucherParticipantSnapshot snapshot);

public static ConflictCheck canAcceptMerchantPayment(
        String actorRoleSubjectId,
        String actorRootSubjectId,
        int actorRole,
        VoucherParticipantSnapshot snapshot);
```

## 9.5 Enforcement rules in policy
- block same-root verifier/redeemer/agent/merchant overlap where action would approve or resolve their own economic activity
- allow same participant to hold multiple roles in general
- deny only conflicting actions, not unrelated role usage
- keep user-facing messages explicit and actionable

### Example reason codes
- `SELF_AGENT_VERIFY`
- `SELF_REDEEM_VERIFY`
- `SELF_MERCHANT_VERIFY`
- `PREVIOUS_VERIFIER_DISPUTE_CONFLICT`
- `DISPUTE_ALREADY_OWNED`
- `SAME_PARTICIPANT_ROLE_COLLISION`

---

## 10. Exact Updates for `VoucherLedger`

## 10.1 Constructor / versioning
- bump `DB_VERSION` to `10`
- update `onCreate()`, `onUpgrade()`, and `onOpen()` to initialize dispute/risk schema

## 10.2 New schema helpers
Add:

```java
private void createVoucherDisputesTable(SQLiteDatabase db);
private void createVoucherRiskEventsTable(SQLiteDatabase db);
private void ensureVoucherRiskSchema(SQLiteDatabase db);
```

## 10.3 New and changed write methods

### Overload `recordIssuedVoucher()`
Add overload:

```java
public void recordIssuedVoucher(
        BitcoinVoucher voucher,
        String agentRoleSubjectId,
        String agentParticipantRootId);
```

Behavior:
- store role subject id and root id
- initialize risk/dispute columns

### Overload `recordRedemption()`
Add overload:

```java
public void recordRedemption(
        BitcoinVoucher voucher,
        String walletAddress,
        String txHash,
        String redeemerRoleSubjectId,
        String redeemerParticipantRootId);
```

### Update `markSettlementVerified()`
Add overload:

```java
public void markSettlementVerified(
        String voucherId,
        String verifierRoleSubjectId,
        String verifierParticipantRootId,
        int achievedQuorum,
        int requiredQuorum);
```

Behavior:
- run action-aware conflict policy
- update settlement verifier ids
- update quorum fields
- only set final settlement state when quorum reached

### Add dispute methods

```java
public void openDispute(
        String voucherId,
        String openedBySubjectId,
        String openedByRootId,
        int openedByRole,
        String reasonCode,
        String evidenceBundleRef);

public void assignDisputeResolver(
        String disputeId,
        String resolverSubjectId,
        String resolverRootId);

public void resolveDispute(
        String disputeId,
        String resolutionCode,
        String resolutionMessage,
        String resolverSubjectId,
        String resolverRootId);
```

### Add risk event methods

```java
public void recordRiskEvent(
        String voucherId,
        String subjectId,
        String participantRootId,
        int actorRole,
        String eventType,
        int riskScoreDelta,
        String ruleCode,
        String eventMessage);

public int getAggregatedRiskScoreForParticipant(String participantRootId);
public int getVoucherRiskState(String voucherId);
public void updateVoucherRiskState(String voucherId, int riskState, int riskScore, String riskFlagsJson, String decisionCode);
```

## 10.4 Read/query additions
Add queries for:
- vouchers on risk hold
- open disputes
- pending quorum approvals
- participant risk history by root id
- risk events pending Rhizome export

## 10.5 Update `getVoucherParticipantSnapshot()`
Expand query to populate new root-id and risk/dispute fields.

## 10.6 Rhizome export alignment
In a later patch, extend `VoucherAuditRhizomeSync` to export:
- verifier audit records
- dispute summaries
- risk event summaries without private wallet secrets

---

## 11. UI Update Summary by Screen

### `SatnetRoleSetupActivity`
- multi-role card layout
- status/risk badge rendering
- “add another role” flow
- blocked role explanation panel

### `BitcoinWalletActivity`
- active-role badge + status subtitle
- secondary task sheet
- denial reason rendering for blocked tasks
- role-aware summary copy

### `VerifierDashboardActivity`
- risk state, dispute state, quorum badges
- conflict badge and reason code display
- blocked actions remain inspectable but not executable where safe

### `AgentVoucherActivity`
- limit summary before issuance
- step-up requirement prompt for high-value issuance

### `VoucherRedemptionActivity`
- pre-redeem risk notice
- dispute hold explanation

### `MerchantLightningActivity`
- merchant role status badge
- settlement risk explanation when held

---

## 12. Migration Plan

## 12.1 Existing installs
On first run after upgrade:
- preserve `participant_subject_id` as legacy fallback
- create `participant_root_subject_id` if missing
- create per-role subject ids for every registered role
- default all existing roles to:
  - status `ACTIVE`
  - risk tier `LOW`
  - reputation score `50`

## 12.2 Voucher migration
For old voucher rows:
- keep existing `agent_subject_id`, `redeemer_subject_id`, etc.
- root-id fields remain null until new local activity touches the record, unless a safe local mapping exists
- risk/dispute fields default to neutral values

## 12.3 Backward compatibility
- do not remove old methods in this phase
- old single-subject conflict methods continue to work if root-id fields are absent
- if root ids are missing, conflict policy falls back to role subject comparison

---

## 13. Testing Requirements

## 13.1 Unit tests
Add tests for:
- `SatnetRoleManager` profile migration
- capability mapping by role
- suspended role denying only scoped capabilities
- per-role subject id persistence
- daily/monthly limit enforcement
- step-up auth requirement calculation

## 13.2 Conflict policy tests
Add tests for:
- verifier blocked from own agent voucher
- verifier blocked from own merchant-linked voucher
- resolver blocked if previously verified same voucher
- agent + merchant user allowed to hold both roles but not self-approve
- fallback behavior when root ids are unavailable

## 13.3 Ledger tests
Add tests for:
- schema migration from v9 to v10
- risk event append-only behavior
- open/resolve dispute lifecycle
- quorum settlement verification behavior
- `getVoucherParticipantSnapshot()` populating new fields

## 13.4 UI / Robolectric tests
Extend existing SATNET UI tests to verify:
- role/task sheet content for multi-role users
- blocked reason text shown when action denied
- suspended roles still visible but disabled
- wallet primary action follows active role only when authorized

---

## 14. Rollout Sequence

### Phase 1
- add `RoleProfile` and capability checks in `SatnetRoleManager`
- keep UI mostly unchanged
- add policy engine scaffolding

### Phase 2
- add `VoucherLedger` risk/dispute schema and migration
- write risk events for verifier conflicts and high-value issuance

### Phase 3
- update wallet and role setup UX for explicit multi-role management
- add dispute wireflow

### Phase 4
- extend Rhizome export/import for dispute and risk summaries
- enable region-scoped policy profiles and quorum rules

---

## 15. Acceptance Criteria

This implementation is complete when:
- a participant can register and switch among multiple roles without losing data
- sensitive actions are gated by capability, status, runtime, and conflict checks
- self-dealing verifier and resolver actions are blocked deterministically
- voucher ledger stores risk/dispute metadata and audit events
- UI explains blocked actions instead of silently hiding them
- existing single-role users are migrated safely
- core tests cover multi-role edge cases and schema migration

