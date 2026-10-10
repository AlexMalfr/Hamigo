# Banque Exam1 et stockage — 0.48

## Distribution

La banque n’est plus dans les assets Android ni dans l’état actuel du dépôt Git. Les anciennes releases et l’historique restent conservés, à la demande de l’utilisateur. Les copies de développement sont ignorées sous `.tools/` ; les crédits et les scripts reproductibles restent publics.

Le lecteur est écrit pour Hamigo, après consultation du fonctionnement de [l’application Exam1RA](https://github.com/Maxime-Favier/Exam1RA). Il consomme directement les deux ressources de [Exam1 Web / REF](https://exam1.r-e-f.org/) :

- `https://exam1.r-e-f.org/assets/questions.json` : version, thèmes, IDs numériques, énoncés, réponses et commentaires.
- `https://exam1.r-e-f.org/assets/questions.zip` : PNG originaux.

Les réponses ne sont pas modifiées. Les exclusions historiques et la divergence connue de 23814 restent appliquées localement. Les IDs numériques source restent les clés de SRS, indépendamment de la date de la banque ; les questions originales Hamigo conservent leurs UUID.

## Téléchargement et mises à jour

Un travail WorkManager connecté démarre dès l’initialisation de l’app, y compris pendant l’accueil. Aucune barre n’est ajoutée à l’accueil. Si la banque n’est pas encore prête, Défis affiche sa progression et désactive seulement l’examen et le mix Exam1. Parcours, Mémo, révisions disponibles et labo restent utilisables.

Une vérification quotidienne est programmée avec Internet et une batterie suffisante ; Android peut la décaler. L’ouverture de l’app reprend un téléchargement manquant et vérifie une banque dont le dernier contrôle date de plus d’un jour. Les paramètres affichent la version publiée par la source, la dernière vérification et un bouton de vérification manuelle. La vérification manuelle ne retélécharge pas une archive dont les métadonnées n’ont pas changé. Une correction du JSON seule réutilise également les illustrations vérifiées.

Le JSON emploie `If-None-Match` quand le serveur fournit un ETag. Les métadonnées de l’archive sont comparées pour détecter aussi une modification d’image sans modification du JSON. Le téléchargement est silencieux si une banque utilisable existe déjà. Une panne de réseau ou une source incompatible conserve cette dernière. Les tentatives initiales utilisent un backoff exponentiel ; une nouvelle ouverture ou une demande manuelle permettent de réessayer.

## Stockage et intégrité

Une génération contient le JSON brut et **une archive ZIP compressée**, sous `noBackupFilesDir/exam-bank/`. Il n’y a pas de deuxième copie extraite de chaque image. L’espace temporaire sert à préparer la mise à jour ; la publication du pointeur courant est atomique.

Contrôles : taille des téléchargements, structure/count/IDs/réponses du JSON, nombre et noms stricts des entrées ZIP, absence de chemins et doublons, tailles décompressées bornées, CRC de toutes les entrées, signature/dimensions PNG et présence de l’image de chaque question. Les empreintes SHA-256 détectent une corruption locale ; elles ne remplacent pas une signature éditoriale de la source. Les téléchargements viennent du serveur HTTPS officiel.

Les images d’une séance désignent sa génération. Une mise à jour n’en remplace pas les images sous les pieds du joueur. Les anciennes générations et dossiers interrompus sont nettoyés au prochain démarrage du processus, avant l’ouverture des séances. Une mise à jour peut donc conserver temporairement deux archives. Aucune progression ni préférence n’est déplacée ou supprimée ; les clés de révision indisponibles pendant un téléchargement restent enregistrées.

Le traitement d’image demeure local et dynamique : dérivation de l’aperçu, fond transparent, recadrage et suppression prudente des pixels isolés. L’agrandissement affiche toujours le PNG original. Un cache mémoire de 8 Mio maximum réutilise originales/aperçus ; il n’écrit pas une collection de PNG dérivés sur disque.

## Optimisation de la release

Les anciennes releases étaient bien signées en release et non débogables, mais R8 était désactivé. Le diagnostic de la 0.47 mesure 54 951 189 octets d’APK : environ 40,8 Mo de banque, 12,5 Mo de DEX compressés (46 Mo décompressés), moins de 1,2 Mo de contenu Hamigo brut. La bibliothèque Material Icons entière représentait 11 400 classes avant suppression du code inutilisé.

La 0.48 active R8 et la réduction intégrée des ressources avec AGP 9.0.1. Les workers persistés gardent leurs noms et constructeurs ; le constructeur ViewModel reste accessible. Les lectures du contenu Hamigo et les variantes sont réutilisées quand seule la banque change. Les mesures finales d’APK, de DEX, de stockage Android et les vérifications réellement exécutées sont consignées dans `docs/VALIDATION.md` et les preuves locales ignorées `output/storage-0.48-*`.

## Tests reproductibles

`tools/import_exam1.ps1 -Download` archive les sources sous `.tools/exam1-sources/` et produit une normalisation d’audit sous `.tools/exam1-normalized/`, sans asset d’application. Après installation du debug sur l’émulateur, `tools/prepare_exam1_tests.ps1` prépare la fixture complète dans son stockage privé. Ce script refuse un téléphone physique. La banque complète n’est incluse ni dans l’APK de tests ni dans Git ; les scénarios de téléchargement emploient aussi une petite banque synthétique et un serveur HTTP local.

Les diagnostics en mémoire n’effectuent pas de téléchargement manuel, et le transport est suspendu quand le bac à sable est actif. Aucun token GitHub n’est requis pour cette banque publique.

Le contrôle de la vraie release utilise [un petit instrument Android indépendant](../tools/release-probe/README.md), pour ne pas conserver artificiellement les API de test ou le Kotlin renommé dans l’app optimisée. La base interne générée de WorkManager garde son constructeur utilisé par Room.
