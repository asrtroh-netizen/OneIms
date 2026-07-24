# Publish OneTools blocklist (CDN + GitHub mirror)

## Goal

Ship `one-blocklist.json` (`schema: onetools.blocklist.v1`) to:

1. **Preferred CDN**: `https://cdn.oneims.app/onetools/one-blocklist.json`
2. **Mirror (this machine can do now)**: GitHub Release `onetools-cdn-assets`

## Sign (optional but recommended)

```powershell
python onetools/scripts/sign_one_index.py sign docs/product/samples/one-blocklist.json
Copy-Item docs/product/samples/one-blocklist.json onetools/src/main/assets/sample-one-blocklist.json -Force
```

App fetch currently accepts unsigned v1 JSON; signature fields are ignored until a verifier is wired.

## Upload via GitHub Release (available without R2 keys)

```powershell
powershell -File onetools/scripts/publish-blocklist.ps1
```

Creates/updates release tag `onetools-cdn-assets` and uploads the JSON asset.

## Upload to real CDN (needs your object-store credentials)

Set env then re-run script:

- `ONE_CDN_PUT_URL` — pre-signed PUT URL or gateway endpoint for `onetools/one-blocklist.json`
- `ONE_CDN_PUT_TOKEN` — optional Bearer token

Or manually copy the signed file into your R2/S3/OSS bucket at `onetools/one-blocklist.json`.
