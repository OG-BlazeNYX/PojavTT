package com.pojavtiers.tagger.util;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A tiny HTTP(S) GET helper that works even where the platform DNS resolver is
 * broken (notably <b>PojavLauncher / Android</b>, where {@code InetAddress} and
 * the standard HTTP clients throw {@code UnresolvedAddressException} because
 * Android exposes no system nameservers).
 * <p>
 * Strategy:
 * <ol>
 *   <li>Try a normal {@link HttpURLConnection} (works on desktop).</li>
 *   <li>If that fails with a DNS error, resolve the host via
 *       <b>DNS-over-HTTPS</b> to Cloudflare {@code 1.1.1.1} / Google
 *       {@code 8.8.8.8} (both are IP literals, so no system DNS is needed),
 *       then open a raw {@link SSLSocket} to the resolved IP with the correct
 *       SNI + hostname verification.</li>
 * </ol>
 * Only depends on the JDK, so it can be unit-tested standalone.
 */
public final class Http {
    private static final Pattern IPV4 = Pattern.compile("\"data\"\\s*:\\s*\"(\\d{1,3}(?:\\.\\d{1,3}){3})\"");
    private static final String[] DOH_ENDPOINTS = {
            // Cloudflare JSON DoH (accept: application/dns-json)
            "https://1.1.1.1/dns-query?type=A&name=",
            "https://1.0.0.1/dns-query?type=A&name=",
            // Google JSON DoH (/resolve)
            "https://8.8.8.8/resolve?type=A&name=",
            "https://8.8.4.4/resolve?type=A&name="
    };

    private Http() {}

    /** GET the given URL as a UTF-8 string, with an Android/Pojav DNS fallback. */
    public static String get(String urlStr) throws Exception {
        try {
            return direct(urlStr);
        } catch (Exception e) {
            if (!isDnsError(e)) throw e;
            // System DNS is unusable (Pojav/Android) -> resolve via DoH and connect by IP.
            return getViaDoh(urlStr);
        }
    }

    /** Returns true if the failure chain looks like a DNS resolution problem. */
    public static boolean isDnsError(Throwable t) {
        while (t != null) {
            String n = t.getClass().getName();
            if (n.contains("UnresolvedAddressException")
                    || n.contains("UnknownHostException")) {
                return true;
            }
            // ConnectException with no useful message also commonly wraps the above on Pojav
            t = t.getCause();
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 1) Normal path
    // ------------------------------------------------------------------
    private static String direct(String urlStr) throws Exception {
        HttpURLConnection conn = (HttpURLConnection)
                URI.create(urlStr).toURL().openConnection(Proxy.NO_PROXY);
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(20_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "PojavTierTagger/1.0");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Accept-Encoding", "identity");

        int code = conn.getResponseCode();
        InputStream is = (code >= 200 && code < 400) ? conn.getInputStream() : conn.getErrorStream();
        String body = readAll(is);
        conn.disconnect();
        if (code < 200 || code >= 300) throw new RuntimeException("HTTP " + code);
        return body;
    }

    // ------------------------------------------------------------------
    // 2) DoH + direct-IP path (Pojav/Android)
    // ------------------------------------------------------------------
    /** Forces the DoH + direct-IP path (also used as the Pojav/Android fallback). */
    public static String getViaDoh(String urlStr) throws Exception {
        URI uri = URI.create(urlStr);
        String host = uri.getHost();
        if (host == null) throw new RuntimeException("Bad URL (no host): " + urlStr);
        int port = uri.getPort() > 0 ? uri.getPort() : ("http".equalsIgnoreCase(uri.getScheme()) ? 80 : 443);
        String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty()) ? "/" : uri.getRawPath();
        if (uri.getRawQuery() != null) path += "?" + uri.getRawQuery();

        String ip = resolveViaDoh(host);
        if (ip == null) throw new RuntimeException("DoH could not resolve " + host);

        return httpsGetByIp(ip, host, port, path);
    }

    /** Resolves an A record for {@code host} using DNS-over-HTTPS (IP-literal servers). */
    public static String resolveViaDoh(String host) {
        for (String base : DOH_ENDPOINTS) {
            try {
                URI dohUri = URI.create(base + host);
                HttpsURLConnection c = (HttpsURLConnection) dohUri.toURL().openConnection(Proxy.NO_PROXY);
                c.setConnectTimeout(10_000);
                c.setReadTimeout(10_000);
                c.setRequestProperty("accept", "application/dns-json");
                c.setRequestProperty("User-Agent", "PojavTierTagger/1.0");
                int code = c.getResponseCode();
                if (code != 200) {
                    c.disconnect();
                    continue;
                }
                String json = readAll(c.getInputStream());
                c.disconnect();
                Matcher m = IPV4.matcher(json);
                if (m.find()) {
                    return m.group(1);
                }
            } catch (Exception ignored) {
                // try next DoH endpoint
            }
        }
        return null;
    }

    /** Opens a raw TLS socket to {@code ip} but presents {@code host} for SNI + cert validation. */
    private static String httpsGetByIp(String ip, String host, int port, String path) throws Exception {
        // Build an InetAddress from the literal IP carrying the real hostname, with NO DNS lookup.
        InetAddress addr = InetAddress.getByAddress(host, parseIpv4(ip));

        Socket plain = new Socket();
        plain.connect(new InetSocketAddress(addr, port), 15_000);
        plain.setSoTimeout(20_000);

        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        SSLSocket ssl = (SSLSocket) factory.createSocket(plain, host, port, true);

        SSLParameters params = ssl.getSSLParameters();
        params.setServerNames(List.of(new SNIHostName(host)));
        params.setEndpointIdentificationAlgorithm("HTTPS"); // verify cert against `host`
        ssl.setSSLParameters(params);
        ssl.startHandshake();

        String request = "GET " + path + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "User-Agent: PojavTierTagger/1.0\r\n"
                + "Accept: application/json\r\n"
                + "Accept-Encoding: identity\r\n"
                + "Connection: close\r\n\r\n";

        OutputStream out = ssl.getOutputStream();
        out.write(request.getBytes(StandardCharsets.UTF_8));
        out.flush();

        byte[] raw = readAllBytes(ssl.getInputStream());
        ssl.close();

        return parseHttpResponse(raw);
    }

    // ------------------------------------------------------------------
    // helpers
    // ------------------------------------------------------------------
    private static byte[] parseIpv4(String ip) {
        String[] parts = ip.split("\\.");
        byte[] b = new byte[4];
        for (int i = 0; i < 4; i++) b[i] = (byte) (Integer.parseInt(parts[i]) & 0xFF);
        return b;
    }

    /** Splits headers/body and de-chunks if needed; returns the body as UTF-8. */
    private static String parseHttpResponse(byte[] raw) {
        int sep = indexOf(raw, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        if (sep < 0) return new String(raw, StandardCharsets.UTF_8);

        String headers = new String(raw, 0, sep, StandardCharsets.US_ASCII);
        int bodyStart = sep + 4;
        byte[] body = new byte[raw.length - bodyStart];
        System.arraycopy(raw, bodyStart, body, 0, body.length);

        // status check
        String statusLine = headers.split("\r\n", 2)[0];
        boolean chunked = headers.toLowerCase().contains("transfer-encoding: chunked");

        byte[] decoded = chunked ? dechunk(body) : body;
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private static byte[] dechunk(byte[] body) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int pos = 0;
        while (pos < body.length) {
            int lineEnd = indexOf(body, "\r\n".getBytes(StandardCharsets.US_ASCII), pos);
            if (lineEnd < 0) break;
            String sizeHex = new String(body, pos, lineEnd - pos, StandardCharsets.US_ASCII).trim();
            if (sizeHex.contains(";")) sizeHex = sizeHex.substring(0, sizeHex.indexOf(';'));
            int size;
            try {
                size = Integer.parseInt(sizeHex.trim(), 16);
            } catch (NumberFormatException e) {
                break;
            }
            if (size == 0) break;
            int dataStart = lineEnd + 2;
            if (dataStart + size > body.length) size = body.length - dataStart;
            out.write(body, dataStart, size);
            pos = dataStart + size + 2; // skip trailing CRLF
        }
        return out.toByteArray();
    }

    private static int indexOf(byte[] data, byte[] target) {
        return indexOf(data, target, 0);
    }

    private static int indexOf(byte[] data, byte[] target, int from) {
        outer:
        for (int i = Math.max(0, from); i <= data.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (data[i + j] != target[j]) continue outer;
            }
            return i;
        }
        return -1;
    }

    private static String readAll(InputStream is) throws Exception {
        if (is == null) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            char[] buf = new char[8192];
            int n;
            while ((n = br.read(buf)) != -1) sb.append(buf, 0, n);
        }
        return sb.toString();
    }

    private static byte[] readAllBytes(InputStream is) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) out.write(buf, 0, n);
        return out.toByteArray();
    }
}
