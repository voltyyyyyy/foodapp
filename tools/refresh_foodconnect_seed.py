"""Refresh the reviewed FoodConnect provider seed from the archived public API response."""

import json
from datetime import datetime, timezone
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
API_PATH = ROOT / "backend/data/foodconnect-sources/foodProviders.json"
SEED_PATH = ROOT / "backend/seeds/foodconnect-providers.json"
LEGACY_SERVICE_SEED_PATH = ROOT / "backend/seeds/foodconnect-services.json"
SOURCE_URL = "https://foodconnect.gov.sg/directory/"
PAGE_LAST_UPDATED = "2023-03-22"  # Directory page footer; record timestamps can differ.
TYPE_LABELS = {"CM": "Cooked Meals", "FR": "Food Rations", "FV": "Food Vouchers", "FP": "Fresh Produce"}
DIET_LABELS = {"H": "Halal", "NH": "Non-Halal", "V": "Vegetarian"}
DELIVERY_LABELS = {"CFDP": "Collect from Distribution Point", "DSD": "Door-step Delivery"}
REGION_LABELS = {"C": "Central", "E": "East", "N": "North", "NE": "North-East", "W": "West"}
PROGRAMS = {
    "CM": ("cookedMeal", "Cooked Meals"),
    "FR": ("foodRation", "Food Rations"),
    "FV": ("foodVoucher", "Food Vouchers"),
    "FP": ("freshProduce", "Fresh Produce"),
}


def split_semicolon(value):
    if not isinstance(value, str) or not value.strip():
        return []
    return [item.strip() for item in value.split(";") if item.strip()]


def name_key(value):
    return "".join(ch.lower() for ch in value if ch.isalnum())


def translated(values, labels):
    return [labels.get(value, value) for value in values]


def enrich_facts(source):
    codes = source.get("supportType") or []
    facts = {
        "source_page_last_updated": PAGE_LAST_UPDATED,
        "source_record_timestamp_raw": source.get("timeStamp"),
        "support_type_codes": codes,
        "support_types": translated(codes, TYPE_LABELS),
        "directory_summary": source.get("summary") or [],
        "directory_description": source.get("description") or "",
        "directory_contact": source.get("contact") or "",
        "directory_email": source.get("email") or "",
        "directory_website": source.get("website") or "",
        "directory_provider_name_raw": source.get("provider") or "",
        "organisation_has_public_contact_flag": source.get("orgHasContact"),
        "organisation_has_website_flag": source.get("orgHasWebsite"),
        "programmes": {},
    }
    facts["source_card_fields"] = {
        key: source.get(key)
        for key in ("provider", "summary", "description", "contact", "email", "website", "supportType", "orgHasContact", "orgHasWebsite")
    }
    facts["source_programme_fields"] = {}
    for code, (prefix, label) in PROGRAMS.items():
        info = source.get(f"{prefix}Info") or {}
        legacy_types = split_semicolon(source.get(f"{prefix}Types"))
        raw_days = source.get(f"{prefix}DistributionDays")
        raw_dates = source.get(f"{prefix}DistributionDates")
        raw_types = source.get(f"{prefix}Types")
        programme = {
            "listed": code in codes,
            "frequency": info.get("frequency") or source.get(f"{prefix}Frequency") or "",
            "frequency_raw": source.get(f"{prefix}Frequency"),
            "recipients": info.get("aidRecipients") or split_semicolon(source.get(f"{prefix}Recipients")),
            "recipients_raw": source.get(f"{prefix}Recipients"),
            "dietary_preference_codes": info.get("dietaryPreference") or [],
            "dietary_preferences": translated(info.get("dietaryPreference") or [], DIET_LABELS),
            "delivery_method_codes": info.get("deliveryMethod") or [],
            "delivery_methods": translated(info.get("deliveryMethod") or [], DELIVERY_LABELS),
            "region_codes": info.get("regions") or [],
            "regions": translated(info.get("regions") or [], REGION_LABELS),
            "estate_group_raw": source.get(f"{prefix}EstateGroup"),
            "delivery_method_raw": source.get(f"{prefix}DeliveryMethod"),
            "distribution_days_raw": raw_days,
            "distribution_dates_raw": raw_dates,
            "has_support_raw": source.get({"CM": "hasCookedMeal", "FR": "hasFoodRations", "FV": "hasFoodVoucher", "FP": "hasFreshProduce"}[code]),
            "types_raw": raw_types,
            "listed_types_raw": legacy_types,
            "api_info": info,
        }
        # Keep older parallel fields where the current card object exposes both forms.
        # Their differences are visible rather than silently reconciled.
        type_labels = translated(legacy_types, DIET_LABELS)
        if type_labels and type_labels != programme["dietary_preferences"]:
            programme["legacy_type_values"] = type_labels
        facts["programmes"][label] = programme
        facts["source_programme_fields"][code] = {
            key: source.get(f"{prefix}{suffix}")
            for suffix, key in (
                ("Frequency", "frequency"),
                ("Recipients", "recipients"),
                ("Types", "types"),
                ("DeliveryMethod", "delivery_method"),
                ("EstateGroup", "estate_group"),
                ("DistributionDays", "distribution_days"),
                ("DistributionDates", "distribution_dates"),
            )
        }
        facts["source_programme_fields"][code]["has_support"] = source.get(
            {"CM": "hasCookedMeal", "FR": "hasFoodRations", "FV": "hasFoodVoucher", "FP": "hasFreshProduce"}[code]
        )
    # These are public aggregates displayed by the directory UI, not internal identifiers.
    facts["aggregate_frequency"] = source.get("aggFrequency") or []
    facts["aggregate_recipients"] = source.get("aggAidRecipients") or []
    facts["aggregate_dietary_codes"] = source.get("aggDietaryPreference") or []
    facts["aggregate_dietary_preferences"] = translated(source.get("aggDietaryPreference") or [], DIET_LABELS)
    facts["aggregate_delivery_codes"] = source.get("aggDeliveryMethod") or []
    facts["aggregate_delivery_methods"] = translated(source.get("aggDeliveryMethod") or [], DELIVERY_LABELS)
    facts["aggregate_region_codes"] = source.get("aggRegions") or []
    facts["aggregate_regions"] = translated(source.get("aggRegions") or [], REGION_LABELS)
    return facts


def main():
    source_records = json.loads(API_PATH.read_text(encoding="utf-8-sig"))
    seed = json.loads(SEED_PATH.read_text(encoding="utf-8-sig"))
    by_name = {name_key(row.get("provider", "")): row for row in source_records}
    # The official response contains a replacement character in this provider's name.
    by_name[name_key("KRSNA’S FREE MEALS")] = next(
        row for row in source_records if "KRSNA" in row.get("provider", "")
    )
    now = datetime.now(timezone.utc).replace(microsecond=0).isoformat()
    for record in seed["records"]:
        raw = by_name.get(name_key(record["name"]))
        if record["name"] == "KRSNA’S FREE MEALS":
            raw = by_name[name_key(record["name"])]
        if not raw:
            raise ValueError(f"No official API row matches {record['name']!r}")
        record["support_types"] = translated(raw.get("supportType") or [], TYPE_LABELS)
        record["directory_facts"] = enrich_facts(raw)
        if "KRSNA" in record["name"]:
            # The API provider field contains U+FFFD; provider branding confirms the readable display name.
            record["name"] = "Krsna's Free Meals"
        record["verified_at"] = now

    by_record = {row["name"]: row for row in seed["records"]}
    updates = {
        "Ascending Hope Community Services": {
            "verification_status": "provider_reviewed",
            "verification_notes": "Provider programmes page confirms food-ration assistance, but current details vary by programme. Current contact and programme sources are linked; FoodConnect frequencies/recipients/regions are retained as source-card facts and not all are independently confirmed.",
        },
        "Club Rainbow (Singapore)": {
            "verification_status": "provider_reviewed",
            "verification_notes": "The 2022 provider annual report describes food ration/voucher assistance for supported families. This is an older programme source, not current intake instructions; contact the provider to confirm current availability. Directory facts remain source-only unless separately confirmed.",
        },
        "Filos Community Services": {
            "verification_status": "provider_reviewed",
            "verification_notes": "Provider assistance page confirms monthly door-to-door ration/essential-item distribution in Chai Chee. It does not independently confirm FoodConnect's voucher category or current intake route. Contact the provider for current details.",
        },
        "Food From The Heart": {
            "verification_status": "provider_reviewed",
            "verification_notes": "Provider programme page describes monthly Community Food Packs and intended beneficiary groups working through community partners. No single public collection point or open intake route is assumed. FoodConnect facts remain separately retained.",
        },
        "Free Food For All": {
            "verification_status": "provider_reviewed",
            "verification_notes": "Current provider Get Help page confirms food-aid programmes, online application and role-based food-aid contact. Its contact values differ from the FoodConnect card; the current provider contact is used and the card values remain preserved in directory_facts.",
        },
        "Loving Heart Multi-Service Centre (Jurong)": {
            "verification_status": "provider_reviewed",
            "verification_notes": "Current provider Food Gift+ page confirms monthly ration support, programme criteria, and an application form. Its Jurong office is not assumed to be the distribution location. Contact the provider to confirm current criteria and process.",
        },
        "Metropolitan YMCA Singapore": {
            "verification_status": "provider_reviewed",
            "verification_notes": "Provider outreach page confirms monthly M.Y Manna groceries, home delivery and collection at its centre. The reviewed page does not establish the FoodConnect card's current intake route or specific collection details; contact the programme before visiting.",
        },
        "Singapore Red Cross Society": {
            "verification_status": "provider_reviewed",
            "verification_notes": "Current Family Crisis Aid page confirms grocery-voucher support and application through a social worker, caseworker, or partner agency. It does not confirm every category in the FoodConnect card; ask about the relevant current programme.",
        },
        "Apex Club of Bukit Timah": {
            "phone": None,
            "email": "apexbt.sg@gmail.com",
            "website": "https://apexbt.org",
            "provider_source": "https://apexbt.org/",
            "contact_source": "https://apexbt.org/contact/",
            "verification_status": "provider_reviewed",
            "eligibility": "Apex says it assesses prospective Redhill food-distribution beneficiaries through home interviews and reviews its assigned beneficiary list. Contact Apex to ask about current eligibility and intake; the public site does not describe an open walk-in application.",
            "verification_notes": "Current Apex site describes its Redhill food distribution and weekly home deliveries to assigned beneficiaries; its help page also describes interviews and review of beneficiaries. Contact page lists apexbt.sg@gmail.com while the home-page footer lists apexbt@gmail.com; the contact-page address is used. FoodConnect source facts remain unconfirmed current details.",
        },
        "Blessed Grace Social Services Limited": {
            "phone": "8428 6377",
            "email": None,
            "website": "https://bgss.org.sg",
            "provider_source": "https://bgss.org.sg/",
            "contact_source": "https://bgss.org.sg/contact-us",
            "verification_status": "directory_only",
            "eligibility": "The current BGSS site describes recovery, debt-management, family-support, and eldercare services but does not confirm the FoodConnect food programmes. Contact its helpline and ask whether food assistance is currently available before relying on this listing.",
            "verification_notes": "Current BGSS pages confirm the 8428 6377 helpline and list its current service areas, but do not mention the FoodConnect food programmes. Food-support details and the directory email are retained as directory-only; center visits are by appointment and its address is not listed as a food service point.",
        },
        "En Community Services Society": {
            "phone": "+65 6788 6625",
            "email": "encares@ecss.org.sg",
            "website": "https://ecss.org.sg",
            "provider_source": "https://ecss.org.sg/wp-content/uploads/2026/03/Annual-Report-ECSS-FY-ending-30-06-2025.pdf",
            "contact_source": "https://ecss.org.sg/contact-us/",
            "verification_status": "provider_reviewed",
            "eligibility": "En's FY2024/25 annual report mentions food-voucher support, but the current public pages do not describe its current intake or the FoodConnect listing's collection/frequency details. Contact En's Social and Community Care team to ask about eligibility and application.",
            "verification_notes": "Current contact page lists the main line and Social and Community Care inbox encares@ecss.org.sg. The FY2024/25 annual report mentions food-voucher support; FoodConnect-specific frequency, regions, and application steps are not independently confirmed. Office is not a confirmed collection point.",
        },
        "Hao Ren Hao Shi Limited": {
            "phone": "+65 9058 5898",
            "email": "enquiry@hrhs.org.sg",
            "website": "https://www.haorenhaoshi.org.sg",
            "provider_source": "https://www.haorenhaoshi.org.sg/",
            "contact_source": "https://www.haorenhaoshi.org.sg/",
            "verification_status": "provider_reviewed",
            "eligibility": "HRHS describes Happy Mart and Mobile Groceries support but the reviewed public pages do not set out beneficiary intake criteria. Contact HRHS to ask about current eligibility, referral, and location details before travelling.",
            "verification_notes": "Current HRHS home page lists Happy Mart on Wednesdays and Sundays at Stirling/Fernvale, Mobile Groceries on Saturdays at various locations, and current role contact details. This differs from FoodConnect's 2023 directory row stating weekly Saturday rations; preserve that card wording in directory_facts and confirm the specific support and location with HRHS.",
        },
        "Hopes in Meals": {
            "verification_status": "directory_only",
            "provider_source": None,
            "contact_source": None,
            "eligibility": "FoodConnect's listing says walk-ins are welcomed at listed organizations, but Hopes in Meals' current eligibility, application method, and the availability of this support could not be independently confirmed. Contact the organization through its listed channel before relying on the listing.",
            "verification_notes": "No current organization-controlled programme or contact page was located in this review. Email and Facebook link are transcribed from the FoodConnect row and are not independently verified. All programme facts remain directory-only.",
        },
        "Krsna's Free Meals": {
            "phone": "+65 9105 6343",
            "email": "admin@krsnasfreemeals.org",
            "website": "https://www.krsnasfreemeals.org",
            "provider_source": "https://www.krsnasfreemeals.org/contact",
            "contact_source": "https://www.krsnasfreemeals.org/contact",
            "verification_status": "provider_reviewed",
            "eligibility": "The provider publishes a meal-service location and contact, but the reviewed page does not specify eligibility or an application process. Contact the provider to confirm whether meals are currently being served and any access requirements.",
            "address": "20 Veerasamy Road",
            "postal": "207327",
            "latitude": 1.307412667002052,
            "longitude": 103.8535128363121,
            "location_notes": "Provider contact page explicitly gives 20 Veerasamy Road, Singapore 207327 and describes it as its meal-service address. OneMap's public search response for postal 207327 returned this address/coordinate. It is a listed meal-service point; exact access or collection terms should be confirmed with the provider.",
            "description": "Krsna's Free Meals lists cooked meals and fresh produce in FoodConnect. Its current contact page publishes 20 Veerasamy Road and meal-service hours, but the page's upper section and footer show different hours. Call before visiting; FoodConnect frequency, diet, delivery, and region details remain source-card facts and are not independently confirmed.",
            "verification_notes": "Current provider contact page confirms meal-site address, phone, and role email. It gives 6:30–9:00am and 11:00am–2:30pm in its upper section, while the footer adds 7:00–9:00pm; no schedule is published to the map seed because of this conflict. FoodConnect lists different phone 9628 2045 and its programme facts remain separately preserved. The API provider-name field contains a replacement character; display spelling is reconciled from provider branding and the raw value is retained in directory_facts.",
        },
        "Loving Heart Multi-Service Centre": {
            "phone": "+65 6897 4766",
            "email": "hello@lovingheart.org.sg",
            "website": "https://lovingheart.org.sg",
            "provider_source": "https://www.lovingheart.org.sg/contact-us",
            "contact_source": "https://www.lovingheart.org.sg/contact-us",
            "verification_status": "directory_only",
            "eligibility": "The provider's current contact page does not confirm the FoodConnect monthly ration listing or its intake criteria. Contact Loving Heart MSC to ask whether food assistance is currently available and how to apply.",
            "verification_notes": "Current provider contact page supplies the public telephone and general hello@ inbox but does not confirm the FoodConnect monthly ration programme. Therefore programme facts remain directory-only. The office address is not asserted to be the distribution point.",
        },
        "Mummy Yummy": {
            "verification_status": "directory_only",
            "provider_source": None,
            "contact_source": None,
            "eligibility": "FoodConnect's entry lists weekday meal support for frail/homebound seniors, seniors, low-income families, and people with disabilities, but the current programme and intake route could not be confirmed on a current organization-controlled source. Contact the listed channel to ask whether this support is still available.",
            "verification_notes": "A past MSF-hosted programme document was located, but no current organization-controlled food-support page was found. The email and Facebook URL are transcribed from FoodConnect and are not independently verified; programme facts remain directory-only.",
        },
        "Realm of Tranquility": {
            "phone": "+65 6469 3843",
            "email": "email@realmoftranquility.com",
            "website": "https://realmoftranquility.com",
            "provider_source": "https://realmoftranquility.com/the-4-pillars/",
            "contact_source": "https://isomer-user-content.by.gov.sg/24/75e28489-c4be-46a0-9e57-eb0fde6fbb48/list-of-ncss-members.pdf",
            "verification_status": "provider_reviewed",
            "eligibility": "Realm's public site describes monthly food-ration support for needy families, but does not state a public application route or detailed eligibility criteria. Contact the organization to ask about current intake.",
            "verification_notes": "Current organization pages describe food-ration distribution and a 2023 annual report records monthly ration packs. Phone is from the organization's current contact page; role email is listed in the NCSS member directory (2025). Its office location is not a food collection point. FoodConnect regional programme details have not been independently revalidated.",
        },
        "Singapore Association of the Visually Handicapped": {
            "phone": "+65 6251 4331",
            "email": "enquiries@savh.org.sg",
            "website": "https://savh.org.sg",
            "provider_source": "https://savh.org.sg/wp-content/uploads/2024/09/SAVH-Annual-Report-2024.pdf",
            "contact_source": "https://savh.org.sg/contact/",
            "verification_status": "provider_reviewed",
            "eligibility": "SAVH's 2024 annual report describes monthly home rations for visually impaired clients; it does not establish public walk-in eligibility. Ask its Social Work team about eligibility and referral requirements.",
            "verification_notes": "Current SAVH contact page gives the Social Work inbox enquiries@savh.org.sg and line 6251 4331 ext 502. Phone field omits an extension so users reach the central switchboard; programme source is the 2024 annual report. FoodConnect's old named contact values remain in directory_facts.",
        },
        "Singapore Buddhist Welfare Services": {
            "phone": "+65 6489 8161",
            "email": "sbws@sbws.org.sg",
            "website": "https://sbws.org.sg/en/home",
            "provider_source": "https://sbws.org.sg/en/services-affiliates/social-welfare-and-community-services/public-assistance-programme/",
            "contact_source": "https://sbws.org.sg/en/about-us/corporate-information/",
            "verification_status": "provider_reviewed",
            "verification_notes": "Current provider programme page confirms monthly food rations through needs assessment and referrals by hospitals, FSCs, grassroots groups, and public agencies; current corporate page confirms the public telephone and role inbox. FoodConnect's older named-staff contact is preserved in directory_facts.",
        },
        "United Indian Muslim Association": {
            "phone": None,
            "email": "md.habib@uima.org.sg",
            "website": "https://www.uima.org.sg",
            "provider_source": "https://www.uima.org.sg/need-help/",
            "contact_source": "https://www.uima.org.sg/need-help/",
            "verification_status": "provider_reviewed",
            "eligibility": "UIMA invites prospective beneficiaries to register with its online form. The reviewed page does not state the specific eligibility for FoodConnect's ration listing. The page shows inconsistent/malformed phone text, so use its email or form to ask about current eligibility and availability.",
            "verification_notes": "Provider Need Help page currently links a beneficiary registration form and lists md.habib@uima.org.sg. Its main body has malformed phone numbers; footer phone differs from the card. No Call number is published in the app; FoodConnect's 6748 6187 remains in directory_facts as an unconfirmed source value. Office address is not asserted as ration collection point.",
        },
        "Willing Hearts": {
            "provider_source": "https://willinghearts.org.sg/contact",
            "contact_source": "https://willinghearts.org.sg/contact",
            "verification_status": "provider_reviewed",
            "verification_notes": "Current provider contact page confirms phone, meal assistance application via a social worker/social agency, and kitchen address/postal code. Kitchen address is not asserted as a public collection point. The directory email is retained but not independently confirmed by the provider page.",
        },
        "Yong-en Care Centre": {
            "phone": "+65 6225 1002",
            "email": "mail@yong-en.org.sg",
            "website": "https://www.yong-en.org.sg",
            "provider_source": "https://www.yong-en.org.sg/care-assistance/",
            "contact_source": "https://www.yong-en.org.sg/care-assistance/",
            "verification_status": "provider_reviewed",
            "eligibility": "Yong-en states that Food Ration referrals are based on Social Service Office or Family Service Centre referrals and residence in the Chinatown vicinity. Email the centre to enquire; the listed Smith Street address is the administrative centre, not a confirmed ration collection point.",
            "verification_notes": "Current provider care-assistance page confirms monthly dry rations for low-income families and aged seniors in Chinatown, Jalan Kukoh, and York Hill; ration eligibility requires SSO/FSC referral and Chinatown-vicinity residence. Current role contact and administrative centre details are published, but no public collection point is confirmed. FoodConnect facts are retained separately.",
        },
    }
    for name, update in updates.items():
        by_record[name].update(update)
    # Preserve actual public source endpoint and page-level versus per-row time metadata.
    seed["source"] = SOURCE_URL
    seed["reviewed_at"] = now
    seed["permission"] = (
        "Public information transcribed from the Government of Singapore FoodConnect directory for internal review. "
        "This is not a partner export or a grant of redistribution rights. Do not treat directory details as current stock, "
        "eligibility approval, or confirmed access without provider confirmation."
    )
    seed["coverage"] = {
        "listed_organisation_count": len(source_records),
        "official_data_endpoint": "https://foodconnect.gov.sg/api/prod/foodProviders",
        "listing_scope": "All rows from the site's public directory data endpoint response; response contained 22 provider records in one array. Raw response and response headers are archived under backend/data/foodconnect-sources/.",
        "source_page_last_updated": PAGE_LAST_UPDATED,
        "record_timestamps": "Per-record timestamp is retained verbatim as source_record_timestamp_raw; formats differ and one row may use an Excel-serial-like value.",
        "source_fact_mapping": "Public provider/card summary, description, contact, email, website, supportType; each supportType's *Info frequency, recipients, dietaryPreference, deliveryMethod and regions; corresponding raw *Frequency, *Recipients, *Types, *DeliveryMethod, *EstateGroup and *DistributionDays/*DistributionDates values (including null versus blank); and public aggregate fields are retained under directory_facts. No PK/SK, agreement, internal download status, named contact person, logo metadata, internal request contact, or internal references are included.",
        "coordinates": "Null except Krsna's confirmed provider-published public meal-service address, geocoded using OneMap. Other provider office/HQ addresses are not assumed to be service or pickup points.",
    }
    seed["source_notes"] = [
        "The raw official response includes 22 provider records; source page footer states last updated 22 Mar 2023. Per-row timestamps are preserved and can differ.",
        "Directory facts are source-card values, not current availability, stock, eligibility approval, or guaranteed service. The FoodConnect FAQ says eligibility varies by provider.",
        "Provider-reviewed status means a current or recent organization source confirmed relevant programme/contact information; it does not validate every FoodConnect field. Directory-only means programme facts have not been independently corroborated by an organization-controlled source.",
        "No unpublished intake information, hours, diet, accessibility, or service address is inferred. Unknown locations remain null.",
    ]
    SEED_PATH.write_text(json.dumps(seed, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    legacy = json.loads(LEGACY_SERVICE_SEED_PATH.read_text(encoding="utf-8-sig"))
    legacy["legacy_status"] = "Reference-only artifact; not an authoritative current directory extraction and not the app import seed."
    legacy["permission"] = (
        "Legacy public-directory staging artifact retained for comparison only. Its records were assembled from an earlier rendered/indexed review. "
        "Use foodconnect-providers.json, refreshed from the complete public API response, as the authoritative provider seed."
    )
    legacy["coverage"] = {
        "listed_organisation_count": len(source_records),
        "authoritative_endpoint": "https://foodconnect.gov.sg/api/prod/foodProviders",
        "listing_scope": "The complete official endpoint response contains 22 provider rows. This older artifact's supplemental directory_entries are reference-only and should not be treated as current exact API facts.",
        "source_page_last_updated": PAGE_LAST_UPDATED,
        "not_exposed_by_endpoint": ["confirmed service collection point except where separately verified", "current eligibility decisions", "current availability or stock", "reliable schedules where provider facts conflict", "accessibility unless independently published"],
    }
    legacy["source_notes"] = [
        "Legacy comparison/reference only; exact public API fields and current provider review status are in foodconnect-providers.json.",
        "The original directory card values can be stale and are not a live availability feed.",
    ]
    LEGACY_SERVICE_SEED_PATH.write_text(json.dumps(legacy, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    statuses = {}
    for row in seed["records"]:
        statuses[row["verification_status"]] = statuses.get(row["verification_status"], 0) + 1
    print(f"Wrote {len(seed['records'])} providers; statuses={statuses}; reviewed_at={now}")


if __name__ == "__main__":
    main()
