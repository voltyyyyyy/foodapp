# Issues

## UX audit verdict: Needs work

The app has a clear visual foundation and several strong trust cues, including explicit eligibility, contact, and availability warnings. The main risks are friction before discovery, incomplete reminder support, unclear schedule meaning, and recovery gaps in reporting and routing.

## Scope and evidence

- Reviewed the current Android Compose screens and state flow, the staff portal source, and the supplied SRS.
- Visual references include the current home capture from 17 October and earlier search, details, report, and route captures from 3–4 October. Older captures are orientation only; findings were checked against current source where possible.
- The Docker stack was not running, so the staff portal was reviewed from source rather than in a live browser. This is an expert walkthrough, not a participant usability study. TalkBack, large text, and the SRS first-use success target have not been tested.

## High impact issues

### 1. Account access blocks discovery before users can judge whether a service fits

**Severity:** High · **Type:** Product decision / possible SRS override

The app shows the sign-in screen whenever there is no active session. A new seeker must register, verify email in a browser, return to the app, and sign in before seeing listings or service details (`MainActivity.kt:154–160`, `AccountScreens.kt:39–99`). This is a substantial hurdle for a person who may need help quickly or may not be able to complete email verification immediately.

The SRS requires authentication for discovery in 4.4.2.5, so this is a deliberate requirement choice rather than a code defect. Consider allowing read-only search and details before sign-in, then requiring an account for bookmarks, reports, reminders, and profile actions. If the gate must remain, explain the account requirement before registration and make the email-verification return path especially clear.

### 2. Administrators can sign into Android but have no staff destination

**Severity:** Medium · **Type:** Role handoff

The Android sign-in accepts the shared account session and displays an Administrator role in Profile, but successful sign-in always opens the seeker app. Android has no moderation screens or visible link to the staff portal (`AppViewModel.kt:107–116`, `AccountScreens.kt:104–122`, `MainActivity.kt:154–160`). An administrator can reasonably expect to find their work queue after signing in and instead reaches discovery.

After an administrator signs in, show a clear “Open staff portal” action or direct them to `/staff/` with an explanation. Keep ordinary registered users in the seeker app.

### 3. Saved-service reminders are absent from the Android flow

**Severity:** High · **Type:** SRS requirement gap

The Saved screen only lists bookmarks and withdrawn items; it has no per-service reminder toggle, daily time, schedule warning, permission state, or notification-settings action (`MainActivity.kt:444–455`). There is no reminder or notification implementation in the Android source. This leaves UC-05 reminder setup and UC-11 receipt unreachable, despite SRS 4.6.

Add the reminder controls and states in Saved, including “Schedule not confirmed,” blocked notification permission, last cache update, and clear enable/disable feedback. Keep settings separate by account and make the sign-out/expiry behavior explicit.

### 4. The app and staff portal use one schedule as both opening hours and a distribution session

**Severity:** High · **Type:** Data meaning / trust

The service `schedules` data drives operating status (`backend/app/domain.py:92, 209–236`; `backend/app/main.py:168–196`). The staff editor labels it “Operating hours” (`staff-portal/portal.js:67–78`), while the seeker details call the same data “Distribution schedule” (`ServiceDetails.kt:88–149`) and result cards describe it as “Scheduled open now” (`MainActivity.kt:431`).

This conflicts with the SRS distinction between operating hours, service sessions, and food availability. Staff may enter office hours where a pickup session is expected, and seekers may read an open status as confirmation that a distribution is happening. Store and label operating hours separately from dated or recurring meal/collection sessions. Use each only for the matching search, detail, and reminder behavior.

### 5. Availability evidence is not visible when users compare results

**Severity:** High · **Type:** Trust / information hierarchy

Result cards show an availability label and, when present, only its observation time (`MainActivity.kt:404–434`). The source, validity end, and confidence are inside the collapsed “Sources & verification” section on details (`ServiceDetails.kt:239–248`). SRS 4.3.2.3–4.3.2.6 requires those facts with public availability labels.

Put concise evidence directly beside the availability label, for example source, observed time, valid-until time, and confidence. Keep the uncertainty label and contact-before-travel warning close to it.

### 6. A route failure removes the service context the user needs to recover

**Severity:** High · **Type:** Broken recovery flow

Requesting directions switches from details to the Map tab. On failure, the route sheet shows the destination role/address, an error, retry, and a “Support details” link; the service name, eligibility, availability, and contact actions are no longer visible (`AppViewModel.kt:396–418`, `FoodMap.kt:249–321`). This does not satisfy the intent of keeping service details visible after route failure in SRS 4.2.2.2 and UC-03.

Keep the selected service detail view open and show route status within it, or retain a persistent service summary with its name, key eligibility, and approved contact actions beside the failed route.

### 7. The report form promises anonymity while the app requires and records an account

**Severity:** High · **Type:** Contradictory copy / trust

The report form says “No account required” (`MainActivity.kt:471–495`), but reporting is behind the authenticated app and submits through the signed-in session; the report flow is expected to store the reporter account ID (`AppViewModel.kt:425–434`, SRS 4.4.1.5). Users may believe their report is anonymous when it is not.

Replace the sentence with a truthful explanation such as: “This report is linked to your FoodBridge account for staff review. Please avoid including sensitive personal details.” Keep the existing note that staff review reports before listings change.

### 8. Leaving and reopening a report silently discards the draft

**Severity:** High · **Type:** User control / data loss

The bottom navigation can leave the report screen, while reopening the form resets category, description, and evidence (`AppViewModel.kt:220–229`). There is no discard confirmation. Network errors retain input only while the form remains open. This makes an accidental tab change or a brief detour destructive, despite UC-04 requiring input retention where possible.

Keep the report draft until submission succeeds or the user explicitly discards it. Ask before abandoning a non-empty report, and provide a visible Cancel action with the same confirmation.

## Medium impact issues

### 9. Blank-origin browsing ignores filters after the user has configured them

**Severity:** Medium · **Type:** Search expectation

The Find screen shows active filter values, then a blank origin sends the user to the all-support directory, which ignores those filters (`MainActivity.kt:280–298`, `AppViewModel.kt:238–259`). The directory does say “Your search filters are not applied,” but only after the user submits (`SearchSuggestions.kt:61–70`). A person who sets a dietary need or distance limit may expect it to affect the results.

Warn next to the Find action that blank-origin browsing is an A–Z directory with no suitability matching. Offer two explicit actions: “Find matching services” after entering an origin, and “Browse all listings” with filters visibly marked as inactive.

### 10. The preferred-time picker allows past times and the “open now” label can mislead

**Severity:** Medium · **Type:** Search clarity / error prevention

The date and time pickers have no minimum date or past-time guard (`MainActivity.kt:346–354`). The selected instant is used to calculate operating status and filter results (`backend/app/main.py:168–188`). Users can therefore request a past time and receive services based on a historical schedule. When a future time is selected, “Scheduled open now” and the “Open now” summary can also read as current status rather than status at the chosen time (`MainActivity.kt:242–249, 431`).

If this control is for upcoming support, disallow past dates and earlier times today. Change the wording to “Open at selected time” when a preferred time is set, and show that time in the result summary.

### 11. Clearing local bookmarks and preferences is immediate and irreversible

**Severity:** Medium · **Type:** Error prevention

“Clear local saves & preferences” calls a function that deletes the current account’s local bookmarks and dietary preferences immediately (`MainActivity.kt:464–466`, `AppViewModel.kt:309`). There is no confirmation or undo.

Show a confirmation that lists exactly what will be removed, then offer a short undo if practical. Keep this action visually separate from routine profile controls.

### 12. Approved data can be published without a final comparison or reason

**Severity:** High · **Type:** Staff workflow / data integrity

For an already approved record, the editor says saving publishes immediately and its primary action is “Save and publish.” The form submits the changed record directly, without a final before/after review or reason (`staff-portal/portal.js:48, 67–75`). This makes an accidental field edit public in one step and diverges from the UC-06 flow of comparing proposed values and confirming approval.

Add a review step with changed fields highlighted, current and proposed values, source/verification context, and a reason before publication. Make the final action explicit and provide a clear return-to-edit path.

### 13. Important staff data requires editing raw JSON

**Severity:** Medium · **Type:** Cognitive load / error-prone editing

Date-specific exceptions and availability observations are exposed as JSON textareas under “Advanced information” (`staff-portal/portal.js:67`). A syntax error is caught, but staff still need to understand the data structure and manually supply status, source, observation time, validity, and confidence.

Use structured controls for availability evidence and date exceptions. Keep raw JSON as an optional expert view only. Show validation next to the relevant field and preserve the structured draft after errors.

### 14. Staff queue tables require horizontal scrolling on narrow screens

**Severity:** Medium · **Type:** Responsive usability / accessibility

The queue renders four-column tables (`staff-portal/portal.js:42`) inside an overflow-scrolling wrapper. The mobile CSS changes the overall layout but does not convert the table into a narrow-screen view (`staff-portal/portal.css:3–5`). A staff member on a phone must pan sideways to compare record, status, and time.

Switch queue rows to stacked cards on narrow screens, keeping the record name, status, submitted time, and primary action visible without horizontal panning.

## Accessibility items to verify

### 15. Small metadata and dynamic status announcements need an assistive-technology pass

**Severity:** Medium · **Type:** Accessibility verification

Some high-value metadata is rendered at 11sp, including availability and observation time (`MainActivity.kt:408, 420–432`). The app’s shared `Banner` component does not define explicit live-region semantics (`MainActivity.kt:213–217`), so loading, error, and success updates need verification with TalkBack. The map should also be checked for an equally usable non-map path to select services.

Check at large Android font settings and with TalkBack. Keep secondary evidence comfortably readable, announce async status/error/success changes, and ensure the list view remains available for every map result.

## Recommended order

1. Decide whether the sign-in gate should remain before discovery; it is the largest first-use friction and requires an explicit SRS/product decision.
2. Separate service sessions from opening hours and surface availability evidence on result cards.
3. Complete Saved reminders and fix report and route recovery behavior.
4. Add the staff publish review step and structured evidence editing.
5. Run the SRS first-use task with at least five representative signed-in users, then verify font scaling and TalkBack behavior. This audit does not establish the 80% / two-minute target in SRS 5.4.1.
