# Sons, vibrations et confettis

La 0.44 ajoute une palette courte de feedback, centralisée dans `AppFeedback` et `FeedbackDesign`.

## Moments retenus

| Interaction | Son | Vibration |
|---|---|---|
| Bouton, onglet, petit réglage | Déclic très discret | Aucune par défaut |
| Sélection d'une réponse | Déclic atténué | Impulsion douce |
| Prise/déplacement/assemblage d'une carte | Déclic borné en fréquence | Prise, petit cran, puis pose |
| Slider de fréquence ou estimation | Aucun | Crans fins, repères plus fermes, butée et fin de geste |
| Retournement d'une flashcard | Déclic discret | Départ puis milieu du flip |
| Bit binaire, vrai/faux, composition Morse | Déclic discret | Impulsions selon la manipulation |
| Réponse juste | Deux cordes pincées ascendantes | Deux impulsions, seconde plus ferme |
| Réponse à corriger | Deux frappes sourdes | Deux impulsions légères |
| Fin de séance validée | Courte résonance ascendante | Trois impulsions alignées sur les attaques |
| Fin à consolider | Accord plus sobre | Deux impulsions douces |
| Grimace de Pico et surprise de Version | Petit ressort élastique | Rebond doux, temporisé |

Les évaluations de flashcards ont un feedback de pose, sans verdict sonore de justesse. Les brouillons d'examen ne produisent **aucun indice sonore/haptique de bonne ou mauvaise réponse**. Le bilan conserve les règles de réussite existantes.

La 0.45 distingue les gestes des formats : cran à chaque changement de position logique sur un slider, repère par dixième de son échelle, double impulsion aux extrémités, puis pose au relâchement. La cadence des crans est bornée à 36 ms et aucune vibration ne dépend de la proximité de la bonne réponse. Les sliders restent silencieux. Le flip de 360 ms a une impulsion de départ puis une autre autour du passage de tranche ; aucune vibration à la recomposition. Binaire actif/inactif, point/trait Morse, vrai/faux et franchissement d'une position dans un classement ont leurs impulsions distinctes. Les contrôles du menu [Diagnostics](DIAGNOSTICS.md) permettent d'essayer les principaux motifs.

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

Les lectures explicitement lancées (questions, Mémo, traducteur et relecture d'une composition) utilisent le même tampon : **180 ms de souffle avant le premier signal**, souffle très bas pendant les pauses/tons et extinction douce. La durée d'un point reste 90 ms, celle d'un trait trois points, les espaces un/trois/sept points. Le début ne dépend pas de l'arrivée tardive d'un premier buffer tonal.

Ce préambule réduit le risque de perdre le premier point sur un chemin audio qui s'ouvre lentement. Il ne constitue pas une mesure de latence ni une garantie sur tous les haut-parleurs et accessoires Bluetooth ; ces derniers nécessitent une écoute physique sur le matériel concerné.

## Écoute pendant la saisie — 0.46

Dans Gameplay → Saisie du Morse, « Écoute pendant la saisie » est activée par défaut. Elle suit aussi **Sons** (y compris le bouton de mute des questions) et le volume média. Deux boutons : chaque point/trait saisi joue 90/270 ms, avec une pause entre les signaux si les saisies sont rapprochées. Un bouton : le son commence au toucher et suit la durée réelle de l'appui, sans attendre sa classification en point/trait au relâchement. Les actions TalkBack jouent le signal choisi. Cela fonctionne aussi dans le traducteur et l'essai des paramètres, ainsi que dans les questions diagnostiques qui copient le réglage local.

Un AudioTrack en streaming mono 48 kHz prépare la sortie pendant que le contrôle est disponible au premier plan ; il reste silencieux hors transmission. Blocs de 5 ms, demande du mode faible latence et enveloppe de 6 ms autour du signal à 700 Hz. Aucun préambule de 180 ms par appui, aucune nouvelle piste créée à chaque point. La latence réelle dépend toujours de la sortie et du matériel, en particulier du Bluetooth : les tests logiciels ne la mesurent pas. Référence : [AudioTrack.Builder](https://developer.android.com/reference/android/media/AudioTrack.Builder).

Relâchement ou annulation du geste arrêtent le manipulateur. Le mute, la désactivation du contrôle, sa disparition et le passage en arrière-plan ferment la piste ; les écoutes pédagogiques et les voix ont priorité. Les clics d'interface sont suspendus pendant le signal, les retours tactiles restent disponibles. Un échec audio ne bloque pas la composition. Le nouveau choix sonore reste local à l'appareil comme Sons/Vibrations : il n'ajoute aucun champ au Gist, ne modifie pas son schéma ni l'horodatage des préférences cloud à lui seul, et ne requiert aucune migration.
