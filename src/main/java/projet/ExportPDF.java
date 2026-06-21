package projet;

import java.awt.Color;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

/**
 * Utilitaire de conversion d'un rapport Markdown réseau en fichier PDF.
 *
 * <p>Gère : titres H1/H2, listes à puces, blocs de code, blockquotes
 * et tableaux Markdown (lignes {@code | ... |}).</p>
 */
public final class ExportPDF {

    private ExportPDF() {}

    // ── Couleurs ──────────────────────────────────────────────────────────────
    private static final Color BLEU_TITRE   = new Color(15, 52, 96);
    private static final Color BLEU_H2      = new Color(22, 33, 62);
    private static final Color BLEU_ENTETE  = new Color(15, 52, 96);
    private static final Color GRIS_PAIR    = new Color(240, 244, 251);
    private static final Color ROUGE_ACCENT = new Color(233, 69, 96);
    private static final Color CODE_BG      = new Color(26, 26, 46);
    private static final Color CODE_FG      = new Color(168, 218, 220);

    // ── Polices ───────────────────────────────────────────────────────────────
    private static final Font FONT_H1     = new Font(Font.HELVETICA, 18, Font.BOLD,   BLEU_TITRE);
    private static final Font FONT_H2     = new Font(Font.HELVETICA, 13, Font.BOLD,   BLEU_H2);
    private static final Font FONT_NORMAL = new Font(Font.HELVETICA,  9, Font.NORMAL, Color.BLACK);
    private static final Font FONT_BOLD   = new Font(Font.HELVETICA,  9, Font.BOLD,   Color.BLACK);
    private static final Font FONT_QUOTE  = new Font(Font.HELVETICA,  9, Font.ITALIC, new Color(85, 85, 85));
    private static final Font FONT_CODE   = new Font(Font.COURIER,    8, Font.NORMAL, CODE_FG);
    private static final Font FONT_ENTETE = new Font(Font.HELVETICA,  9, Font.BOLD,   Color.WHITE);

    /**
     * Convertit un rapport Markdown en PDF.
     *
     * @param markdown     contenu Markdown à convertir
     * @param cheminSortie chemin du fichier PDF à écrire
     */
    public static void exporter(String markdown, String cheminSortie) {
        try (Document doc = new Document(PageSize.A4, 50, 50, 60, 60)) {

            PdfWriter.getInstance(doc, new FileOutputStream(cheminSortie));
            doc.open();

            String[] lignes = markdown.split("\n");
            int i = 0;

            while (i < lignes.length) {
                String ligne = lignes[i];

                // ── H1 ────────────────────────────────────────────────────────
                if (ligne.startsWith("# ")) {
                    Paragraph p = new Paragraph(ligne.substring(2), FONT_H1);
                    p.setSpacingAfter(10);
                    doc.add(p);

                // ── H2 ────────────────────────────────────────────────────────
                } else if (ligne.startsWith("## ")) {
                    Paragraph p = new Paragraph(ligne.substring(3), FONT_H2);
                    p.setSpacingBefore(14);
                    p.setSpacingAfter(6);
                    doc.add(p);

                // ── Tableau Markdown ──────────────────────────────────────────
                } else if (ligne.startsWith("| ")) {
                    List<String[]> rangees = new ArrayList<>();
                    while (i < lignes.length && lignes[i].startsWith("| ")) {
                        // Ignorer les lignes séparatrices :--- 
                        if (!lignes[i].contains(":---") && !lignes[i].matches("[| :-]+")) {
                            String[] cellules = lignes[i].split("\\|");
                            // cellules[0] est vide (avant le premier |)
                            List<String> vals = new ArrayList<>();
                            for (int k = 1; k < cellules.length; k++) {
                                vals.add(cellules[k].trim());
                            }
                            rangees.add(vals.toArray(new String[0]));
                        }
                        i++;
                    }
                    if (!rangees.isEmpty()) {
                        doc.add(creerTableau(rangees));
                        doc.add(new Paragraph(" "));
                    }
                    continue; // i déjà incrémenté dans la boucle interne

                // ── Bloc de code ──────────────────────────────────────────────
                } else if (ligne.startsWith("```")) {
                    StringBuilder code = new StringBuilder();
                    i++;
                    while (i < lignes.length && !lignes[i].startsWith("```")) {
                        code.append(lignes[i]).append("\n");
                        i++;
                    }
                    doc.add(creerBlocCode(code.toString()));

                // ── Blockquote ────────────────────────────────────────────────
                } else if (ligne.startsWith("> ")) {
                    Paragraph p = new Paragraph(ligne.substring(2), FONT_QUOTE);
                    p.setIndentationLeft(15);
                    p.setSpacingAfter(4);
                    doc.add(p);

                // ── Liste à puces ─────────────────────────────────────────────
                } else if (ligne.startsWith("- ") || ligne.startsWith("* ")) {
                    String texte = ligne.substring(2);
                    Paragraph p = new Paragraph("• " + nettoyerGras(texte), FONT_NORMAL);
                    p.setIndentationLeft(15);
                    doc.add(p);

                // ── Paragraphe normal ─────────────────────────────────────────
                } else if (!ligne.isBlank()) {
                    Paragraph p = new Paragraph(nettoyerGras(ligne), FONT_NORMAL);
                    p.setSpacingAfter(3);
                    doc.add(p);
                }

                i++;
            }

            System.out.println("PDF '" + cheminSortie + "' généré avec succès !");

        } catch (DocumentException | IOException e) {
            System.err.println("Erreur génération PDF : " + e.getMessage());
        }
    }

    // =========================================================================
    // Helpers privés
    // =========================================================================

    /**
     * Construit un {@link PdfPTable} à partir de rangées de cellules.
     * La première rangée est traitée comme en-tête (fond bleu, texte blanc).
     */
    private static PdfPTable creerTableau(List<String[]> rangees) throws DocumentException {
        int nbColonnes = rangees.get(0).length;
        PdfPTable table = new PdfPTable(nbColonnes);
        table.setWidthPercentage(100);
        table.setSpacingBefore(6);

        for (int r = 0; r < rangees.size(); r++) {
            String[] cellules = rangees.get(r);
            boolean estEntete = (r == 0);

            for (String val : cellules) {
                PdfPCell cell = new PdfPCell(new Phrase(val, estEntete ? FONT_ENTETE : FONT_NORMAL));
                cell.setPadding(5);
                cell.setBorderColor(new Color(221, 227, 240));
                if (estEntete) {
                    cell.setBackgroundColor(BLEU_ENTETE);
                } else if (r % 2 == 0) {
                    cell.setBackgroundColor(GRIS_PAIR);
                } else {
                    cell.setBackgroundColor(Color.WHITE);
                }
                table.addCell(cell);
            }
        }
        return table;
    }

    /**
     * Construit un bloc de code avec fond sombre et police Courier.
     */
    private static PdfPTable creerBlocCode(String contenu) throws DocumentException {
        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingBefore(4);
        table.setSpacingAfter(4);

        PdfPCell cell = new PdfPCell(new Phrase(contenu, FONT_CODE));
        cell.setBackgroundColor(CODE_BG);
        cell.setPadding(10);
        cell.setBorderColor(ROUGE_ACCENT);
        cell.setBorderWidthLeft(3);
        table.addCell(cell);
        return table;
    }

    /**
     * Supprime le formatage Markdown gras ({@code **texte**}) pour n'en
     * garder que le texte brut.
     */
    private static String nettoyerGras(String texte) {
        return texte.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
    }
}