# Publish OneTools phone blocklist into OneBlock

## Canonical (main branch)

```text
https://raw.githubusercontent.com/asrtroh-netizen/OneBlock/main/phone/one-blocklist.json
```

## Release mirror (OneBlock only — not OneIms)

```text
https://github.com/asrtroh-netizen/OneBlock/releases/download/onetools-cdn-assets/one-blocklist.json
```

Tag: `onetools-cdn-assets` on **asrtroh-netizen/OneBlock**.  
The old OneIms release with the same tag has been removed.

```powershell
powershell -File onetools/scripts/publish-blocklist.ps1
```

Optional CDN dual-write: set `ONE_CDN_PUT_URL` (+ `ONE_CDN_PUT_TOKEN`).
