> **Reporting a backport bug?** See the [bug reporting guide](versions/1.7.10/BUG_REPORTING.md). Diagnostic reports are available in 0.4.1.
> **GTNH / 1.7.10 branch:** [Voxy engine compatibility alpha and installation instructions](versions/1.7.10/README.md).
> Version 0.4.0 adds retained chunk capture, isolated model fallbacks, and Angelica support for shader packs exposing Voxy's native interface. The original external Voxy renderer, baker, database, and adaptive LOD hierarchy remain in use. See the linked README for tested configurations and limits. This is an alpha; the upstream documentation below describes modern NeoForge, not this GTNH build.

### This is a backport of [Roxy](https://www.curseforge.com/minecraft/mc-mods/roxy) to forge 1.7.10

## Roxy allows for the loading of Voxy, a 1.21.11 Fabric exclusive LOD rendering mod, on Forge 1.7.10.

### Mod was created for and tested on GTNH 2.9.0 beta 3. REQUIRES JAVA 21+.

To make minecraft 1.7.10 run with Java 21+ you need PrismLauncher and [lwjgl3ify](https://www.curseforge.com/minecraft/mc-mods/lwjgl3ify) mod. Use the guide in the lwjgl3ify description.

Works with Angelica, and tested on latest versions of Complimentary, BSL, and photon. As long as a shaderpack has voxy support, it will work there too.

Voxy is a Fabric exclusive LOD rendering mod. Roxy provides a translation layer between Voxy and Forge, allowing an original Voxy jar to function not only on Forge, but also on a version that Voxy did not release for.

**_You require an original Voxy .jar_** for this mod to function, Roxy does not function without it. Currently, voxy-0.2.16-beta for MC 1.21.11 is the only verified version that works entirely as intended.

## Installation (because it's slightly unusual):

1.  Use PrismLauncher
2.  Download roxy into the mods folder
3.  Download [lwjgl3ify](https://www.curseforge.com/minecraft/mc-mods/lwjgl3ify)
4.  Set instance to use Java 21+
5.  Download [This version of voxy](https://modrinth.com/mod/voxy/version/H3w2nVdU) and place it in the mods folder
6.  Profit

If you aren't seeing any changes type `/roxy on` command in chat to turn it on.

## AI disclosure

This backport was mostly written with AI. If that's not your thing, don't use it. Despite that, the mod works, and is maintained. If you find any bugs, or find that your hardware is not supported, please report a bug.

## License

MIT. This does not relicense Voxy; Voxy remains All Rights Reserved and is not redistributed.
