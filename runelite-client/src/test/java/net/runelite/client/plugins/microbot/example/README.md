# ExamplePlugin Performance Tests

This directory contains tests for the ExamplePlugin which measures GameObject composition retrieval performance.

## Test Files

### 1. ExamplePluginPerformanceTest.java
**Type:** Unit Test
**Purpose:** Tests performance with mocked components
**Dependencies:** None (uses mocks)

This test validates that the GameObject composition caching optimization works correctly. It:
- Creates a mock scene with 1000+ game objects
- Measures time to retrieve all ObjectCompositions
- Verifies caching provides performance improvements
- Tests cache clearing functionality

**Run with:**
```bash
mvn test -Dtest=ExamplePluginPerformanceTest
```

Or in your IDE, right-click the test class and select "Run Test"

### 2. ExamplePluginIntegrationTest.java
**Type:** Integration Test
**Purpose:** Full end-to-end test with real RuneLite client
**Dependencies:** Valid OSRS credentials, network connection

This test performs a complete integration test:
1. Starts the RuneLite client
2. Logs into OSRS
3. Enables the ExamplePlugin
4. Waits for performance metrics to be collected
5. Verifies functionality
6. Shuts down cleanly

**⚠️ DISABLED BY DEFAULT** - This test is annotated with `@Ignore` because it requires credentials.

## How to Run

### Command Line

```bash
mvn test -DskipTests=false \
         -Dtest=ExamplePluginIntegrationTest \
         -Dtest.username=your_username \
         -Dtest.password=your_password \
         -Dtest.isMember=true
```

**Parameters:**
- `-Dtest.username` - Your OSRS username (required)
- `-Dtest.password` - Your OSRS password (required)
- `-Dtest.isMember=true` - Set to true for members, false for F2P (optional, default: false)

### IntelliJ IDEA

1. Right-click on the test class → "Modify Run Configuration..."
2. Add VM options:
   ```
   -Dtest.username=your_username
   -Dtest.password=your_password
   -Dtest.isMember=true
   ```
3. Remove the `@Ignore` annotation (line 42)
4. Click "Run"

## What Happens During the Test

The test automatically:
1. ✅ Creates a fresh profile named `test-[username]`
2. ✅ Deletes any existing profile with that name (ensures clean state)
3. ✅ Encrypts your password using Microbot's encryption
4. ✅ Sets up the profile for LoginManager
5. ✅ Starts the client and logs in
6. ✅ Enables the ExamplePlugin
7. ✅ Collects performance metrics
8. ✅ Verifies functionality
9. ✅ Deletes the test profile (cleanup)
10. ✅ Shuts down

**No manual profile setup required!**

## Performance Expectations

### Without Caching
- **5666 objects** × **20ms per lookup** = **~113 seconds**
- Each object requires a client thread call to retrieve composition

### With Caching (Current Implementation)
- **First lookup per unique ID:** ~20ms
- **Subsequent lookups:** <0.1ms (HashMap lookup)
- **Real-world improvement:** 10-100x faster depending on object ID distribution

### Example Scenario
If you have 5666 objects but only 200 unique object IDs:
- **Before:** 5666 × 20ms = 113 seconds
- **After:** (200 × 20ms) + (5466 × 0.1ms) = **~4.5 seconds** (25x faster!)

## Test Results

The performance test (`ExamplePluginPerformanceTest`) validates that:
- ✅ All objects can be retrieved and their compositions fetched
- ✅ Execution completes in under 5 seconds (with cache)
- ✅ Second run is significantly faster than first run (cache hit)
- ✅ Cache can be cleared and repopulated

## Troubleshooting

### Test fails with "Client is null"
- Ensure Microbot singleton is properly initialized
- Check that mocks are correctly set up in `@Before` method

### Integration test times out during login
- Verify credentials are correct
- Check network connectivity
- Ensure OSRS servers are online
- Look for 2FA or other login barriers

### Performance test fails threshold
- Cache may not be working correctly
- Check that `Rs2TileObjectModel.compositionOf` is using the cache
- Verify `ConcurrentHashMap` is being populated
- Run test in isolation (not in parallel with other tests)

## CI/CD Integration

For continuous integration:

```yaml
# Example GitHub Actions
- name: Run Unit Tests
  run: mvn test -Dtest=ExamplePluginPerformanceTest

# Integration tests should only run with secrets
- name: Run Integration Tests
  if: github.event_name == 'push' && github.ref == 'refs/heads/main'
  env:
    TEST_USERNAME: ${{ secrets.OSRS_TEST_USERNAME }}
    TEST_PASSWORD: ${{ secrets.OSRS_TEST_PASSWORD }}
  run: mvn test -Dtest=ExamplePluginIntegrationTest
```

## Contributing

When adding new performance tests:
1. Follow the existing pattern (unit test with mocks first)
2. Add integration tests for critical paths only
3. Document expected performance characteristics
4. Set reasonable timeout thresholds
5. Clean up resources in `@After` methods

## Related Files

- `/runelite-client/src/main/java/net/runelite/client/plugins/microbot/example/ExamplePlugin.java` - Plugin implementation
- `/runelite-client/src/main/java/net/runelite/client/plugins/microbot/example/ExampleScript.java` - Performance measurement script
