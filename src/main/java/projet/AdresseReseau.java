package projet;

public class AdresseReseau {

    private String nom;
    private String type;
    private int ip;
    private int masque;
    private int vlan; // 0 = pas de VLAN

    // Constructeurs sans nom (compatibilité avec l'existant)
    public AdresseReseau(int ip, int masque) {
        this("", ip, masque);
    }

    public AdresseReseau(String ip, String masque) {
        this("", "", ip, masque);
    }

    public AdresseReseau(String ip, int prefixeCIDR) {
        this("", "", adresseEnEntier(ip), prefixeEnMasque(prefixeCIDR));
    }

    public AdresseReseau(String nom, int ip, int masque) {
        this(nom, "", ip, masque);
    }

    // Constructeurs avec nom
    public AdresseReseau(String nom, String type, int ip, int masque) {
        this.nom = nom;
        this.type = type;
        this.ip = ip;
        this.masque = masque;
        this.vlan = 0;
    }

    public AdresseReseau(String nom, String type, String ip, String masque) {
        this(nom, type, adresseEnEntier(ip), adresseEnEntier(masque));
    }

    public AdresseReseau(String nom, String ip, int prefixeCIDR) {
        this(nom, adresseEnEntier(ip), prefixeEnMasque(prefixeCIDR));
    }

    public static int adresseEnEntier(String adresse) {
        String[] octets = adresse.split("\\.");
        int resultat = 0;
        for (String octet : octets) {
            resultat = (resultat << 8) | Integer.parseInt(octet);
        }
        return resultat;
    }

    public static int prefixeEnMasque(int prefixeCIDR) {
        if (prefixeCIDR == 0) {
            return 0;
        }
        return (int) (0xFFFFFFFFL << (32 - prefixeCIDR));
    }

    public static String entierEnAdresse(int valeur) {
        return String.format("%d.%d.%d.%d",
                (valeur >>> 24) & 0xFF,
                (valeur >>> 16) & 0xFF,
                (valeur >>> 8) & 0xFF,
                valeur & 0xFF);
    }

    public String getNom() {
        return nom;
    }

    public void setNom(String nom) {
        this.nom = nom;
    }

    public int getVlan() {
        return vlan;
    }

    public void setVlan(int vlan) {
        this.vlan = vlan;
    }

    public AdresseReseau adresseReseau() {
        int reseau = this.ip & this.masque;
        return new AdresseReseau(this.nom, reseau, this.masque);
    }

    public AdresseReseau adresseBroadcast() {
        int broadcast = this.ip | ~this.masque;
        return new AdresseReseau(this.nom, broadcast, this.masque);
    }

    public long nombreHotes() {
        int bitsHotes = Integer.bitCount(~this.masque);
        if (bitsHotes == 0) {
            return 1;
        }
        return (1L << bitsHotes) - 2;
    }

    public String toJson() {
        return String.format(
                "{\"nom\":\"%s\",\"type\":\"%s\",\"ip\":\"%s\",\"masque\":\"%s\",\"adresse reseau\":\"%s\",\"adresse broadcast\":\"%s\",\"nombre hotes\":%d,\"vlan\":%d}",
                nom,
                type,
                entierEnAdresse(ip),
                entierEnAdresse(masque),
                entierEnAdresse(adresseReseau().ip),
                entierEnAdresse(adresseBroadcast().ip),
                nombreHotes(),
                vlan
        );
    }

    public String getType() {
        return this.type;
    }

    @Override
    public String toString() {
        return nom + " : " + entierEnAdresse(ip) + " / " + entierEnAdresse(masque);
    }
}