package projet;

import java.time.LocalDate;

public class Metadonnees {
    private String auteur;
    private LocalDate dateCreation;
    private String titre;
    private String entreprise;
    private Double version;

    public Metadonnees(String auteur, LocalDate dateCreation, String titre, String entreprise, Double version) {
        this.auteur = auteur;
        this.dateCreation = dateCreation;
        this.titre = titre;
        this.entreprise = entreprise;
        this.version = version;
    }

    public String getAuteur() { return auteur; }
    public LocalDate getDateCreation() { return dateCreation; }
    public String getTitre() { return titre; }
    public String getEntreprise() { return entreprise; }
    public Double getVersion() { return version; }
}