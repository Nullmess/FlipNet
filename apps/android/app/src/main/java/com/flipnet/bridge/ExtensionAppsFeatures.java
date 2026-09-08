package com.flipnet.bridge;

import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Regroupe les utilitaires Android.
final class ExtensionAppsFeatures {
    private final FlipNetService gateway;

    // Initialise les utilitaires.
    ExtensionAppsFeatures(FlipNetService gateway) {
        this.gateway = gateway;
    }

    // Liste les applications publiées dans FNCAP2.
    List<CapabilityProtocol.App> capabilityApps() {
        ArrayList<CapabilityProtocol.App> apps = new ArrayList<>();
        apps.add(new CapabilityProtocol.App("translate", "app_search", "translate_flow",
                "Translate", "Traduction", "Languages", "Langues",
                "French English", "bundled", "utilities"));
        apps.add(new CapabilityProtocol.App("calculator", "app_run", "input",
                "Calculator", "Calculatrice", "Expression", "Expression",
                "(12 + 8) / 4", "bundled", "utilities"));
        apps.add(new CapabilityProtocol.App("convert", "app_run", "input",
                "Unit converter", "Conversion d unites", "Value and units",
                "Valeur et unites", "10 km to mi", "bundled", "utilities"));
        apps.add(new CapabilityProtocol.App("currency", "app_run", "input",
                "Currency", "Devises", "Amount and currencies",
                "Montant et devises", "10 EUR to USD", "bundled", "utilities"));
        apps.add(new CapabilityProtocol.App("timezone", "app_search", "search",
                "World time", "Heure mondiale", "City or timezone",
                "Ville ou fuseau", "Paris", "bundled", "utilities"));
        apps.add(new CapabilityProtocol.App("phone_status", "app_run", "run",
                "Phone status", "Etat du telephone", "", "", "",
                "bundled", "phone"));
        apps.add(new CapabilityProtocol.App("phone_notifications", "app_run", "run",
                "Notifications", "Notifications", "", "", "",
                "bundled", "phone"));
        apps.add(new CapabilityProtocol.App("phone_clipboard", "app_run", "run",
                "Phone clipboard", "Presse-papiers", "", "", "",
                "bundled", "phone"));
        apps.add(new CapabilityProtocol.App("phone_copy", "app_run", "input",
                "Copy to phone", "Copier vers telephone", "Text to copy",
                "Texte a copier", "", "bundled", "phone"));
        apps.add(new CapabilityProtocol.App("phone_url", "app_run", "input",
                "Send URL to phone", "Envoyer URL", "Public URL",
                "URL publique", "https://", "bundled", "phone"));
        apps.add(new CapabilityProtocol.App("phone_location", "app_run", "run",
                "Phone location", "Localisation", "", "", "",
                "bundled", "phone"));
        apps.add(new CapabilityProtocol.App("phone_find", "app_run", "run",
                "Find phone", "Faire sonner", "", "", "",
                "bundled", "phone"));
        return apps;
    }

    private static final String[][] DYNAMIC_LANGUAGES = {
            {"ar", "Arabic", "Arabe"}, {"zh", "Chinese", "Chinois"},
            {"nl", "Dutch", "Neerlandais"}, {"en", "English", "Anglais"},
            {"fr", "French", "Francais"}, {"de", "German", "Allemand"},
            {"el", "Greek", "Grec"}, {"hi", "Hindi", "Hindi"},
            {"it", "Italian", "Italien"}, {"ja", "Japanese", "Japonais"},
            {"ko", "Korean", "Coreen"}, {"pl", "Polish", "Polonais"},
            {"pt", "Portuguese", "Portugais"}, {"ro", "Romanian", "Roumain"},
            {"ru", "Russian", "Russe"}, {"es", "Spanish", "Espagnol"},
            {"sv", "Swedish", "Suedois"}, {"tr", "Turkish", "Turc"},
            {"uk", "Ukrainian", "Ukrainien"}
    };

    // Décrit un choix d’utilitaire.
    private static final class DynamicChoice {
        final int score;
        final String value;
        final String label;

        // Initialise un choix d’utilitaire.
        DynamicChoice(int score, String value, String label) {
            this.score = score;
            this.value = value;
            this.label = label;
        }
    }

    // Construit la clé de recherche d’un utilitaire.
    private static String dynamicSearchKey(String value) {
        String folded = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKD);
        return folded.replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    // Calcule la pertinence d’un champ.
    private static int dynamicFieldScore(String query, String value) {
        String q = dynamicSearchKey(query);
        String hay = dynamicSearchKey(value);
        if (q.isEmpty()) return 1;
        if (hay.isEmpty()) return 0;
        if (q.equals(hay)) return 1000;
        if (hay.startsWith(q)) return 700;
        for (String word : hay.split(" ")) {
            if (word.startsWith(q)) return 600;
        }
        if (hay.contains(q)) return 450;
        String[] tokens = q.split(" ");
        boolean all = tokens.length > 0;
        for (String token : tokens) {
            boolean found = false;
            for (String word : hay.split(" ")) {
                if (word.startsWith(token)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                all = false;
                break;
            }
        }
        return all ? 350 + tokens.length * 10 : 0;
    }

    // Construit les choix de langue.
    private static List<DynamicChoice> dynamicLanguageChoices(String language, String exclude) {
        ArrayList<DynamicChoice> choices = new ArrayList<>();
        boolean french = "fr".equalsIgnoreCase(language);
        for (String[] item : DYNAMIC_LANGUAGES) {
            if (item[0].equals(exclude)) continue;
            choices.add(new DynamicChoice(1, item[0], item[french ? 2 : 1]));
        }
        return choices;
    }

    // Construit les couples de traduction.
    private static List<DynamicChoice> dynamicTranslationChoices(String query, String language) {
        ArrayList<DynamicChoice> choices = new ArrayList<>();
        String q = dynamicSearchKey(query);
        if (q.isEmpty()) {
            choices.add(new DynamicChoice(1, "fr>en", "French to English"));
            choices.add(new DynamicChoice(1, "en>fr", "English to French"));
            choices.add(new DynamicChoice(1, "es>fr", "Spanish to French"));
            choices.add(new DynamicChoice(1, "fr>es", "French to Spanish"));
            return choices;
        }
        String[] tokens = q.split(" ");
        for (String[] source : DYNAMIC_LANGUAGES) {
            for (String[] target : DYNAMIC_LANGUAGES) {
                if (source[0].equals(target[0])) continue;
                boolean french = "fr".equalsIgnoreCase(language);
                String sourceName = source[french ? 2 : 1];
                String targetName = target[french ? 2 : 1];
                String label = sourceName + (french ? " vers " : " to ") + targetName;
                String value = source[0] + ">" + target[0];
                int score = dynamicFieldScore(q, source[0] + " " + source[1] + " " + source[2] + " "
                        + target[0] + " " + target[1] + " " + target[2]);
                if (tokens.length >= 2) {
                    int sourceScore = dynamicFieldScore(tokens[0], source[0] + " " + source[1] + " " + source[2]);
                    StringBuilder targetQuery = new StringBuilder();
                    for (int index = 1; index < tokens.length; index++) {
                        if (targetQuery.length() > 0) targetQuery.append(' ');
                        targetQuery.append(tokens[index]);
                    }
                    int targetScore = dynamicFieldScore(targetQuery.toString(),
                            target[0] + " " + target[1] + " " + target[2]);
                    if (sourceScore > 0 && targetScore > 0)
                        score += 1200 + sourceScore + targetScore;
                }
                if (score > 0) {
                    if ((source[0].equals("fr") && target[0].equals("en"))
                            || (source[0].equals("en") && target[0].equals("fr")))
                        score += 25;
                    choices.add(new DynamicChoice(score, value, label));
                }
            }
        }
        choices.sort((left, right) -> {
            int score = Integer.compare(right.score, left.score);
            return score != 0 ? score : left.label.compareTo(right.label);
        });
        return choices.subList(0, Math.min(8, choices.size()));
    }

    // Construit les choix de fuseau horaire.
    private static List<DynamicChoice> dynamicTimezoneChoices(String query) {
        ArrayList<DynamicChoice> choices = new ArrayList<>();
        ArrayList<String> zones = new ArrayList<>(ZoneId.getAvailableZoneIds());
        java.util.Collections.sort(zones);
        for (String zone : zones) {
            String city = zone.substring(zone.lastIndexOf('/') + 1).replace('_', ' ');
            String label = city + " - " + zone;
            int score = dynamicFieldScore(query, city + " " + zone);
            if (score > 0) choices.add(new DynamicChoice(score, zone, label));
        }
        choices.sort((left, right) -> {
            int score = Integer.compare(right.score, left.score);
            return score != 0 ? score : left.label.compareTo(right.label);
        });
        return choices.subList(0, Math.min(8, choices.size()));
    }

    // Recherche les choix d’un utilitaire.
    ServiceResult appSearch(String argument, String language) throws Exception {
        String raw = argument == null ? "" : argument;
        int separator = raw.indexOf('|');
        String appId = (separator < 0 ? raw : raw.substring(0, separator))
                .trim().toLowerCase(Locale.ROOT);
        String query = separator < 0 ? "" : raw.substring(separator + 1).trim();
        List<DynamicChoice> choices;
        if (appId.equals("timezone")) choices = dynamicTimezoneChoices(query);
        else if (appId.equals("translate_source")) choices = dynamicLanguageChoices(language, "");
        else if (appId.equals("translate_target")) choices = dynamicLanguageChoices(language, query.toLowerCase(Locale.ROOT));
        else if (appId.equals("translate")) choices = dynamicTranslationChoices(query, language);
        else throw new UserError(AndroidFeatureSupport.tr(language, "Search unavailable for this app", "Recherche indisponible pour cette application"));
        if (choices.isEmpty()) throw new UserError(AndroidFeatureSupport.tr(language, "No matching result", "Aucun resultat correspondant"));
        StringBuilder body = new StringBuilder("FNSEL1\n").append(appId);
        for (DynamicChoice choice : choices.subList(0, Math.min(20, choices.size()))) {
            String line = choice.value.replace('|', ' ') + "|" + choice.label.replace('|', ' ');
            if (body.toString().getBytes(StandardCharsets.UTF_8).length
                    + line.getBytes(StandardCharsets.UTF_8).length + 1 > 1000) break;
            body.append('\n').append(line);
        }
        return new ServiceResult("Choose", body.toString());
    }

    // Exécute un utilitaire.
    ServiceResult appRun(String argument, String language) throws Exception {
        String raw = argument == null ? "" : argument;
        int separator = raw.indexOf('|');
        String appId = (separator < 0 ? raw : raw.substring(0, separator))
                .trim().toLowerCase(Locale.ROOT);
        String value = separator < 0 ? "" : raw.substring(separator + 1).trim();
        if (appId.isEmpty()) {
            throw new UserError(AndroidFeatureSupport.tr(language,
                    "Application catalog is still loading. Refresh features.",
                    "Le catalogue des applications charge encore. Actualise les fonctions."));
        }
        if (PhoneLink.handles(appId)) {
            PhoneLink.PhoneResult result = PhoneLink.run(gateway, appId, value, language);
            return new ServiceResult(result.title, result.body);
        }
        switch (appId) {
            case "translate":
                return dynamicTranslate(value, language);
            case "calculator":
                return dynamicCalculator(value);
            case "convert":
                return dynamicConvert(value);
            case "currency":
                return dynamicCurrency(value);
            case "timezone":
                return dynamicTimezone(value);
            default:
                throw new UserError(AndroidFeatureSupport.tr(language, "Unknown application", "Application inconnue"));
        }
    }

    // Traduit un texte.
    private ServiceResult dynamicTranslate(String value, String language) throws Exception {
        Matcher matcher = Pattern.compile(
                "^\\s*([A-Za-z-]{2,12})>([A-Za-z-]{2,12})\\s+(.+)\\s*$",
                Pattern.DOTALL).matcher(value);
        if (!matcher.matches()) throw new UserError(AndroidFeatureSupport.tr(language, "Choose languages, then enter text", "Choisis les langues, puis saisis le texte"));
        String source = matcher.group(1);
        String target = matcher.group(2);
        String text = matcher.group(3);
        if (text.getBytes(StandardCharsets.UTF_8).length > 500)
            throw new UserError(AndroidFeatureSupport.tr(language, "Text too long", "Texte trop long"));
        JSONObject payload = AndroidFeatureSupport.getJson(
                "https://api.mymemory.translated.net/get?q=" + AndroidFeatureSupport.urlEncode(text)
                        + "&langpair=" + AndroidFeatureSupport.urlEncode(source + "|" + target));
        JSONObject responseData = payload.optJSONObject("responseData");
        String translated = responseData == null
                ? "" : responseData.optString("translatedText", "").trim();
        if (translated.isEmpty()) throw new UserError(AndroidFeatureSupport.tr(language, "Translation unavailable", "Traduction indisponible"));
        return new ServiceResult(source.toLowerCase(Locale.ROOT) + " > "
                + target.toLowerCase(Locale.ROOT), translated);
    }

    // Analyse un nombre décimal.
    private static String dynamicNumber(double value) throws UserError {
        if (!Double.isFinite(value)) throw new UserError("Resultat non fini");
        if (Math.abs(value) < 1e-12) value = 0.0;
        return java.math.BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    // Analyse une expression arithmétique.
    private static final class DynamicExpressionParser {
        private final String expression;
        private int index;

        // Initialise l’analyseur de calcul.
        DynamicExpressionParser(String expression) {
            this.expression = expression;
        }

        // Analyse la valeur reçue.
        double parse() throws UserError {
            double value = parseExpression();
            skipSpaces();
            if (index != expression.length()) throw new UserError("Expression invalide");
            if (!Double.isFinite(value)) throw new UserError("Resultat non fini");
            return value;
        }

        // Analyse une addition ou une soustraction.
        private double parseExpression() throws UserError {
            double value = parseTerm();
            while (true) {
                skipSpaces();
                if (consume('+')) value += parseTerm();
                else if (consume('-')) value -= parseTerm();
                else return value;
            }
        }

        // Analyse une multiplication ou une division.
        private double parseTerm() throws UserError {
            double value = parseFactor();
            while (true) {
                skipSpaces();
                if (consume('*')) value *= parseFactor();
                else if (consume('/')) {
                    double divisor = parseFactor();
                    if (Math.abs(divisor) < 1e-15) throw new UserError("Division par zero");
                    value /= divisor;
                } else if (consume('%')) {
                    double divisor = parseFactor();
                    if (Math.abs(divisor) < 1e-15) throw new UserError("Division par zero");
                    value %= divisor;
                } else return value;
            }
        }

        // Analyse une valeur ou une parenthèse.
        private double parseFactor() throws UserError {
            skipSpaces();
            if (consume('+')) return parseFactor();
            if (consume('-')) return -parseFactor();
            if (consume('(')) {
                double value = parseExpression();
                skipSpaces();
                if (!consume(')')) throw new UserError("Parenthese manquante");
                return value;
            }
            return parseNumber();
        }

        // Analyse un nombre dans l’expression.
        private double parseNumber() throws UserError {
            skipSpaces();
            int start = index;
            boolean decimal = false;
            while (index < expression.length()) {
                char current = expression.charAt(index);
                if (Character.isDigit(current)) {
                    index++;
                } else if ((current == '.' || current == ',') && !decimal) {
                    decimal = true;
                    index++;
                } else {
                    break;
                }
            }
            if (start == index) throw new UserError("Nombre attendu");
            String number = expression.substring(start, index).replace(',', '.');
            try {
                return Double.parseDouble(number);
            } catch (NumberFormatException error) {
                throw new UserError("Nombre invalide");
            }
        }

        // Ignore les espaces de l’expression.
        private void skipSpaces() {
            while (index < expression.length() && Character.isWhitespace(expression.charAt(index))) index++;
        }

        // Consomme le caractère attendu.
        private boolean consume(char expected) {
            if (index < expression.length() && expression.charAt(index) == expected) {
                index++;
                return true;
            }
            return false;
        }
    }

    // Calcule une expression.
    private ServiceResult dynamicCalculator(String value) throws Exception {
        if (value.isEmpty() || value.length() > 96) throw new UserError("Expression invalide");
        double result = new DynamicExpressionParser(value).parse();
        return new ServiceResult("Calculator", value + "\n= " + dynamicNumber(result));
    }

    // Trouve la catégorie d’une unité.
    private String dynamicUnitCategory(String unit) {
        switch (unit) {
            case "mm": case "cm": case "m": case "km":
            case "in": case "ft": case "yd": case "mi": return "length";
            case "mg": case "g": case "kg": case "oz": case "lb": return "mass";
            case "ms": case "s": case "min": case "h": case "d": return "time";
            case "b": case "kb": case "mb": case "gb":
            case "kib": case "mib": case "gib": return "data";
            default: return "";
        }
    }

    // Retourne le facteur d’une unité.
    private double dynamicUnitFactor(String unit) throws UserError {
        switch (unit) {
            case "mm": return 0.001;
            case "cm": return 0.01;
            case "m": return 1.0;
            case "km": return 1000.0;
            case "in": return 0.0254;
            case "ft": return 0.3048;
            case "yd": return 0.9144;
            case "mi": return 1609.344;
            case "mg": return 0.001;
            case "g": return 1.0;
            case "kg": return 1000.0;
            case "oz": return 28.349523125;
            case "lb": return 453.59237;
            case "ms": return 0.001;
            case "s": return 1.0;
            case "min": return 60.0;
            case "h": return 3600.0;
            case "d": return 86400.0;
            case "b": return 1.0;
            case "kb": return 1000.0;
            case "mb": return 1_000_000.0;
            case "gb": return 1_000_000_000.0;
            case "kib": return 1024.0;
            case "mib": return 1_048_576.0;
            case "gib": return 1_073_741_824.0;
            default: throw new UserError("Unite inconnue");
        }
    }

    // Convertit une température.
    private double dynamicTemperature(double value, String source, String target)
            throws UserError {
        double kelvin;
        if (source.equals("c")) kelvin = value + 273.15;
        else if (source.equals("f")) kelvin = (value - 32.0) * 5.0 / 9.0 + 273.15;
        else if (source.equals("k")) kelvin = value;
        else throw new UserError("Unite de temperature invalide");
        if (target.equals("c")) return kelvin - 273.15;
        if (target.equals("f")) return (kelvin - 273.15) * 9.0 / 5.0 + 32.0;
        if (target.equals("k")) return kelvin;
        throw new UserError("Unite de temperature invalide");
    }

    // Convertit une unité.
    private ServiceResult dynamicConvert(String value) throws Exception {
        Matcher matcher = Pattern.compile(
                "^\\s*([-+]?(?:\\d+(?:[.,]\\d*)?|[.,]\\d+))\\s*([A-Za-z]+)"
                        + "\\s+(?:to|in|vers)\\s+([A-Za-z]+)\\s*$",
                Pattern.CASE_INSENSITIVE).matcher(value);
        if (!matcher.matches()) throw new UserError("Format: 10 km to mi");
        double amount = Double.parseDouble(matcher.group(1).replace(',', '.'));
        String source = matcher.group(2).toLowerCase(Locale.ROOT);
        String target = matcher.group(3).toLowerCase(Locale.ROOT);
        double result;
        if (source.matches("[cfk]") || target.matches("[cfk]")) {
            result = dynamicTemperature(amount, source, target);
        } else {
            String category = dynamicUnitCategory(source);
            if (category.isEmpty() || !category.equals(dynamicUnitCategory(target)))
                throw new UserError("Unites incompatibles");
            result = amount * dynamicUnitFactor(source) / dynamicUnitFactor(target);
        }
        return new ServiceResult("Unit converter", dynamicNumber(amount) + " " + source
                + "\n= " + dynamicNumber(result) + " " + target);
    }

    // Convertit une devise.
    private ServiceResult dynamicCurrency(String value) throws Exception {
        Matcher matcher = Pattern.compile(
                "^\\s*([-+]?(?:\\d+(?:[.,]\\d*)?|[.,]\\d+))\\s+([A-Za-z]{3})"
                        + "\\s+(?:to|in|vers)\\s+([A-Za-z]{3})\\s*$",
                Pattern.CASE_INSENSITIVE).matcher(value);
        if (!matcher.matches()) throw new UserError("Format: 10 EUR to USD");
        double amount = Double.parseDouble(matcher.group(1).replace(',', '.'));
        String base = matcher.group(2).toUpperCase(Locale.ROOT);
        String quote = matcher.group(3).toUpperCase(Locale.ROOT);
        double rate = 1.0;
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(new Date());
        if (!base.equals(quote)) {
            JSONObject payload = AndroidFeatureSupport.getJson("https://api.frankfurter.dev/v2/rate/"
                    + base + "/" + quote);
            rate = payload.optDouble("rate", Double.NaN);
            date = payload.optString("date", "latest");
            if (!Double.isFinite(rate)) throw new UserError("Taux de change indisponible");
        }
        return new ServiceResult("Currency", dynamicNumber(amount) + " " + base
                + "\n= " + dynamicNumber(amount * rate) + " " + quote
                + "\nRate: " + dynamicNumber(rate) + "\nDate: " + date);
    }

    // Convertit un fuseau horaire.
    private ServiceResult dynamicTimezone(String value) throws Exception {
        String name = value.trim().isEmpty() ? "UTC" : value.trim();
        try {
            ZonedDateTime now = ZonedDateTime.now(ZoneId.of(name));
            String body = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd\nHH:mm:ss", Locale.ROOT))
                    + "\n" + now.getOffset().getId().replace("Z", "UTC");
            if (!body.contains("UTC") && (body.endsWith("+00:00") || body.endsWith("-00:00")))
                body = body.substring(0, body.length() - 6) + "UTC+00:00";
            else if (!body.contains("UTC"))
                body = body.substring(0, body.lastIndexOf('\n') + 1) + "UTC" + now.getOffset();
            return new ServiceResult(name, body);
        } catch (DateTimeException error) {
            throw new UserError("Timezone inconnue");
        }
    }
}
