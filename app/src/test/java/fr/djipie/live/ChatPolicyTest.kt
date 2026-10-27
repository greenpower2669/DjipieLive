package fr.djipie.live

import org.junit.Assert.*
import org.junit.Test

class ChatPolicyTest {
    @Test fun questionFirstFilter() {
        assertTrue(ChatPolicy.shouldRead(ChatLine("1", "Léa", "Comment jouer ?"), true))
        assertFalse(ChatPolicy.shouldRead(ChatLine("2", "Sam", "Bonsoir"), true))
        assertTrue(ChatPolicy.shouldRead(ChatLine("2", "Sam", "Bonsoir"), false))
    }

    @Test fun stripUrlsAndLimitLength() {
        val clean = ChatPolicy.clean("Bonjour https://exemple.fr/test?x=1\\n salut")
        assertFalse(clean.contains("https://"))
        assertTrue(clean.contains("lien"))
        assertTrue(ChatPolicy.clean("a".repeat(500)).length <= 240)
    }
}
