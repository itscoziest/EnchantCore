# ProGuard Configuration for EnchantCore - Enhanced with aesthetic obfuscation

-verbose
-dontnote "**" # Suppress notes about duplicate library classes, etc.

# === WARNINGS ===
-dontwarn org.bukkit.**
-dontwarn org.spigotmc.**
-dontwarn com.strikesenchantcore.lib.jackson.**

# ===== PRESERVE DEPENDENCIES =====
-keep class org.bukkit.** { *; }
-keep class org.spigotmc.** { *; }
-keep class com.sk89q.** { *; } # WorldEdit/WorldGuard
-keep class net.milkbowl.** { *; } # Vault
-keep class me.clip.** { *; } # PlaceholderAPI

# ===== KEEP SHADED LIBRARIES =====
-keep class com.strikesenchantcore.lib.bstats.** { *; }
-keep class org.yaml.snakeyaml.** { *; }
# Jackson library (after relocation)
-keep class com.strikesenchantcore.lib.jackson.** { *; }
-keep interface com.strikesenchantcore.lib.jackson.** { *; }

# ===== KEEP YOUR PLUGIN'S ENTRY POINTS =====

# Keep the main plugin class and its lifecycle methods
-keep public class com.strikesenchantcore.EnchantCore {
    public void onEnable();
    public void onDisable();
    public void onLoad();
    public static com.strikesenchantcore.EnchantCore getInstance();
    <fields>; # IMPORTANT: Keep ALL fields in your main class to prevent conflicts
}

# Keep StrikesLicenseManager class unobfuscated
-keep public class com.strikesenchantcore.StrikesLicenseManager {
    <methods>;
    <fields>;
}

# --- CRITICAL: PREVENT LOGGER CONFLICTS ---
# This is likely the source of your error
-keepclassmembers class * {
    private static final java.util.logging.Logger *;
    protected static final java.util.logging.Logger *;
    public static final java.util.logging.Logger *;
    private java.util.logging.Logger *;
    protected java.util.logging.Logger *;
    public java.util.logging.Logger *;
}

# --- PREVENT JACKSON SERIALIZATION ISSUES ---
# Keep names of fields that will be serialized/deserialized with Jackson
-keepclassmembers class * {
    @com.strikesenchantcore.lib.jackson.annotation.JsonProperty <fields>;
    @com.strikesenchantcore.lib.jackson.annotation.JsonIgnore <fields>;
    @com.strikesenchantcore.lib.jackson.annotation.JsonCreator <methods>;
    @com.strikesenchantcore.lib.jackson.annotation.JsonValue <methods>;
}

# Keep any classes that will be serialized/deserialized with Jackson
-keepclassmembers class * {
    public <init>(...);
    <fields>;
}

# --- CRITICAL: Preserve Bukkit Interface Implementations ---
-keepclassmembers class * implements org.bukkit.command.CommandExecutor {
    public <init>(...);
    public boolean onCommand(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[]);
    <fields>;
}

-keepclassmembers class * implements org.bukkit.command.TabCompleter {
    public <init>(...);
    public java.util.List onTabComplete(org.bukkit.command.CommandSender, org.bukkit.command.Command, java.lang.String, java.lang.String[]);
    <fields>;
}

# Improved EventHandler detection
-keepclasseswithmembers class * {
    @org.bukkit.event.EventHandler <methods>;
}

-keepclassmembers class * implements org.bukkit.event.Listener {
    public <init>(...);
    <fields>;
}

# Keep enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep serializable classes
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# Keep BBB_ placeholder fields
-keepclassmembers class com.strikesenchantcore.EnchantCore {
    private static final java.lang.String *BBB_*;
    static final java.lang.String *IIllIIllIIllIlIIIlIllIlIIIlIIlIIlIIlllIlIIlIIIllIIlllIIllIIlIlllIIllIIIlIIIlIIlllIIIlIIllIIlIIIlllIllIllIIlllIllIllIIllIIlIIlIlIIIlIIIllIIllIIllIIIlIIlIIlIlllIllIIlIIlIlllIIllIlIIllIIllIIIllIllIIllIIllIllIIlllIllIIllIl*;
    static final java.lang.String *IIllIlIllIIIllIIlllIIIllIlIIllIIIlIIIllIlllIlIlllIllIIIllIllIllIIIlllIIllIIlIIIlllIIllIIIllIIllIllIIIlllIIlllIIllIIIlllIlIllIIIlIIlllIlIIIlllIIllIIllIIIlllIIllIIIllIIIllIIIllIlIlllIIllIIIllIIlllIIllIIlIIlIIllIllIIllIlllIllIlIllIIIlllIllIll*;
    static final java.lang.String *IIllIIllIIllIllIIIllIIlllIIIllIllIIllIIIllIlllIlllIIllIIlIIIlIlIllIIlIIllIIlIIIlIllIlllIIllIllIIllIlIlIllIIllIIIllIIllIIIlllIlIIllIIlIllIIlIIlIIIlIIlIIlllIIlllIIllIIlIIllIIllIIlllIIlllIIlIlIIIlllIIIlllIIIlllIIlIIllIlIIlllIlllIll*;
    static final java.lang.String *IllIllIlllIIlIIlllIllIIlIIlllIIlIIlllIIllIIlIlIIllIIIllIIllIIllIIIlIIlIlIllIIIllIlllIllIIIlIIIllIIIllIIlIIlIIlIIllIIllIllIIlIIIllIIlIllIIlIIlIIllIlIIIllIllIIIllIIllIIIllIllIIlllIIllIIllIllIIlllIIllIIlllIlIIlIIIllIIlIIlIIl*;
    static final java.lang.String *IIlllIIlIllIIIllIllIIIlIlIllIllIIlIIlIllIIllIlIIlllIIIllIIIlIIlIIIllIIIlIlllIllIIlllIIllIIIlllIIllIIlIIlIIlIIIlIIIllIIlIlIIIllIIIllIIllIIIlllIIIllIIIllIIlIIlIlIllIllIlIIlllIlllIIIllIIlIIIlIllIlIllIlIllIIllIllIllIIlIl*;
}

# ===== AESTHETIC OBFUSCATION OPTIONS =====
# Use custom dictionaries with LLILLILILI patterns
-obfuscationdictionary dictionary.txt
-classobfuscationdictionary class-dictionary.txt
-packageobfuscationdictionary package-dictionary.txt

# Use longer, more complex class names by default
-keepattributes SourceFile,LineNumberTable,*Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod


# Use unique class member names (this is critical)
-useuniqueclassmembernames

# IMPORTANT: Disable aggressive overloading as it can cause remapping issues
# -overloadaggressively

-allowaccessmodification

# Disable optimization for stability
-dontoptimize