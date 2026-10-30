package com.abhishek.antiscroll.rules.unlock

import kotlin.random.Random

/**
 * Text for the typing challenge: plain sentences, picked at random. [random] is injected so tests
 * can fix the choice. A paragraph is [PARAGRAPH_SENTENCES] sentences.
 */
class TypingSentences(private val random: Random, private val sentences: List<String> = ALL) {

    init {
        require(sentences.size > PARAGRAPH_SENTENCES) { "need more sentences than a paragraph holds" }
    }

    /** [count] different sentences joined by spaces, never exactly [notThis] (the text just failed). */
    fun pick(count: Int, notThis: String? = null): String {
        require(count in 1 until sentences.size) { "count must be between 1 and ${sentences.size - 1}" }
        while (true) {
            val text = sentences.shuffled(random).take(count).joinToString(" ")
            if (text != notThis) return text
        }
    }

    companion object {
        const val PARAGRAPH_SENTENCES = 4

        val ALL = listOf(
            "The kettle clicked off just as the rain began to fall.",
            "She folded the map twice and tucked it into her coat.",
            "A small boat drifted past the old stone bridge.",
            "We planted tomatoes along the sunny side of the fence.",
            "The library stays open late on Thursday evenings.",
            "He tied his shoes, grabbed his keys and walked out.",
            "Fresh bread cools slowly on the kitchen windowsill.",
            "The train was ten minutes late, so we bought tea.",
            "Clouds gathered over the hills before noon.",
            "My neighbour plays the piano every Sunday morning.",
            "The bicycle needs new brakes before the long ride.",
            "A quiet walk can clear a noisy mind.",
            "They painted the front door a deep shade of green.",
            "The museum closes early on public holidays.",
            "Write the list first, then go to the market.",
            "The cat slept in a patch of warm afternoon light.",
            "Our street lights flicker on just after sunset.",
            "He finished the puzzle with one piece missing.",
            "The river was calm, and the air smelled of pine.",
            "Bring an umbrella; the forecast looks uncertain.",
            "She keeps her notes in a small blue notebook.",
            "The bus stops twice before the station.",
            "We watched the stars from the roof until midnight.",
            "Soup tastes better on the second day.",
            "The garden gate creaks every time the wind blows.",
            "He learned to cook rice without burning the pot.",
            "The old clock in the hall runs five minutes fast.",
            "A good chair makes a long day at the desk easier.",
            "The children built a fort out of cushions.",
            "Turn left at the bakery and walk two blocks.",
            "The lake froze early this winter.",
            "She sharpened every pencil before the exam.",
            "The radio played softly while we packed the car.",
            "Most plants need less water in the colder months.",
            "He wrote a letter by hand for the first time in years.",
            "The market sells apples, pears and fresh honey.",
            "We missed the last ferry and stayed the night.",
            "The dog waited patiently by the front door.",
            "A clear desk helps me think a little faster.",
            "The hikers reached the top just before sunset.",
        )
    }
}
