# Live plugins

The local plugin manager loads standalone plugin JARs while Microbot is running.
After starting a client that includes this capability, supported plugin JARs can
be added, updated, reloaded, or removed without restarting the client.

## Manage installed JARs

Open **Local Plugins** from Microbot's plugin panel:

- **Add JAR** copies a plugin JAR into the plugin folder and loads it. Turn it on
  in the Microbot plugin list afterwards.
- **Update JAR** swaps the selected plugin for a newer JAR. Your settings are kept.
- **Reload** restarts the selected plugin from its JAR.
- **Remove** turns the plugin off and deletes its JAR file from the plugin folder.
  Your settings are kept.
- **Rescan folder** picks up JARs you added, replaced or deleted in the folder.
- **Change folder** switches to a different plugin folder. Every plugin in the
  current folder is turned off and unloaded, including plugins you installed from
  the Plugin Hub. Their files stay where they are. New Plugin Hub installs go to
  the new folder, and the Hub's **Open Folder** button opens it.
- **Watch folder for changes** loads a JAR about two seconds after it stops
  changing. Build somewhere else and copy the finished JAR into the folder. If a
  JAR can't be loaded, watching stops and the error is shown. Fix the JAR, then
  tick **Watch folder for changes** again. Keep one JAR per plugin.
- A Plugin Hub plugin that the Hub has blocked (disabled or a broken version)
  stays in the folder but is not loaded. Rescan tells you once, and watching
  carries on without it. Remove it in the Plugin Hub, or install a working
  version there.

By default, plugin JARs live in `~/.runelite/microbot-plugins/` (on Windows,
`%USERPROFILE%\.runelite\microbot-plugins`). The chosen folder is saved in your
current RuneLite profile. If you switch profiles, the other profile's folder is
used after you restart Microbot. Microbot runs a copy of each JAR from the
`.runtime` folder inside it, so you can replace the JAR while it is loaded. Don't
edit `.runtime` yourself.

Plugins are matched by their main class, not their file name, so renaming a JAR
doesn't make it a different plugin. If a plugin can't be stopped cleanly, Remove
still deletes its file and asks you to restart Microbot to finish.

At startup, Plugin Hub plugins that are disabled on the Hub, marked broken for
your version, or need a newer client are not loaded. Microbot lists them in the
"Microbot Plugins Not Loaded" notice, together with any JAR that failed to load.

## Build a standalone plugin JAR

Select the package containing one self-contained plugin and its entry class:

```text
./gradlew :client:pluginJar "-PpluginPackage=net.runelite.client.plugins.microbot.yourplugin" "-PpluginClass=net.runelite.client.plugins.microbot.yourplugin.YourPlugin"
```

This produces `runelite-client/build/libs/YourPlugin.jar`, containing that
package's compiled classes and resources. The task uses the project's Java 11
target and does not include the client or its libraries. It rejects shared API
and service packages. Helpers outside the selected package and third-party
dependencies are not copied automatically; package plugin-owned helpers with
the entry class or arrange their dependencies separately.

The JAR only loads into a client that no longer contains that package. A client
built from the same source tree already has those classes, so it rejects the JAR
with "Plugin bundles shared client classes". Move the plugin out of the client
first, then load the JAR into a client built without it.

The JAR manifest declares `Microbot-Plugin-Id` and `Microbot-Plugin-Class` using
the full entry class name. Keep the identity stable across versions. Older JARs
without these attributes can be discovered from their `@PluginDescriptor` entry
class. Package one plugin entry point per JAR.

## Plugin lifecycle

`startUp()` and `shutDown()` must be fast and idempotent. Stop scripts and
plugin-owned executors, unregister event subscribers, remove overlays and toolbar
components, and release plugin-owned resources. Avoid static references from
shared client code to plugin instances. A new class loader cannot clean up work
or callbacks that a plugin leaves behind.

Constructors, static initializers, and injection methods must not start work or
register listeners. Replacement prepares the new instance while the current
plugin is still active, so activation side effects belong in `startUp()`.

`Script` instances and their base executors are tracked automatically. Ordinary
`Script.shutdown()` remains restartable. Permanent removal cancels its tasks and
shuts down its executors, then waits on the manager's background worker. A
disposed script cannot restart; rollback creates a fresh instance.

Register additional resources owned exclusively by the plugin with
`PluginResources`, using the plugin's class loader:

```java
executor = PluginResources.registerExecutor(getClass().getClassLoader(),
    Executors.newSingleThreadExecutor());
worker = PluginResources.registerThread(getClass().getClassLoader(),
    new Thread(this::runWorker));
worker.start();
resource = PluginResources.registerCloseable(getClass().getClassLoader(), resource);
```

Keep registered resources in plugin fields because the registry uses weak
references. Register threads before starting them and executors before submitting
work. Do not register an injected/shared client executor or shared client thread.
Registered `AutoCloseable.close()` methods run on background threads and must
tolerate retries after an exception. Remove UI and client-thread resources in the
normal shutdown callback. Disposal requests cancellation for all managed workers,
then waits within one bounded timeout. If cleanup fails or work ignores
interruption, replacement is blocked and removal can retry cleanup; Java cannot
forcibly stop an arbitrary thread.

File I/O, class scanning, compilation, and waits belong off the client thread.
Use the existing plugin lifecycle and UI threading conventions when registering
or unregistering a plugin. Wait for actual shutdown conditions with bounded
timeouts; do not block the client thread or use fixed sleeps to await game state.

Live replacement covers standalone plugins and resources packaged inside their
own JARs. Changes to shared Microbot utilities, caches, RuneLite APIs, the hidden
always-on Microbot plugin, or the plugin manager itself still require rebuilding
and relaunching the client. Cross-plugin Java dependencies need explicit
lifecycle support; do not assume a dependent plugin survives replacement of
another plugin's class loader.
