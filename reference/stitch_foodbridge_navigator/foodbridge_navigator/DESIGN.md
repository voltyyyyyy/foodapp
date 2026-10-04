---
name: FoodBridge Navigator
colors:
  surface: '#f7f9ff'
  surface-dim: '#d7dadf'
  surface-bright: '#f7f9ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f1f4f9'
  surface-container: '#ebeef3'
  surface-container-high: '#e5e8ee'
  surface-container-highest: '#e0e3e8'
  on-surface: '#181c20'
  on-surface-variant: '#3e494a'
  inverse-surface: '#2d3135'
  inverse-on-surface: '#eef1f6'
  outline: '#6f797a'
  outline-variant: '#bec8ca'
  surface-tint: '#006972'
  primary: '#00535b'
  on-primary: '#ffffff'
  primary-container: '#006d77'
  on-primary-container: '#9becf7'
  inverse-primary: '#82d3de'
  secondary: '#2c694e'
  on-secondary: '#ffffff'
  secondary-container: '#aeeecb'
  on-secondary-container: '#316e52'
  tertiary: '#004e7a'
  on-tertiary: '#ffffff'
  tertiary-container: '#00679f'
  on-tertiary-container: '#c5e2ff'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#9ff0fb'
  primary-fixed-dim: '#82d3de'
  on-primary-fixed: '#001f23'
  on-primary-fixed-variant: '#004f56'
  secondary-fixed: '#b1f0ce'
  secondary-fixed-dim: '#95d4b3'
  on-secondary-fixed: '#002114'
  on-secondary-fixed-variant: '#0e5138'
  tertiary-fixed: '#cde5ff'
  tertiary-fixed-dim: '#94ccff'
  on-tertiary-fixed: '#001d32'
  on-tertiary-fixed-variant: '#004b74'
  background: '#f7f9ff'
  on-background: '#181c20'
  surface-variant: '#e0e3e8'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 28px
    fontWeight: '600'
    lineHeight: 34px
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  title-md:
    fontFamily: Inter
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
  body-lg:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.01em
  label-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '600'
    lineHeight: 16px
    letterSpacing: 0.02em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  unit: 8px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 32px
  xxl: 48px
  gutter: 16px
  margin-mobile: 16px
  margin-desktop: 32px
  max-width: 1280px
---

## Brand & Style
The design system is built on a foundation of **Civic Modernism**. It prioritizes clarity, dignity, and reliability for a community-focused food security platform. The visual language avoids the cluttered, "urgent" tropes of traditional charity branding in favor of a calm, institutional, and highly practical interface.

The aesthetic follows a **Minimalist** approach with a focus on high legibility and clear information hierarchy. By utilizing generous whitespace and a restricted color palette, the system ensures that users—often in stressful situations—can navigate the interface with confidence and minimal cognitive load. The emotional response should be one of stability and respect.

## Colors
This design system utilizes a palette rooted in nature and stability. 

- **Primary Action:** Deep Teal is the primary driver for progress and main interactions.
- **Surface Strategy:** Use white (#FFFFFF) for primary content cards and containers. Use the light grey (#F8F9FA) for page backgrounds to provide a soft contrast that defines content boundaries without harsh lines.
- **Semantic Clarity:** Colors are used strictly for functional communication. 
    - **Green** indicates immediate availability.
    - **Amber** signals limited stock or closing soon.
    - **Red** denotes urgent errors or unavailable services.
    - **Grey** is reserved for expired listings or historical data.
    - **Blue** highlights informational tooltips or secondary system notifications.

## Typography
The system uses **Inter** exclusively to ensure maximum legibility across all digital touchpoints. 

- **Hierarchy:** Use `headline-lg` for primary page titles and `title-md` for card titles.
- **Readability:** Body text defaults to 16px (`body-lg`) to ensure accessibility for a diverse user base. Use `body-sm` (14px) for secondary descriptions, metadata, and supporting text in data-heavy views.
- **Labels:** Button text and availability badges should use `label-md` for a slightly tighter, more functional appearance.

## Layout & Spacing
The design system employs a strict **8px spacing grid** to maintain vertical rhythm and visual consistency.

- **Grid System:** A 12-column fluid grid is used for desktop (breakpoint: 1024px+), transitioning to a 4-column grid on mobile devices.
- **Margins:** Page margins are 16px on mobile and 32px on desktop to provide breathing room.
- **Rhythm:** Use `lg` (24px) for vertical spacing between sections and `md` (16px) for spacing between internal card elements.
- **Touch Targets:** All interactive elements must adhere to a minimum 44px hit area, regardless of their visual size.

## Elevation & Depth
Depth is conveyed through **Low-contrast outlines** and minimal, soft shadows. The goal is to create a flat, approachable interface that feels grounded.

- **Level 0 (Base):** The main background (#F8F9FA).
- **Level 1 (Cards):** White background with a 1px border (#E9ECEF) and a very soft, diffused shadow (0px 2px 4px rgba(0, 0, 0, 0.05)).
- **Level 2 (Overlays/Modals):** White background with a medium shadow (0px 8px 16px rgba(0, 0, 0, 0.08)) to indicate temporary interaction.
- **Active States:** Subtle tonal shifts rather than dramatic elevation changes are preferred for buttons and inputs.

## Shapes
The system uses a **Rounded** (Level 2) shape language to balance professional structure with a friendly, accessible feel.

- **Standard Elements:** 8px (0.5rem) radius for standard cards, inputs, and buttons.
- **Large Elements:** 16px (1rem) radius for large modal containers or prominent banners.
- **Small Elements:** 4px radius for small chips or nested tags where space is limited.

## Components

### Buttons
- **Primary:** Deep Teal background, white text. Min-height 44px. 
- **Secondary:** Outline in Deep Teal, transparent background.
- **Ghost:** No background or border; Deep Teal text. Reserved for low-emphasis actions.

### Cards
- **Style:** White background, 1px border in #E9ECEF, 8px corner radius.
- **Content:** 24px internal padding. Titles in `title-md`, descriptions in `body-sm`.
- **States:** Hover should trigger a subtle increase in shadow depth and a border color shift to Deep Teal.

### Availability Badges
- **Requirement:** Must always include an icon (e.g., checkmark, clock, warning triangle) and text.
- **Styling:** Small pill shape with low-opacity background tint matching the semantic color and high-contrast text.

### Form Controls
- **Fields:** 44px minimum height. 1px border (#CED4DA) that turns Deep Teal on focus.
- **Labels:** Persistent labels above the field (never just placeholders).
- **Errors:** 2px Red border (#BC4749) with error text directly below the field in 12px weight.

### Header
- **Desktop:** 72px height, sticky at the top, white background with a thin bottom border.
- **Mobile:** 64px height, contains a simplified navigation menu.

### Icons
- Use simple, 2px stroke-width line icons (e.g., Lucide or Feather style). Avoid filled icons unless used for active navigation states.