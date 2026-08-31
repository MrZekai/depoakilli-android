# Feature matrix - v0.5.18 closed-test candidate

| Feature | v0.5.1 behavior |
|---|---|
| Smart / Deep Scan | Recursive shared-storage engine |
| Junk Cleaner | Temp/incomplete/thumbnail artifacts + review candidates |
| Duplicate Cleaner | Any accessible file type; sample + full SHA-256 |
| Large Files | Any accessible file over 100 MB |
| Media Cleaner | Old screenshots and storage-heavy photo/video review |
| Downloads & APK | Old downloads, installers, incomplete artifacts |
| WhatsApp Cleaner | In-app scan of WhatsApp / Business shared Media |
| Deep App Cache | StorageStats measurement + Android official cache-clean action |
| App Manager | In-app launchable-app list, storage/cache stats, uninstall handoff |
| Storage Analyzer | File-type totals from the same shared-storage index |
| Device status | Not presented as a cleaner tool |


## v0.5.1 focused cleaner actions

| Home action | Real implementation |
|---|---|
| Smart Clean | Standard on-device assessment across shared storage + exact duplicates |
| Deep Clean | Broader review across accessible storage; lower-confidence personal files remain review-only |
| WhatsApp Cleaner | Direct shared-media indexing under supported WhatsApp / WhatsApp Business media roots |
| Duplicate Files | Sample fingerprint followed by full streaming SHA-256 |
| Large Files | Shared-storage files >= 100 MB |
| APK Cleaner | APK installer packages only |
| Media Cleaner | Accessible screenshot / large media review + exact media duplicates; nothing is preselected merely for being a screenshot |
| Deep App Cache | StorageStats measurement + Android `ACTION_CLEAR_APP_CACHE` |
| Storage Analyzer | File-type totals without presenting files as junk |
