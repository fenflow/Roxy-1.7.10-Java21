package net.rasanovum.roxy.legacy.loader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Experimental full-engine loader. External bytes are never included in a Roxy artifact. */
public final class ExternalVoxyLoader extends URLClassLoader {

    private static final String BRIDGE = "net.rasanovum.roxy.legacy.modern.";
    private int externalClasses;

    public ExternalVoxyLoader(Path jar, Path cache) throws IOException {
        super(urls(jar, cache), ExternalVoxyLoader.class.getClassLoader());
    }

    private static URL[] urls(Path jar, Path cache) throws IOException {
        if (!VoxyRuntime.SUPPORTED_SHA256.equals(VoxyRuntime.sha256(jar))) {
            throw new IOException("Full engine requires the pinned original Voxy 0.2.16-beta / 1.21.11 JAR");
        }
        Path directory = cache.resolve(VoxyRuntime.SUPPORTED_SHA256);
        Files.createDirectories(directory);
        Path dependency = directory.resolve("rocksdbjni-10.2.1.jar");
        // Extract only the explicit nested dependency, never classes/shaders from Voxy itself.
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry entry = zip.getEntry("META-INF/jars/rocksdbjni-10.2.1.jar");
            if (entry == null || entry.getSize() > 64000000)
                throw new IOException("Missing/invalid RocksDB dependency");
            Path temporary = Files.createTempFile(directory, "rocksdb-", ".tmp");
            try {
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, temporary, StandardCopyOption.REPLACE_EXISTING);
                }
                if (!Files.exists(dependency) || !VoxyRuntime.sha256(dependency)
                    .equals(VoxyRuntime.sha256(temporary))) {
                    Files.move(temporary, dependency, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        }
        String development = System.getProperty("roxy.developmentBridgeJar");
        if (development != null) return new URL[] { Paths.get(development)
            .toUri()
            .toURL(),
            jar.toUri()
                .toURL(),
            dependency.toUri()
                .toURL() };
        return new URL[] { jar.toUri()
            .toURL(),
            dependency.toUri()
                .toURL() };
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        String mapped = VoxyPlatformPatch.mappedClass(name);
        if (!mapped.equals(name)) return loadClass(mapped, resolve);
        if (!name.startsWith("me.cortex.voxy.") && !name.startsWith(BRIDGE) && !name.startsWith("org.rocksdb.")) {
            return super.loadClass(name, resolve);
        }
        synchronized (getClassLoadingLock(name)) {
            Class<?> type = findLoadedClass(name);
            if (type == null) {
                String resource = name.replace('.', '/') + ".class";
                URL url = name.startsWith(BRIDGE) && System.getProperty("roxy.developmentBridgeJar") == null
                    ? getParent().getResource(resource)
                    : findResource(resource);
                if (url == null) throw new ClassNotFoundException(name);
                try {
                    URLConnection connection = url.openConnection();
                    connection.setUseCaches(false);
                    try (InputStream in = connection.getInputStream()) {
                        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                        byte[] buffer = new byte[8192];
                        int size;
                        while ((size = in.read(buffer)) != -1) bytes.write(buffer, 0, size);
                        byte[] data = bytes.toByteArray();
                        if (!name.startsWith("org.rocksdb.")) {
                            data = VoxyPlatformPatch.apply(data);
                            data = ModelInputPatch.apply(name, data);
                            data = ShaderLibraryPatch.apply(name, data);
                            data = IndirectDrawPatch.apply(name, data);
                        }
                        type = defineClass(name, data, 0, data.length);
                        if (name.startsWith("me.cortex.voxy.")) externalClasses++;
                    }
                } catch (IOException error) {
                    throw new ClassNotFoundException(name, error);
                }
            }
            if (resolve) resolveClass(type);
            return type;
        }
    }

    public int externalClassesLoaded() {
        return externalClasses;
    }
}
