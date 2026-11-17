# Feature matrix

| Feature | Behavior |
|---|---|
| Clean tab (Home) | Cleanup plan: "Up to X", Auto rows, system steps, "Worth a look", biggest folders/apps; quick scan at launch when the plan is older than 6 h |
| Swipe review | One file at a time: Delete marks, Keep is remembered; nothing is deleted before the preview sheet is confirmed |
| Trash tab | Restore, delete forever, empty; 7-day retention |
| Junk | Temporary files, unfinished downloads, preview caches, Android's trash |
| Duplicates | Any file type; sample + full SHA-256; one original always kept; optional rewarded deep check for large look-alikes |
| Large files | Files ≥ 100 MB |
| Media | Screenshots, large media and media duplicates; nothing preselected because it is a screenshot |
| Downloads & APK | Old downloads, installers, unfinished downloads |
| WhatsApp | In-app review of WhatsApp / Business shared media |
| App cache | StorageStats measurement + Android `ACTION_CLEAR_APP_CACHE` |
| App manager | Launchable apps, storage/cache, uninstall through Android |
| Storage analysis / change | File-type totals and folder review; storage change since the last scan |

Every delete uses one confirmation (`ui/common/DeleteConfirmDialog`) that lists names, folders, kept originals and where the files go.
