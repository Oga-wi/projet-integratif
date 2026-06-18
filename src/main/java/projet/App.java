package projet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class App {

    private App() {
    }

    public static void main(String[] args) {
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
                    a.nombreHotes()
            ));
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

        try {
            Files.writeString(Path.of("rapport_statistiques.md"), md.toString());
            System.out.println("Rapport 'rapport_statistiques.md' généré avec succès !");
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écriture du fichier : " + e.getMessage());
        }
    }
}
