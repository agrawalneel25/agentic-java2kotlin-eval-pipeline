package edge.generics

class Sample {
    fun sum(values: List<out Number>): Double {
        var total = 0.0
        for (value in values) {
            total += value.toDouble()
        }
        return total
    }

    fun addOne(values: MutableList<in Int>) {
        values.add(1)
    }
}
