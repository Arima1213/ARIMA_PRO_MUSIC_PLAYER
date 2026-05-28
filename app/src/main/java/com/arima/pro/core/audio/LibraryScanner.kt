package com.arima.pro.core.audio

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.example.data.database.AppDatabase
import com.example.data.database.FolderEntity
import com.example.data.database.SongEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LibraryScanner(private val context: Context) {

    private val db = AppDatabase.getDatabase(context)
    private val formatDetector = FormatDetector(context)
    private val semaphore = Semaphore(4)

    fun scanFolder(folderPath: String): Flow<ScanProgress> = flow {
        emit(ScanProgress("Initializing scan...", 0, 0, 0, 0.0f))

        val isContent = folderPath.startsWith("content://")
        val parsedSongs = mutableListOf<SongEntity>()
        var filesScanned = 0
        var tracksFound = 0
        var totalBytesScanned = 0L

        val supportedExtensions = setOf("mp3", "flac", "wav", "dsf", "dff", "aac", "m4a", "ogg", "aiff")

        if (isContent) {
            val rootDoc = DocumentFile.fromTreeUri(context, Uri.parse(folderPath))
            if (rootDoc == null || !rootDoc.exists() || !rootDoc.isDirectory) {
                emit(ScanProgress("Folder not found or invalid", 0, 0, 0, 1.0f))
                return@flow
            }

            emit(ScanProgress("Traversing directory...", 0, 0, 0, 0.05f))
            val documentFiles = mutableListOf<DocumentFile>()
            traverseDocFolder(rootDoc, documentFiles)

            val totalFiles = documentFiles.size
            if (totalFiles == 0) {
                emit(ScanProgress("No audio files found in directory", 0, 0, 0, 1.0f))
                return@flow
            }

            for (docFile in documentFiles) {
                val name = docFile.name ?: "Unknown"
                val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                if (!supportedExtensions.contains(extension)) {
                    filesScanned++
                    continue
                }

                val formattedSize = formatFileSize(docFile.length())
                val songEntity = semaphore.withPermit {
                    withContext(Dispatchers.IO) {
                        try {
                            var title = ""
                            var artist = ""
                            var album = ""
                            var durationVal = 0L
                            var artBytes: ByteArray? = null

                            val retriever = MediaMetadataRetriever()
                            try {
                                retriever.setDataSource(context, docFile.uri)
                                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: ""
                                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: ""
                                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM) ?: ""
                                durationVal = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                                artBytes = retriever.embeddedPicture
                            } catch (e: Exception) {
                                // Ignored
                            } finally {
                                retriever.release()
                            }

                            if (artBytes == null) {
                                try {
                                    context.contentResolver.openInputStream(docFile.uri)?.use { stream ->
                                        val bytes = stream.readBytes()
                                        val tempFile = java.io.File.createTempFile("art_extract", ".bin")
                                        tempFile.outputStream().use { it.write(bytes) }
                                        try {
                                            val af = org.jaudiotagger.audio.AudioFileIO.read(tempFile)
                                            af.tag?.firstArtwork?.binaryData?.let { artBytes = it }
                                        } catch (e: Exception) { /* jaudiotagger failed */ }
                                        tempFile.delete()
                                    }
                                } catch (e: Exception) { /* stream failed */ }
                            }

                            if (artBytes != null && artBytes!!.size > 300_000) {
                                try {
                                    val bmp = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes!!.size)
                                    if (bmp != null) {
                                        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 256, 256, true)
                                        val output = java.io.ByteArrayOutputStream()
                                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, output)
                                        artBytes = output.toByteArray()
                                        bmp.recycle()
                                        scaled.recycle()
                                    }
                                } catch (e: Exception) { /* compression failed — keep original */ }
                            }

                            if (title.isEmpty()) title = name.substringBeforeLast('.')
                            if (artist.isEmpty()) artist = "Unknown Artist"
                            if (album.isEmpty()) album = "Unknown Album"

                            val techInfo = formatDetector.detectTechnicalProperties(docFile.uri)
                            val formatStr = formatDetector.detectFormat(docFile.uri).name

                            SongEntity(
                                title = title,
                                artist = artist,
                                album = album,
                                duration = durationVal,
                                format = formatStr,
                                sampleRate = "${techInfo.sampleRate / 1000} kHz",
                                bitDepth = "${techInfo.bitDepth} bit",
                                fileSize = formattedSize,
                                path = docFile.uri.toString(),
                                folderPath = folderPath,
                                isFavorite = false,
                                albumArt = artBytes
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                if (songEntity != null) {
                    parsedSongs.add(songEntity)
                    tracksFound++
                    totalBytesScanned += docFile.length()
                }

                filesScanned++
                val progress = filesScanned.toFloat() / totalFiles.toFloat()
                emit(ScanProgress(
                    step = "Processing: $name",
                    filesScanned = filesScanned,
                    filesTotal = totalFiles,
                    tracksFound = tracksFound,
                    progress = progress
                ))
            }
        } else {
            val rootDir = File(folderPath)
            if (!rootDir.exists() || !rootDir.isDirectory) {
                emit(ScanProgress("Folder not found or invalid", 0, 0, 0, 1.0f))
                return@flow
            }

            emit(ScanProgress("Traversing directory...", 0, 0, 0, 0.05f))
            val audioFiles = mutableListOf<File>()
            traverseFolder(rootDir, audioFiles)

            val totalFiles = audioFiles.size
            if (totalFiles == 0) {
                emit(ScanProgress("No audio files found", 0, 0, 0, 1.0f))
                return@flow
            }

            for (file in audioFiles) {
                val extension = file.extension.lowercase(Locale.ROOT)
                if (!supportedExtensions.contains(extension)) {
                    filesScanned++
                    continue
                }

                val formattedSize = formatFileSize(file.length())
                val songEntity = semaphore.withPermit {
                    withContext(Dispatchers.IO) {
                        try {
                            var title = ""
                            var artist = ""
                            var album = ""

                            var artBytes: ByteArray? = null
                            try {
                                val audioFile = AudioFileIO.read(file)
                                val tag = audioFile.tag
                                if (tag != null) {
                                    title = tag.getFirst(FieldKey.TITLE)
                                    artist = tag.getFirst(FieldKey.ARTIST)
                                    album = tag.getFirst(FieldKey.ALBUM)
                                    artBytes = tag.firstArtwork?.binaryData
                                }
                            } catch (e: Exception) {
                                // Fallback
                            }

                            if (artBytes == null) {
                                try {
                                    val retriever = MediaMetadataRetriever()
                                    retriever.setDataSource(file.absolutePath)
                                    artBytes = retriever.embeddedPicture
                                    retriever.release()
                                } catch (e: Exception) {
                                    // Ignored
                                }
                            }

                            if (artBytes != null && artBytes!!.size > 300_000) {
                                try {
                                    val bmp = android.graphics.BitmapFactory.decodeByteArray(artBytes, 0, artBytes!!.size)
                                    if (bmp != null) {
                                        val scaled = android.graphics.Bitmap.createScaledBitmap(bmp, 256, 256, true)
                                        val output = java.io.ByteArrayOutputStream()
                                        scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 75, output)
                                        artBytes = output.toByteArray()
                                        bmp.recycle()
                                        scaled.recycle()
                                    }
                                } catch (e: Exception) { /* ignore */ }
                            }

                            if (title.isEmpty()) title = file.nameWithoutExtension
                            if (artist.isEmpty()) artist = "Unknown Artist"
                            if (album.isEmpty()) album = "Unknown Album"

                            val uri = Uri.fromFile(file)
                            val techInfo = formatDetector.detectTechnicalProperties(uri)
                            val formatStr = formatDetector.detectFormat(uri).name

                            SongEntity(
                                title = title,
                                artist = artist,
                                album = album,
                                duration = getDuration(file),
                                format = formatStr,
                                sampleRate = "${techInfo.sampleRate / 1000} kHz",
                                bitDepth = "${techInfo.bitDepth} bit",
                                fileSize = formattedSize,
                                path = file.absolutePath,
                                albumArt = artBytes,
                                folderPath = folderPath,
                                isFavorite = false
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }
                }

                if (songEntity != null) {
                    parsedSongs.add(songEntity)
                    tracksFound++
                    totalBytesScanned += file.length()
                }

                filesScanned++
                val progress = filesScanned.toFloat() / totalFiles.toFloat()
                emit(ScanProgress(
                    step = "Processing: ${file.name}",
                    filesScanned = filesScanned,
                    filesTotal = totalFiles,
                    tracksFound = tracksFound,
                    progress = progress
                ))
            }
        }

        val songDao = db.songDao()
        val folderDao = db.folderDao()

        if (parsedSongs.isNotEmpty()) {
            emit(ScanProgress("Storing tracks into database...", filesScanned, filesScanned, tracksFound, 0.95f))
            withContext(Dispatchers.IO) {
                songDao.deleteSongsByFolder(folderPath)
                songDao.insertSongs(parsedSongs)

                val sdf = SimpleDateFormat("dd MMM yyyy HH:mm", Locale.getDefault())
                val nowStr = sdf.format(Date())
                folderDao.insertFolder(
                    FolderEntity(
                        path = folderPath,
                        fileCount = tracksFound,
                        totalSize = formatFileSize(totalBytesScanned),
                        lastScan = nowStr
                    )
                )
            }
        }

        emit(ScanProgress("Scan completed successfully", filesScanned, filesScanned, tracksFound, 1.0f))
    }

    private fun traverseFolder(dir: File, files: MutableList<File>) {
        val list = dir.listFiles() ?: return
        for (file in list) {
            if (file.isDirectory) {
                traverseFolder(file, files)
            } else if (file.isFile) {
                files.add(file)
            }
        }
    }

    private fun traverseDocFolder(dir: DocumentFile, files: MutableList<DocumentFile>) {
        val list = dir.listFiles()
        for (file in list) {
            if (file.isDirectory) {
                traverseDocFolder(file, files)
            } else if (file.isFile) {
                files.add(file)
            }
        }
    }

    private fun getDuration(file: File): Long {
        return try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            durationStr?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        }
    }

    private fun formatFileSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}
