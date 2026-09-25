package com.radiicall.music

import android.content.ComponentName
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.view.View
import android.widget.PopupMenu
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.common.util.concurrent.ListenableFuture
import com.radiicall.music.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val tracks = mutableListOf<Track>()
    private lateinit var adapter: TrackAdapter
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@registerForActivityResult

        uris.forEach { uri ->
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                try {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (_: SecurityException) { }
            }

            val uriText = uri.toString()
            if (tracks.none { it.uri == uriText }) {
                tracks += Track(uriText, displayName(uri))
            }
        }
        tracks.sortBy { it.title.lowercase() }
        persistLibrary()
        refreshUi()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.statusBarColor = ContextCompat.getColor(this, R.color.bg)
        window.navigationBarColor = ContextCompat.getColor(this, R.color.bg)

        loadLibrary()
        adapter = TrackAdapter(
            tracks = tracks,
            onClick = { index -> playIndex(index) },
            onMore = { index, anchor -> showTrackMenu(index, anchor) }
        )
        binding.trackList.layoutManager = LinearLayoutManager(this)
        binding.trackList.adapter = adapter

        binding.importButton.setOnClickListener { openPicker() }
        binding.emptyImportButton.setOnClickListener { openPicker() }
        binding.playPause.setOnClickListener {
            controller?.let { if (it.isPlaying) it.pause() else it.play() }
        }
        binding.next.setOnClickListener { controller?.seekToNextMediaItem() }
        binding.previous.setOnClickListener { controller?.seekToPreviousMediaItem() }

        setupAudioControls()
        connectController()
        refreshUi()
    }

    private fun openPicker() {
        picker.launch(arrayOf("audio/mpeg", "audio/*"))
    }

    private fun setupAudioControls() {
        val prefs = getSharedPreferences("radiicall_audio", MODE_PRIVATE)
        val bass = prefs.getInt("bass", 50)
        val treble = prefs.getInt("treble", 50)
        val volume = prefs.getInt("volume", 85)

        binding.bassSeek.progress = bass
        binding.trebleSeek.progress = treble
        binding.volumeSeek.progress = volume
        updateAudioLabels(bass, treble, volume)

        binding.bassSeek.setOnSeekBarChangeListener(simpleSeekListener { value ->
            binding.bassValue.text = "$value%"
            prefs.edit().putInt("bass", value).apply()
            sendAudioEffect(PlaybackService.ACTION_SET_BASS, value)
        })

        binding.trebleSeek.setOnSeekBarChangeListener(simpleSeekListener { value ->
            binding.trebleValue.text = if (value == 50) "0" else if (value > 50) "+${value - 50}" else "${value - 50}"
            prefs.edit().putInt("treble", value).apply()
            sendAudioEffect(PlaybackService.ACTION_SET_TREBLE, value)
        })

        binding.volumeSeek.setOnSeekBarChangeListener(simpleSeekListener { value ->
            binding.volumeValue.text = "$value%"
            prefs.edit().putInt("volume", value).apply()
            controller?.volume = value / 100f
        })
    }

    private fun simpleSeekListener(onChange: (Int) -> Unit) = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
            if (fromUser) onChange(progress)
        }
        override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
        override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
    }

    private fun updateAudioLabels(bass: Int, treble: Int, volume: Int) {
        binding.bassValue.text = "$bass%"
        binding.trebleValue.text = if (treble == 50) "0" else if (treble > 50) "+${treble - 50}" else "${treble - 50}"
        binding.volumeValue.text = "$volume%"
    }

    private fun sendAudioEffect(action: String, value: Int) {
        val intent = Intent(this, PlaybackService::class.java)
            .setAction(action)
            .putExtra(PlaybackService.EXTRA_VALUE, value)
        try {
            startService(intent)
        } catch (_: Exception) { }
    }

    private fun connectController() {
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayerUi()
                    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updatePlayerUi()
                    override fun onPlaybackStateChanged(playbackState: Int) = updatePlayerUi()
                })

                val prefs = getSharedPreferences("radiicall_audio", MODE_PRIVATE)
                controller?.volume = prefs.getInt("volume", 85) / 100f
                sendAudioEffect(PlaybackService.ACTION_SET_BASS, prefs.getInt("bass", 50))
                sendAudioEffect(PlaybackService.ACTION_SET_TREBLE, prefs.getInt("treble", 50))
                updatePlayerUi()
            } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun playIndex(index: Int) {
        val c = controller ?: return
        val items = tracks.map {
            MediaItem.Builder()
                .setMediaId(it.uri)
                .setUri(Uri.parse(it.uri))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(it.title)
                        .setArtist("Radiicall Music")
                        .build()
                )
                .build()
        }
        c.setMediaItems(items, index, 0L)
        c.prepare()
        c.play()
    }

    private fun showTrackMenu(index: Int, anchor: View) {
        if (index !in tracks.indices) return
        PopupMenu(this, anchor).apply {
            menu.add("Remover da biblioteca")
            menu.add("Excluir arquivo do celular")
            setOnMenuItemClickListener { item ->
                when (item.title.toString()) {
                    "Remover da biblioteca" -> {
                        removeFromLibrary(index)
                        true
                    }
                    "Excluir arquivo do celular" -> {
                        confirmDeleteFile(index)
                        true
                    }
                    else -> false
                }
            }
            show()
        }
    }

    private fun removeFromLibrary(index: Int) {
        if (index !in tracks.indices) return
        tracks.removeAt(index)
        persistLibrary()
        refreshUi()
        Toast.makeText(this, "Música removida da biblioteca", Toast.LENGTH_SHORT).show()
    }

    private fun confirmDeleteFile(index: Int) {
        if (index !in tracks.indices) return
        val track = tracks[index]
        AlertDialog.Builder(this)
            .setTitle("Excluir música?")
            .setMessage("O arquivo “${track.title}” será excluído do celular quando o Android permitir. Esta ação não pode ser desfeita.")
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Excluir") { _, _ -> deleteFile(index) }
            .show()
    }

    private fun deleteFile(index: Int) {
        if (index !in tracks.indices) return
        val uri = Uri.parse(tracks[index].uri)
        try {
            val deleted = DocumentsContract.deleteDocument(contentResolver, uri)
            if (deleted) {
                tracks.removeAt(index)
                persistLibrary()
                refreshUi()
                Toast.makeText(this, "Arquivo excluído", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "O Android não permitiu excluir este arquivo", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(
                this,
                "Não foi possível excluir do aparelho. Use “Remover da biblioteca” ou exclua pelo gerenciador de arquivos.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun updatePlayerUi() {
        runOnUiThread {
            val c = controller
            val item = c?.currentMediaItem
            val title = item?.mediaMetadata?.title?.toString()
            binding.miniPlayer.visibility = if (item == null) View.GONE else View.VISIBLE
            binding.nowPlayingTitle.text = title ?: "Nenhuma música"
            binding.nowPlayingSubtitle.text = if (c?.isPlaying == true) "Tocando agora" else "Pausado"
            binding.playPause.setImageResource(
                if (c?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play
            )
        }
    }

    private fun refreshUi() {
        if (::adapter.isInitialized) adapter.refresh()
        val empty = tracks.isEmpty()
        binding.emptyState.visibility = if (empty) View.VISIBLE else View.GONE
        binding.trackList.visibility = if (empty) View.GONE else View.VISIBLE
        binding.libraryCount.text = "${tracks.size} música${if (tracks.size == 1) "" else "s"}"
    }

    private fun displayName(uri: Uri): String {
        var name = "Música MP3"
        val cursor: Cursor? = contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0) name = it.getString(idx)
            }
        }
        return name.removeSuffix(".mp3").removeSuffix(".MP3")
    }

    private fun persistLibrary() {
        val encoded = tracks.map { "${it.uri}\u0001${it.title}" }.toSet()
        getSharedPreferences("radiicall_music", MODE_PRIVATE)
            .edit().putStringSet("library", encoded).apply()
    }

    private fun loadLibrary() {
        val prefs = getSharedPreferences("radiicall_music", MODE_PRIVATE)
        var stored = prefs.getStringSet("library", emptySet()) ?: emptySet()

        // Migração simples caso o usuário tenha dados da versão anterior no mesmo pacote.
        if (stored.isEmpty()) {
            stored = getSharedPreferences("vmb_music", MODE_PRIVATE)
                .getStringSet("library", emptySet()) ?: emptySet()
        }

        tracks.clear()
        stored.forEach { value ->
            val parts = value.split("\u0001", limit = 2)
            if (parts.size == 2) tracks += Track(parts[0], parts[1])
        }
        tracks.sortBy { it.title.lowercase() }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onDestroy()
    }
}
