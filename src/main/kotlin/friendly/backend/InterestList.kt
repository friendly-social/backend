package friendly.backend

data class InterestList private constructor(val raw: List<Interest>) {
    fun serializable(): InterestListSerializable = InterestListSerializable(
        raw = raw.map(Interest::serializable),
    )

    companion object {
        fun orThrow(vararg raw: Interest): InterestList = orThrow(raw.toList())

        fun orThrow(raw: List<Interest>): InterestList {
            require(raw.toSet().size == raw.size) {
                "A list of interests must be unique"
            }
            return InterestList(raw)
        }
    }
}
