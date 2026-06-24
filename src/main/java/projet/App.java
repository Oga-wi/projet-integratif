package projet;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.stream.Collectors;

import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public final class App {

    /**
     * Classe utilitaire : constructeur privé pour interdire l'instanciation.
     */
    private App() {
    }

    // =========================================================================
    // Génération du graphe Mermaid
    // =========================================================================

    /**
     * Génère un bloc de code Mermaid (syntaxe {@code graph TD}) représentant la
     * topologie du réseau.
     *
     * @param adresses   liste de tous les équipements à afficher comme nœuds
     * @param connexions liste des liaisons physiques entre équipements
     * @return chaîne Markdown contenant le bloc {@code ```mermaid ... ```}
     */
    private static String genererMermaidTopologie(List<AdresseReseau> adresses,
            List<ConnexionReseau> connexions) {
        StringBuilder mermaid = new StringBuilder();

        mermaid.append("```mermaid\ngraph TD\n");

        for (AdresseReseau a : adresses) {
            String id = sanitizeId(a.getNom());
            String ip = a.toString().split(" : ")[1].split(" / ")[0];
            String label = a.getNom() + "\\n" + ip;

            switch (a.getType()) {
                case "Routeur":
                    mermaid.append(String.format("    %s{{%s}}\n", id, label));
                    break;
                case "Serveur":
                    mermaid.append(String.format("    %s(%s)\n", id, label));
                    break;
                default:
                    mermaid.append(String.format("    %s[%s]\n", id, label));
                    break;
            }
        }

        mermaid.append("\n");

        for (ConnexionReseau c : connexions) {
            String idA = sanitizeId(c.getEquipementA().getNom());
            String idB = sanitizeId(c.getEquipementB().getNom());
            String liaison = c.getTypeConnexion() + " " + c.getDebitMbps() + " Mbps";
            mermaid.append(String.format("    %s -- \"%s\" --> %s\n", idA, liaison, idB));
        }

        mermaid.append("\n");

        mermaid.append("    classDef routeur fill:#f0ad4e,stroke:#c87f0a,color:#000\n");
        mermaid.append("    classDef serveur fill:#5bc0de,stroke:#31b0d5,color:#000\n");
        mermaid.append("    classDef poste   fill:#5cb85c,stroke:#4cae4c,color:#000\n");

        for (AdresseReseau a : adresses) {
            String id = sanitizeId(a.getNom());
            switch (a.getType()) {
                case "Routeur":
                    mermaid.append(String.format("    class %s routeur\n", id));
                    break;
                case "Serveur":
                    mermaid.append(String.format("    class %s serveur\n", id));
                    break;
                default:
                    mermaid.append(String.format("    class %s poste\n", id));
                    break;
            }
        }

        mermaid.append("```\n");
        return mermaid.toString();
    }

    /**
     * Transforme un nom d'équipement en identifiant Mermaid valide.
     *
     * @param nom nom brut de l'équipement
     * @return identifiant utilisable directement dans la syntaxe Mermaid
     */
    private static String sanitizeId(String nom) {
        return nom.replaceAll("[^a-zA-Z0-9]", "_");
    }

    // =========================================================================
    // Main
    // =========================================================================

    /**
     * Méthode principale : orchestre la saisie utilisateur, la construction du
     * rapport Markdown et l'export des fichiers.
     *
     * @param args arguments de la ligne de commande (non utilisés)
     */
    public static void main(String[] args) {

        // ── Choix du moteur IA et du format d'export ──────────────────────────
        int choixIA;
        int choixExport;
        int maxTokens = 500;
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("=== Choisissez le modèle d'analyse IA ===");
            System.out.println("1. Gemini 2.5 Flash-Lite (gratuit, variable GOOGLE_API_KEY)");
            System.out.println("2. Gemini 3.1 Flash-Lite (gratuit, variable GOOGLE_API_KEY) [RECOMMANDER]");
            System.out.println("3. OpenRouter Free       (gratuit, variable OPENROUTER_API_KEY)");
            System.out.println("0. Aucune analyse IA");
            do {
                System.out.print("Votre choix : ");
                while (!scanner.hasNextInt()) {
                    System.out.println("Entrée invalide.");
                    System.out.print("Votre choix : ");
                    scanner.next();
                }
                choixIA = scanner.nextInt();
                if (choixIA < 0 || choixIA > 3) {
                    System.out.println("Choix invalide.");
                }
            } while (choixIA < 0 || choixIA > 3);

            if (choixIA == 1 || choixIA == 2) {
                System.out.println("\n=== Taille maximale du résumé (Tokens de sortie) ===");
                System.out.println("1. Court  (~200 tokens) - Idéal pour un survol rapide");
                System.out.println("2. Moyen  (500 tokens)  - Bon compromis détails/concision");
                System.out.println("3. Long   (1000 tokens) - Analyse détaillée du rapport");

                int choixTokens;
                do {
                    System.out.print("Votre choix de longueur : ");
                    while (!scanner.hasNextInt()) {
                        System.out.println("Entrée invalide. Veuillez saisir 1, 2 ou 3.");
                        System.out.print("Votre choix de longueur : ");
                        scanner.next();
                    }
                    choixTokens = scanner.nextInt();
                    if (choixTokens < 1 || choixTokens > 3) {
                        System.out.println("Choix invalide. Veuillez saisir 1, 2 ou 3.");
                    }
                } while (choixTokens < 1 || choixTokens > 3);

                switch (choixTokens) {
                    case 1:
                        maxTokens = 200;
                        break;
                    case 2:
                        maxTokens = 500;
                        break;
                    case 3:
                        maxTokens = 1000;
                        break;
                }
                System.out.println("-> Limite configurée à : " + maxTokens + " tokens.");
            }

            System.out.println("\n=== Choisissez le format d'export ===");
            System.out.println("1. Markdown uniquement (.md)");
            System.out.println("2. PDF uniquement      (.pdf)");
            System.out.println("3. Les deux            (.md + .pdf)");
            do {
                System.out.print("Votre choix : ");
                while (!scanner.hasNextInt()) {
                    System.out.println("Entrée invalide. Veuillez saisir 1, 2 ou 3.");
                    System.out.print("Votre choix : ");
                    scanner.next();
                }
                choixExport = scanner.nextInt();
                if (choixExport < 1 || choixExport > 3) {
                    System.out.println("Choix invalide. Veuillez saisir 1, 2 ou 3.");
                }
            } while (choixExport < 1 || choixExport > 3);
        }

        // =========================================================================
        // Déclaration des équipements réseau
        // =========================================================================
        List<AdresseReseau> adresses = new ArrayList<>();

        AdresseReseau routeurPrincipal = new AdresseReseau("Routeur-Principal", "Routeur", "172.16.0.1", "255.255.0.0");
        AdresseReseau routeurSite2 = new AdresseReseau("Routeur-Site2", "Routeur", "172.16.1.1", "255.255.0.0");
        AdresseReseau serveurWeb = new AdresseReseau("Serveur-Web", "Serveur", "10.0.0.50", "255.0.0.0");
        AdresseReseau serveurBdd = new AdresseReseau("Serveur-BDD", "Serveur", "10.0.0.51", "255.0.0.0");
        AdresseReseau serveurFtp = new AdresseReseau("Serveur-FTP", "Serveur", "10.0.0.52", "255.0.0.0");
        AdresseReseau serveurDns = new AdresseReseau("Serveur-DNS", "Serveur", "10.0.0.10", "255.0.0.0");
        AdresseReseau pc1 = new AdresseReseau("PC1", "Ordinateur", "192.168.10.100", "255.255.255.0");
        AdresseReseau pc2 = new AdresseReseau("PC2", "Ordinateur", "192.168.10.101", "255.255.255.0");
        AdresseReseau pc3 = new AdresseReseau("PC3", "Ordinateur", "192.168.10.102", "255.255.255.0");
        AdresseReseau imprimante = new AdresseReseau("Imprimante-A", "Imprimante", "192.168.10.200", "255.255.255.0");
        AdresseReseau pc4 = new AdresseReseau("PC4", "Ordinateur", "192.168.20.100", "255.255.255.0");
        AdresseReseau pc5 = new AdresseReseau("PC5", "Ordinateur", "192.168.20.101", "255.255.255.0");
        AdresseReseau pointAcces = new AdresseReseau("AP-WiFi-S2", "Borne WiFi", "192.168.20.1", "255.255.255.0");

        adresses.add(routeurPrincipal);
        adresses.add(routeurSite2);
        adresses.add(serveurWeb);
        adresses.add(serveurBdd);
        adresses.add(serveurFtp);
        adresses.add(serveurDns);
        adresses.add(pc1);
        adresses.add(pc2);
        adresses.add(pc3);
        adresses.add(imprimante);
        adresses.add(pc4);
        adresses.add(pc5);
        adresses.add(pointAcces);

        // =========================================================================
        // Déclaration des connexions réseau
        // =========================================================================
        List<ConnexionReseau> connexions = new ArrayList<>();

        connexions.add(new ConnexionReseau(routeurPrincipal, routeurSite2, "Fibre WAN", 1000));
        connexions.add(new ConnexionReseau(serveurWeb, routeurPrincipal, "Fibre", 10000));
        connexions.add(new ConnexionReseau(serveurBdd, routeurPrincipal, "Fibre", 10000));
        connexions.add(new ConnexionReseau(serveurFtp, routeurPrincipal, "Fibre", 5000));
        connexions.add(new ConnexionReseau(serveurDns, routeurPrincipal, "Ethernet", 1000));
        connexions.add(new ConnexionReseau(pc1, routeurPrincipal, "Ethernet", 1000));
        connexions.add(new ConnexionReseau(pc2, routeurPrincipal, "Ethernet", 1000));
        connexions.add(new ConnexionReseau(pc3, routeurPrincipal, "Ethernet", 1000));
        connexions.add(new ConnexionReseau(imprimante, routeurPrincipal, "Ethernet", 100));
        connexions.add(new ConnexionReseau(pc4, routeurSite2, "Ethernet", 1000));
        connexions.add(new ConnexionReseau(pc5, pointAcces, "WiFi", 300));
        connexions.add(new ConnexionReseau(pointAcces, routeurSite2, "Ethernet", 1000));

        // =========================================================================
        // Calcul des statistiques globales
        // =========================================================================
        long totalEquipements = adresses.size();

        Map<String, Long> repartitionParType = adresses.stream()
                .collect(Collectors.groupingBy(AdresseReseau::getType, Collectors.counting()));

        long totalHotesPossibles = adresses.stream()
                .mapToLong(AdresseReseau::nombreHotes)
                .sum();

        // =========================================================================
        // Construction du rapport Markdown
        // =========================================================================
        StringBuilder md = new StringBuilder();

        md.append("# Tableau de Bord & Statistiques Réseau\n\n");
        md.append("Ce rapport fournit une analyse globale des infrastructures réseau configurées.\n\n");

        md.append("## Topologie Réseau\n\n");
        md.append("> 🟠 Routeurs  🔵 Serveurs  🟢 Postes / Autres\n\n");
        md.append(genererMermaidTopologie(adresses, connexions));
        md.append("\n");

        md.append("## Indicateurs Clés\n\n");
        md.append(String.format("- **Nombre total d'équipements enregistrés :** %d\n", totalEquipements));
        md.append(String.format("- **Capacité totale d'adresses hôtes disponibles :** %,d\n\n", totalHotesPossibles));

        md.append("## Répartition par Type d'Équipement\n\n");
        md.append("| Type d'appareil | Quantité | Pourcentage |\n");
        md.append("| :--- | :---: | :---: |\n");
        for (Map.Entry<String, Long> entree : repartitionParType.entrySet()) {
            double pourcentage = ((double) entree.getValue() / totalEquipements) * 100;
            md.append(String.format("| %s | %d | %.1f %% |\n",
                    entree.getKey().isEmpty() ? "Non spécifié" : entree.getKey(),
                    entree.getValue(),
                    pourcentage));
        }
        md.append("\n");

        md.append("## Détail des Équipements\n\n");
        md.append("| Nom | Type | Adresse IP | Masque | Réseau | Broadcast | Hôtes du sous-réseau |\n");
        md.append("| :--- | :--- | :--- | :--- | :--- | :--- | :---: |\n");
        for (AdresseReseau a : adresses) {
            String ipStr = a.toString().split(" : ")[1].split(" / ")[0];
            String masqueStr = a.toString().split(" / ")[1];
            String reseauStr = a.adresseReseau().toString().split(" : ")[1].split(" / ")[0];
            String broadcastStr = a.adresseBroadcast().toString().split(" : ")[1].split(" / ")[0];
            md.append(String.format("| %s | %s | %s | %s | %s | %s | %,d |\n",
                    a.getNom(),
                    a.getType().isEmpty() ? "Inconnu" : a.getType(),
                    ipStr, masqueStr, reseauStr, broadcastStr,
                    a.nombreHotes()));
        }
        md.append("\n");

        md.append("## Connexions Réseau\n\n");
        md.append("| Équipement A | Équipement B | Type de liaison | Débit |\n");
        md.append("| :--- | :--- | :--- | :---: |\n");
        for (ConnexionReseau c : connexions) {
            md.append(String.format("| %s | %s | %s | %d Mbps |\n",
                    c.getEquipementA().getNom(),
                    c.getEquipementB().getNom(),
                    c.getTypeConnexion(),
                    c.getDebitMbps()));
        }
        md.append("\n");

        md.append("## Réseaux Distincts\n\n");
        md.append("| Réseau | Masque | Broadcast | Équipements | Hôtes max |\n");
        md.append("| :--- | :--- | :--- | :--- | :---: |\n");
        Map<String, List<AdresseReseau>> parReseau = adresses.stream()
                .collect(Collectors.groupingBy(
                        a -> a.adresseReseau().toString().split(" : ")[1].split(" / ")[0]));
        for (Map.Entry<String, List<AdresseReseau>> entree : parReseau.entrySet()) {
            AdresseReseau ref = entree.getValue().get(0);
            String adresseReseau = entree.getKey();
            String masque = ref.adresseReseau().toString().split(" / ")[1];
            String broadcast = ref.adresseBroadcast().toString().split(" : ")[1].split(" / ")[0];
            String equipements = entree.getValue().stream()
                    .map(AdresseReseau::getNom)
                    .collect(Collectors.joining(", "));
            md.append(String.format("| %s | %s | %s | %s | %,d |\n",
                    adresseReseau, masque, broadcast, equipements, ref.nombreHotes()));
        }
        md.append("\n");

        // =========================================================================
        // Analyse IA (optionnelle)
        // =========================================================================
        boolean analyseIAReussie = true;

        switch (choixIA) {
            case 0:
                analyseIAReussie = true;
                break;
            case 1:
            case 2:
                String geminiKey = System.getenv("GOOGLE_API_KEY");
                if (geminiKey == null || geminiKey.isBlank()) {
                    System.err.println("Erreur : variable d'environnement GOOGLE_API_KEY non définie.");
                    analyseIAReussie = false;
                    break;
                }

                String modelIA = "";
                if (choixIA == 1) {
                    System.out.println("Analyse IA avec Gemini 2.5 Flash-Lite...");
                    modelIA = "gemini-2.5-flash-lite";
                } else if (choixIA == 2) {
                    System.out.println("Analyse IA avec Gemini 3.1 Flash-Lite...");
                    modelIA = "gemini-3.1-flash-lite";
                }

                try (Client client = new Client()) {
                    GenerateContentConfig config = GenerateContentConfig.builder()
                            .maxOutputTokens(maxTokens)
                            .temperature(0.2f)
                            .build();

                    String consigneLongueur;
                    switch (maxTokens) {
                        case 200:
                            consigneLongueur = "Fais un résumé TRÈS COURT et concis (maximum 3 à 4 phrases).";
                            break;
                        case 1000:
                            consigneLongueur = "Fais une analyse détaillée et complète.";
                            break;
                        default:
                            consigneLongueur = "Fais un résumé de longueur moyenne (environ 2 à 3 paragraphes).";
                            break;
                    }

                    String prompt = "Voici un rapport réseau en Markdown :\n\n" + md.toString()
                            + "\n\n" + consigneLongueur
                            + " Analyse l'état de ce réseau et propose des pistes pour améliorer sa consommation électrique."
                            + " Rédige en français. Les premiers titres doivent obligatoirement être en ####."
                            + " IMPORTANT : Termine impérativement toutes tes phrases et respecte la limite de longueur imposée pour ne pas être coupé.";

                    GenerateContentResponse response = client.models.generateContent(
                            modelIA,
                            prompt,
                            config);

                    String resultatIA = response.text();

                    if (resultatIA != null && !resultatIA.isBlank()) {
                        md.append("\n## Analyse IA\n\n");
                        md.append(resultatIA);
                        md.append("\n\n*Analyse réalisée automatiquement avec").append(modelIA).append("*\n\n");
                        analyseIAReussie = true;
                    } else {
                        System.err.println("Erreur : La réponse de l'IA est vide.");
                        analyseIAReussie = false;
                    }

                } catch (Exception e) {
                    System.err.println("Erreur Gemini : " + e.getMessage());
                    e.printStackTrace();
                    analyseIAReussie = false;
                }
                break;

            case 3:
                String apiKey = System.getenv("OPENROUTER_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                    System.err.println("Erreur : variable d'environnement OPENROUTER_API_KEY non définie.");
                    analyseIAReussie = false;
                    break;
                }
                System.out.println("Analyse IA avec OpenRouter Free...");
                try {
                    JsonObject messageSystem = new JsonObject();
                    messageSystem.addProperty("role", "system");
                    messageSystem.addProperty("content",
                            "Tu es un assistant utile qui analyse des rapports réseau.");

                    JsonObject messageUser = new JsonObject();
                    messageUser.addProperty("role", "user");
                    messageUser.addProperty("content",
                            "Analyse ce rapport réseau :\n\n" + md.toString());

                    com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
                    messages.add(messageSystem);
                    messages.add(messageUser);

                    JsonObject jsonRequestBody = new JsonObject();
                    jsonRequestBody.addProperty("model", "openrouter/free");
                    jsonRequestBody.add("messages", messages);

                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + apiKey)
                            .POST(HttpRequest.BodyPublishers.ofString(jsonRequestBody.toString()))
                            .build();

                    String responseBody = httpClient
                            .send(request, HttpResponse.BodyHandlers.ofString())
                            .body();

                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    if (json.has("error")) {
                        System.err.println("Erreur OpenRouter : "
                                + json.getAsJsonObject("error").get("message").getAsString());
                        analyseIAReussie = false;
                        break;
                    }

                    String analyse = json.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();

                    md.append("## Analyse IA\n\n")
                            .append("Analyse réalisée avec OpenRouter Free.\n\n")
                            .append(analyse).append("\n");

                } catch (java.io.IOException | InterruptedException e) {
                    System.err.println("Erreur OpenRouter (I/O) : " + e.getMessage());
                    analyseIAReussie = false;
                } catch (com.google.gson.JsonSyntaxException e) {
                    System.err.println("Erreur OpenRouter (JSON) : " + e.getMessage());
                    analyseIAReussie = false;
                } catch (RuntimeException e) {
                    System.err.println("Erreur OpenRouter : " + e.getMessage());
                    analyseIAReussie = false;
                }
                break;

            default:
                System.out.println("Aucune analyse IA sélectionnée.");
        }

        // =========================================================================
        // Export des fichiers
        // =========================================================================
        if (!analyseIAReussie) {
            System.err.println("⚠ L'analyse IA a échoué. Aucun fichier exporté.");
            return;
        }

        if (choixExport == 1 || choixExport == 3) {
            try {
                Files.writeString(Path.of("rapport_statistiques.md"), md.toString());
                System.out.println("Rapport 'rapport_statistiques.md' généré avec succès !");
            } catch (IOException e) {
                System.err.println("Erreur lors de l'écriture du fichier : " + e.getMessage());
            }
        }

        if (choixExport == 2 || choixExport == 3) {
            ExportPDF.exporter(md.toString(), "rapport_statistiques.pdf");
        }
    }
}