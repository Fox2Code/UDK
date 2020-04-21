## Configuration

In `build.gradle` there is the `udk` block as represented here
```Groovy
udk {
    version = "1.15.2"
}
```
This block can contain many value  
**NOTE:** only the 4 first values are available in `udk.base`  
**\*** = Required

- **boolean** `keepFramesByDefault` (Default: true (except with `udk.puzzle`))  
  This value set if frames should be kept by default. If set to `false`
  the frames need to be recalculated before being loaded into the java VM.
  
- **List\<String\>** `keepFrames` (Default: empty list)  
  This value is being used when `keepFramesByDefault` is false to keep
  specific classes or packages frames, to target a package the string must end
  with `.` or `/` (example: `just/some/package/`)
  
- **boolean** `inline` (Default: true)  
  The value of this field is whenever the methods inlining is enabled or not.  
  Inlining has a small performance boost on your compiled code.
  
- **boolean** `laxCast` (Default: true)  
  This field allows disabling/enabling laxCast.
  laxCast help avoid `ClassCastException` on numbers by modifying bytecode equivalent
  of `((Integer)???).intValue()` to  `((Number)???).intValue()` this
  generally improve compatibility with different versions of the same code.
  
- **String** `version`**\*** (Default: null)  
  This field is the version of Minecraft to use.  
  It follows [Repacker](https://github.com/Fox2Code/Repacker) naming standards.
  
- **String** `main` (Default: null)  
  Main class to use when running `runClient` and `runClientJar`.  
  If null using default class provided by the current plugin.
  
- **String** `mainServer` (Default: null)  
  Main class to use when running `runServer` and `runServerJar`.  
  If null using default class provided by the current plugin.
   
- **String\[\]** `mainArgs` (Default: null)  
  Arguments to use when running `runClient` and `runClientJar`.  
  If null using default class provided by the current plugin.  
  This can also use `%value%` as described [here](#args-values).
  
- **String\[\]** `mainArgsServer` (Default: null)  
  Arguments to use when running `runServer` and `runServerJar`.  
  If null using default class provided by the current plugin.  
  This can also use `%value%` as described [here](#args-values).
  
- **String** `username` (Default: accountUsername)  
  This value is the default username for user that do not have set  
  their username yet via `defaultUsername` gradle task (see [Gradle Tasks](TASKS.md))

- **File** `runDir` (Default: ./run)  
  Directory where the game store save and options files in a testing environment.
  
- **boolean** `server` (Default: false)  
  If true UDK will extract the server jar instead of the client jar
  
- **boolean** `useStartup` (Default: true)  
  Whenever UDK use Startup to launch the game  
  (It's recommended to not modify this value)

- **boolean** `nativeServer` (Default: false)  
  Whenever UDK extract natives when launching the server. 
  
- **boolean** `open` (Default: false)  
  Open is a value to make all Minecraft classes public  
  and allow accessing anonymous classes and lambda methods  
  (Recommended for advanced developers only)

**NOTE:** After this note every argument is specific to `udk.puzzle`

- **String** `puzzleVersion`**\*** (Default: null)  
  PuzzleModLoader version to use for development environment

- **String** `modMain`**\*** (Default: null)  
  Main class of you mod.  
  Set `ModMain` in `META-INF/MANIFEST.MF`.

- **String** `modID`**\*** (Default: null)  
  ID of your mod.  
  Set `ModID` in `META-INF/MANIFEST.MF`.  

- **String** `modVersion` (Default: project.version)  
  Version of your mod.  
  Set `ModVersion` in `META-INF/MANIFEST.MF`.  

- **String** `modName`**\*** (Default: null)  
  Display name of your mod.  
  Set `ModName` in `META-INF/MANIFEST.MF`.  

- **String** `modHook` (Default: null)  
  Hook class name of your mod.  
  Set `ModHook` in `META-INF/MANIFEST.MF`.  

- **String** `modDesc` (Default: null)  
  Description of your mod.  
  Set `ModDesc` in `META-INF/MANIFEST.MF`.  

- **String** `modWebsite` (Default: null)  
  Website of your mod.  
  Set `ModWebsite` in `META-INF/MANIFEST.MF`.  

- **String** `modUpdateURL` (Default: null)  
  UpdateURL of your mod. (WIP)  
  Set `ModUpdateURL` in `META-INF/MANIFEST.MF`.  


### Args values

- `%run_dir%` => Run directory of the Game

- `%assets_dir%` => Assets directory of the game

- `%assets_index%` => Assets index of the game

- `%username%` => Current username

- `%mc_ver%` => Version of minecraft