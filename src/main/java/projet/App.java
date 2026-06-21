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
import com.google.genai.types.GenerateContentResponse;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Point d'entrée de l'application de génération de rapports réseau.
 *
 * <p>Ce programme :
 * <ol>
 *   <li>Demande à l'utilisateur quel moteur d'IA utiliser pour l'analyse.</li>
 *   <li>Déclare une liste d'équipements réseau ({@link AdresseReseau}) et de
 *       liaisons ({@link ConnexionReseau}).</li>
 *   <li>Génère un rapport Markdown complet incluant un graphe de topologie
 *       Mermaid, des tableaux de statistiques et une analyse IA optionnelle.</li>
 *   <li>Écrit le rapport dans {@code rapport_statistiques.md}.</li>
 * </ol>
 */
public final class App {

    /** Classe utilitaire : constructeur privé pour interdire l'instanciation. */
    private App() {
    }

    // =========================================================================
    // Génération du graphe Mermaid
    // =========================================================================

    /**
     * Génère un bloc de code Mermaid (syntaxe {@code graph TD}) représentant
     * la topologie du réseau : nœuds colorés par type d'équipement et arêtes
     * annotées avec le type de liaison et le débit.
     *
     * <p><b>Formes utilisées :</b>
     * <ul>
     *   <li>Routeur  → hexagone  {@code {{ }}}  (orange)</li>
     *   <li>Serveur  → arrondi   {@code ( )}    (bleu)</li>
     *   <li>Autres   → rectangle {@code [ ]}    (vert)</li>
     * </ul>
     *
     * @param adresses  liste de tous les équipements à afficher comme nœuds
     * @param connexions liste des liaisons physiques entre équipements
     * @return chaîne Markdown contenant le bloc {@code ```mermaid ... ```}
     */
    private static String genererMermaidTopologie(List<AdresseReseau> adresses,
                                                   List<ConnexionReseau> connexions) {
        StringBuilder mermaid = new StringBuilder();

        // En-tête du bloc Mermaid : graphe orienté de haut en bas (TD = Top-Down)
        mermaid.append("```mermaid\ngraph TD\n");

        // ── 1. Déclaration des nœuds ─────────────────────────────────────────
        // Chaque équipement devient un nœud dont la forme varie selon son type.
        // Le label affiche le nom ET l'adresse IP sur deux lignes (\n Mermaid).
        for (AdresseReseau a : adresses) {
            // sanitizeId remplace tout caractère non alphanumérique par "_"
            // car Mermaid n'accepte pas les tirets ou espaces dans les identifiants.
            String id    = sanitizeId(a.getNom());
            String ip    = a.toString().split(" : ")[1].split(" / ")[0];
            String label = a.getNom() + "\\n" + ip;

            switch (a.getType()) {
                case "Routeur":
                    // Hexagone  {{label}}  → symbolise un routeur
                    mermaid.append(String.format("    %s{{%s}}\n", id, label));
                    break;
                case "Serveur":
                    // Arrondi  (label)  → symbolise un serveur
                    mermaid.append(String.format("    %s(%s)\n", id, label));
                    break;
                default:
                    // Rectangle  [label]  → postes, imprimantes, équipements inconnus
                    mermaid.append(String.format("    %s[%s]\n", id, label));
                    break;
            }
        }

        mermaid.append("\n");

        // ── 2. Déclaration des arêtes ─────────────────────────────────────────
        // Chaque ConnexionReseau devient une flèche annotée :
        //   IdA -- "TypeLiaison DebitMbps Mbps" --> IdB
        for (ConnexionReseau c : connexions) {
            String idA    = sanitizeId(c.getEquipementA().getNom());
            String idB    = sanitizeId(c.getEquipementB().getNom());
            String liaison = c.getTypeConnexion() + " " + c.getDebitMbps() + " Mbps";
            mermaid.append(String.format("    %s -- \"%s\" --> %s\n", idA, liaison, idB));
        }

        mermaid.append("\n");

        // ── 3. Styles CSS Mermaid par classe de type ──────────────────────────
        // classDef définit une classe de style réutilisable.
        mermaid.append("    classDef routeur fill:#f0ad4e,stroke:#c87f0a,color:#000\n");
        mermaid.append("    classDef serveur fill:#5bc0de,stroke:#31b0d5,color:#000\n");
        mermaid.append("    classDef poste   fill:#5cb85c,stroke:#4cae4c,color:#000\n");

        // Affectation de chaque nœud à sa classe de style
        for (AdresseReseau a : adresses) {
            String id = sanitizeId(a.getNom());
            switch (a.getType()) {
                case "Routeur": mermaid.append(String.format("    class %s routeur\n", id)); break;
                case "Serveur": mermaid.append(String.format("    class %s serveur\n", id)); break;
                default:        mermaid.append(String.format("    class %s poste\n",   id)); break;
            }
        }

        mermaid.append("```\n");
        return mermaid.toString();
    }

    /**
     * Transforme un nom d'équipement en identifiant Mermaid valide.
     * Remplace tout caractère non alphanumérique (tirets, espaces, points…)
     * par un underscore {@code _}.
     *
     * <p>Exemple : {@code "Routeur-Principal"} → {@code "Routeur_Principal"}
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
     * Méthode principale : orchestre la saisie utilisateur, la construction
     * du rapport Markdown et l'écriture du fichier de sortie.
     *
     * @param args arguments de la ligne de commande (non utilisés)
     */
    public static void main(String[] args) {

        // ── Choix du moteur IA ────────────────────────────────────────────────
        // On lit le choix avant de fermer le Scanner (try-with-resources)
        // pour éviter de fermer System.in trop tôt.
        int choixIA;
        try (Scanner scanner = new Scanner(System.in)) {
            System.out.println("=== Choisissez le modèle d'analyse IA ===");
            System.out.println("1. Gemini 2.5 Flash-Lite (gratuit, variable GOOGLE_API_KEY)");
            System.out.println("2. OpenRouter Free       (gratuit, variable OPENROUTER_API_KEY)");
            System.out.println("0. Aucune analyse IA");
            System.out.print("Votre choix : ");
            choixIA = scanner.nextInt();
        }

        // =========================================================================
        // Déclaration des équipements réseau
        // =========================================================================
        // Chaque AdresseReseau prend : nom, type, adresse IP (String), masque (String).
        // Les types reconnus pour le graphe Mermaid sont : "Routeur", "Serveur", "Ordinateur".
        // Tout autre type sera affiché en rectangle vert générique.

        List<AdresseReseau> adresses = new ArrayList<>();

        // ── Cœur du réseau : routeurs ─────────────────────────────────────────
        AdresseReseau routeurPrincipal = new AdresseReseau("Routeur-Principal", "Routeur", "172.16.0.1",   "255.255.0.0");
        AdresseReseau routeurSite2     = new AdresseReseau("Routeur-Site2",     "Routeur", "172.16.1.1",   "255.255.0.0");

        // ── Zone serveurs (réseau 10.0.0.0/8) ────────────────────────────────
        AdresseReseau serveurWeb  = new AdresseReseau("Serveur-Web",  "Serveur",    "10.0.0.50",  "255.0.0.0");
        AdresseReseau serveurBdd  = new AdresseReseau("Serveur-BDD",  "Serveur",    "10.0.0.51",  "255.0.0.0");
        AdresseReseau serveurFtp  = new AdresseReseau("Serveur-FTP",  "Serveur",    "10.0.0.52",  "255.0.0.0");
        AdresseReseau serveurDns  = new AdresseReseau("Serveur-DNS",  "Serveur",    "10.0.0.10",  "255.0.0.0");

        // ── Zone postes de travail site 1 (réseau 192.168.10.0/24) ───────────
        AdresseReseau pc1         = new AdresseReseau("PC1",          "Ordinateur", "192.168.10.100", "255.255.255.0");
        AdresseReseau pc2         = new AdresseReseau("PC2",          "Ordinateur", "192.168.10.101", "255.255.255.0");
        AdresseReseau pc3         = new AdresseReseau("PC3",          "Ordinateur", "192.168.10.102", "255.255.255.0");
        AdresseReseau imprimante  = new AdresseReseau("Imprimante-A", "Imprimante", "192.168.10.200", "255.255.255.0");

        // ── Zone postes de travail site 2 (réseau 192.168.20.0/24) ───────────
        AdresseReseau pc4         = new AdresseReseau("PC4",          "Ordinateur", "192.168.20.100", "255.255.255.0");
        AdresseReseau pc5         = new AdresseReseau("PC5",          "Ordinateur", "192.168.20.101", "255.255.255.0");
        AdresseReseau pointAcces  = new AdresseReseau("AP-WiFi-S2",   "Borne WiFi", "192.168.20.1",   "255.255.255.0");

        // Ajout de tous les équipements à la liste principale
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
        // ConnexionReseau(équipementA, équipementB, typeConnexion, débitMbps)
        // La flèche dans le graphe va de A vers B.

        List<ConnexionReseau> connexions = new ArrayList<>();

        // Liaison inter-routeurs (WAN fibre longue distance)
        connexions.add(new ConnexionReseau(routeurPrincipal, routeurSite2,    "Fibre WAN",  1000));

        // Serveurs → routeur principal (liaisons haut débit)
        connexions.add(new ConnexionReseau(serveurWeb,       routeurPrincipal,"Fibre",      10000));
        connexions.add(new ConnexionReseau(serveurBdd,       routeurPrincipal,"Fibre",      10000));
        connexions.add(new ConnexionReseau(serveurFtp,       routeurPrincipal,"Fibre",       5000));
        connexions.add(new ConnexionReseau(serveurDns,       routeurPrincipal,"Ethernet",    1000));

        // Postes site 1 → routeur principal
        connexions.add(new ConnexionReseau(pc1,              routeurPrincipal,"Ethernet",    1000));
        connexions.add(new ConnexionReseau(pc2,              routeurPrincipal,"Ethernet",    1000));
        connexions.add(new ConnexionReseau(pc3,              routeurPrincipal,"Ethernet",    1000));
        connexions.add(new ConnexionReseau(imprimante,       routeurPrincipal,"Ethernet",     100));

        // Postes site 2 → routeur site 2 (via borne WiFi ou filaire)
        connexions.add(new ConnexionReseau(pc4,              routeurSite2,    "Ethernet",    1000));
        connexions.add(new ConnexionReseau(pc5,              pointAcces,      "WiFi",         300));
        connexions.add(new ConnexionReseau(pointAcces,       routeurSite2,    "Ethernet",    1000));

        // =========================================================================
        // Calcul des statistiques globales
        // =========================================================================

        long totalEquipements = adresses.size();

        // Regroupement par type pour le tableau de répartition
        Map<String, Long> repartitionParType = adresses.stream()
                .collect(Collectors.groupingBy(AdresseReseau::getType, Collectors.counting()));

        // Somme de tous les hôtes adressables sur l'ensemble des sous-réseaux
        long totalHotesPossibles = adresses.stream()
                .mapToLong(AdresseReseau::nombreHotes)
                .sum();

        // =========================================================================
        // Construction du rapport Markdown
        // =========================================================================

        StringBuilder md = new StringBuilder();

        md.append("# Tableau de Bord & Statistiques Réseau\n\n");
        md.append("Ce rapport fournit une analyse globale des infrastructures réseau configurées.\n\n");

        // ── Section 1 : Topologie Mermaid ─────────────────────────────────────
        // genererMermaidTopologie() construit le bloc ```mermaid``` à partir
        // des listes d'équipements et de connexions déclarées plus haut.
        md.append("## Topologie Réseau\n\n");
        md.append("> 🟠 Routeurs  🔵 Serveurs  🟢 Postes / Autres\n\n");
        md.append(genererMermaidTopologie(adresses, connexions));
        md.append("\n");

        // ── Section 2 : Indicateurs clés ──────────────────────────────────────
        md.append("## Indicateurs Clés\n\n");
        md.append(String.format("- **Nombre total d'équipements enregistrés :** %d\n", totalEquipements));
        md.append(String.format("- **Capacité totale d'adresses hôtes disponibles :** %,d\n\n", totalHotesPossibles));

        // ── Section 3 : Répartition par type ──────────────────────────────────
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

        // ── Section 4 : Détail des équipements ────────────────────────────────
        // Pour chaque équipement : on extrait l'IP et le masque depuis toString()
        // puis on calcule l'adresse réseau et le broadcast via les méthodes dédiées.
        md.append("## Détail des Équipements\n\n");
        md.append("| Nom | Type | Adresse IP | Masque | Réseau | Broadcast | Hôtes du sous-réseau |\n");
        md.append("| :--- | :--- | :--- | :--- | :--- | :--- | :---: |\n");
        for (AdresseReseau a : adresses) {
            String ipStr        = a.toString().split(" : ")[1].split(" / ")[0];
            String masqueStr    = a.toString().split(" / ")[1];
            String reseauStr    = a.adresseReseau().toString().split(" : ")[1].split(" / ")[0];
            String broadcastStr = a.adresseBroadcast().toString().split(" : ")[1].split(" / ")[0];
            md.append(String.format("| %s | %s | %s | %s | %s | %s | %,d |\n",
                    a.getNom(),
                    a.getType().isEmpty() ? "Inconnu" : a.getType(),
                    ipStr, masqueStr, reseauStr, broadcastStr,
                    a.nombreHotes()));
        }
        md.append("\n");

        // ── Section 5 : Tableau des connexions ────────────────────────────────
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

        // ── Section 6 : Réseaux distincts ─────────────────────────────────────
        // On groupe les équipements par adresse réseau (ex. 192.168.10.0)
        // pour identifier les sous-réseaux présents dans l'infrastructure.
        md.append("## Réseaux Distincts\n\n");
        md.append("| Réseau | Masque | Broadcast | Équipements | Hôtes max |\n");
        md.append("| :--- | :--- | :--- | :--- | :---: |\n");
        Map<String, List<AdresseReseau>> parReseau = adresses.stream()
                .collect(Collectors.groupingBy(
                        a -> a.adresseReseau().toString().split(" : ")[1].split(" / ")[0]));
        for (Map.Entry<String, List<AdresseReseau>> entree : parReseau.entrySet()) {
            AdresseReseau ref    = entree.getValue().get(0);
            String adresseReseau = entree.getKey();
            String masque        = ref.adresseReseau().toString().split(" / ")[1];
            String broadcast     = ref.adresseBroadcast().toString().split(" : ")[1].split(" / ")[0];
            String equipements   = entree.getValue().stream()
                    .map(AdresseReseau::getNom)
                    .collect(Collectors.joining(", "));
            md.append(String.format("| %s | %s | %s | %s | %,d |\n",
                    adresseReseau, masque, broadcast, equipements, ref.nombreHotes()));
        }
        md.append("\n");

        // =========================================================================
        // Analyse IA (optionnelle)
        // =========================================================================

        switch (choixIA) {

            // ── Cas 1 : Google Gemini ──────────────────────────────────────────
            // Utilise le SDK officiel Google GenAI.
            // La clé API doit être définie dans la variable d'environnement GOOGLE_API_KEY.
            case 1:
                String geminiKey = System.getenv("GOOGLE_API_KEY");
                if (geminiKey == null || geminiKey.isBlank()) {
                    System.err.println("Erreur : variable d'environnement GOOGLE_API_KEY non définie.");
                    break;
                }
                System.out.println("Analyse IA avec Gemini 2.5 Flash-Lite...");
                try (Client client = new Client()) {
                    GenerateContentResponse response = client.models.generateContent(
                            "gemini-2.5-flash-lite",
                            "Voici un rapport réseau en Markdown :\n\n" + md.toString()
                                + "\n\nFais un résumé de l'état de ce réseau, en français."
                                + " Les premiers titres doivent être en ####",
                            null);
                    md.append("## Analyse IA\n\n");
                    md.append(response.text())
                      .append("\nAnalyse réalisée avec Gemini 2.5 Flash-Lite.\n\n");
                } catch (Exception e) {
                    System.err.println("Erreur Gemini : " + e.getMessage());
                }
                break;

            // ── Cas 2 : OpenRouter ────────────────────────────────────────────
            // Appel REST manuel vers l'API OpenRouter (compatible OpenAI).
            // La clé API doit être définie dans la variable d'environnement OPENROUTER_API_KEY.
            case 2:
                String apiKey = System.getenv("OPENROUTER_API_KEY");
                if (apiKey == null || apiKey.isBlank()) {
                    System.err.println("Erreur : variable d'environnement OPENROUTER_API_KEY non définie.");
                    break;
                }
                System.out.println("Analyse IA avec OpenRouter Free...");
                try {
                    // Construction du corps JSON de la requête (format ChatML)
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

                    // Envoi de la requête HTTP POST avec le client Java 11+
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

                    // Désérialisation et extraction du texte généré
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();
                    if (json.has("error")) {
                        System.err.println("Erreur OpenRouter : "
                                + json.getAsJsonObject("error").get("message").getAsString());
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
                } catch (com.google.gson.JsonSyntaxException e) {
                    System.err.println("Erreur OpenRouter (JSON) : " + e.getMessage());
                } catch (RuntimeException e) {
                    System.err.println("Erreur OpenRouter : " + e.getMessage());
                }
                break;

            default:
                System.out.println("Aucune analyse IA sélectionnée.");
        }

        // =========================================================================
        // Écriture du fichier de sortie
        // =========================================================================

        try {
            Files.writeString(Path.of("rapport_statistiques.md"), md.toString());
            System.out.println("Rapport 'rapport_statistiques.md' généré avec succès !");
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écriture du fichier : " + e.getMessage());
        }
    }
}