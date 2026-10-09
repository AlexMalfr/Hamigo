# Sons, vibrations et confettis

La 0.44 ajoute une palette courte de feedback, centralisée dans `AppFeedback` et `FeedbackDesign`.

## Moments retenus

| Interaction | Son | Vibration |
|---|---|---|
| Bouton, onglet, petit réglage | Déclic très discret | Aucune par défaut |
| Sélection d'une réponse | Déclic atténué | Impulsion douce |
| Prise/déplacement/assemblage d'une carte | Déclic borné en fréquence | Prise, petit cran, puis pose |
| Réponse juste | Deux cordes pincées ascendantes | Deux impulsions, seconde plus ferme |
| Réponse à corriger | Deux frappes sourdes | Deux impulsions légères |
| Fin de séance validée | Courte résonance ascendante | Trois impulsions alignées sur les attaques |
| Fin à consolider | Accord plus sobre | Deux impulsions douces |
| Grimace de Pico et surprise de Version | Petit ressort élastique | Rebond doux, temporisé |

Les évaluations de flashcards ont un feedback de pose, sans verdict sonore de justesse. Les brouillons d'examen ne produisent **aucun indice sonore/haptique de bonne ou mauvaise réponse**. Le bilan conserve les règles de réussite existantes.

Les actions ordinaires passent par `feedbackClick`, les gestes utiles par `feedbackAction` ou un événement explicite. Un verdict remplace le déclic de son bouton. Les petits événements sont espacés d'au moins 90 ms et les réactions de Pico de 600 ms ; aucun son n'est émis par ses expressions automatiques, par le scroll ou par chaque mouvement du stylet. Les relectures/recompositions n'émettent pas de nouveau verdict.

## Palette sonore originale

Six WAV mono 16 bits à 32 kHz, 80 à 980 ms, représentent environ **175 Ko**. Ils sont préchargés dans SoundPool ; aucune bibliothèque sonore, aucun téléchargement ni synthèse audio en temps réel n'est nécessaire pour les interactions.

`tools/make_ui_sounds.py` recrée les fichiers avec Python et NumPy : contacts irréguliers excitant un boîtier amorti, cordes pincées par modèle Karplus–Strong et délai élastique pour Pico. Il s'agit de créations par modélisation physique et bruit filtré, **pas d'enregistrements de matériel réel**, ni d'une succession de sinus purs ou de samples provenant d'une banque tierce. Le Morse pédagogique conserve évidemment son signal tonal.

Le contrôleur coupe les effets en arrière-plan ou lorsque le volume média est nul. Les sons suivent le volume média (usage GAME), indépendamment du mode de sonnerie, avec le mute propre à Hamigo. Le Morse et les voix réservent la priorité audio et interrompent les sons d’interface ; ceux-ci ne sont pas mis en attente pour être joués tardivement. Les petits clics ne prennent pas le focus audio et n'interrompent donc pas une musique extérieure.

## Réglages et compatibilité

« Sons » et « Vibrations » sont côte à côte dans Gameplay. Le bouton audio à droite du drapeau d'une question partage le réglage Sons. Ce réglage concerne l'interface : les lectures pédagogiques explicitement demandées restent disponibles.

Ces deux préférences restent locales à l'appareil, conservées entre mises à jour ; elles ne modifient pas le schéma des Gists ni les choix audio d'un autre téléphone. Le rendu tactile varie avec le moteur et les réglages Android. Les patterns ont des amplitudes variables lorsque le matériel le permet, sinon une courte séquence compatible ; ils respectent le réglage système de retour tactile. Permission Android normale `VIBRATE`, sans popup.

Références d'implémentation : [haptique Android](https://developer.android.com/develop/ui/views/haptics/haptics-apis), [SoundPool](https://developer.android.com/reference/android/media/SoundPool), [attributs audio](https://developer.android.com/reference/android/media/AudioAttributes). Un volume média à zéro et le mute Sons sont toujours respectés.

## Fin de séance

Deux gerbes de papier partent du bas, dans la palette turquoise/corail/doré, avec rotation, variation d'orientation, gravité et disparition. Les morceaux mesurent 7 à 13 dp et montent plus haut avec une dispersion plus large et une rotation lente. L'effet dure 4,2 secondes, puis le Canvas disparaît ; il n'intercepte aucun toucher. Il est joué une seule fois par séance, avec une version moins fournie pour une séance à consolider. Une animation désactivée dans Android est respectée, et passer en arrière-plan interrompt la présentation.

## Démarrage du Morse

Toutes les écoutes (questions, Mémo, traducteur et composition) utilisent le même tampon : **180 ms de souffle avant le premier signal**, souffle très bas pendant les pauses/tons et extinction douce. La durée d'un point reste 90 ms, celle d'un trait trois points, les espaces un/trois/sept points. Le début ne dépend pas de l'arrivée tardive d'un premier buffer tonal.

Ce préambule réduit le risque de perdre le premier point sur un chemin audio qui s'ouvre lentement. Il ne constitue pas une mesure de latence ni une garantie sur tous les haut-parleurs et accessoires Bluetooth ; ces derniers nécessitent une écoute physique sur le matériel concerné.
