///usr/bin/env jbang "$0" "$@" ; exit $?
//REPOS mavenCentral,ehi=http://jars.interlis.ch/
//DEPS ch.interlis:ili2pg:4.3.1 org.postgresql:postgresql:42.1.4.jre6 ch.ehi:ehisqlgen:1.13.8

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import ch.ehi.ili2db.base.Ili2db;
import ch.ehi.ili2db.base.Ili2dbException;
import ch.ehi.ili2db.gui.Config;
import ch.ehi.ili2pg.PgMain;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

public class create_schema_sql {
    
    public static void main(String... args) throws Ili2dbException, IOException {

        // Rahmenmodell
        List<String> schemas = List.of("stage", "live");
        SortedMap<String,String> models = new TreeMap<>();
        models.put("ili1", "DM01AVCH24LV95D;PLZOCH1LV95D");
        models.put("ili2", "OeREBKRM_V2_0;OeREBKRMkvs_V2_0;OeREBKRMtrsfr_V2_0");

        var config = new Config();
        new PgMain().initConfig(config);
        config.setFunction(Config.FC_SCRIPT);
        config.setModeldir("https://geo.so.ch/models;http://models.geo.admin.ch");
        Config.setStrokeArcs(config, Config.STROKE_ARCS_ENABLE);
        config.setCreateFk(Config.CREATE_FK_YES);
        config.setCreateFkIdx(Config.CREATE_FKIDX_YES);
        config.setCreateImportTabs(true);
        config.setCreateMetaInfo(true);
        config.setNameOptimization(Config.NAME_OPTIMIZATION_DISABLE);
        config.setDefaultSrsCode("2056");
       
        var contentBuilder = new StringBuilder();
        for (var schema : schemas) {
            config.setValue(Config.CREATE_GEOM_INDEX, Config.TRUE);
            config.setTidHandling(Config.TID_HANDLING_PROPERTY);        
            config.setBasketHandling(Config.BASKET_HANDLING_READWRITE);
            config.setCreateTypeDiscriminator(Config.CREATE_TYPE_DISCRIMINATOR_ALWAYS);

            contentBuilder.append("/* SCHEMA: " + schema + " */\n");
            for (var model : models.entrySet()) {
                String fileName = schema+"_"+model.getKey()+".sql";
                config.setDbschema(schema);
                config.setModels(model.getValue());
                config.setCreatescript(new File(fileName).getAbsolutePath());
                Ili2db.run(config, null);
                
                var content = new String(Files.readAllBytes(Paths.get(fileName)));
                if (model.getKey().equalsIgnoreCase("ili2")) {
                    String replacedContent = content
                          .replaceAll("CREATE SEQUENCE", "-- CREATE SEQUENCE")
                          .replaceAll("CREATE TABLE (.*T_ILI2DB)", "CREATE TABLE IF NOT EXISTS $1")
                          .replaceAll("(CREATE.*INDEX) (T_ILI2DB)", "$1 IF NOT EXISTS $2")
                          .replaceAll("(ALTER TABLE .*T_ILI2DB.* ADD CONSTRAINT .* FOREIGN KEY)", "-- $1")
                          .replaceAll("(INSERT INTO .*T_ILI2DB_SETTINGS)", "-- $1");
                    contentBuilder.append(replacedContent);
                } else {
                    contentBuilder.append(content);
                }
            }
        }

        // WMS
        schemas = List.of("stage_wms", "live_wms");
        for (var schema : schemas) {
            String fileName = schema+".sql";
            config.setDbschema(schema);
            config.setModels("SO_AGI_OeREB_WMS_20220222");
            config.setTidHandling(Config.TID_HANDLING_PROPERTY);        
            config.setBasketHandling(null);
            config.setCreateTypeDiscriminator(null);
            config.setCreatescript(new File(fileName).getAbsolutePath());
            Ili2db.run(config, null);    

            var content = new String(Files.readAllBytes(Paths.get(fileName)));
            contentBuilder.append(content);
        }

        var fos = new FileOutputStream("setup.sql");
        fos.write(contentBuilder.toString().getBytes());
        fos.close();
    }
}
