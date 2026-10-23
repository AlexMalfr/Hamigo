# Hamigo — app access for Google Play review

Hamigo is a French amateur-radio learning app. Learning works without an account. Its optional GitHub integration requests the gist and user OAuth scopes. Hamigo reads GitHub blocks but never edits the profile or changes blocks. It synchronizes learning data through two unlisted Gists and exchanges friend requests through Gist comments.

## Reusable demonstration access

No GitHub account, password, one-time code, developer intervention or specific location is needed for this demonstration. The reusable access code is:

**691427083519640782135906**

The interface is in French. The instructions below give the exact visible labels, with English explanations.

### Recommended route from a fresh installation

1. On **Ton pseudo** (nickname), enter any nickname, then tap **Continuer** (Continue). The short notice beside this button announces the Exam’1 background download and its size; downloading starts after this action.
2. Keep the proposed daily goal, then tap **Continuer**.
3. On the reminder page, select **Pas maintenant** (Not now). Notifications are optional.
4. Keep the proposed Morse input settings and tap **Continuer**.
5. If Android displays a microphone permission prompt, you may deny it. Select **Continuer sans microphone** (Continue without microphone). Microphone permission is optional.
6. On **6/6 · GitHub**, tap **Commencer sans compte** (Start without an account).
7. Tap **Passer le tutoriel** (Skip tutorial), confirm **Passer le tutoriel**, then tap **Commencer** (Start) on Pico’s final welcome card. The app is now on **Parcours** (Learning path).
8. Select **Mémo** (second bottom tab from the left). Tap the turquoise calculator button at the bottom right, whose accessibility label is **Ouvrir la calculatrice** (Open calculator). Opening a memo sheet is not necessary.
9. Enter or paste the exact digits **691427083519640782135906** in the calculator’s expression field, then press **=**. Select **Entrer dans la démonstration** (Enter demonstration) in the confirmation sheet.
10. The **DÉMONSTRATION — Données fictives · GitHub simulé** banner identifies the demo. It opens the populated **Équipe** (Team) screen as Camille. No GitHub account is required.

### If onboarding has already been completed

Select **Mémo**, open its calculator, enter the exact code and press **=**, then confirm **Entrer dans la démonstration**. If the introductory tutorial is active, first tap **Passer le tutoriel**, confirm, and tap **Commencer** on its final welcome card.

The same code also works in any other in-app calculator. There is no visible demonstration entry in onboarding or Settings. The older Diagnostics unlock code is not needed.

The code is deliberately public. It grants access only to isolated fictitious data, never to a real GitHub account or another user’s data. The demonstration remains active after restarting the app. Tap **Quitter** in the banner to return to ordinary local data. You may enter it again with the same code.

**Quitter** returns to the ordinary profile on **Parcours**. The onboarding and tutorial completed in the steps above remain completed.

## Privacy, rules, licenses and deletion controls

All these entries can be reached without authorizing a real GitHub account. In the demo, use **Moi → Réglages**; outside the demo, the same settings entries are available after completing or skipping onboarding.

| Item | Exact French path or label | Public resource |
| --- | --- | --- |
| Privacy policy | **Moi → Réglages → Données et confidentialité → Politique de confidentialité**. Also available by expanding **Données et confidentialité** on onboarding step **6/6 · GitHub**. | [Privacy policy](https://alexandre.malfre.yt/projects/Hamigo/confidentialite.html) |
| Team rules | **Moi → Réglages → Données et confidentialité → Règles de l’équipe** | [Team rules](https://alexandre.malfre.yt/projects/Hamigo/conditions.html) |
| Hamigo data on GitHub | **Moi → Réglages → Mes données → Supprimer mes données sur GitHub** | [Deletion instructions](https://alexandre.malfre.yt/projects/Hamigo/donnees.html) |
| Open-source licenses and attribution | **Moi → Réglages → Licences et attributions**. Select a document to read its complete text offline. | [Source repository](https://github.com/AlexMalfr/Hamigo) |

Deleting Hamigo’s Gists removes its backup and social summary, including their history and comments. It disconnects Hamigo and retains learning data on the device. It does not delete the user’s GitHub account. In the demonstration, this action affects only fictitious Gists.

To inspect the ordinary consent flow, leave the demo, open **Équipe**, then choose **Se connecter avec GitHub** (Sign in with GitHub). **Avant de rejoindre l’équipe** shows the team rules; **Accepter et continuer** opens the separate **Tes données sur GitHub** disclosure. **Sans connexion** declines data authorization. Only **Autoriser et continuer** starts the real GitHub authorization flow. The same two disclosures are shown if GitHub sign-in is selected during onboarding; declining or canceling there keeps the onboarding page open.

## Features to exercise

- **Équipe**: refresh the team, open Léa’s profile, inspect daily and weekly statistics, accept Sam’s incoming request, remove a friend, or use the invitation/QR controls.
- For a reciprocal request, open an existing friend’s three-dot menu and select **Demander l’ajout en retour**, then confirm **Envoyer**. A fictitious friend accepts through the same inbox parser used in normal operation.
- **Moi → Réglages**: use **Synchroniser maintenant**. Learning and preference changes are saved to the local demo service through the app’s normal synchronization and merge implementation.
- **Moi → Réglages → Mes données**: select **Supprimer mes données sur GitHub**, wait for the five-second confirmation countdown, then confirm **Supprimer**. This deletes the demo service’s Hamigo Gists, disconnects the fictitious account, and retains its local learning data. Reconnecting in the demo recreates its backups.
- **Parcours**, **Mémo**, **Défis** and **Moi** remain the ordinary interactive screens. They use separate demonstration preferences and progress.
- Licenses are available offline in **Réglages → Licences et attributions**; privacy and deletion instructions are also linked from the settings.

Native lessons and memo pages are included in the app. The separate Exam’1 question bank downloads from the REF servers during initial setup and needs internet access for that first download. It remains usable offline afterwards; its status and manual update control are available in settings. The demonstration does not replace that bank with bundled copies.

## Scope of the demonstration

This is explicitly a local simulation of the GitHub service. It exercises the production client screens, synchronization, merge rules, inbox and data deletion against persistent fictitious Gists. It does **not** authenticate a real GitHub account, contact the GitHub API, send real Gist comments or emails, or block/report actual GitHub accounts. External GitHub account actions show an explanatory notice instead. Ordinary GitHub OAuth and the actual GitHub block/report destinations remain available after leaving the demonstration.

Personal progress, preferences, friends and credentials are not copied into the demonstration. Personal synchronization is paused while it is active. **Quitter** restores the ordinary profile; no personal data reset or uninstall is required.

The older Diagnostics menu is a separate component inspector with temporary fixtures. It is not the demonstration account described above. Please do not submit test support forms or reports against real GitHub users.

## Publisher setup before submission

Paste the access code and English access instructions into **Play Console → Policy and programs → App content → Sign-in details / App access**. Also enter the privacy URL in Play Console’s **Privacy Policy** field and the external deletion URL in the applicable deletion field; these are direct links, in addition to the in-app entries above. Preparing this document does not populate or submit Play Console.

Google requires [review access details in English](https://support.google.com/googleplay/android-developer/answer/15748846?hl=en) and a [privacy policy linked from Play Console and the app](https://support.google.com/googleplay/android-developer/answer/10144311?hl=en). These instructions do not assume that a particular reviewer is a native English speaker. They describe the French interface explicitly and disclose the simulated GitHub service.

GitHub block decisions are read during synchronization. Blocked accounts are removed from the team and their incoming requests are filtered; an older backup cannot restore them. Unblocking on GitHub does not automatically add a friend again. The demonstration uses the same filtering logic with its local GitHub service.
