package projet;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class App {

    private App() {
    }

    public static void main(String[] args) {
        List<AdresseReseau> adresses = new ArrayList<>();

        adresses.add(new AdresseReseau("PC1","Ordinateur", "192.168.10.100", "255.255.255.0"));
        adresses.add(new AdresseReseau("Serveur", "Serveur", "10.0.0.50", "255.0.0.0"));
        adresses.add(new AdresseReseau("Routeur","Routeur","172.16.0.1", "255.255.0.0"));

        for (AdresseReseau a : adresses) {
            System.out.println(a);
            System.out.println("Réseau : " + a.adresseReseau());
            System.out.println("Broadcast : " + a.adresseBroadcast());
            System.out.println("Hôtes : " + a.nombreHotes());
            System.out.println("---");
        }

        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < adresses.size(); i++) {
            json.append(adresses.get(i).toJson());
            if (i < adresses.size() - 1) {
                json.append(",");
            }
        }
        json.append("]");

        try {
            Files.writeString(Path.of("adresses.json"), json.toString());
            System.out.println("Fichier adresses.json créé avec succès.");
        } catch (IOException e) {
            System.err.println("Erreur lors de l'écriture du fichier : " + e.getMessage());
        }

        System.out.println(json);
    }
}