# Validation de Hamigo

Vérification du 3 octobre 2026.

- Banque Exam1 : 2 961 identifiants uniques, textes/réponses comparés à la source, 2 961 PNG valides, aucune image manquante. 11 exclusions documentées ; 2 950 questions dans le jeu courant.
- Parcours : 14 chapitres, 56 leçons, 224 exercices ; 17 catégories et 364 flashcards.
- 15 tests JVM : calendrier SRS, séries avec changements d'heure, réponses numériques, seuils de réussite indépendants des deux épreuves.
- 18 tests instrumentés sur l'émulateur Pixel 9a Android API 36 : deux parcours Compose, contenu empaqueté, couverture des calculs procéduraux, révisions anticipées, correction d'erreurs, bonus XP, persistance/restauration, profils JSON, jeton chiffré Keystore, PNG partagé/FileProvider, rappels et OAuth sans réseau.
- APK signé personnellement installé et démarré sur le Samsung SM-S938B via ADB Wi-Fi. La signature locale exclue de Git permettra les mises à jour.
- Dépôt GitHub AlexMalfr/Hamigo vérifié privé. Version Android fondée sur le nombre de commits.

Les tests GitHub n'effectuent aucune publication réelle ; ils vérifient les formats, la sécurité locale, les liens et les primitives HTTP/OAuth. La synchronisation distante nécessite la connexion volontaire d'un compte depuis l'application et n'a pas été validée de bout en bout avec un jeton utilisateur. Les tests de rappels contrôlent programmation, annulation et heure locale ; ils ne promettent pas une livraison exacte sous Doze ou avec notifications désactivées.

La revue de banque contrôle complétude, intégrité et notes d'obsolescence. Elle ne prétend pas remplacer une relecture manuelle des 2 961 réponses communautaires.
