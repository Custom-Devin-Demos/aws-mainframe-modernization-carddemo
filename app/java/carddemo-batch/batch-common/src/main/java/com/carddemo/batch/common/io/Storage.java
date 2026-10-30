package com.carddemo.batch.common.io;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/** Resolves job inputs and publishes outputs for {@code s3://} URIs or local paths. */
public final class Storage {

    private S3Client s3;

    public static boolean isS3(String location) {
        return location.startsWith("s3://");
    }

    /** Returns a local path for the input, downloading it into {@code scratch} if it lives on S3. */
    public Path fetch(String location, Path scratch) {
        if (!isS3(location)) {
            return Paths.get(location.startsWith("file:") ? URI.create(location).getPath() : location);
        }
        URI uri = URI.create(location);
        String key = uri.getPath().substring(1);
        Path target = scratch.resolve(Paths.get(key).getFileName().toString());
        try {
            Files.createDirectories(scratch);
            Files.deleteIfExists(target);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        client().getObject(GetObjectRequest.builder().bucket(uri.getHost()).key(key).build(), target);
        return target;
    }

    /** Copies every file under {@code localDir} to {@code targetRoot}, preserving relative paths. */
    public void publish(Path localDir, String targetRoot) {
        if (!isS3(targetRoot)) {
            Path root = Paths.get(targetRoot);
            if (root.toAbsolutePath().normalize().equals(localDir.toAbsolutePath().normalize())) {
                return;
            }
            forEachFile(localDir, f -> {
                Path dst = root.resolve(localDir.relativize(f).toString());
                Files.createDirectories(dst.getParent());
                Files.copy(f, dst, StandardCopyOption.REPLACE_EXISTING);
            });
            return;
        }
        URI uri = URI.create(targetRoot.endsWith("/") ? targetRoot : targetRoot + "/");
        String prefix = uri.getPath().substring(1);
        forEachFile(localDir, f -> {
            String key = prefix + localDir.relativize(f).toString().replace('\\', '/');
            client().putObject(PutObjectRequest.builder().bucket(uri.getHost()).key(key).build(), RequestBody.fromFile(f));
        });
    }

    private interface IoConsumer {
        void accept(Path p) throws IOException;
    }

    private static void forEachFile(Path dir, IoConsumer action) {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) files.filter(Files::isRegularFile).sorted()::iterator) {
                action.accept(f);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private synchronized S3Client client() {
        if (s3 == null) {
            s3 = S3Client.create();
        }
        return s3;
    }
}
