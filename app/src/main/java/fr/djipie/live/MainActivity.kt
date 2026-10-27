package fr.djipie.live

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.util.ArrayDeque
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : Activity() {
    companion object {
        private const val REQUEST_AUTHORIZE = 41
        private const val YOUTUBE_READ = "https://www.googleapis.com/auth/youtube.readonly"
        private const val MAX_QUEUED = 12
    }

    private val ui = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val queue = ArrayDeque<ChatLine>()
    private val alreadySeen = LinkedHashSet<String>()
    private lateinit var status: TextView
    private lateinit var lastMessage: TextView
    private lateinit var startButton: Button
    private lateinit var pauseButton: Button
    private lateinit var questionCheck: CheckBox
    private lateinit var tts: TextToSpeech
    private var speechReady = false
    private var speaking = false
    private var paused = false
    @Volatile private var monitoring = false
    @Volatile private var runId = 0
    @Volatile private var accessToken: String? = null
    private var spokenCount = 0
    private var questionsOnly = false
    private var initialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        questionsOnly = prefs.getBoolean("questions", false)
        buildScreen()
        tts = TextToSpeech(this) { result ->
            ui.post {
                speechReady = result == TextToSpeech.SUCCESS
                if (speechReady) {
                    tts.language = Locale.FRANCE
                    tts.setSpeechRate(prefs.getInt("speed", 100) / 100f)
                    tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(id: String?) = Unit
                        override fun onDone(id: String?) = finishedSpeaking()
                        override fun onError(id: String?) = finishedSpeaking()
                    })
                    initialized = true
                    showStatus("Voix prête. Tu peux essayer la lecture !")
                } else showStatus("La synthèse vocale n'est pas disponible sur cet appareil.")
            }
        }
    }

    private fun dp(value: Int) = (resources.displayMetrics.density * value + .5f).toInt()

    private fun buildScreen() {
        val bg = Color.rgb(8, 15, 36)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }
        val scroll = ScrollView(this).apply { fillViewport = true }
        scroll.addView(root)
        setContentView(scroll)

        fun label(value: String, size: Float, bold: Boolean = false): TextView =
            TextView(this).apply {
                text = value
                textSize = size
                setTextColor(Color.WHITE)
                if (bold) typeface = Typeface.DEFAULT_BOLD
                setPadding(0, dp(5), 0, dp(9))
            }

        fun addButton(text: String, handler: () -> Unit): Button =
            Button(this).apply {
                this.text = text
                textSize = 18f
                isAllCaps = false
                setTextColor(Color.WHITE)
                setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.rgb(36, 92, 154)))
                minHeight = dp(58)
                contentDescription = text
                setOnClickListener { handler() }
                root.addView(this, LinearLayout.LayoutParams(-1, dp(62)).apply { bottomMargin = dp(12) })
            }

        root.addView(label("🎙️ DjipieLive", 30f, true))
        root.addView(label("Compagnon vocal pour les directs YouTube", 18f))
        status = label("Initialisation de la voix…", 19f, true).apply {
            setTextColor(Color.rgb(83, 225, 220))
            setPadding(dp(12), dp(15), dp(12), dp(15))
            setBackgroundColor(Color.rgb(21, 42, 65))
            setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE)
        }
        root.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(15) })
        addButton("🔊 Écouter un message d'essai") {
            offer(ChatLine("demo" + System.nanoTime(), "Camille", "Bonjour ! Comment fonctionne Max YutNori ?"))
            if (!speechReady) showStatus("La voix n'est pas encore prête.")
        }
        addButton("🔐 Autoriser l'accès YouTube") { authorize() }
        startButton = addButton("▶ Démarrer la lecture du direct") { startMonitor() }
        pauseButton = addButton("⏸ Mettre la lecture en pause") {
            paused = !paused
            if (paused) {
                tts.stop()
                speaking = false
                showStatus("Lecture en pause. Les messages peuvent encore arriver.")
            } else {
                showStatus("Lecture reprise.")
                readNext()
            }
            pauseButton.text = if (paused) "▶ Reprendre la lecture" else "⏸ Mettre la lecture en pause"
        }
        addButton("■ Arrêter le direct") { stopMonitor(); showStatus("Lecture arrêtée.") }

        questionCheck = CheckBox(this).apply {
            text = "Lire uniquement les questions"
            textSize = 19f
            setTextColor(Color.WHITE)
            isChecked = questionsOnly
            contentDescription = "Lire uniquement les questions"
            setPadding(0, dp(9), 0, dp(12))
            setOnCheckedChangeListener { _, checked ->
                questionsOnly = checked
                getSharedPreferences("settings", MODE_PRIVATE).edit().putBoolean("questions", checked).apply()
                if (checked) queue.removeAll { !it.isQuestion }
            }
        }
        root.addView(questionCheck)

        root.addView(label("Vitesse de la voix", 19f, true))
        val currentSpeed = label("", 17f)
        root.addView(currentSpeed)
        val seek = SeekBar(this).apply {
            max = 160
            min = 70
            progress = getSharedPreferences("settings", MODE_PRIVATE).getInt("speed", 100)
            contentDescription = "Vitesse de la synthèse vocale"
        }
        fun speedText(progress: Int) {
            currentSpeed.text = "Vitesse : " + progress + " %"
            if (initialized) tts.setSpeechRate(progress / 100f)
        }
        speedText(seek.progress)
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                speedText(p)
                if (fromUser) getSharedPreferences("settings", MODE_PRIVATE).edit().putInt("speed", p).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) = Unit
            override fun onStopTrackingTouch(s: SeekBar?) = Unit
        })
        root.addView(seek, LinearLayout.LayoutParams(-1, dp(55)))

        root.addView(label("Dernier commentaire lu", 19f, true))
        lastMessage = label("Aucun pour l'instant.", 19f)
        root.addView(lastMessage)
        root.addView(label("Conseil : lance ton direct sur le premier téléphone, puis garde DjipieLive ouvert sur le deuxième.", 17f))
        addButton("Déconnecter Google") {
            stopMonitor()
            accessToken = null
            Identity.getAuthorizationClient(this).revokeAccess(
                com.google.android.gms.auth.api.identity.RevokeAccessRequest.builder()
                    .setScopes(listOf(Scope(YOUTUBE_READ))).build()
            ).addOnCompleteListener { showStatus("Accès Google déconnecté.") }
        }
    }

    private fun showStatus(message: String) { status.text = message }

    private fun authorize(after: (() -> Unit)? = null) {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(YOUTUBE_READ)))
            .build()
        Identity.getAuthorizationClient(this).authorize(request)
            .addOnSuccessListener { result ->
                if (result.hasResolution()) {
                    try {
                        startIntentSenderForResult(result.pendingIntent!!.intentSender, REQUEST_AUTHORIZE, null, 0, 0, 0)
                    } catch (_: IntentSender.SendIntentException) {
                        showStatus("Impossible d'ouvrir la connexion Google.")
                    }
                } else {
                    keepAuthorization(result)
                    after?.invoke()
                }
            }
            .addOnFailureListener { showStatus("Connexion impossible. Vérifie la configuration OAuth et les services Google.") }
    }

    @Deprecated("API Android historique utilisée pour le PendingIntent OAuth")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_AUTHORIZE) {
            if (resultCode != RESULT_OK || data == null) {
                showStatus("Autorisation Google annulée.")
                return
            }
            try {
                keepAuthorization(Identity.getAuthorizationClient(this).getAuthorizationResultFromIntent(data))
            } catch (_: Exception) {
                showStatus("L'autorisation Google n'a pas abouti.")
            }
        }
    }

    private fun keepAuthorization(result: AuthorizationResult) {
        accessToken = result.accessToken
        showStatus(if (accessToken.isNullOrBlank()) "Aucun jeton Google reçu." else "YouTube autorisé. Tu peux démarrer la lecture.")
    }

    private fun startMonitor() {
        if (monitoring) { showStatus("La lecture du direct est déjà active."); return }
        if (accessToken.isNullOrBlank()) { authorize(); showStatus("Autorise d'abord YouTube, puis appuie sur Démarrer."); return }
        monitoring = true
        val current = ++runId
        alreadySeen.clear()
        queue.clear()
        spokenCount = 0
        showStatus("Recherche de ton direct YouTube en cours…")
        executor.execute {
            try {
                val chat = YouTubeClient.currentChat(accessToken ?: return@execute)
                ui.post {
                    if (!monitoring || runId != current) return@post
                    if (chat == null) {
                        stopMonitor()
                        showStatus("Aucun direct actif avec chat trouvé. Démarre le live sur YouTube, puis réessaie.")
                    } else {
                        showStatus("Direct trouvé : " + chat.title + ". Connexion au chat…")
                        poll(current, chat.chatId, null, true, 0)
                    }
                }
            } catch (ex: Exception) {
                ui.post { if (runId == current) {
                    stopMonitor()
                    showStatus("Recherche impossible : " + friendlyError(ex))
                } }
            }
        }
    }

    private fun poll(current: Int, chatId: String, token: String?, initial: Boolean, failures: Int) {
        if (!monitoring || runId != current) return
        executor.execute {
            try {
                val batch = YouTubeClient.messages(accessToken ?: return@execute, chatId, token)
                ui.post {
                    if (!monitoring || runId != current) return@post
                    batch.items.forEach { line ->
                        if (alreadySeen.add(line.id) && !initial) offer(line)
                    }
                    while (alreadySeen.size > 350) alreadySeen.remove(alreadySeen.first())
                    if (initial) showStatus("Chat connecté. Les nouveaux commentaires seront lus.")
                    ui.postDelayed({ poll(current, chatId, batch.nextPageToken, false, 0) }, batch.waitMs)
                }
            } catch (ex: Exception) {
                ui.post {
                    if (!monitoring || runId != current) return@post
                    if (ex is ApiFailure && (ex.code == 401 || ex.code == 403 || ex.code == 404)) {
                        stopMonitor()
                        showStatus(friendlyError(ex))
                    } else {
                        val n = (failures + 1).coerceAtMost(5)
                        val wait = 8000L * n
                        showStatus("Connexion momentanément interrompue. Nouvelle tentative dans " + wait / 1000 + " secondes.")
                        ui.postDelayed({ poll(current, chatId, token, false, n) }, wait)
                    }
                }
            }
        }
    }

    private fun friendlyError(ex: Exception): String = when (ex) {
        is ApiFailure -> when (ex.code) {
            401 -> "Session Google expirée. Autorise YouTube à nouveau."
            403 -> "Accès refusé ou chat inactif. Vérifie l'accès API et le direct."
            404 -> "Chat introuvable. Vérifie que le direct est toujours actif."
            429 -> "Limite YouTube atteinte. Réessai automatique."
            else -> "Erreur YouTube HTTP " + ex.code
        }
        else -> "Réseau indisponible ou réponse inattendue."
    }

    private fun offer(line: ChatLine) {
        if (!ChatPolicy.shouldRead(line, questionsOnly)) return
        if (queue.size >= MAX_QUEUED) queue.removeFirst()
        queue.addLast(line)
        readNext()
    }

    private fun readNext() {
        if (!speechReady || speaking || paused || queue.isEmpty()) return
        val important = queue.firstOrNull { it.isQuestion }
        val line = important ?: queue.first()
        queue.remove(line)
        speaking = true
        spokenCount += 1
        lastMessage.text = line.author + " : " + line.text
        if (monitoring) showStatus("Lecture active — " + spokenCount + " commentaire(s) lu(s).")
        val spoken = line.author + " demande : " + line.text
        val result = tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "chat-" + System.nanoTime())
        if (result != TextToSpeech.SUCCESS) {
            speaking = false
            showStatus("La synthèse vocale a échoué.")
        }
    }

    private fun finishedSpeaking() {
        ui.post {
            speaking = false
            ui.postDelayed({ readNext() }, 1600L)
        }
    }

    private fun stopMonitor() {
        monitoring = false
        runId += 1
        queue.clear()
        alreadySeen.clear()
        speaking = false
        tts.stop()
    }

    override fun onDestroy() {
        monitoring = false
        runId += 1
        executor.shutdownNow()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}
