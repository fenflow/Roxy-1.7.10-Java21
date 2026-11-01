# Reporting Roxy 1.7.10 bugs

Report incompatibilities, visual bugs, crashes, performance problems and other
backport issues in [this repository](https://github.com/fenflow/Roxy-1.7.10-Java21/issues/new?template=bug_report.yml).
Search existing issues first. This guide is for the Java 21+ Minecraft 1.7.10
backport, developed and tested in GTNH. Other packs are welcome to report issues;
using Java 21+ alone does not guarantee compatibility with their other mods.

## Collect the evidence

1. Reproduce the problem if possible. Record what you expected, what happened,
   and numbered steps, including how often it happens. If you cannot reproduce
   it reliably, say so and describe what you were doing.
2. With Roxy 0.4.1 or newer, run **`/roxy report`** in the affected world before
   quitting or restarting. This is Roxy's command, not `/voxy report`. Attach
   **`.minecraft/roxy/support-report.txt`** from that launcher instance's game
   directory. Each run replaces the file, so copy it aside for comparisons.
3. Attach the client log from the same session: **`logs/fml-client-latest.log`**
   and **`logs/latest.log`** if present. For a crash, also attach the matching
   **`crash-reports/crash-*.txt`**. If the JVM itself crashed, include its
   **`hs_err_pid*.log`** if available. If startup failed before logs were written,
   attach the launcher's console log. Do not restart before collecting logs;
   they may rotate. Zip large text files if needed.
4. For visual bugs, attach screenshots or a short video showing the defect and
   enough surrounding terrain to understand it. Include shader pack/version,
   preset and changed settings, resource packs, normal render distance and Roxy
   distance. If feasible, compare shaders on/off or `/roxy on` and `/roxy off`
   from the same viewpoint. Name affected blocks/mods and dimension.
5. Supply **GTNH version and any added/removed/updated mods**, OR **the other
   modpack's name/version and a full mod list with versions** (including custom
   packs). Include Roxy, external Voxy, Forge, Angelica, GTNHLib, LWJGL3ify and
   EndlessIDs versions where installed, Java version, OS and GPU/driver. The
   report includes selected versions, not a complete mod list.

For suspected incompatibilities, name the other mod/version and what makes you
suspect it. For performance issues, give approximate FPS/frame-time or stall
duration, distance settings and whether it happens while exploring new terrain.
Report results of any tests already tried. A minimal reproduction helps, but is
not required; use a separate test instance/world for mod-removal experiments.
Never remove content mods from your only copy of a world to test a report.

## If the command cannot run

If the game crashes before entering a world, the command is unavailable, or
writing the report fails, submit the issue anyway. Explain why the report is
missing, include the error shown and the available logs/crash report, and enter
hardware/runtime versions manually. An older build may not have this command.
A report generated after restarting cannot recover the previous session's
failure history. No recorded failures does not mean that no bug occurred.

## Review attachments before posting

The support report contains GPU/OpenGL, OS/Java, heap/CPU counts, selected mod
versions, renderer state/counters and up to 16 recent exception traces. It omits
exception messages, source filenames, chat, world/server identities and player
coordinates. It is saved locally; nothing is automatically uploaded.

Full logs, crash reports, mod lists and recordings are **not anonymized**. Review
every attachment for usernames, local paths, server addresses/IPs, chat, world
names, coordinates, tokens and other private data. Redact sensitive values while
keeping exception types, stack frames and relevant surrounding diagnostics.
Hardware/software identifiers can also be identifying. Do not attach an entire
instance, saves, cached world database or account/launcher credentials. Attach
text files rather than screenshots of logs. Explain any omitted evidence.

The report is diagnostic evidence, not a GPU support certification. Please send
backport issues here rather than assuming the external Voxy project caused them.
