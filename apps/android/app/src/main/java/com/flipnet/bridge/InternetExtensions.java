package com.flipnet.bridge;

import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

// Regroupe la météo le Web Wikipédia et les flux.
final class InternetExtensions {
    private final AndroidStateStore store;

    // Initialise les fonctions Internet.
    InternetExtensions(AndroidStateStore store) {
        this.store = store;
    }

    // Construit le libellé d’une ville.
    private String weatherPlaceLabel(JSONObject place) {
        String name = place.optString("name", "Lieu");
        String countryCode = place.optString("country_code", "").toUpperCase(Locale.ROOT);
        String admin1 = place.optString("admin1", "");
        StringBuilder label = new StringBuilder(name);
        if (!countryCode.isEmpty()) label.append(" (").append(countryCode).append(")");
        if (!admin1.isEmpty() && !admin1.equalsIgnoreCase(name)) {
            label.append(" - ").append(admin1);
        }
        return label.toString().replace('|', '/');
    }

    // Recherche des villes météo.
    ServiceResult weatherSearch(String query, String language) throws Exception {
        query = query.trim();
        if (query.length() < 3) throw new UserError(AndroidFeatureSupport.tr(language, "Enter at least 3 characters", "Saisis au moins 3 caracteres"));
        JSONObject geocode = AndroidFeatureSupport.getJson(
                "https://geocoding-api.open-meteo.com/v1/search?count=8&language=" + language + "&format=json&name="
                        + AndroidFeatureSupport.urlEncode(query));
        JSONArray results = geocode.optJSONArray("results");
        if (results == null || results.length() == 0) throw new UserError(AndroidFeatureSupport.tr(language, "City not found", "Ville introuvable"));
        StringBuilder body = new StringBuilder("FNLOC1");
        int count = Math.min(8, results.length());
        for (int index = 0; index < count; index++) {
            JSONObject place = results.getJSONObject(index);
            long locationId = place.optLong("id", 0L);
            if (locationId <= 0L) continue;
            body.append('\n').append(locationId).append('|').append(weatherPlaceLabel(place));
        }
        if (body.indexOf("\n") < 0) throw new UserError(AndroidFeatureSupport.tr(language, "City not found", "Ville introuvable"));
        return new ServiceResult(AndroidFeatureSupport.tr(language, "Choose a city", "Choisir une ville"), body.toString());
    }

    // Charge la météo d’une ville.
    ServiceResult weatherLocation(String locationId, String language) throws Exception {
        locationId = locationId.trim();
        if (!locationId.matches("[1-9][0-9]*")) throw new UserError(AndroidFeatureSupport.tr(language, "Invalid city", "Ville invalide"));
        JSONObject place = AndroidFeatureSupport.getJson(
                "https://geocoding-api.open-meteo.com/v1/get?id=" + AndroidFeatureSupport.urlEncode(locationId));
        return weatherForPlace(place, language);
    }

    // Charge la météo d’une position.
    private ServiceResult weatherForPlace(JSONObject place, String language) throws Exception {
        double latitude = place.getDouble("latitude");
        double longitude = place.getDouble("longitude");
        String name = place.optString("name", AndroidFeatureSupport.tr(language, "Weather", "Meteo"));
        String country = place.optString("country", "");
        String title = country.isEmpty() ? name : name + ", " + country;

        String forecastUrl = "https://api.open-meteo.com/v1/forecast?timezone=auto"
                + "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m"
                + "&daily=temperature_2m_min,temperature_2m_max,precipitation_probability_max"
                + "&forecast_days=2&latitude=" + latitude + "&longitude=" + longitude;
        JSONObject forecast = AndroidFeatureSupport.getJson(forecastUrl);
        JSONObject current = forecast.getJSONObject("current");
        JSONObject daily = forecast.getJSONObject("daily");
        int code = current.optInt("weather_code", -1);
        String body = AndroidFeatureSupport.weatherDescription(code, language)
                + AndroidFeatureSupport.tr(language, "\nTemperature: ", "\nTemperature : ") + AndroidFeatureSupport.format1(current.optDouble("temperature_2m")) + " C"
                + AndroidFeatureSupport.tr(language, "\nFeels like: ", "\nRessenti : ") + AndroidFeatureSupport.format1(current.optDouble("apparent_temperature")) + " C"
                + AndroidFeatureSupport.tr(language, "\nMin / Max: ", "\nMin / Max : ") + AndroidFeatureSupport.format1(daily.getJSONArray("temperature_2m_min").optDouble(0))
                + " / " + AndroidFeatureSupport.format1(daily.getJSONArray("temperature_2m_max").optDouble(0)) + " C"
                + AndroidFeatureSupport.tr(language, "\nMax rain: ", "\nPluie max : ") + AndroidFeatureSupport.format0(daily.getJSONArray("precipitation_probability_max").optDouble(0)) + " %"
                + AndroidFeatureSupport.tr(language, "\nWind: ", "\nVent : ") + AndroidFeatureSupport.format1(current.optDouble("wind_speed_10m")) + " km/h";
        return new ServiceResult(title.trim(), body);
    }

    // Charge la météo demandée.
    ServiceResult weather(String city, String language) throws Exception {
        city = city.trim();
        if (city.isEmpty()) throw new UserError(AndroidFeatureSupport.tr(language, "Enter a city", "Indique une ville"));
        JSONObject geocode = AndroidFeatureSupport.getJson(
                "https://geocoding-api.open-meteo.com/v1/search?count=1&language=" + language + "&format=json&name="
                        + AndroidFeatureSupport.urlEncode(city));
        JSONArray results = geocode.optJSONArray("results");
        if (results == null || results.length() == 0)
            throw new UserError(AndroidFeatureSupport.tr(language, "City not found", "Ville introuvable"));
        return weatherForPlace(results.getJSONObject(0), language);
    }

    // Recherche des articles Wikipédia.
    private JSONArray wikipediaMatches(String query, int limit, String language) throws Exception {
        JSONObject payload = AndroidFeatureSupport.getJson(
                "https://" + language + ".wikipedia.org/w/api.php?action=query&list=search&utf8=1"
                        + "&format=json&formatversion=2&srlimit=" + limit
                        + "&srsearch=" + AndroidFeatureSupport.urlEncode(query));
        JSONObject queryObject = payload.optJSONObject("query");
        return queryObject == null ? null : queryObject.optJSONArray("search");
    }

    // Construit le catalogue Wikipédia.
    ServiceResult wikipediaSearch(String query, String language) throws Exception {
        query = query.trim();
        if (query.length() < 2) throw new UserError(AndroidFeatureSupport.tr(language, "Enter at least 2 characters", "Saisis au moins 2 caracteres"));
        JSONArray matches = wikipediaMatches(query, 8, language);
        if (matches == null || matches.length() == 0)
            throw new UserError(AndroidFeatureSupport.tr(language, "No article found", "Aucun article trouve"));
        StringBuilder body = new StringBuilder("FNWIKI1");
        for (int index = 0; index < Math.min(8, matches.length()); index++) {
            JSONObject match = matches.getJSONObject(index);
            long pageId = match.optLong("pageid", 0L);
            String title = match.optString("title", "").replace('|', '/');
            if (pageId > 0L && !title.isEmpty())
                body.append('\n').append(pageId).append('|').append(title);
        }
        if (body.indexOf("\n") < 0) throw new UserError(AndroidFeatureSupport.tr(language, "No article found", "Aucun article trouve"));
        return new ServiceResult(AndroidFeatureSupport.tr(language, "Choose an article", "Choisir un article"), body.toString());
    }

    // Charge un article Wikipédia.
    ServiceResult wikipediaPage(String pageId, String language) throws Exception {
        pageId = pageId.trim();
        if (!pageId.matches("[1-9][0-9]*")) throw new UserError(AndroidFeatureSupport.tr(language, "Invalid article", "Article invalide"));
        JSONObject payload = AndroidFeatureSupport.getJson(
                "https://" + language + ".wikipedia.org/w/api.php?action=query&prop=extracts&exintro=1"
                        + "&explaintext=1&redirects=1&format=json&formatversion=2&pageids="
                        + AndroidFeatureSupport.urlEncode(pageId));
        JSONObject queryObject = payload.optJSONObject("query");
        JSONArray pages = queryObject == null ? null : queryObject.optJSONArray("pages");
        JSONObject page = pages == null || pages.length() == 0 ? null : pages.getJSONObject(0);
        String title = page == null ? "Wikipedia" : page.optString("title", "Wikipedia");
        String extract = page == null ? "" : page.optString("extract", "");
        if (extract.isEmpty()) throw new UserError(AndroidFeatureSupport.tr(language, "No summary available", "Article sans resume disponible"));
        return new ServiceResult(title, extract);
    }

    // Recherche puis charge un article.
    ServiceResult wikipedia(String query, String language) throws Exception {
        query = query.trim();
        if (query.isEmpty()) throw new UserError(AndroidFeatureSupport.tr(language, "Enter a search", "Indique une recherche"));
        JSONArray matches = wikipediaMatches(query, 1, language);
        if (matches == null || matches.length() == 0)
            throw new UserError(AndroidFeatureSupport.tr(language, "No article found", "Aucun article trouve"));
        return wikipediaPage(String.valueOf(matches.getJSONObject(0).optLong("pageid", 0L)), language);
    }

    // Décrit un résultat Web Android.
    private static final class WebResult {
        final String token;
        final String title;
        final String source;
        final String snippet;
        final String url;

        // Initialise un résultat Web.
        WebResult(String token, String title, String source, String snippet, String url) {
            this.token = token;
            this.title = title;
            this.source = source;
            this.snippet = snippet;
            this.url = url;
        }

        // Convertit un résultat Web en JSON.
        JSONObject toJson() throws Exception {
            JSONObject value = new JSONObject();
            value.put("token", token);
            value.put("title", title);
            value.put("source", source);
            value.put("snippet", snippet);
            value.put("url", url);
            return value;
        }
    }

    // Nettoie le texte HTML.
    private String htmlText(String value) {
        return Html.fromHtml(value == null ? "" : value, Html.FROM_HTML_MODE_LEGACY)
                .toString().replaceAll("\\s+", " ").trim();
    }

    // Lit un attribut HTML.
    private String htmlAttribute(String attributes, String name) {
        Pattern pattern = Pattern.compile(
                "(?is)\\b" + Pattern.quote(name) + "\\s*=\\s*([\\\"'])(.*?)\\1");
        Matcher matcher = pattern.matcher(attributes);
        return matcher.find() ? matcher.group(2) : "";
    }

    // Extrait l’URL réelle de DuckDuckGo.
    private String unwrapDuckDuckGoUrl(String value) throws Exception {
        String raw = value == null ? "" : value.trim().replace("&amp;", "&");
        if (raw.startsWith("//")) raw = "https:" + raw;
        else if (raw.startsWith("/")) raw = "https://duckduckgo.com" + raw;
        URL parsed = new URL(raw);
        if (parsed.getHost().toLowerCase(Locale.ROOT).endsWith("duckduckgo.com")) {
            for (String field : parsed.getQuery() == null ? new String[0] : parsed.getQuery().split("&")) {
                String[] pair = field.split("=", 2);
                if (pair.length == 2 && pair[0].equals("uddg")) {
                    raw = URLDecoder.decode(pair[1], "UTF-8");
                    break;
                }
            }
        }
        URL target = new URL(raw);
        if (!target.getProtocol().equalsIgnoreCase("http")
                && !target.getProtocol().equalsIgnoreCase("https")) return "";
        return new URL(target.getProtocol(), target.getHost(), target.getPort(), target.getFile())
                .toString();
    }

    // Construit un identifiant de résultat Web.
    private String webToken(String url) {
        CRC32 crc = new CRC32();
        crc.update(url.getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.ROOT, "%08X", crc.getValue());
    }

    // Identifie la source du résultat Web.
    private String webSource(String value) throws Exception {
        String host = new URL(value).getHost();
        return host.toLowerCase(Locale.ROOT).startsWith("www.") ? host.substring(4) : host;
    }

    // Analyse les résultats DuckDuckGo.
    private List<WebResult> parseWebResults(String document) throws Exception {
        Pattern anchors = Pattern.compile("(?is)<a\\b([^>]*)>(.*?)</a>");
        Pattern snippets = Pattern.compile(
                "(?is)<(?:a|div|span|td)\\b[^>]*class=([\"'])[^\"']*"
                        + "(?:result__snippet|result-snippet|result-snippet__text)[^\"']*\\1[^>]*>"
                        + "(.*?)</(?:a|div|span|td)>");
        ArrayList<String> snippetValues = new ArrayList<>();
        Matcher snippetMatcher = snippets.matcher(document);
        while (snippetMatcher.find()) snippetValues.add(htmlText(snippetMatcher.group(2)));

        ArrayList<WebResult> results = new ArrayList<>();
        ArrayList<String> seen = new ArrayList<>();
        Matcher matcher = anchors.matcher(document);
        int snippetIndex = 0;
        while (matcher.find() && results.size() < 8) {
            String attributes = matcher.group(1);
            String classes = htmlAttribute(attributes, "class");
            String rel = htmlAttribute(attributes, "rel");
            String href = htmlAttribute(attributes, "href");
            boolean resultLink = classes.contains("result__a") || classes.contains("result-link");
            boolean liteLink = rel.contains("nofollow")
                    && (href.contains("uddg=") || href.startsWith("http://")
                    || href.startsWith("https://"));
            if (!resultLink && !liteLink) continue;
            String title = htmlText(matcher.group(2)).replace('|', '/');
            String url;
            try {
                url = unwrapDuckDuckGoUrl(href);
            } catch (Exception ignored) {
                continue;
            }
            if (url.isEmpty() || title.isEmpty() || seen.contains(url)) continue;
            String snippet = snippetIndex < snippetValues.size() ? snippetValues.get(snippetIndex) : "";
            snippetIndex++;
            results.add(new WebResult(webToken(url), title, webSource(url), snippet, url));
            seen.add(url);
        }
        return results;
    }

    // Analyse les résultats RSS Bing.
    private List<WebResult> parseBingRssResults(byte[] document) throws Exception {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new ByteArrayInputStream(document), "UTF-8");
        ArrayList<WebResult> results = new ArrayList<>();
        ArrayList<String> seen = new ArrayList<>();
        boolean inItem = false;
        String title = "";
        String link = "";
        String description = "";
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT;
                event = parser.next()) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("item".equalsIgnoreCase(name)) {
                    inItem = true;
                    title = "";
                    link = "";
                    description = "";
                } else if (inItem && "title".equalsIgnoreCase(name) && title.isEmpty()) {
                    title = htmlText(parser.nextText());
                } else if (inItem && "link".equalsIgnoreCase(name) && link.isEmpty()) {
                    link = parser.nextText().trim();
                } else if (inItem && "description".equalsIgnoreCase(name)
                        && description.isEmpty()) {
                    description = htmlText(parser.nextText());
                }
            } else if (event == XmlPullParser.END_TAG && "item".equalsIgnoreCase(parser.getName())) {
                if (!title.isEmpty() && !link.isEmpty() && !seen.contains(link)) {
                    results.add(new WebResult(
                            webToken(link), title.replace('|', '/'), webSource(link), description, link));
                    seen.add(link);
                    if (results.size() >= 8) break;
                }
                inItem = false;
            }
        }
        return results;
    }

    // Reconstruit un résultat Web depuis JSON.
    private WebResult webResultFromJson(JSONObject value) {
        return new WebResult(
                value.optString("token", ""),
                value.optString("title", "Web result"),
                value.optString("source", "web"),
                value.optString("snippet", ""),
                value.optString("url", ""));
    }

    // Enregistre une recherche Web.
    private void cacheWebSearch(String query, List<WebResult> results) throws Exception {
        JSONObject state = store.load();
        JSONArray previous = state.getJSONArray("web_results");
        JSONArray merged = new JSONArray();
        ArrayList<String> tokens = new ArrayList<>();
        for (WebResult result : results) {
            JSONObject item = result.toJson();
            for (int index = 0; index < previous.length(); index++) {
                JSONObject old = previous.optJSONObject(index);
                if (old != null && result.token.equalsIgnoreCase(old.optString("token"))) {
                    item.put("page_title", old.optString("page_title", ""));
                    item.put("page_body", old.optString("page_body", ""));
                    break;
                }
            }
            merged.put(item);
            tokens.add(result.token);
        }
        for (int index = 0; index < previous.length() && merged.length() < 64; index++) {
            JSONObject old = previous.optJSONObject(index);
            if (old == null || tokens.contains(old.optString("token"))) continue;
            merged.put(old);
        }
        state.put("web_results", merged);

        String normalized = query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        JSONArray searches = state.getJSONArray("web_searches");
        JSONArray updated = new JSONArray();
        JSONObject current = new JSONObject();
        current.put("query", normalized);
        JSONArray currentTokens = new JSONArray();
        for (String token : tokens) currentTokens.put(token);
        current.put("tokens", currentTokens);
        updated.put(current);
        for (int index = 0; index < searches.length() && updated.length() < 16; index++) {
            JSONObject item = searches.optJSONObject(index);
            if (item == null || normalized.equals(item.optString("query"))) continue;
            updated.put(item);
        }
        state.put("web_searches", updated);
        store.save(state);
    }

    // Charge une recherche Web en cache.
    private List<WebResult> cachedWebSearch(String query) throws Exception {
        JSONObject state = store.load();
        String normalized = query.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
        JSONArray searches = state.getJSONArray("web_searches");
        JSONArray tokens = null;
        for (int index = 0; index < searches.length(); index++) {
            JSONObject item = searches.optJSONObject(index);
            if (item != null && normalized.equals(item.optString("query"))) {
                tokens = item.optJSONArray("tokens");
                break;
            }
        }
        ArrayList<WebResult> results = new ArrayList<>();
        if (tokens == null) return results;
        JSONArray cached = state.getJSONArray("web_results");
        for (int tokenIndex = 0; tokenIndex < tokens.length(); tokenIndex++) {
            String token = tokens.optString(tokenIndex);
            for (int index = 0; index < cached.length(); index++) {
                JSONObject item = cached.optJSONObject(index);
                if (item != null && token.equalsIgnoreCase(item.optString("token"))) {
                    results.add(webResultFromJson(item));
                    break;
                }
            }
        }
        return results;
    }

    // Charge un résultat Web en cache.
    private JSONObject cachedWebResult(String token) throws Exception {
        JSONArray cached = store.load().getJSONArray("web_results");
        for (int index = 0; index < cached.length(); index++) {
            JSONObject item = cached.optJSONObject(index);
            if (item != null && token.equalsIgnoreCase(item.optString("token"))) return item;
        }
        throw new UserError("Resultat Web expire");
    }

    // Enregistre une page Web.
    private void cacheWebPage(String token, String title, String body) throws Exception {
        JSONObject state = store.load();
        JSONArray cached = state.getJSONArray("web_results");
        for (int index = 0; index < cached.length(); index++) {
            JSONObject item = cached.optJSONObject(index);
            if (item == null || !token.equalsIgnoreCase(item.optString("token"))) continue;
            item.put("page_title", title.substring(0, Math.min(160, title.length())));
            item.put("page_body", body.substring(0, Math.min(6000, body.length())));
            store.save(state);
            return;
        }
        throw new UserError("Resultat Web expire");
    }

    // Construit les cartes de résultats Web.
    private ServiceResult webResultCards(String title, List<WebResult> results) throws Exception {
        if (results.isEmpty()) throw new UserError("Recherche Web sans resultat exploitable");
        StringBuilder body = new StringBuilder("FNWEB1");
        for (int index = 0; index < Math.min(8, results.size()); index++) {
            WebResult item = results.get(index);
            String label = AndroidFeatureSupport.shortText(item.title + " - " + item.source, 76).replace('|', '/');
            body.append('\n').append(item.token).append('|').append(label);
        }
        return new ServiceResult(title, body.toString());
    }

    // Recherche des pages Web.
    ServiceResult webSearch(String query, String language) throws Exception {
        query = query == null ? "" : query.trim().replaceAll("\\s+", " ");
        if (query.length() < 2) throw new UserError(AndroidFeatureSupport.tr(language, "Enter at least 2 characters", "Saisis au moins 2 caracteres"));
        Exception lastError = null;
        String encoded = AndroidFeatureSupport.urlEncode(query);
        String region = AndroidFeatureSupport.isFrench(language) ? "fr-fr" : "us-en";
        String[] duckDuckGoAddresses = {
                "https://html.duckduckgo.com/html/?kl=" + region + "&q=" + encoded,
                "https://lite.duckduckgo.com/lite/?kl=" + region + "&q=" + encoded
        };
        for (String address : duckDuckGoAddresses) {
            try {
                String document = new String(
                        AndroidFeatureSupport.getWebBytes(address, "text/html,application/xhtml+xml", 1_000_000),
                        StandardCharsets.UTF_8);
                List<WebResult> results = parseWebResults(document);
                if (!results.isEmpty()) {
                    cacheWebSearch(query, results);
                    return webResultCards(AndroidFeatureSupport.tr(language, "Web results", "Resultats Web"), results);
                }
            } catch (Exception error) {
                lastError = error;
            }
        }
        try {
            String address = "https://www.bing.com/search?format=rss&setlang="
                    + (AndroidFeatureSupport.isFrench(language) ? "fr-FR" : "en-US") + "&q=" + encoded;
            List<WebResult> results = parseBingRssResults(
                    AndroidFeatureSupport.getWebBytes(address, "application/rss+xml,application/xml,text/xml", 1_000_000));
            if (!results.isEmpty()) {
                cacheWebSearch(query, results);
                return webResultCards(AndroidFeatureSupport.tr(language, "Web results", "Resultats Web"), results);
            }
        } catch (Exception error) {
            lastError = error;
        }
        List<WebResult> cached = cachedWebSearch(query);
        if (!cached.isEmpty()) return webResultCards(
                AndroidFeatureSupport.tr(language, "Cached Web results", "Resultats Web en cache"), cached);
        String detail = lastError == null || lastError.getMessage() == null
                ? AndroidFeatureSupport.tr(language, "no usable response", "aucune reponse exploitable")
                : AndroidFeatureSupport.asciiText(lastError.getMessage());
        throw new UserError(AndroidFeatureSupport.tr(language, "Web search unavailable: ",
                "Recherche Web indisponible : ") + detail);
    }

    // Valide une URL publique.
    private URL publicWebUrl(String value) throws Exception {
        String raw = value == null ? "" : value.trim();
        if (raw.isEmpty()) throw new UserError("Indique une URL");
        if (!raw.contains("://")) raw = "https://" + raw;
        URL url = new URL(raw);
        if (!url.getProtocol().equalsIgnoreCase("http")
                && !url.getProtocol().equalsIgnoreCase("https")) {
            throw new UserError("URL HTTP ou HTTPS invalide");
        }
        if (url.getUserInfo() != null) throw new UserError("Identifiants interdits dans l URL");
        String host = url.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".local") || host.endsWith(".localhost")) {
            throw new UserError("Adresse locale interdite");
        }
        InetAddress[] addresses = InetAddress.getAllByName(host);
        if (addresses.length == 0) throw new UserError("Domaine introuvable");
        for (InetAddress address : addresses) {
            if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                    || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                    || address.isMulticastAddress()) {
                throw new UserError("Adresse privee ou locale interdite");
            }
        }
        return new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile());
    }

    // Décode le contenu d’une page Web.
    private String decodeWebBytes(byte[] payload, String contentType) throws Exception {
        Matcher matcher = Pattern.compile("(?i)charset=([A-Za-z0-9._-]+)").matcher(contentType);
        String charset = matcher.find() ? matcher.group(1) : "UTF-8";
        try {
            return new String(payload, charset);
        } catch (Exception ignored) {
            return new String(payload, StandardCharsets.UTF_8);
        }
    }

    // Extrait le texte lisible d’une page.
    private ServiceResult extractWebPage(String document, String source) throws Exception {
        Matcher titleMatcher = Pattern.compile("(?is)<title\\b[^>]*>(.*?)</title>").matcher(document);
        String title = titleMatcher.find() ? htmlText(titleMatcher.group(1)) : source;
        String cleaned = document.replaceAll(
                "(?is)<(?:script|style|noscript|svg|nav|form|head)\\b[^>]*>.*?</(?:script|style|noscript|svg|nav|form|head)>",
                " ");
        Matcher primary = Pattern.compile("(?is)<(?:article|main)\\b[^>]*>(.*?)</(?:article|main)>")
                .matcher(cleaned);
        if (primary.find() && primary.group(1).length() >= 120) cleaned = primary.group(1);
        cleaned = cleaned.replaceAll(
                "(?i)</?(?:article|blockquote|br|div|h[1-6]|li|main|p|pre|section|table|td|th|tr)\\b[^>]*>",
                "\n");
        String text = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY).toString()
                .replace("\r", "")
                .replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
        if (text.length() < 40) throw new UserError("Page sans texte lisible");
        return new ServiceResult(title.isEmpty() ? source : title,
                text.substring(0, Math.min(6000, text.length())));
    }

    // Télécharge une page publique.
    private ServiceResult readWebUrl(String value) throws Exception {
        URL current = publicWebUrl(value);
        for (int redirect = 0; redirect <= 5; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) current.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(20_000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", "FlipNet-Android/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9");
            int status = connection.getResponseCode();
            if (status >= 300 && status < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if (location == null || location.isEmpty()) throw new UserError("Redirection Web invalide");
                current = publicWebUrl(new URL(current, location).toString());
                continue;
            }
            if (status < 200 || status >= 300) {
                connection.disconnect();
                throw new UserError("HTTP " + status);
            }
            String contentType = connection.getContentType() == null
                    ? "text/html" : connection.getContentType().toLowerCase(Locale.ROOT);
            if (!contentType.contains("text/html") && !contentType.contains("application/xhtml+xml")
                    && !contentType.contains("text/plain")) {
                connection.disconnect();
                throw new UserError("Type de page non lisible");
            }
            byte[] payload;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) != -1) {
                    total += read;
                    if (total > 1_000_000) throw new UserError("Page trop volumineuse");
                    output.write(buffer, 0, read);
                }
                payload = output.toByteArray();
            } finally {
                connection.disconnect();
            }
            String document = decodeWebBytes(payload, contentType);
            if (contentType.contains("text/plain")) {
                String text = document.trim();
                if (text.length() < 40) throw new UserError("Page sans texte lisible");
                return new ServiceResult(webSource(current.toString()),
                        text.substring(0, Math.min(6000, text.length())));
            }
            return extractWebPage(document, webSource(current.toString()));
        }
        throw new UserError("Trop de redirections Web");
    }

    // Charge une page trouvée.
    ServiceResult webPage(String token) throws Exception {
        token = token == null ? "" : token.trim().toUpperCase(Locale.ROOT);
        if (!token.matches("[A-F0-9]{8,16}")) throw new UserError("Resultat Web invalide");
        JSONObject item = cachedWebResult(token);
        try {
            ServiceResult result = readWebUrl(item.optString("url"));
            cacheWebPage(token, result.title, result.body);
            return new ServiceResult(result.title,
                    "Source: " + item.optString("source", "web") + "\n\n" + result.body);
        } catch (Exception error) {
            String body = item.optString("page_body", "").trim();
            if (!body.isEmpty()) {
                return new ServiceResult(item.optString("page_title", item.optString("title", "Web page")),
                        "Cached page\n\n" + body);
            }
            throw error;
        }
    }

    // Charge une URL publique.
    ServiceResult webUrl(String value) throws Exception {
        URL url = publicWebUrl(value);
        String address = url.toString();
        WebResult item = new WebResult(
                webToken(address), webSource(address), webSource(address), "", address);
        ArrayList<WebResult> values = new ArrayList<>();
        values.add(item);
        cacheWebSearch(address, values);
        return webPage(item.token);
    }

    // Décrit une source de flux.
    private static final class FeedSource {
        final String id;
        final String title;
        final String url;

        // Initialise une source de flux.
        FeedSource(String id, String title, String url) {
            this.id = id;
            this.title = title;
            this.url = url;
        }
    }

    // Décrit un article de flux Android.
    private static final class FeedEntry {
        final String title;
        final String summary;
        final String link;

        // Initialise un article de flux.
        FeedEntry(String title, String summary, String link) {
            this.title = title;
            this.summary = summary;
            this.link = link;
        }
    }

    // Trouve la source du flux.
    private FeedSource feedSource(String sourceId) throws UserError {
        String id = sourceId == null || sourceId.trim().isEmpty()
                ? "fr_certfr" : sourceId.trim().toLowerCase(Locale.ROOT);
        switch (id) {
            case "fr_certfr":
                return new FeedSource(id, "CERT-FR", "https://www.cert.ssi.gouv.fr/feed/");
            case "fr_lemonde":
                return new FeedSource(id, "Le Monde", "https://www.lemonde.fr/rss/une.xml");
            case "fr_lemonde_pixels":
                return new FeedSource(id, "Le Monde Pixels", "https://www.lemonde.fr/pixels/rss_full.xml");
            case "fr_franceinfo":
                return new FeedSource(id, "franceinfo", "https://www.francetvinfo.fr/titres.rss");
            case "fr_france24":
                return new FeedSource(id, "France 24", "https://www.france24.com/fr/rss");
            case "fr_rfi":
                return new FeedSource(id, "RFI", "https://www.rfi.fr/fr/rss");
            case "fr_lefigaro":
                return new FeedSource(id, "Le Figaro", "https://www.lefigaro.fr/rss/figaro_actualites.xml");
            case "fr_liberation":
                return new FeedSource(id, "Liberation", "https://www.liberation.fr/arc/outboundfeeds/rss-all/?outputType=xml");
            case "fr_20minutes":
                return new FeedSource(id, "20 Minutes", "https://www.20minutes.fr/feeds/rss-une.xml");
            case "fr_bfmtv":
                return new FeedSource(id, "BFMTV", "https://www.bfmtv.com/rss/news-24-7/");
            case "fr_numerama":
                return new FeedSource(id, "Numerama", "https://www.numerama.com/feed/");
            case "fr_01net":
                return new FeedSource(id, "01net", "https://www.01net.com/actualites/feed/");
            case "fr_clubic":
                return new FeedSource(id, "Clubic", "https://www.clubic.com/feed/news.rss");
            case "fr_nextink":
                return new FeedSource(id, "Next.ink", "https://next.ink/feed/");
            case "fr_jdg":
                return new FeedSource(id, "Journal du Geek", "https://www.journaldugeek.com/feed/");
            case "fr_frandroid":
                return new FeedSource(id, "Frandroid", "https://www.frandroid.com/feed");
            case "fr_lesnumeriques":
                return new FeedSource(id, "Les Numeriques", "https://www.lesnumeriques.com/rss.xml");
            case "fr_korben":
                return new FeedSource(id, "Korben", "https://korben.info/feed");
            case "fr_linuxfr":
                return new FeedSource(id, "LinuxFr.org", "https://linuxfr.org/news.atom");
            case "en_bbc":
                return new FeedSource(id, "BBC News", "https://feeds.bbci.co.uk/news/rss.xml");
            case "en_guardian":
                return new FeedSource(id, "The Guardian", "https://www.theguardian.com/world/rss");
            case "en_nyt":
                return new FeedSource(id, "New York Times", "https://rss.nytimes.com/services/xml/rss/nyt/World.xml");
            case "en_aljazeera":
                return new FeedSource(id, "Al Jazeera", "https://www.aljazeera.com/xml/rss/all.xml");
            case "en_npr":
                return new FeedSource(id, "NPR News", "https://feeds.npr.org/1001/rss.xml");
            case "en_ars":
                return new FeedSource(id, "Ars Technica", "https://feeds.arstechnica.com/arstechnica/index");
            case "en_verge":
                return new FeedSource(id, "The Verge", "https://www.theverge.com/rss/index.xml");
            case "en_techcrunch":
                return new FeedSource(id, "TechCrunch", "https://techcrunch.com/feed/");
            case "en_wired":
                return new FeedSource(id, "Wired", "https://www.wired.com/feed/rss");
            case "en_engadget":
                return new FeedSource(id, "Engadget", "https://www.engadget.com/rss.xml");
            case "en_hn":
                return new FeedSource(id, "Hacker News", "https://hnrss.org/frontpage");
            case "en_redhat":
                return new FeedSource(id, "Red Hat Blog", "https://www.redhat.com/en/rss/blog");
            case "en_lwn":
                return new FeedSource(id, "LWN.net", "https://lwn.net/headlines/rss");
            case "en_ubuntu":
                return new FeedSource(id, "Ubuntu Blog", "https://ubuntu.com/blog/feed");
            case "en_docker":
                return new FeedSource(id, "Docker Blog", "https://www.docker.com/blog/feed/");
            case "en_kubernetes":
                return new FeedSource(id, "Kubernetes Blog", "https://kubernetes.io/feed.xml");
            case "en_cloudflare":
                return new FeedSource(id, "Cloudflare Blog", "https://blog.cloudflare.com/rss/");
            case "en_cisa":
                return new FeedSource(id, "CISA Advisories", "https://www.cisa.gov/cybersecurity-advisories/all.xml");
            case "en_bleeping":
                return new FeedSource(id, "BleepingComputer", "https://www.bleepingcomputer.com/feed/");
            case "en_krebs":
                return new FeedSource(id, "Krebs on Security", "https://krebsonsecurity.com/feed/");
            case "en_thn":
                return new FeedSource(id, "The Hacker News", "https://feeds.feedburner.com/TheHackersNews");
            case "en_google_sec":
                return new FeedSource(id, "Google Security Blog", "https://security.googleblog.com/feeds/posts/default");
            case "en_ms_sec":
                return new FeedSource(id, "Microsoft Security", "https://www.microsoft.com/en-us/security/blog/feed/");
            case "en_schneier":
                return new FeedSource(id, "Schneier on Security", "https://www.schneier.com/feed/atom/");
            default:
                throw new UserError("Source RSS inconnue");
        }
    }

    // Nettoie le texte d’un flux.
    private String cleanFeedText(String value, int limit) {
        if (value == null) return "";
        String cleaned = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString();
        cleaned = AndroidFeatureSupport.asciiText(cleaned)
                .replace('|', '/')
                .replace('=', '-')
                .replaceAll("\\s+", " ")
                .trim();
        return cleaned.substring(0, Math.min(limit, cleaned.length()));
    }

    // Télécharge puis analyse un flux.
    private List<FeedEntry> readFeed(FeedSource source, int limit) throws Exception {
        byte[] xml = AndroidFeatureSupport.getBytes(source.url, "application/rss+xml, application/atom+xml, text/xml");
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new ByteArrayInputStream(xml), "UTF-8");
        List<FeedEntry> entries = new ArrayList<>();
        boolean inEntry = false;
        String title = "";
        String summary = "";
        String link = "";

        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) {
                    inEntry = true;
                    title = "";
                    summary = "";
                    link = "";
                } else if (inEntry && "title".equalsIgnoreCase(name) && title.isEmpty()) {
                    title = cleanFeedText(parser.nextText(), 150);
                } else if (inEntry && ("description".equalsIgnoreCase(name)
                        || "summary".equalsIgnoreCase(name)
                        || "content".equalsIgnoreCase(name)) && summary.isEmpty()) {
                    summary = cleanFeedText(parser.nextText(), 620);
                } else if (inEntry && "link".equalsIgnoreCase(name) && link.isEmpty()) {
                    String href = parser.getAttributeValue(null, "href");
                    link = href == null ? parser.nextText().trim() : href.trim();
                }
            } else if (event == XmlPullParser.END_TAG) {
                String name = parser.getName();
                if ("item".equalsIgnoreCase(name) || "entry".equalsIgnoreCase(name)) {
                    if (!title.isEmpty()) entries.add(new FeedEntry(title, summary, link));
                    inEntry = false;
                    if (entries.size() >= limit) break;
                }
            }
        }
        return entries;
    }

    // Construit les cartes d’un flux.
    private ServiceResult feedCards(String title, List<String> sourceIds, List<Integer> sourceIndexes,
            List<FeedEntry> entries) throws UserError {
        if (entries.isEmpty()) throw new UserError("Aucun titre reçu");
        StringBuilder body = new StringBuilder("FNFEED1\nTitle=")
                .append(cleanFeedText(title, 42))
                .append("\nCount=")
                .append(entries.size());
        for (int index = 0; index < entries.size() && index < 6; index++) {
            body.append("\nItem=")
                    .append(sourceIds.get(index))
                    .append('|')
                    .append(sourceIndexes.get(index))
                    .append('|')
                    .append(entries.get(index).title);
        }
        return new ServiceResult(title, body.toString());
    }

    // Liste les titres d’un flux.
    ServiceResult headlines(String sourceId) throws Exception {
        FeedSource source = feedSource(sourceId);
        List<FeedEntry> entries = readFeed(source, 6);
        List<String> sourceIds = new ArrayList<>();
        List<Integer> sourceIndexes = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            sourceIds.add(source.id);
            sourceIndexes.add(index + 1);
        }
        return feedCards(source.title, sourceIds, sourceIndexes, entries);
    }

    // Résume les derniers articles.
    ServiceResult feedDigest(String digestId) throws Exception {
        String id = digestId == null ? "" : digestId.trim().toLowerCase(Locale.ROOT);
        String title;
        String[] sourceIds;
        switch (id) {
            case "fr_news":
                title = "Actualites FR";
                sourceIds = new String[]{"fr_lemonde", "fr_franceinfo", "fr_france24",
                        "fr_rfi", "fr_lefigaro", "fr_liberation", "fr_20minutes", "fr_bfmtv"};
                break;
            case "fr_tech":
                title = "Numerique FR";
                sourceIds = new String[]{"fr_lemonde_pixels", "fr_numerama", "fr_01net",
                        "fr_clubic", "fr_nextink", "fr_jdg", "fr_frandroid",
                        "fr_lesnumeriques", "fr_korben", "fr_linuxfr"};
                break;
            case "fr_cyber":
                title = "Cyber FR";
                sourceIds = new String[]{"fr_certfr", "fr_nextink", "fr_korben", "fr_numerama"};
                break;
            case "fr_systems":
                title = "Systemes FR";
                sourceIds = new String[]{"fr_linuxfr", "fr_nextink", "fr_korben", "fr_01net"};
                break;
            case "fr_all":
                title = "Mix FR";
                sourceIds = new String[]{"fr_lemonde", "fr_franceinfo", "fr_numerama",
                        "fr_01net", "fr_linuxfr", "fr_certfr", "fr_france24", "fr_nextink"};
                break;
            case "en_news":
                title = "World News EN";
                sourceIds = new String[]{"en_bbc", "en_guardian", "en_nyt", "en_aljazeera", "en_npr"};
                break;
            case "en_tech":
                title = "Tech News EN";
                sourceIds = new String[]{"en_ars", "en_verge", "en_techcrunch",
                        "en_wired", "en_engadget", "en_hn"};
                break;
            case "en_cyber":
                title = "Cyber EN";
                sourceIds = new String[]{"en_cisa", "en_bleeping", "en_krebs", "en_thn",
                        "en_google_sec", "en_ms_sec", "en_schneier"};
                break;
            case "en_systems":
                title = "Systems EN";
                sourceIds = new String[]{"en_redhat", "en_lwn", "en_ubuntu", "en_docker",
                        "en_kubernetes", "en_cloudflare"};
                break;
            case "en_all":
                title = "Mix EN";
                sourceIds = new String[]{"en_bbc", "en_guardian", "en_ars", "en_verge",
                        "en_bleeping", "en_lwn", "en_cloudflare", "en_cisa"};
                break;
            default:
                throw new UserError("Digest inconnu");
        }

        List<String> itemSources = new ArrayList<>();
        List<Integer> itemIndexes = new ArrayList<>();
        List<FeedEntry> items = new ArrayList<>();
        List<List<FeedEntry>> bySource = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(6, sourceIds.length));
        ArrayList<Future<List<FeedEntry>>> futures = new ArrayList<>();
        try {
            for (String sourceId : sourceIds) {
                futures.add(pool.submit(() -> readFeed(feedSource(sourceId), 3)));
            }
            for (Future<List<FeedEntry>> future : futures) {
                try {
                    bySource.add(future.get());
                } catch (Exception error) {
                    bySource.add(new ArrayList<>());
                }
            }
        } finally {
            pool.shutdownNow();
        }
        for (int article = 0; article < 3 && items.size() < 6; article++) {
            for (int source = 0; source < sourceIds.length && items.size() < 6; source++) {
                List<FeedEntry> entries = bySource.get(source);
                if (article >= entries.size()) continue;
                itemSources.add(sourceIds[source]);
                itemIndexes.add(article + 1);
                items.add(entries.get(article));
            }
        }
        return feedCards(title, itemSources, itemIndexes, items);
    }

    // Charge un article de flux.
    ServiceResult feedArticle(String argument) throws Exception {
        String[] fields = argument == null ? new String[0] : argument.split("\\|", 2);
        if (fields.length != 2) throw new UserError("Article invalide");
        int index;
        try {
            index = Integer.parseInt(fields[1]);
        } catch (NumberFormatException error) {
            throw new UserError("Numero d'article invalide");
        }
        if (index < 1 || index > 20) throw new UserError("Article introuvable");
        FeedSource source = feedSource(fields[0]);
        List<FeedEntry> entries = readFeed(source, index);
        if (entries.size() < index) throw new UserError("Article introuvable");
        FeedEntry entry = entries.get(index - 1);
        StringBuilder body = new StringBuilder("Source: ").append(source.title);
        if (!entry.summary.isEmpty()) body.append("\n\n").append(entry.summary);
        if (!entry.link.isEmpty()) body.append("\n\n").append(entry.link);
        return new ServiceResult(entry.title, body.toString());
    }
}
