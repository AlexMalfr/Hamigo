package com.malfreyt.alexandre.hamigo

import org.junit.Assert.*
import org.junit.Test

class LicenseDocumentLinksTest {
    @Test fun packagedLicensesResolveOfflineFromEachOriginalDocumentLocation() {
        assertEquals("PERIMETERS.md",licenseLinkTarget("THIRD_PARTY_NOTICES.md","LICENSES/README.md"))
        assertEquals("Apache-2.0.txt",licenseLinkTarget("THIRD_PARTY_NOTICES.md","LICENSES/Apache-2.0.txt"))
        assertEquals("BSD-2-Clause-commonmark.txt",licenseLinkTarget("THIRD_PARTY_NOTICES.md","LICENSES/BSD-2-Clause-commonmark.txt"))
        assertEquals("LICENSE",licenseLinkTarget("PERIMETERS.md","../LICENSE"))
        assertEquals("THIRD_PARTY_NOTICES.md",licenseLinkTarget("PERIMETERS.md","../THIRD_PARTY_NOTICES.md"))
    }
    @Test fun sourceReferencesResolveAgainstTheShippedCommitAndExternalUrlsStayExact() {
        assertEquals("${HamigoLinks.REPOSITORY}/blob/${BuildConfig.SOURCE_REVISION}/docs/SOURCES-EXAM1.md",
            licenseLinkTarget("PERIMETERS.md","../docs/SOURCES-EXAM1.md"))
        val url="https://github.com/commonmark/commonmark-java?tab=readme-ov-file#usage"
        assertEquals(url,licenseLinkTarget("THIRD_PARTY_NOTICES.md",url))
    }
}
