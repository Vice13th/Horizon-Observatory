# Security & privacy

Horizon Observatory records GNSS, cellular, sensor and location data **on the device**; nothing is uploaded by the app itself.
Exports and reports contain location data â€” treat them as sensitive.

- Report vulnerabilities privately to the maintainer (do not open a public issue with exploit details).
- Imported CSVs (identity table) are size-capped (2 MB) and validated; report CSV cells that look like formulas are neutralised.
- Note: export/report files are written under the app-specific external files directory (same base as the existing session export).
