package com.flipnet.bridge;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;

// Regroupe les outils partagés Android.
final class AndroidFeatureSupport {
    private static final String WEB_BROWSER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36 FlipNet/"
                    + BuildConfig.VERSION_NAME;

    // Initialise les outils Android partagés.
    private AndroidFeatureSupport() {
    }

    // Regroupe un hôte et un port.
    static final class HostPort {
        final String host;
        final int port;

        // Initialise un hôte et son port.
        HostPort(String host, int port) {
            this.host = host;
            this.port = port;
        }
    }

    // Indique si la langue est le français.
    static boolean isFrench(String language) {
        return "fr".equalsIgnoreCase(language);
    }

    // Choisit le texte localisé.
    static String tr(String language, String english, String french) {
        return isFrench(language) ? french : english;
    }

    // Convertit le texte pour le Flipper.
    static String asciiText(String value) {
        if (value == null) return "";
        String mapped = value
                .replace('\u00A0', ' ')
                .replace("\u00AD", "")
                .replace("\u200B", "")
                .replace("\uFEFF", "")
                .replace("Æ", "AE")
                .replace("æ", "ae")
                .replace("Œ", "OE")
                .replace("œ", "oe")
                .replace("ẞ", "SS")
                .replace("ß", "ss")
                .replace("Ø", "O")
                .replace("ø", "o")
                .replace("Ł", "L")
                .replace("ł", "l")
                .replace("Đ", "D")
                .replace("đ", "d")
                .replace("Ð", "D")
                .replace("ð", "d")
                .replace("Þ", "TH")
                .replace("þ", "th")
                .replace("‘", "'")
                .replace("’", "'")
                .replace("‚", "'")
                .replace("‛", "'")
                .replace("“", "\"")
                .replace("”", "\"")
                .replace("„", "\"")
                .replace("‟", "\"")
                .replace("‐", "-")
                .replace("‑", "-")
                .replace("‒", "-")
                .replace("–", "-")
                .replace("—", "-")
                .replace("−", "-")
                .replace("…", "...")
                .replace("•", "-")
                .replace("·", "-")
                .replace("∙", "-")
                .replace("◦", "-")
                .replace("→", "->")
                .replace("←", "<-")
                .replace("↔", "<->")
                .replace("⇒", "=>")
                .replace("⇐", "<=")
                .replace("×", "x")
                .replace("÷", "/")
                .replace("±", "+/-")
                .replace("≈", "~")
                .replace("≠", "!=")
                .replace("≤", "<=")
                .replace("≥", ">=")
                .replace("°", " deg")
                .replace("€", " EUR")
                .replace("£", " GBP")
                .replace("¥", " JPY")
                .replace("¢", " cent")
                .replace("©", "(C)")
                .replace("®", "(R)")
                .replace("™", "(TM)")
                .replace("½", "1/2")
                .replace("¼", "1/4")
                .replace("¾", "3/4")
                .replace("ⓘ", "(i)")
                .replace("ℹ", "(i)")
                .replace("▶", "[play]")
                .replace("►", "[play]")
                .replace("🔊", "[audio]")
                .replace("🔉", "[audio]")
                .replace("🔈", "[audio]")
                .replace("🎧", "[audio]")
                .replace("ˈ", "")
                .replace("ˌ", "")
                .replace("ː", ":")
                .replace("ˑ", ":")
                .replace("ʼ", "'")
                .replace("ʰ", "h")
                .replace("ʲ", "y")
                .replace("ʷ", "w")
                .replace("ʱ", "h")
                .replace("ʴ", "r")
                .replace("ɐ", "a")
                .replace("ɑ", "a")
                .replace("ɒ", "o")
                .replace("ɓ", "b")
                .replace("ʙ", "b")
                .replace("β", "b")
                .replace("ɔ", "o")
                .replace("ɕ", "sh")
                .replace("ç", "c")
                .replace("ɗ", "d")
                .replace("ɖ", "d")
                .replace("ɘ", "e")
                .replace("ə", "e")
                .replace("ɚ", "er")
                .replace("ɛ", "e")
                .replace("ɜ", "e")
                .replace("ɝ", "er")
                .replace("ɞ", "o")
                .replace("ɟ", "j")
                .replace("ʄ", "j")
                .replace("ɡ", "g")
                .replace("ɢ", "g")
                .replace("ʛ", "g")
                .replace("ɦ", "h")
                .replace("ɧ", "h")
                .replace("ħ", "h")
                .replace("ɥ", "w")
                .replace("ʜ", "h")
                .replace("ɨ", "i")
                .replace("ɪ", "i")
                .replace("ɫ", "l")
                .replace("ɬ", "l")
                .replace("ɭ", "l")
                .replace("ɮ", "l")
                .replace("ʟ", "l")
                .replace("ɱ", "m")
                .replace("ɯ", "u")
                .replace("ɰ", "w")
                .replace("ŋ", "ng")
                .replace("ɳ", "n")
                .replace("ɲ", "ny")
                .replace("ɴ", "n")
                .replace("ɵ", "o")
                .replace("ɸ", "f")
                .replace("ɹ", "r")
                .replace("ɻ", "r")
                .replace("ɽ", "r")
                .replace("ɾ", "r")
                .replace("ʀ", "r")
                .replace("ʁ", "r")
                .replace("ɺ", "r")
                .replace("ɼ", "r")
                .replace("ʂ", "sh")
                .replace("ʃ", "sh")
                .replace("ʈ", "t")
                .replace("θ", "th")
                .replace("ʉ", "u")
                .replace("ʊ", "u")
                .replace("ʋ", "v")
                .replace("ⱱ", "v")
                .replace("ʌ", "u")
                .replace("ɣ", "g")
                .replace("ɤ", "o")
                .replace("χ", "kh")
                .replace("ʎ", "y")
                .replace("ʏ", "y")
                .replace("ʐ", "zh")
                .replace("ʑ", "zh")
                .replace("ʒ", "zh")
                .replace("ʔ", "'")
                .replace("ʕ", "'")
                .replace("ʡ", "'");
        String decomposed = Normalizer.normalize(mapped, Normalizer.Form.NFKD);
        StringBuilder output = new StringBuilder(decomposed.length());
        for (int index = 0; index < decomposed.length();) {
            int codePoint = decomposed.codePointAt(index);
            int type = Character.getType(codePoint);
            if (codePoint == '\r') {
                // Conserve les retours LF.
            } else if (codePoint < 128) {
                output.appendCodePoint(codePoint);
            } else if (type == Character.NON_SPACING_MARK
                    || type == Character.COMBINING_SPACING_MARK
                    || type == Character.ENCLOSING_MARK) {
                // Ignore les signes déjà convertis.
            } else if (Character.isDigit(codePoint)) {
                int digit = Character.digit(codePoint, 10);
                output.append(digit >= 0 ? digit : ' ');
            } else {
                // Retire les signes Unicode restants.
                output.append(' ');
            }
            index += Character.charCount(codePoint);
        }
        String[] lines = output.toString().split("\n", -1);
        StringBuilder cleaned = new StringBuilder(output.length());
        for (int index = 0; index < lines.length; index++) {
            if (index > 0) cleaned.append('\n');
            cleaned.append(lines[index].replaceAll("[\\t\\x0B\\f ]+", " ").trim());
        }
        return cleaned.toString().trim();
    }

    // Limite un texte UTF-8.
    static String truncateUtf8(String value, int maxBytes) {
        if (value == null) return "";
        StringBuilder result = new StringBuilder();
        int used = 0;
        for (int index = 0; index < value.length();) {
            int codePoint = value.codePointAt(index);
            String part = new String(Character.toChars(codePoint));
            int length = part.getBytes(StandardCharsets.UTF_8).length;
            if (used + length > maxBytes) break;
            result.append(part);
            used += length;
            index += Character.charCount(codePoint);
        }
        return result.toString();
    }

    // Sépare un hôte et un port.
    static HostPort parseHostPort(String value, int defaultPort) throws Exception {
        String raw = value.trim();
        if (raw.isEmpty()) throw new UserError("Indique un domaine");
        URL parsed = new URL(raw.contains("://") ? raw : "https://" + raw);
        String host = parsed.getHost();
        int port = parsed.getPort() > 0 ? parsed.getPort() : defaultPort;
        if (host.isEmpty() || port < 1 || port > 65535) throw new UserError("Domaine ou port invalide");
        return new HostPort(host, port);
    }

    // Lit le nom commun du certificat.
    static String commonName(String distinguishedName) {
        for (String item : distinguishedName.split(",")) {
            String trimmed = item.trim();
            if (trimmed.startsWith("CN=")) return trimmed.substring(3);
        }
        return distinguishedName;
    }

    // Réduit un texte pour l’affichage.
    static String shortText(String value, int limit) {
        String text = value == null ? "" : value.replaceAll("\\s+", " ").trim();
        return text.length() <= limit ? text : text.substring(0, Math.max(0, limit - 3)).trim() + "...";
    }

    // Télécharge puis analyse une réponse JSON.
    static JSONObject getJson(String url) throws Exception {
        return new JSONObject(new String(getBytes(url, "application/json"), StandardCharsets.UTF_8));
    }

    // Télécharge une réponse bornée.
    static byte[] getBytes(String address, String accept) throws Exception {
        return getBytes(address, accept, 1_000_000, false);
    }

    // Télécharge une réponse bornée.
    static byte[] getBytes(String address, String accept, int maxBytes) throws Exception {
        return getBytes(address, accept, maxBytes, false);
    }

    // Télécharge une page Web bornée.
    static byte[] getWebBytes(String address, String accept, int maxBytes) throws Exception {
        return getBytes(address, accept, maxBytes, true);
    }

    // Télécharge une réponse bornée.
    static byte[] getBytes(String address, String accept, int maxBytes, boolean browserHeaders)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setRequestProperty(
                "User-Agent",
                browserHeaders ? WEB_BROWSER_USER_AGENT
                        : "FlipNet-Android/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty("Accept", accept);
        if (browserHeaders) {
            connection.setRequestProperty("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.7");
            connection.setRequestProperty("Cache-Control", "no-cache");
        }
        connection.setInstanceFollowRedirects(true);
        int status = connection.getResponseCode();
        if (status < 200 || status >= 300) {
            connection.disconnect();
            throw new UserError("HTTP " + status);
        }
        try (InputStream input = connection.getInputStream();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            int total = 0;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new UserError("Réponse trop volumineuse");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        } finally {
            connection.disconnect();
        }
    }

    // Encode une valeur pour une URL.
    static String urlEncode(String value) throws Exception {
        return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
    }

    // Formate un nombre décimal.
    static String format1(double value) {
        return Double.isNaN(value) ? "?" : String.format(Locale.FRANCE, "%.1f", value);
    }

    // Formate un nombre entier.
    static String format0(double value) {
        return Double.isNaN(value) ? "?" : String.format(Locale.FRANCE, "%.0f", value);
    }

    // Traduit le code météo.
    static String weatherDescription(int code, String language) {
        if (!isFrench(language)) {
            if (code == 0) return "Clear sky";
            if (code == 1 || code == 2) return "Partly cloudy";
            if (code == 3) return "Overcast";
            if (code == 45 || code == 48) return "Fog";
            if (code >= 51 && code <= 57) return "Drizzle";
            if (code >= 61 && code <= 67) return "Rain";
            if (code >= 71 && code <= 77) return "Snow";
            if (code >= 80 && code <= 82) return "Showers";
            if (code >= 85 && code <= 86) return "Snow showers";
            if (code >= 95) return "Thunderstorm";
            return "Weather unavailable";
        }
        switch (code) {
            case 0: return "Ciel dégagé";
            case 1: return "Plutôt dégagé";
            case 2: return "Partiellement nuageux";
            case 3: return "Couvert";
            case 45:
            case 48: return "Brouillard";
            case 51:
            case 53:
            case 55: return "Bruine";
            case 61:
            case 63:
            case 65: return "Pluie";
            case 71:
            case 73:
            case 75: return "Neige";
            case 80:
            case 81:
            case 82: return "Averses";
            case 95:
            case 96:
            case 99: return "Orage";
            default: return "Météo";
        }
    }}
