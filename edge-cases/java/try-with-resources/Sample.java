package edge.resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;

public final class Sample {
    public String firstLine(Reader input) throws IOException {
        try (BufferedReader reader = new BufferedReader(input)) {
            return reader.readLine();
        }
    }
}
