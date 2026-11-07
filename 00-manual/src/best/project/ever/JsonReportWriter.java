package best.project.ever;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonReportWriter {

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    public void write(Report report, Path file) throws IOException {
        Files.writeString(file, gson.toJson(report));
    }
}
