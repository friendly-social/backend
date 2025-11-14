package friendly.backend

data class NetworkDegree(val int: Int) {
    companion object {
        val Zero: NetworkDegree = NetworkDegree(int = 0)
        val One: NetworkDegree = NetworkDegree(int = 1)
        val Two: NetworkDegree = NetworkDegree(int = 2)
        val Three: NetworkDegree = NetworkDegree(int = 3)
    }
}
