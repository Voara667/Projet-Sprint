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
- **Configuration des routes (décision revue au sprint 1, complétée aux sprints 2 et 3)** : plus de fichier `mapping.xml`. La résolution URL -> controller se fait par annotations :
  - `@Controller` (sur la classe) marque une classe comme controller à détecter. Reste un simple marqueur, sans attribut.
  - `@RequestMapping("/chemin")` : **au sprint 1, portée sur la classe** ; **à partir du sprint 2, portée sur la méthode** (`@Target(ElementType.METHOD)`), pour permettre à une seule classe controller de regrouper plusieurs actions/URL, comme dans Spring MVC.
  - **Depuis le sprint 3** : `@RequestMapping` porte un second attribut `method()` de type `String`, valeur par défaut `"GET"`. Une même URL peut donc être partagée par deux actions différentes tant qu'elles ne partagent pas la même méthode HTTP (ex. `/test` en `GET` → une action, `/test` en `POST` → une autre).
  - Convention d'URL : toujours avec slash initial, ex. `/dept/new`, `/dept/list`.
  - Au démarrage, `FrontServletController.init()` parcourt récursivement `WEB-INF/classes` via `ClassScanner`, charge chaque `.class` trouvé, retient celles annotées `@Controller`, puis (depuis le sprint 2) parcourt les méthodes de chaque classe retenue pour repérer celles annotées `@RequestMapping`.
  - Une classe `@Controller` sans aucune méthode annotée `@RequestMapping` est ignorée avec un avertissement loggé.
  - **Depuis le sprint 3**, la clé de routage n'est plus l'URL seule mais le couple (URL, méthode HTTP), porté par la classe `UrlMethod` (`equals`/`hashCode` surchargés : URL sensible à la casse, méthode HTTP insensible à la casse). Plusieurs méthodes (même classe ou classes différentes) peuvent partager exactement la même `UrlMethod` (même URL **et** même méthode HTTP) : le framework les accumule dans une liste, il n'y a pas de détection d'erreur en cas de doublon à ce stade (décision reconduite du sprint 1/2, à reconsidérer au sprint 3 bis dès que les méthodes seront réellement invoquées — voir section 7).
  - Une URL/méthode HTTP demandée qui ne correspond à aucune route connue déclenche une exception, et le traitement de cette exception doit permettre d'afficher la liste des routes connues (URL, méthode HTTP, classe, méthode Java) pour faciliter le diagnostic. **Depuis le sprint 3**, deux cas sont distingués : URL totalement inconnue (`UrlNotFoundException`, HTTP 404) et URL connue mais méthode HTTP différente (`HttpMethodNotSupportedException`, HTTP 405, message listant les méthodes HTTP réellement supportées pour cette URL).
  - Limite connue : le scan ne lit que `WEB-INF/classes` (répertoire de fichiers `.class`), pas l'intérieur des `.jar` de `WEB-INF/lib`. À garder en tête si un sprint futur veut scanner des controllers empaquetés en jar.

## 4. Glossaire des termes utilisés dans ce projet

- **Front Controller** : Servlet unique qui reçoit toutes les requêtes HTTP de l'application et décide du traitement à appliquer. Classe : `FrontServletController`.
- **Listener (`ControllerScannerListener`)** : classe qui s'exécute automatiquement au démarrage (et à l'arrêt) de l'application web, avant l'initialisation des Servlets. État actuel : se contente de logger le démarrage/arrêt de l'application ; le scan effectif des controllers et de leurs méthodes annotées a lieu dans `FrontServletController.init()`, pas dans le Listener. Point à clarifier/déplacer si un futur sprint veut que le Listener porte réellement le scan (cohérence avec le nom de la classe).
- **ClassScanner** : classe utilitaire du framework qui parcourt récursivement un répertoire de classes compilées (typiquement `WEB-INF/classes`) et renvoie la liste des `Class<?>` annotées `@Controller`.
- **`@Controller`** : annotation de classe (`framework.core.annotation`), marqueur pur, sans attribut, qui indique qu'une classe doit être scannée pour y chercher des méthodes d'action.
- **`@RequestMapping`** : annotation portée sur les méthodes depuis le sprint 2 (`@Target(ElementType.METHOD)`), qui associe une URL (avec slash initial, ex. `/dept/new`) à une méthode d'action précise d'une classe controller. Depuis le sprint 3, porte aussi l'attribut `method()` (`String`, défaut `"GET"`) pour préciser la méthode HTTP attendue.
- **Route / `RouteInfo`** : association entre une URL et un couple (classe controller, méthode action), construite dynamiquement au démarrage par scan des annotations. Inchangée depuis le sprint 2.
- **`UrlMethod`** *(nouveau, sprint 3)* : classe clé de la map de routes, encapsulant le couple (URL, méthode HTTP). `equals()`/`hashCode()` surchargés : comparaison de l'URL sensible à la casse, comparaison de la méthode HTTP insensible à la casse (`GET` = `get`). Remplace la simple `String` utilisée comme clé aux sprints 1/2.
- **Action** : méthode d'une classe `@Controller`, annotée `@RequestMapping`, représentant une opération associée à une URL et une méthode HTTP (ex. `create()`, `list()`, `testGet()`, `testPost()`). Depuis le sprint 3, ces méthodes sont détectées et mappées avec leur méthode HTTP mais toujours pas invoquées.
- **processRequest** : méthode unique appelée par `doGet` et `doPost`, qui contient la logique commune de traitement d'une requête. Depuis le sprint 3 : construit une clé `UrlMethod(path, méthode HTTP de la requête)` et la cherche dans la map des routes ; si trouvée, affiche/retourne la classe et la méthode associées ; si l'URL existe mais pas avec cette méthode HTTP, déclenche `HttpMethodNotSupportedException` (405) listant les méthodes HTTP supportées pour cette URL ; si l'URL est totalement inconnue, déclenche `UrlNotFoundException` (404) listant toutes les routes connues.
- **`UrlNotFoundException`** : exception (`extends ServletException`) levée quand l'URL demandée ne correspond à aucune route connue, quelle que soit la méthode HTTP. Traitée en HTTP 404.
- **`HttpMethodNotSupportedException`** *(nouveau, sprint 3)* : exception (`extends ServletException`) levée quand l'URL demandée est connue mais pas avec la méthode HTTP utilisée. Traitée en HTTP 405, message listant les méthodes HTTP réellement supportées pour cette URL.
- **ServletContext** : objet partagé pour toute l'application web, qui permet de transmettre des informations entre le Listener (exécuté au démarrage) et les Servlets (exécutés à chaque requête).

## 5. Journal des sprints

| Sprint | Objectif | Statut |
|---|---|---|
| Sprint 0 | Mettre en place le Front Controller, le Listener, un mapping URL -> controller via `mapping.xml`, et afficher dans le navigateur le nom de la classe controller associée à une URL demandée | Terminé (fusionné dans `main`) |
| Sprint 1 | Remplacer le mapping XML par une détection des controllers via annotations `@Controller` / `@RequestMapping` (niveau classe), scan dynamique de `WEB-INF/classes` au démarrage, routes construites dynamiquement, gestion du cas "plusieurs controllers pour la même URL" | Terminé (fusionné dans `main`) |
| Sprint 2 | Déplacer `@RequestMapping` de la classe vers la méthode, pour permettre plusieurs actions/URL par classe controller (calqué sur Spring MVC). Construire une map URL -> (classe, méthode). Ne pas encore invoquer les méthodes. Lever une exception listant les routes connues si l'URL demandée est inconnue | Terminé (fusionné dans `main`) |
| Sprint 3 | Ajouter la méthode HTTP comme second critère de routage (attribut `method()` sur `@RequestMapping`, défaut `GET`). Introduire la clé `UrlMethod` (URL + méthode HTTP, `equals`/`hashCode` surchargés) à la place de la simple `String`. Distinguer URL inconnue (`UrlNotFoundException`, 404) et méthode HTTP non supportée pour une URL connue (`HttpMethodNotSupportedException`, 405). Toujours pas d'invocation des méthodes d'action | Terminé (fusionné dans `main`) |
| Sprint 3 bis | À définir (voir section 7 : invocation réelle des méthodes d'action, méthode HTTP typée en enum, comportement "prototype" vs "singleton") | À venir |

*(À mettre à jour à la fin de chaque sprint, avant de fusionner la Pull Request correspondante, pour que ce fichier reste fiable d'une session de travail à l'autre.)*

## 6. État technique à la fin du sprint 3 (point de départ du sprint 3 bis)

À garder en tête pour ne pas re-découvrir le code à chaque session :

- `FrontServletController` distingue désormais l'URL **et** la méthode HTTP : `Map<UrlMethod, List<RouteInfo>> routes`. `processRequest` construit la clé à partir de `getPathInfo()` (repli sur `/` si `null`) et `request.getMethod()`, puis :
  - route trouvée → affiche en HTML la/les classe(s) + méthode(s) associées (toujours pas d'invocation) ;
  - URL connue mais méthode HTTP différente → `HttpMethodNotSupportedException`, réponse HTTP 405 listant les méthodes HTTP réellement supportées pour cette URL ;
  - URL totalement inconnue → `UrlNotFoundException`, réponse HTTP 404 listant toutes les routes connues (URL, méthode HTTP, classe, méthode Java).
- Aucune méthode d'action n'est encore invoquée, il n'y a toujours pas de notion de paramètres de requête ni de vue/rendu au-delà du HTML généré par le Front Controller lui-même.
- Le contenu des réponses HTML passe par un `escapeHtml()` interne avant affichage (nom de classe/méthode, message d'erreur) — précaution ajoutée au sprint 3, non explicitement demandée mais à conserver.
- `TestController` (`testapp.controller`) a été ajouté avec deux actions sur la même URL mais des méthodes HTTP différentes : `testGet()` (`/test`, `GET` implicite) et `testPost()` (`/test`, méthode `POST` explicite) — sert à valider la distinction par méthode HTTP.
- Les classes de test des sprints précédents (`AaaController`, `BbbController`, `CccController`, `DeptController`, les doublons `DuplicateAaaController`/`DuplicateBbbController`/`DuplicateCccController`, la variante de casse `testCccController`) sont conservées et continuent de fonctionner sans modification (`method()` par défaut `"GET"`, migration silencieuse confirmée).
- Fichier `UrlMappingLoader` : vestige de l'approche `mapping.xml` du sprint 0, à vérifier/nettoyer dans `build/` si toujours présent (`build/` régénérable par `deploy.sh`).
- Rappel process : ce fichier (`PROJECT_INSTRUCTIONS.md`) doit être mis à jour et committé **avant** la fusion de la PR d'un sprint, pas après — à ne pas oublier pour le sprint 3 bis.

## 7. Spécification du sprint 3 bis (à venir)

### Objectif
Passer de la simple détection/affichage à la **véritable invocation** de la méthode d'action associée à une route. On garde le principe (url, méthode HTTP) du sprint 3, mais la méthode HTTP devient un type dédié (enum) plutôt qu'une `String`, et surtout : quand une route est trouvée, le Front Controller instancie le controller et **appelle réellement** la méthode annotée (sans paramètre pour l'instant — les paramètres de requête sont prévus pour le sprint 4).

### Décisions à prendre / proposées pour ce sprint
1. **Méthode HTTP typée** : remplacer la `String method()` de `@RequestMapping` par un type dédié, ex. un enum `HttpMethod { GET, POST }` (extensible plus tard à `PUT`, `DELETE`, etc.), toujours avec `GET` comme valeur par défaut.
   ```java
   public enum HttpMethod { GET, POST }

   @Target(ElementType.METHOD)
   @Retention(RetentionPolicy.RUNTIME)
   public @interface RequestMapping {
       String value();
       HttpMethod method() default HttpMethod.GET;
   }
   ```
2. **Invocation réelle de l'action** : dans `processRequest`, une fois la route trouvée, instancier `routeInfo.getControllerClass()` (constructeur par défaut) puis invoquer `routeInfo.getAction()` via réflexion (`method.invoke(instance)`). Pas de paramètres cette étape-ci.
3. **Cycle de vie de l'instance controller : "prototype", pas "singleton".** Chaque requête crée une nouvelle instance du controller (comportement par défaut de ce sprint). Le mode singleton (une seule instance réutilisée pour toute l'application) est explicitement repoussé à un sprint ultérieur (sprint 4 ou après) — ne pas l'implémenter maintenant même si ça semble plus optimal.
4. **Doublon exact (même URL + même méthode HTTP) : devient une erreur.** Contrairement aux sprints 1 à 3 où un doublon s'accumulait silencieusement dans une liste, dès lors qu'on invoque réellement une méthode, deux actions strictement identiques (même `UrlMethod`) créent une ambiguïté qu'on ne peut plus laisser passer : le chargement des routes doit lever une exception si deux `@RequestMapping` déclarent exactement la même URL et la même méthode HTTP. Ce test ne peut réussir que si `UrlMethod.hashCode()`/`equals()` sont bien implémentés (sans ça, deux clés identiques en apparence ne seraient jamais détectées comme des doublons dans la `Map`).
   - **Ne change pas** : deux actions peuvent toujours partager la même URL tant qu'elles diffèrent par la méthode HTTP (`/test` en `GET` et `/test` en `POST` restent parfaitement valides) — seule l'exacte identité (URL, méthode HTTP) devient bloquante.
5. **Vérification de l'invocation côté développeur** : pas de mécanisme de vue pour l'instant. Pour valider manuellement que l'invocation a bien lieu, la méthode de test ajoute un `System.out.println(...)` et on vérifie son affichage dans la console Tomcat.

### Ce qui change dans `FrontServletController.init()` / chargement des routes
1. Construction de la map inchangée dans son principe (`Map<UrlMethod, List<RouteInfo>>` ou évolution vers une valeur unique `RouteInfo` si le doublon exact devient bloquant — à trancher pendant le sprint).
2. **Nouveau** : lors de l'ajout d'une route, si une entrée existe déjà pour l'exacte même `UrlMethod`, lever une exception dédiée (ex. `DuplicateRouteException`) plutôt que d'accumuler silencieusement.

### Ce qui change dans `processRequest`
1. Résolution de la route inchangée (URL + méthode HTTP typée désormais en `HttpMethod`).
2. **Nouveau** : une fois la route trouvée, instancier le controller et invoquer la méthode d'action via réflexion, sans paramètre.
3. Les cas d'erreur (404 / 405) restent inchangés par rapport au sprint 3.

### Points laissés ouverts (à trancher pendant le sprint, pas figés)
- Nom exact de l'exception de doublon (`DuplicateRouteException` proposé).
- Faut-il garder `Map<UrlMethod, List<RouteInfo>>` (en validant `size() == 1` avant d'invoquer) ou passer directement à `Map<UrlMethod, RouteInfo>` maintenant que le doublon exact est bloquant ? Les deux sont défendables ; à documenter ici une fois tranché.
- Gestion des exceptions levées par la méthode invoquée elle-même (ex. `InvocationTargetException`) — non précisée dans les notes de sprint, à définir.

### Plan de test manuel suggéré
- `GET /test` et `POST /test` → chacune invoque bien sa propre méthode (`System.out.println` visible dans la console Tomcat, une ligne différente pour chaque cas).
- Appeler `/test` en `GET` plusieurs fois de suite → vérifier (via `System.out.println` d'un identifiant d'instance, ex. `this.hashCode()`) qu'une nouvelle instance du controller est bien créée à chaque appel (comportement "prototype").
- Déclarer volontairement deux méthodes avec exactement la même URL et la même méthode HTTP → vérifier qu'une exception est bien levée au démarrage (et non silencieusement ignorée) ; si elle n'est pas levée, c'est que `equals`/`hashCode` de `UrlMethod` (ou la détection de doublon) ne fonctionne pas.
- Non-régression : les cas 404 (URL inconnue) et 405 (méthode HTTP non supportée) du sprint 3 continuent de fonctionner.

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