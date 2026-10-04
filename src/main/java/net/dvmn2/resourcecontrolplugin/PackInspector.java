package net.dvmn2.resourcecontrolplugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Скачивает пак по URL (во ВРЕМЕННЫЙ файл), считает SHA-1 и размер, проверяет, что это zip
 * с pack.mcmeta в корне, и ищет core-шейдеры. Вызывать ТОЛЬКО из асинхронного потока.
 */
public final class PackInspector {

    public record Result(String sha1, long size, String packFormat, boolean hasShaders) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private PackInspector() {
    }

    public static Result inspect(String url, long maxBytes) throws IOException, InterruptedException {
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException ex) {
            throw new IOException("некорректный URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IOException("разрешены только https-ссылки");
        }

        Path tmp = Files.createTempFile("resourcecontrol-", ".zip");
        try {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(60))
                    .header("User-Agent", "ResourceControlPlugin/1.0")
                    .GET()
                    .build();
            HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
            String sha1;
            long total = 0;
            try (InputStream in = response.body()) {
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode());
                }
                if (!"https".equalsIgnoreCase(response.uri().getScheme())) {
                    throw new IOException("редирект на не-https адрес");
                }
                MessageDigest digest = MessageDigest.getInstance("SHA-1");
                try (OutputStream out = Files.newOutputStream(tmp)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    while ((read = in.read(buffer)) >= 0) {
                        total += read;
                        if (total > maxBytes) {
                            throw new IOException("файл больше лимита " + (maxBytes / 1048576) + " МБ");
                        }
                        digest.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                    }
                }
                sha1 = HexFormat.of().formatHex(digest.digest());
            } catch (NoSuchAlgorithmException ex) {
                throw new IOException(ex);
            }
            if (total == 0) {
                throw new IOException("пустой файл");
            }

            try (ZipFile zip = new ZipFile(tmp.toFile())) {
                ZipEntry mcmeta = zip.getEntry("pack.mcmeta");
                if (mcmeta == null) {
                    throw new IOException("в корне архива нет pack.mcmeta (возможно, всё лежит внутри вложенной папки)");
                }
                String format = readPackFormat(zip, mcmeta);
                boolean shaders = false;
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    String[] parts = entries.nextElement().getName().split("/");
                    if (parts.length >= 3 && parts[0].equals("assets") && parts[2].equals("shaders")) {
                        shaders = true;
                        break;
                    }
                }
                return new Result(sha1, total, format, shaders);
            }
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private static String readPackFormat(ZipFile zip, ZipEntry mcmeta) {
        try (Reader reader = new InputStreamReader(zip.getInputStream(mcmeta), StandardCharsets.UTF_8)) {
            JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
            JsonObject pack = root.getAsJsonObject("pack");
            if (pack == null) {
                return "нет секции pack";
            }
            if (pack.has("pack_format")) {
                return pack.get("pack_format").toString();
            }
            if (pack.has("min_format")) {
                return "min_format=" + pack.get("min_format") + ", max_format=" + pack.get("max_format");
            }
            return "не указан";
        } catch (IOException | JsonParseException | IllegalStateException ex) {
            return "pack.mcmeta не читается";
        }
    }
}
