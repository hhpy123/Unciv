package com.unciv.logic.multiplayer.storage

import com.unciv.json.json
import com.unciv.ui.utils.UncivDateFormat.parseDate
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset
import java.util.*
import kotlin.concurrent.timer


object DropBox: FileStorage {
    private var remainingRateLimitSeconds = 0
    private var rateLimitTimer: Timer? = null

    private fun dropboxApi(url: String, data: String = "", contentType: String = "", dropboxApiArg: String = ""): InputStream? {

        if (remainingRateLimitSeconds > 0)
            throw FileStorageRateLimitReached(remainingRateLimitSeconds)

        with(URL(url).openConnection() as HttpURLConnection) {
            requestMethod = "POST"  // default is GET

            @Suppress("SpellCheckingInspection")
            setRequestProperty("Authorization", "Bearer LTdBbopPUQ0AAAAAAAACxh4_Qd1eVMM7IBK3ULV3BgxzWZDMfhmgFbuUNF_rXQWb")

            if (dropboxApiArg != "") setRequestProperty("Dropbox-API-Arg", dropboxApiArg)
            if (contentType != "") setRequestProperty("Content-Type", contentType)

            doOutput = true

            try {
                if (data != "") {
                    // StandardCharsets.UTF_8 requires API 19
                    val postData: ByteArray = data.toByteArray(Charset.forName("UTF-8"))
                    val outputStream = DataOutputStream(outputStream)
                    outputStream.write(postData)
                    outputStream.flush()
                }

                return inputStream
            } catch (ex: Exception) {
                println(ex.message)
                val reader = BufferedReader(InputStreamReader(errorStream))
                val responseString = reader.readText()
                println(responseString)

                val error = json().fromJson(ErrorResponse::class.java, responseString)
                // Throw Exceptions based on the HTTP response from dropbox
                when {
                    error.error_summary.startsWith("too_many_requests/") -> triggerRateLimit(error)
                    error.error_summary.startsWith("path/not_found/") -> throw FileNotFoundException()
                    error.error_summary.startsWith("path/conflict/file") -> throw FileStorageConflictException()
                }
                
                return null
            } catch (error: Error) {
                println(error.message)
                val reader = BufferedReader(InputStreamReader(errorStream))
                println(reader.readText())
                return null
            }
        }
    }

    // This is the location in Dropbox only
    private fun getLocalGameLocation(fileName: String) = "/MultiplayerGames/$fileName"

    override fun deleteFile(fileName: String){
        dropboxApi(
            url="https://api.dropboxapi.com/2/files/delete_v2",
            data="{\"path\":\"${getLocalGameLocation(fileName)}\"}",
            contentType="application/json"
        )
    }

    override fun getFileMetaData(fileName: String): FileMetaData {
        val stream = dropboxApi(
            url="https://api.dropboxapi.com/2/files/get_metadata",
            data="{\"path\":\"${getLocalGameLocation(fileName)}\"}",
            contentType="application/json"
        )!!
        val reader = BufferedReader(InputStreamReader(stream))
        return json().fromJson(MetaData::class.java, reader.readText())
    }

    override fun saveFileData(fileName: String, data: String, overwrite: Boolean) {
        val overwriteModeString = if(!overwrite) "" else ""","mode":{".tag":"overwrite"}"""
        dropboxApi(
            url="https://content.dropboxapi.com/2/files/upload",
            data=data,
            contentType="application/octet-stream",
            dropboxApiArg = """{"path":"${getLocalGameLocation(fileName)}"$overwriteModeString}"""
        )
    }

    override fun loadFileData(fileName: String): String {
        val inputStream = downloadFile(getLocalGameLocation(fileName))
        return BufferedReader(InputStreamReader(inputStream)).readText()
    }

    fun downloadFile(fileName: String): InputStream {
        val response = dropboxApi("https://content.dropboxapi.com/2/files/download",
                contentType = "text/plain", dropboxApiArg = "{\"path\":\"$fileName\"}")
        return response!!
    }

    /**
     * If the dropbox rate limit is reached for this bearer token we strictly have to wait for the
     * specified retry_after seconds before trying again. If non is supplied or can not be parsed
     * the default value of 5 minutes will be used.
     * Any attempt before the rate limit is dropped again will also contribute to the rate limit
     */
    private fun triggerRateLimit(response: ErrorResponse) {
        remainingRateLimitSeconds = response.error?.retry_after?.toIntOrNull() ?: 300

        rateLimitTimer = timer("RateLimitTimer", true, 0, 1000) {
            remainingRateLimitSeconds--
            if (remainingRateLimitSeconds == 0)
                rateLimitTimer?.cancel()
        }
        throw FileStorageRateLimitReached(remainingRateLimitSeconds)
    }

    fun fileExists(fileName: String): Boolean = try {
            dropboxApi("https://api.dropboxapi.com/2/files/get_metadata",
                "{\"path\":\"$fileName\"}", "application/json")
            true
        } catch (ex: FileNotFoundException) {
            false
        }

//
//    fun createTemplate(): String {
//        val result =  dropboxApi("https://api.dropboxapi.com/2/file_properties/templates/add_for_user",
//                "{\"name\": \"Security\",\"description\": \"These properties describe how confidential this file or folder is.\",\"fields\": [{\"name\": \"Security Policy\",\"description\": \"This is the security policy of the file or folder described.\nPolicies can be Confidential, Public or Internal.\",\"type\": \"string\"}]}"
//                ,"application/json")
//        return BufferedReader(InputStreamReader(result)).readText()
//    }

//    private class FolderList{
//        var entries = ArrayList<MetaData>()
//        var cursor = ""
//        var has_more = false
//    }

    @Suppress("PropertyName")
    private class MetaData: FileMetaData {
//        var name = ""
        private var server_modified = ""

        override fun getLastModified(): Date {
            return server_modified.parseDate()
        }
    }

    @Suppress("PropertyName")
    private class ErrorResponse {
        var error_summary = ""
        var error: Details? = null

        class Details {
            var retry_after = ""
        }
    }
}
