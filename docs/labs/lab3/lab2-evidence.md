# Recovered Lab 2 stack exercise

Extracted from `reference/stitch_foodbridge_navigator/SC2006 Labs.docx`, first occurrence of the stack exercise. Text is evidence recorded in that document; the original chat, model identity and diagram revision were not independently verified. Referenced files such as `lab 1.md` are historical names, not current repository links.

# FoodBridge Navigator — AI technology stack exercise



## Material supplied to the AI



The FoodBridge Navigator Lab 1 description and requirements (Singapore-only Android 12+ food-support search; approved service data; anonymous search, routing, reporting, bookmarks and local preferences; administrator moderation; authorised data imports) and the Lab 2 analysis class diagram, showing entity, boundary and control classes. The complete source documents are `lab 1.md` and `Lab2_Analysis.md`; the entity and boundary/control JPEG diagrams accompany this report.



## Exact prompt



> Based on the supplied FoodBridge Navigator system description and Lab 2 class diagrams, recommend a technology stack appropriate for an entry-level software engineering team. The first release targets Android 12+ in Singapore. It needs an Android UI, a small API for approved service search and moderation, local bookmarks/preferences, a persistent server-side data store, and OneMap integration. Keep the proposal small and explain the role of each technology. Treat the proposal as a discussion starting point.



## Original AI chat response



> I recommend a small Android client and one server application. Build the Android UI with Kotlin and Jetpack Compose. The client can keep bookmark IDs and optional preferences on the device, while obtaining current approved service details from the server; avoid storing the user's device location after the search session.

>

> Build a single Python FastAPI service for search, service details, public correction reports, administrator authentication and moderation. Keep matching, freshness labels, imports and approval decisions in this service so the same rules apply to every client. Use SQLite for a course prototype database containing approved organisations/services, availability observations, reports, change requests, decisions and import provenance. Use transactions for an approval and its audit record. If the team later needs deployment with more concurrent writers, reassess the database before production.

>

> Use OneMap through a small adapter for address lookup and routes, with a timeout and an explicit unavailable state. Import only authorised partner exports or a manually verified seed dataset. Keep the first version to one mobile app, one API service and one database. Test the main search, stale-data, report-validation and moderation paths before adding extra infrastructure.



This is the verbatim response generated for this exercise. Its product descriptions were checked against the official [Android Compose](https://developer.android.com/compose), [FastAPI](https://fastapi.tiangolo.com/), [SQLite](https://www.sqlite.org/about.html), and [OneMap](https://www.onemap.gov.sg/apidocs/) documentation on 19 September 2026. The suitability assessment is the AI's inference from the project needs, not a claim made by those documentation sites.


