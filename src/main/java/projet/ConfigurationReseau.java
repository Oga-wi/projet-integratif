package projet;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Menu interactif permettant à l'utilisateur de saisir manuellement
 * les équipements et connexions d'une infrastructure réseau.
 */
public final class ConfigurationReseau {

    private ConfigurationReseau() {}

    /**
     * Lance la saisie interactive et retourne les listes peuplées.
     *
     * @param scanner le Scanner partagé avec App (ne pas le fermer ici)
     * @return un tableau à deux éléments : [0] List<AdresseReseau>, [1] List<ConnexionReseau>
     */
    public static Object[] saisirReseau(Scanner scanner) {
        List<AdresseReseau> adresses = new ArrayList<>();
        List<ConnexionReseau> connexions = new ArrayList<>();

        // ── Saisie des équipements ────────────────────────────────────────────
        System.out.println("\n=== Saisie des équipements réseau ===");
        System.out.println("Types disponibles : Routeur, Serveur, Ordinateur, Imprimante, Borne WiFi, Switch, Firewall");

        boolean ajouterEquipement = true;
        while (ajouterEquipement) {
            System.out.println("\n--- Nouvel équipement ---");

            System.out.print("Nom (ex: Routeur-Principal) : ");
            String nom = scanner.nextLine().trim();
            while (nom.isEmpty()) {
                System.out.print("Nom invalide. Réessayez : ");
                nom = scanner.nextLine().trim();
            }

            System.out.print("Type (ex: Routeur) : ");
            String type = scanner.nextLine().trim();
            if (type.isEmpty()) type = "Inconnu";

            String ip = saisirAdresseIP(scanner, "Adresse IP (ex: 192.168.1.1) : ");
            String masque = saisirMasque(scanner);

            AdresseReseau equipement = new AdresseReseau(nom, type, ip, masque);

            // VLAN
            System.out.print("VLAN (0 = aucun, ex: 10) : ");
            int vlan = lireEntier(scanner, 0, 4094);
            if (vlan != 0) {
                equipement.setVlan(vlan);
            }

            adresses.add(equipement);
            System.out.println("✔ Équipement \"" + nom + "\" ajouté (" + adresses.size() + " au total).");

            System.out.print("\nAjouter un autre équipement ? (o/n) : ");
            String rep = scanner.nextLine().trim().toLowerCase();
            ajouterEquipement = rep.equals("o") || rep.equals("oui") || rep.equals("y") || rep.equals("yes");
        }

        if (adresses.size() < 2) {
            System.out.println("⚠ Moins de 2 équipements — les connexions nécessitent au moins 2 équipements.");
            return new Object[]{adresses, connexions};
        }

        // ── Saisie des connexions ─────────────────────────────────────────────
        System.out.println("\n=== Saisie des connexions réseau ===");
        System.out.println("Types disponibles : Ethernet, Fibre, Fibre WAN, WiFi, Série");

        boolean ajouterConnexion = true;
        while (ajouterConnexion) {
            System.out.println("\n--- Nouvelle connexion ---");

            // Affichage de la liste numérotée
            System.out.println("Équipements disponibles :");
            for (int i = 0; i < adresses.size(); i++) {
                System.out.printf("  %2d. %s%n", i + 1, adresses.get(i).getNom());
            }

            System.out.print("Numéro de l'équipement A : ");
            int indexA = lireEntier(scanner, 1, adresses.size()) - 1;

            System.out.print("Numéro de l'équipement B : ");
            int indexB = lireEntier(scanner, 1, adresses.size()) - 1;
            while (indexB == indexA) {
                System.out.println("A et B doivent être différents.");
                System.out.print("Numéro de l'équipement B : ");
                indexB = lireEntier(scanner, 1, adresses.size()) - 1;
            }

            System.out.print("Type de liaison (ex: Ethernet) : ");
            String typeLiaison = scanner.nextLine().trim();
            if (typeLiaison.isEmpty()) typeLiaison = "Ethernet";

            System.out.print("Débit en Mbps (ex: 1000) : ");
            int debit = lireEntier(scanner, 0, Integer.MAX_VALUE);

            connexions.add(new ConnexionReseau(adresses.get(indexA), adresses.get(indexB), typeLiaison, debit));
            System.out.println("✔ Connexion "
                    + adresses.get(indexA).getNom() + " <-> " + adresses.get(indexB).getNom() + " ajoutée.");

            System.out.print("\nAjouter une autre connexion ? (o/n) : ");
            String rep = scanner.nextLine().trim().toLowerCase();
            ajouterConnexion = rep.equals("o") || rep.equals("oui") || rep.equals("y") || rep.equals("yes");
        }

        System.out.println("\n✔ Configuration terminée : "
                + adresses.size() + " équipement(s), "
                + connexions.size() + " connexion(s).");

        return new Object[]{adresses, connexions};
    }

    // =========================================================================
    // Helpers privés
    // =========================================================================

    /** Demande une adresse IPv4 valide à l'utilisateur. */
    private static String saisirAdresseIP(Scanner scanner, String invite) {
        while (true) {
            System.out.print(invite);
            String valeur = scanner.nextLine().trim();
            if (estIPValide(valeur)) return valeur;
            System.out.println("Adresse IP invalide (format attendu : x.x.x.x avec x entre 0 et 255).");
        }
    }

    /**
     * Demande un masque de sous-réseau, acceptant soit la notation
     * décimale pointée (255.255.255.0) soit la notation CIDR (/24).
     */
    private static String saisirMasque(Scanner scanner) {
        System.out.println("Masque : notation décimale (ex: 255.255.255.0) ou CIDR (ex: /24 ou 24)");
        while (true) {
            System.out.print("Masque : ");
            String valeur = scanner.nextLine().trim();

            // Notation CIDR avec ou sans slash
            String cidrStr = valeur.startsWith("/") ? valeur.substring(1) : valeur;
            try {
                int cidr = Integer.parseInt(cidrStr);
                if (cidr >= 0 && cidr <= 32) {
                    // Convertit en notation décimale pour AdresseReseau
                    int masqueInt = AdresseReseau.prefixeEnMasque(cidr);
                    return AdresseReseau.entierEnAdresse(masqueInt);
                }
            } catch (NumberFormatException ignored) {
                // pas un entier, on essaie la notation décimale
            }

            // Notation décimale pointée
            if (estIPValide(valeur)) return valeur;

            System.out.println("Masque invalide. Exemples valides : 255.255.255.0  /24  24  255.0.0.0");
        }
    }

    /** Valide qu'une chaîne est une adresse IPv4 correcte. */
    private static boolean estIPValide(String valeur) {
        if (valeur == null || valeur.isEmpty()) return false;
        String[] octets = valeur.split("\\.", -1);
        if (octets.length != 4) return false;
        for (String octet : octets) {
            try {
                int v = Integer.parseInt(octet);
                if (v < 0 || v > 255) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    /**
     * Lit un entier dans l'intervalle [min, max] et redemande
     * tant que l'entrée est invalide.
     */
    private static int lireEntier(Scanner scanner, int min, int max) {
        while (true) {
            try {
                String ligne = scanner.nextLine().trim();
                int valeur = Integer.parseInt(ligne);
                if (valeur >= min && valeur <= max) return valeur;
                System.out.printf("Valeur hors limites (%d-%d). Réessayez : ", min, max);
            } catch (NumberFormatException e) {
                System.out.print("Entrée invalide (nombre entier attendu). Réessayez : ");
            }
        }
    }
}