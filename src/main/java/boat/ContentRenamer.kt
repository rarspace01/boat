package boat

import boat.info.MediaProxyService
import java.io.File

class ContentRenamer(val mediaProxyService: MediaProxyService) {

    fun extractName(fullName: String): String {
        val file = File(fullName)
        val fileName = file.name
        val parentFile = file.parentFile
        val parentName = parentFile?.name ?: ""
        val grandParentName = parentFile?.parentFile?.name ?: ""

        val nameWithoutExtension = fileName.substringBeforeLast(".")

        // Remove SxxExx or Sxx
        val seasonPattern = "S([0-9]{1,2})(E[0-9]{1,2})?".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val episodePattern = "(?<=^|[^a-zA-Z0-9])(?:E|Ep|\\s-\\s|\\s|-|\\.)([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val yearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()
        val bracketPattern = "\\[.*?]".toRegex()
        val dvdPattern = "(?<=^|\\s)(?:dvd|disc|cd)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)

        val seasonInNamePattern = "(?<=^|\\s)Season\\s([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        fun cleanName(inputName: String): String {
            var name = inputName
            name = name.replace("(", "").replace(")", "").replace("_", " ").trim()
            name = name.removePrefix(".")

            val seasonMatch = seasonPattern.find(name)
            val seasonMatch2 = seasonPattern2.find(name)
            val seasonMatch3 = seasonInNamePattern.find(name)
            val dvdMatch = dvdPattern.find(name)
            if (seasonMatch != null) {
                name = name.substring(0, seasonMatch.range.first)
            } else if (seasonMatch2 != null) {
                name = name.substring(0, seasonMatch2.range.first)
            } else if (seasonMatch3 != null) {
                name = name.substring(0, seasonMatch3.range.first)
            } else if (dvdMatch != null) {
                name = name.substring(0, dvdMatch.range.first)
            } else {
                // Special handling for episode numbers that might be separated by a dash without spaces
                val nameForEpisodeMatching = name.replace("-", " ")
                val episodeMatch = episodePattern.find(nameForEpisodeMatching)
                if (episodeMatch != null) {
                    name = name.substring(0, episodeMatch.range.first)
                } else {
                    val yearMatch = yearPattern.find(name)
                    if (yearMatch != null) {
                        name = name.substring(0, yearMatch.range.first)
                    }
                }
            }

            name = name.replace(bracketPattern, "").trim()
            name = name.replace(".", " ").replace("_", " ").trim()

            // Remove trailing dashes or other common separators
            while (name.endsWith("-") || name.endsWith(".")) {
                name = name.substring(0, name.length - 1).trim()
            }

            // If extracted name ends with a number that looks like an episode (e.g., show-24)
            // and we haven't truncated it yet, do a last resort truncation
            val trailingEpisodePattern = "-([0-9]{1,3})$".toRegex()
            val trailingMatch = trailingEpisodePattern.find(name)
            if (trailingMatch != null) {
                name = name.substring(0, trailingMatch.range.first).trim()
            }
            return name
        }

        val cleanedFileName = cleanName(nameWithoutExtension)
        val cleanedParentName = if (parentName.isNotBlank()) cleanName(parentName) else ""
        val cleanedGrandParentName = if (grandParentName.isNotBlank()) cleanName(grandParentName) else ""

        // If the filename only contains season/episode/year info (or very little else)
        // then the parent folder is likely the show name
        if (cleanedFileName.isEmpty() || cleanedFileName.length < 3 || 
            (parentName.isNotBlank() && (cleanedFileName.lowercase() == "chapter" || cleanedFileName.lowercase().startsWith("chapter "))) ||
            (parentName.isNotBlank() && (cleanedFileName.lowercase().startsWith("episode") || cleanedFileName.lowercase().startsWith("ep ")))
        ) {
            if (cleanedParentName.isNotEmpty() && !cleanedParentName.lowercase().startsWith("season")) {
                return cleanedParentName
            }
            if (cleanedGrandParentName.isNotEmpty()) {
                return cleanedGrandParentName
            }
        }

        return cleanedFileName.ifBlank { cleanedParentName }.ifBlank { cleanedGrandParentName }
    }

    fun extractYear(fullName: String): Int? {
        val yearPattern = "\\b([1-2][0-9]{3})\\b".toRegex()
        val match = yearPattern.find(File(fullName).name) ?: yearPattern.find(File(fullName).parentFile?.name ?: "")
        return match?.groupValues?.get(1)?.toInt()
    }

    fun extractSeason(fullName: String): Int? {
        val file = File(fullName)
        val fileName = file.name
        val parentFile = file.parentFile
        val seasonPattern = "S([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonPatternInParent = "(?<=Season\\s)([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val dvdPattern = "(?<=^|\\s)(?:dvd|disc|cd)\\s?([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        
        val match = seasonPattern.find(fileName) 
            ?: seasonPattern.find(parentFile?.name ?: "")
            ?: seasonPatternInParent.find(parentFile?.name ?: "")
            ?: seasonPattern.find(parentFile?.parentFile?.name ?: "")
            ?: dvdPattern.find(fileName)
            
        if (match != null) {
            return match.groupValues[match.groupValues.size - 1].toInt()
        }
        
        val match2 = seasonPattern2.find(fileName) 
            ?: seasonPattern2.find(parentFile?.name ?: "")
            ?: seasonPattern2.find(parentFile?.parentFile?.name ?: "")
            
        return match2?.groupValues?.get(1)?.toInt()
    }

    fun extractEpisode(fullName: String): Int? {
        val file = File(fullName)
        val fileName = file.name
        val parentFile = file.parentFile
        val episodePattern = "(?<=^|[^a-zA-Z0-9])(?:E|Ep|\\s-\\s|\\s|-|\\.)([0-9]{1,3})(?=[^a-zA-Z0-9]|$)".toRegex(RegexOption.IGNORE_CASE)
        val seasonEpisodePattern = "S[0-9]{1,2}E([0-9]{1,2})".toRegex(RegexOption.IGNORE_CASE)
        val seasonPattern2 = "(?:\\[|\\b)([0-9]{1,2})x([0-9]{1,2})(?:\\]|\\b)".toRegex()
        val replaced = fileName.replace("_", " ")

        val match2 = seasonPattern2.find(fileName) ?: seasonPattern2.find(parentFile?.name ?: "")
        if (match2 != null) {
            return match2.groupValues[2].toInt()
        }
        
        val match = seasonEpisodePattern.find(fileName)
            ?: episodePattern.find(replaced) 
            ?: episodePattern.find(fileName) 
            ?: "-([0-9]{1,3})\\.".toRegex().find(fileName)
            ?: episodePattern.find(parentFile?.name ?: "")
            
        if (match != null) {
            return match.groupValues[1].toInt()
        }
        
        return null
    }

    fun processFilesFromDirectory(path: String, logFile: File? = null) {
        val files = File(path).listFiles()
        files?.let { files ->
            for (file in files) {
                if (file.isFile) {
                    val fullName = file.absolutePath
                    // extract Movie or TV show name from full path
                    val name = extractName(fullName)
                    val year = extractYear(fullName)
                    var season = extractSeason(fullName)
                    val episode = extractEpisode(fullName)
                    
                    val queriedName = if (year != null) {
                        mediaProxyService.search(name, year).firstOrNull()
                    } else {
                        mediaProxyService.search(name).firstOrNull()
                    }
                    
                    if (season == null && episode != null && queriedName != null && queriedName.seasonCount == 1) {
                        season = 1
                    }

                    val idString = queriedName?.id ?: ""
                    val logLine = "F: $fullName -> Extracted: $name -> $queriedName (Y: $year, S: $season, E: $episode) $idString"
                    println(logLine)
                    logFile?.appendText(logLine + "\n")
                } else {
                    processFilesFromDirectory(file.absolutePath, logFile)
                }
            }
        }
    }
}