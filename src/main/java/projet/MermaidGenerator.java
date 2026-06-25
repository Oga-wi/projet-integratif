package projet;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * Utilitaire de génération de diagrammes Mermaid pour la topologie réseau.
 */
public final class MermaidGenerator {

    private MermaidGenerator() {}

    /**
     * Génère un bloc de code Mermaid (syntaxe {@code graph TD}) représentant la
     * topologie du réseau, avec des sous-graphes par VLAN.
     *
     * @param adresses   liste de tous les équipements à afficher comme nœuds
     * @param connexions liste des liaisons physiques entre équipements
     * @return chaîne Markdown contenant le bloc {@code ```mermaid ... ```}
     */
    public static String genererTopologie(List<AdresseReseau> adresses,
            List<ConnexionReseau> connexions) {
        StringBuilder mermaid = new StringBuilder();

        mermaid.append("```mermaid\ngraph TD\n");

        // ── Nœuds hors VLAN (routeurs / infrastructure) ───────────────────────
        for (AdresseReseau a : adresses) {
            if (a.getVlan() != 0) continue;
            String id = sanitizeId(a.getNom());
            String ip = a.toString().split(" : ")[1].split(" / ")[0];
            String label = a.getNom() + "\\n" + ip;
            mermaid.append(String.format("    %s{{%s}}\n", id, label));
        }

        mermaid.append("\n");

        // ── Subgraphs par VLAN ────────────────────────────────────────────────
        Map<Integer, String> nomsVlan = new LinkedHashMap<>();
        nomsVlan.put(10, "VLAN 10 — Serveurs DMZ");
        nomsVlan.put(20, "VLAN 20 — Postes Site Principal");
        nomsVlan.put(30, "VLAN 30 — Postes Site 2");

        Map<Integer, List<AdresseReseau>> parVlan = adresses.stream()
                .filter(a -> a.getVlan() != 0)
                .collect(Collectors.groupingBy(AdresseReseau::getVlan));

        new TreeMap<>(parVlan).forEach((vlanId, membres) -> {
            String nomVlan = nomsVlan.getOrDefault(vlanId, "VLAN " + vlanId);
            mermaid.append(String.format("    subgraph %s[\"%s\"]\n", "vlan" + vlanId, nomVlan));
            for (AdresseReseau a : membres) {
                String id = sanitizeId(a.getNom());
                String ip = a.toString().split(" : ")[1].split(" / ")[0];
                String label = a.getNom() + "\\n" + ip;
                switch (a.getType()) {
                    case "Serveur":
                        mermaid.append(String.format("        %s(%s)\n", id, label));
                        break;
                    default:
                        mermaid.append(String.format("        %s[%s]\n", id, label));
                        break;
                }
            }
            mermaid.append("    end\n");
        });

        mermaid.append("\n");

        // ── Connexions ────────────────────────────────────────────────────────
        for (ConnexionReseau c : connexions) {
            String idA = sanitizeId(c.getEquipementA().getNom());
            String idB = sanitizeId(c.getEquipementB().getNom());
            String liaison = c.getTypeConnexion() + " " + c.getDebitMbps() + " Mbps";
            mermaid.append(String.format("    %s -- \"%s\" --> %s\n", idA, liaison, idB));
        }

        mermaid.append("\n");

        // ── Styles ────────────────────────────────────────────────────────────
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
    public static String sanitizeId(String nom) {
        return nom.replaceAll("[^a-zA-Z0-9]", "_");
    }
}