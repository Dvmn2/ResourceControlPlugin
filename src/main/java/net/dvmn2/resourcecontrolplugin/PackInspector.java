package net.dvmn2.resourcecontrolplugin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
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
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Скачивает пак по URL потоком (БЕЗ записи на диск), на лету считает SHA-1 и размер,
 * проверяет, что это zip с pack.mcmeta в корне, и ищет core-шейдеры. Вызывать ТОЛЬКО из асинхронного потока.
 */
public final class PackInspector {

    public record Result(String sha1, long size, String packFormat, boolean hasShaders) {
    }

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private static final int MAX_MCMETA_BYTES = 64 * 1024;

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

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(60))
                .header("User-Agent", "ResourceControlPlugin/1.0")
                .GET()
                .build();
        HttpResponse<InputStream> response = HTTP.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream body = response.body()) {
            if (response.statusCode() != 200) {
                throw new IOException("HTTP " + response.statusCode());
            }
            if (!"https".equalsIgnoreCase(response.uri().getScheme())) {
                throw new IOException("редирект на не-https адрес");
            }

            return analyze(body, maxBytes);
        }
    }

    /** Один проход по потоку: лимит -> SHA-1 -> разбор zip. Без записи на диск. */
    static Result analyze(InputStream body, long maxBytes) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }

        // Всё в один проход, без записи на диск: лимит размера и подсчёт байт -> SHA-1 -> разбор zip.
        LimitedInputStream limited = new LimitedInputStream(body, maxBytes);
        DigestInputStream digested = new DigestInputStream(limited, digest);

        boolean anyEntry = false;
        boolean mcmetaFound = false;
        boolean shaders = false;
        String format = "не указан";
        try (ZipInputStream zip = new ZipInputStream(digested)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                anyEntry = true;
                String name = entry.getName();
                if (name.equals("pack.mcmeta")) {
                    mcmetaFound = true;
                    format = readPackFormat(zip);
                } else {
                    String[] parts = name.split("/");
                    if (parts.length >= 3 && parts[0].equals("assets") && parts[2].equals("shaders")) {
                        shaders = true;
                    }
                }
            }
            // Хвост файла (центральный каталог zip) тоже должен попасть в SHA-1 и в подсчёт размера.
            digested.transferTo(OutputStream.nullOutputStream());
        } catch (ZipException ex) {
            throw new IOException("файл не является корректным zip-архивом: " + ex.getMessage());
        }

        long total = limited.count();
        if (total == 0) {
            throw new IOException("пустой файл");
        }
        if (!anyEntry) {
            throw new IOException("файл не является zip-архивом (возможно, ссылка ведёт на html-страницу)");
        }
        if (!mcmetaFound) {
            throw new IOException("в корне архива нет pack.mcmeta (возможно, всё лежит внутри вложенной папки)");
        }
        return new Result(HexFormat.of().formatHex(digest.digest()), total, format, shaders);
    }

    /** Читает pack.mcmeta из текущей записи ZipInputStream (не закрывая поток). */
    private static String readPackFormat(InputStream entryStream) throws IOException {
        // pack.mcmeta крошечный; жёсткий потолок защищает от мусора в архиве
        byte[] data = entryStream.readNBytes(MAX_MCMETA_BYTES + 1);
        if (data.length > MAX_MCMETA_BYTES) {
            return "pack.mcmeta слишком большой";
        }
        try (Reader reader = new InputStreamReader(new ByteArrayInputStream(data), StandardCharsets.UTF_8)) {
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
        } catch (JsonParseException | IllegalStateException ex) {
            return "pack.mcmeta не читается";
        }
    }

    /** Считает прочитанные байты и обрывает чтение при превышении лимита. */
    private static final class LimitedInputStream extends FilterInputStream {
        private final long max;
        private long count;

        LimitedInputStream(InputStream in, long max) {
            super(in);
            this.max = max;
        }

        long count() {
            return count;
        }

        private void add(long n) throws IOException {
            count += n;
            if (count > max) {
                throw new IOException("файл больше лимита " + (max / 1048576) + " МБ");
            }
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                add(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) {
                add(n);
            }
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            // skip() обошёл бы подсчёт и SHA-1 — читаем явно
            byte[] tmp = new byte[(int) Math.min(8192, Math.max(0, n))];
            int r = tmp.length == 0 ? 0 : read(tmp, 0, tmp.length);
            return Math.max(r, 0);
        }

        @Override
        public boolean markSupported() {
            return false;
        }
    }
}
