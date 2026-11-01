# Roxy 1.7.10 / GTNH — original Voxy engine alpha

Version 0.4.0 runs the original external Voxy GPU renderer, model baker, world
engine/database, and adaptive LOD hierarchy inside GTNH. Roxy supplies the legacy
Minecraft/Forge/Angelica interfaces and lifecycle. The original Voxy JAR remains
separate and unchanged. The previous 0.2.x custom terrain renderer is no longer
initialized.

This is a playable development alpha for the configuration below. It has been
visually tested in a disposable GTNH world; it is not a guarantee that every
GTNH block, dimension, resource pack, GPU, or camera angle renders correctly.
See [validation](VALIDATION.md) for evidence and remaining coverage gaps.

## Install

1. Use the **Java 17–26 edition of GTNH 2.9.0-beta-3**, running **Java 21 or newer**,
   with **Angelica 2.2.10** and the pack's LWJGL3ify installation.
2. Remove older Roxy JARs and put `roxy-0.4.1-gtnh-alpha.jar` in `.minecraft/mods`.
3. Obtain the original [Voxy 0.2.16-beta for Minecraft 1.21.11](https://modrinth.com/mod/voxy/version/H3w2nVdU)
   separately and put it in the same `mods` folder. Keep its original filename.
4. Launch the game, enter a world, and explore. Voxy
   starts automatically and retains observed terrain after you leave an area.
   Shader packs are optional; see the supported interface below.

```text
.minecraft/mods/
  roxy-0.4.1-gtnh-alpha.jar
  voxy-0.2.16-beta+1.21.11.jar
```

Roxy identifies Voxy by its Fabric mod ID in `mods` or `mods/1.7.10`, then verifies
the binary. The old `roxy/voxy.jar` location remains a fallback. No renaming is
required. Only Modrinth version `H3w2nVdU` is accepted; its SHA-256 is:

`7f8226cf64a4f5dc064e1075965714b718ac5f42fb28f826135a37c64e66e10b`

Use a GPU/driver supporting Voxy's modern compute and direct-state-access OpenGL
requirements. Live tests used an NVIDIA RTX 3060 with OpenGL 4.6 on Windows 11.
Other GPU vendors and operating systems have not been validated here.

## What runs in game

| Responsibility | Implementation |
| --- | --- |
| Nearby chunks, entities, machines, gameplay | GTNH and Angelica |
| Legacy block IDs, metadata, biomes, geometry, atlas and lightmap access | Roxy compatibility code |
| Six-view model baking, rasterization and model texture generation | Original external Voxy |
| Voxel reduction, five-level hierarchy, world updates, section serialization, RocksDB/Zstandard storage | Original external Voxy, with Roxy registry adapters |
| Asynchronous mesh generation, GPU hierarchy/occlusion traversal, indirect drawing, SSAO and composition | Original external Voxy |
| Camera matrices, framebuffer/depth handoff, near-chunk coverage and lifecycle | Roxy compatibility code |
| Shader pack parsing and distant shader drawing | Original Voxy `IrisShaderPatch` and `IrisVoxyRenderPipeline` |
| Angelica shader sources, uniforms, samplers, material IDs, buffers and camera/depth integration | Roxy compatibility code |

Roxy captures RenderBlocks geometry through GTNHLib and feeds it to Voxy's original
baker. It does not execute Fabric's entrypoint or modern Minecraft mixins. The
original Voxy rendering pipeline performs the distant terrain rendering. The
model adapter also preserves legacy directional shading and adjacent-face lighting
for translucent full cubes such as ice; fluid and inset geometry keep their own
lighting rules.

Received chunk packets enter a persistent, fair capture queue after their data is
installed. Each chunk's nonempty upper sections receive attention before the
queue returns for deeper sections. Moving away does not reset the queue; unloaded
chunks remain available until capture completes. Voxy's original reduction and
database ingestion run on a bounded worker queue. Repeated scans do not recapture
unchanged terrain; packet and block-change notifications drive updates.

If a mod's model callback throws, Roxy caches a static textured fallback for that
block/metadata and continues. It reports the exception once per model, with a
fallback count in `/roxy status`. Tile-dependent appearances may be approximate
in LODs; nearby machines continue to use their normal renderers.

## Shader support

Enable a pack through Angelica's **Shader Packs** screen. The pack must expose
Voxy's native `voxy.json`/GLSL interface and otherwise work with Angelica. Roxy
adapts that shared interface, with no shader-name checks or bundled shader code.
It uses the pack's own materials, custom uniforms, shadow/noise textures,
render targets, blending, TAA offsets and requested storage buffers.

Validation targets are **Complementary Reimagined r5.9.3**, **Photon 1.4**, and
**BSL 10.1.8** on Angelica 2.2.10. See [validation](VALIDATION.md) for the actual
test results. A pack without Voxy's interface cannot shade distant terrain through
this bridge; disable that pack and use `/roxy on` to resume without shaders.
Support for one pack does not imply every pack or graphics option is compatible.

## Controls and storage

- `/roxy on`, `/roxy off`: start or stop Voxy, including capture.
- `/roxy report`: save a local support report for a bug report.
- `/roxy status`: renderer state, captured sections, queued chunks, model fallbacks and distance or failure.
- `/roxy hud`: toggle the status overlay; F3 also shows original Voxy GPU counters.
- `/roxy distance 64`: set maximum distance, from 8 to 128 chunks.
- `/roxy reload`: reload game resources and rebuild captured models.
- `/roxy probe`: check the original reducer independently of terrain rendering.

The default radius is 64 chunks (1,024 blocks). Normal video render distance
controls nearby terrain; set it below the Voxy distance to see LODs. Voxy chooses
resolution adaptively and uses full-resolution sections near the boundary.
Increasing distance does not generate unexplored terrain or request extra chunks
from a server. Unobserved areas remain absent and can expose cached terrain edges.

Settings use the `voxy` category of `config/roxy.cfg`. The original Voxy world
database lives under `roxy/voxy-worlds/<world-or-server-hash>/dim<id>`. Registry
names, full metadata and biome identities survive restarts. The 0.2.x legacy
cache is preserved but not imported; revisit terrain to populate the new database.
If a server resets its world at the same address, move aside its derived database
while the game is closed. Roxy does not write to Minecraft region files.

The default geometry allocation is 512 MiB, in addition to Voxy's model textures
and other buffers. An explicit `-Dvoxy.geometryBufferSizeOverrideMB=...` overrides
it. The tested scene used roughly 640 MiB of Voxy buffers and 548 MiB of textures;
GTNH, Angelica and the game atlas require additional memory.

## Tested target and limits

| Component | Tested version |
| --- | --- |
| GTNH | 2.9.0-beta-3 |
| Minecraft / Forge | 1.7.10 / 10.13.4.1614 |
| Angelica | 2.2.10 |
| GTNHLib / Hodgepodge | 0.11.46 / 2.7.196 |
| EndlessIDs | 1.7.4, including 16-bit metadata |
| LWJGL3ify / LWJGL | 3.0.31 / 3.4.2 |
| Automated test runtimes | Java 21.0.13 and 25.0.5 |
| Live game runtime | Java 25.0.5 |

- Tile-entity-dependent appearances, animated special renderers, connected
  textures, and neighbor-dependent models are not fully represented by a static
  block/metadata snapshot. Detailed nearby rendering remains Angelica's job.
- Ice and water can still differ in appearance across the native/LOD boundary;
  this alpha does not promise pixel-identical transparency or bug-free visuals.
- Shader packs need Voxy's native interface. Packs control their own fog; without
  shaders, environmental fog hides distant drawing underwater, in lava, or during blindness.
- Capture has a soft six-millisecond budget and a 128-section upper limit per tick.
  Block changes share the budget with newly received terrain. Expensive initial
  model callbacks can exceed the soft budget. Capture can lag behind very fast
  travel, but moving/unloading no longer discards the pending chunks.
- The database grows with explored terrain. There is no server importer,
  background world generation, or dedicated multiplayer validation in this alpha.
- Angelica 2.1.61, other pack releases, all dimensions and all modded machine
  appearances remain unverified. No claim of completely bug-free visuals is made.

## Troubleshooting

See the [bug reporting guide](BUG_REPORTING.md) for attachments and a checklist.
Run `/roxy report` after reproducing the issue, before closing the game. It saves
`.minecraft/roxy/support-report.txt` locally and replaces the previous report.
Nothing uploads automatically. Include your **GTNH version OR, for another
modpack, its name/version and full mod list with versions**. Development and live
testing target GTNH; other Minecraft 1.7.10 packs running Java 21+ may use the
backport with its required dependencies, but are not automatically validated.

`/roxy status` and `logs/fml-client-latest.log` report the real failure. A missing
Voxy JAR is not a Java version problem. `UnsupportedClassVersionError` means the
launcher must select Java 21+; merely installing a newer runtime is insufficient.
An unsupported binary error means the exact external release above is required.

Developer commands `/roxy engineprobe`, `/roxy modelprobe`, and
`/roxy rendererprobe` exercise individual components, not full gameplay.
`-Droxy.rendererDiagnostics=true` writes GPU/depth diagnostics to
`roxy/render-diagnostics.txt` and adds readback overhead. Leave it off normally.

## External-JAR boundary and build

Published artifacts contain compatibility code only. They contain no Voxy
classes, shaders, assets, sources, nested JARs, or transformed class files. The
isolated loader reads the user's pinned original JAR and adapts its platform
references, model-input calls and legacy face-lighting metadata in memory. It extracts the JAR's own RocksDB JNI
dependency into a local runtime cache. No transformed Voxy output is saved.

This describes the technical distribution boundary; it is not permission from
Voxy's author or a legal guarantee. Voxy remains subject to its own license.

This directory is an independent Gradle build; the root project is modern Roxy.
Use JDK 25 for Gradle with JDK 8 and 21 available as compiler toolchains:

```powershell
.\gradlew.bat build '-ProxyVoxyJar=C:/path/to/voxy-0.2.16-beta+1.21.11.jar'
.\gradlew.bat test '-ProxyTestJava=25' '-ProxyVoxyJar=C:/path/to/voxy-0.2.16-beta+1.21.11.jar'
```

The external JAR is required to compile the bridge and run integration tests; no
build task downloads or republishes it. Install `build/libs/roxy-0.4.1-gtnh-alpha.jar`,
not the `-dev` or `-sources` artifacts. `verifyNoBundledVoxy` audits the artifact.

Pass `-ProxyAngelicaJar=C:/path/to/angelica-2.2.10.jar` to also run the hook/stack
verification against the actual host shader binary. That optional test skips
when no Angelica JAR is supplied; release validation supplies both external JARs.
