# Récapitulatif des tâches — Projet Java Desktop (Connected Neighbours)

## Contexte du projet

Application desktop **JavaFX** faisant partie d'un projet plus large "Connected
Neighbours" (app web/mobile de quartier avec API Node.js). Cette partie Java est l'**outil d'administration** utilisé
par les modérateurs/admins.

- **Builder** : Maven
- **JDK** : Java 21
- **UI** : JavaFX (FXML + Controllers, pattern MVC)
- **BDD locale** : H2 (embarqué, mode offline-first)
- **HTTP client** : OkHttp (appels vers l'API Node.js)
- **JSON** : Jackson (+ jackson-datatype-jsr310 pour les dates)
- **Auth** : auth0 java-jwt + jwks-rsa (vérification JWT côté client, SSO via navigateur) — **démarrage offline-first** : le login SSO est skipé si un dernier user est mémorisé (`SessionConfig`) et que la base H2 locale contient des données
- **Tests** : JUnit 5 + Mockito
- **Packaging final** : fat `.jar` via Maven Shade Plugin

---

## Architecture des packages

```
com.connectedneighbours/
├── Launcher.java                   # Point d'entrée
├── MainApp.java                    # Point d'entrée JavaFX (skip login offline si dernier user mémorisé + données H2, sinon login SSO → dashboard)
├── AppContext.java                 # Contexte global (SsoAuthService, ApiClient, User courant ; logout efface le dernier user mémorisé)
├── controller/                     # Contrôleurs FXML (MVC)
│   ├── BaseController.java         # Classe parente (sync bar, header, updateSyncUI, re-login)
│   ├── Page.java                   # Enum des pages de navigation
│   ├── DashboardController.java    # Dashboard principal (cartes stats, tableau, alertes, sync)
│   ├── IncidentController.java     # Écran incidents (TableView, filtres, CRUD, historique)
│   ├── StatisticsController.java   # Écran statistiques (PieChart, cartes métriques)
│   └── SettingsController.java     # Paramètres API (schéma, hôte, port, test connexion, thèmes)
├── model/                      # Entités
│   ├── Incident.java               # Incident (statut, priorité, localisation, flag synced)
│   ├── IncidentHistory.java        # Historique des modifications d'un incident
│   ├── Alert.java                  # Alerte quartier
│   ├── District.java               # District (id, name)
│   ├── Statistic.java              # Statistiques agrégées
│   ├── SyncLog.java                # Log de synchronisation
│   └── User.java                   # Utilisateur (profil complet avec TOTP, balance, etc.)
├── service/                    # Logique métier
│   ├── SyncService.java            # Push/pull auto, scheduler, résolution de conflits, AUTH_REQUIRED
│   ├── IncidentService.java        # Service métier incidents (CRUD, validation, synced)
│   ├── UpdateService.java          # Auto-update (ÉCHEAFAUDÉ — fichier vide)
│   ├── ConnectivityChecker.java    # Interface de détection réseau
│   ├── SyncStatus.java             # Enum des états de sync (OFFLINE, SYNCING, SUCCESS, ERROR, AUTH_REQUIRED)
│   ├── SyncStatusListener.java     # Interface listener pour l'UI
│   └── UiExecutor.java             # Interface pour exécuter sur le thread JavaFX
├── repository/                 # Accès BDD locale H2 + HTTP
│   ├── ApiClient.java              # Client HTTP OkHttp (GET/POST/PUT/DELETE avec JWT)
│   ├── ApiException.java           # Exception HTTP (statusCode, isNotFound)
│   ├── DatabaseManager.java        # Schéma SQL H2, initialisation connexion
│   ├── DistrictRepository.java     # CRUD districts
│   ├── IncidentRepository.java     # CRUD complet incidents
│   ├── AlertRepository.java        # Requêtes alertes (findRecent)
│   ├── StatisticRepository.java    # CRUD statistiques
│   ├── SyncLogRepository.java      # CRUD logs de synchronisation
│   └── UserRepository.java         # CRUD utilisateurs
├── plugin/                     # Système de plugins (ServiceLoader)
│   ├── Plugin.java                  # Interface (init, execute, shutdown)
│   ├── PluginManager.java          # Chargement hybride (built-in ServiceLoader + JARs externes)
│   └── plugins/
│       ├── HelloPlugin.java            # Plugin de validation ServiceLoader
│       ├── ExportStatsPlugin.java      # Export CSV/PDF (ÉCHEAFAUDÉ — fichier vide)
│       ├── SocialAnalysisPlugin.java   # Analyse interactions Neo4j (IMPLÉMENTÉ — UI JavaFX + API, 414 lignes)
│       └── LocalCalendarPlugin.java    # Calendrier quartier (IMPLÉMENTÉ — UI JavaFX + API, 212 lignes)
├── theme/                      # Gestion des thèmes CSS
│   ├── Theme.java                 # Classe immuable (id, displayName, cssUrl, builtin)
│   └── ThemeManager.java          # Persistance (prefs), scan ./themes/, applyTheme(Scene)
├── i18n/                       # Internationalisation
│   └── I18nManager.java           # Gestion ResourceBundle (ÉCHEAFAUDÉ — fichier vide)
├── config/                     # Configuration
│   ├── JacksonConfig.java         # ObjectMapper Jackson (dates ISO-8601)
│   ├── ApiConfig.java             # Config API persistée via java.util.prefs (NOUVEAU)
│   ├── AuthConfig.java            # Config auth-service URL (NOUVEAU)
│   └── SessionConfig.java         # Dernier user connecté persisté via java.util.prefs (JSON) pour démarrage offline-first (NOUVEAU)
├── auth/                       # SSO / JWT
│   ├── SsoAuthService.java        # Flow SSO complet : navigateur → callback loopback → JWT (access token en mémoire seulement, non persisté)
│   ├── CallbackServer.java        # Mini serveur HTTP loopback pour réception du token
│   ├── JwtVerifier.java           # Vérification JWT via JWKS RSA
│   └── exception/
│       └── TokenUnavailableException.java  # Exception token expiré/absent
└── util/                       # Utilitaires
    ├── DatabaseUtil.java           # Utilitaires BDD (executeQuery, executeUpdate, countRows)
    └── ViewLoader.java             # Chargement de vues FXML (ÉCHEAFAUDÉ — fichier vide)
```

**Ressources** :

```
src/main/resources/
├── com/connectedneighbours/
│   ├── fxml/
│   │   ├── dashboard.fxml        # Dashboard principal (151 lignes)
│   │   ├── header.fxml           # Header de navigation (41 lignes)
│   │   ├── incidents.fxml        # Écran incidents (96 lignes)
│   │   ├── settings.fxml         # Paramètres API (125 lignes)
│   │   └── statistics.fxml       # Écran statistiques (83 lignes)
│   └── css/
│       ├── theme-light.css       # Thème clair (450 lignes, par défaut)
│       ├── theme-dark.css        # Thème sombre (471 lignes)
│       └── theme-template.css    # Modèle commenté pour thèmes perso (199 lignes)
├── i18n/
│   ├── messages_fr.properties    # FR (VIDE)
│   ├── messages_en.properties    # EN (VIDE)
│   └── messages_es.properties    # ES (VIDE)
├── META-INF/
│   ├── MANIFEST.MF
│   └── services/com.connectedneighbours.plugin.Plugin  # Contient HelloPlugin
└── logback.xml                   # Config logging (VIDE)
```

---

## Liste complète des tâches

### 🟦 Base (fondations)

1. **BDD H2 + DatabaseManager** — Créer le schéma SQL (incidents, alerts, statistics, sync_log), initialiser la
   connexion H2 embarquée au démarrage.
2. **Modèles Java (Incident, Alert, Statistic, User)** — Classes POJO avec annotations Jackson pour la sérialisation
   JSON, alignées avec le schéma H2.
3. **IncidentRepository (CRUD local)** — Insert, findAll, findById, findUnsynced, update, delete dans H2. Utilisé en
   offline comme en online.

### 🟩 Offline-first

4. **Fonctionnement hors-ligne complet** — Toutes les lectures et écritures passent d'abord par H2. Un flag `synced`
   marque ce qui reste à synchroniser.

### 🟧 Synchronisation

5. **SyncService — sync auto avec l'API** — `ScheduledExecutorService` qui tourne à intervalle régulier, détecte une
   connexion Internet, et envoie/reçoit les données via HTTP (OkHttp).
6. **Résolution de conflits** — Stratégie "Last-Write-Wins" basée sur `updated_at` (comparaison local vs serveur), avec
   log des conflits dans `sync_log`.

### 🟪 UI JavaFX

7. **Dashboard principal (FXML)** — Écran d'accueil avec cartes de stats (incidents ouverts/en
   cours/résolus/non-synchronisés), tableau des incidents récents, panneau d'alertes, barre de statut de
   synchronisation.
8. **Écran incidents (liste + détail)** — TableView JavaFX avec filtres (statut, date). Clic/double-clic sur une ligne
   ouvre le détail.
9. **Écran statistiques (graphiques)** — JavaFX Charts : BarChart pour participation, LineChart pour tendances, PieChart
   pour répartition.
10. **ThemeManager — thèmes CSS** — Charger dynamiquement un fichier `.css` JavaFX (light, dark, custom). Persister le
    choix dans un fichier de config.

### 🟨 Plugins (système extensible)

11. **Interface Plugin + PluginManager** — Interface `Plugin` (init, execute, shutdown). `PluginManager` utilise
    `java.util.ServiceLoader` pour charger les plugins.
12. **Plugin ExportStats** — Exporte les statistiques en CSV ou PDF.
13. **Plugin SocialAnalysis** — Analyse les interactions (qui a aidé qui) depuis les données reçues via l'API (source
    Neo4j côté backend).
14. **Plugin LocalCalendar** — Affiche les événements du quartier dans un calendrier local JavaFX.

### 🟥 Sécurité

15. **SSO — authentification JWT partagé** — Récupérer et stocker un token JWT depuis l'API Node.js. Le joindre à chaque
    requête HTTP (`Authorization: Bearer`).
16. **MFA pour actions sensibles** — Avant certaines actions (suppression, export), demander un code TOTP envoyé via
    l'API.

### 🔄 Mises à jour & cycle de vie

17. **UpdateService — auto-update** — Au démarrage, comparer la version locale avec le serveur central. Si nouvelle
    version dispo, proposer le téléchargement et le remplacement du `.jar`.
18. **Désinstallation depuis l'UI** — Bouton dans les paramètres qui supprime le répertoire d'installation et les
    données locales.

### 🌍 Internationalisation

19. **i18n (FR/EN/ES)** — `ResourceBundle` + fichiers `messages_fr.properties`, `messages_en.properties`,
    `messages_es.properties`. Combobox dans les paramètres pour changer de langue à chaud.

### ✅ Qualité

21. **Tests unitaires (JUnit 5 + Mockito)** — Tester `IncidentService`, `SyncService`, `DatabaseManager` avec JUnit 5.
    Mocker les appels HTTP avec Mockito.

### 📦 Packaging

22. **Fat JAR avec Maven Shade** — `mvn clean package` produit un `.jar` unique avec toutes les dépendances (JavaFX
    inclus).

---

## État d'avancement actuel (à date de ce récap)

### ✅ Implémenté (code fonctionnel)

- ✅ **Tâche 1** — `DatabaseManager.java` (H2, schéma complet)
- ✅ **Tâche 2** — Modèles `Incident`, `Alert`, `District`, `Statistic`, `User`, `IncidentHistory`, `SyncLog` +
  `JacksonConfig`
- ✅ **Tâche 3** — `IncidentRepository` (CRUD complet) + `AlertRepository`, `StatisticRepository`, `SyncLogRepository`,
  `UserRepository`, `DistrictRepository`
- ✅ **Tâche 4** — `IncidentService.java` (125 lignes) — CRUD, validation, synced flag
- ✅ **Tâche 5** — `SyncService` (push/pull, scheduler, détection connexion) + `ConnectivityChecker`, `SyncStatus`,
  `SyncStatusListener`, `UiExecutor` (interfaces/enum support)
- ✅ **Tâche 6** — Résolution de conflits (Last-Write-Wins dans `SyncService`) + `SyncLogRepository` (persistance des
  logs de conflits)
- ✅ **Tâche 7** — `dashboard.fxml` (151 lignes) + `DashboardController.java` (cartes stats, tableau incidents, alertes,
  barre de sync)
- ✅ **Tâche 8** — `incidents.fxml` (96 lignes) + `IncidentController.java` (609 lignes) — TableView 10 colonnes,
  filtres, double-clic édition, création, historique
- ✅ **Tâche 9** — `statistics.fxml` (83 lignes) + `StatisticsController.java` (129 lignes) — 5 cartes métriques
  (utilisateurs, annonces, événements, votes, incidents), 2 PieCharts (incidents par statut et par catégorie), fenêtre
  modale ouverte depuis le header
- ✅ **Tâche 10** — `ThemeManager` + `Theme.java` + 3 CSS (light/dark/template). **Tous les FXML** (dashboard, header,
  incidents, settings, statistics) refactorés : styles inline → `styleClass`. `MainApp`/`HeaderController` utilisent
  `ThemeManager.applyTheme(scene)`. Thèmes personnalisés via `./themes/`. Persistance `java.util.prefs` (clé
  `theme.id`). Voir `doc/themes.md`.
- ✅ **Tâche 11** — `PluginManager.java` (210 lignes) — chargement hybride built-in (`ServiceLoader`) + externe (JARs
  `./plugins/` via `URLClassLoader`), cycle de vie complet (init/execute/shutdown), erreurs isolées par plugin,
  rechargement à chaud des externes
- ✅ **Tâche 13** — `SocialAnalysisPlugin.java` (414 lignes) — UI JavaFX (`TabPane` avec onglets
  interactions/top contributeurs, `TableView`, `PieChart`) alimentée depuis l'API (`/api/social/...`) via
  `ApiClient` + `JacksonConfig`. Enregistré dans `META-INF/services/com.connectedneighbours.plugin.Plugin`.
- ✅ **Tâche 14** — `LocalCalendarPlugin.java` (212 lignes) — UI JavaFX (`TableView` des événements + `ComboBox`
  de filtre), données récupérées en asynchrone (`CompletableFuture`) depuis l'API (`/api/events`). Enregistré dans
  le fichier ServiceLoader.
- ✅ **Tâche 15** — SSO complet : `SsoAuthService.java` (flow navigateur → callback loopback → JWT),
  `CallbackServer.java` (mini serveur HTTP sur port dynamique), `JwtVerifier.java` (vérification via JWKS RSA),
  `AuthConfig.java`, `TokenUnavailableException.java`
- ✅ `ApiClient.java` (OkHttp, GET/POST/PUT/DELETE avec JWT)
- ✅ `ApiException.java` (exception HTTP avec statusCode)
- ✅ `BaseController.java` (193 lignes) — classe parente mutualisant la barre de sync, le header, le re-login auto
- ✅ `Page.java` — enum DASHBOARD/INCIDENTS/USERS/STATISTICS/SETTINGS
- ✅ `HeaderController.java` (215 lignes) — navigation toutes pages, ouverture modale statistiques/paramètres

### 🟡 Échafaudés (fichiers créés mais vides — structure prête, logique à écrire)

- 🟡 **Tâche 12** — `ExportStatsPlugin.java` (vide — **seul plugin restant à implémenter**)
- 🟡 **Tâche 17** — `UpdateService.java` (vide)
- 🟡 **Tâche 19** — `I18nManager.java` + 3 fichiers properties (`messages_fr/en/es.properties`) — tous vides
- 🟡 **Tâche 21** — `IncidentRepositoryTest.java` + `IncidentServiceTest.java` (vides) — **note** :
  `ApiClientTest.java` (39 lignes) et `SyncServiceTest.java` (161 lignes) sont eux implémentés

### ⬜ Pas encore commencé

- ⬜ **Tâche 16** — MFA pour actions sensibles (aucun code TOTP côté client ; le MFA est géré côté serveur lors du login
  navigateur, mais pas de vérification TOTP locale pour les actions sensibles dans l'app)
- ⬜ **Tâche 18** — Désinstallation depuis l'UI (aucun bouton/logique dans `SettingsController`)
- ⬜ **Tâche 22** — Fat JAR packaging (config Maven Shade à jour dans le `pom.xml`, mais non testé en build réel)

### Nouveautés transversales (hors tâches numérotées)

- **`AppContext.java`** (64 lignes) — Contexte global partagé : instancie `SsoAuthService` + `ApiClient` (avec
  fournisseur de token), gère l'utilisateur courant et la déconnexion. Injecté dans les contrôleurs via `MainApp`.
- **`BaseController.java`** (193 lignes) — Classe parente des contrôleurs : barre de sync mutualisée
  (`updateSyncUI`, `onSyncNowClick`, `triggerRelogin`), header de navigation, hook `onSyncSuccess()` pour recharger
  les données, helper `showError()`.
- **`Page.java`** (13 lignes) — Enum `DASHBOARD` / `INCIDENTS` / `USERS` / `STATISTICS` / `SETTINGS` pour la
  navigation et le style actif du header.
- **`HeaderController.java`** (215 lignes) — Navigation entre écrans, ouverture des fenêtres modales Statistiques et
  Paramètres avec `ThemeManager.applyTheme(scene)`.
- **`IncidentController.java`** + `incidents.fxml` (609 + 96 lignes) — Écran complet : TableView 10 colonnes, filtres
  statut/catégorie, double-clic édition modale, création, historique des modifications.
- **`StatisticsController.java`** + `statistics.fxml` (129 + 83 lignes) — Fenêtre modale : 5 cartes métriques
  (utilisateurs, annonces, événements, votes, incidents) avec valeurs et tendances, 2 PieCharts (incidents par statut
  et par catégorie).
- **`SettingsController.java`** + `settings.fxml` (393 + 125 lignes) — Écran de paramètres API : configuration
  scheme/hôte/port (persisté via `java.util.prefs`), aperçu URL de base, test de connexion par socket, support IPv6,
  sélection de thème.
- **`ApiConfig.java`** (159 lignes) — Configuration API persistée dans les `Preferences` Java (scheme, host, port, URL
  de base).
- **`AuthConfig.java`** (108 lignes) — Configuration de l'URL de l'auth-service (port 3001 par défaut), persistée dans
  les `Preferences`.
- **`MainApp.java`** (236 lignes) — Intègre le flow SSO (login via navigateur avant d'accéder au dashboard), utilise
  `AppContext`. **Skip du login au démarrage** si un dernier user est mémorisé (`SessionConfig`) et que la base H2
  contient des données (helper `hasLocalData()`). Initialise `PluginManager` au démarrage.
- **`UserRepository.java`** (136 lignes) — CRUD complet pour les utilisateurs en BDD locale.
- **`District.java`** + **`DistrictRepository.java`** (42 + 73 lignes) — Modèle et CRUD districts.
- **`ApiException.java`** (30 lignes) — Exception HTTP avec `statusCode` et helper `isNotFound()`.
- **`PluginManager.java`** (210 lignes) — Chargement hybride built-in (`ServiceLoader`) + externe (JARs `./plugins/`).
  Cycle de vie complet (init/loadAll/executeAll/shutdownAll), rechargement à chaud des plugins externes.
- **`HelloPlugin.java`** (41 lignes) — Plugin de validation du cycle de vie `ServiceLoader`.
- **`SocialAnalysisPlugin.java`** (414 lignes) — UI JavaFX (`TabPane` + `TableView` + `PieChart`) consommant
  l'API (`/api/social/...`) via `ApiClient` + `JacksonConfig`. Enregistré dans le fichier ServiceLoader.
- **`LocalCalendarPlugin.java`** (212 lignes) — UI JavaFX (`TableView` + `ComboBox` de filtre) consommant
  l'API (`/api/events`) en asynchrone (`CompletableFuture`). Enregistré dans le fichier ServiceLoader.
- **`statistics.fxml`** — Refactoré pour utiliser les `styleClass` du vocabulaire CSS (`.card`, `.card-label`,
  `.card-value`, `.card-trend`, `.header`, `.header-title`, `.button-ghost`, `.scroll-transparent`, `.app-bg`),
  cohérent avec les autres FXML.
- **`DatabaseUtil.java`** (90 lignes) — Utilitaires pour les opérations BDD (executeQuery, executeUpdate, countRows).
- **`ViewLoader.java`** — Utilitaire de chargement FXML (échafaudé, vide).
- **`logback.xml`** — Fichier de config logging (échafaudé, vide ; les dépendances SLF4J/Logback ont été retirées du
  `pom.xml`).

### Fonctionnement — Démarrage offline-first (skip du login SSO)

Application du principe "offline-only" au démarrage de l'app Java GUI : l'écran de connexion SSO (qui ouvre le
navigateur) est désormais **skipé** quand c'est possible, pour atterrir directement sur le dashboard.

- **`SessionConfig.java`** (~85 lignes) — Persistance du dernier utilisateur connecté via
  `java.util.prefs.Preferences` (pattern identique à `ApiConfig`/`AuthConfig`). Le `User` est sérialisé en JSON via
  `JacksonConfig` (le module jsr310 gère `LocalDateTime`). Clés : `session.lastUser` (JSON) et `session.lastLoginAt`
  (ISO-8601). Méthodes : `saveLastUser(User)`, `loadLastUser() → Optional<User>`, `clearLastUser()`.
  **Aucun access token n'est persisté** (conformément au commentaire de `SsoAuthService` : le refresh token reste dans
  le navigateur via cookie HttpOnly).
- **`MainApp.start()`** — Avant le flow SSO navigateur, tente `SessionConfig.loadLastUser()` + vérifie qu'au moins une
  table H2 (`INCIDENTS`, `ALERTS`, `USERS`, `STATISTICS`, `SYNC_LOG`) contient ≥ 1 ligne (helper `hasLocalData()`,
  via `DatabaseUtil.countRows`). Si les deux conditions sont remplies → `appContext.setCurrentUser(restoredUser);
  showDashboard(); return;` (skip du SSO). Sinon, flow navigateur inchangé. Après chaque login réussi (`start` et
  `backToLogin`), `SessionConfig.saveLastUser(user)` est appelé.
- **`AppContext.logout()`** — Appelle `SessionConfig.clearLastUser()` avant de vider l'état en mémoire : le bouton
  "Déconnexion" efface donc le dernier user mémorisé → le prochain démarrage exigera un re-login.
- **`SyncStatus.java`** — État `AUTH_REQUIRED` (pour distinguer "token absent/expiré" de "erreur réseau générique
  `ERROR`").
- **`SyncService.syncCycle()`** — `catch (TokenUnavailableException)` avant `catch (Exception)` : émet
  `SyncStatus.AUTH_REQUIRED` au lieu de `ERROR` quand l'access token manque.
- **`BaseController.updateSyncUI()`** — Case `AUTH_REQUIRED` : label "Reconnexion requise", dot orange, et déclenche
  `MainApp.backToLogin()` via `stage.getUserData()` (même pattern que `onLogoutClick`, sans boîte de confirmation).
  Guard `reloginRequested` anti-boucle partagé entre tous les contrôleurs.

**Comportement final au lancement (GUI) :**
1. **1er lancement** (pas de user mémorisé OU H2 vide) → login SSO navigateur comme avant, puis mémorisation du user.
2. **Lancements suivants** (user mémorisé + données locales) → dashboard direct, hors-ligne, sans ouvrir le navigateur.
3. **Bouton "Déconnexion"** → efface le user mémorisé → prochain démarrage exigera un re-login.
4. **Sync tournée en ligne sans token** (session restaurée) → `AUTH_REQUIRED` → re-login navigateur automatique
   (refresh silencieux via cookie navigateur si ≤ 7j).

---

## Stack technique — récapitulatif des dépendances Maven

> **Source de vérité : `pom.xml`** (167 lignes). Les versions ci-dessous reflètent exactement le `pom.xml` du projet.
> GroupId : `com.connectedneighbours`, artifactId : `admin-desktop`, version : `1.0.0`, packaging : `jar`, Java 21.

### Coordonnées du projet

```xml
<groupId>com.connectedneighbours</groupId>
<artifactId>admin-desktop</artifactId>
<version>1.0.0</version>
<packaging>jar</packaging>
<!-- maven.compiler.source / target = 21, encodage UTF-8 -->
```

### Dépendances (scope `compile` sauf indication)

| Groupe | ArtifactId | Version | Rôle |
|---|---|---|---|
| `org.openjfx` | `javafx-controls` | 21.0.11-ea+4 | UI toolkit (boutons, tables, layout…) |
| `org.openjfx` | `javafx-fxml` | 21.0.11-ea+4 | Chargement des vues FXML |
| `org.openjfx` | `javafx-web` | 21.0.11-ea+4 | Disponible (non utilisé dans le flow SSO actuel — le navigateur OS est utilisé) |
| `org.openjfx` | `javafx-swing` | 21.0.11-ea+4 | Disponible (interop Swing, non utilisé) |
| `com.h2database` | `h2` | 2.4.240 | BDD embarquée offline-first (`jdbc:h2:./data/admin_db`) |
| `com.squareup.okhttp3` | `okhttp` | 4.12.0 | Client HTTP (appels vers l'API Node.js, SSO userinfo) |
| `com.auth0` | `java-jwt` | 4.5.2 | Décodage/vérification JWT |
| `com.auth0` | `jwks-rsa` | 0.22.1 | Fetch des clés JWKS RSA pour vérifier la signature JWT |
| `com.fasterxml.jackson.core` | `jackson-databind` | 2.22.0 | (Dé)sérialisation JSON (HTTP + persistance du dernier user dans `SessionConfig`) |
| `com.fasterxml.jackson.datatype` | `jackson-datatype-jsr310` | 2.22.0 | Module Jackson pour `java.time` (`LocalDateTime` sur `User`, `Incident`…) |
| `org.junit.jupiter` | `junit-jupiter-api` | 5.14.4 | API de tests (scope `test`) |
| `org.junit.jupiter` | `junit-jupiter-engine` | 5.14.4 | Runner JUnit 5 (scope `test`) |
| `org.mockito` | `mockito-core` | 5.23.0 | Mocks pour tests (`SyncServiceTest`, `ApiClientTest`) (scope `test`) |

> **Logs** : SLF4J/Logback ont été **retirés** du `pom.xml`. Le code utilise `java.util.logging` ou
> `e.printStackTrace()` directement. Le fichier `src/main/resources/logback.xml` existe mais est vide (scaffolding).

### Plugins Maven (`<build><plugins>`)

| GroupId | ArtifactId | Version | Rôle |
|---|---|---|---|
| `org.openjfx` | `javafx-maven-plugin` | 0.0.8 | `mvn javafx:run` — mainClass : `com.connectedneighbours.MainApp` |
| `org.apache.maven.plugins` | `maven-shade-plugin` | 3.6.2 | Fat JAR (tâche 22) — mainClass (Manifest) : `com.connectedneighbours.Launcher` ; filtre `META-INF/*.SF|DSA|RSA` |
| `org.apache.maven.plugins` | `maven-surefire-plugin` | 3.5.6 | Runner de tests (`mvn test`) |
| `org.apache.maven.plugins` | `maven-compiler-plugin` | 3.15.0 | Compilation Java 21 (`<source>21</source><target>21</target>`) |

### Commandes Maven utiles

```powershell
# Important : utiliser un JDK 21 (ex : JAVA_HOME=C:\Users\Yann\.jdks\temurin-21.0.11)
mvn -q compile          # compilation seule
mvn -q test             # tests unitaires (JUnit5 + Mockito)
mvn -q javafx:run       # lancer l'app GUI
mvn -q clean package    # fat jar (tâche 22, à valider)
```