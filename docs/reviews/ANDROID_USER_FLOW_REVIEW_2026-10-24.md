# Android user flow review — 24 October 2026

**Implementation follow-up:** See the [39-finding fix register and verification record](ANDROID_FLOW_FIXES_2026-10-24.md). This review preserves the original findings and evidence; it does not describe the post-fix state.

**Verdict: Needs work.** The app covers the main food-support tasks, but several transitions lose context or make travel and reminder decisions harder to judge. Prioritise continuity, safe travel decisions and reliable recovery before visual polish.

**Emulator follow-up completed:** The second pass used a connected Android 12/API 31 emulator and a separately built review app. The register now contains **39 findings: 9 High, 28 Medium and 2 Low**. New device-observed issues are F37–F39; F34's report-keyboard risk is now reproduced. See the [emulator walkthrough, findings and screenshot evidence](ANDROID_EMULATOR_REVIEW_2026-10-24.md) for exactly what was exercised and what remains unverified.

## Scope and evidence

Reviewed the current Android source at commit `96ef02e`, including every user-facing screen, shared navigation, repository/cache behaviour, reminders, sessions and reporting. Also traced the browser verification/reset handoff and the backend rules that affect visible validation, preferred-time searches and report receipts. Staff moderation is outside scope.

The initial pass was a **source-based expert walkthrough**. The subsequent [emulator pass](ANDROID_EMULATOR_REVIEW_2026-10-24.md) adds current screenshots, UI interaction and alarm-state evidence. It is still an expert review rather than representative-user usability testing. TalkBack, real email/Google consent, live geocoding/routing accuracy and actual notification delivery remain unverified. Existing account verification notes describe earlier builds, not this run.

No production application behaviour, real accounts, credentials or live data were changed. The emulator pass used an isolated package, synthetic account/listings and a separate SQLite API. Other pre-existing workspace changes were left intact.

**Evidence labels:** **Confirmed** means the behaviour is directly expressed in source; **Design judgement** means the proposed hierarchy or interaction improvement needs user validation; **Device check** means runtime testing is still needed. These labels do not imply measured failure rates.

**Priority:** **High** = could misdirect travel, obscure an unsuccessful task, or undermine a core flow; **Medium** = material confusion, repeated effort or recovery friction; **Low** = secondary clarity/polish. No finding is labelled a tested blocker.

## Flow coverage

| Part of the app | Path reviewed | Findings |
| --- | --- | --- |
| Launch and session restore | Launch → stored session check → Find or sign-in; expiry → reauthenticate | F01, F11–F12, F23 |
| Email sign-in and Google sign-in | Enter credentials/choose Google → success, cancellation, validation and network failure | F01–F03 |
| Registration | Name/email/password/confirmation → email verification → return to sign-in | F01–F04 |
| Recovery and verification | Forgot/resend → email → browser link → expired link/retry → app | F01–F04 |
| Find and location | Address/name entry, suggestions, location permission, geocode candidates | F05, F09–F10, F21 |
| Filters and future time | Radius/type/diet/access/time → Done/dismiss → search | F07–F08, F14, F17 |
| Nearest results | Zero/nonzero results → edit, compare, save, detail | F06, F10–F12, F17 |
| Browse A–Z and name results | All support/name matches → provider or service → return | F05–F06, F20 |
| Map discovery | Initial map, pan/search area, filters, pin preview, saved places, list switch | F05, F12–F15, F21, F35 |
| Directions | Destination → start location → mode → route/retry → steps/external app | F11, F16–F17, F21–F22, F35 |
| Service information | Contact, diets/access, eligibility, schedules, sources and reporting | F10, F16–F19, F32 |
| Provider directory/details | Query/category → provider → programmes/contact/location | F06, F15–F16, F19–F20, F35 |
| Saved | Save/remove, provider/service/missing listing, refresh and offline state | F06, F23, F25–F26 |
| Reminders | Daily time, session switch, permissions, expiry, notification tap | F21, F24–F26 |
| Correction reports | Start, edit, cancel, keep/discard, resume, expired session, send/receipt | F27–F30, F34 |
| Profile and privacy | Edit name, diets, password, sign-out, clear local settings, data-use information | F01–F04, F07, F31, F33–F34 |
| Help and failure recovery | How-to, external assistance, missing handlers, offline/network errors | F03, F09–F12, F18, F21–F23, F36 |
| Accessibility across screens | Labels, switch semantics, calendar targets, status, large text, IME | F02, F32, F34–F35 |

## I like — preserve these behaviours

- **Honest support information.** Availability, operating hours, collection locations and eligibility are separate concepts. Unknown states and the instruction to contact providers should remain explicit.
- **Privacy and account boundaries.** Sign-in gates discovery as required; saves/preferences are separated by account; location remains temporary. Report drafts are encrypted and same-account session recovery is supported.
- **Useful recovery foundations.** Reports retain input on ordinary request errors; leaving a draft offers keep/discard/continue; missing saved listings remain removable; location and route failures provide alternatives. Shared banners use a polite live region and bookmark buttons have accessible labels.

## I wish — issue register

### Entry, accounts and onboarding

**F01 · Medium · Passwords disappear before the outcome is known.** **Confirmed.**

- **Path:** Submit registration with mismatched passwords, sign in during a network failure, or submit an invalid password change. The handlers immediately clear password fields; the browser reset form does the same before its request completes.
- **Impact:** A small validation mistake or temporary connection problem forces users to retype several long values. This is especially costly during account creation.
- **Change:** Keep passwords in memory until success, explicit cancellation or leaving the form; clear on those events. Never persist passwords in saved state. Preserve the app's existing secure storage boundary.
- **Evidence:** [Account forms][accounts], [browser reset handoff][account-browser].

**F02 · Medium · Form errors are not translated into field-level guidance.** **Confirmed; assistive-technology outcome needs a device check.**

- **Path:** Send an invalid email, a short password, an empty name or an invalid report evidence link. The repository stringifies backend `detail`; validation arrays can reach a top-level banner as JSON-like text. Fields do not identify the error locally.
- **Impact:** Users must infer which input failed and scroll between the banner and field. The backend sanitises validation errors to location/message/type; the concern here is readability and field association, not exposure of submitted passwords.
- **Change:** Map validation errors to plain messages beside the relevant fields; retain a short form summary and bring the first invalid field into view. Keep useful server validation as the authority.
- **Evidence:** [Request error handling][repository], [account inputs][accounts], [report inputs][report-ui].

**F03 · Medium · Email verification/reset has a weak return path.** **Confirmed + design judgement.**

- **Path:** Register or request recovery → open email → browser form → manually reopen FoodBridge. The browser exposes a clickable **Staff sign in** link while registered users get only a sentence telling them to return to the app. Android's manifest has no web-link return route.
- **Impact:** A necessary multi-app task ends with a prominent path intended for a different audience. The app cannot acknowledge completion until users navigate back and sign in.
- **Change:** Give registered users the primary return action, with a supported app-link handoff and a clear manual fallback. Show the pending email and concise resend/change-email guidance in the app. Keep acknowledgements neutral so they do not disclose account existence.
- **Evidence:** [Account entry][accounts], [browser page][account-html], [manifest][manifest].

**F04 · Medium · Privacy and basic help are unavailable before creating an account.** **Confirmed.**

- **Path:** Fresh launch → sign-in/register. The account-null branch returns the authentication screen before help/privacy routing, and account entry has no links to either.
- **Impact:** Users must supply identity information before reading the in-app explanation of data use or learning how the service works.
- **Change:** Make privacy and a short service explanation accessible from account entry. Keep discovery, saved support and reporting behind sign-in, as required by the SRS.
- **Evidence:** [Root account gate][shell], [account entry][accounts], [privacy content][privacy].

### Find, lists and navigation

**F05 · Medium · Address and organisation search change meaning implicitly.** **Confirmed + design judgement.**

- **Path:** Type text that matches a known provider/service and press Find support. The app chooses name matching before address geocoding, which changes whether dietary/access filters apply. Map can preview a single match while Find opens a name-results page.
- **Impact:** The same field and submit action can mean nearby suitability search, name lookup or map preview. A visible filter summary may have no effect on the selected path.
- **Change:** Make the options explicit at the decision point: **Search near this address** and **Find this organisation**. Label name suggestions as bypassing suitability matching before selection. Retain the existing A–Z and name-search semantics.
- **Evidence:** [Search dispatch][vm-search], [suggestions and name results][suggestions], [map search handling][map].

**F06 · Medium · Back and tab navigation do not reliably restore the previous place.** **Confirmed.**

- **Path:** From Saved, open a saved provider and press Back: the page falls back to Providers instead of Saved. Open a provider from a filtered directory and return: the directory's `remember` query/category are recreated. Nearest results have neither an app-bar Back action nor a root BackHandler to edit the search; system Back can leave the activity. Reselecting Find uses `results.isNotEmpty()`, so zero-result context is lost.
- **Impact:** Users repeatedly reconstruct searches or leave the app when trying to go one step back.
- **Change:** Use explicit origin/back-stack state for each destination; restore query, category and list position. Define Back from nearest results as returning to search editing. Use search state rather than nonempty data to decide which page to restore.
- **Evidence:** [Navigation shell][shell], [tab/back transitions][vm-nav], [directory state][providers].

**F07 · Medium · Filter edits commit immediately and also change profile preferences.** **Confirmed.**

- **Path:** Open filters, change a diet/radius, then dismiss by Back or swipe. Changes have already updated state and cleared results; diets have also been saved to the account's local preferences. Find's Done only closes; Map's dismiss callback may run a search.
- **Impact:** Dismiss, Done and changing a profile preference have overlapping meanings. Users cannot experiment with a search without changing their saved diets.
- **Change:** Use a temporary filter draft with **Apply filters** and an explicit reset/cancel policy. Distinguish dietary preferences from a one-search override, or clearly state when a choice updates both. Make dismissal consistent across Find and Map.
- **Evidence:** [Filter sheet][filters], [state invalidation and navigation][vm-update], [diet persistence][vm-local], [map sheet dismissal][map].

**F08 · Medium · A preferred time silently imposes an operating-hours filter.** **Confirmed.**

- **Path:** Set a future time while leaving **Open at selected time** off. Backend search still requires `OPEN` whenever `preferred_time` is present.
- **Impact:** A separate off switch implies closed/unknown-hour services can remain, while the results exclude them. Users may not understand why support disappears.
- **Change:** Make this dependency explicit: selecting a time enables and explains the operating-hours constraint, or make the switch accurately represent the chosen search contract. Keep hours distinct from confirmed food/distribution availability.
- **Evidence:** [Time and hours controls][filters], [search filtering][backend-search].

**F09 · Medium · Empty results offer a generic rather than contextual recovery.** **Confirmed + design judgement.**

- **Path:** A nearest search returns zero services. **Change filters** returns to the whole Find screen instead of opening filters. Map says to adjust filters/browse providers without an inline action in its empty summary.
- **Impact:** Users must work out which constraint to change and how to reach it.
- **Change:** Show the active needs with direct **Edit filters**, **Change area** and **Contact a provider** actions. Explain that unknown information can exclude services. Never silently broaden radius or relax dietary/access needs.
- **Evidence:** [Results/empty state][results], [map summary][map].

**F10 · Medium · Result cards omit information needed to compare destinations.** **Confirmed + design judgement.**

- **Path:** Compare several nearby cards. They show organisation, support type, access/location role and operating status, but omit the address, dietary options and next collection session. Service names can be truncated after two lines.
- **Impact:** Users open and return from several details screens to answer “where is it, can I use it, and when can I collect?”. Similar sites from one provider are difficult to distinguish.
- **Change:** Add a compact address/area, confirmed diet summary and next recorded collection time or an explicit unknown state. Keep collection time visually separate from operating hours. Use full service identity in accessible names.
- **Evidence:** [Service cards][cards], [service detail facts][details].

### Continuity, map and travel

**F11 · High · Calling or opening a website destroys the active search/route context.** **Confirmed.**

- **Path:** Search → open a service → call the provider or open its website → return. `onStop()` calls `clearLocation()`, clearing origin, candidates, results, ETAs, route and destination.
- **Impact:** The app tells users to contact providers before travelling, then makes that advised action erase the journey they were preparing.
- **Change:** Preserve non-location UI state and selected listing. Continue clearing sensitive location/origin/route data as required; show **Starting location cleared for privacy — set it again to continue** and an explicit recovery action. Any broader location-retention change needs a deliberate privacy decision.
- **Evidence:** [Activity lifecycle][activity], [location clearing][vm-location], [contact actions][details].

**F12 · High · Cleared search state can be presented as a real zero-match result.** **Confirmed.**

- **Path:** Leave the app while nearest results are visible, then return. Clearing removes results and `hasSearched` but leaves `page = results`; ResultsScreen checks only whether the list is empty. It displays **No confirmed matches** even though no new search returned zero.
- **Impact:** Privacy clearing is misrepresented as absence of suitable support. On Map, clearing can instead restore the unfiltered service set without explaining the transition.
- **Change:** Model “search not run”, “context cleared”, “loading”, “failed” and “completed with zero matches” as distinct states. Require a completed search before displaying a no-match claim.
- **Evidence:** [Lifecycle clearing][vm-location], [result empty state][results], [map source selection][map].

**F13 · High · Map markers make materially different locations look equivalent.** **Confirmed + design judgement.**

- **Path:** Explore the map. Every service gets the same marker hue, regardless of collection/coordination role, access or reported unavailability. The distinction appears only after selecting a pin or opening details.
- **Impact:** A coordination office can visually read as an equally usable food collection destination.
- **Change:** Distinguish confirmed collection sites from coordination/unconfirmed addresses using marker shape/icon plus text/legend. Keep unknown and unavailable states legible without relying on colour alone. Preserve provider-office versus collection-point boundaries.
- **Evidence:** [Marker rendering][map-render], [pin preview][map], [location-role labels][repository].

**F14 · Medium · Map filtering and ranking context are difficult to see.** **Confirmed.**

- **Path:** Open Map without a completed search, or set filters without a starting point. It can display all cached/loaded services while saying **Food support near you**. The map shows a generic Filters chip, with no active-needs summary, and its matched count omits the nearest-ten/straight-line explanation used on Find.
- **Impact:** Users cannot readily tell whether pins are nearby suitability matches or general listings, or whether more than ten matches exist.
- **Change:** Label the unsearched map **All listed service locations**. Show active filter count/summary and the current origin/search scope; distinguish general listings from the ten nearest confirmed matches and expose the total where useful.
- **Evidence:** [Map state, chips and summary][map], [results context][results].

**F15 · Medium · Selecting a provider on Map can choose a service on the user's behalf.** **Confirmed.**

- **Path:** Select a provider suggestion that has linked services. `preview()` takes the first matching service and shows its pin/card rather than the provider or a destination choice.
- **Impact:** A provider with several locations/programmes becomes one arbitrarily ordered destination. Users may not see the other options.
- **Change:** Show a provider summary with its services and a location chooser; select automatically only when there is exactly one suitable destination, and label it clearly.
- **Evidence:** [Map preview selection][map], [provider destination chooser][providers-detail].

**F16 · High · Directions have priority over deciding whether to go.** **Confirmed layout; design judgement.**

- **Path:** Open a service/provider detail or a selected map pin. A large directions button appears before contact, while eligibility, collection schedules and delivery/application arrangements are collapsed. Even unconfirmed collection addresses retain a strong directions action.
- **Impact:** The strongest action encourages travel before the user has confirmed eligibility, a collection session or the correct destination. Warnings help, but compete with the action hierarchy.
- **Change:** Put “Can I use this?”, next recorded collection time and “Where do I go?” in a short visible decision summary. Make contact/application the primary action for referral, assessment and unconfirmed-location cases; keep labelled directions available as a secondary action where appropriate.
- **Evidence:** [Service details][details], [provider details][providers-detail], [map preview actions][map].

**F17 · High · Future-time search context is lost when opening details and directions.** **Confirmed.**

- **Path:** Search for a future time → open a result. Detail refresh fetches the current-time service and copies distance/explanations, but not `status_at`; the calendar defaults to today. Directions use current departure timing; public transport explicitly says **Depart now**.
- **Impact:** One task switches between future suitability, current hours, today's calendar and a current route without a coherent explanation. Users can misjudge when to travel.
- **Change:** Carry an explicit selected-time context through results, details and calendar. Clearly separate current route estimates from future support searches. If future route planning is unsupported, say so next to the estimate; do not imply an arrival-time guarantee.
- **Evidence:** [Search and detail refresh][vm-discovery], [calendar defaults][calendar], [route summary][route-summary], [current-time service endpoint][backend-detail].

**F18 · High · A failed detail refresh leaves an old listing actionable without explanation.** **Confirmed.**

- **Path:** Open a loaded/cached service that has since been withdrawn, or whose refresh fails. `detail()` ignores the failure. The earlier record and directions button remain; there is no detail-specific “could not confirm current listing” state. A 404 can occur even when the previous catalogue load was online.
- **Impact:** Users may treat withdrawn or outdated information as recently checked, particularly when preparing travel.
- **Change:** Show refresh progress/outcome on details, distinguish a network failure from withdrawal, and offer retry. A confirmed withdrawal needs a prominent no-travel state with navigation disabled; cached information should remain clearly labelled where useful.
- **Evidence:** [Silent detail refresh][vm-discovery], [detail navigation][details], [withdrawn endpoint behaviour][backend-detail].

**F19 · Medium · Service contact fallback is incomplete.** **Confirmed.**

- **Path:** Open a service with no phone/website, but with a linked provider that has contacts. Service details say **Contact details not confirmed** and do not offer **View provider** or use the provider's public contact. Provider email is available on provider details but is not offered as a fallback here; the current service schema does not have an email field.
- **Impact:** “Contact before going” becomes difficult to act on even when the directory contains a useful route to help.
- **Change:** Offer a clearly attributed provider-contact fallback and a provider-details link; render a confirmed public email where present. Keep service-specific and provider-wide contact information distinguishable.
- **Evidence:** [Service contact and provider lookup][details], [provider contacts][providers-detail], [service schema][domain].

**F20 · Medium · Provider information does not expose a browsable list of linked services.** **Confirmed.**

- **Path:** Browse A–Z/name matches → provider → look for a particular service. Linked services are used for a directions chooser, but are not displayed as service cards/links on the provider page. Programmes and locations therefore require separate browsing or entering the navigation flow.
- **Impact:** Users cannot inspect the alternatives before deciding which location/programme to use; mixed A–Z provider/service rows can feel duplicative.
- **Change:** Add **Services from this provider** with names, location roles, addresses and detail links; group provider/service identity consistently in browse and name results. Continue treating the provider's office separately from collection sites.
- **Evidence:** [Provider linked-service handling][providers-detail], [mixed browse entries][suggestions].

**F21 · Medium · Permanently denied location permission lacks a direct recovery action.** **Confirmed; permission-dialog behaviour needs a device check.**

- **Path:** Deny location permanently, then choose device location again. The UI can repeat a request that Android will no longer show. Manual address entry remains available, but there is no app-location-settings action or differentiated permanently-denied state.
- **Impact:** The same button appears to do nothing useful. Returning from notification settings also clears search/route context through F11.
- **Change:** Explain whether permission is denied or device location is off; offer the correct settings shortcut and retain manual address entry as an equally clear path. Avoid repeated automatic permission requests.
- **Evidence:** [Find permission handling][find], [map permission/start sheet][map], [activity lifecycle][activity].

**F22 · Medium · Successful routes hide the external navigation handoff.** **Confirmed + design judgement.**

- **Path:** Calculate a successful walking/driving route and look for **Open in Google Maps** to begin turn-by-turn navigation. That button exists only when route availability is not true.
- **Impact:** Failure offers a useful handoff that success removes. The app's route estimate/steps can be mistaken for an active navigation experience.
- **Change:** Offer **Open navigation app** on successful routes too; label FoodBridge's view as a route preview/estimate. Keep travel mode and destination explicit during handoff.
- **Evidence:** [Route summary and external-link condition][route-summary].

### Saved, offline use and reminders

**F23 · Medium · Local saves/preferences depend on completing a catalogue load.** **Confirmed.**

- **Path:** Sign in/recover a session during a catalogue failure with no usable approved cache. `refresh()` fetches services before reading local bookmark/diet values, and sets no general loading state. Saved can appear empty although account-local IDs exist; the initial error also tells users to **Start the API**.
- **Impact:** A server/content problem looks like lost personal data, with operator-facing recovery text.
- **Change:** Load account-local saves and preferences independently first. Use a loading/error state for unavailable listing details, retain removable saved IDs, and provide a plain **Retry loading support** action. Keep cached access subject to the existing sign-in rules.
- **Evidence:** [Refresh order and error copy][vm-refresh], [cache/local retrieval][repository], [Saved rendering][saved].

**F24 · High · “Reminder on” can mean no notification will ever be scheduled for that session pattern.** **Confirmed.**

- **Path:** Save a service whose sessions end at 07:00; leave the shared reminder time at 08:00, or choose a time after all of its sessions. The scheduler requires a session whose end is after the reminder instant. The UI checks only whether schedule rows exist and still displays the switch as enabled with “on session days”. Enabling after today's reminder time also skips today's notification.
- **Impact:** Users rely on an apparently configured reminder that has no qualifying upcoming delivery.
- **Change:** Display the next qualifying reminder date/time per service, or **No reminder can be scheduled at this time** with a direct time-edit action. Explain today's cutoff and the effect of changing the shared time on every saved service. Do not promise precise Android delivery.
- **Evidence:** [Reminder scheduling][reminders], [reminder controls][reminder-ui].

**F25 · Medium · Reminder configuration dominates Saved before there is anything to remind about.** **Confirmed + design judgement.**

- **Path:** Open Saved with no bookmarks, or with only providers. Reminder help, the shared time and a **Notifications are blocked** panel appear before the empty state/list. “Not yet granted” permission can be presented with the same blocked wording; per-service controls request permission only when enabled.
- **Impact:** A simple “find my saved support” task starts with irrelevant setup and a warning. Providers cannot use the service-reminder controls shown elsewhere on this page.
- **Change:** Lead with saved items/empty state. Reveal reminder setup when a service reminder is enabled or requested; distinguish permission not requested from blocked/disabled. Group each service and its reminder control as one understandable unit.
- **Evidence:** [Saved hierarchy][saved], [notification allowance][reminders], [permission request][reminder-ui].

**F26 · Medium · Accidental unsaving also removes reminder setup without undo.** **Confirmed.**

- **Path:** Tap the filled bookmark on a saved service. The item disappears and its reminder is removed immediately, with no Undo action; re-saving does not restore the previous reminder choice.
- **Impact:** A reversible-looking bookmark toggle discards related configuration, potentially unnoticed.
- **Change:** Confirm the outcome with **Removed from Saved — Undo** and restore the reminder configuration if undone. Keep routine saving lightweight; a confirmation dialog for every bookmark tap would add unnecessary effort.
- **Evidence:** [Bookmark/reminder removal][vm-local], [Saved list][saved].

### Reporting and follow-through

**F27 · High · An existing report draft silently changes which listing a new report is about.** **Confirmed.**

- **Path:** Start a report for A → keep draft → open listing B → tap **Report an issue**. `openReport()` restores A's target whenever any draft exists, rather than starting a report about B.
- **Impact:** Users expecting to report B can continue an old report for A. The heading changes, but the selection happens without a decision or explanation before entry.
- **Change:** Ask **Resume report for A or start a report for B?** before changing context. Preserve/discard the old draft explicitly, or support drafts per listing. Show the target prominently throughout editing and in the receipt.
- **Evidence:** [Report draft selection][vm-report], [report heading][report-ui].

**F28 · Medium · Report limits and submission feedback do not support correction well.** **Confirmed.**

- **Path:** Paste more than 1,000 description characters: the whole edit is ignored. Evidence has a backend 1,000-character limit but no visible limit/counter. Submit uses the generic **Loading information…** state, and fields remain editable while a request is in flight.
- **Impact:** Paste can appear broken; users discover evidence limits late. Edits made during submission can be cleared on success even though the request sent the earlier snapshot.
- **Change:** Explain length/URL requirements near fields, handle long paste visibly, and use **Sending report…** feedback. Lock the submitted snapshot while sending or retain subsequent edits as a separate draft. Preserve inputs after failure.
- **Evidence:** [Report inputs and global loading][report-ui], [submission snapshot/reset][vm-submit], [server report validation][domain].

**F29 · Medium · The report reference disappears with no practical receipt.** **Confirmed.**

- **Path:** Send a report → leave confirmation. The reference is ordinary Text with no copy/share action; navigation clears it, and the app has no submitted-report history. Provider reports also receive the generic **Back to support** label.
- **Impact:** Users must manually record the ID during one screen and have no in-app way to refer to their submission later.
- **Change:** Offer **Copy reference** and a small account-scoped receipt with target/category/time. Label the return action for the actual listing. Only offer report-status tracking if backed by real server state; do not invent an outcome or review timeframe.
- **Evidence:** [Confirmation][confirmation], [navigation clearing][vm-nav], [report response][backend-report].

**F30 · Medium · A timed-out report has an ambiguous outcome on retry.** **Confirmed design gap; network scenario needs runtime reproduction.**

- **Path:** The server stores a report, but the response does not reach Android. The client retains the draft and invites retry. Each backend POST generates a new reference, with no client submission key to recover the prior result.
- **Impact:** Retrying may create duplicate reports, while the user cannot tell whether the first send worked.
- **Change:** Give a send attempt a stable submission ID and make retries return the same receipt, or provide another server-backed outcome check. Use **Could not confirm submission** when the result is uncertain.
- **Evidence:** [Client submission][vm-submit], [request timeout][repository], [new report reference on each POST][backend-report].

### Profile, trust and accessibility

**F31 · Low · Successful name editing does not close the editor.** **Confirmed.**

- **Path:** Edit display name → save successfully. The model clears `profileNameDraft` and reports success, but the local `editing` flag stays true; Save/Cancel remain visible.
- **Impact:** The completed task still looks unfinished; “Cancel” is ambiguous after the save has already happened.
- **Change:** Exit edit mode on success, show the updated name and retain a brief acknowledgement. Keep the editor open with the draft on failure.
- **Evidence:** [Profile editor][accounts], [name-update response][vm-update-name].

**F32 · Medium · Dates and freshness labels do not distinguish different kinds of checking.** **Confirmed + design judgement.**

- **Path:** Compare “Updated” in Saved/results with “Checked” in sources. Catalogue retrieval uses the local fetch time; search uses the server's latest record update. `formatTime()` omits the year, while preferred times are rendered as raw ISO-like strings. Older programme sources can therefore appear less old than they are.
- **Impact:** Users must infer whether a timestamp means downloaded, edited, verified or observed, and whether the date belongs to this year.
- **Change:** Use explicit **Downloaded**, **Listing updated**, **Source checked** and **Availability observed/valid until** labels. Include the year for provenance and old records; use readable Singapore date/time formatting for selected searches.
- **Evidence:** [Timestamp formatting and summaries][cards], [fetch timestamp][repository], [search timestamp][vm-discovery], [source sections][details].

**F33 · Medium · Privacy information contains an unfinished operator policy and no account-help path.** **Confirmed.**

- **Path:** Profile → Privacy → Contact & retention. The text says the operator must publish contact details and a retention policy before production. Clearing local settings removes saves/diets/reminders, not the server account, submitted reports or encrypted report drafts.
- **Impact:** Users have no concrete contact or retention information and no clear route for account/data questions. A local-clear action must not be mistaken for account deletion.
- **Change:** Replace the production placeholder with the actual operator contact and retention policy before release. Explain local clearing separately from account/report data; provide a real account/data request route. Preserve accurate confirmation of what the existing clear action removes.
- **Evidence:** [Privacy and local-clear copy][privacy], [clear implementation][vm-local], [draft storage][vm-update].

**F34 · Medium · Report actions remain hidden behind the keyboard, even after scrolling.** **Reproduced on the emulator; other form/device variations still need checking.**

- **Path:** On the 360 × 640 dp API 31 review emulator, focus the report evidence field. The keyboard covers Submit/Cancel. Swiping the visible form to scroll further does not move those actions above the keyboard; dismissing the keyboard restores access. Account entry and Map explicitly use `imePadding`; shared Page and the authenticated shell do not.
- **Impact:** Finishing the form requires users to discover and dismiss the keyboard. The action is present in the accessibility hierarchy while visually covered, so hierarchy inspection alone misses the problem.
- **Change:** Apply consistent IME/window-inset handling and enough scrollable bottom space for the final field and actions. Verify profile/password forms separately, including large text. Add appropriate Next/Done actions to multi-field account forms.
- **Evidence:** [Shared Page/shell][shell], [account and password forms][accounts], [report form][report-ui].
- **Device evidence:** [Form after attempted scrolling with keyboard open](evidence/android-20261024/35-report-keyboard-scrolled.png).

**F35 · Medium · Custom controls need a focused accessibility pass.** **Confirmed implementation gaps; user impact needs device checks.**

- **Path to test:** Use TalkBack/switch access to change diets, travel mode, calendar day and programme expansion; repeat at 200% font size and narrow display width.
- **Risk:** `Toggle` places a Text and Switch beside one another without an explicit shared label/row toggle. Route-mode Surfaces have click behaviour but no explicit selected semantics; programme headers lack the expanded-state treatment already used by `ExpandableSection`. Calendar divides the available width into seven cells (about 38 dp each on a 360 dp screen after visible padding/gaps), with single-letter weekdays. Map overlay columns are not generally scrollable, and travel-mode labels use one line.
- **Change:** Use labelled toggleable/selectable groups and explicit selected/expanded state; ensure heading navigation and focus order. Give calendar dates generous targets and unambiguous weekday labels, and provide a readable list alternative. Verify overlays and dialogs at large text.
- **Evidence:** [Switch row][cards], [route modes/map overlays][map], [calendar][calendar], [programme headers][providers], [shared expandable semantics][components].
- **Limit:** This is not a WCAG conformance verdict. Contrast, effective touch areas, SDK map accessibility, actual announcements and large-text clipping have not been measured on a device.

**F36 · Low · Help gives general instructions without returning users directly to their task.** **Confirmed + design judgement.**

- **Path:** Find → How to find support. The page explains three steps and opens SupportGoWhere, but has no direct **Search an address**, **Browse providers**, or contextual search/filter recovery action. Users rely on Back/bottom navigation.
- **Impact:** Someone who needed help must translate instructions into navigation steps themselves.
- **Change:** Add concise task links and a contact-before-travel checklist. Retain the honest statement that FoodBridge cannot arrange emergency support; keep external assistance labelled and handle missing browsers clearly.
- **Evidence:** [Help screen][help].

### Additional findings from the emulator walkthrough

**F37 · Medium · Entry and discovery alternatives are buried by vertical spacing.** **Device-observed + design judgement.**

- **Path:** Open sign-in on a 360 × 640 dp phone at normal text size. Google sign-in, Create an account and resend are below the first screen. On Find, Browse all, Providers and Help are also below the first screen; entering an address inserts another address-search action and pushes the main Find button further down. Saved shows reminder configuration before the first service name is fully readable.
- **Impact:** First-time users may miss registration, non-location browsing and other valid ways to get help. Repeated scrolling adds effort to short tasks.
- **Change:** Reduce unnecessary gaps and repeated framing while retaining readable type and generous targets. Group sign-in alternatives together, keep browse/provider entry points discoverable and lead Saved with saved items. Test the initial viewport rather than judging only a full-page design.
- **Evidence:** [Sign-in first view](evidence/android-20261024/01-signin.png), [alternatives after scrolling](evidence/android-20261024/41-signin-alternatives.png), [Find](evidence/android-20261024/02-find.png), [Saved](evidence/android-20261024/05-saved-top.png); [shared spacing][shell], [account layout][accounts].

**F38 · Medium · The leave-report dialog clips “Continue editing” at normal text size.** **Reproduced on the emulator.**

- **Path:** Enter a report description, then tap another tab. The dialog shows Keep draft and Discard draft with large gaps; Continue editing is cut off at the bottom of the rounded dialog. This occurred at font scale 1.0, not only with enlarged text.
- **Impact:** The action for staying in the form is difficult to discover and operate. Android Back can dismiss the dialog, but users should not need that workaround.
- **Change:** Lay out the three choices as a compact, vertically bounded group with every label and touch area visible. Allow content scrolling when necessary, preserve focus and test at normal/200% text sizes.
- **Evidence:** [Clipped dialog](evidence/android-20261024/11-leave-draft.png); [leave-report dialog source][shell].

**F39 · High · Route overlays cover travel-mode labels and most of the usable map.** **Reproduced on the emulator.**

- **Path:** Open a successful route on the 360 × 640 dp emulator. The bottom route summary overlaps the top mode selector; Drive/Public transport/Walk labels are covered while their icons remain partly visible. The location button also overlaps that area. Tapping the visible Drive icon changes the selection, but does not reveal the labels.
- **Impact:** Users cannot clearly identify or confirm their travel mode, and the map has almost no unobscured area. This affects the ordinary route flow at default font size.
- **Change:** Size overlays against the space remaining after the top controls and system navigation. Keep mode labels and selection permanently visible. Start with a compact route summary; expand full steps/details into a bounded draggable sheet or a separate view. Test long names, warnings and large text.
- **Evidence:** [Route overlay collision](evidence/android-20261024/27-route-preview.png); [map overlay structure][map], [300 dp summary][route-summary]. The route data in this screenshot is explicitly synthetic; the overlap is in the unchanged app UI.

## What if — focused design explorations

1. **What if “ready to travel” were a decision summary instead of a large directions button?** Prototype a visible eligibility/access line, next recorded session, collection-location confidence and contact action, then test whether users can choose an appropriate next step without opening every accordion.
2. **What if Find and Map were two views of one explicit search state?** Keep one labelled address/name/browse scope, matched needs, completed/cleared/error state and restoration policy. Test switching views after zero results, filter edits and external-app return.
3. **What if Saved were a support plan rather than a list with global notification setup first?** Put the saved service/provider first; show next recorded session, next qualifying reminder and a clearly attributed contact. Keep food availability and appointment/collection guarantees explicit rather than inferred.

## Priority actions

1. **Restore continuity and truthful state:** F11–F12, F17–F18 and F27. Distinguish cleared/failed/zero results, retain the selected task, preserve time context and make draft-target changes explicit.
2. **Support safe travel and dependable reminders:** F13, F16, F24 and F39. Make destination confidence and access visible before directions, keep route controls visible, and show whether and when a reminder can actually be scheduled.
3. **Reduce repeated work and restore reachable actions:** F01–F03, F06–F08, F23 and F34/F37–F38. Improve form recovery, consistent Back/filter behaviour, local-data loading and small-screen layouts. Continue the remaining device checks below.

## Device/usability verification checklist

The [follow-up matrix](ANDROID_EMULATOR_REVIEW_2026-10-24.md) records completed emulator coverage. This checklist also includes scenarios still requiring additional devices, external integrations or user research.

| Scenario | What should be observable |
| --- | --- |
| Register, verify, reset; bad input/offline retry | Plain field errors, retained in-memory input, clear return to user sign-in; live email/Google completion tested separately |
| Address/name/A–Z search with strict diets and accessibility | Search scope is clear; suitability filters are never silently relaxed; nearest results retain the ten-result limit |
| Zero-result search → Map/Find → Back/reselect | Real empty results remain distinguishable from never-searched or cleared state |
| Results/detail/route → phone, browser, Settings, background → return | Privacy-cleared location is explained; selected task is recoverable; no false no-match claim |
| Future search → detail/calendar → public transport | Future support context and current route timing remain explicit |
| Multiple services under one provider; coordination office pin | The intended service can be inspected/chosen; an office is not presented as confirmed food collection |
| Bookmark removal/undo and offline catalogue failure | Local saves are visible independently; related reminder effects are understandable |
| Reminder before/after session end, after today's cutoff, no future sessions | Next qualifying delivery or reason for no delivery is visible; Android delays and blocked permissions remain honest |
| A draft → B report; expiry; uncertain send → retry | Target selection is explicit, drafts survive, no accidental target switch or duplicate report |
| TalkBack, keyboard/switch access, 200% text, narrow phone/landscape, IME | Controls have understandable names/states, actions remain reachable, overlays don't hide essential content |

For user validation, use task success as “can identify an appropriate next step or contact route”, not “taps Navigate”. Include people using dietary/access constraints, people unfamiliar with provider names, and people relying on larger text or assistive technology. Do not claim completion-rate improvements until observed.

## Source references

[accounts]: ../../android/app/src/main/java/sg/foodbridge/navigator/AccountScreens.kt
[account-browser]: ../../staff-portal/account/account.js
[account-html]: ../../staff-portal/account/index.html
[manifest]: ../../android/app/src/main/AndroidManifest.xml
[activity]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L119
[shell]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L157
[find]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L302
[filters]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L351
[results]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L415
[cards]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L445
[saved]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L501
[reminder-ui]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L525
[report-ui]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L596
[confirmation]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L624
[help]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L631
[privacy]: ../../android/app/src/main/java/sg/foodbridge/navigator/MainActivity.kt#L575
[vm-update-name]: ../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt#L205
[vm-update]: ../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt#L215
[vm-refresh]: ../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt#L253
[vm-nav]: ../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt#L264
[vm-report]: ../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt#L278
[vm-search]: ../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt#L309
[vm-location]: ../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt#L358
[vm-local]: ../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt#L365
[vm-discovery]: ../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt#L397
[vm-submit]: ../../android/app/src/main/java/sg/foodbridge/navigator/AppViewModel.kt#L527
[suggestions]: ../../android/app/src/main/java/sg/foodbridge/navigator/SearchSuggestions.kt
[map]: ../../android/app/src/main/java/sg/foodbridge/navigator/FoodMap.kt#L49
[route-summary]: ../../android/app/src/main/java/sg/foodbridge/navigator/FoodMap.kt#L255
[map-render]: ../../android/app/src/main/java/sg/foodbridge/navigator/FoodMap.kt#L433
[calendar]: ../../android/app/src/main/java/sg/foodbridge/navigator/ServiceDetails.kt#L117
[details]: ../../android/app/src/main/java/sg/foodbridge/navigator/ServiceDetails.kt#L188
[providers]: ../../android/app/src/main/java/sg/foodbridge/navigator/Providers.kt
[providers-detail]: ../../android/app/src/main/java/sg/foodbridge/navigator/Providers.kt#L212
[components]: ../../android/app/src/main/java/sg/foodbridge/navigator/UiComponents.kt
[repository]: ../../android/app/src/main/java/sg/foodbridge/navigator/Repository.kt
[reminders]: ../../android/app/src/main/java/sg/foodbridge/navigator/Reminders.kt#L127
[backend-detail]: ../../backend/app/main.py#L151
[backend-search]: ../../backend/app/main.py#L167
[backend-report]: ../../backend/app/main.py#L253
[domain]: ../../backend/app/domain.py
