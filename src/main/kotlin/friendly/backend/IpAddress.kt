package friendly.backend

data class IpAddress private constructor(val string: String) {
    companion object {
        val MaxLength: Int = 39

        fun orThrow(string: String): IpAddress {
            require(string.length <= MaxLength) {
                "IP address must not be more than $MaxLength"
            }
            return IpAddress(string)
        }
    }
}
