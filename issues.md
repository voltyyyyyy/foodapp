# Issues

## UX audit follow-up — 17 October 2026

### Current verdict

The original walkthrough found several high-impact flow and trust problems. Most code-level findings have since been addressed. The remaining open items are the account gate, representative-user testing, and a real TalkBack pass.

### Open issues

#### 1. Sign-in still blocks discovery

**Severity:** High · **Type:** Product decision

People must register, verify their email and sign in before they can search or inspect service details. That can delay someone who needs food support. Read-only search and details for visitors, with sign-in required for bookmarks, reports and reminders, would lower this barrier.

The SRS and repository guide currently require sign-in for discovery. Keep this as an explicit product decision: either retain the gate and validate it with seekers, or revise the access rule to permit read-only discovery. It was not changed as part of the code fixes.

#### 2. First-use success target has not been measured

**Severity:** High · **Type:** Usability validation

SRS 5.4.1 requires at least 80% of five or more representative signed-in users to complete a location-and-filter search within two minutes without help. No participant study has been run, so the target remains unverified.

#### 3. TalkBack verification remains outstanding

**Severity:** Medium · **Type:** Accessibility validation

Status banners now expose a polite live region, availability evidence uses body text sizing, and the map retains a list path. The available Android 12 emulator has no TalkBack service installed, so announcements, focus order and touch exploration could not be checked with a screen reader.

### Completed fixes and review results

| Finding | Current status |
| --- | --- |
| Account requirement was not explained before registration | The account requirement and email-verification return path are explained on account-access screens. The sign-in gate itself remains the product decision above. |
| Administrator sign-in had no staff destination | Android now links administrators to the browser staff portal from Profile and Home. |
| Saved reminders were missing | Saved now has a shared daily Singapore time, per-service enable controls, schedule and permission states, local-cache freshness, account separation and reminder lifecycle handling. |
| Opening hours and distribution sessions were conflated | Current data and staff forms distinguish operating hours from collection/meal sessions; reminders use only approved session schedules. |
| Availability evidence was hidden from result comparison | Result cards now show source, observation time, validity limit and confidence beside the availability label. |
| Route failure lost service context | The route summary retains the service name, eligibility, availability evidence, contact actions, location and a retry path. |
| Report copy implied anonymity | The report flow now states that reports are linked to the signed-in account for staff review. |
| Leaving a report discarded the draft | Drafts are retained locally by account; leaving offers keep, discard or continue editing, and a resume action is available. |
| Blank-origin browsing silently ignored filters | The Find screen states before browsing that A–Z browsing does not apply suitability filters and separates it from matching search. |
| Past preferred times and ambiguous status labels | Past selections are rejected; results say “Open at selected time” and include the selected time. The time control is now placed earlier in the filter sheet, with a scroll cue for further options. |
| Clearing local data was immediate | A confirmation explains which local bookmarks, reminder settings and dietary preferences will be removed. |
| Staff edits could publish without review | The portal compares current and proposed values, requires a reason and has an explicit publication confirmation. |
| Staff exceptions and availability required raw JSON | These now have structured controls; the original JSON remains an optional expert reference. |
| Staff queues required horizontal scrolling on phones | Narrow layouts render rows as stacked cards with labelled fields and usable actions. |
| Dynamic announcements and small metadata were inaccessible | Banners use polite live-region semantics; evidence uses readable body text; map results remain reachable through the list. Screen-reader behavior still needs a real TalkBack pass. |
| A route button did not identify its destination | Service buttons now say whether directions lead to a collection site, coordination site or merely the listed address. Provider buttons distinguish provider addresses and service locations. |
| Important search filters were below the initial filter-sheet viewport without a cue | Preferred time now appears after support type, and the sheet tells users to scroll for dietary, time and access filters. |

### Verification scope and limits

- Reviewed current Android, backend and staff-portal source against the supplied SRS and repository guide.
- Visually checked the Android 12 emulator at the default font scale and temporarily at 1.3×. The filter sheet remained scrollable; the time and access controls were visible after scrolling. The system font scale was restored afterward.
- Checked the sign-in and registration screens before the scroll-reset change. The shared scroll state was reset on auth-page changes and the Android build passed; the signed-in emulator session was preserved, so those screens were not reopened after the final patch.
- The emulator has no TalkBack service. No five-person usability study was conducted.
- Docker health, backend/account/staff acceptance suites and Android build had passed during this task. The staff portal was reviewed from source and its HTTP endpoints were checked; a live browser layout pass was unavailable.
