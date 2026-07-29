# Brand assets

The Device Risk Signals mark uses two open observation rings around one independent signal. The open
geometry represents raw observations that remain separate from any risk score or verdict.

## Files

| Asset | Intended use |
| --- | --- |
| `docs/images/readme-header.svg` | Wide README and repository introduction |
| `docs/images/device-risk-signals-logo.svg` | Packaged README and npm logo |
| `website/assets/logo.svg` | Canonical square logo for GitHub Pages and external listings |
| `website/assets/logo-512.png` | Raster fallback for services that do not accept SVG |
| `website/assets/logo-mark-light.svg` | Transparent mark for light surfaces |
| `website/assets/logo-mark-light-512.png` | Transparent light-surface PNG fallback |
| `website/assets/logo-mark-dark.svg` | Transparent mark for dark surfaces |
| `website/assets/logo-mark-dark-512.png` | Transparent dark-surface PNG fallback |
| `website/assets/favicon.svg` | Browser favicon |
| `website/assets/social-preview.svg` | Editable social-card source |
| `website/assets/social-preview.png` | Rendered 1200 by 630 Open Graph and social card |

## Usage

- Preserve the clear space already included in the square logo.
- Use the transparent mark only when the surrounding surface supplies sufficient contrast.
- Do not recolor the independent signal to green or turn it into a checkmark; the SDK does not return
  a trusted or safe verdict.
- Do not add a shield, lock, fingerprint, warning triangle, score, or vendor badge.
- Keep the SVG as the source of truth and regenerate PNG fallbacks after changing its geometry.

The current palette uses `#111814` for the dark field, `#f5f4ef` for observation rings, and
`#ff5a2b` for the independent signal. The light-surface mark uses the website ink `#18211c` and
coral `#c95736`.
