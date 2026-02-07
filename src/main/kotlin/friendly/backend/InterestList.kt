package friendly.backend

data class InterestList private constructor(val raw: List<Interest>) {
    fun serializable(): InterestListSerializable = InterestListSerializable(
        raw = raw.map(Interest::serializable),
    )

    companion object {
        val MaxSize: Int = 100

        fun orThrow(vararg raw: Interest): InterestList = orThrow(raw.toList())

        fun orThrow(raw: List<Interest>): InterestList {
            require(raw.size <= MaxSize) {
                "You cannot pick more than 100 interests"
            }
            require(raw.toSet().size == raw.size) {
                "A list of interests must be unique"
            }
            return InterestList(raw)
        }
    }
}
