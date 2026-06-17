# Gestionnaire et Analyseur d'Adresses Réseau

Ce projet est une application Java permettant de manipuler des adresses IP, de calculer automatiquement les informations de sous-réseau (adresse réseau, adresse de broadcast, nombre d'hôtes disponibles), puis d'exporter ces données sous forme de fichiers de données (**JSON**) et de rapports d'analyse (**Markdown**).

## Fonctionnalités

- **Calculs Réseau Avancés** : Détermination de l'adresse réseau, de l'adresse de broadcast et de la capacité d'hôtes à partir d'une IP et d'un masque (ou préfixe CIDR).
- **Manipulation Binaire** : Stockage optimisé des adresses sous forme d'entiers (`int`) et conversions fluides en chaînes de caractères (ex: `192.168.1.1`).
- **Export Multi-format** :
  - Génération automatique d'un fichier `adresses.json` pour l'interopérabilité.
  - Génération d'un rapport de synthèse `rapport_statistiques.md` incluant des indicateurs clés et des statistiques par type d'équipement.

---

## Exemple de Rapport Généré

L'application produit un tableau de bord au format Markdown structuré comme suit :

### Indicateurs Clés
- **Nombre total d'équipements enregistrés :** 5
- **Capacité totale d'adresses hôtes disponibles :** 16 842 998

### Répartition par Type d'Équipement
| Type d'appareil | Quantité | Pourcentage |
| :--- | :---: | :---: |
| Serveur | 2 | 40.0 % |
| Ordinateur | 2 | 40.0 % |
| Routeur | 1 | 20.0 % |

---

## Structure du Code

Le projet est articulé autour de deux classes principales situées dans le package `projet` :

1. **`AdresseReseau.java`** : Le modèle de données. Il contient la logique binaire pour les calculs réseau, les différents constructeurs (permettant d'associer un nom, un type ou un préfixe CIDR) ainsi que la méthode de sérialisation `toJson()`.
2. **`App.java`** : Le point d'entrée du programme. Il centralise la liste des équipements, utilise les *Java Streams* pour compiler les statistiques globales et écrit les fichiers de sortie.

---

## 💻 Installation et Utilisation

### Prérequis
- **Java 11** ou version supérieure installé.

### Compilation et Exécution

1. **Cloner le projet** ou récupérer les sources dans votre répertoire de travail.
2. **Compiler les fichiers Java** depuis la racine du projet :
   ```bash
   javac projet/AdresseReseau.java projet/App.java
