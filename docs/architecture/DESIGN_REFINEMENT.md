# FoodBridge minimalist redesign

2 October 2026. Requested direction: cleaner, less cluttered, smoother to use. Guided by the installed `frontend-design` skill from anthropics/skills.

## Audience and job

A Singapore resident should be able to find suitable food assistance without an account or a long form. The interface should make location, mandatory needs, and provider contact clear. Calm visual presentation must preserve uncertainty about stock, opening hours, eligibility, and fictional data.

## Design tokens

| Role | Value | Use |
| --- | --- | --- |
| Canvas | `#FAFAF8` | Nearly white page and sheet backgrounds |
| Surface | `#FFFFFF` | Individual service results and form groups |
| Action | `#00535B` | Main action, selection, recognizable leaf mark |
| Text | `#182D31` | Headings and body |
| Secondary text | `#617175` | Supporting information |
| Divider | `#E4E8E5` | Separate distinct sections |

Status colors remain semantic: mint for reported availability, peach for limited availability and caution, pale red for reported unavailability. Status wording is always present.

Typography: bundled Atkinson Hyperlegible regular/bold, selected for distinguishable letterforms and readable assistance information. Headline 32/37sp; section heading 26/32sp; subheading 20/27sp; body 16/24sp and 14/21sp. Android font scaling remains enabled. Font license: `docs/licenses/AtkinsonHyperlegible-OFL.txt`.

Layout: left aligned content, 24dp page gutters, 24dp section rhythm, 48dp minimum interactive targets. Rounded service containers distinguish selectable records; detail sections use spacing and dividers instead of nested cards.

## Layout choices

The original screen showed a large brand header, a persistent demo banner, a promotional headline, and many filter chips inside a long card. The revised home screen gives location and search the strongest emphasis.

```text
leaf / foodbridge                      Demo data

Find food support
near you.
Short description

[ Postal code or address                  ]
Use current location
-------------------------------------------
Search filters                         >
Current criteria summary
-------------------------------------------
[ Find support                            ]
Short privacy note
Need help getting started?

Find              Map          Saved        Settings
```

Rejected approach: keep all chips visible and shrink their text. That would preserve visual overload and make mandatory dietary choices harder to read. Instead, a native modal sheet holds distance, service type, diets, wheelchair, operating-time and preferred-time controls, with a fixed Done action. Current criteria remain visible on home and results.

Results retain name/type/distance, explicit availability and hours, evidence timestamp when present, a matching explanation, asynchronous ETA or unavailable state, and a bookmark. Description and full evidence live in details. Stable IDs and `LazyColumn` defer composing offscreen result rows. Returning from Map to Find preserves the results screen.

Details show contact before eligibility/location information. Hours and verification expand in response to a tap; there are no automatic decorative animations. Mandatory information remains reachable. Android correction categories use a menu rather than a long row of competing choices. Staff work lives in the separate browser portal, with collection navigation, searchable lists and dedicated edit forms.

## Review and limits

The first screenshot critique removed excess spacers that pushed help below the fold. The large colored demo banner became a compact header action opening an explanation; fictional records retain a visible Demo label and a travel warning. The final pass bundles the readable font and removes unnecessary card borders from detail/settings sections. A further result-screen critique tightened supporting metadata into a grouped block with explicit small-text line heights, so cards can be scanned with less scrolling.

This redesign changes presentation and navigation, not provider facts, dietary matching semantics, authentication, privacy rules, or backend workflows. Compilation and screenshot review are implementation evidence; they do not establish the representative-user usability NFR or measured frame-time improvement.

## Map-first refinement — 3 October 2026

Audience/job: a seeker should explore nearby food support without leaving the map to enter an address. Retain mandatory suitability filters and explicit uncertainty.

Layout: full native map under a left-aligned floating search field, one filters control, a location action and a compact bottom summary/selected-service preview. Preserve the bottom navigation. The app header is omitted only on the map exploration page. Rejected: a separate introductory Map page or search form that hides the map.

Tokens: land #FAFAF8, water #BAD9DE, parks #DFEADF, support markers #00535B, controls #FFFFFF, location #276BE8. Native labels remain legible; unrelated POIs and 3D objects are suppressed. Atkinson Hyperlegible continues in Compose controls. Mapbox renders its own cartographic labels. Meaning is carried by text and marker detail, not color alone.

Implementation: native Mapbox Android SDK 11.32.0 with 16 KB-compatible ndk27 artifacts; bundled FoodBridge Quiet style imports Mapbox Standard with custom configuration. OneMap geocoding and route adapters stay server-side. Search by address, device location or map center; no filters silently relaxed. Editing criteria clears stale pins; a completed empty search is distinguished from initial exploration. Native attribution/logo/telemetry choices retained. Mapbox public token read from ignored local.properties or environment at build time; no secret token included.

Native custom map rendering confirmed in the emulator. Compilation and visual review are implementation evidence; usability and performance targets still need representative measurements.

### Saved services in map search

User requested saved results beneath the map search field on tap. Added an anchored native dropdown when the search field gains focus: saved service name/type, demo label, empty and removed-record guidance. Tapping a saved service dismisses the keyboard, centres the map and opens its preview. Saved services outside current matches are explicitly labelled suitability-not-checked. Dropdown is scrollable, and bottom map preview hides while the field is focused to reduce clutter. No search history or coordinates are persisted.

## Nearest10 and provider readability - 3 October 2026

Find starts with an address and Across Singapore search area, then returns up to10 nearest qualifying services. Results explain that distance is straight-line; counts distinguish displayed matches from all qualifying matches. Filters remain explicit and are never silently relaxed.

Provider list rows use teal support-type icon badges, a location row and concise review status. Detail pages group Contact, Getting support, Address and Listed programmes with labelled icons. Programme cards expand independently; the first opens initially. Recipients, delivery methods, regions, dietary options and dates have separate readable label/value rows. Source notes are collapsed to reduce repetition while preserving original information. Icons supplement text rather than replacing it.

Build21721 succeeded; APK installed and list/detail/programme screenshots visually reviewed in the emulator. User preferences were preserved. No automated tests or frame-time measurements were run.

## Result details, reporting and name search - 3 October 2026

References reviewed: harmony_community_kitchen, food_distribution_calendar and report_issue_foodbridge_navigator (screenshots and HTML). Keep the existing teal, mint, warm canvas and Atkinson Hyperlegible tokens. Left-aligned sections explain Before you go, Contact, Support details, Distribution schedule, Location and Sources. Avoid copying unverified Free, walk-in or availability guarantees from fictional reference content.

The memorable element is a seven-day calendar strip: previous/next week, Today, selected date and dots for recorded sessions. Date-specific overrides replace recurring hours; overnight sessions identify next-day endings/continuations. Unknown schedules remain explicit. Session panels distinguish calendar hours from stock evidence. Regular weekly hours expand; provenance remains reachable. Prominent Report incorrect information buttons lead to a named-listing form, categories, 1000-character description, optional evidence link and reviewed-report confirmation. Native dropdown categories keep the form compact compared with the reference radio list.

Search accepts an address or an organisation. Name suggestions explicitly distinguish provider contacts from service locations; name results do not claim suitability. Address search continues mandatory-filtered nearest10 ranking. Empty states explain selected requirements and offer provider contacts without silently removing dietary filters. Saved services remain in the map dropdown.

Emulator critique: the first fixed-width calendar hid Sunday. Final seven-column layout fits the full week, keeps tall touch areas and announces selected dates to accessibility services. Visual walkthrough confirmed service details, named report navigation/back, and unknown schedule presentation. Postal560123 resolves correctly; the native empty state names the unchanged Nut-free+Vegetarian filters. Final build47370 succeeded45s and installed. No stock/date facts were invented and no automated tests added.

## Browse all and directory distribution information

An empty Find field keeps its primary button enabled; pressing it opens all active support alphabetically. Counts distinguish provider contacts from geocoded service locations. All-support browsing has no suitability filtering or nearest10 cap; the page explains this. Address searches retain mandatory matching. Back from a listing returns to the browse view.

Distribution cards prioritize the FoodConnect frequency and listed weekdays/dates, then delivery/collection, regions, recipients and diets. Provider facts are explicitly linked to actual service locations, not inferred from fuzzy names. Directory facts do not establish current stock or exact site hours. Unknown-hour calendars expand on request so they do not bury useful programme information. Fictional records were withdrawn and excluded from online/offline app snapshots.

All-support page and actual provider distribution cards visually reviewed in the emulator. Final build70314 succeeded23s and installed. Actual published dataset:22 providers and2 service locations; no fictional listings in public or offline app lists. No automated tests run.

## Directions on Map

Get directions opens the existing Map tab instead of embedding a second map in service details. Route mode keeps a floating starting-address search and device-location action; a bottom panel names the destination, offers Walk/Public transport/Drive, and shows real duration/distance, source and expandable steps. Searching an origin recalculates the destination route. Close/Back returns to map exploration. No nearby-result filters or saved-name suggestions compete with route entry. No origin means a clear start-address prompt. Unavailable transit remains explicit; no route is inferred.


## Google-style map refinement - 4 October

User asks to follow Google Maps. Design uses white#FFFFFF chrome, existing deep teal#00535B actions, mint#E2F3EA selection, Google blue#4285F4 location and muted#617175 secondary text. Atkinson Hyperlegible stays as the app typeface; compact body/label sizes preserve readability. Map gets the largest area. Floating pill search and small filter/provider chips; full-width bottom place/journey sheet with rounded top and compact actions. Directions gets a separate start/destination card with icon mode controls. This follows the requested navigation convention while keeping food-support content and the existing visual identity. Saved places stay in the search dropdown. Dynamic measured padding keeps route endpoints and Google attribution clear of UI.

Device location is requested automatically on map entry if access is already granted and location is enabled. Otherwise a starting-location sheet offers permission/device location or a typed address. Failed/time-out/out-of-Singapore location also opens this sheet. New directions use a session device origin by default, then allow manual changes. Mode changes preserve the chosen start. Location stays in memory and clears on background.

Follow-up: manual starts and route modes remain selected across tabs/destinations during the session. Map organisation name matches open a local preview with View support details above Navigate here. Transit journeys show grouped walking/transport sections with real timetable/stop information; operator names appear as plain attribution, with no SBS website action.
