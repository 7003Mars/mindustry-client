package mindustry.core;

import arc.*;
import arc.Files.*;
import arc.files.*;
import arc.struct.*;
import arc.util.*;
import arc.util.io.*;

public class Version{
    /** Build type. 'official' for official releases; 'custom' or 'bleeding edge' are also used. */
    public static String type = "unknown";
    /** Build modifier, e.g. 'alpha' or 'release' */
    public static String modifier = "unknown";
    /** Number specifying the major version, e.g. '4' */
    public static int number;
    /** Build number, e.g. '43'. set to '-1' for custom builds. */
    public static int build = 0;
    /** Revision number. Used for hotfixes. Does not affect server compatibility. */
    public static int revision = 0;
    /** Whether version loading is enabled. */
    public static boolean enabled = true;
    /** Custom client build number used for auto updates */
    public static int clientBuild = 0;
    /** Custom client update url used for... updating */
    public static String updateUrl = "unknown";
    /** Custom client version string used for various things */
    public static String clientVersion = "unknown";

    public static void init(){
        if(!enabled) return;

        Fi file = OS.isAndroid || OS.isIos ? Core.files.internal("version.properties") : new Fi("version.properties", FileType.internal);
        Fi version = OS.isAndroid || OS.isIos ? Core.files.internal("version") : new Fi("version", FileType.internal);

        ObjectMap<String, String> map = new ObjectMap<>();
        PropertiesUtils.load(map, file.reader());

        clientBuild = Integer.parseInt(map.get("clientBuild"));
        updateUrl = map.get("updateUrl");
        clientVersion = version.readString();
        type = map.get("type");
        number = Integer.parseInt(map.get("number", "4"));
        modifier = map.get("modifier");
        if(map.get("build").contains(".")){
            String[] split = map.get("build").split("\\.");
            try{
                build = Integer.parseInt(split[0]);
                revision = Integer.parseInt(split[1]);
            }catch(Throwable e){
                e.printStackTrace();
                build = -1;
            }
        }else{
            build = Strings.canParseInt(map.get("build")) ? Integer.parseInt(map.get("build")) : -1;
        }
    }

    public static String buildString(){
        return build < 0 ? "custom" : build + (revision == 0 ? "" : "." + revision);
    }

    /** get menu version without colors */
    public static String combined(){
        if(build == -1){
            return "custom build";
        }
        return (type.equals("official") ? modifier : type) + " build " + build + (revision == 0 ? "" : "." + revision) + "\n(Client Version: " + clientVersion + " Release: " + clientBuild + ")";
    }
}
