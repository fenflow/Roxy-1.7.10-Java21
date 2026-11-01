# Support report validation (2026-11-01)

- Built the packaged 0.4.1 alpha with the pinned external Voxy binary and
  Angelica 2.2.10 supplied to validation. All 41 tests passed with no skips;
  formatting, Checkstyle and `verifyNoBundledVoxy` passed.
- Used Computer Use to launch the existing disposable Roxy Bridge Test world
  in GTNH 2.9.0-beta-3, Java 25.0.5, Windows 11, NVIDIA RTX 3060 / driver 616.92.
  Entered `/roxy report` through in-game chat using the production JAR, without
  a development bridge override. The client logged the save confirmation and
  attachment guidance; `roxy/support-report.txt` was generated successfully.
- Inspected the generated report: OpenGL 4.6, 6144 MiB maximum heap, renderer
  running, 4390 captured sections, zero pending chunks, eight model fallbacks,
  selected dependency versions, and bounded exception types/code locations.
  Actual fallback traces included Thaumic Bases and Forestry model callbacks.
  These are diagnostics from this test scene, not new compatibility claims.
- The live check caught a Git-derived Forge version label. The final build
  overrides the injected `Tags.VERSION` with the same release version used by
  the JAR and `mcmod.info`; generated/compiled metadata was checked after rebuild.
- The privacy regression test checks that exception messages and source filenames
  are omitted and failure history is bounded to 16 entries. Full game logs are
  intentionally separate and must be reviewed/redacted by the reporter.

This is a report-command smoke test, not exhaustive renderer validation. Other
modpacks, multiplayer, failed writes, pre-startup crashes and every renderer
failure mode were not exercised live. The guide explains how to file an issue
when a support report cannot be generated.
