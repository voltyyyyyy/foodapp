# Android emulator follow-up — 24 October 2026

**Implementation follow-up:** The [fix register](ANDROID_FLOW_FIXES_2026-10-24.md) records changes and subsequent device checks. Screenshots below preserve the original review state.

**Verdict: Needs work.** The device walkthrough reproduced core continuity, reporting, reminder and navigation problems, and found three additional small-screen issues. The [full issue register](ANDROID_USER_FLOW_REVIEW_2026-10-24.md) now contains **39 findings: 9 High, 28 Medium and 2 Low**.

## Test environment and limits

- **Device:** Connected `FoodBridge_Review31`, Android 12/API 31, `emulator-5556`, ADB server port 5038. Portrait 1080 × 1920 px at 480 dpi: **360 × 640 dp**. Default font scale 1.0; the Map starting-location sheet was also inspected at 2.0 and then restored to 1.0. The fresh emulator's status-bar clock uses UTC; app support/reminder times use Singapore time.
- **App:** Current Kotlin source at `96ef02e`, built successfully as separate package `sg.foodbridge.navigator.review`, version 1.0.0. Only build configuration changed the application ID and API base URL. The normal installed app was not replaced. Review APK SHA-256: `7C2D53B6C04DA7BAB28BA998A08FDE5D24C79FDCD364475AAA6616F4AACFD8A2`.
- **Data:** One synthetic registered user, two synthetic providers and three synthetic services, in an isolated SQLite database served on host loopback port 18766. All listing names start with Review. One synthetic correction report was submitted, the fixture display name was edited, and fixture bookmarks/reminder choices were exercised. None of these are real support records.
- **External services:** Geocoding and route responses were deliberately deterministic fixture adapters. The app's Google Maps view and markers rendered; this does not validate real geocoding, directions accuracy, transit timings or food availability. SMTP/Google sign-in were unconfigured in the fixture; their error/recovery UI was exercised, not live delivery or consent.
- **Method:** Actual taps, text input, swipes, Back/tab navigation, screenshots, Android accessibility-hierarchy inspection and filtered alarm inspection. Unedited screenshots are retained under [evidence/android-20261024](evidence/android-20261024/01-signin.png). This is an expert walkthrough, not a representative-user study or full accessibility audit.
- **Isolation:** The previously running emulator remained offline and was not wiped/reset. The existing Docker stack was inspected and left running without rebuilding it. No live accounts, listings, reports, credentials or persistent volumes were changed. The normal default debug APK output was rebuilt after retaining the review APK separately.

## Most important device findings

| Priority | Finding | What happened on the emulator | Evidence |
| --- | --- | --- | --- |
| High | **F12 — False no-match claim after backgrounding** | A completed search showed three results. After Home, waiting for the activity to stop, and reopening the same app, it showed “0 of 0 / No confirmed matches” without a new search. | [Before](evidence/android-20261024/03-results.png), [after](evidence/android-20261024/04-return-false-empty.png) |
| High | **F27 — Reporting B restores A's target** | Kept a draft for Review Morning Meals; opened Review Delivery Coordination Office and tapped Report an issue. The form said “For Review Morning Meals” and restored its description. | [Listing B](evidence/android-20261024/13-coordination-detail.png), [restored report](evidence/android-20261024/14-wrong-draft-target.png) |
| High | **F24 — Enabled reminder with no qualifying service alarm** | For a daily 06:00–07:00 service, enabled an 08:00 reminder. The UI said “Around 08:00 on session days”; Android showed only the app's `REMINDER_EXPIRY` alarm and no service-reminder alarm. | [Enabled UI](evidence/android-20261024/07-reminder-enabled.png), [alarm observation](evidence/android-20261024/observations.json) |
| High | **F39 — Route overlays hide the mode labels** | The route summary covered the lower part of the travel-mode selector. Icons were partly visible, labels were hidden, and the map had almost no clear area. Tapping the visible Drive icon changed its selection but did not resolve the overlap. | [Route preview](evidence/android-20261024/27-route-preview.png) |
| Medium | **F38 — Continue editing is clipped** | Leaving a filled report opened a dialog whose Continue editing label was cut off at its bottom edge at font scale 1.0. | [Dialog](evidence/android-20261024/11-leave-draft.png) |
| Medium | **F34 — Keyboard hides report actions** | With the evidence field focused, Submit/Cancel sat behind the keyboard. Further swiping the visible form did not move them into view. Android Back dismissed the keyboard and restored access. | [After attempted scroll](evidence/android-20261024/35-report-keyboard-scrolled.png) |
| Medium | **F06 — Saved provider returns to the wrong destination** | Opened a bookmarked provider from Saved and pressed Back. Providers appeared while Saved remained the selected bottom tab; the earlier directory query was gone. | [Returned page](evidence/android-20261024/22-saved-provider-back.png) |
| Medium | **F01/F02 — Account errors force retyping and expose raw validation structure** | Invalid synthetic registration cleared both password fields, retained name/email, and displayed validation JSON rather than a field error. An invalid report link similarly produced JSON while retaining its draft. | [Registration](evidence/android-20261024/43-registration-validation.png), [report](evidence/android-20261024/15-report-validation.png) |
| Medium | **F07 — Dismissal commits filters** | Selected Hot meals, then used Android Back without pressing Done. The Find summary retained Hot meals. | [Sheet](evidence/android-20261024/31-filters.png), [after dismissal](evidence/android-20261024/32-filter-dismiss.png) |
| Medium | **F37 — Important alternatives are below the first viewport** | Google sign-in/Create an account required scrolling. Find's browse/provider/help routes and Saved's first service identity were also pushed down by framing and configuration. | [Sign-in](evidence/android-20261024/01-signin.png), [after scroll](evidence/android-20261024/41-signin-alternatives.png), [Find](evidence/android-20261024/02-find.png), [Saved](evidence/android-20261024/05-saved-top.png) |
| Low | **F31 — Successful name save still looks like editing** | The account name updated, but the editable field and Save/Cancel remained. | [Saved name/editor](evidence/android-20261024/18-name-saved-editor.png) |

F37–F39 are new additions to the original review. Other rows confirm or strengthen existing findings rather than adding duplicate issues. F11 was directly demonstrated through backgrounding; the same location-clearing lifecycle is used when a call/browser takes the app out of the foreground, but a completed phone call was not separately exercised.

## I like — behaviours that held up

- **Report recovery after ordinary validation errors:** The invalid evidence URL was rejected, category/description/evidence were retained, and correcting the URL produced a report reference. The report was stored only in the isolated database. [Error](evidence/android-20261024/15-report-validation.png), [receipt](evidence/android-20261024/16-report-sent.png).
- **Denied location has a usable alternative:** Android's permission dialog was declined, and manual address entry successfully produced the three fixture matches. This checks the first-denial path, not permanent denial or device GPS accuracy.
- **Browse preserves its distinct purpose:** With Hot meals selected, A–Z still showed both providers and all three services and explicitly said filters were not applied. [Browse screen](evidence/android-20261024/39-browse-all.png).
- **Location warnings remain visible:** A provider without a confirmed collection point showed a clear warning. The concern remains the competing directions hierarchy, not an absence of warnings.
- **Clear local settings is explicit and cancellable:** The confirmation named saves, reminders and diets, and Cancel left them in place. [Confirmation](evidence/android-20261024/38-clear-confirmation.png).
- **Unavailable integrations fail honestly:** The fixture's recovery screen retained its email and showed “Account email is unavailable”; Google sign-in offered email/password as the alternative. [Email failure](evidence/android-20261024/45-recovery-unavailable.png), [Google failure](evidence/android-20261024/47-google-unconfigured.png). These are deliberate fixture limitations, not findings that the user's real integration is misconfigured.

## Coverage and remaining gaps

| Area | Actually exercised | Still unverified |
| --- | --- | --- |
| Launch/session | Fresh signed-out launch, email sign-in, background/return, sign-out | Remote revocation/expiry and same-account reauthentication in this run; previous verification notes are separate evidence |
| Sign-in/registration | Fields, password visibility affordance, scrolling, invalid-registration submission and retained/cleared input | Successful real email registration, lockout, autofill/password managers |
| Google | Entry and fixture-unconfigured error | Live chooser, real consent/account creation/collision |
| Recovery/verification | Forgot-password request screen, unavailable-mail error, resend screen, return to sign-in | Actual mailbox delivery, browser link completion, expired/used-link recovery |
| Discovery | Address search, organisation suggestion selection, three-result list, result/details/tab transitions, A–Z | More than ten matches, ambiguous addresses, real address lookup, strict zero-match filter combinations |
| Filters | Normal-size sheet, type selection, Back dismissal and retained summary | Full preferred-date flow, dietary AND combinations, wheelchair/radius combinations on-device |
| Map/location | Maps canvas/markers, three identical marker styles, initial scope label, manual origin, denied permission and list switch | Permanent denial, accurate/approximate device fix, pan/search-area interactions and provider-with-many-locations map suggestion |
| Directions | Successful synthetic route preview, visible Drive-icon selection, route-overlay layout, absence of external-nav button on success | Live walk/drive/transit results, current versus future departure consistency, external navigation handoff and route-failure recovery |
| Service details/calendar | Saved and matched service details, collapsed sections, collection calendar and report entry | Every schedule exception/overnight case and future-search context; withdrawn-record refresh |
| Providers | Directory, query, provider detail, bookmark, Saved detail and Back | Every programme/application link, real contact/browser handlers and no-linked-location variants |
| Saved/reminders | Service/provider saves, reminder enablement, shared default time, actual scheduled-alarm state | Notification delivery, API 33+ permission request, time-edit delivery, disabled notification channel, missing/withdrawn listing and fresh-login/no-cache outage |
| Reports | Category menu, description/evidence, keep/discard dialog, cross-listing draft restore, invalid link/retry, receipt | Process death, session-expiry recovery, uncertain network outcome/idempotent retry and 1,000-character paste |
| Profile/privacy | Name edit/save/cancel, password-change form inspection, privacy page, local-clear confirmation/cancel, sign-out | Successful password change/global revocation; actual operator retention/contact content and data-request flow |
| Help | Help page and navigation back to other tasks | Completing the external SupportGoWhere journey or missing-browser handler |
| Accessibility/layout | Default small portrait layout, 200% text on the Map start sheet, keyboard coverage on reports, hierarchy inspection | TalkBack audio/focus, switch/keyboard navigation, contrast measurements, landscape, larger devices, API 35 edge-to-edge and 200% text across every page |

The hierarchy marked the reminder switch as checkable/clickable with empty text/content description and `NAF="true"`; this supports F35's naming concern. It does not substitute for listening to TalkBack. Screenshots were assessed alongside the hierarchy: the hierarchy still exposed Submit/Cancel underneath the visible keyboard, illustrating why source/tree checks alone are insufficient.

## Corrections to the first pass

- The backend already sanitises validation errors to location/message/type. F02 has been corrected: the verified issue is raw technical presentation and missing field association, not submitted-password exposure.
- The service schema does not include an email field. F19 now accurately describes the missing fallback to a linked provider's contact/email, rather than claiming the service screen omits a supported service-email field.
- F34 is no longer merely a possible IME issue: it was reproduced specifically on the report form. Other forms remain separate device checks.
- A just-issued Home event followed by an immediate reopen did not trigger the lifecycle clearing. Allowing the activity to fully stop reproduced F12. The recorded result uses the completed background transition, not the immediate-reopen attempt.

## What if — the next design iteration

1. **What if route mode stayed fixed while only a compact summary expanded?** Prototype the top controls plus a bottom sheet with a usable minimum map area; test the same small viewport before adding full journey details.
2. **What if every interrupted task had an explicit recovery state?** Treat “location cleared”, “draft restored for another listing” and “no reminder qualifies” as visible decisions with a next action, rather than reusing zero-results, editing or enabled states.

## Priority actions

1. Fix truthful state/target handling: **F12, F27 and F24**.
2. Fix unreachable/covered controls on the supported small-screen layout: **F39, F38 and F34**.
3. Then improve navigation/form recovery and first-screen hierarchy: **F06, F01/F02, F07 and F37**; validate with users who need larger text or have dietary/access requirements.

No fixes were implemented as part of this review. The evidence makes the proposed work concrete for a subsequent design/implementation pass.
