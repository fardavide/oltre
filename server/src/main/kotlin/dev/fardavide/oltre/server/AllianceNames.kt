package dev.fardavide.oltre.server

import com.ibm.icu.lang.UCharacter
import java.text.Normalizer

@JvmInline
internal value class CanonicalAllianceName(val value: String) {

    val likePrefix: String
        get() = value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    companion object {

        fun normalised(value: String): CanonicalAllianceName = CanonicalAllianceName(
            UCharacter.foldCase(Normalizer.normalize(value, Normalizer.Form.NFKC).replace(Regex("(?U)\\s+"), " ").trim(), true),
        )
    }
}
