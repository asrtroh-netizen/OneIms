# Publish OneTools phone blocklist into OneBlock

## Canonical (main branch)

```text
https://raw.githubusercontent.com/OneCatx/OneBlock/main/phone/one-blocklist.json
```

## Release mirror (OneBlock only — not OneIms)

```text
https://github.com/OneCatx/OneBlock/releases/download/onetools-cdn-assets/one-blocklist.json
```

Tag: `onetools-cdn-assets` on **OneCatx/OneBlock**.  
The old OneIms release with the same tag has been removed.

```powershell
powershell -File onetools/scripts/publish-blocklist.ps1
```

Optional CDN dual-write: set `ONE_CDN_PUT_URL` (+ `ONE_CDN_PUT_TOKEN`).
