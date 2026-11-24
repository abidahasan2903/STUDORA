package com.example.core

import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.media.PlaybackParams
import android.os.Build
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.resume

data class RecordingState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedMs: Long = 0L,
    val amplitude: Int = 0,
    val activeFilePath: String? = null,
    val errorMessage: String? = null
)

data class PlaybackState(
    val playingFilePath: String? = null,
    val playingTitle: String = "",
    val isPlaying: Boolean = false,
    val currentPositionMs: Int = 0,
    val durationMs: Int = 0,
    val playbackSpeed: Float = 1.0f,
    val errorMessage: String? = null
)

data class TtsSynthesisProgress(
    val isSynthesizing: Boolean = false,
    val currentChunk: Int = 0,
    val totalChunks: Int = 0,
    val statusText: String = ""
)

class AudioService(
    private val context: Context,
    private val storage: LocalStorageManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var mediaRecorder: MediaRecorder? = null
    private var recordingTimerJob: Job? = null
    private var accumulatedRecordMs = 0L
    private var lastResumeTimestamp = 0L

    private val _recordingState = MutableStateFlow(RecordingState())
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackTickerJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _ttsProgress = MutableStateFlow(TtsSynthesisProgress())
    val ttsProgress: StateFlow<TtsSynthesisProgress> = _ttsProgress.asStateFlow()

    // ---- Physical Microphone Recording ----

    fun startRecording(): Result<File> {
        stopPlayback()
        releaseRecorder()
        if (!storage.hasEnoughSpace(2L * 1024L * 1024L)) {
            val err = "Insufficient storage space to record audio."
            _recordingState.value = RecordingState(errorMessage = err)
            return Result.failure(IOException(err))
        }

        val outFile = storage.createNewFile(storage.audioRecordingsDir, "voice", "m4a")
        return try {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outFile.absolutePath)
                prepare()
                start()
            }
            mediaRecorder = recorder
            accumulatedRecordMs = 0L
            lastResumeTimestamp = System.currentTimeMillis()
            _recordingState.value = RecordingState(
                isRecording = true,
                isPaused = false,
                elapsedMs = 0L,
                amplitude = 0,
                activeFilePath = outFile.absolutePath,
                errorMessage = null
            )
            startRecordingTicker()
            Result.success(outFile)
        } catch (e: Exception) {
            outFile.delete()
            releaseRecorder()
            val msg = "Microphone failed to start: ${e.localizedMessage ?: "Unknown error"}"
            _recordingState.value = RecordingState(errorMessage = msg)
            Result.failure(e)
        }
    }

    fun pauseRecording() {
        val recorder = mediaRecorder ?: return
        if (!_recordingState.value.isRecording || _recordingState.value.isPaused) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                recorder.pause()
                accumulatedRecordMs += System.currentTimeMillis() - lastResumeTimestamp
                recordingTimerJob?.cancel()
                _recordingState.value = _recordingState.value.copy(
                    isPaused = true,
                    elapsedMs = accumulatedRecordMs,
                    amplitude = 0
                )
            } catch (e: Exception) {
                _recordingState.value = _recordingState.value.copy(
                    errorMessage = "Pause failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun resumeRecording() {
        val recorder = mediaRecorder ?: return
        if (!_recordingState.value.isRecording || !_recordingState.value.isPaused) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                recorder.resume()
                lastResumeTimestamp = System.currentTimeMillis()
                _recordingState.value = _recordingState.value.copy(isPaused = false)
                startRecordingTicker()
            } catch (e: Exception) {
                _recordingState.value = _recordingState.value.copy(
                    errorMessage = "Resume failed: ${e.localizedMessage}"
                )
            }
        }
    }

    fun stopRecording(): Result<Pair<File, Long>> {
        val path = _recordingState.value.activeFilePath
        val recorder = mediaRecorder
        if (recorder == null || path == null) {
            return Result.failure(IllegalStateException("No active recording."))
        }
        recordingTimerJob?.cancel()
        val finalDuration = if (_recordingState.value.isPaused) {
            accumulatedRecordMs
        } else {
            accumulatedRecordMs + (System.currentTimeMillis() - lastResumeTimestamp)
        }
        return try {
            recorder.stop()
            releaseRecorder()
            val file = File(path)
            if (!file.exists() || file.length() == 0L) {
                file.delete()
                _recordingState.value = RecordingState(errorMessage = "Recorded audio file was empty.")
                Result.failure(IOException("Recorded audio file was empty."))
            } else {
                val measuredDuration = readMediaDurationMs(file).takeIf { it > 0L } ?: finalDuration
                _recordingState.value = RecordingState()
                Result.success(file to measuredDuration)
            }
        } catch (e: Exception) {
            File(path).delete()
            releaseRecorder()
            _recordingState.value = RecordingState(errorMessage = "Failed to stop recording: ${e.localizedMessage}")
            Result.failure(e)
        }
    }

    fun cancelRecording() {
        val path = _recordingState.value.activeFilePath
        recordingTimerJob?.cancel()
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) {
        }
        releaseRecorder()
        if (path != null) File(path).delete()
        _recordingState.value = RecordingState()
    }

    private fun startRecordingTicker() {
        recordingTimerJob?.cancel()
        recordingTimerJob = scope.launch {
            while (isActive) {
                val elapsed = accumulatedRecordMs + (System.currentTimeMillis() - lastResumeTimestamp)
                val amp = try {
                    mediaRecorder?.maxAmplitude ?: 0
                } catch (_: Exception) {
                    0
                }
                _recordingState.value = _recordingState.value.copy(
                    elapsedMs = elapsed,
                    amplitude = amp
                )
                delay(120)
            }
        }
    }

    private fun releaseRecorder() {
        try {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        } catch (_: Exception) {
        }
        mediaRecorder = null
    }

    // ---- Audio Playback (Local M4A & Synthesized AI WAV) ----

    fun playFile(filePath: String, title: String, speed: Float = 1.0f) {
        val file = File(filePath)
        if (!file.exists() || file.length() == 0L) {
            _playbackState.value = PlaybackState(errorMessage = "Audio file not found on device.")
            return
        }

        if (_playbackState.value.playingFilePath == filePath && mediaPlayer != null) {
            togglePlayPause()
            return
        }

        stopPlayback()
        try {
            val player = MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && speed != 1.0f) {
                    playbackParams = PlaybackParams().setSpeed(speed.coerceIn(0.5f, 2.0f))
                }
                setOnCompletionListener {
                    playbackTickerJob?.cancel()
                    _playbackState.value = _playbackState.value.copy(
                        isPlaying = false,
                        currentPositionMs = _playbackState.value.durationMs
                    )
                }
                setOnErrorListener { _, _, _ ->
                    stopPlayback()
                    _playbackState.value = PlaybackState(errorMessage = "Audio playback failed.")
                    true
                }
                start()
            }
            mediaPlayer = player
            _playbackState.value = PlaybackState(
                playingFilePath = filePath,
                playingTitle = title,
                isPlaying = true,
                currentPositionMs = 0,
                durationMs = player.duration.coerceAtLeast(1),
                playbackSpeed = speed,
                errorMessage = null
            )
            startPlaybackTicker()
        } catch (e: Exception) {
            stopPlayback()
            _playbackState.value = PlaybackState(
                errorMessage = "Unable to play audio: ${e.localizedMessage}"
            )
        }
    }

    fun togglePlayPause() {
        val player = mediaPlayer ?: return
        try {
            if (player.isPlaying) {
                player.pause()
                playbackTickerJob?.cancel()
                _playbackState.value = _playbackState.value.copy(isPlaying = false)
            } else {
                player.start()
                _playbackState.value = _playbackState.value.copy(isPlaying = true)
                startPlaybackTicker()
            }
        } catch (e: Exception) {
            _playbackState.value = _playbackState.value.copy(errorMessage = e.localizedMessage)
        }
    }

    fun seekTo(positionMs: Int) {
        val player = mediaPlayer ?: return
        try {
            val clamped = positionMs.coerceIn(0, _playbackState.value.durationMs)
            player.seekTo(clamped)
            _playbackState.value = _playbackState.value.copy(currentPositionMs = clamped)
        } catch (_: Exception) {
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        val player = mediaPlayer
        _playbackState.value = _playbackState.value.copy(playbackSpeed = speed)
        if (player != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val wasPlaying = player.isPlaying
                player.playbackParams = PlaybackParams().setSpeed(speed.coerceIn(0.5f, 2.0f))
                if (!wasPlaying) player.pause()
            } catch (_: Exception) {
            }
        }
    }

    fun stopPlayback() {
        playbackTickerJob?.cancel()
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {
        }
        mediaPlayer = null
        _playbackState.value = PlaybackState()
    }

    private fun startPlaybackTicker() {
        playbackTickerJob?.cancel()
        playbackTickerJob = scope.launch {
            while (isActive) {
                val pos = try {
                    mediaPlayer?.currentPosition ?: 0
                } catch (_: Exception) {
                    0
                }
                _playbackState.value = _playbackState.value.copy(currentPositionMs = pos)
                delay(200)
            }
        }
    }

    fun readMediaDurationMs(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val dur = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            dur
        } catch (_: Exception) {
            0L
        }
    }

    // ---- Offline AI / TTS Narration Generation to Persistent Audio File ----

    suspend fun getAvailableTtsLanguages(): List<Locale> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            var tts: TextToSpeech? = null
            tts = TextToSpeech(context) { status ->
                val result = if (status == TextToSpeech.SUCCESS) {
                    val locales = try {
                        tts?.availableLanguages
                            ?.filter { it.language.isNotBlank() }
                            ?.sortedBy { it.displayName }
                            ?: listOf(Locale.US, Locale.UK, Locale("bn", "BD"))
                    } catch (_: Exception) {
                        listOf(Locale.US)
                    }
                    locales.ifEmpty { listOf(Locale.US) }
                } else {
                    listOf(Locale.US)
                }
                tts?.shutdown()
                if (cont.isActive) cont.resume(result)
            }
            cont.invokeOnCancellation { tts?.shutdown() }
        }
    }

    suspend fun synthesizeNoteToLocalAudio(
        text: String,
        languageTag: String,
        speechRate: Float
    ): Result<Pair<File, Long>> = withContext(Dispatchers.IO) {
        val cleanText = text.replace(Regex("[#*`]+"), " ").replace(Regex("\\s+"), " ").trim()
        if (cleanText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("No text available to narrate."))
        }
        if (!storage.hasEnoughSpace(5L * 1024L * 1024L)) {
            return@withContext Result.failure(IOException("Insufficient device storage space."))
        }

        val chunks = splitIntoTtsChunks(cleanText, maxChars = 2800)
        _ttsProgress.value = TtsSynthesisProgress(
            isSynthesizing = true,
            currentChunk = 0,
            totalChunks = chunks.size,
            statusText = "Initializing speech engine..."
        )

        val tempChunkFiles = mutableListOf<File>()
        try {
            val locale = Locale.forLanguageTag(languageTag).takeIf { it.language.isNotBlank() } ?: Locale.US
            val initSuccess = initAndSynthesizeChunks(chunks, locale, speechRate, tempChunkFiles)
            if (!initSuccess.isSuccess) {
                tempChunkFiles.forEach { it.delete() }
                _ttsProgress.value = TtsSynthesisProgress()
                return@withContext Result.failure(
                    initSuccess.exceptionOrNull() ?: IOException("Text-to-Speech synthesis failed.")
                )
            }

            _ttsProgress.value = _ttsProgress.value.copy(statusText = "Saving audio file locally...")
            val finalFile = storage.createNewFile(storage.aiAudioDir, "ai_audio", "wav")
            if (tempChunkFiles.size == 1) {
                tempChunkFiles.first().copyTo(finalFile, overwrite = true)
            } else {
                mergeWavFiles(tempChunkFiles, finalFile)
            }
            tempChunkFiles.forEach { it.delete() }

            if (!finalFile.exists() || finalFile.length() <= 44L) {
                finalFile.delete()
                _ttsProgress.value = TtsSynthesisProgress()
                return@withContext Result.failure(IOException("Synthesized audio file was empty."))
            }

            val duration = readMediaDurationMs(finalFile)
            _ttsProgress.value = TtsSynthesisProgress()
            Result.success(finalFile to duration)
        } catch (e: Exception) {
            tempChunkFiles.forEach { it.delete() }
            _ttsProgress.value = TtsSynthesisProgress()
            Result.failure(e)
        }
    }

    private suspend fun initAndSynthesizeChunks(
        chunks: List<String>,
        locale: Locale,
        speechRate: Float,
        outChunkFiles: MutableList<File>
    ): Result<Unit> = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            var ttsRef: TextToSpeech? = null
            ttsRef = TextToSpeech(context) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    ttsRef?.shutdown()
                    if (cont.isActive) {
                        cont.resume(Result.failure(IOException("Android Text-to-Speech engine is unavailable on this device.")))
                    }
                    return@TextToSpeech
                }

                val tts = ttsRef!!
                val langStatus = tts.setLanguage(locale)
                if (langStatus == TextToSpeech.LANG_MISSING_DATA || langStatus == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.US)
                }
                tts.setSpeechRate(speechRate.coerceIn(0.6f, 2.0f))

                scope.launch(Dispatchers.IO) {
                    try {
                        for ((idx, chunk) in chunks.withIndex()) {
                            _ttsProgress.value = TtsSynthesisProgress(
                                isSynthesizing = true,
                                currentChunk = idx + 1,
                                totalChunks = chunks.size,
                                statusText = "Generating audio segment ${idx + 1} of ${chunks.size}..."
                            )
                            val chunkFile = File(storage.tempDir, "tts_${UUID.randomUUID()}.wav")
                            val ok = synthesizeSingleChunk(tts, chunk, chunkFile)
                            if (!ok || !chunkFile.exists() || chunkFile.length() <= 44L) {
                                throw IOException("Failed to synthesize audio segment ${idx + 1}.")
                            }
                            outChunkFiles.add(chunkFile)
                        }
                        withContext(Dispatchers.Main) { tts.shutdown() }
                        if (cont.isActive) cont.resume(Result.success(Unit))
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) { tts.shutdown() }
                        if (cont.isActive) cont.resume(Result.failure(e))
                    }
                }
            }
            cont.invokeOnCancellation { ttsRef?.shutdown() }
        }
    }

    private suspend fun synthesizeSingleChunk(
        tts: TextToSpeech,
        text: String,
        destFile: File
    ): Boolean = suspendCancellableCoroutine { cont ->
        val utteranceId = UUID.randomUUID().toString()
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(true)
            }

            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) {
                if (id == utteranceId && cont.isActive) cont.resume(false)
            }

            override fun onError(id: String?, errorCode: Int) {
                if (id == utteranceId && cont.isActive) cont.resume(false)
            }
        })
        val params = Bundle()
        val res = tts.synthesizeToFile(text, params, destFile, utteranceId)
        if (res != TextToSpeech.SUCCESS && cont.isActive) {
            cont.resume(false)
        }
    }

    private fun splitIntoTtsChunks(text: String, maxChars: Int): List<String> {
        if (text.length <= maxChars) return listOf(text)
        val chunks = mutableListOf<String>()
        var idx = 0
        while (idx < text.length) {
            val end = (idx + maxChars).coerceAtMost(text.length)
            if (end == text.length) {
                chunks.add(text.substring(idx))
                break
            }
            val splitAt = text.lastIndexOf('.', end).takeIf { it > idx + maxChars / 2 }
                ?: text.lastIndexOf(' ', end).takeIf { it > idx }
                ?: end
            chunks.add(text.substring(idx, splitAt + 1).trim())
            idx = splitAt + 1
        }
        return chunks.filter { it.isNotBlank() }
    }

    private fun mergeWavFiles(inputs: List<File>, output: File) {
        val firstBytes = FileInputStream(inputs.first()).use { it.readBytes() }
        if (firstBytes.size <= 44) throw IOException("Invalid WAV header.")
        val header = firstBytes.copyOfRange(0, 44)
        var totalPcmBytes = 0L

        FileOutputStream(output).use { out ->
            out.write(header)
            inputs.forEach { file ->
                val bytes = FileInputStream(file).use { it.readBytes() }
                if (bytes.size > 44) {
                    val pcmLen = bytes.size - 44
                    out.write(bytes, 44, pcmLen)
                    totalPcmBytes += pcmLen
                }
            }
            out.flush()
        }

        // Update RIFF chunk size (offset 4) and data subchunk size (offset 40)
        java.io.RandomAccessFile(output, "rw").use { raf ->
            val riffSize = (totalPcmBytes + 36).toInt()
            raf.seek(4)
            raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(riffSize).array())
            raf.seek(40)
            raf.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(totalPcmBytes.toInt()).array())
        }
    }
}
