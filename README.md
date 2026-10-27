![Frame 21799](https://github.com/M-U-C-K-A/WorldPainter/assets/96072629/41ebb073-3513-466a-8a15-3ab3770ef1f5)
# WorldPainter — édition modernisée
[![Made by](https://img.shields.io/badge/Made%20by-Captain--Chaos-blueviolet)](https://github.com/Captain-Chaos)
[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)

WorldPainter est un générateur de cartes interactif pour Minecraft : on « peint » des paysages avec des outils semblables à ceux d'un logiciel de dessin (sculpter le relief, peindre les matériaux, les arbres, la neige, etc.).

Ce fork reprend la dernière version du projet d'origine ([Captain-Chaos/WorldPainter](https://github.com/Captain-Chaos/WorldPainter)) et la modernise.

## Nouveautés de ce fork

### À jour avec Minecraft
- Basé sur WorldPainter 2.27 : export pour **Minecraft 1.20.5 à 1.21.11 et 26.1 et suivantes**. Minecraft 26.2 et 26.3 convertissent automatiquement à l'ouverture les cartes exportées au format 26.1.
- Le dépôt d'origine est configuré comme remote `upstream` pour récupérer facilement ses futures mises à jour.

### Génération procédurale — menu **Generate › Procedural terrain…** (⇧⌘G)
- Une pile de **couches de bruit** à combiner : relief doux (fractal), crêtes montagneuses, collines arrondies, cellules (volcans, cratères), dunes.
- Pour chaque couche : taille des reliefs, hauteur, niveau de détail, rugosité, netteté des sommets, déformation (warp), couverture (pour créer des chaînes ou des îles séparées), direction (dunes).
- Terrasses, niveau de la mer, et **règles de surface** (type de terrain selon l'altitude et la pente : neige en altitude, roche sur les pentes raides, plages au bord de l'eau…).
- 8 préréglages : Minecraft-like, Plaines, Collines, Montagnes alpines, Archipel, Canyons de mesa, Dunes, Îles volcaniques.
- **Aperçu en relief en temps réel**, graine aléatoire ou fixe.
- Application à toute la carte ou **seulement à la sélection, avec des bords adoucis**, en mode Remplacer, Ajouter du relief, Monter seulement ou Creuser seulement.
- Calcul en parallèle sur tous les cœurs du processeur, sans couture entre les tuiles : utilisable sur de très grandes cartes.

### Fondu des bords — menu **Generate › Blend map edges…**
Fini les falaises brutes au bord d'une carte de 200 × 200 : le terrain rejoint progressivement le paysage qui l'entoure.
- **Zone de transition** réglable (16 à 4096 blocs), placée à l'extérieur de la carte (qui est alors agrandie), à l'intérieur (le bord existant est adouci) ou à cheval sur le bord.
- **Cible** : un terrain procédural (par défaut *Minecraft-like*, proche du terrain que Minecraft génère au-delà de la carte) ou un niveau plat.
- Forme de la courbe (linéaire, en S, douce côté carte ou côté cible) et **irrégularité** pour éviter une transition trop géométrique.
- Le relief du bord est flouté progressivement avec la distance : pas de stries, pas de cassure aux coins.
- Minecraft termine ensuite lui-même le raccord sur les derniers blocs grâce à son système de *chunk blending*.

![Avant / après le fondu des bords](docs/images/edge-blending.png)

### Interface plus moderne
- Thèmes **FlatLaf** clair et sombre, avec un mode **automatique qui suit le réglage du système** (par défaut), variantes natives macOS, couleur d'accent, icônes claires en mode sombre. Les anciens thèmes restent disponibles dans les préférences.
- **Nouveau système de panneaux** (remplace JIDE, une bibliothèque commerciale qui empêchait de compiler le projet librement) : panneaux à gauche et à droite, regroupés en onglets compacts, redimensionnables, détachables dans une fenêtre, masquables et ré-affichables via **View › Panels**, disposition mémorisée par carte.

### Grands terrains et toutes les plateformes
- Passage à **Java 21**.
- Application **native avec Java intégré** (rien à installer) : `.app`/`.dmg` pour macOS (Apple Silicon et Intel), Windows, `.deb`/`.rpm` pour Linux.
- L'application empaquetée peut utiliser **jusqu'à 75 % de la mémoire** de la machine (contre 25 % par défaut pour Java), ce qui permet de charger des cartes environ trois fois plus grandes.
- Intégration continue GitHub Actions : tests et paquets pour les quatre plateformes à chaque push.

## Compiler et lancer

Il suffit d'un JDK 21 et de Maven. Toutes les dépendances se téléchargent automatiquement. Voir [BUILDING.md](BUILDING.md) pour le détail.

```bash
cd WorldPainter
mvn -DskipTests install
mvn -pl WPGUI exec:exec
```

Pour créer l'application macOS (dans `dist/`) :

```bash
packaging/package.sh        # dist/WorldPainter.app
packaging/package.sh dmg    # image disque à distribuer
```

## Distribution

WorldPainter est un logiciel libre sous licence GPL version 3. Si vous le distribuez, tel quel ou modifié, vous devez aussi distribuer le code source (avec vos modifications) sous GPL version 3 (ou ultérieure). Voir le fichier [LICENSE](LICENSE).

## Contributeurs

[![Contributors](https://contrib.rocks/image?repo=Captain-Chaos/WorldPainter)](https://github.com/Captain-Chaos/WorldPainter/graphs/contributors)
