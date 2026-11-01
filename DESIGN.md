# MedX Elite — Design v3 rationale ("Clarity")

Branch: `redesign-v3` (from `main` @ d076f2c). Visual layer only — auth, Firebase, data, navigation and Firestore rules are untouched.

## What the research says great mobile UI is made of

| Source | Principle we take |
|---|---|
| Refactoring UI (Wathan & Schoger) | Hierarchy first: emphasise by *de‑emphasising* (weight + colour before size). Systems over guesswork: fixed spacing, type and shadow scales. 8–10 greys, one primary + semantic colours. Depth from a single light source (soft two-part shadows), fewer borders — separate groups with background shade and space. Never grey text on coloured fills; use a tint of the fill. |
| Apple HIG – Typography | Body ≥17 pt‑equivalent, avoid light weights, minimise typefaces, keep hierarchy when text scales, avoid truncation at large sizes (stack instead), loose leading for long reading. |
| Material 3 Expressive (2025, 46 studies / 18k participants) | Expressive = colour, shape, size, motion and containment used to point at what matters; key actions found up to 4× faster. Larger corner radii (20/32/48 dp), emphasised type for hero moments, spring motion, bigger buttons/touch targets, contrast beyond minimums. |
| Duolingo core-tabs refresh (Feb 2026) | Consistency balanced with purpose, simplicity with clarity; minimal number of type styles; use whitespace around components instead of forcing containers; consistent header size & title position across tabs. |
| Brilliant breakdown (2026) | "You always know what to do next": one golden-path CTA per screen, tactile buttons, clean home, small delightful details (colour that follows context). |
| Google Play / Apple Design Award 2025 winners (CapWords, Speechify) | Delight + inclusivity: Dynamic Type, accessibility at the core, playful-but-purposeful feedback. |
| WCAG 2.2 AA | 4.5:1 body text, 3:1 large text/UI parts; ≥48 dp targets (Android). |

## Chosen direction: **Clarity — a warm, light‑first, premium study companion**

Warm paper canvas, crisp white cards with soft elevation, one confident brand colour (**MedX Cobalt #3156E3**) and clear semantic colours for right/wrong. Big friendly display type (Plus Jakarta Sans) for headings and numbers; the system sans for long medical reading (full Greek/µ coverage, best legibility). Rounded, generous, calm — so the questions are the hero, and progress is always visible.

**Why this, not the others:** the two rejected looks failed on the same axis — style over legibility (neon/glass glow; monochrome dot-matrix). Every authoritative source above converges on hierarchy, restraint, contrast and a clear next action. Study apps users love (Duolingo, Brilliant, AMBOSS, Things-style productivity apps) are light-first, warm and tactile. Dark mode is a fully designed counterpart (warm near-black, lifted surfaces, no pure black, no glow). Default appearance stays **Automatic** (follows the phone), per HIG/M3; saved `appearance`/`accent` keys stay backward compatible (same enum names; colour values retuned for AA contrast).

## The system

- **Spacing:** 4‑based scale 4/8/12/16/20/24/32/40/48; screen margin 20 dp; card padding 20 dp; related items 8–12 dp, groups 24–32 dp.
- **Type (Jakarta for display/title/label, system sans for body):** Display 56/40 ExtraBold (numbers), Headline 32/28/24 Bold, Title 20/17/15, Body 17/15/13 (reading line height ~1.5), Label 15/13/12 SemiBold. Two weights do most work.
- **Colour (light):** canvas #F7F6F3, card #FFFFFF, sunken #EFEEEA, ink #17181C, ink‑2 #5E616A (5.7:1), ink‑3 #6F727A (≥4.5:1), hairline #E7E5E0; brand #3156E3 (5.9:1 on white) + container #E9EDFF; success #13814D/#E3F5EC, error #C9303A/#FDECEC, amber #8F5600/#FFF3DC, streak coral #F2683C.  **Dark:** canvas #101114, card #1A1B1F, raised #24252A, ink #F2F2F4, ink‑2 #A5A8B1 (7.2:1), brand #8FA5FF, success #56D394, error #FF7A7A, amber #F5B547.
- **Depth:** light — soft two-layer shadow (low-alpha ink, large blur) on cards, no borders; dark — elevation by lighter surface, no shadow.
- **Shape:** cards 24 dp, option rows 20 dp, buttons full pill (56 dp primary), chips pill, subject badges 14 dp squircles with tinted fill + coloured glyph.
- **Motion:** spring-based press scale on cards/buttons, animated progress/colour; all of it switches off when the system "Remove animations" setting is on (animator scale 0).
- **Feedback:** correct = green container + check badge; wrong pick = red container + cross; the right answer is always shown alongside (learning science: emphasise the correct answer).
- **Accessibility:** ≥48 dp targets, AA contrast checked numerically, sp everywhere, text wraps instead of clipping, layouts verified at 1.3× font scale.
- **Google sign-in:** follows the Sign in with Google branding guidelines — light: #FFFFFF fill, #747775 1 dp stroke, #1F1F1F text; dark: #131314 fill, #8E918F stroke, #E3E3E3 text; 12 dp leading pad, 10 dp after the logo; "Continue with Google", medium weight 14–16 sp; the four-colour G (#4285F4 / #34A853 / #FBBC05 / #EA4335) as a VectorDrawable using the exact path geometry shipped in Google's own GIS button renderer (accounts.google.com/gsi/client). Note: Google's branding page and asset zip now show the 2025 gradient "G"; the GIS-rendered button still uses the four-colour G, which is what was requested.

## Signature moments
1. **Home "Today" hero** — cobalt card with the goal ring, streak chip and one golden-path CTA ("Continue practising").
2. **Exam countdown** — a big confident number with a run‑up progress bar.
3. **Quiz runner** — calm reading column, tactile option cards, a segmented progress strip, a single big Next pill.
4. **Results** — score ring + verdict, three stat tiles, filterable review cards.

Sources: refactoringui.com; allthingspm.app/book-summaries/refactoring-ui; developer.apple.com/design/human-interface-guidelines/typography; m3.material.io/blog/building-with-m3-expressive; design.google/library/expressive-material-design-google-research; m3.material.io/styles/shape; blog.duolingo.com/core-tabs-redesign; dailydesignchallenge.com/breakdowns/brilliant; developer.apple.com/design/awards/2025; blog.google (Google Play Best of 2025); developers.google.com/identity/branding-guidelines.
