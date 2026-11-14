# Stage 73 — Voxy-style service thread setting

Adds the General-page Service Threads control, matching the role of Voxy PC's serviceThreads option. Mobile range is 1–4 workers, default 1. The value is applied when the client renderer initializes, so changing it requires a Minecraft restart. Parent LOD aggregation/meshing uses this bounded worker pool; GLES upload/draw remain render-thread-only. Sodium builder-thread toggle is omitted because this mobile renderer does not use Sodium's PC builder pipeline. Android performance still requires on-device testing.
