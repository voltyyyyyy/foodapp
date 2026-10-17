# Food-support source review — 18 October 2026

Three Luna subagents researched the existing 22 providers and additional Singapore food-support programmes. Their findings were checked and curated into concise listing copy, source-specific programme facts and an audited publication batch. A website review confirms what a source publishes; it does not confirm live stock, current capacity or an individual's eligibility.

The batch contains **24 updates** (22 providers and two existing map services), **seven new providers**, **five new meal sites** and four organisation records. Field-level sources, contradictions and unanswered questions are recorded in [the research evidence](food-support-2026-10-18.json). The executable batch is [listing-review-2026-10-18.json](../../backend/seeds/listing-review-2026-10-18.json).

## Additional providers and sites

| Provider | Listing and location decision | Primary sources |
| --- | --- | --- |
| The Food Bank Singapore | Provider contact for emergency food aid. No office/warehouse pin. Its separate F&B meal-card programme is marked paused; no relaunch is assumed. | [Programmes](https://foodbank.sg/programmes/), [food-support contact](https://foodbank.sg/contact/) |
| The Singapore Buddhist Lodge | Meal site at 17–19 Kim Yam Road, 239329. Published daily breakfast 07:00–09:00, lunch 11:30–14:00, dinner 17:00–19:30. | [Current meal times and address](https://www.sbl.org.sg/chn/contact.aspx), [2024 government confirmation of free vegetarian meals](https://www.istana.gov.sg/newsroom/speech-by-president-tharman-shanmugaratnam-at-the-90th-anniversary-of-the-singapore-buddhist-lodge/) |
| Central Sikh Gurdwara Board | Central Sikh Temple at 2 Towner Road, 327804, and Silat Road Gurdwara at 8 Jalan Bukit Merah, 169541. Daily langar is described; exact meal windows remain empty. Temple phone details found only in older publications are omitted from new public records. | [MCCY/ICCS visitor meals](https://www.iccs.sg/about/visit-singapore/places-of-worship/central-sikh-gurdwara/), [Silat Road board page](https://sikhs.org.sg/about-srst/), [board's explanation of free vegetarian langar](https://sikhs.org.sg/wp-content/uploads/Books/Bhai-Maharaj-Singh-Ji-Gurdwara-Sahib-Silat-Road-2nd-Revision.pdf) |
| Sri Guru Singh Sabha Singapore | Visitor meal site at 90–92 Wilkie Road, 228086. Free vegetarian langar is explicit; serving days and times are unknown. | [Official visitor-meal description and address](https://singhsabha.sg/) |
| BW Monastery | Joyfeast at 1 Woodlands Drive 16, 737764. First Sunday of each month; serving time is unknown. English/Chinese FAQ wording differs on other Sunday meals, so no every-Sunday calendar is inferred. | [Official FAQ](https://bwmonastery.org.sg/bwm-faqs) |
| Sree Ramar Temple | Provider-only record for invited Annadhana groups and agency/FSC-selected Project Akshaya families. No public walk-in pin is added. | [Seva programmes](https://www.sreeramartemple.org.sg/seva) |
| Langar Outreach Program | Provider-only vegetarian meal delivery. Partner kitchens and its office are not represented as beneficiary pickup points. | [Meal programme](https://lop.sg/), [request help](https://lop.sg/contact-us/) |

The five mapped sites were matched by postal code and building identity against OneMap's public address search. Wilkie Road returned two nearby building points; the first matching 228086 gurdwara result was used. Coordinates describe buildings, not an accessible entrance. Raw OneMap responses remain under ignored `backend/data/listing-research-2026-10-18/`. No wheelchair accessibility was inferred.

Only the Lodge has exact supported start/end times for a new recurring meal calendar. A monthly recurrence, a start time without an end, a temple's general opening hours or a volunteer shift does not become a dated food appointment. New availability observations remain empty.

## Existing-provider updates

- Added current contacts where supported, including Club Rainbow, Filos, BGSS, Metropolitan YMCA and Realm of Tranquility. Contacts that could not be freshly checked retain their older directory context.
- Replaced generic intake text with documented referral, application or assessment routes. Club Rainbow's general age/medical criteria are separated from its family-specific food-support evidence; Food from the Heart's reported income profile is not presented as a universal threshold.
- Recorded current programme distinctions: SBWS meal delivery versus monthly rations; HRHS Happy Mart versus Mobile Groceries; Red Cross voucher programmes; and FFFA's automated versus app-based delivery.
- Used [AIC's own general hotline, 1800-650-6060](https://www.aic.sg/Contact-Us), rather than copying the conflicting number on Willing Hearts' referral text.
- Retained Krsna's section-level dinner-hours discrepancy and encoded no meal calendar while the published sections differ.
- Kept older FoodConnect programme objects and raw values. New `directory_facts.reviewed_programmes` and `research_review` objects identify the more recent source review. Android displays those facts first, with older directory details expandable separately.
- Hopes in Meals and Mummy Yummy have no newly confirmed programme source; their previous verification timestamps are retained. BGSS and Loving Heart MSC's contact pages do not establish that their older food programmes currently operate. UIMA's generic beneficiary registration does not establish its food-specific offer. None is silently upgraded to current verified food assistance.

## Follow-up leads

Jamiyah's Jampacked programme has concrete sites and income criteria, but the research did not settle whether any beneficiary payment is required; it is excluded from this confirmed-free batch. THK's cited free-meal-centre location was last established by an older annual report; current operation and access need confirmation. Paid/subsidised Meals-on-Wheels, donation drop-off sites, historical one-off events and paid temple restaurants were excluded.

## Validation and publication

The [batch tool](../../tools/apply-listing-review.py) validates all records before using staff APIs. It stages imports before publishing, stops for quarantine or suspected duplicates, requires the reviewed versions for existing records, publishes organisations before services and verifies discovery visibility and publication audits. Repeating a completed batch is a no-op; it cannot overwrite a conflicting imported ID.

```powershell
./backend/.venv/Scripts/python.exe backend/tests/verify_listing_review.py
./backend/.venv/Scripts/python.exe backend/tests/verify_staff_portal.py
./tools/build-android.ps1
```

The disposable-database checks passed: all 24 updates, 16 new records, audits, safe replay, stale-edit/ID-collision rejection, no quarantine/duplicates and retained unknown stock/access. The staff suite passed 38 checks. The Android build passed.

The batch was published to the running local stack through the audited administrator API. The authenticated discovery endpoints returned **29 providers and seven active service records**. All 47 initial batch operations were recorded and verified; one further audited source-link correction points Apex's contact source at its contact page instead of the conflicting homepage footer. No imports were quarantined or flagged as suspected duplicates. A fresh local test administrator was created using the operator CLI; its credentials remain in ignored staff credential files.

The updated APK was installed on the emulator. The provider directory displayed 29 providers; filtering, provider details and the reviewed FFFA programme panel were checked. The new programme facts and review timestamp appeared above the separately expandable older FoodConnect details. This verifies the affected presentation, not real-world food availability, every route or production uptime.
