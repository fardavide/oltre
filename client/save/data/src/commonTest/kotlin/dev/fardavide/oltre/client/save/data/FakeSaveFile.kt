package dev.fardavide.oltre.client.save.data

// Handwritten, per the repo's no-mocking-framework rule. Holds the last text written, which is
// all a save file is.
internal class FakeSaveFile(initial: String? = null) : SaveFile {

    var content: String? = initial
        private set

    var writeCount: Int = 0
        private set

    var clearCount: Int = 0
        private set

    override suspend fun read(): String? = content

    override suspend fun write(text: String) {
        content = text
        writeCount++
    }

    override suspend fun clear() {
        content = null
        clearCount++
    }
}
