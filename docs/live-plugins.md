# Live plugins

The local plugin manager loads standalone plugin JARs while Microbot is running.
After starting a client that includes this capability, supported plugin JARs can
be added, updated, reloaded, or removed without restarting the client.

## Manage installed JARs

Open **Local Plugins** from Microbot's plugin panel:

- **Add JAR** imports a compiled plugin JAR and makes its configuration available.
- **Update JAR** replaces the selected plugin while retaining compatible settings.
- **Reload** creates a fresh plugin instance from its installed JAR.
- **Remove** stops and uninstalls the plugin. Saved settings are retained.
- **Rescan folder** detects added, replaced, and deleted JARs.
- **Change folder** selects and saves a different managed JAR directory. Plugins
  from the previous folder are unloaded, their JARs stay in place, and the new
  directory is scanned. The Hub's **Open Folder** button follows the selection.
- **Watch folder for changes** applies changes after two matching observations.
  Finish builds outside the watched directory, then copy the JAR with a temporary
  filename and rename it to `.jar`. A failed scan stops watching and reports the
  error; fix the JAR and enable watching again. Keep one JAR per plugin identity.

By default, installed JARs live in `~/.runelite/microbot-plugins/` (on Windows,
`%USERPROFILE%\.runelite\microbot-plugins`). The selected directory is saved in
the current RuneLite profile. The manager loads an immutable copy of each JAR so
Windows does not lock the installed file against replacement. Treat its `.runtime`
cache as internal storage.

A plugin's identity comes from its entry class or manifest, rather than its
filename. Renaming a JAR does not make it a different plugin. The manager stops
the previous instance before activating an update. Check the operation result if
an update or removal fails; a plugin whose shutdown fails must not be replaced by
a second running instance.

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
