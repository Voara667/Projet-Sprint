# Instructions du projet : Framework Web "Front Controller" maison

Ce fichier est la memoire durable du projet. Il doit etre recopie en entier au debut de toute nouvelle discussion portant sur un sprint suivant, pour ne pas avoir a reexpliquer le contexte a chaque fois. Il doit aussi etre committe a la racine du depot du framework et mis a jour a la fin de chaque sprint, avant la fusion de la Pull Request correspondante.

## 1. Objectif general du projet

Construire, sprint par sprint, un framework web Java fait maison qui reproduit en simplifie le role d'un framework comme Spring MVC : un point d'entree unique (le Front Controller) qui recoit toutes les requetes HTTP, les associe a un controleur grace a une configuration externe, puis delegue le traitement a ce controleur.

Le framework est compile en `.jar` et deploye dans une application web de test, elle-meme deployee sur Tomcat. L'application de test sert uniquement a valider le framework, elle n'a pas vocation a devenir un vrai projet metier.

## 2. Architecture generale

Quatre elements interviennent :

- Navigateur : envoie une requete HTTP, par exemple `http://localhost:8080/test-app/aaa`.
- Tomcat / Serveur : recoit la requete et la transmet a l'application web deployee sous le contexte `/test-app`.
- Application de test : contient le `web.xml`, le dossier `WEB-INF/lib` ou est copie le `.jar` du framework, et ses propres classes controleurs.
- Framework : compile en `.jar`, contient la mecanique generique (Front Controller, Listener, chargement de configuration) independante de toute application precise.

Le `web.xml` de l'application de test declare :
- Un Listener du framework, execute au demarrage de l'application, avant tout Servlet.
- Le Servlet `FrontServletController` du framework, mappe sur toutes les URL de l'application (`/*`), pour intercepter toutes les requetes au lieu de laisser Tomcat afficher un contenu par defaut (listing de dossier, etc.).

## 3. Decisions techniques figees

Ces choix sont valides et ne doivent pas etre remis en question a chaque nouveau sprint sans raison explicite :

- API Servlet : Jakarta Servlet (paquet `jakarta.servlet.*`), car Tomcat 10 ou plus est utilise. Ne jamais utiliser `javax.servlet.*`.
- Construction : pas de Maven ni Gradle. Compilation manuelle via `javac`, archivage via `jar`, le tout orchestre par le script `deploy.sh`. Structure imposee du depot : `build/`, `lib/`, `src/`, `deploy.sh`, `README.md`.
- Depot GitHub : seul le depot du framework est versionne sur GitHub (le depot existe deja). L'application de test reste locale, elle sert uniquement de banc d'essai.
- Workflow Git : une branche par sprint (`sprint0`, `sprint1`, etc.), creee depuis `main`, fusionnee via Pull Request sur GitHub. Projet en solo, donc auto-revue avant fusion.
- Paquetage racine du framework : `framework.core`. Paquetage de l'application de test : `testapp.controller`. A garder coherent sur tout le projet une fois fixe ; modifiable uniquement si une contrainte externe (formateur, enonce) l'impose.
- Fichier de configuration des routes : XML, nomme `mapping.xml`, situe par defaut dans `/WEB-INF/mapping.xml` de l'application de test. Son emplacement exact est passe en parametre dans `web.xml` (context-param `mappingConfigLocation`) pour que le framework ne soit jamais code en dur autour d'une application precise.

## 4. Glossaire des termes utilises dans ce projet

- Front Controller : Servlet unique qui recoit toutes les requetes HTTP de l'application et decide du traitement a appliquer. Classe : `FrontServletController`.
- Listener : classe qui s'execute automatiquement au demarrage (et a l'arret) de l'application web, avant l'initialisation des Servlets. Utilisee ici pour charger la configuration des routes une seule fois, au demarrage.
- Mapping URL -> Controleur : association entre une URL et le nom complet (avec paquetage) d'une classe controleur, declaree dans `mapping.xml`.
- processRequest : methode unique appelee par `doGet` et `doPost`, qui contient la logique commune de traitement d'une requete, pour eviter la duplication entre GET et POST.
- ServletContext : objet partage pour toute l'application web, qui permet de transmettre des informations entre le Listener (execute au demarrage) et les Servlets (executes a chaque requete).

## 5. Journal des sprints

| Sprint | Objectif | Statut |
|---|---|---|
| Sprint 0 | Mettre en place le Front Controller, le Listener de chargement de configuration, et afficher dans le navigateur le nom de la classe controleur associee a une URL demandee | Terminé |
| Sprint 1 | A definir | A venir |

A mettre a jour a la fin de chaque sprint, avant de fusionner la Pull Request correspondante, pour que ce fichier reste fiable d'une session de travail a l'autre.

## 6. Workflow Git / GitHub

1. Se placer sur `main` et synchroniser : `git checkout main` puis `git pull`.
2. Creer la branche du sprint : `git checkout -b sprintX` (X = numero du sprint).
3. Travailler par petits commits clairs et atomiques (un commit = une etape logique, pas un seul gros commit en fin de sprint).
4. Pousser la branche : `git push -u origin sprintX`.
5. Ouvrir une Pull Request sur GitHub, de `sprintX` vers `main`.
6. Relire son propre diff avant de fusionner : verifier qu'aucun fichier compile du dossier `build/` n'a ete ajoute par erreur, que la description de la PR resume bien le sprint et reprend le plan de test manuel.
7. Fusionner la Pull Request (squash and merge recommande, pour garder un historique propre sur `main` tout en gardant le detail des commits visible dans la PR fermee).
8. Revenir sur `main`, `git pull`, supprimer la branche locale et distante si elle n'est plus utile.
9. Mettre a jour le journal des sprints (section 5) avec le statut "Termine".

## 7. Comment reutiliser ce fichier pour demarrer un nouveau sprint

Dans une nouvelle discussion, copier-coller l'integralite de ce fichier, puis indiquer le numero du sprint vise et ce qui est attendu pour ce sprint. Cela suffit pour que la suite du travail s'appuie sur les memes conventions, sans repeter toute l'explication initiale du projet.
