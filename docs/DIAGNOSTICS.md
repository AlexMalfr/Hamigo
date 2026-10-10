# Diagnostics sur appareil

Depuis une calculatrice Hamigo, saisir **73887388 suivi de l’année courante**, puis `=` : **738873882026** en 2026, **738873882027** en 2027. Repère mnémotechnique : **73 88, deux fois, puis l’année en cours**. L'année est lue dans le calendrier local du téléphone à chaque vérification ; l'ancien code n'est plus accepté après le changement d'année. Le code doit être saisi tel quel ; une équation équivalente n'ouvre pas le menu. Ce raccourci caché n'est pas une protection de sécurité.

## Outils disponibles

- **Outils** : mode debug persistant, gel de progression, bac à sable client, essais des vibrations.
- **Types** : ouvrir un exemple de chacun des formats d'exercice, ou une séance qui les parcourt tous.
- **Questions** : catalogue des identifiants uniques du contenu Hamigo et de la banque Exam1 téléchargée, avec filtres Toutes / Hamigo / Exam’1 / Variantes / Mémo et nombre de questions par banque. Exam’1 inclut ses questions illustrées, également testables. Recherche dans tous les champs (ID, énoncé, réponses possibles, explication, paires, valeurs, unités, thème, source, asset et emplacement dans le Parcours), filtre par format. Les mots peuvent correspondre à des champs différents. Chaque question du Parcours indique chapitre → leçon → position dans la leçon, dans la liste et le détail ; une question présente dans plusieurs cours indique chaque emplacement. Les positions sont des repères de lecture, jamais des identifiants. Si Exam1 affiche zéro question en 0.48, sa banque manque encore : attendre son téléchargement ou consulter son état dans les paramètres. Le détail présente aussi source, asset, valeur/tolérance et correction masquable. « Tester cette question » ouvre l'écran natif de réponse. Pendant la migration 0.47–0.51, les anciens IDs du Parcours restent aussi recherchables. Les filtres défilent horizontalement sur petit écran et les résultats restent au-dessus du clavier.
- **Aperçus** : pages Moi avec différents états d'activité, équipe fictive, chapitres terminés et bilans de cours/examen. Les pages restent défilables ; les actions sont bloquées.
- **Infos** : version, appareil, API Android, dimensions/densité/police de la fenêtre, heap et inventaire du contenu par banque/type.

Le mode debug ajoute une **surimpression** avec ID, type, position et version sur les questions. Il ne réserve pas d'espace dans leur layout. Son icône permet de rouvrir les outils. Les identifiants affichés sont ceux déjà utilisés par le contenu et la répétition espacée ; le menu ne les renumérote pas.

## Tester sans gagner d'XP

Les séances ouvertes dans le menu sont toujours isolées. Elles utilisent les composants natifs, mais ne valident aucun cours, n'ajoutent aucun XP et n'altèrent pas les rappels SRS réels. Fermer leur écran revient aux outils ; fermer les outils retrouve la séance d'origine.

**Geler la progression** s'applique aussi aux séances lancées normalement. Le gel porte sur XP, validation du parcours et enregistrement SRS, pas seulement sur l'XP. Une séance commencée gelée le reste jusqu'à sa fermeture, même si le réglage est désactivé en cours de route. Les réponses déjà enregistrées avant l'activation du gel restent conservées. Le bandeau « PROGRESSION GELÉE » reste visible même si les autres diagnostics sont désactivés.

Mode debug et gel sont des préférences **locales persistantes**. Aucune modification du schéma des sauvegardes GitHub.

Les préférences Sons/Vibrations des séances isolées et du bac à sable sont également séparées. Leur réglage initial reprend celui de l'appareil ; le contrôleur retrouve le réglage réel en quittant le test, sans enregistrer ses modifications dans les préférences personnelles.

La question visible dans le diagnostic conserve ses écoutes pédagogiques, y compris le récepteur Fréquence : souffle et voix en continu après la première manipulation, selon Sons, avec reprise après une lecture de Pico. La séance réelle derrière le menu reste couverte et silencieuse. Fermer le test, valider la réponse, couper ses sons ou passer en arrière-plan arrête sa piste ; aucun XP ni SRS réel n'est ajouté. La simulation reste désactivée dans un véritable examen.

Les paramètres Gameplay sont repris à l'ouverture du modèle isolé : mode Morse à un/deux boutons et timing, avec les espaces de lettre/mot qui en découlent. La copie reste en mémoire ; la changer dans le bac à sable ne modifie pas les préférences personnelles. Les aperçus possèdent aussi un registre Android de résultats isolé pour les écrans qui enregistrent un sélecteur ou une demande de permission. Toute tentative de lancement reçoit une annulation, sans ouvrir d'application ni demander d'autorisation réelle.

## Bac à sable client

Le bac à sable remplace temporairement l'application affichée par un autre `AppModel` et un contexte à préférences **en mémoire**. Aucun identifiant de Gist, jeton ou compte personnel n'y est copié. Les XP, amis et cours fictifs sont séparés des données réelles ; les champs permettent de régler XP total, XP du jour, durée de série et nombre initial de cours terminés. **Choisir les cours terminés** permet ensuite de cocher/décocher n'importe quelle leçon, **Tout terminer** ou **Tout décocher**, sans XP ni changement SRS. Le registre fictif est recalé pour qu'une ancienne séance fictive ne rétablisse pas les coches retirées. Les séances ordinaires peuvent y faire évoluer ces données fictives, sauf si le gel est actif.

La synchronisation réelle est suspendue : jobs UI annulés, travaux WorkManager annulés, contrôle avant les transports GitHub/OAuth et passerelle du modèle fictif qui refuse le réseau. Les réglages fictifs ne peuvent pas programmer de rappels Android. Les aperçus bloquent les actions ; les liens externes du contexte fictif ne s'ouvrent pas. Une requête réelle déjà expédiée avant l'activation ne peut pas être retirée de GitHub ; aucune donnée fictive n'est utilisée dans cette requête.

**Revenir aux données réelles** détruit le modèle fictif, rétablit le modèle initial et reprend les synchronisations autorisées par les préférences originales. Fermer le processus perd également les données fictives ; au prochain lancement de l'app, leur drapeau de pause est désactivé. Si le processus meurt, les workers restent suspendus jusqu'à ce prochain lancement. Le gel et le mode debug restent conservés.

Le changement de modèle recrée la navigation Compose : un champ de réponse non validé ou la position de scroll peut être réinitialisé. Les réponses réellement enregistrées et les données de progression restent conservées. Les tests de questions dans le menu, qui n'effectuent pas ce changement global, préservent la séance réelle sous l'overlay.

## Limites des diagnostics

Les profils et bilans sont des fixtures, pas des comptes GitHub de test. Ils servent à inspecter les composants natifs. La surimpression est volontairement visible et peut recouvrir un petit bout de contenu ; elle n'est pas affichée en usage normal. Les essais haptiques respectent les réglages de l'app et d'Android et ne constituent pas une calibration du moteur physique.

Validation exécutée et limites appareil : [VALIDATION.md](VALIDATION.md).
