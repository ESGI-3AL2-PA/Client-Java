# Thèmes — `ThemeManager`

L'application supporte des **thèmes JavaFX** interchangeables à chaud, persistés
entre les lancements, avec la possibilité pour l'utilisateur de créer ses propres
thèmes.

## Sommaire

- [Thèmes fournis](#thèmes-fournis)
- [Thèmes personnalisés](#thèmes-personnalisés)
- [Changer de thème](#changer-de-thème)
- [Vocabulaire CSS](#vocabulaire-css)
- [Fichiers sources](#fichiers-sources)

---

## Thèmes fournis

Deux thèmes **built-in** sont embarqués dans le classpath :

| Thème  | Fichier CSS (classpath)             | Libellé affiché |
|--------|-------------------------------------|-----------------|
| Clair  | `.../css/theme-light.css`           | « Clair »       |
| Sombre | `.../css/theme-dark.css`            | « Sombre »      |

Le thème **Clair** est sélectionné par défaut au premier lancement. Ils
définissent un vocabulaire de classes CSS commun (`.card`, `.header`,
`.button-primary`…). Voir [Vocabulaire CSS](#vocabulaire-css).

---

## Thèmes personnalisés

L'utilisateur peut créer ses propres thèmes en déposant des fichiers `.css`
dans le dossier **`./themes/`** (relatif au répertoire de travail de l'app,
au même niveau que le dossier `./data/` de la BDD H2).

### Créer un thème

1. Le dossier `./themes/` est créé automatiquement au premier lancement.
2. Copiez le modèle de base :
   ```
   src/main/resources/com/connectedneighbours/css/theme-template.css
   ```
   …vers `./themes/mon-theme.css` (le nom est libre, à l'exception des noms
   réservés ci-dessous).
3. Modifiez les couleurs et propriétés dans le fichier copié. Toutes les
   classes disponibles sont listées et commentées dans `theme-template.css`.
4. Dans l'app : ouvrez **Paramètres → Apparence**, cliquez sur **Recharger**,
   puis sélectionnez votre thème dans la liste déroulante.

### Règles

- L'**identifiant** d'un thème perso = nom du fichier **sans l'extension `.css`**
  (ex: `mon-theme.css` → `mon-theme`). C'est aussi ce nom qui s'affiche dans la
  liste déroulante.
- **Noms réservés** (ignorés si un fichier `.css` les porte) :
  `light.css`, `dark.css` — ils collisionneraient avec les thèmes built-in.
- Seuls les fichiers d'extension `.css` (insensible à la casse) sont pris en
  compte ; les sous-dossiers sont ignorés.
- Le choix du thème est **persisté** dans les `Preferences` utilisateur Java
  (clé `theme.id`). Il survit aux redémarrages. Si un thème perso persisté
  n'existe plus au prochain lancement (fichier supprimé), l'app retombe sur le
  thème Clair.

### Démarrage avec un thème par défaut

Au démarrage (`MainApp.start()`), `ThemeManager.reloadCustomThemes()` est
appelé afin que les thèmes perso soient disponibles dès le premier écran.

---

## Changer de thème

- **Depuis l'UI** : `Paramètres → section « Apparence » → ComboBox « Thème »`.
  La sélection **ne s'applique pas automatiquement** : l'utilisateur doit
  cliquer sur **Recharger** pour persister + appliquer le thème choisi. Le
  bouton re-scanne aussi le dossier `./themes/` (récupère les nouveaux
  fichiers `.css` déposés manuellement).
- **Application visuelle** : le thème est appliqué à la scène courante dès le
  clic sur « Recharger », et à toutes les nouvelles scènes lors de la
  navigation suivante.

---

## Vocabulaire CSS

Tous les thèmes (built-in et perso) doivent définir ces classes pour que l'UI
soit correctement stylée. Les FXML les référencent via `styleClass="..."`.

### Structure / fond

| Classe              | Usage                              |
|---------------------|------------------------------------|
| `.app-bg` / `.root` | fond principal (BorderPane racine) |

### Header de navigation (`header.fxml`)

| Classe               | Usage                                    |
|----------------------|------------------------------------------|
| `.header`            | bandeau supérieur                        |
| `.header-title`      | titre « Connected Neighbours »           |
| `.accent-badge`      | badge « Admin »                          |
| `.nav-button`        | bouton de navigation (inactif)           |
| `.nav-button-active` | bouton de navigation de la page courante |
| `.nav-button-logout` | bouton « Déconnexion »                   |
| `.text-faint`        | label email utilisateur (gris léger)     |

### Titres & cartes

| Classe                 | Usage                                      |
|------------------------|--------------------------------------------|
| `.page-title`          | titre de page (22px bold)                  |
| `.card`                | panneau carte (fond, bord, ombre)          |
| `.card-title`          | titre d'une carte                          |
| `.card-label`          | label au-dessus d'une valeur               |
| `.card-value`          | grosse valeur d'une carte stat             |
| `.card-trend`          | petite ligne sous une valeur               |
| `.card-value-open`     | valeur carte « incidents ouverts » (rouge) |
| `.card-value-progress` | valeur carte « en cours » (orange)         |
| `.card-value-resolved` | valeur carte « résolus » (vert)            |
| `.card-value-unsynced` | valeur carte « non synchronisés » (violet) |

### Boutons

| Classe            | Usage                                 |
|-------------------|---------------------------------------|
| `.button-primary` | action principale (bleu)              |
| `.button-success` | enregistrer (vert)                    |
| `.button-danger`  | suppression / déconnexion (rouge)     |
| `.button-accent`  | export stats (violet)                 |
| `.button-ghost`   | bouton secondaire (transparent bordé) |
| `.link-button`    | lien textuel (« Voir tout »)          |

### Barres & conteneurs

| Classe                  | Usage                                           |
|-------------------------|-------------------------------------------------|
| `.status-bar`           | barre de statut sync (bas)                      |
| `.status-bar-label`     | label statut (12px)                             |
| `.status-bar-secondary` | label secondaire (11px, gris)                   |
| `.action-bar`           | barre d'actions d'une fenêtre modale (settings) |
| `.scroll-transparent`   | ScrollPane sans fond                            |
| `.table-transparent`    | TableView sans fond                             |

### Textes & champs

| Classe           | Usage                                       |
|------------------|---------------------------------------------|
| `.text-muted`    | texte secondaire (#444)                     |
| `.text-faint`    | texte discret (#aaa)                        |
| `.field-label`   | label de champ dans une GridPane (settings) |
| `.waiting-label` | écran d'attente login SSO                   |

### Note sur les couleurs métier

Les couleurs de **statut** (`OPEN` → rouge, `IN_PROGRESS` → orange,
`RESOLVED` → vert) sont appliquées **en Java** sur les cellules de tableau
via `setStyle(...)` dans les cell factories (`DashboardController`,
`IncidentController`). Elles ne sont **pas** surchargeables par CSS — c'est
volontaire pour préserver le code couleur métier quel que soit le thème.
Les classes `.card-value-open` etc. ci-dessus ne couvrent que les cartes
stat du dashboard.

---

## Fichiers sources

### Java

| Fichier                                           | Rôle                                                               |
|---------------------------------------------------|--------------------------------------------------------------------|
| `com/connectedneighbours/theme/Theme.java`        | Classe immuable : id, displayName, cssUrl, builtin                 |
| `com/connectedneighbours/theme/ThemeManager.java` | Persistance (`Preferences`), scan `./themes/`, `applyTheme(Scene)` |

### CSS (classpath : `src/main/resources/com/connectedneighbours/css/`)

| Fichier              | Rôle                                                |
|----------------------|-----------------------------------------------------|
| `theme-light.css`    | thème clair (référence, par défaut au 1er lancement)|
| `theme-dark.css`     | thème sombre                                        |
| `theme-template.css` | **modèle** commenté pour créer un thème perso       |

### Runtime

| Chemin      | Rôle                                 |
|-------------|--------------------------------------|
| `./themes/` | dossier des thèmes perso (auto-créé) |

### Intégration

- `MainApp.start()` → `ThemeManager.reloadCustomThemes()` au démarrage, puis
  `ThemeManager.applyTheme(scene)` à chaque création de scène
  (`showWaiting`, `showDashboard`, `showIncidents`).
- `HeaderController.onSettingsClick()` → `ThemeManager.applyTheme(scene)` pour
  la fenêtre modale Paramètres.
- `SettingsController` → ComboBox `themeCombo` + bouton `onReloadThemesClick`.
- `HeaderController.setActivePage()` → manipule `getStyleClass()` au lieu de
  `setStyle()` inline (permet au thème de styliser les boutons nav).

### Persistance

- `Preferences.userNodeForPackage(ThemeManager.class)`, clé `theme.id`.
- Défaut : `light`. Fallback si id inconnu : `light`.
