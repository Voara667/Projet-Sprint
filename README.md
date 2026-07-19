# Framework Web maison

## C'est quoi ?

Un mini-framework MVC Java fait maison, inspiré de Spring MVC. Une seule Servlet (le Front Controller) reçoit toutes les requêtes HTTP, retrouve la bonne méthode grâce à des annotations, l'exécute, puis affiche soit une page de diagnostic, soit une vue JSP avec des données.

## Prérequis

- JDK 17+
- Apache Tomcat 10+ (API Jakarta, `jakarta.servlet.*`)
- Jars Spring Framework 6.x (`spring-core`, `spring-context`, `spring-beans`, `spring-web`, `spring-expression`, `spring-aop`, `spring-jcl`), disponibles dans `~/.m2/repository/org/springframework/...`

## Comment l'utiliser

1. Créer un projet web (dossier avec `WEB-INF/classes`, `WEB-INF/lib`, `web.xml`), placé **au même niveau** que ce dépôt (`Projet-Sprint`).
2. Y écrire des contrôleurs annotés `@Controller`/`@RequestMapping`.
3. Déclarer dans son `web.xml` : le `ContextLoaderListener` de Spring, `ControllerScannerListener` (le nôtre) et `FrontServletController` (le nôtre) mappé sur `/*`.
4. Depuis ce dépôt, lancer :
   ```bash
   export CATALINA_HOME=/chemin/vers/tomcat
   ./deploy.sh ../nom-du-projet
   ```
   (sans argument, `./deploy.sh` déploie par défaut `../test-app`)

Le script compile le framework, génère `framework.jar`, le copie avec les jars Spring dans le projet cible, compile ses contrôleurs, déploie le tout sur Tomcat et redémarre le serveur.

## Ce qu'il fait

- Détecte les contrôleurs (`@Controller`) et leurs actions (`@RequestMapping`) par scan de classes, au démarrage de l'application (pas à la première requête).
- Route selon l'URL **et** la méthode HTTP (`GET`/`POST`), avec 404/405 explicites si non trouvé.
- Refuse de démarrer si deux actions partagent exactement la même URL et méthode HTTP (doublon).
- Instancie et invoque réellement chaque contrôleur (nouvelle instance à chaque requête).
- Une action peut retourner un `ModelAndView` : le framework transfère les données vers la vue et l'affiche (JSP), sinon elle affiche un simple diagnostic.
- Une action peut demander un `ApplicationContext` Spring en paramètre, pour aller chercher des beans (repository, service) gérés par Spring — `null` si Spring n'est pas démarré, pas d'erreur du framework dans ce cas.

Note importante: le contrôleur doit toujours passer par ApplicationContext ctx en paramètre et appeler ctx.getBean(...) lui-même. 
