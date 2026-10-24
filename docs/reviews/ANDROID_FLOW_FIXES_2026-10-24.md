# Android flow fixes — 24 October 2026

Implementation follow-up to the [39-finding review](ANDROID_USER_FLOW_REVIEW_2026-10-24.md) and [original emulator walkthrough](ANDROID_EMULATOR_REVIEW_2026-10-24.md). All 39 findings have code changes. This is an implementation and expert verification record, not a claim that every real-world integration or assistive-technology scenario has been tested.

## Issue-by-issue changes

| Finding | Resulting behaviour | Implementation |
| --- | --- | --- |
| F01 | Passwords remain in memory after failed requests; successful account transitions dispose of them. Browser reset keeps input on failure. | AccountScreens.kt; account/account.js |
| F02 | Safe server validation maps to individual fields, explains the correction and focuses the first invalid field. Secret input is never echoed in errors. | AccountScreens.kt; Repository.kt; backend validation handler |
| F03 | Verification/reset pages provide an explicit Return to FoodBridge link and fallback instructions. The custom URI returns to sign-in and never authenticates or claims verification by itself. | Manifest; MainActivity.kt; account browser pages |
| F04 | Basic help, official external assistance and privacy/account help are accessible before sign-in. Discovery remains authenticated. | AccountScreens.kt; MainActivity.kt; public privacy API/page |
| F05 | The primary Find action explicitly searches near an address. Name suggestions/actions disclose their separate meaning; Map provider selection remains a provider selection. | MainActivity.kt; SearchSuggestions.kt; FoodMap.kt; AppViewModel.kt |
| F06 | Detail navigation records its origin. Back from a saved provider returns to Saved; directory query/category and list positions survive navigation. Results Back edits the search, including zero-result searches. Screen scroll states are retained by screen and listing. | MainActivity.kt; AppViewModel.kt; Providers.kt; SearchSuggestions.kt |
| F07 | Filters use a temporary draft. Apply commits; Cancel, Back and swipe dismissal discard edits. Search diets are separate from saved profile diets. Reset affects the draft only. | MainActivity.kt; AppViewModel.kt |
| F08 | Selecting a preferred time visibly enables the operating-hours constraint. Explanatory copy states the coupling; turning it off clears the preferred time. | SearchFiltersSheet; applySearchFilters |
| F09 | Zero results show the active area/type/diet/time/access constraints, explain that unknowns cannot satisfy confirmation filters and open filters directly. No filters are silently relaxed. | ResultsScreen |
| F10 | Cards include the service address/location role, confirmed dietary options or explicit unknown, and the next recorded session. | ServiceCard; nextSessionLabel |
| F11 | Leaving the foreground still clears private origins and routes. Public destination/detail selection and report task remain available, with an explanation and a way to set the starting location again. | AppViewModel.clearLocation; map recovery |
| F12 | Idle, searching, failed, cleared and completed search states are distinct. A privacy-cleared search returns to editing rather than becoming a false zero-match result. | UiState; AppViewModel; shell/map |
| F13 | Confirmed service locations and coordination/unconfirmed addresses use different marker shapes/symbols; availability adds independent symbols and accessible descriptions. | FoodMap.kt |
| F14 | Map shows general-listing versus matched-result scope, result count, distance ranking, active filters and a marker key. | FoodMap.kt |
| F15 | A provider name preview offers provider information and linked service choices; it never picks the first service automatically. | FoodMap.kt; Providers.kt |
| F16 | Details present eligibility/access, next session, location role and provider contact before secondary directions. | ServiceDetails.kt |
| F17 | A selected future instant is carried to detail retrieval; the backend recomputes opening and availability at that instant from fresh data. Calendar selection starts on that date. Routes explicitly depart now and do not promise arrival for a future session. | AppViewModel; ServiceDetails.kt; FoodMap.kt; GET service details |
| F18 | Details show refresh progress, an honest cached-information error with retry, and a withdrawn-listing warning. Withdrawn/inactive listings disable directions. | AppViewModel.detail; DetailScreen |
| F19 | Missing service phone/website fall back to the linked provider, with attribution; email and the provider page are available. | ServiceDetails.kt |
| F20 | Provider pages list linked active services individually for inspection and destination choice. | Providers.kt |
| F21 | Location recovery offers app permission Settings, device location Settings and manual address entry. Repeated permanent-denial requests have a recovery path. | FindScreen; FoodMap.kt |
| F22 | Successful and failed route calculations can hand off to a navigation app, with the chosen mode and origin/destination. | MapRouteSummary |
| F23 | Account-scoped saved IDs, diets, reminder settings and report receipts hydrate before the catalogue network request. Missing listing data remains explicit. | AppViewModel.refresh; Repository.kt; SavedScreens.kt |
| F24 | Enabled reminders show the next qualifying time or why no alarm can be scheduled, today’s cutoff and a shared-time edit action. The preview and alarm scheduler use the same calculation. | Reminders.kt; SavedScreens.kt |
| F25 | Saved begins with saved content. Reminder settings appear beside eligible saved services; global configuration appears only when relevant. | SavedScreens.kt |
| F26 | Removing a bookmark offers Undo, restoring both the listing and its prior reminder setting for the same account. | AppViewModel; ReminderStore; BookmarkUndo |
| F27 | Reporting a different listing while a draft exists explicitly asks whether to resume the original or discard it and start for the new target. | AppViewModel; ReportTargetDialog |
| F28 | Long pasted text is retained and marked over limit. Required fields and complete evidence URLs are validated. Sending disables editing and states that the app is awaiting a reference. | ReportScreens.kt; AppViewModel |
| F29 | A receipt contains target, category, submission time and copyable reference. Encrypted account-scoped local history remains accessible from Profile and Saved. It does not claim to show staff review status. | ReportScreens.kt; Repository.kt; AppViewModel |
| F30 | A UUID submission identity is persisted with the draft before sending. Unchanged retries reuse it; edits create a new identity. The backend serializes duplicate sends per account and returns the original receipt, including after target retirement. | AppViewModel; Repository.kt; POST reports |
| F31 | A successful display-name save closes the editor and returns to the updated profile. | AccountScreens.kt |
| F32 | Human-readable dates include the year. Local download/fetch time, source verification, import time, availability observations and the instant at which status was checked have separate labels. | MainActivity.kt; ServiceDetails.kt; Providers.kt; Reminders.kt |
| F33 | A public configurable operator/privacy endpoint and browser page back the in-app account-help section. At the user’s request, absent development configuration uses conspicuously labelled test email/policy/retention values. Production never substitutes these placeholders. | MainActivity.kt; backend privacy API; account/privacy.*; setup/config |
| F34 | Shared pages handle IME insets and scrollable forms; Android uses resize keyboard behaviour. Report Submit and Cancel can scroll above the keyboard; Next/Done support field progression and keyboard dismissal. | MainActivity.kt; Manifest; AccountScreens.kt; ReportScreens.kt |
| F35 | Labelled toggle rows expose switch semantics, mode controls expose selection, expanded sections expose state and calendar dates have larger targets plus a date-list alternative. | MainActivity.kt; UiComponents.kt; SavedScreens.kt; ServiceDetails.kt; FoodMap.kt |
| F36 | Help links directly to nearby search and provider browsing for signed-in users, plus official external assistance for everyone. | HelpScreen |
| F37 | Denser shared spacing retains readable text/touch sizes. Find puts Browse A–Z, Providers and Help near the title. Sign-in places Google, account creation and recovery closer to the main form. | MainActivity.kt; AccountScreens.kt; SavedScreens.kt |
| F38 | Leave-report and draft-target choices use one bounded scroll container with full-width actions. Continue editing is reachable at normal and large text sizes. | ReportScreens.kt |
| F39 | Travel modes, a weighted map and a compact scrollable summary occupy separate layout regions. Full route details open in a bounded sheet, so they cannot cover the modes. | FoodMap.kt |

Filenames above are in `android/app/src/main/java/sg/foodbridge/navigator/` unless a browser/backend path is specified. The original review’s line references describe its original source revision; use current symbols for the fixes.

## Verification

- Android debug build: passed with the local Java 17 / Gradle 8.13 toolchain. The review build uses package `sg.foodbridge.navigator.review` and an isolated API; the standard debug output is rebuilt with the normal application ID/API afterwards.
- Account acceptance: **74 passed**.
- New flow-recovery acceptance: **22 passed**, including production-safe privacy defaults, safe field errors, future detail OPEN/CLOSED and availability expiry, concurrent duplicate submission, per-account isolation, edited-payload conflict and replay after target retirement/assessment.
- Staff acceptance: **38 passed**, including authenticated access, validation, atomic audit rollback, stale version handling, report assessment and account migration.
- General backend acceptance: **30 passed**. Local 2,000-service/100-request/concurrency-20 search p95 was **461.21 ms**. An earlier 550.97 ms run overlapped emulator startup; the repeat ran without that contention. This is isolated SQLite evidence, not production performance.
- Routing acceptance and listing-review acceptance: passed using disposable databases and mocked upstream routing.
- Account browser JavaScript syntax and diff whitespace: passed. Kotlin sources are valid UTF-8.
- Reminder runtime: after changing the synthetic daily 06:00–07:00 service from 08:00 to 06:00, Android's active alarm list contains a `sg.foodbridge.REMINDER` alarm. This confirms scheduling, not eventual delivery timing.

Device verification uses Android 12 / API 31 at 1080×1920, density 480 (360×640 dp), synthetic account/listings and a separate SQLite API. It exercises address search/results/detail, Apply versus Cancel filters, contextual zero results, draft restoration after reinstall, explicit draft target choice, malformed evidence correction, report sending/copy/history, location Settings/manual recovery, route modes/details, 200% text with scrollable report choices, Saved hierarchy, unavailable reminder explanation/shared-time editing, bookmark Undo, saved-provider Back with scroll restoration, successful profile editor completion, failed-sign-in password retention and privacy access before sign-in. Evidence is under [the fix screenshots](evidence/android-fixes-20261024/README.md).

## Practical limits

Real email delivery, Google sign-in consent, live geocoding/routing accuracy, actual delayed notification delivery and a TalkBack walkthrough are not proven by this run. The emulator uses synthetic route/geocode responses; map tiles can render, but that does not validate directions. Accessibility semantics/source and large-text screenshots are narrower evidence than a complete accessibility certification.

The operator test placeholders are suitable for interface testing only. Production requires real monitored contact information and approved policy/retention text. The return link is a credential-free custom scheme, not a verified-domain Android App Link. The app still clears private location context on backgrounding; restoring the public task does not retain the private origin.

No live database, real account, credential or persistent Docker volume was reset. No production deployment was performed. Existing unrelated Compose environment settings, diagrams, SSH files and temporary user files were preserved.
