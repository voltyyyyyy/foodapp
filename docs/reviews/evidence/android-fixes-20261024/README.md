# Fix verification screenshots

Android 12 / API 31; 360×640 dp; default font scale except files explicitly marked `large` (200%). Synthetic review account and listings only. These captures document the implementation walkthrough and its iterations. They are evidence for the named behaviour, rather than proof of every state in the final app or of real upstream services.

| Capture | Behaviour checked |
| --- | --- |
| [Find](final-find.png) | Browse A–Z, Providers and Help are visible near the title; address search has an explicit action |
| [Sign-in layout](final-signin-clean.png), [failed sign-in](final-signin-failure.png) | Entry alternatives stay closer to the form; a separate XML assertion verified that the masked password remained populated after failure |
| [Public privacy](final-public-privacy.png), [test policy](final-privacy-placeholder.png) | Privacy is accessible before sign-in and sample operator values are visibly labelled |
| [Name saved](final-name-saved.png) | A successful save closes the display-name editor |
| [Cancel filters](fixed-filters-cancel.png), [Apply filters](fixed-filters-apply.png) | Cancel retains Across Singapore; Apply changes the effective radius to 1 km |
| [Zero results](fixed-zero-results.png) | Vegan/radius constraints stay visible; zero does not imply filters were relaxed |
| [Saved position](final-saved-position.png) | Back from a saved provider returns to Saved at its previous scroll position |
| [Undo](fixed-bookmark-undo.png), [restored](fixed-bookmark-restored.png) | Removal offers Undo and restores the reminder setting with the bookmark |
| [Reminder unavailable](fixed-reminder-unavailable.png) | The 08:00 reminder cannot qualify for a daily 06:00–07:00 session; the reason, cutoff and time edit action are explicit |
| [Keyboard actions](fixed-report-keyboard-bottom.png), [final resize-mode Cancel](final-report-keyboard-actions.png) | Submit and Cancel can scroll above an open keyboard; the later capture also verifies the explicit resize keyboard mode |
| [Evidence correction](fixed-evidence-error.png) | A malformed evidence URL is retained with field guidance |
| [Receipt copy](fixed-receipt-copy.png), [history](fixed-receipt-history.png) | Copy feedback and an account-local submission history |
| [Draft target choice](final-draft-target-choice.png) | The existing service draft and requested provider target are named explicitly |
| [200% dialog](final-leave-large-actions.png) | All three leave-report choices are reachable by scrolling |
| [Location recovery](fixed-location-recovery.png), [Find after denial](final-find-location-settings.png) | App Settings and manual address entry are available; Find presents an explicit Settings action after denial |
| [Map scope](fixed-map-matches.png) | Match count, ranking, filter scope and differentiated location markers |
| [Route modes](fixed-route-modes.png), [200% route](fixed-route-large-clear.png) | Travel modes occupy their own region above the map; the summary cannot overlay them |

The original review evidence remains in `../android-20261024/`. See the [fix register](../../ANDROID_FLOW_FIXES_2026-10-24.md) for checks and limitations. Actual email/Google consent, real route accuracy, delayed notification delivery and TalkBack are outside this evidence.
