package projet;

public class ConnexionReseau {

    private AdresseReseau equipementA;
    private AdresseReseau equipementB;
    private String typeConnexion;
    private int debitMbps;

    public ConnexionReseau(AdresseReseau equipementA, AdresseReseau equipementB, String typeConnexion) {
        this(equipementA, equipementB, typeConnexion, 0);
    }

    public ConnexionReseau(AdresseReseau equipementA, AdresseReseau equipementB, String typeConnexion, int debitMbps) {
        this.equipementA = equipementA;
        this.equipementB = equipementB;
        this.typeConnexion = typeConnexion;
        this.debitMbps = debitMbps;
    }

    public AdresseReseau getEquipementA() {
        return equipementA;
    }

    public AdresseReseau getEquipementB() {
        return equipementB;
    }

    public String getTypeConnexion() {
        return typeConnexion;
    }

    public int getDebitMbps() {
        return debitMbps;
    }

    public String toJson() {
        return String.format(
                "{\"equipementA\":\"%s\",\"equipementB\":\"%s\",\"type\":\"%s\",\"debitMbps\":%d}",
                equipementA.getNom(),
                equipementB.getNom(),
                typeConnexion,
                debitMbps
        );
    }

    @Override
    public String toString() {
        return equipementA.getNom() + " <-> " + equipementB.getNom() + " (" + typeConnexion + ")";
    }
}