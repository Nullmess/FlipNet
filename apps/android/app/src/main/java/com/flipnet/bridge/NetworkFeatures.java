package com.flipnet.bridge;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.net.URL;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

// Regroupe les contrôles réseau Android.
final class NetworkFeatures {
    private final AndroidStateStore store;

    // Initialise les fonctions réseau.
    NetworkFeatures(AndroidStateStore store) {
        this.store = store;
    }

    // Analyse les en-têtes de sécurité HTTP.
    ServiceResult httpSecurity(String value) throws Exception {
        String raw = value.trim();
        if (raw.isEmpty()) throw new UserError("Indique un domaine ou une URL");
        URL url = new URL(raw.contains("://") ? raw : "https://" + raw);
        if (!"http".equalsIgnoreCase(url.getProtocol())
                && !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new UserError("URL HTTP ou HTTPS invalide");
        }

        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "FlipNet-Android/" + BuildConfig.VERSION_NAME);
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*");
        connection.setRequestProperty("Range", "bytes=0-0");
        connection.setInstanceFollowRedirects(true);

        try {
            int status = connection.getResponseCode();
            URL finalUrl = connection.getURL();
            boolean https = "https".equalsIgnoreCase(finalUrl.getProtocol());
            String csp = connection.getHeaderField("Content-Security-Policy");
            boolean hsts = https && hasHeader(connection, "Strict-Transport-Security");
            boolean xfo = hasHeader(connection, "X-Frame-Options")
                    || (csp != null && csp.toLowerCase(Locale.ROOT).contains("frame-ancestors"));
            boolean xcto = "nosniff".equalsIgnoreCase(
                    connection.getHeaderField("X-Content-Type-Options"));
            boolean referrer = hasHeader(connection, "Referrer-Policy");
            boolean hasCsp = csp != null && !csp.trim().isEmpty();
            int score = (hsts ? 1 : 0) + (hasCsp ? 1 : 0) + (xfo ? 1 : 0)
                    + (xcto ? 1 : 0) + (referrer ? 1 : 0);
            boolean redirected = !finalUrl.toString().replaceAll("/+$", "")
                    .equals(url.toString().replaceAll("/+$", ""));

            StringBuilder body = new StringBuilder();
            body.append("HTTP : ").append(status)
                    .append(" | HTTPS : ").append(https ? "oui" : "non")
                    .append("\nRedirection : ").append(redirected ? "oui" : "non")
                    .append("\nScore headers : ").append(score).append("/5")
                    .append("\nHSTS : ").append(hsts ? "OK" : "absent")
                    .append("\nCSP : ").append(hasCsp ? "OK" : "absent")
                    .append("\nXFO : ").append(xfo ? "OK" : "absent")
                    .append("\nXCTO : ").append(xcto ? "OK" : "absent")
                    .append("\nReferrer : ").append(referrer ? "OK" : "absent");
            String server = connection.getHeaderField("Server");
            if (server != null && !server.trim().isEmpty()) {
                body.append("\nServeur : ").append(AndroidFeatureSupport.shortText(server, 70));
            }
            return new ServiceResult(finalUrl.getHost(), body.toString());
        } finally {
            connection.disconnect();
        }
    }

    // Indique si la réponse contient un en-tête.
    private boolean hasHeader(HttpURLConnection connection, String name) {
        String value = connection.getHeaderField(name);
        return value != null && !value.trim().isEmpty();
    }

    // Analyse le certificat TLS.
    ServiceResult tlsInspect(String value) throws Exception {
        AndroidFeatureSupport.HostPort target = AndroidFeatureSupport.parseHostPort(value, 443);
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, null, null);

        try (Socket raw = new Socket()) {
            raw.connect(new InetSocketAddress(target.host, target.port), 10_000);
            raw.setSoTimeout(10_000);
            try (SSLSocket socket = (SSLSocket) context.getSocketFactory()
                    .createSocket(raw, target.host, target.port, true)) {
                socket.startHandshake();
                Certificate[] chain = socket.getSession().getPeerCertificates();
                if (chain.length == 0 || !(chain[0] instanceof X509Certificate)) {
                    throw new UserError("Certificat indisponible");
                }
                X509Certificate certificate = (X509Certificate) chain[0];
                long days = (certificate.getNotAfter().getTime() - new Date().getTime()) / 86_400_000L;
                String body = "TLS : valide | " + socket.getSession().getProtocol()
                        + "\nSujet : " + AndroidFeatureSupport.shortText(AndroidFeatureSupport.commonName(certificate.getSubjectX500Principal().getName()), 80)
                        + "\nÉmetteur : " + AndroidFeatureSupport.shortText(AndroidFeatureSupport.commonName(certificate.getIssuerX500Principal().getName()), 80)
                        + "\nExpire : " + String.format(
                                Locale.ROOT, "%tF (%d j)", certificate.getNotAfter(), days)
                        + "\nChiffrement : " + socket.getSession().getCipherSuite()
                        + "\nPort : " + target.port;
                return new ServiceResult(target.host, body);
            }
        } catch (javax.net.ssl.SSLHandshakeException error) {
            return new ServiceResult(target.host, "TLS : INVALIDE\n" + AndroidFeatureSupport.shortText(error.getMessage(), 240));
        }
    }

    // Résout les enregistrements DNS.
    ServiceResult dnsLookup(String value) throws Exception {
        AndroidFeatureSupport.HostPort target = AndroidFeatureSupport.parseHostPort(value, 53);
        InetAddress[] addresses = InetAddress.getAllByName(target.host);
        ArrayList<String> ipv4 = new ArrayList<>();
        ArrayList<String> ipv6 = new ArrayList<>();
        for (InetAddress address : addresses) {
            String text = address.getHostAddress();
            ArrayList<String> list = text.contains(":") ? ipv6 : ipv4;
            if (!list.contains(text)) list.add(text);
        }

        StringBuilder body = new StringBuilder();
        if (!ipv4.isEmpty()) {
            body.append("IPv4 :");
            for (int i = 0; i < Math.min(5, ipv4.size()); i++) body.append("\n- ").append(ipv4.get(i));
        }
        if (!ipv6.isEmpty()) {
            if (body.length() > 0) body.append('\n');
            body.append("IPv6 :");
            for (int i = 0; i < Math.min(4, ipv6.size()); i++) body.append("\n- ").append(ipv6.get(i));
        }
        if (body.length() == 0) throw new UserError("Aucune adresse trouvée");
        return new ServiceResult(target.host, body.toString());
    }

    // Contrôle la sécurité du domaine.
    ServiceResult domainAudit(String value) throws Exception {
        String raw = value.trim();
        if (raw.isEmpty()) throw new UserError("Indique un domaine ou une URL");
        URL url = new URL(raw.contains("://") ? raw : "https://" + raw);
        String host = url.getHost();
        if (host.isEmpty()) throw new UserError("Domaine invalide");

        StringBuilder body = new StringBuilder();
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            int ipv4 = 0;
            int ipv6 = 0;
            for (InetAddress address : addresses) {
                if (address.getHostAddress().contains(":")) ipv6++; else ipv4++;
            }
            body.append("DNS : OK | A ").append(ipv4).append(" | AAAA ").append(ipv6);
        } catch (Exception error) {
            body.append("DNS : ÉCHEC");
        }

        long tlsStarted = System.nanoTime();
        try {
            AndroidFeatureSupport.HostPort target = new AndroidFeatureSupport.HostPort(host, url.getPort() > 0 ? url.getPort() : 443);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, null, null);
            try (Socket rawSocket = new Socket()) {
                rawSocket.connect(new InetSocketAddress(target.host, target.port), 10_000);
                try (SSLSocket socket = (SSLSocket) context.getSocketFactory()
                        .createSocket(rawSocket, target.host, target.port, true)) {
                    socket.startHandshake();
                    Certificate[] chain = socket.getSession().getPeerCertificates();
                    long days = -1;
                    if (chain.length > 0 && chain[0] instanceof X509Certificate) {
                        days = (((X509Certificate) chain[0]).getNotAfter().getTime()
                                - new Date().getTime()) / 86_400_000L;
                    }
                    body.append("\nTLS : ").append(socket.getSession().getProtocol())
                            .append(" | ").append(days >= 0 ? days + " j" : "?");
                }
            }
        } catch (Exception error) {
            body.append("\nTLS : ÉCHEC");
        }

        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(10_000);
            connection.setReadTimeout(10_000);
            connection.setRequestProperty("User-Agent", "FlipNet-Android/" + BuildConfig.VERSION_NAME);
            connection.setRequestProperty("Range", "bytes=0-0");
            connection.setInstanceFollowRedirects(true);
            long started = System.nanoTime();
            int status = connection.getResponseCode();
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            String csp = connection.getHeaderField("Content-Security-Policy");
            boolean hsts = hasHeader(connection, "Strict-Transport-Security");
            boolean xfo = hasHeader(connection, "X-Frame-Options")
                    || (csp != null && csp.toLowerCase(Locale.ROOT).contains("frame-ancestors"));
            boolean xcto = "nosniff".equalsIgnoreCase(
                    connection.getHeaderField("X-Content-Type-Options"));
            boolean referrer = hasHeader(connection, "Referrer-Policy");
            int score = (hsts ? 1 : 0) + (csp != null ? 1 : 0) + (xfo ? 1 : 0)
                    + (xcto ? 1 : 0) + (referrer ? 1 : 0);
            body.append("\nHTTPS : ").append(status).append(" | Headers ").append(score).append("/5")
                    .append("\nLatence : ").append(elapsed).append(" ms");
            connection.disconnect();
        } catch (Exception error) {
            long elapsed = (System.nanoTime() - tlsStarted) / 1_000_000L;
            body.append("\nHTTP : ÉCHEC\nLatence : ").append(elapsed).append(" ms");
        }
        return new ServiceResult(host, body.toString());
    }

    // Teste un point de terminaison réseau.
    ServiceResult endpointCheck(String value) throws Exception {
        String raw = value.trim();
        if (raw.isEmpty()) throw new UserError("Indique host:port ou une URL");
        boolean isUrl = raw.contains("://");
        URL url = new URL(isUrl ? raw : "https://" + raw);
        String host = url.getHost();
        int port = url.getPort() > 0 ? url.getPort()
                : ("http".equalsIgnoreCase(url.getProtocol()) ? 80 : 443);
        if (host.isEmpty()) throw new UserError("Cible invalide");

        StringBuilder body = new StringBuilder();
        try {
            body.append("DNS : OK | ").append(InetAddress.getAllByName(host).length)
                    .append(" adresse(s)");
        } catch (Exception error) {
            body.append("DNS : ÉCHEC");
        }

        long started = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 10_000);
            long elapsed = (System.nanoTime() - started) / 1_000_000L;
            body.append("\nTCP ").append(port).append(" : OUVERT | ").append(elapsed).append(" ms");
        } catch (Exception error) {
            body.append("\nTCP ").append(port).append(" : FERMÉ/TIMEOUT");
            return new ServiceResult(host, body.toString());
        }

        if (port == 443 || "https".equalsIgnoreCase(url.getProtocol())) {
            try {
                ServiceResult tls = tlsInspect(host + ":" + port);
                String first = tls.body.split("\\n", 2)[0];
                body.append("\n").append(first);
            } catch (Exception error) {
                body.append("\nTLS : ÉCHEC");
            }
        }

        if (isUrl || port == 80 || port == 443 || port == 8080 || port == 8443) {
            try {
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(10_000);
                connection.setRequestProperty("User-Agent", "FlipNet-Android/" + BuildConfig.VERSION_NAME);
                long httpStarted = System.nanoTime();
                int status = connection.getResponseCode();
                long elapsed = (System.nanoTime() - httpStarted) / 1_000_000L;
                body.append("\nHTTP : ").append(status).append(" | ").append(elapsed).append(" ms");
                connection.disconnect();
            } catch (Exception error) {
                body.append("\nHTTP : ÉCHEC");
            }
        }
        return new ServiceResult(host, body.toString());
    }

    // Décrit un port TCP ouvert.
    private static final class OpenPort {
        final int port;
        final long elapsedMs;

        // Initialise le résultat d un port.
        OpenPort(int port, long elapsedMs) {
            this.port = port;
            this.elapsedMs = elapsedMs;
        }
    }

    // Retourne le nom usuel d un port.
    private String portName(int port) {
        switch (port) {
            case 21: return "FTP";
            case 22: return "SSH";
            case 23: return "TELNET";
            case 25: return "SMTP";
            case 53: return "DNS";
            case 80: return "HTTP";
            case 110: return "POP3";
            case 135: return "MSRPC";
            case 139: return "NETBIOS";
            case 143: return "IMAP";
            case 389: return "LDAP";
            case 443: return "HTTPS";
            case 445: return "SMB";
            case 465: return "SMTPS";
            case 587: return "SUBMISSION";
            case 636: return "LDAPS";
            case 993: return "IMAPS";
            case 995: return "POP3S";
            case 1433: return "MSSQL";
            case 3306: return "MYSQL";
            case 3389: return "RDP";
            case 5432: return "POSTGRESQL";
            case 5900: return "VNC";
            case 6443: return "K8S API";
            case 8000: return "HTTP ALT";
            case 8080: return "HTTP ALT";
            case 8443: return "HTTPS ALT";
            case 9090: return "WEB ADMIN";
            case 10250: return "KUBELET";
            default: return "TCP";
        }
    }

    // Analyse tous les ports TCP de la cible.
    ServiceResult portScan(String value) throws Exception {
        AndroidFeatureSupport.HostPort target = AndroidFeatureSupport.parseHostPort(value, 443);
        InetAddress address = InetAddress.getByName(target.host);
        int workerCount = 128;
        int timeoutMs = 120;
        ExecutorService pool = Executors.newFixedThreadPool(workerCount);
        AtomicInteger nextPort = new AtomicInteger(1);
        List<OpenPort> opened = Collections.synchronizedList(new ArrayList<>());
        ArrayList<Future<?>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < workerCount; worker++) {
                futures.add(pool.submit(() -> {
                    int port;
                    while ((port = nextPort.getAndIncrement()) <= 65535) {
                        long started = System.nanoTime();
                        try (Socket socket = new Socket()) {
                            socket.connect(new InetSocketAddress(address, port), timeoutMs);
                            long elapsed = Math.max(1L,
                                    (System.nanoTime() - started) / 1_000_000L);
                            opened.add(new OpenPort(port, elapsed));
                        } catch (Exception ignored) {
                            // Ignore les ports fermes.
                        }
                    }
                }));
            }
            for (Future<?> future : futures) future.get();
            opened.sort(Comparator.comparingInt(item -> item.port));
            StringBuilder body = new StringBuilder();
            body.append("Ports: 1-65535\nOpen: ").append(opened.size());
            for (OpenPort item : opened) {
                body.append("\n").append(item.port).append(": OPEN | ")
                        .append(portName(item.port)).append(" | ")
                        .append(item.elapsedMs).append(" ms");
            }
            if (opened.isEmpty()) body.append("\nAucun port TCP ouvert");
            return new ServiceResult(target.host, body.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    // Trouve l’adresse IPv4 locale.
    private String localIpv4() throws UserError {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface current = interfaces.nextElement();
                if (!current.isUp() || current.isLoopback()) continue;
                Enumeration<InetAddress> addresses = current.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    String value = address.getHostAddress();
                    if (address.isSiteLocalAddress() && value != null && !value.contains(":")) {
                        return value;
                    }
                }
            }
        } catch (Exception error) {
            throw new UserError("Impossible de trouver le reseau local");
        }
        throw new UserError("Aucune adresse IPv4 locale");
    }

    // Construit le préfixe du réseau.
    private String networkPrefix(String value) throws UserError {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        String address = raw.isEmpty() || "current".equals(raw) || "local".equals(raw)
                ? localIpv4() : raw.split("/", 2)[0];
        String[] parts = address.split("\\.");
        if (parts.length != 4) throw new UserError("Sous-reseau IPv4 invalide");
        for (String part : parts) {
            int number;
            try {
                number = Integer.parseInt(part);
            } catch (NumberFormatException error) {
                throw new UserError("Sous-reseau IPv4 invalide");
            }
            if (number < 0 || number > 255) throw new UserError("Sous-reseau IPv4 invalide");
        }
        if (raw.contains("/") && !raw.endsWith("/24")) {
            throw new UserError("Limite: reseau /24 uniquement");
        }
        return parts[0] + "." + parts[1] + "." + parts[2];
    }

    // Recherche les hôtes du réseau.
    ServiceResult hostDiscovery(String value) throws Exception {
        String prefix = networkPrefix(value);
        int[] ports = {22, 53, 80, 88, 135, 389, 443, 445, 3389, 8006, 8123, 9090};
        ExecutorService pool = Executors.newFixedThreadPool(48);
        ArrayList<Future<String>> futures = new ArrayList<>();
        try {
            for (int suffix = 1; suffix <= 254; suffix++) {
                final String host = prefix + "." + suffix;
                futures.add(pool.submit(() -> {
                    ArrayList<Integer> opened = new ArrayList<>();
                    for (int port : ports) {
                        try (Socket socket = new Socket()) {
                            socket.connect(new InetSocketAddress(host, port), 300);
                            opened.add(port);
                            if (opened.size() >= 2) break;
                        } catch (Exception ignored) {
                        }
                    }
                    if (opened.isEmpty()) return null;
                    StringBuilder line = new StringBuilder(host).append(" | ");
                    for (int index = 0; index < opened.size(); index++) {
                        if (index > 0) line.append(',');
                        line.append(opened.get(index));
                    }
                    return line.toString();
                }));
            }

            ArrayList<String> found = new ArrayList<>();
            for (Future<String> future : futures) {
                String host = future.get();
                if (host != null) found.add(host);
            }
            StringBuilder body = new StringBuilder("Reseau: ")
                    .append(prefix).append(".0/24\n")
                    .append(found.size()).append(" hote(s) detecte(s)");
            int limit = Math.min(18, found.size());
            for (int index = 0; index < limit; index++) body.append("\n").append(found.get(index));
            if (found.size() > limit) body.append("\n+ ").append(found.size() - limit).append(" autre(s)");
            if (found.isEmpty()) body.append("\nAucun service TCP detecte");
            return new ServiceResult("Host Discovery", body.toString());
        } finally {
            pool.shutdownNow();
        }
    }

    // Déduit le rôle d’un appareil.
    private String inventoryRole(JSONArray ports) {
        String values = ports.toString();
        if (values.contains("88") || values.contains("389")) return "directory";
        if (values.contains("8006")) return "proxmox";
        if (values.contains("8123")) return "home-assistant";
        if (values.contains("53")) return "dns";
        if (values.contains("443") || values.contains("80")) return "web";
        if (values.contains("22")) return "linux";
        if (values.contains("3389") || values.contains("135")) return "windows";
        return "device";
    }

    // Construit un instantané réseau.
    ServiceResult inventoryScan(String value, String language) throws Exception {
        ServiceResult discovery = hostDiscovery(value);
        JSONObject state = store.load();
        JSONObject previous = state.optJSONObject("inventory");
        JSONObject current = new JSONObject();
        JSONArray assets = new JSONArray();
        String[] lines = discovery.body.split("\n");
        String network = lines.length > 0 ? lines[0].replace("Reseau: ", "") : value;
        for (int index = 2; index < lines.length; index++) {
            String line = lines[index];
            if (!line.contains(" | ")) continue;
            String[] parts = line.split(" \\| ", 2);
            JSONObject asset = new JSONObject();
            asset.put("address", parts[0]);
            JSONArray ports = new JSONArray();
            for (String raw : parts[1].split(",")) {
                try { ports.put(Integer.parseInt(raw.trim())); } catch (Exception ignored) {}
            }
            asset.put("services", ports);
            asset.put("role", inventoryRole(ports));
            assets.put(asset);
        }
        current.put("network", network);
        current.put("scanned_at", System.currentTimeMillis());
        current.put("assets", assets);
        JSONArray oldAssets = previous == null ? new JSONArray() : previous.optJSONArray("assets");
        java.util.HashMap<String, String> oldMap = new java.util.HashMap<>();
        if (oldAssets != null) for (int i=0;i<oldAssets.length();i++) {
            JSONObject item=oldAssets.optJSONObject(i); if(item!=null) { JSONArray itemPorts=item.optJSONArray("services"); oldMap.put(item.optString("address"), itemPorts == null ? "[]" : itemPorts.toString()); }
        }
        java.util.HashMap<String, String> newMap = new java.util.HashMap<>();
        for (int i=0;i<assets.length();i++) { JSONObject item=assets.getJSONObject(i); newMap.put(item.getString("address"), item.getJSONArray("services").toString()); }
        int added=0, removed=0, changed=0;
        for(String key:newMap.keySet()) { if(!oldMap.containsKey(key)) added++; else if(!newMap.get(key).equals(oldMap.get(key))) changed++; }
        for(String key:oldMap.keySet()) if(!newMap.containsKey(key)) removed++;
        JSONObject change = new JSONObject().put("network", network).put("added", added).put("removed", removed).put("changed", changed).put("time", System.currentTimeMillis());
        JSONArray changes = state.optJSONArray("inventory_changes"); if(changes==null) changes=new JSONArray();
        if(added+removed+changed>0) { JSONArray updated=new JSONArray().put(change); for(int i=0;i<Math.min(15,changes.length());i++) updated.put(changes.get(i)); changes=updated; }
        state.put("inventory", current); state.put("inventory_changes", changes); store.save(state);
        boolean fr="fr".equals(language);
        String body=(fr?"Reseau: ":"Network: ")+network+"\n"+(fr?"Appareils: ":"Devices: ")+assets.length()+"\n"+(fr?"Nouveaux: ":"New: ")+added+"\n"+(fr?"Disparus: ":"Missing: ")+removed+"\n"+(fr?"Modifies: ":"Changed: ")+changed;
        for(int i=0;i<Math.min(14,assets.length());i++){JSONObject a=assets.getJSONObject(i);body+="\n"+a.getString("address")+" | "+a.getString("role")+" | "+a.getJSONArray("services").toString().replace("[","").replace("]","");}
        return new ServiceResult(fr?"Inventaire reseau":"Network Inventory", body);
    }

    // Résume le dernier inventaire.
    ServiceResult inventorySummary(String language) throws Exception {
        JSONObject inventory=store.load().optJSONObject("inventory"); boolean fr="fr".equals(language);
        if(inventory==null || inventory.length()==0) throw new UserError(fr?"Aucun inventaire. Lance un scan.":"No inventory. Run a scan.");
        JSONArray assets=inventory.optJSONArray("assets"); if(assets==null) assets=new JSONArray();
        String body=(fr?"Reseau: ":"Network: ")+inventory.optString("network","?")+"\n"+(fr?"Appareils: ":"Devices: ")+assets.length();
        for(int i=0;i<Math.min(16,assets.length());i++){JSONObject a=assets.getJSONObject(i);body+="\n"+a.optString("address")+" | "+a.optString("role","device");}
        return new ServiceResult(fr?"Inventaire":"Inventory", body);
    }

    // Liste les changements réseau.
    ServiceResult inventoryChanges(String language) throws Exception {
        JSONArray changes=store.load().optJSONArray("inventory_changes"); boolean fr="fr".equals(language);
        if(changes==null || changes.length()==0) return new ServiceResult(fr?"Changements":"Changes", fr?"Aucun changement":"No change");
        StringBuilder body=new StringBuilder();
        for(int i=0;i<Math.min(8,changes.length());i++){JSONObject c=changes.getJSONObject(i);if(i>0)body.append("\n");body.append(c.optString("network","?"));body.append("\n+").append(c.optInt("added")).append("  -").append(c.optInt("removed")).append("  ~").append(c.optInt("changed"));}
        return new ServiceResult(fr?"Changements reseau":"Network Changes", body.toString());
    }
}
