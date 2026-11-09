# LIA (OmniAgent) — Assistant Personnel Android Zero-Trust & Multimodal

**LIA** (*Local Intelligent Assistant* / **OmniAgent**) est un assistant personnel Android natif écrit en **Kotlin** et **Jetpack Compose** (Material 3). Il combine les modèles **Google Gemini 2026** (`gemini-3.8-flash`, `gemini-4-argon`, `gemini-3.5-flash-lite`, `gemini-embedding-2`), une mémoire locale persistante sous **Room SQLite v6**, un moteur **RAG hybride** (vecteurs 64D hors-ligne + embeddings sémantiques en ligne + BM25 + décroissance temporelle), un moteur d'apprentissage par renforcement (**Q-Learning** contextuel), une bulle flottante système (`SoniaOverlayBubbleService`) et un noyau de sécurité **Zero-Trust** (`SoniaSecurityKernel`).

---

## Architecture Principale

```
app/src/main/java/com/example/
├── MainActivity.kt                         # Navigation principale simplifiée (Chat LIA & Paramètres)
├── model/
│   └── AgentModels.kt                      # Modèles de domaine (ChatMessage, SecurityAuditEvent, GlobalAccessPolicyMode)
├── security/
│   ├── SoniaSecurityKernel.kt              # AES-256-GCM (AndroidKeyStore), PIN PBKDF2-HMAC-SHA256, pare-feu PII/Injection, chaîne SHA-256
│   ├── SafeContactResolver.kt              # Résolution anti-hallucination de contacts (Levenshtein + Dice bigrams)
│   └── AppCheckConfig.kt                   # Configuration Firebase App Check conditionnelle (Debug vs Play Integrity)
├── data/
│   ├── GeminiDualEngineClient.kt           # Client HTTP/SSE Gemini 2026 (x-goog-api-key, backoff exponentiel, embeddings)
│   ├── LiaAssistantBrain.kt                # Orchestrateur cognitif principal (RAG, RL, Intents, Gemini, Workspace)
│   ├── LiaLocalUtilitiesEngine.kt          # Utilitaires instantanés hors-ligne (calculatrice AST, devises, unités, horloge)
│   ├── LiaFactAndRuleExtractor.kt          # Extraction de faits utilisateur, règles conditionnelles, notes et macros
│   ├── LiaMemoryAndHistoryStore.kt         # Magasin réactif unifié basé sur Room SQLite v6
│   ├── LiaSouvenirRagEngine.kt             # Moteur RAG hybride (cache vectoriel ConcurrentHashMap + gemini-embedding-2 + BM25)
│   ├── LiaReinforcementLearningEngine.kt   # Q-Learning contextuel et adaptation de profil
│   ├── LiaGoogleCloudWorkspaceClient.kt    # Connecteur OAuth2 Google Workspace (Gmail, Calendar, Drive, Contacts, Tasks)
│   ├── LiaUniversalAppsAndGoogleHub.kt     # Découverte dynamique d'applications Android installées et deep-links
│   ├── OmniFirestoreRepository.kt          # Synchronisation cloud optionnelle avec assainissement PII
│   └── room/
│       └── LiaRoomDatabase.kt              # Base SQLite Room v6 avec index et migrations non-destructives (MIGRATION_1_6)
├── service/
│   ├── AppActionExecutor.kt                # Passerelle Zero-Trust centralisée pour tous les Intents Android (Chat + Bulle)
│   ├── IntentHandler.kt                    # Classification déterministe d'intents système (lampe, caméra, réveil, GPS, etc.)
│   ├── SoniaOverlayBubbleService.kt        # Service de bulle flottante système avec reconnaissance vocale et TTS
│   ├── LiaProactiveAutomationService.kt    # Automatisation proactive et routines planifiées
│   ├── LiaNotificationListenerService.kt   # Lecture et résumé sécurisé des notifications entrantes
│   ├── LiaAccessibilityService.kt          # Service d'accessibilité Android
│   └── LiaMcpConnectorRegistry.kt          # Registre de connecteurs Model Context Protocol (JSON-RPC 2.0 / HTTP)
├── viewmodel/
│   └── OmniAgentViewModel.kt               # État UI réactif, verrouillage PIN PBKDF2, confirmation HITL
└── ui/
    ├── screens/
    │   ├── OperatorConsoleScreen.kt        # Écran principal de conversation multimodale LIA
    │   └── LiaSettingsScreen.kt            # Centre de contrôle à 12 onglets (dont Coffre-Fort Zero-Trust)
    └── components/
        ├── LiaLiveDiagnosticAndControlDeck.kt
        ├── LiaDialogsAndModals.kt
        └── MarkdownTextComponent.kt
```

---

## Points Clés Techniques

### 1. Moteur IA Gemini 2026 (`GeminiDualEngineClient`)
- **Modèles supportés** :
  - `gemini-3.8-flash` (modèle multimodal et agentique rapide par défaut)
  - `gemini-4-argon` (modèle de raisonnement profond complexe)
  - `gemini-3.5-flash-lite` (modèle ultra-basse latence)
  - `gemini-embedding-2` (embeddings sémantiques en ligne pour le moteur RAG)
- **Sécurité réseau** : Authentification par en-tête `x-goog-api-key` (aucune clé exposée dans les URLs de requête) et retry automatique avec **backoff exponentiel** sur les codes HTTP `429` / `503`.

### 2. Sécurité Zero-Trust (`SoniaSecurityKernel` & `AppActionExecutor`)
- **Chiffrement matériel au repos** : Les clés API Gemini et les jetons OAuth2 Google Workspace sont chiffrés via `AES/GCM/NoPadding` (256 bits) avec une clé maîtresse stockée dans l'`AndroidKeyStore`.
- **Authentification PIN PBKDF2** : Le code PIN de sécurité est haché avec `PBKDF2WithHmacSHA256` (12 000 itérations + sel aléatoire de 16 octets via `SecureRandom`) et vérifié en temps constant (`MessageDigest.isEqual`).
- **Passerelle d'exécution unifiée (`AppActionExecutor`)** :
  - Applique les politiques `STANDARD_SAFE`, `STRICT_AUDIT` et `LOCKDOWN` aussi bien depuis le chat principal que depuis la bulle flottante `SoniaOverlayBubbleService`.
  - Liste blanche stricte de schémas URI (`tel:`, `smsto:`, `mailto:`, `geo:`, `https:`, `google.navigation:`).
  - Confirmation **Human-In-The-Loop (HITL)** obligatoire avant tout appel téléphonique, SMS, e-mail ou ouverture de lien sensible.
- **Protection contre les injections de prompt indirectes** : `sanitizeExternalRagContent()` neutralise les instructions malveillantes cachées dans les notifications ou contenus externes avant leur injection dans le contexte RAG.
- **Chaîne d'audit immuable** : Journalisation chaînée par hachage SHA-256 avec compteur atomique monotone (`AtomicInteger`).

### 3. Persistance Room v6 & RAG Hybride (`LiaRoomDatabase` & `LiaSouvenirRagEngine`)
- **Source unique de vérité** : Toutes les conversations, mémoires RAG, faits utilisateur, règles personnalisées, journaux d'exécution et la table Q-Learning sont stockés dans **Room SQLite v6** avec des index SQLite optimisés (`@Index`) et des migrations explicites (`MIGRATION_1_2` à `MIGRATION_5_6`).
- **RAG Hybride** : Combine un cache vectoriel en mémoire (`ConcurrentHashMap`) avec les embeddings en ligne `gemini-embedding-2` (avec repli automatique sur l'encodeur 64D local par trigrammes/unigrammes), le score lexical **BM25**, et une décroissance temporelle pondérée par l'importance.

---

## Configuration & Compilation

### Prérequis
- **Android Studio** Ladybug / Meerkat ou supérieur (AGP `8.7.3`, Kotlin `2.0.21`, JDK 17)
- **SDK Android** : `minSdk = 26` (Android 8.0), `targetSdk = 35` (Android 15)

### Clé API Gemini
Vous pouvez configurer votre clé API Google AI Studio :
1. Directement dans l'application via **Paramètres > Cerveau IA (Gemini)** (chiffrée au repos via `AndroidKeyStore` AES-256-GCM).
2. Ou au moment du build via la propriété Gradle `-PGEMINI_API_KEY="AIza..."` dans `~/.gradle/gradle.properties`.

### Commandes Gradle
```bash
# Compiler l'APK Debug
./gradlew assembleDebug

# Exécuter la suite de tests unitaires (JUnit 4 + Robolectric)
./gradlew testDebugUnitTest
```
