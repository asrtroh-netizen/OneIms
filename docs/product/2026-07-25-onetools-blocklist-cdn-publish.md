# Publish OneTools phone blocklist into OneBlock repo

Target file:

`https://raw.githubusercontent.com/asrtroh-netizen/OneBlock/main/phone/one-blocklist.json`

OneBlock also hosts domain rule-sets for Karing/Mihomo; the phone list lives under `phone/` and does not replace them.

```powershell
powershell -File onetools/scripts/publish-blocklist.ps1
```

Optional real CDN:

```powershell
$env:ONE_CDN_PUT_URL = 'https://...'
$env:ONE_CDN_PUT_TOKEN = '...'
powershell -File onetools/scripts/publish-blocklist.ps1
```
