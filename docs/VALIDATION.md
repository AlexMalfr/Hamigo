# Validation de Hamigo

Vérification du 4 octobre 2026.

- Banque Exam1 : 2 961 identifiants uniques, textes/réponses comparés à la source, 2 961 PNG valides, aucune image manquante. 11 exclusions documentées ; 2 950 questions dans le jeu courant.
- Parcours : 21 chapitres, 94 leçons, 826 exercices rédigés ; 17 catégories de ressources et 364 flashcards. Le script d'approfondissement est idempotent et conserve les identifiants historiques. Le catalogue procédural contient 5 188 variantes.
- **31 tests JVM réussis** : 16 tests des règles d'apprentissage, quatre du générateur étendu, six des outils mathématiques, deux des séances aléatoires et trois des messages de rappel.
- **40 tests instrumentés réussis** sur Pixel 9a Android API 36 : état Compose actualisé sans quitter la page, parcours utilisateur, contenu et progression, fusion de sauvegardes, XP sans doublon, événements concurrents, préférences, format malformé, Keystore, invitations, partage FileProvider, décodage réel des QR, posters avec graphiques, rappels et audit visuel.
- **131 captures Android revues** : accueil, cinq onglets, réglages, cours, formats de question et corrections, résultats, ressources et outils, invitations, posters, notifications et les 48 combinaisons de poses/expressions de Pico. Les captures finales sont conservées dans `output/visual-audit-release/` et le rapport des tests Android dans `output/android-tests-final.txt`.
- Assemblage debug et APK de tests réussis. Lint debug : aucune erreur, 50 avertissements et une indication, principalement des API Android/Compose anciennes et des suggestions KTX.
- Application OAuth GitHub commune Hamigo réellement enregistrée avec Device Flow activé ; identifiant public intégré dans l'APK. L'endpoint de demande de code accepte cet identifiant. Aucun secret OAuth n'a été créé pour l'app Android.
- Page HTTPS d'invitation et fichier Android Asset Links publiés et accessibles en HTTP 200. Le certificat annoncé correspond à la signature de release locale, exclue de Git.
- Dépôt GitHub AlexMalfr/Hamigo vérifié privé. La version Android utilise le nombre de commits et la révision Git. La livraison construit l'APK signé depuis un commit propre, vérifie sa signature, met à jour le Samsung SM-S938B avec `adb install -r`, puis contrôle la version et la vérification des App Links sur ce téléphone. Les preuves de livraison sont conservées dans `output/`.

Les tests GitHub ne publient aucun Gist. Ils vérifient les formats, la fusion, la protection locale et les liens ; l'enregistrement de l'app OAuth et son endpoint ont aussi été contrôlés réellement. La synchronisation distante complète nécessite la connexion volontaire d'un compte depuis Hamigo et n'a pas été validée de bout en bout avec un jeton utilisateur. Les rappels sont contrôlés pour leur contenu, illustration, programmation, annulation et heure locale ; Android décide de leur présentation extérieure et peut retarder la livraison sous Doze.

La revue de banque contrôle complétude, intégrité et notes d'obsolescence. Elle ne remplace pas une relecture pédagogique des 2 961 réponses communautaires et de tous les nouveaux cours par un opérateur formateur.
