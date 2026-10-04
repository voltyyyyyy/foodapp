# Product Requirements Document (PRD): FoodBridge Navigator

**Project:** FoodBridge Navigator  
**Version:** 1.0.0  
**Status:** Approved / In Review  
**Platform:** Mobile Web Application (Responsive, Mobile-First PWA)  
**Primary Market:** Singapore (with support for local civic food ecosystem, Halal certification, and Singpass/PDPA standards)  
**Primary Theme & Design Tokens:**  
- **Primary Color:** Teal `#006D77` (Civic Trust, Hygiene, Compassion)
- **Accent Highlight:** Amber/Orange `#E29578` & Vibrant Emerald for Live/Flash Statuses
- **Surfaces:** Clean off-white `#F7F9FF` to `#FFFFFF` with soft 8px/12px border radius
- **Typography:** Inter (Legible, accessible, neutral)

---

## 1. Executive Summary & Vision

**FoodBridge Navigator** is a hyper-local, community-centered food security and surplus redistribution progressive web app. It bridges the gap between verified civic food relief services (soup kitchens, food pantries, meal charities) and individuals seeking dignified, immediate nutrition, while also empowering organizations, caterers, and individuals to share safe, time-sensitive food surplus from events before it goes to waste.

### Core Problems Solved
1. **Fragmented Information & Friction:** Food assistance details are frequently outdated, buried in complex PDFs, or lack real-time serving hours, creating wasted journeys for beneficiaries.
2. **Stigma & Cognitive Load:** People in vulnerable circumstances often face confusing registration processes and intimidating interfaces.
3. **Preventable Surplus Waste:** Event buffets, corporate seminars, and bakeries dispose of safe, packaged meals due to lack of a rapid, low-friction drop-and-claim channel.

---

## 2. Target Personas & Use Cases

### Persona 1: The Urgent Seeker (Beneficiary / Caregiver)
- **Goal:** Quickly locate hot meals or grocery aid within walking distance right now, without complex logins or invasive verification.
- **Key Pain Point:** Showing up at a center only to find it closed or out of portions.
- **App Experience:** Direct map-first or simplified home search, immediate help callout, clear diet filters (Halal, Vegetarian, Nut-Free), and live schedule timetables.

### Persona 2: The Civic Event Organizer / Food Rescuer
- **Goal:** Rapidly share 10–50 leftover bento boxes or catered food from an event ending in 30 minutes.
- **Key Pain Point:** Existing donation processes take days to verify; food spoils if not claimed in 1–3 hours.
- **App Experience:** "Share Community Food Drop" flow with GPS auto-location, 1/2/3-hour strict expiry timer, and instant map publish.

### Persona 3: Community Center & Pantry Staff
- **Goal:** Keep timetables accurate, report anomalies, and manage walk-in distribution volume without bureaucratic overhead.
- **App Experience:** Timetable updates, crowd status indicators, and report-an-issue ticketing.

---

## 3. Key Product Features & Information Architecture

### A. Navigation & Shell Philosophy
- **Headerless, Minimalist Header:** Eliminates cluttered top app bars for maximum content canvas and calm aesthetic.
- **Unified 4-Icon Bottom Taskbar:**
  1. **Map View:** Central geo-spatial discovery of nearby pantries, kitchens, and live drops.
  2. **Saved Locations / Bookmarks:** Quick offline-accessible list of favorite centers and frequent routes.
  3. **Home:** High-level search, urgent help card, categorized shortcuts, and calendar widget.
  4. **Profile:** Authentication (Google / Email), user preferences, dietary restrictions, and posted drops.

---

### B. Core Screens & Functional Specifications

#### 1. Home Screen (Simplified & Headerless)
- **Auto-Location Detection:** Auto-fills user’s current neighborhood or postal code with one-tap refresh.
- **Search Bar:** Simple input labeled *"Search"* (replacing intimidating "Find Support" terminology).
- **Immediate Help Entry Point:** Minimal, high-visibility callout card connecting to 24/7 crisis hotlines and immediate walk-in centers (no cumbersome voucher bureaucracy).
- **One-Page Fit:** Calibrated layout preventing unnecessary vertical scrolling on standard mobile screens.

#### 2. Map-Centric Discovery & Results
- **Interactive Map:** Color-coded map pins for Civic Kitchens (Teal), Pantries (Navy), and Flash Drops (Amber/Emerald pulse).
- **Flash Surplus Drops:** Distinctive highlighted tags with real-time countdown clocks (`⚡ Flash Drop • 48m left`) to reflect acute urgency.
- **Filter Pills:** One-tap toggles for *All Services*, *Hot Meals*, *Pantries*, *Halal Certified*, *Vegetarian*, and *Wheelchair Accessible*.

#### 3. Meal Service Timetable & Organization Detail (e.g., Harmony Community Kitchen)
- **Clutter-Free Timetable Widget:** Replaces confusing tables with clean day-of-week rows.
- **Soft "Today" Highlight:** Highlighted with a calm light teal/green surface rather than disruptive alert bubbles.
- **Live Meal Status:** Clear indicators for *Walk-in Welcome*, *Lunch (12:00 PM – 2:30 PM)*, and *Dinner*.
- **Direct Issue Reporting:** Embedded "Report an Issue" link at the base of the detail screen to crowd-verify hours and stock levels.

#### 4. Community Food Drop Sharing (Flash Redistribution)
- **Strict Auto-Expiry Selector:** 
  - `1 Hour` (Hot Buffet / Cooked Catering)
  - `2 Hours` (Recommended for Individually Boxed Bentos)
  - `3 Hours Max` (Ambient Bakery, Fresh Whole Fruit)
  - Auto-removal from public map upon timer expiration to ensure food safety and dignity.
- **Location & Beaconing:** Building name, floor, specific pickup notes (e.g., *"Level 3 Foyer, table outside Suite 308"*), and optional GPS pin.
- **Safety & Verification:** Portion estimator (`1-5 pax`, `6-12 pax`, `15-25 pax`, `30+ pax`), food photo verification, and dietary tags.

#### 5. Food Distribution Calendar
- **Airy Week Selector:** Minimalist 7-day strip (`M 21` to `S 27`) with active day markers.
- **Distribution Cards:** Chronological list of scheduled community distributions with direct route links and calendar reminders.

#### 6. Simplified Sign In & Trust
- **Low-Barrier Auth:** Single-click "Continue with Google" or Email login.
- **Guest-First Policy:** Full browsing of food locations without requiring sign-in; authentication required only for posting community drops or saving bookmarks.
- **Civic Privacy Assurance:** Minimal PDPA compliant security notice.

---

## 4. Technical & Non-Functional Requirements

| Dimension | Specification |
|---|---|
| **Responsive Target** | Mobile-First (375px – 430px primary viewport; fully responsive up to desktop) |
| **Performance** | Sub-second Time to Interactive (TTI); lightweight asset bundle; zero heavy JS frameworks for core map/timetable renders |
| **Accessibility (a11y)** | WCAG 2.1 AA compliant color contrast ratio (minimum 4.5:1 for body copy), legible touch targets (minimum 44x44px), semantic HTML |
| **Data Protection** | Singapore PDPA compliant; zero tracking of unauthenticated seekers; organizer contact numbers shielded unless courier/pickup claims food |
| **Offline Reliability** | LocalStorage caching for bookmarked addresses and emergency helpline phone numbers |

---

## 5. Success Metrics (KPIs)

1. **Surplus Diversion Rate:** Ratio of posted flash drops claimed vs. expired uncollected (Target: >75% claimed within 90 minutes).
2. **Time to Assistance:** Average time from opening app to initiating navigation route to a verified meal center (< 45 seconds).
3. **Data Freshness / Report Accuracy:** Percentage of community issue reports addressed within 24 hours (Target: >90%).
4. **Beneficiary Dignity Score:** User satisfaction rating on clarity and lack of intrusive questioning.

---

## 6. Future Roadmap

- **Phase 1.1:** Volunteer courier push-notifications for high-volume drops (>30 pax).
- **Phase 1.2:** WhatsApp / Telegram bot integration for instantaneous flash drop broadcasting.
- **Phase 1.3:** Multilingual language switcher (English, Chinese, Malay, Tamil) for older demographic accessibility.
