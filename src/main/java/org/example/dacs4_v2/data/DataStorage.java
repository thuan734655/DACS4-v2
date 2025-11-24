package org.example.dacs4_v2.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class DataStorage {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String DATA_DIR = "data";

    public static void save(Object data, String fileName) {
        if (data == null || fileName == null || fileName.isEmpty()) return;
        try {
            File dir = new File(DATA_DIR);
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File file = new File(dir, fileName);
            try (FileWriter writer = new FileWriter(file)) {
                GSON.toJson(data, writer);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static <T> T load(String fileName, Class<T> type) {
        if (fileName == null || fileName.isEmpty() || type == null) return null;
        File file = new File(DATA_DIR, fileName);
        if (!file.exists()) return null;
        try (FileReader reader = new FileReader(file)) {
            return GSON.fromJson(reader, type);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}
