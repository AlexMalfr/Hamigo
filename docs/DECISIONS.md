# Hamigo

Application native Android en Kotlin / Jetpack Compose et Material 3. Nom : une petite onde, une courte leçon. Mascotte **Pico**, petit poste radio crème et corail.

Le parcours est écrit et stable ; l'entraînement mélange les questions locales. Aucun modèle d'IA ni serveur n'est nécessaire pour apprendre. Les rappels sont des alarmes Android inexactes, adaptées aux économies d'énergie.

Les épreuves officielles actuelles comportent chacune 20 QCM : réglementation (15 min) et technique (30 min), 1 point par bonne réponse, 0 autrement, réussite à 10/20 dans chaque partie. Sources et date de vérification se trouvent dans SOURCES-COURSES.md.

Le système de répétition espacée est une variante SM-2 explicite, distincte de l'algorithme propriétaire de Duolingo. Les réponses incorrectes sont revues après dix minutes puis selon les intervalles calculés. L'XP est enregistré à chaque réponse ; terminer la session valide une leçon.

Social sans serveur dédié : partage Android d'image, fichiers et nudges ; comparaison de profils importés. Option GitHub Gists secrets pour publier automatiquement un résumé et actualiser les amis, avec jeton fourni par l'utilisateur ou application OAuth personnelle. Un Gist secret est accessible à toute personne qui connaît son URL. Aucun jeton personnel de développement n'est intégré à l'APK.
