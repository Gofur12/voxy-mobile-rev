# Stage 67 — Live/cache reconciliation

- Live chunk ingestion queues every vertical section, including empty sections.
- Empty live sections remove old persistent snapshots rather than leaving stale terrain in the far-field cache.
- A restore gate records sections reconciled from live Minecraft chunk data.
- Delayed disk-cache results for those sections are ignored, so an older cached snapshot cannot overwrite a newer live section.
- The gate is cleared after disk restore is complete and the live section queue has drained.
- Added a pure-Java smoke test for changed, untouched, and reset section behavior.

The cache still only contains terrain that has been captured from chunks Minecraft delivered previously. Runtime validation on Android remains necessary.
