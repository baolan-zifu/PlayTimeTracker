package cat.nyaa.playtimetracker;

import com.google.common.io.LineReader;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DatabaseManager {
    private final File dbFile;
    private Map<UUID, DatabaseRecord> recordMap;

    public DatabaseManager(File db_file) {
        dbFile = db_file;
        recordMap = new HashMap<>();
        if (dbFile.isFile()) { // Read in
            ConfigurationSection cfg = YamlConfiguration.loadConfiguration(dbFile);
            for (String uuid_str : cfg.getKeys(false)) {
                ConfigurationSection sec = cfg.getConfigurationSection(uuid_str);
                UUID id = UUID.fromString(uuid_str);
                recordMap.put(id, DatabaseRecord.deserialize(id, sec));
            }
        }
    }

    public DatabaseManager(File db_file, File old_db_file) {
        dbFile = db_file;
        recordMap = new HashMap<>();
        if (old_db_file.isFile()) {
            FileReader fr = null;
            LineReader lr;
            try {
                fr = new FileReader(old_db_file);
                lr = new LineReader(fr);
                String line;
                while ((line = lr.readLine()) != null) {
                    String[] tmp = line.split(" ", 2);
                    if (tmp.length != 2) continue;
                    UUID a = null;
                    try {
                        a = UUID.fromString(tmp[0]);
                    } catch (IllegalArgumentException ex) {
                        Main.log("Illegal data line: " + line);
                        continue;
                    }
                    recordMap.put(a, DatabaseRecord.deserialize_legacy(a, tmp[1]));
                }
            } catch (IOException ex) {
                Main.log("Failed to parse legacy database");
                ex.printStackTrace();
            } finally {
                try {
                    if (fr != null) fr.close();
                } catch (IOException ex) {
                    Main.log("Failed to parse legacy database");
                    ex.printStackTrace();
                }
            }
        }
    }

    public void save() {
        final Map<UUID, DatabaseRecord> clonedMap = new HashMap<>();
        final File clonedFile = new File(dbFile.toURI());
        for (Map.Entry<UUID, DatabaseRecord> entry : recordMap.entrySet()) {
            clonedMap.put(entry.getKey(), entry.getValue().clone());
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronizeSave(clonedFile, clonedMap);
            }
        }).start();
    }

    public void synchronizeSave() {
        synchronizeSave(dbFile, recordMap);
    }

    private static synchronized void synchronizeSave(final File dbFile, final Map<UUID, DatabaseRecord> records) {
        YamlConfiguration cfg = new YamlConfiguration();
        for (UUID id : records.keySet()) {
            records.get(id).serialize(cfg.createSection(id.toString()));
        }
        try {
            cfg.save(dbFile);
        } catch (IOException ex) {
            System.out.print(">>>>>> PlayTimeTracker Database Emergency Dump <<<<<<\n" +
                    cfg.saveToString() +
                    "\n>>>>>> Emergency dump ends <<<<<<"
            );
            ex.printStackTrace();
        }
    }

    public DatabaseRecord getRecord(UUID id) {
        return recordMap.get(id);
    }

    public void createRecord(UUID id, ZonedDateTime time) {
        recordMap.put(id, new DatabaseRecord(id, time));
    }

    public Map<UUID, DatabaseRecord> getAllRecords() {
        return recordMap;
    }
}