package com.webdavshare.android.webdav

import android.content.Context
import android.os.Environment
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*

/**
 * Implémentation d'un serveur WebDAV simple basée sur NanoHTTPD
 */
class WebDAVServer(
    private val context: Context,
    private val port: Int
) : NanoHTTPD(port) {

    companion object {
        private const val TAG = "WebDAVServer"
        private const val XML_HEADER = "<?xml version=\"1.0\" encoding=\"utf-8\" ?>"
    }

    /**
     * Démarre le serveur WebDAV
     */
    @Throws(IOException::class)
    override fun start() {
        super.start(SOCKET_READ_TIMEOUT, false)
        Log.i(TAG, "Serveur WebDAV démarré sur le port $port")
    }

    /**
     * Gère les requêtes entrantes au serveur WebDAV
     */
    override fun serve(session: IHTTPSession): Response {
        val method = session.method.name
        val uri = session.uri

        // Journalisation pour le débogage
        Log.d(TAG, "Méthode: $method, URI: $uri, Headers: ${session.headers}")

        return try {
            when (method) {
                "OPTIONS" -> handleOptions()
                "PROPFIND" -> handlePropFind(session, uri)
                "PROPPATCH" -> handlePropPatch(session, uri)
                "GET" -> handleGet(uri)
                "HEAD" -> handleHead(uri)
                "PUT" -> handlePut(session, uri)
                "DELETE" -> handleDelete(uri)
                "MKCOL" -> handleMkcol(uri)
                "MOVE" -> handleMove(session, uri)
                "COPY" -> handleCopy(session, uri)
                "LOCK" -> handleLock(session, uri)
                "UNLOCK" -> handleUnlock(session, uri)
                else -> createResponse(Response.Status.NOT_IMPLEMENTED, NanoHTTPD.MIME_PLAINTEXT, "Méthode non implémentée: $method")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors du traitement de la requête: $method $uri", e)
            createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Erreur serveur: ${e.message}")
        }
    }

    /**
     * Gère la méthode OPTIONS pour exposer les capacités du serveur
     */
    private fun handleOptions(): Response {
        val response = createResponse(Response.Status.OK, NanoHTTPD.MIME_PLAINTEXT, "")
        response.addHeader("Allow", "OPTIONS, GET, HEAD, PROPFIND, PROPPATCH, PUT, DELETE, MKCOL, MOVE, COPY, LOCK, UNLOCK")
        response.addHeader("DAV", "1, 2")
        response.addHeader("MS-Author-Via", "DAV")
        return response
    }

    /**
     * Gère la méthode PROPFIND pour lister les fichiers et dossiers
     */
    private fun handlePropFind(session: IHTTPSession, path: String): Response {
        val depth = session.headers["depth"]?.trim() ?: "infinity"
        if (depth != "0" && depth != "1" && depth != "infinity") {
            return createResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Profondeur WebDAV invalide")
        }
        val targetFile = getFileFromPath(path)

        if (targetFile == null || !targetFile.exists()) {
            return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Non trouvé")
        }
        if (!targetFile.canRead()) {
            return createResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Accès au dossier refusé")
        }

        val responseXml = StringBuilder()
        responseXml.append(XML_HEADER)
        responseXml.append("<D:multistatus xmlns:D=\"DAV:\">")

        // Ajouter les propriétés du dossier/fichier cible
        appendFileProperties(responseXml, targetFile, path)

        // Si depth est 1 ou infinity, ajouter les enfants
        if (depth == "1" || depth == "infinity") {
            if (targetFile.isDirectory) {
                val children = targetFile.listFiles()
                    ?: return createResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Impossible de lire le contenu du dossier")
                children.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) })).forEach { child ->
                    val childPath = if (path.endsWith("/")) "$path${child.name}" else "$path/${child.name}"
                    appendFileProperties(responseXml, child, childPath)
                }
            }
        }

        responseXml.append("</D:multistatus>")

        val response = createResponse(Response.Status.MULTI_STATUS, "application/xml; charset=utf-8", responseXml.toString())
        response.addHeader("DAV", "1, 2")
        return response
    }

    /**
     * Gère la méthode PROPPATCH (support basique pour Windows)
     */
    private fun handlePropPatch(session: IHTTPSession, path: String): Response {
        val targetFile = getFileFromPath(path)
        if (targetFile == null || !targetFile.exists()) {
            return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Non trouvé")
        }

        val formattedPath = formatHref(path, targetFile.isDirectory)
        val xml = """$XML_HEADER
<D:multistatus xmlns:D="DAV:">
  <D:response>
    <D:href>${escapeXml(formattedPath)}</D:href>
    <D:propstat>
      <D:status>HTTP/1.1 200 OK</D:status>
    </D:propstat>
  </D:response>
</D:multistatus>"""
        return createResponse(Response.Status.MULTI_STATUS, "application/xml; charset=utf-8", xml)
    }

    /**
     * Gère la méthode LOCK (support pour Windows WebDAV client)
     */
    private fun handleLock(session: IHTTPSession, path: String): Response {
        val targetFile = getFileFromPath(path)
        val lockToken = "urn:uuid:" + UUID.randomUUID().toString()
        val formattedPath = formatHref(path, targetFile?.isDirectory ?: false)

        val xml = """$XML_HEADER
<D:prop xmlns:D="DAV:">
  <D:lockdiscovery>
    <D:activelock>
      <D:locktype><D:write/></D:locktype>
      <D:lockscope><D:exclusive/></D:lockscope>
      <D:depth>0</D:depth>
      <D:owner><D:href>Windows</D:href></D:owner>
      <D:timeout>Second-3600</D:timeout>
      <D:locktoken><D:href>$lockToken</D:href></D:locktoken>
      <D:lockroot><D:href>${escapeXml(formattedPath)}</D:href></D:lockroot>
    </D:activelock>
  </D:lockdiscovery>
</D:prop>"""
        val response = createResponse(Response.Status.OK, "application/xml; charset=utf-8", xml)
        response.addHeader("Lock-Token", "<$lockToken>")
        return response
    }

    /**
     * Gère la méthode UNLOCK
     */
    private fun handleUnlock(session: IHTTPSession, path: String): Response {
        return createResponse(Response.Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
    }

    /**
     * Formate un chemin en href WebDAV valide
     */
    private fun formatHref(path: String, isDirectory: Boolean): String {
        val normalized = if (path.startsWith("/")) path else "/$path"
        // Encode each segment independently so spaces, accents and # are valid
        // in DAV href values without encoding the hierarchy separators.
        val encoded = normalized.split("/").joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8")
                .replace("+", "%20")
                .replace("%7E", "~")
        }
        return if (isDirectory && !encoded.endsWith("/")) "$encoded/" else encoded
    }

    /**
     * Échappe les caractères spéciaux XML
     */
    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    /**
     * Ajoute les propriétés d'un fichier ou dossier au XML de réponse PROPFIND
     */
    private fun appendFileProperties(xml: StringBuilder, file: File, path: String) {
        val href = formatHref(path, file.isDirectory)
        val displayName = if (path == "/" || file.name.isEmpty()) "/" else file.name
        
        xml.append("<D:response>")
        xml.append("<D:href>${escapeXml(href)}</D:href>")
        xml.append("<D:propstat>")
        xml.append("<D:prop>")
        
        // Nom d'affichage
        xml.append("<D:displayname>${escapeXml(displayName)}</D:displayname>")
        
        // Type de ressource
        if (file.isDirectory) {
            xml.append("<D:resourcetype><D:collection/></D:resourcetype>")
            xml.append("<D:iscollection>1</D:iscollection>")
        } else {
            xml.append("<D:resourcetype/>")
            xml.append("<D:iscollection>0</D:iscollection>")
            xml.append("<D:getcontentlength>${file.length()}</D:getcontentlength>")
        }
        
        xml.append("<D:ishidden>0</D:ishidden>")
        
        // Type MIME
        val mimeType = getMimeType(file) ?: if (file.isDirectory) "httpd/unix-directory" else "application/octet-stream"
        xml.append("<D:getcontenttype>$mimeType</D:getcontenttype>")
        
        // Dates (RFC 1123 pour getlastmodified, ISO 8601 pour creationdate)
        val lastModified = file.lastModified()
        if (lastModified > 0) {
            xml.append("<D:getlastmodified>${formatHttpDate(lastModified)}</D:getlastmodified>")
            xml.append("<D:creationdate>${formatIsoDate(lastModified)}</D:creationdate>")
        }
        
        xml.append("</D:prop>")
        xml.append("<D:status>HTTP/1.1 200 OK</D:status>")
        xml.append("</D:propstat>")
        xml.append("</D:response>")
    }

    /**
     * Gère la méthode GET pour télécharger un fichier
     */
    private fun handleGet(path: String): Response {
        val targetFile = getFileFromPath(path)
        
        if (targetFile == null || !targetFile.exists()) {
            return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Non trouvé")
        }
        
        if (targetFile.isDirectory) {
            return createDirectoryListing(targetFile, path)
        }
        
        return try {
            val inputStream = FileInputStream(targetFile)
            val mimeType = getMimeType(targetFile) ?: "application/octet-stream"
            
            // Fixed-length framing is more compatible with Windows WebDAV clients
            // than combining a chunked body with a manually supplied Content-Length.
            val response = newFixedLengthResponse(
                Response.Status.OK,
                mimeType,
                inputStream,
                targetFile.length()
            )
            response.addHeader(
                "Content-Disposition",
                "inline; filename*=UTF-8''" + java.net.URLEncoder.encode(targetFile.name, "UTF-8").replace("+", "%20")
            )
            response.addHeader("Accept-Ranges", "bytes")
            response.addHeader("DAV", "1, 2")
            response
        } catch (e: Exception) {
            Log.e(TAG, "Erreur lecture fichier", e)
            createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Erreur lors de la lecture du fichier: ${e.message}")
        }
    }

    /**
     * Gère la méthode HEAD
     */
    private fun handleHead(path: String): Response {
        val targetFile = getFileFromPath(path)
        if (targetFile == null || !targetFile.exists()) {
            return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Non trouvé")
        }
        val mimeType = getMimeType(targetFile) ?: if (targetFile.isDirectory) "httpd/unix-directory" else "application/octet-stream"
        val response = createResponse(Response.Status.OK, mimeType, "")
        if (!targetFile.isDirectory) {
            response.addHeader("Content-Length", targetFile.length().toString())
        }
        return response
    }

    /**
     * Gère la méthode PUT pour téléverser un fichier
     */
    private fun handlePut(session: IHTTPSession, path: String): Response {
        val targetFile = getFileFromPath(path)
        if (targetFile == null) {
            return createResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Chemin invalide")
        }

        val parent = targetFile.parentFile
        if (parent == null || !parent.exists() || !parent.isDirectory) {
            return createResponse(Response.Status.CONFLICT, NanoHTTPD.MIME_PLAINTEXT, "Le dossier parent n'existe pas")
        }
        if (!parent.canWrite() || targetFile.isDirectory) {
            return createResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Écriture interdite")
        }

        return try {
            val existedBefore = targetFile.exists()
            val files = HashMap<String, String>()
            session.parseBody(files)

            val contentFile = files["content"]
            if (!contentFile.isNullOrEmpty()) {
                val uploadedFile = File(contentFile)
                if (!uploadedFile.isFile) {
                    return createResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Contenu reçu invalide")
                }
                // Écrire dans un fichier temporaire du même dossier : un transfert
                // interrompu ne doit pas laisser un fichier partiellement écrit.
                val temporaryFile = File.createTempFile(".webdav-", ".upload", parent)
                try {
                    uploadedFile.inputStream().use { input ->
                        temporaryFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    if (!temporaryFile.renameTo(targetFile)) {
                        throw IOException("Impossible de finaliser le transfert")
                    }
                } finally {
                    temporaryFile.delete()
                    uploadedFile.delete()
                }
            } else {
                // Un PUT vide est valide : il crée ou tronque le fichier.
                targetFile.outputStream().use { output ->
                    val postData = files["postData"]
                    if (postData != null) output.write(postData.toByteArray(Charsets.UTF_8))
                }
            }

            createResponse(
                if (existedBefore) Response.Status.OK else Response.Status.CREATED,
                NanoHTTPD.MIME_PLAINTEXT,
                "Transfert terminé"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erreur PUT", e)
            createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Erreur lors de l'upload: ${e.message}")
        }
    }

    /**
     * Gère la méthode DELETE pour supprimer un fichier ou dossier
     */
    private fun handleDelete(path: String): Response {
        val targetFile = getFileFromPath(path)
        
        if (targetFile == null || !targetFile.exists()) {
            return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Non trouvé")
        }
        
        val success = if (targetFile.isDirectory) {
            targetFile.deleteRecursively()
        } else {
            targetFile.delete()
        }
        
        return if (success) {
            createResponse(Response.Status.NO_CONTENT, NanoHTTPD.MIME_PLAINTEXT, "")
        } else {
            createResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Impossible de supprimer")
        }
    }

    /**
     * Gère la méthode MKCOL pour créer un dossier
     */
    private fun handleMkcol(path: String): Response {
        val dirName = path.trimEnd('/').substringAfterLast('/')
        val parent = getParentFile(path)
        
        if (parent == null || !parent.exists() || !parent.isDirectory) {
            return createResponse(Response.Status.CONFLICT, NanoHTTPD.MIME_PLAINTEXT, "Le dossier parent n'existe pas")
        }
        
        val targetDir = File(parent, dirName)
        
        if (targetDir.exists()) {
            return createResponse(Response.Status.METHOD_NOT_ALLOWED, NanoHTTPD.MIME_PLAINTEXT, "Le dossier existe déjà")
        }
        
        return if (targetDir.mkdir()) {
            createResponse(Response.Status.CREATED, NanoHTTPD.MIME_PLAINTEXT, "")
        } else {
            createResponse(Response.Status.FORBIDDEN, NanoHTTPD.MIME_PLAINTEXT, "Impossible de créer le dossier")
        }
    }

    /**
     * Gère la méthode MOVE pour déplacer ou renommer un fichier ou dossier
     */
    private fun handleMove(session: IHTTPSession, path: String): Response {
        val destination = session.headers["destination"]
        if (destination.isNullOrEmpty()) {
            return createResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Destination non spécifiée")
        }
        
        val sourceFile = getFileFromPath(path)
        if (sourceFile == null || !sourceFile.exists()) {
            return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Source non trouvée")
        }
        
        return try {
            val destPath = try {
                val uri = java.net.URI(destination)
                uri.path
            } catch (e: Exception) {
                destination
            }
            
            val destFile = getFileFromPath(destPath)
            if (destFile == null) {
                return createResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Destination invalide")
            }
            
            if (destFile.exists()) {
                if (destFile.isDirectory) destFile.deleteRecursively() else destFile.delete()
            }
            
            val success = sourceFile.renameTo(destFile)
            if (success) {
                createResponse(Response.Status.CREATED, NanoHTTPD.MIME_PLAINTEXT, "Déplacé avec succès")
            } else {
                createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Impossible de déplacer le fichier")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur MOVE", e)
            createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Erreur: ${e.message}")
        }
    }

    /**
     * Gère la méthode COPY pour copier un fichier ou dossier
     */
    private fun handleCopy(session: IHTTPSession, path: String): Response {
        val destination = session.headers["destination"]
        if (destination.isNullOrEmpty()) {
            return createResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Destination non spécifiée")
        }
        
        val sourceFile = getFileFromPath(path)
        if (sourceFile == null || !sourceFile.exists()) {
            return createResponse(Response.Status.NOT_FOUND, NanoHTTPD.MIME_PLAINTEXT, "Source non trouvée")
        }
        
        return try {
            val destPath = try {
                val uri = java.net.URI(destination)
                uri.path
            } catch (e: Exception) {
                destination
            }
            
            val destFile = getFileFromPath(destPath)
            if (destFile == null) {
                return createResponse(Response.Status.BAD_REQUEST, NanoHTTPD.MIME_PLAINTEXT, "Destination invalide")
            }
            
            sourceFile.copyRecursively(destFile, overwrite = true)
            createResponse(Response.Status.CREATED, NanoHTTPD.MIME_PLAINTEXT, "Copié avec succès")
        } catch (e: Exception) {
            Log.e(TAG, "Erreur COPY", e)
            createResponse(Response.Status.INTERNAL_ERROR, NanoHTTPD.MIME_PLAINTEXT, "Erreur: ${e.message}")
        }
    }

    /**
     * Crée une réponse HTTP simple
     */
    private fun createResponse(status: Response.IStatus, mimeType: String, message: String): Response {
        val response = newFixedLengthResponse(status, mimeType, message)
        response.addHeader("DAV", "1, 2")
        return response
    }

    /**
     * Crée une liste de répertoire en HTML
     */
    private fun createDirectoryListing(directory: File, path: String): Response {
        val html = StringBuilder()
        html.append("<html><head><meta charset=\"utf-8\"><title>Index de ${escapeXml(path)}</title></head><body>")
        html.append("<h1>Index de ${escapeXml(path)}</h1><hr><pre>")
        
        val parentPath = path.trimEnd('/').substringBeforeLast('/', "")
        if (parentPath.isNotEmpty() || path != "/") {
            val upPath = if (parentPath.isEmpty()) "/" else "$parentPath/"
            html.append("<a href=\"$upPath\">..</a>\n")
        }
        
        directory.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase(Locale.ROOT) }))?.forEach { file ->
            val fileName = file.name
            val filePath = if (path.endsWith("/")) "$path$fileName" else "$path/$fileName"
            val suffix = if (file.isDirectory) "/" else ""
            
            html.append("<a href=\"${escapeXml(filePath)}$suffix\">${escapeXml(fileName)}$suffix</a>\n")
        }
        
        html.append("</pre><hr></body></html>")
        
        return createResponse(Response.Status.OK, "text/html; charset=utf-8", html.toString())
    }

    /**
     * Obtient un File à partir d'un chemin (supporte /DavWWWRoot pour Windows)
     */
    private fun getFileFromPath(path: String): File? {
        return try {
            // Normalise le chemin et interdit toute sortie de la racine partagée,
            // notamment les séquences ../ et les chemins absolus.
            var cleanPath = path.replace('\\', '/').trimStart('/')
            if (cleanPath.equals("DavWWWRoot", ignoreCase = true)) {
                cleanPath = ""
            } else if (cleanPath.startsWith("DavWWWRoot/", ignoreCase = true)) {
                cleanPath = cleanPath.substring("DavWWWRoot/".length)
            }

            val rootDir = Environment.getExternalStorageDirectory().canonicalFile
            val candidate = if (cleanPath.isEmpty()) rootDir else File(rootDir, cleanPath).canonicalFile
            val rootPath = rootDir.path
            if (candidate.path != rootPath && !candidate.path.startsWith(rootPath + File.separator)) {
                Log.w(TAG, "Chemin WebDAV refusé hors de la racine: " + path)
                null
            } else {
                candidate
            }
        } catch (e: IOException) {
            Log.w(TAG, "Chemin WebDAV invalide: " + path, e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "Accès WebDAV refusé: " + path, e)
            null
        }
    }

    /**
     * Obtient le fichier parent à partir d'un chemin
     */
    private fun getParentFile(path: String): File? {
        val trimmed = path.trimEnd('/')
        val parentPath = trimmed.substringBeforeLast('/', "")
        return getFileFromPath(parentPath)
    }

    /**
     * Formate une date en HTTP-date RFC 1123 pour WebDAV (getlastmodified)
     */
    private fun formatHttpDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("GMT")
        return sdf.format(Date(timestamp))
    }

    /**
     * Formate une date en ISO 8601 pour WebDAV (creationdate)
     */
    private fun formatIsoDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("GMT")
        return sdf.format(Date(timestamp))
    }

    /**
     * Obtient le type MIME d'un fichier
     */
    private fun getMimeType(file: File): String? {
        val fileNameMap = java.net.URLConnection.getFileNameMap()
        return fileNameMap.getContentTypeFor(file.name)
    }
}
