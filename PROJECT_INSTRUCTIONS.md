# Instructions du projet : Framework Web "Front Controller" maison

Ce fichier est la mémoire durable du projet. Il doit être recopié en entier au début de toute nouvelle discussion portant sur un sprint suivant, pour ne pas avoir à réexpliquer le contexte à chaque fois. Il doit aussi être committé à la racine du dépôt du framework et mis à jour à la fin de chaque sprint, avant la fusion de la Pull Request correspondante.

## 1. Objectif général du projet

Construire, sprint par sprint, un framework web Java fait maison qui reproduit en simplifié le rôle d'un framework comme Spring MVC : un point d'entrée unique (le Front Controller) qui reçoit toutes les requêtes HTTP, les associe à un controller (et maintenant à une méthode d'action précise), puis délègue le traitement.

Le framework est compilé en `.jar` et déployé dans une application web de test, elle-même déployée sur Tomcat. L'application de test sert uniquement à valider le framework, elle n'a pas vocation à devenir un vrai projet métier.

## 2. Architecture générale

Quatre éléments interviennent :

- **Navigateur** : envoie une requête HTTP, par exemple `http://localhost:8080/test-app/dept/new`.
- **Tomcat / Serveur** : reçoit la requête et la transmet à l'application web déployée sous le contexte `/test-app`.
- **Application de test** : contient le `web.xml`, le dossier `WEB-INF/lib` où est copié le `.jar` du framework, `WEB-INF/classes` où sont compilées ses propres classes controllers.
- **Framework** : compilé en `.jar`, contient la mécanique générique (Front Controller, Listener, scan des controllers, résolution des routes) indépendante de toute application précise.

Le `web.xml` de l'application de test déclare :

- Un Listener du framework (`ControllerScannerListener`), exécuté au démarrage de l'application, avant tout Servlet.
- Le Servlet `FrontServletController` du framework, mappé sur toutes les URL de l'application (`/*`), pour intercepter toutes les requêtes au lieu de laisser Tomcat afficher un contenu par défaut (listing de dossier, etc.).

Il n'y a pas de context-param lié à un fichier de mapping XML : le `web.xml` ne contient que la déclaration du listener et du servlet.

## 3. Décisions techniques figées

Ces choix sont validés et ne doivent pas être remis en question à chaque nouveau sprint sans raison explicite :

- **API Servlet** : Jakarta Servlet (paquet `jakarta.servlet.*`), car Tomcat 10 ou plus est utilisé. Ne jamais utiliser `javax.servlet.*`.
- **Construction** : pas de Maven ni Gradle. Compilation manuelle via `javac`, archivage via `jar`, le tout orchestré par le script `deploy.sh`. Structure imposée du dépôt : `build/`, `src/`, `deploy.sh`, `README.md`, `framework.jar` (généré par `deploy.sh`).
- **Dépôt GitHub** : seul le dépôt du framework est versionné sur GitHub (le dépôt existe déjà). L'application de test reste locale, elle sert uniquement de banc d'essai.
- **Workflow Git** : une branche par sprint (`sprint0`, `sprint1`, `sprint2`, etc.), créée depuis `main`, fusionnée via Pull Request sur GitHub. Projet en solo, donc auto-revue avant fusion.
- **Paquetage racine du framework** : `framework.core` (annotations dans `framework.core.annotation`). Paquetage de l'application de test : `testapp.controller`. À garder cohérent sur tout le projet une fois fixé ; modifiable uniquement si une contrainte externe (formateur, énoncé) l'impose.
- **Configuration des routes (décision revue au sprint 1, complétée au sprint 2)** : plus de fichier `mapping.xml`. La résolution URL -> controller se fait par annotations :
  - `@Controller` (sur la classe) marque une classe comme controller à détecter. Reste un simple marqueur, sans attribut.
  - `@RequestMapping("/chemin")` : **au sprint 1, portée sur la classe** ; **à partir du sprint 2, portée sur la méthode** (`@Target(ElementType.METHOD)`), pour permettre à une seule classe controller de regrouper plusieurs actions/URL, comme dans Spring MVC.
  - Convention d'URL : toujours avec slash initial, ex. `/dept/new`, `/dept/list`.
  - Au démarrage, `FrontServletController.init()` parcourt récursivement `WEB-INF/classes` via `ClassScanner`, charge chaque `.class` trouvé, retient celles annotées `@Controller`, puis (depuis le sprint 2) parcourt les méthodes de chaque classe retenue pour repérer celles annotées `@RequestMapping`.
  - Une classe `@Controller` sans aucune méthode annotée `@RequestMapping` est ignorée avec un avertissement loggé.
  - Plusieurs méthodes (même classe ou classes différentes) peuvent partager la même URL : le framework les accumule dans une liste par URL, il n'y a pas de détection d'erreur en cas de doublon à ce stade (décision reconduite du sprint 1).
  - Une URL demandée qui ne correspond à aucune route connue déclenche une exception ; le traitement de cette exception doit permettre d'afficher la liste des routes connues (URL, classe, méthode) pour faciliter le diagnostic.
  - Limite connue : le scan ne lit que `WEB-INF/classes` (répertoire de fichiers `.class`), pas l'intérieur des `.jar` de `WEB-INF/lib`. À garder en tête si un sprint futur veut scanner des controllers empaquetés en jar.

## 4. Glossaire des termes utilisés dans ce projet

- **Front Controller** : Servlet unique qui reçoit toutes les requêtes HTTP de l'application et décide du traitement à appliquer. Classe : `FrontServletController`.
- **Listener (`ControllerScannerListener`)** : classe qui s'exécute automatiquement au démarrage (et à l'arrêt) de l'application web, avant l'initialisation des Servlets. État actuel : se contente de logger le démarrage/arrêt de l'application ; le scan effectif des controllers et de leurs méthodes annotées a lieu dans `FrontServletController.init()`, pas dans le Listener. Point à clarifier/déplacer si un futur sprint veut que le Listener porte réellement le scan (cohérence avec le nom de la classe).
- **ClassScanner** : classe utilitaire du framework qui parcourt récursivement un répertoire de classes compilées (typiquement `WEB-INF/classes`) et renvoie la liste des `Class<?>` annotées `@Controller`.
- **`@Controller`** : annotation de classe (`framework.core.annotation`), marqueur pur, sans attribut, qui indique qu'une classe doit être scannée pour y chercher des méthodes d'action.
- **`@RequestMapping`** : annotation portée sur les méthodes depuis le sprint 2 (`@Target(ElementType.METHOD)`), qui associe une URL (avec slash initial, ex. `/dept/new`) à une méthode d'action précise d'une classe controller.
- **Route / `RouteInfo`** : association entre une URL et un couple (classe controller, méthode action), construite dynamiquement au démarrage par scan des annotations.
- **Action** : méthode d'une classe `@Controller`, annotée `@RequestMapping`, représentant une opération associée à une URL (ex. `create()`, `list()`). Depuis le sprint 2, ces méthodes sont détectées mais pas encore invoquées.
- **processRequest** : méthode unique appelée par `doGet` et `doPost`, qui contient la logique commune de traitement d'une requête. Depuis le sprint 2 : recherche l'URL demandée dans la map des routes ; si trouvée, affiche/retourne la classe et la méthode associées ; si non trouvée, déclenche une exception listant les routes connues.
- **ServletContext** : objet partagé pour toute l'application web, qui permet de transmettre des informations entre le Listener (exécuté au démarrage) et les Servlets (exécutés à chaque requête).

## 5. Journal des sprints

| Sprint | Objectif | Statut |
|---|---|---|
| Sprint 0 | Mettre en place le Front Controller, le Listener, un mapping URL -> controller via `mapping.xml`, et afficher dans le navigateur le nom de la classe controller associée à une URL demandée | Terminé (fusionné dans `main`) |
| Sprint 1 | Remplacer le mapping XML par une détection des controllers via annotations `@Controller` / `@RequestMapping` (niveau classe), scan dynamique de `WEB-INF/classes` au démarrage, routes construites dynamiquement, gestion du cas "plusieurs controllers pour la même URL" | Terminé (fusionné dans `main`) |
| Sprint 2 | Déplacer `@RequestMapping` de la classe vers la méthode, pour permettre plusieurs actions/URL par classe controller (calqué sur Spring MVC). Construire une map URL -> (classe, méthode). Ne pas encore invoquer les méthodes. Lever une exception listant les routes connues si l'URL demandée est inconnue | À venir |
| Sprint 3 | À définir | À venir |
| Sprint 3 bis | À définir | À venir |

*(À mettre à jour à la fin de chaque sprint, avant de fusionner la Pull Request correspondante, pour que ce fichier reste fiable d'une session de travail à l'autre.)*

## 6. État technique à la fin du sprint 1 (point de départ du sprint 2)

À garder en tête pour ne pas re-découvrir le code à chaque session :

- `FrontServletController` ne fait, pour l'instant, qu'afficher en HTML le nom de la/des classe(s) controller(s) trouvée(s) pour une URL : aucune méthode du controller n'est encore invoquée, il n'y a pas encore de notion de méthode d'action, de paramètres de requête, ni de vue/rendu de réponse au-delà du texte brut généré par le Front Controller lui-même.
- Les classes de test dans `testapp.controller` (`AaaController`, `BbbController`, `CccController`) sont des controllers vides, juste annotés, qui servent à valider la détection et le routage.
- Des classes de test supplémentaires existent déjà dans l'app de test pour des cas limites : doublons d'URL (`DuplicateAaaController`, `DuplicateBbbController`, `DuplicateCccController`) et variante de casse (`testCccController`) — utiles pour les plans de test manuels des prochains sprints.
- Fichier `UrlMappingLoader` : présent uniquement comme `.class` compilé résiduel dans `build/`, le `.java` correspondant a déjà été supprimé du `src/` (vestige de l'approche `mapping.xml` du sprint 0). À nettoyer (`build/` régénérable par `deploy.sh`) pour éviter toute confusion future.

## 7. Spécification du sprint 2 (à venir)

### Objectif
Passer d'un mapping "URL → classe controller" à un mapping "URL → (classe controller, méthode action)", à la manière de Spring MVC : une classe `@Controller` peut désormais contenir plusieurs méthodes, chacune associée à sa propre URL. On ne va **pas encore invoquer** ces méthodes ce sprint : on se contente de les détecter, de les mapper, et d'afficher/retourner l'information "cette URL correspond à cette classe + cette méthode", ou de signaler une URL inconnue.

### Décisions prises pour ce sprint
1. **`@Controller` reste inchangée** : annotation de classe, simple marqueur, sans attribut.
2. **`@RequestMapping` change de cible : `TYPE` → `METHOD`.** On réutilise l'annotation existante plutôt que d'en créer une nouvelle, cohérent avec Spring qui l'autorise aussi bien sur la classe que sur la méthode.
   ```java
   @Target(ElementType.METHOD)
   @Retention(RetentionPolicy.RUNTIME)
   public @interface RequestMapping {
       String value(); // ex: "/dept/new"
   }
   ```
3. **Convention d'URL : avec slash initial** (`/dept/new`, `/dept/list`), cohérent avec `getRequestURI()`.
4. **Gestion des doublons d'URL : accumulation en liste, pas d'erreur** — reconduction de la règle du sprint 1.
5. **URL inconnue : exception avec listing des routes connues.** Quand `processRequest` ne trouve pas l'URL demandée, on lève une exception (ex. `UrlNotFoundException extends ServletException`) dont le traitement affiche la liste des routes connues (URL, classe, méthode associées).

### Structure de données interne à faire évoluer
Avant (sprint 1) :
```java
Map<String, List<Class<?>>> routes;
```
Maintenant :
```java
class RouteInfo {
    Class<?> controllerClass;
    Method action;
}
Map<String, List<RouteInfo>> routes;
```

### Ce qui change dans `FrontServletController.init()`
1. Scan de `WEB-INF/classes` via `ClassScanner` (inchangé) → récupère les classes `@Controller`.
2. **Nouveau** : pour chaque classe `@Controller` trouvée, parcourir ses méthodes déclarées (`getDeclaredMethods()`), et retenir celles annotées `@RequestMapping`.
3. Pour chaque méthode annotée, lire la valeur de l'URL, construire un `RouteInfo(classe, méthode)`, et l'ajouter à la liste associée à cette URL dans la map globale.
4. Cas à couvrir : une classe `@Controller` sans aucune méthode annotée → avertissement loggé.

### Ce qui change dans `processRequest`
1. Récupérer le chemin demandé (`getRequestURI()` réduit au path relatif à l'app, comme au sprint 1/0).
2. Chercher ce chemin dans la map de routes.
   - **Trouvé** : afficher (ou renvoyer) la ou les classes + méthodes associées à cette URL — pas d'invocation de la méthode ce sprint.
   - **Non trouvé** : lever l'exception d'URL non supportée, avec le listing de toutes les routes connues (URL, classe, méthode) en message.

### Points laissés ouverts (à trancher pendant le sprint, pas figés)
- Nom exact du package/des classes de test (`dept.controller` vs autre).
- Nom exact de la classe d'exception et sa hiérarchie (`ServletException` vs `RuntimeException`).
- Convention de nommage des méthodes d'action elles-mêmes (`create`, `list`, etc.) — libre, puisqu'on ne les invoque pas encore, seule leur annotation `@RequestMapping` compte pour ce sprint.

### Plan de test manuel suggéré
- Une classe `@Controller` avec deux méthodes annotées différemment (`/dept/new` → `create()`, `/dept/list` → `list()`) → vérifier que les deux routes apparaissent correctement.
- Une URL non déclarée (ex. `/dept/delete`) → vérifier que l'exception se déclenche et que le listing des routes connues s'affiche.
- Deux méthodes (même classe ou classes différentes) partageant la même URL → vérifier qu'elles sont bien toutes les deux listées, sans erreur bloquante.
- Une classe `@Controller` sans aucune méthode annotée → vérifier l'avertissement loggé.

## 8. Workflow Git / GitHub

1. Se placer sur `main` et synchroniser : `git checkout main` puis `git pull`.
2. Créer la branche du sprint : `git checkout -b sprintX` (X = numéro du sprint, ex. `sprint2`, `sprint3`, `sprint3bis`).
3. Travailler par petits commits clairs et atomiques (un commit = une étape logique, pas un seul gros commit en fin de sprint).
4. Pousser la branche : `git push -u origin sprintX`.
5. Ouvrir une Pull Request sur GitHub, de `sprintX` vers `main`.
6. Relire son propre diff avant de fusionner : vérifier qu'aucun fichier compilé du dossier `build/` n'a été ajouté par erreur, que la description de la PR résume bien le sprint et reprend le plan de test manuel.
7. Fusionner la Pull Request (squash and merge recommandé, pour garder un historique propre sur `main` tout en gardant le détail des commits visible dans la PR fermée).
8. Revenir sur `main`, `git pull`, supprimer la branche locale et distante si elle n'est plus utile.
9. Mettre à jour le journal des sprints (section 5) et l'état technique (section 6) avec le statut "Terminé" et les nouveaux points à retenir.

## 9. Comment réutiliser ce fichier pour démarrer un nouveau sprint

Dans une nouvelle discussion, copier-coller l'intégralité de ce fichier, puis indiquer le numéro du sprint visé et ce qui est attendu pour ce sprint. Cela suffit pour que la suite du travail s'appuie sur les mêmes conventions, sans répéter toute l'explication initiale du projet.
