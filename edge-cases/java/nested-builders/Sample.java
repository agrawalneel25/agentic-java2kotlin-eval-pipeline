package edge.builders;

public abstract class Sample {
    public abstract static class Builder<T extends Builder<T>> {
        private String name;

        public T name(String value) {
            this.name = value;
            return self();
        }

        protected abstract T self();
    }
}
