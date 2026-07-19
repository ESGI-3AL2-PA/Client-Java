# TODO — Suivi d'avancement (Client-Java / admin-desktop)

## Légende

- `[x]` Terminé
- `[~]` Partiel / en cours
- `[ ]` À faire

---

## 🟦 Base (fondations)

- [x] **BDD H2 + `DatabaseManager`** — schéma SQL complet (`repository/DatabaseManager.java`)
- [x] **Modèles** (`Incident`, `Alert`, `Statistic`, `User`, `IncidentHistory`, `SyncLog`) avec Jackson
- [x] **`IncidentRepository`** — CRUD complet

## 🟩 Offline-first

- [x] Lecture/écriture systématique via H2, flag `synced` sur `Incident`

## 🟧 Synchronisation

- [x] **`SyncService`** — push/pull, scheduler, détection connexion (`ConnectivityChecker`)
- [x] **Résolution de conflits** — Last-Write-Wins + `SyncLogRepository`
- [ ] *(mineur)* Vérifier si `pushLocalUsers()` doit être ré-ajouté dans `SyncService`
  (retiré au commit `1578bc9 "fix"` — actuellement seul `pullRemoteUsers()` est appelé).
  À clarifier avec l'API : les users sont-ils censés être créés/modifiés côté client ?

## 🟪 UI JavaFX

- [x] **Dashboard** (`dashboard.fxml` + `DashboardController.java`, 306 lignes) — cartes stats,
  tableau incidents, alertes, barre de sync
- [x] **Paramètres** (`settings.fxml` + `SettingsController.java`, 393 lignes) — config API,
  test de connexion, thèmes, langue
- [x] **Écran Incidents** (tâche 8) — `controller/IncidentController.java` (609 lignes) et
  `resources/.../fxml/incidents.fxml` (96 lignes) **implémentés**.
    - `TableView<Incident>` avec toutes les colonnes du modèle, filtres (statut, catégorie),
      double-clic → dialog modale d'édition (statut, catégorie, description, assignation).
    - Bouton « + Nouvel incident » (dialog de création avec catégorie + description obligatoires).
    - Navigation in-app : remplace la scène principale, même header que le dashboard.
    - Bouton de navigation depuis `dashboard.fxml` (`btnIncidents`) câblé vers `MainApp.showIncidents()`.
- [x] **Écran Statistiques** (tâche 9) — `controller/StatisticsController.java` (129 lignes) et
  `resources/.../fxml/statistics.fxml` (83 lignes) **implémentés**.
    - 5 cartes métriques (utilisateurs, annonces, événements, votes, incidents) avec valeurs et tendances.
    - 2 PieCharts : incidents par statut et incidents par catégorie.
    - `StatisticRepository` (CRUD complet) consommé dans `initialize()`.
    - Fenêtre modale (900×650) ouverte depuis `HeaderController.onStatisticsClick()`.
- [x] **`ThemeManager`** (tâche 10) — `theme/ThemeManager.java` + `theme/Theme.java`
  (classe immuable) implémentés. 2 CSS built-in (`theme-light.css`,
  `theme-dark.css`) remplis avec un vocabulaire de classes
  CSS partagé (`.card`, `.header`, `.nav-button`, `.button-primary`…).
  `theme-template.css` fourni comme modèle commenté pour créer des thèmes perso.
  Les FXML `dashboard`, `header`, `settings`, `incidents`, `statistics` ont été refactorés
  (styles inline → `styleClass`). `MainApp` et `HeaderController` appellent
  `ThemeManager.applyTheme(scene)` au lieu de câbler `theme-light.css` en dur.
  Persistance via `java.util.prefs` (clé `theme.id`, défaut `light` = thème
  Clair au premier lancement).
  **Thèmes personnalisés** : l'utilisateur dépose des `.css` dans `./themes/`
  (auto-créé). La sélection dans la ComboBox ne s'applique pas automatiquement :
  l'utilisateur doit cliquer sur « Recharger » pour persister + appliquer le
  thème (et re-scanner le dossier). Le nom du fichier (sans `.css`) devient
  l'id et le libellé affiché. Noms réservés : `light`, `dark`. Voir
  `doc/themes.md`.

## 🟨 Plugins (système extensible)

- [x] **`PluginManager`** (tâche 11) — `plugin/PluginManager.java` (210 lignes) **implémenté**.
    - Chargement hybride : built-in via `ServiceLoader<Plugin>` sur le classpath + externes
      via `URLClassLoader` depuis le dossier `./plugins/` (JARs).
    - Cycle de vie complet : `init(AppContext)` → `loadAll()` → `executeAll(Object)` / `execute(String, Object)`
      → `shutdownAll()`. Idempotent et thread-safe (`synchronized`).
    - Gestion d'erreurs isolée par plugin : un plugin qui lève une exception ne casse pas les autres.
    - `META-INF/services/com.connectedneighbours.plugin.Plugin` contient `HelloPlugin`.
- [x] **`HelloPlugin`** — plugin de validation du cycle de vie `ServiceLoader` (41 lignes).
- [ ] **`ExportStatsPlugin`** (tâche 12) — export CSV/PDF des statistiques (fichier **vide**,
  seul plugin restant à implémenter).
- [x] **`SocialAnalysisPlugin`** (tâche 13) — `plugin/plugins/SocialAnalysisPlugin.java`
  (414 lignes) **implémenté**. Fenêtre JavaFX avec `TabPane` (onglets interactions/top
  contributeurs), `TableView` et `PieChart` alimentés depuis l'API (`/api/social/...`)
  via `ApiClient` + `JacksonConfig`. Enregistré dans
  `META-INF/services/com.connectedneighbours.plugin.Plugin`.
- [x] **`LocalCalendarPlugin`** (tâche 14) — `plugin/plugins/LocalCalendarPlugin.java`
  (212 lignes) **implémenté**. Fenêtre JavaFX avec `TableView` des événements du quartier
  + filtre `ComboBox`, données récupérées en asynchrone (`CompletableFuture`) depuis
  l'API (`/api/events`). Enregistré dans le fichier ServiceLoader.

> ⚠️ Plus que **`ExportStatsPlugin`** reste à implémenter (tâche 12). `PluginManager` est
> prêt à le charger dès qu'il sera renseigné dans
> `META-INF/services/com.connectedneighbours.plugin.Plugin`.
> ℹ️ **Note** : le fichier ServiceLoader contient actuellement `HelloPlugin` en double
> (lignes 1 et 4) — doublon inoffensif (`ServiceLoader` déduplique), à nettoyer.

## 🟥 Sécurité

- [x] **SSO / JWT** — `SsoAuthService`, `CallbackServer`, `JwtVerifier`, `AuthConfig`,
  `TokenUnavailableException` (flow complet navigateur → callback loopback → JWT)

## 🔄 Mises à jour & cycle de vie

- [ ] **`UpdateService`** (tâche 17) — `service/UpdateService.java` est **vide**.
    - Comparer version locale (`pom.xml` / constante) vs version serveur, proposer le download +
      remplacement du `.jar`.
- [ ] **Désinstallation depuis l'UI** (tâche 18) — aucun bouton/logique dans
  `SettingsController.java`. Prévoir suppression du répertoire d'install + données locales
  (BDD H2 + prefs `ApiConfig`/`AuthConfig`).

## 🌍 Internationalisation

- [ ] **`I18nManager`** (tâche 19) — `i18n/I18nManager.java` est **vide**.
    - Fichiers `messages_fr.properties`, `messages_en.properties`, `messages_es.properties`
      existent mais sont **vides** — à remplir en premier (clés utilisées dans les FXML/contrôleurs).
    - Prévoir une `ComboBox` dans `settings.fxml` pour changer de langue à chaud.

## ✅ Qualité (tests)

- [x] `ApiClientTest.java` (39 lignes)
- [x] `SyncServiceTest.java` (161 lignes)
- [ ] **`IncidentRepositoryTest.java`** — fichier **vide**. Tester CRUD + `findUnsynced`
  (H2 en mémoire, voir config de `DatabaseManager`).
- [ ] **`IncidentServiceTest.java`** — fichier **vide**.

### 🟦 `IncidentService`

- [x] **`IncidentService`** (125 lignes) — service métier formalisant la logique incidents. Centralise :
    - CRUD complet (getAllIncidents, getIncidentsByStatus, createIncident, updateIncident, deleteIncident)
    - Validation (catégorie et description obligatoires)
    - Gestion automatique de synced=false et updatedAt sur les modifications
    - Extraction des catégories distinctes pour le filtre ComboBox

## 📦 Packaging

- [X] **Fat JAR (Maven Shade)** (tâche 22) — config présente dans `pom.xml`, **jamais buildé/testé
  en conditions réelles**. À faire : lancer `mvn clean package` et vérifier que le
  `.jar` produit se lance bien avec `java -jar target/admin-desktop-1.0.0.jar` (JavaFX inclus,
  pas d'erreur de module manquant).

---

## Notes pour les IA qui reprennent ce projet

1. **Toujours vérifier l'état réel des fichiers avant de se fier à ce TODO** — il peut être
   désynchronisé si des changements ont été faits sans mise à jour. Une commande utile :
   ```powershell
   Get-ChildItem -Recurse -File -Path "src" -Include *.java,*.fxml,*.css,*.properties |
     ForEach-Object { "{0,6} {1}" -f (Get-Content -LiteralPath $_.FullName -Raw | Measure-Object -Line).Lines, $_.FullName }
   ```
   Un fichier à `0` ligne = pas encore implémenté.
2. **Conventions du projet** :
    - Repositories : SQL brut + `DatabaseUtil.executeQuery/executeUpdate` (voir
      `StatisticRepository.java` comme modèle simple et complet).
    - Config persistée : `java.util.prefs.Preferences` (voir `ApiConfig`, `AuthConfig`), pas de
      fichier `.properties`/`.ini` custom.
    - Pas de framework de logging (SLF4J/Logback retirés du `pom.xml`) — le code utilise
      `java.util.logging` ou `e.printStackTrace()` directement.
    - `AppContext` est le point d'injection central (auth + API + user courant) — les nouveaux
      contrôleurs doivent le recevoir en paramètre plutôt que d'instancier leurs propres services.
3. **Avant de commencer une tâche** : mettre à jour la case correspondante en `[~]`. **Après
   l'avoir terminée et vérifiée** (compilation OK, tests passés si applicable) : cocher `[x]`
   et ajouter une ligne courte dans `RESUME.md` (section "État d'avancement actuel") si la
   description globale de l'architecture doit changer.
4. **Build/test rapide** :
   ```powershell
   mvn -q compile          # compilation seule
   mvn -q test             # tests unitaires (JUnit5 + Mockito)
   mvn -q javafx:run       # lancer l'app GUI
   mvn -q clean package    # fat jar (tâche 22, à valider)
   ```
