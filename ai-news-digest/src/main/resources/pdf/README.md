# Fonts and icons of the digest

Embedded in the PDF page by `render.DigestAssets` (docs/09-pdf-design.md); nothing is loaded at runtime.

| Folder | Source | Version | License |
|---|---|---|---|
| `fonts/` | Inter, https://github.com/rsms/inter (`docs/font-files/`) | v4.1 | SIL Open Font License 1.1, `fonts/OFL-Inter.txt` |
| `icons/`, `icons/ui/` | Lucide, https://github.com/lucide-icons/lucide (`icons/`) | 1.80.0 | ISC, `icons/LICENSE-Lucide.txt` |

## Icon keys

The aggregator may only choose the keys in `icon-keys.txt`; every key needs `icons/<key>.svg` (checked at startup and by `DigestAssetsTest`).

| Key | Lucide icon |
|---|---|
| `default` | `newspaper` |
| `ai` | `brain` |
| `chip` | `cpu` |
| `cloud` | `cloud` |
| `security` | `shield-check` |
| `code` | `code` |
| `chart` | `chart-line` |
| `money` | `circle-dollar-sign` |
| `law` | `scale` |
| `research` | `microscope` |
| `rocket` | `rocket` |
| `warning` | `triangle-exclamation-point` |
| `globe` | `globe` |

Layout icons in `icons/ui/`: `check`, `eye`, `file-text`, `list-checks`, `newspaper` (same names as in Lucide).

A new key: add it to `icon-keys.txt`, copy the matching Lucide SVG unchanged as `icons/<key>.svg`, extend this table. The aggregator prompt gets the list automatically (placeholder `allowedIconKeys`).
