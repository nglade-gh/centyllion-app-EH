package com.centyllion.model.gallery

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Rules of the personal gallery written on the disk: conversion between a data URL and a JPEG file,
 * merge by id, name of the picture file, and the cases where `galerie.json` cannot be trusted.
 *
 * Everything here is pure (no canvas, no IndexedDB, no directory handle), so these tests run in
 * `:core:jvmTest` and never need a browser.
 */
class GalleryDiskTest {

    /** The first bytes of a JPEG image: enough to check that the bytes survive the round trip. */
    private val jpeg = byteArrayOf(
        0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(),
        0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
    )

    private val dataUrl = encodeImageDataUrl(jpeg)

    private val now = "2026-11-25T10:00:00.000Z"

    private fun entry(id: String, title: String = "", image: String = "", date: String = "") = GalleryEntry(
        id = id,
        title = title.ifBlank { id },
        description = "description of $id",
        image = image,
        model = """{"model":{"name":"$id"}}""",
        date = date
    )

    // ------------------------------------------------------------ data URL <-> bytes

    @Test
    fun dataUrlRoundTripKeepsTheBytesAndTheMediaType() {
        val payload = assertNotNull(decodeImageDataUrl(dataUrl))
        assertEquals("image/jpeg", payload.first)
        assertContentEquals(jpeg, payload.second)
        // the payload is standard base64: it can be read by any tool
        assertTrue(dataUrl.startsWith("data:image/jpeg;base64,"))
        assertEquals(jpeg.size, payload.second.size)
    }

    @Test
    fun aFileNameOrAPathIsNotADataUrl() {
        assertNull(decodeImageDataUrl(""))
        assertNull(decodeImageDataUrl("image-local-1.jpg"))
        assertNull(decodeImageDataUrl("Gallery/Autopulse2.jpg"))
        assertNull(decodeImageDataUrl("data:image/jpeg,not-base64"))
        assertNull(decodeImageDataUrl("data:image/jpeg;base64,"))
    }

    @Test
    fun imageFileNamesAreRecognised() {
        assertTrue(isGalleryImageFileName("image-local-1.jpg"))
        assertFalse(isGalleryImageFileName("Gallery/Autopulse2.jpg"))
        assertFalse(isGalleryImageFileName("galerie.json"))
        assertFalse(isGalleryImageFileName(dataUrl))
        assertFalse(isGalleryImageFileName(""))
    }

    // ---------------------------------------------------------------- to the disk

    @Test
    fun toDiskWritesOneJsonAndOneJpegFileWithoutBase64() {
        val content = toDisk(listOf(entry("local-42", image = dataUrl)), now)

        val read = decodeDisk(content.json)
        assertIs<GalleryDiskRead.Loaded>(read)
        val diskEntry = read.entries.single()
        assertEquals("local-42", diskEntry.id)
        assertEquals("local-42", diskEntry.title)
        assertEquals("description of local-42", diskEntry.description)
        // the picture is a file of the folder, never base64 inside the JSON
        assertEquals("image-local-42.jpg", diskEntry.image)
        assertFalse(content.json.contains("base64"))
        assertFalse(content.json.contains("data:image"))
        // the date is filled at the first write, so every entry carries one
        assertEquals(now, diskEntry.date)
        assertEquals("""{"model":{"name":"local-42"}}""", diskEntry.model)

        val image = content.images.single()
        assertEquals("image-local-42.jpg", image.fileName)
        assertEquals("image/jpeg", image.mediaType)
        assertContentEquals(jpeg, image.bytes)
    }

    @Test
    fun theDiskJsonHasTheFormatOfTheExport() {
        val content = toDisk(listOf(entry("local-1", image = dataUrl)), now)
        // version 1 and the key models: the file is an export, and an export can be dropped here
        assertTrue(content.json.contains("\"version\": 1"))
        assertTrue(content.json.contains("\"models\""))
        assertTrue(content.json.contains("\"exportedAt\": \"$now\""))
        val read = decodeDisk(content.json)
        assertIs<GalleryDiskRead.Loaded>(read)
        assertEquals(now, read.exportedAt)
        assertEquals(1, read.entries.size)
    }

    @Test
    fun anEntryWithoutPictureKeepsNoImageAndWritesNoFile() {
        val content = toDisk(listOf(entry("local-2")), now)
        assertTrue(content.images.isEmpty())
        val read = decodeDisk(content.json)
        assertIs<GalleryDiskRead.Loaded>(read)
        assertEquals("", read.entries.single().image)
        assertEquals(now, read.entries.single().date)
    }

    @Test
    fun anImageThatIsAlreadyAFileNameIsKeptAsIs() {
        // a distributed model carries a served path: it is not copied into the folder
        val content = toDisk(
            listOf(entry("gol", image = "Gallery/GoL_Modifiers.jpg")),
            now
        )
        assertTrue(content.images.isEmpty())
        val read = decodeDisk(content.json)
        assertIs<GalleryDiskRead.Loaded>(read)
        assertEquals("Gallery/GoL_Modifiers.jpg", read.entries.single().image)
    }

    @Test
    fun anExistingDateIsNotRewritten() {
        val read = decodeDisk(toDisk(listOf(entry("local-3", image = dataUrl, date = "2025-01-02T03:04:05Z")), now).json)
        assertIs<GalleryDiskRead.Loaded>(read)
        assertEquals("2025-01-02T03:04:05Z", read.entries.single().date)
    }

    @Test
    fun anIdRepeatedInTheListIsWrittenOnce() {
        val content = toDisk(listOf(entry("local-4", title = "first"), entry("local-4", title = "last")), now)
        val read = decodeDisk(content.json)
        assertIs<GalleryDiskRead.Loaded>(read)
        assertEquals(1, read.entries.size)
        assertEquals("last", read.entries.single().title)
    }

    // -------------------------------------------------------------- from the disk

    @Test
    fun fromDiskRebuildsTheDataUrls() {
        val content = toDisk(listOf(entry("local-42", image = dataUrl)), now)
        val read = decodeDisk(content.json)
        assertIs<GalleryDiskRead.Loaded>(read)
        val images = content.images.associate { it.fileName to it.bytes }

        val back = fromDisk(read.entries, images)
        assertEquals(1, back.size)
        assertEquals(dataUrl, back.single().image)
        assertEquals("local-42", back.single().id)
    }

    @Test
    fun aMissingPictureFileKeepsTheMetadata() {
        val content = toDisk(listOf(entry("local-42", image = dataUrl)), now)
        val read = decodeDisk(content.json)
        assertIs<GalleryDiskRead.Loaded>(read)
        // the picture was deleted by hand: the model is still there, and so is the name of its file
        val back = fromDisk(read.entries, emptyMap())
        assertEquals("local-42", back.single().id)
        assertEquals("image-local-42.jpg", back.single().image)
        assertEquals("""{"model":{"name":"local-42"}}""", back.single().model)
    }

    // ------------------------------------------------------------- picture names

    @Test
    fun thePictureNameIsDerivedFromTheIdAndSanitised() {
        assertEquals("image-local-1732190000000.jpg", galleryImageFileName("local-1732190000000"))
        // forbidden characters of a file system become underscores
        assertEquals("image-a_b_c.jpg", galleryImageFileName("a/b:c"))
        assertEquals("image-a_b_c.jpg", galleryImageFileName("a\\b?c"))
        // an empty id still gives a usable name
        assertEquals("image-entry.jpg", galleryImageFileName(""))
        assertEquals("image-entry.jpg", galleryImageFileName("   "))
        // a Windows device name gets a leading underscore
        assertEquals("image-_CON.jpg", galleryImageFileName("CON"))
        assertTrue(galleryImageFileName("../..").endsWith(".jpg"))
        assertFalse(galleryImageFileName("../..").contains("/"))
    }

    @Test
    fun thePictureNameLengthIsBounded() {
        val name = galleryImageFileName("x".repeat(500))
        assertTrue(name.length <= "image-".length + 64 + ".jpg".length)
        assertTrue(name.startsWith("image-"))
        assertTrue(name.endsWith(".jpg"))
    }

    @Test
    fun twoIdsCleanedToTheSameTextGetTwoFiles() {
        val content = toDisk(
            listOf(
                entry("a/b", image = dataUrl),
                entry("a:b", image = dataUrl)
            ),
            now
        )
        assertEquals(2, content.images.size)
        assertEquals(2, content.images.map { it.fileName }.distinct().size)
        val read = decodeDisk(content.json)
        assertIs<GalleryDiskRead.Loaded>(read)
        assertEquals(2, read.entries.map { it.image }.distinct().size)
        assertEquals(2, read.entries.size)
    }

    // --------------------------------------------------------------------- merge

    @Test
    fun mergeKeepsBothSidesAndTheLastWriterWins() {
        val disk = listOf(entry("a", title = "A from disk"), entry("b", title = "B from disk"))
        val mine = listOf(entry("b", title = "B from me"), entry("c", title = "C from me"))

        val merged = mergeById(disk, mine)

        assertEquals(listOf("a", "b", "c"), merged.map { it.id })
        assertEquals("A from disk", merged.first { it.id == "a" }.title)
        // the same id: the caller wins
        assertEquals("B from me", merged.first { it.id == "b" }.title)
        assertEquals("C from me", merged.first { it.id == "c" }.title)
    }

    @Test
    fun mergeDropsTheExplicitlyRemovedIds() {
        val disk = listOf(entry("a"), entry("b"))
        val mine = listOf(entry("a"))

        val merged = mergeById(disk, mine, setOf("b"))

        assertEquals(listOf("a"), merged.map { it.id })
    }

    @Test
    fun mergeOfAnEmptyDiskIsTheCallersGallery() {
        val mine = listOf(entry("a"), entry("b"))
        assertEquals(mine.map { it.id }, mergeById(emptyList(), mine).map { it.id })
    }

    @Test
    fun mergeKeepsTheOrderOfTheDiskThenAppends() {
        val disk = listOf(entry("z"), entry("a"))
        val mine = listOf(entry("a"), entry("n"))
        assertEquals(listOf("z", "a", "n"), mergeById(disk, mine).map { it.id })
    }

    // ---------------------------------------------------------------- unreadable

    @Test
    fun anAbsentFileIsNotAnError() {
        assertIs<GalleryDiskRead.Missing>(decodeDisk(null))
    }

    @Test
    fun anUnusableFileIsReportedAsUnreadable() {
        assertIs<GalleryDiskRead.Unreadable>(decodeDisk(""))
        assertIs<GalleryDiskRead.Unreadable>(decodeDisk("   "))
        assertIs<GalleryDiskRead.Unreadable>(decodeDisk("<html>not a gallery</html>"))
        assertIs<GalleryDiskRead.Unreadable>(decodeDisk("""{"version":1}"""))
        assertIs<GalleryDiskRead.Unreadable>(decodeDisk("""[]"""))
        // a file written by a newer version is refused instead of being truncated
        assertIs<GalleryDiskRead.Unreadable>(decodeDisk("""{"version":2,"models":[]}"""))
        // an entry without id cannot be used: the file is kept aside rather than overwritten
        assertIs<GalleryDiskRead.Unreadable>(decodeDisk("""{"version":1,"models":[{"title":"no id"}]}"""))
        assertIs<GalleryDiskRead.Unreadable>(decodeDisk("""{"version":1,"models":[{"id":""}]}"""))
    }

    @Test
    fun anEmptyGalleryIsReadable() {
        val read = decodeDisk(encodeDisk(emptyList(), now))
        assertIs<GalleryDiskRead.Loaded>(read)
        assertTrue(read.entries.isEmpty())
        assertEquals(now, read.exportedAt)
    }

    @Test
    fun unknownKeysOfAFutureEntryAreIgnored() {
        val read = decodeDisk(
            """{"version":1,"exportedAt":"$now","models":[{"id":"x","title":"X","colour":"red"}]}"""
        )
        assertIs<GalleryDiskRead.Loaded>(read)
        assertEquals("X", read.entries.single().title)
    }

    // ------------------------------------------------------------------- backups

    @Test
    fun aFileKeptAsideHasADatedNameInTheGalleryFolder() {
        val name = galleryBackupFileName("20261125-101233")
        assertEquals("galerie.bak-20261125-101233.json", name)
        // the name is useful as a file name: no separator, and it stays a JSON file
        assertFalse(name.contains("/"))
        assertTrue(name.endsWith(".json"))
        assertEquals("galerie.bak-backup.json", galleryBackupFileName(""))
        // a stamp carrying separators cannot escape the folder
        val messy = galleryBackupFileName("2026-11-25/10:12:33")
        assertFalse(messy.contains("/"))
        assertFalse(messy.contains(":"))
        assertTrue(messy.endsWith(".json"))
    }
}
