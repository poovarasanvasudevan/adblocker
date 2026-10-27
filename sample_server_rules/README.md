# AdBlocker Server Rules & Filter Catalog Setup Guide

This folder contains the ready-to-deploy filter catalog JSON and ad-blocking rules file for your AdBlocker app.

---

## 1. Files Included

| File | Purpose | Description |
| :--- | :--- | :--- |
| [`filters.json`](filters.json) | **Filter Catalog Registry** | A JSON array containing filter list metadata (ID, title, description, rule count, download URL). The app reads this to present filter lists to the user. |
| [`adblock_rules.txt`](adblock_rules.txt) | **Rule Definitions** | The actual domain/blocking rules list. Compatible with hosts format (`0.0.0.0 domain.com`), plain domain names (`domain.com`), and AdBlock/EasyList syntax (`||domain.com^`). |

---

## 2. Supported Rule Formats in `adblock_rules.txt`

The Go tunnel engine (`tunnel.Tunnel.compileAdblockRules`) natively parses:

1. **Hosts Format**:
   ```text
   0.0.0.0 doubleclick.net
   127.0.0.1 adservice.google.com
   ```
2. **EasyList / AdBlock Format**:
   ```text
   ||doubleclick.net^
   ||adservice.google.com^
   ```
3. **Plain Domain Lists**:
   ```text
   doubleclick.net
   adservice.google.com
   ```
4. **Comments**:
   Lines starting with `#` or `!` are treated as comments and ignored.

---

## 3. How the App Uses These Files

1. **On First Startup**:
   - `FilterRepository` loads `blocklist_starter.txt` from local assets into fast in-memory memory cache (`starterBlockedDomains`).
   - `FilterRepository` seeds the default filter lists into the local SQLite database (`AdBlockDatabaseHelper.TABLE_FILTER_LISTS`).

2. **On Refresh / Update Button** (in the Filters tab):
   - The user taps the refresh icon in the Filters tab (`onUpdateAllFilters`).
   - `FilterRepository.updateAllActiveFilters()` loops through all enabled filter lists, downloads each file from its `downloadUrl`, and compiles them into `.trie` (Patricia Trie) and `.bloom` (Bloom Filter) files using the embedded Go engine.
   - When browsing or when VPN is active, queries are matched against these compiled trie files in sub-millisecond time.

---

## 4. Hosting on Your Server

You can host these two files anywhere that serves plain HTTP/HTTPS GET requests:
- **Cloudflare Pages / GitHub Pages / Vercel** (Free & Fast CDN)
- **AWS S3 / Google Cloud Storage / Firebase Hosting**
- **Your own Nginx / Apache / Node.js / Python / Go backend**

### Example:
- **Catalog URL**: `https://your-server.com/filters.json`
- **Rules URL**: `https://your-server.com/rules/adblock_rules.txt`

In `filters.json`, ensure `"downloadUrl"` points to your hosted rules URL:
```json
{
  "id": "my_server_master_filter",
  "name": "My Master AdBlock Filter",
  "description": "Consolidated rules from my server",
  "isEnabled": true,
  "isBuiltIn": true,
  "category": "ads",
  "ruleCount": 50000,
  "downloadUrl": "https://your-server.com/rules/adblock_rules.txt",
  "originalUrl": "https://your-server.com/rules/adblock_rules.txt"
}
```
