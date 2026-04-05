# Client-Java

Client Java pour l'administration du site Connected Neighbours.

## Structure du Projet

- `pom.xml` : Fichier de configuration Maven, gère le cycle de vie du projet et ses dépendances.
- `src/main/java/com/connectedneighbours/`
    - `MainApp.java` : Point d'entrée.
    - `controller/` : Les contrôleurs liant les vues FXML à la logique métier (MVC).
    - `model/` : Les classes représentant les entités de l'application (Incident, Statistic, etc.).
    - `service/` : Contient la logique métier, notamment la synchronisation avec l'API, les mises à jour et la gestion
      des incidents.
    - `repository/` : Les classes gérant l'accès à la base de données locale (H2).
    - `plugin/` : L'architecture des plugins avec ServiceLoader pour étendre l'application.
        - `plugins/` : Les implémentations spécifiques des plugins.
    - `theme/` : La logique de gestion des thèmes (clair, sombre, etc.).
    - `i18n/` : Le gestionnaire de l'internationalisation.
    - `auth/` : La gestion de l'authentification (SSO avec l'application Web).
- `src/main/resources/` : Les ressources non-Java (fichiers de configuration, vues, styles, images).
    - `com/connectedneighbours/fxml/` : Vues de l'application (fichiers FXML).
    - `com/connectedneighbours/css/` : Fichiers de style CSS pour le design de l'interface.
    - `com/connectedneighbours/images/` : Ressources visuelles et icônes.
    - `i18n/` : Fichiers `.properties` contenant les textes pour chaque langue.
    - `META-INF/services/` : Enregistrement des implémentations de l'interface Plugin, utilisé par ServiceLoader.
    - `logback.xml` : Configuration du framework de logging.
- `src/test/` : Contient les cas de tests unitaires et d'intégration.

## Dépendances

| Lib                   | Usage                            |
|-----------------------|----------------------------------|
| JavaFX 21             | (controls, fxml, web)UI Desktop  | 
| `H2 BDD`              | locale embarquée (offline-first) |
| Apache HttpClient 5   | Sync avec l'API                  |
| Node.js               | Jackson Sérialisation JSON       |
| JUnit 5 + Mockito     | Tests                            |
| Logback/SLF4J         | Logs                             |
| `javafx-maven-plugin` | `mvn javafx:run` pour lancer     |
| `maven-shade-plugin`  | Génère le fat `.jar` final       |