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
import com.google.gson.JsonParser; // <-- Import réajouté ici

public final class App {
    private App() {
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== Choisissez le modèle d'analyse IA ===");
        System.out.println("1. Gemini 2.5 Flash-Lite (gratuit)");
        System.out.println("2. OpenRouter Free (gratuit)");
        System.out.println("0. Aucune analyse IA");
        System.out.print("Votre choix : ");

        int choixIA = scanner.nextInt();
        scanner.close();

        List<AdresseReseau> adresses = new ArrayList<>();

        AdresseReseau pc1 = new AdresseReseau("PC1", "Ordinateur", "192.168.10.100", "255.255.255.0");
        AdresseReseau pc2 = new AdresseReseau("PC2", "Ordinateur", "192.168.10.101", "255.255.255.0");
        AdresseReseau serveurWeb = new AdresseReseau("Serveur-Web", "Serveur", "10.0.0.50", "255.0.0.0");
        AdresseReseau serveurBdd = new AdresseReseau("Serveur-BDD", "Serveur", "10.0.0.51", "255.0.0.0");
        AdresseReseau routeur = new AdresseReseau("Routeur-Principal", "Routeur", "172.16.0.1", "255.255.0.0");

        adresses.add(pc1);
        adresses.add(pc2);
        adresses.add(serveurWeb);
        adresses.add(serveurBdd);
        adresses.add(routeur);

        List<ConnexionReseau> connexions = new ArrayList<>();
        connexions.add(new ConnexionReseau(pc1, routeur, "Ethernet", 1000));
        connexions.add(new ConnexionReseau(pc2, routeur, "Ethernet", 1000));
        connexions.add(new ConnexionReseau(serveurWeb, routeur, "Fibre", 10000));
        connexions.add(new ConnexionReseau(serveurBdd, routeur, "Fibre", 10000));

        long totalEquipements = adresses.size();
        Map<String, Long> repartitionParType = adresses.stream()
                .collect(Collectors.groupingBy(AdresseReseau::getType, Collectors.counting()));

        long totalHotesPossibles = adresses.stream()
                .mapToLong(AdresseReseau::nombreHotes)
                .sum();

        StringBuilder md = new StringBuilder();

        md.append("# Tableau de Bord & Statistiques Réseau\n\n");
        md.append("Ce rapport fournit une analyse globale des infrastructures réseau configurées.\n\n");

        md.append("## Indicateurs Clés\n");
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
                    ipStr,
                    masqueStr,
                    reseauStr,
                    broadcastStr,
                    a.nombreHotes()));
        }

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
                .collect(Collectors.groupingBy(a -> a.adresseReseau().toString().split(" : ")[1].split(" / ")[0]));

        for (Map.Entry<String, List<AdresseReseau>> entree : parReseau.entrySet()) {
            AdresseReseau ref = entree.getValue().get(0);
            String adresseReseau = entree.getKey();
            String masque = ref.adresseReseau().toString().split(" / ")[1];
            String broadcast = ref.adresseBroadcast().toString().split(" : ")[1].split(" / ")[0];
            String equipements = entree.getValue().stream()
                    .map(AdresseReseau::getNom)
                    .collect(Collectors.joining(", "));

            md.append(String.format("| %s | %s | %s | %s | %,d |\n",
                    adresseReseau,
                    masque,
                    broadcast,
                    equipements,
                    ref.nombreHotes()));
        }
        md.append("\n");

        switch (choixIA) {
            case 1:
                System.out.println("Analyse IA avec Gemini 2.5 Flash-Lite...");
                try {
                    Client client = new Client();
                    GenerateContentResponse response = client.models.generateContent(
                            "gemini-2.5-flash-lite",
                            "Voici un rapport réseau en Markdown :\n\n" + md.toString() +
                                    "\n\nFais un résumé de l'état de ce réseau, en français. Les premier titre doivent être en ####",
                            null);
                    String analyse = response.text();

                    md.append("## Analyse IA\n\n");
                    md.append(analyse).append("\n");

                    System.out.println("\n=== Analyse Gemini ===");
                    System.out.println(analyse);

                } catch (Exception e) {
                    System.err.println("Erreur Gemini : " + e.getMessage());
                }
                break;

            case 2:
                System.out.println("Analyse IA avec OpenRouter Free...");
                try {
                    String SYSTEM_PROMPT = "Tu es un assistant utile qui analyse des rapports réseau.";
                    String contenu = "Analyse ce rapport réseau :\n\n" + md.toString();

                    JsonObject messageSystem = new JsonObject();
                    messageSystem.addProperty("role", "system");
                    messageSystem.addProperty("content", SYSTEM_PROMPT);

                    JsonObject messageUser = new JsonObject();
                    messageUser.addProperty("role", "user");
                    messageUser.addProperty("content", contenu);

                    com.google.gson.JsonArray messages = new com.google.gson.JsonArray();
                    messages.add(messageSystem);
                    messages.add(messageUser);

                    JsonObject jsonRequestBody = new JsonObject();
                    jsonRequestBody.addProperty("model", "openrouter/free");
                    jsonRequestBody.add("messages", messages);

                    String body = jsonRequestBody.toString();

                    HttpClient httpClient = HttpClient.newHttpClient();
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("https://openrouter.ai/api/v1/chat/completions"))
                            .header("Content-Type", "application/json")
                            .header("Authorization", "Bearer " + System.getenv("OPENROUTER_API_KEY"))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    String responseBody = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body();
                    JsonObject json = JsonParser.parseString(responseBody).getAsJsonObject();

                    if (json.has("error")) {
                        System.err.println("Erreur renvoyée par l'API OpenRouter : "
                                + json.getAsJsonObject("error").get("message").getAsString());
                        break;
                    }

                    String analyse = json.getAsJsonArray("choices")
                            .get(0).getAsJsonObject()
                            .getAsJsonObject("message")
                            .get("content").getAsString();

                    md.append("## Analyse IA\n\n").append(analyse).append("\n");
                    System.out.println("\n=== Analyse OpenRouter ===\n" + analyse);

                } catch (Exception e) {
                    System.err.println("Erreur OpenRouter : " + e.getMessage());
                }
                break;

            default:
                System.out.println("Aucune dynamic IA sélectionnée.");
        }

        try {
            Files.writeString(Path.of("rapport_statistiques.md"), md.toString());
            System.out.println("Rapport 'rapport_statistiques.md' généré avec succès !");
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écriture du fichier : " + e.getMessage());
        }
    }
}