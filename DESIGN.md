---
version: "alpha"
name: Signal Bench
description: A light, restrained inspection interface for device risk telemetry.
colors:
  background: "oklch(1 0 0)"
  surface: "oklch(0.97 0 0)"
  surface-strong: "oklch(0.93 0.006 38)"
  ink: "oklch(0.20 0.015 38)"
  muted: "oklch(0.48 0.018 38)"
  border: "oklch(0.87 0.008 38)"
  primary: "oklch(0.60 0.16 38)"
  primary-pressed: "oklch(0.53 0.15 38)"
  accent: "oklch(0.43 0.10 202)"
  success-surface: "oklch(0.94 0.035 202)"
  error: "oklch(0.50 0.18 25)"
typography:
  headline:
    fontFamily: System
    fontSize: 30px
    fontWeight: 700
    lineHeight: 36px
    letterSpacing: -0.02em
  title:
    fontFamily: System
    fontSize: 18px
    fontWeight: 650
    lineHeight: 24px
  body:
    fontFamily: System
    fontSize: 15px
    fontWeight: 400
    lineHeight: 22px
  label:
    fontFamily: System
    fontSize: 13px
    fontWeight: 600
    lineHeight: 18px
  telemetry:
    fontFamily: monospace
    fontSize: 12px
    fontWeight: 400
    lineHeight: 18px
spacing:
  xs: 4px
  sm: 8px
  md: 12px
  lg: 16px
  xl: 24px
  xxl: 32px
rounded:
  sm: 6px
  md: 10px
  lg: 14px
  full: 9999px
components:
  button-primary:
    backgroundColor: "{colors.primary}"
    textColor: "{colors.background}"
    rounded: "{rounded.md}"
    height: 52px
  status-ready:
    backgroundColor: "{colors.success-surface}"
    textColor: "{colors.accent}"
    rounded: "{rounded.full}"
---

# Signal Bench

## Overview

The demo should feel like a well-made inspection instrument on a clean engineering bench: clinical
white, exact labels, and one clay-colored control that makes the next action obvious. It is a security
tool without security theatre. The JSON is the main artifact.

## Colors

Use a restrained strategy. White owns the canvas; ink and neutral separators structure the payload.
Clay coral is reserved for collection actions. Deep teal communicates a successful, inspectable
result. React Native does not currently accept OKLCH color strings, so implementation tokens may use
documented sRGB translations while this file remains the normative color source.

## Typography

Use the native system sans for all interface copy and the platform monospace face only for JSON and
field identifiers. Respect user font scaling; do not compress telemetry by disabling scaling.

## Layout

Honor safe-area insets. Use a single scrolling column on phones and a centered column with a 760px
maximum width on larger devices. Maintain a 16px phone gutter and a 24px large-screen gutter.

## Elevation & Depth

Separate regions with tonal surface changes and hairline dividers. Avoid decorative shadows. The
payload viewer may use a solid outline because its boundary communicates scroll containment.

## Shapes

Controls use 10px corners; major payload regions use at most 14px. Status chips may be pill-shaped.
Minimum touch height is 48px so the same control clears both platform requirements.

## Components

- The collection button has idle, pressed, disabled, and loading states with stable dimensions.
- Status is written as text and color; color never carries meaning alone.
- Probe summaries are rows, not a grid of repetitive cards.
- The JSON viewer supports selection and copying while preserving wrapping for narrow screens.
- Errors stay inline near the collection action and retain the previous successful payload.

## Do's and Don'ts

- Do make platform, collection time, duration, and probe counts immediately scannable.
- Do explain that collection remains on-device when no endpoint is configured.
- Don't use neon colors, black terminal panels, fake prompts, scan-line effects, or cyberpunk copy.
- Don't hide failed or skipped probes; they are part of the useful result.
- Don't place text below 12px or create touch targets below 48px.
