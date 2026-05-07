package edge.builders

abstract class Sample {
    abstract class Builder<T : Builder<T>> {
        private var name: String? = null

        fun name(value: String): T {
            name = value
            return self()
        }

        protected abstract fun self(): T
    }
}
