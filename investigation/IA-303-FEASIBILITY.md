# IA-303 — feasibility: a cleaner guard than wrapping every handler

**Recommendation up front: use option 1 — a decorating `BWEventListener` registered as
`new BWClient(new GuardedListener(bot, log))`.** `Bot.java` changes by exactly one statement, the
guard is one new file of uniform one-line delegations plus a small log writer, it fails closed when
handlers are added later, and it costs one virtual call per event. The per-step `onEnd` requirement
is met by a separate three-line change in `LearningManager.onEnd`, which is the only one of the four
`onEnd` steps that can currently propagate.

Everything below is anchored to a file I opened (repo paths are relative to `/mnt/c/InfestedArtosis`)
or to the JBWAPI 2.2.0 jar/ sources, and labeled **verified** or **inferred**. The jar was read by
parsing the class files with `python3` (no `javap`/JDK exists in WSL; only a Windows JRE at
`/mnt/c/Program Files/Java/jre1.8.0_261`, which has no `javap`).

## 1. What I verified

### 1.1 The JBWAPI dispatch chain — the fact that opens the door

**Verified from the jar** (`/mnt/c/Users/bradl/.m2/repository/com/github/JavaBWAPI/JBWAPI/2.2.0/JBWAPI-2.2.0.jar`,
bytecode-parsed):

- `bwapi.BWEventListener` is an **interface** (access `0x0601`) with exactly **18 methods, all
  returning `void`**: `onStart()`, `onEnd(boolean)`, `onFrame()`, `onSendText(String)`,
  `onReceiveText(Player,String)`, `onPlayerLeft(Player)`, `onNukeDetect(Position)`,
  `onUnitDiscover(Unit)`, `onUnitEvade(Unit)`, `onUnitShow(Unit)`, `onUnitHide(Unit)`,
  `onUnitCreate(Unit)`, `onUnitDestroy(Unit)`, `onUnitMorph(Unit)`, `onUnitRenegade(Unit)`,
  `onSaveGame(String)`, `onUnitComplete(Unit)`, `onPlayerDropped(Player)`.
  **There are no non-void methods**, so a proxy has no return-value problem, and no method declares
  checked exceptions (their descriptors are all `V`, and `EventHandler.operation` calls them
  bare, which would not compile otherwise).
- `bwapi.DefaultBWListener` is a pure adapter: `public class` implementing `BWEventListener` with
  `<init>` plus the same 18 methods as empty `public void` bodies.
- `bwapi.BWClient` has exactly one public constructor: `BWClient(BWEventListener)` (it does
  `Objects.requireNonNull` and stores the field). Its `startGame()` calls
  `startGame(BWClientConfiguration.DEFAULT)`. **No method of `BWClient` has any exception handler**
  (I walked every `Code` attribute's exception table: all `NONE`).

**Verified from the JBWAPI 2.2.0 sources on GitHub** (raw files at tag `2.2.0`, cross-checked against
the jar's structure):

- `BWClientConfiguration`: default `async = false`, `maxFrameDurationMs = 40`; the Builder javadoc
  gives ~42.86 ms between frames at "fastest". So this bot runs the **synchronous** wrapper.
- `BotWrapper` (sync): `onFrame()` → `handleEvents()` → for each event
  `EventHandler.operation(eventListener, botGame, event)`. **No try/catch anywhere.**
- `EventHandler.operation`: one `switch` mapping `MatchStart` → `onStart()` (after `game.init()`),
  `MatchFrame` → `onFrame()`, `MatchEnd` → `onEnd(v1 != 0)`, and the unit events to the per-unit
  handlers. `onEnd` is delivered as an ordinary event **inside the same loop**.
- `BWClient.startGame(config)` runs the frame loop **on the main thread**:
  `while (liveGameData.isInGame()) { botWrapper.onFrame(); ... } botWrapper.endGame();`
  A throw from any handler therefore escapes `EventHandler` → `BotWrapper` → `BWClient.startGame` →
  `main` itself, skipping `endGame()` — so **`onEnd` is never delivered and the learning row is
  never written**. This is the ticket's failure mode, verified end-to-end rather than assumed.
- `BotWrapperAsync` (only used if `async=true`, which this bot does not set): the bot thread catches
  `Throwable`, stores it in `lastBotThrow`, and dies silently; the main thread's `asyncOnFrame()`
  then **rethrows it as `new RuntimeException(lastThrow)`**. So even JBWAPI's own "handling" mode
  ends in the exception escaping to `main`. Nothing in JBWAPI 2.2.0 protects the bot.

### 1.2 The bot side

**Verified in `src/main/java/Bot.java`:**

- Line 32: `public class Bot extends DefaultBWListener`. Lines 189–193: `main` does exactly
  `Bot bot = new Bot(); bot.bwClient = new BWClient(bot); bot.bwClient.startGame();` (the brief said
  "around 190-192"; on disk it is 190–192).
- `Bot` overrides **10 of the 18** interface methods (the brief's "twenty handlers" is not what is
  on disk): `onStart` :52, `onFrame` :106, `onUnitHide` :123, `onUnitShow` :128, `onUnitCreate` :137,
  `onUnitComplete` :144, `onUnitDestroy` :157, `onUnitRenegade` :165, `onUnitMorph` :171,
  `onEnd` :178.
- Every manager field is a plain declaration with no initializer (`Bot.java:33-50`), so **`new Bot()`
  is safe to construct in a unit test**, and each handler's first dereference is a null-manager NPE:
  `onStart` at :54 (`bwClient.getGame()`), `onFrame` at :110 (`informationManager.onFrame()`),
  `onUnitHide` :124, `onUnitRenegade` :166, `onUnitMorph` :172 (same), `onUnitDestroy` :158
  (`combatTelemetry`), `onEnd` :179 (`learningManager`). `onUnitShow` :129, `onUnitCreate` :138 and
  `onUnitComplete` :145 dereference the **parameter** first (`unit.getType()` / `unit.getPlayer()`),
  so with a `null` argument they NPE on their first statement too — the injected-throw test works
  with `null` unit arguments and no mocking framework.
- `onEnd` order (`Bot.java:178-187`): `learningManager.onEnd` :179, then `planEventLogger.onEnd`
  :181, `squadDecisionLogger.onEnd` :184, `combatTelemetry.onEnd` :186.

**Verified elsewhere in `src/main/java/`:**

- `telemetry/TelemetryWriter.java:16-19` — the doctrine: the bot must not write to stdout or
  stderr; the first write failure disables the writer permanently (:82-85). Write directory is the
  **relative** path `bwapi-data/write/` (:23). The class is package-private (`final class`, :21),
  so a guard outside the `telemetry` package cannot reuse it.
- `learning/LearningManager.java:113-122` — `onEnd` builds a `GameRecord` (`createGameRecord`,
  unguarded) and appends; the `try` catches **`IOException` only** (:120-121). A `RuntimeException`
  from `createGameRecord` propagates and would skip the three telemetry teardowns behind it.
- `learning/LearningHistoryRepository.java:19,34-39` — the learning row is a CSV append to the
  relative path `bwapi-data/write/<opponent>.csv`. This is the row that is lost on a crash.
- All three telemetry teardowns already self-catch: `CombatTelemetry.onEnd` catches
  `RuntimeException` (`telemetry/CombatTelemetry.java:143-145`), `PlanEventLogger.onEnd` catches
  `Exception` (`telemetry/PlanEventLogger.java:115`), `SquadDecisionLogger.onEnd` catches
  `RuntimeException` (`telemetry/SquadDecisionLogger.java:94`). **`LearningManager.onEnd` is the
  only propagating step in `Bot.onEnd`.**

### 1.3 Build constraints

**Verified in `pom.xml`:**

- Java 8: `maven.compiler.source/target 1.8` (:20-21) and compiler plugin `<source>8</source>
  <target>8</target>` (:67-68), maven-compiler-plugin 3.15.0 (:65).
- `<annotationProcessorPaths>` contains exactly one entry: Lombok (:69-75). **The Lombok version on
  disk is 1.18.46** (:38 and :74) — the brief says 1.18.42; the pom disagrees, and it does not
  change any conclusion.
- No mocking framework: the only test-scoped dependency is `junit-jupiter` 5.14.4 (:47-52). The 48
  existing test classes are plain JUnit 5; `telemetry/TelemetryLogTest.java:18-31` establishes the
  `@TempDir` + injected-writer-directory pattern for file-writer tests.
- `mainClass` is bare `Bot` (:89) — `Bot.java` lives in the **default package**
  (`src/main/java/Bot.java`, alongside `Debug.java` and `AutoObserver.java`), so a guard class in
  the default package needs **zero import changes**.
- Checkstyle (`checkstyle.xml`, severity `error` at :8): line length 150 (:17-20),
  `LambdaBodyLength` max 15 (:44-46), `MissingSwitchDefault` (:88), `OneTopLevelClass` (:95),
  `ParameterNumber` max 8 with `ignoreOverriddenMethods` (:40-43). No Javadoc requirement. The
  plugin is bound to `validate` (pom.xml:104-131).

### 1.4 Frame budget (measured)

From `tmp/games/GAME_KSV3501{A,B,D}` and `GAME_KQGC303{4,5}` `logs_0/frames.csv`
(columns verified: `frame_count, frame_time_max, frame_time_avg, num_actions, ...`), dropping each
game's first sample (frame-0 map analysis, e.g. KSV3501D row 1: `frame_time_max` 8989 ms):

- Pooled **n = 3425 samples: per-frame `frame_time_avg` p50 = 1.17 ms, p90 = 2.04 ms, p99 = 3.67 ms**.
- Per-game medians 1.00–1.71 ms; worst single non-frame-0 sample avg 32.62 ms.

The bot spends ~1–2 ms of compute per frame out of the ~42.86 ms frame interval (JBWAPI Builder
javadoc) and the 40 ms `maxFrameDurationMs` default. That is the denominator for the proxy-cost
question. **No telemetry counts listener invocations** — `num_actions` is commands sent, not events
received — so events-per-frame is an estimate, not a measurement.

## 2. The options

### Option 1 — a decorating `BWEventListener` (recommended)

One class implementing the interface, holding the real `Bot` as a `BWEventListener` field,
delegating each of the 18 methods inside one shared `run(handler, body)` try/catch. Registration is
`new BWClient(new GuardedListener(bot, new GuardLog()))` — **`Bot.java` changes by one statement**
(line 191), zero imports (same package), zero handler rewrites.

- The 18 delegations are uniform one-liners in a single new file, written once, never touched again.
  Note honestly: this *is* the ticket's `guard(String, Runnable)` shape — but living in one
  mechanical file instead of being threaded through every handler body in `Bot`, which is the
  churn the operator objects to.
- **Fails closed.** `GuardedListener implements BWEventListener`, so if JBWAPI ever adds method 19,
  the class does not compile until a delegation is added (loud, mechanical). And any *new override
  Bot adds later* (e.g. `onNukeDetect`) is automatically guarded, because the delegation for it
  already exists.
- Runtime cost: one extra virtual call per listener invocation, no reflection, no allocation
  (method references like `delegate::onFrame` are cached). **Inferred:** ~1–2 ns per call; against a
  measured 1.17 ms median frame, unmeasurable.
- Testability: direct — `new GuardedListener(new Bot(), log)` with `@TempDir` (section 4.2).
- Build impact: none. Java 8: trivially yes.

### Option 2 — `java.lang.reflect.Proxy` on `BWEventListener`

One `InvocationHandler`; `Proxy.newProxyInstance(...)` produces the listener. Roughly 15–20 lines
before the log writer.

- **Coverage is automatic and future-proof** — every interface method, including ones added in later
  JBWAPI versions, with no per-method code. Also fails closed.
- **Cost, quantified.** The proxy class itself is generated bytecode that forwards to
  `handler.invoke(proxy, method, args)` — the hot path then either switches on `method.getName()` and
  calls the concrete method directly (best case) or does `method.invoke(bot, args)`
  (+`InvocationTargetException` unwrapping — a delegate throw arrives wrapped; `e.getCause()` is the
  real exception). **Inferred, not measured** (no JDK in WSL to benchmark): 10–50 ns per call
  including the `Object[]` varargs allocation and boxing. Events per frame are also an estimate
  (section 1.4) — call it 10–100 typical, a few hundred in a big fight. At 50 ns × 200 events =
  **10 µs, i.e. ~0.9 % of the median 1.17 ms frame and ~0.02 % of the 42.86 ms interval**. By
  throughput this is negligible; the honest objections are qualitative: permanent indirection on
  every future profile, string-keyed dispatch, two synthetic stack frames per call, and a debugger
  that steps you into `invoke` before it steps you into `onFrame`.
- Return values: **moot — all 18 interface methods return `void`** (verified), so the handler
  returns `null` everywhere. `hashCode`/`equals`/`toString` on the proxy must not explode; the
  simplest handler passes non-interface methods through via `method.invoke`.
- Testability: excellent — the handler can be tested with a five-line anonymous listener that
  throws; no `Bot`, no BWAPI.
- Build impact: none. Java 8: yes (`Proxy` is JDK 1.3+).

Verdict: technically clean and cheap enough, but it buys "no 18 one-liners" at the price of making
the most-debugged path in the bot reflective. The one-liners are not a cost worth paying to avoid.

### Option 3 — annotation + annotation processor generating the delegate

An `@Guarded` annotation on `Bot`; a processor reads `BWEventListener` (via the annotation mirror)
and emits a `GuardedListener` equivalent to option 1's class at compile time.

Evaluated properly, because the operator asked about it by name:

- **It is feasible on this build without a new plugin.** `<annotationProcessorPaths>`
  (pom.xml:69-75) accepts multiple `<path>` entries, so the processor is a dependency, not a plugin;
  on JDK 8 annotation processing runs by default (`-proc:full` is a JDK 23+ concern, correctly noted
  as unavailable in CI). Generated sources land in `target/generated-sources/annotations` and are
  compiled in the same `javac` run. Lombok coexistence is unproblematic — two independent
  processors touching disjoint classes; ordering is unspecified but irrelevant here.
- **It buys nothing over option 1.** This is the decisive point, not a dismissal: Lombok earns a
  processor by generating *many* members from *many* annotations. A guard processor generates
  **one fixed class with zero parameters** — the interface is fixed by JBWAPI, the behavior is
  fixed by the ticket. The generated output is line-for-line what you would check in by hand. You
  would write ~250–400 lines of processor, service registration, and build wiring to avoid writing
  ~100 lines once.
- **Fail-closed is identical to option 1**: `Bot.main` references the generated class (compile error
  if generation stops), and the generated class implements the interface (compile error if JBWAPI
  adds a method). The processor's one theoretical advantage — automatic coverage of new interface
  methods — is matched by the compiler's own interface check.
- Costs option 1 doesn't have: a second artifact to build and version; PR review shows an annotation
  instead of readable guard code; IDE navigation jumps into generated sources; checkstyle runs at
  `validate` (pom.xml:125-128), which precedes `generate-sources` on a clean build but **does** lint
  the generated root on warm incremental builds — inconsistent linting depending on build state.

Verdict: dominated by option 1. Same runtime, same failure mode, strictly more machinery and review
friction.

### Option 4 — AspectJ / weaving

An aspect with a pointcut like `execution(* Bot.on*(..))` and after-throwing advice.

- **Build impact is the disqualifier, concretely.** Compile-time weaving requires the
  `aspectj-maven-plugin` — a second compiler-like toolchain in the build, which must be ordered
  after Lombok compilation, plus an `aspectjrt` runtime dependency shipped in the jar
  (pom.xml:60-102 currently has exactly the three plugins the project needs). Load-time weaving
  needs `-javaagent` on the tournament JVM's command line, which this repo does not control — the
  entry point is `Bot.main` (pom.xml:89) but agent flags are set by the harness, not by `main`.
- Runtime: after JIT, advice call overhead is comparable to the proxy (tens of ns) — **inferred**;
  not the reason to reject it.
- Debugging: stack traces grow advice frames; breakpoints inside advice require the aspect source
  to be on the debug source path; behaviour differs between woven and unwoven test runs unless
  tests are woven too.
- **Fail mode is semi-open:** coverage is a *textual* pattern. `execution(* Bot.on*(..))` happens to
  match all 18 interface methods today, but nothing ties the pointcut to the interface — a handler
  is guarded because of its name, not because it is a handler.
- Java 8: supported by current AspectJ, but it is a toolchain with its own release cadence to track.

Verdict: too heavy, for reasons that are specific (new plugin + weave ordering, or an agent flag we
cannot set, plus name-based rather than interface-based coverage), not aesthetic.

### Option 5 — the ticket's `guard(String, Runnable)` in `Bot` (baseline)

Every handler becomes `guard("onFrame", this::frame)` with the body moved to a private method.

- `Bot.java` churn: 10 handlers rewritten into 10 private methods plus 10 guard call sites
  (`Bot.java:52-187`), roughly 60–80 lines touched, with a string literal repeated per call site —
  exactly the boilerplate the operator rejects.
- Runtime: one lambda allocation per guarded call (**inferred:** escape-analysis usually
  stack-allocates it; irrelevant either way).
- **Fails open.** A handler added later without `guard(...)` is silently unguarded — the failure
  that produced IA-239/IA-302 can silently return. There is no compiler or test that can cheaply
  enforce the convention (short of reflection-over-source tricks).
- Testability: `guard` itself is directly testable; the injected-throw test targets the same public
  handlers.
- It *does* have one genuine advantage: per-step granularity inside `onEnd` falls out naturally
  (`guard("onEnd:learning", ...)` at each of `Bot.java:179-186`). Section 4.3 gets that granularity
  more cheaply.

### Also considered and rejected

- **Switch to `BotWrapperAsync` and poll `getLastBotThrow()`**: async mode *rethrows* the stored
  throwable on the main thread (verified, section 1.1), and `getLastBotThrow` is package-private to
  `bwapi`. It also changes frame-timing semantics wholesale. Not a guard.
- **`Thread.setDefaultUncaughtExceptionHandler`**: fires only *after* the thread is dead — it can
  improve the crash record but cannot keep the frame loop alive or deliver `onEnd`. A possible
  complement, not a substitute.

## 3. Comparison

| | 1 Decorator | 2 Proxy | 3 Annotation proc. | 4 AspectJ | 5 Ticket `guard` |
|---|---|---|---|---|---|
| `Bot.java` lines changed | **1** (line 191) | ~4 (in `main`) | ~2 | 0 | ~60–80 (all 10 handlers) |
| Total new code | ~150 (2 files) | ~50 (1 file) | ~300+ generating ~100 | ~30 + runtime dep + plugin | ~20 |
| Hot-path cost | 1 virtual call, ~1–2 ns (inferred) | ~10–50 ns + varargs garbage per event (inferred) | none (generated code = option 1) | ~tens of ns after JIT (inferred) | 1 lambda alloc (inferred) |
| vs 1.17 ms median frame | unmeasurable | ~0.02–0.9 % at 10–200 events/frame | unmeasurable | unmeasurable | unmeasurable |
| Build-system impact | none | none | `annotationProcessorPaths` entry + generated-source/IDE/checkstyle friction | **new plugin + weave step**, or `-javaagent` we can't set | none |
| Java 8 | yes | yes | yes (JDK 8 runs processors by default) | yes, with toolchain risk | yes |
| Testability | direct, `@TempDir`, no mocks | direct, no `Bot` needed | test the generated class; processor needs its own harness | awkward (woven tests) | direct |
| New handler added later, guard forgotten | **Closed**: delegation already exists; new interface method = compile error | **Closed, automatic**: no code to forget | Closed (regeneration + interface check) | Semi-open: guarded by name pattern, not by interface | **Open**: silently unguarded |

## 4. Recommended sketch

### 4.1 The guard point and the cap

Two new files in the default package beside `Bot.java`. Java 8, house style (no comments in bodies,
checkstyle-clean shapes; `LambdaBodyLength` ≤ 15 is satisfied — the lambdas are single statements).

```java
import bwapi.BWEventListener;
import bwapi.Player;
import bwapi.Position;
import bwapi.Unit;

/**
 * Delegating listener that stops a handler throw from killing the JBWAPI client loop. One CSV row
 * per swallowed failure is written to bwapi-data/write/guard.csv; after MAX_FAILURES_PER_GAME
 * failures in one game the writer is disabled but failures keep being swallowed, so the game
 * reaches MatchEnd and the learning row is still written.
 */
final class GuardedListener implements BWEventListener {
    private static final int MAX_FAILURES_PER_GAME = 3;

    private final BWEventListener delegate;
    private final GuardLog log;
    private int failures;

    GuardedListener(BWEventListener delegate, GuardLog log) {
        this.delegate = delegate;
        this.log = log;
    }

    @Override
    public void onStart() {
        failures = 0;
        run("onStart", delegate::onStart);
    }

    @Override
    public void onFrame() {
        run("onFrame", delegate::onFrame);
    }

    @Override
    public void onUnitShow(Unit unit) {
        run("onUnitShow", () -> delegate.onUnitShow(unit));
    }

    @Override
    public void onUnitDestroy(Unit unit) {
        run("onUnitDestroy", () -> delegate.onUnitDestroy(unit));
    }

    // ... 14 more delegations, identical shape, one per remaining interface method:
    // onEnd(boolean), onSendText, onReceiveText, onPlayerLeft, onNukeDetect, onUnitDiscover,
    // onUnitEvade, onUnitHide, onUnitCreate, onUnitMorph, onUnitRenegade, onSaveGame,
    // onUnitComplete, onPlayerDropped.

    private void run(String handler, Runnable body) {
        try {
            body.run();
        } catch (RuntimeException e) {
            failures++;
            if (failures <= MAX_FAILURES_PER_GAME) {
                log.record(handler, e);
            }
        }
    }
}
```

```java
// Bot.java — the only change to an existing handler file:
bot.bwClient = new BWClient(new GuardedListener(bot, new GuardLog()));
```

Decisions embedded there, stated plainly:

- **Catch `RuntimeException`, not `Throwable`/`Error`.** No interface method declares checked
  exceptions (verified), so `RuntimeException` is everything a handler can legally throw. An `Error`
  (OOM, `StackOverflowError`) should still kill the bot — swallowing those produces a zombie with a
  broken JVM.
- **Cap semantics (ticket scope item 2): a record cap, not a death switch.** After 3 recorded
  failures the guard stops writing but keeps swallowing. The alternative — rethrow at the cap —
  reintroduces exactly the loss the ticket exists to fix: a persistent defect would kill the frame
  loop, skip `MatchEnd`, and lose the learning row. With the record cap, a persistent defect yields
  a degraded game that still ends, still writes its learning row, and is identifiable afterwards by
  its `guard.csv` rows (so polluted rows can be filtered from UCB history). The count resets in
  `onStart`, i.e. per game.
- **The per-call string exists here too** — but once per interface method in one mechanical file,
  never per handler body in `Bot`.

### 4.2 How the crash record reaches the writer

`GuardLog` follows the `TelemetryWriter` doctrine exactly (TelemetryWriter.java:16-19, 82-85),
because `TelemetryWriter` itself is package-private to `telemetry` (:21) and cannot be reused from
the default package:

```java
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * Append-only CSV of swallowed handler failures under bwapi-data/write/. The bot never writes to
 * stdout or stderr, so the guard's record lives here. The first IO failure disables the log
 * permanently: a write that failed once will keep failing, and the guard must never become the
 * thing that kills the bot.
 */
final class GuardLog {
    private static final String HEADER = "timestamp_millis,handler,exception,message,top_frame\n";
    private final Path path;
    private boolean disabled;

    GuardLog() {
        this(Paths.get("bwapi-data/write/guard.csv"));
    }

    GuardLog(Path path) {
        this.path = path;
    }

    void record(String handler, RuntimeException e) {
        if (disabled) {
            return;
        }
        StackTraceElement[] stack = e.getStackTrace();
        String top = stack.length > 0 ? stack[0].toString() : "";
        String row = System.currentTimeMillis() + "," + handler + "," + e.getClass().getName() + ","
                + csv(e.getMessage()) + "," + csv(top) + "\n";
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            boolean fresh = !Files.exists(path);
            Files.write(path, ((fresh ? HEADER : "") + row).getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException writeFailure) {
            disabled = true;
        }
    }
}
```

- Path is relative (`bwapi-data/write/`), identical to the telemetry and learning writers
  (TelemetryWriter.java:23, LearningHistoryRepository.java:19), so it resolves wherever the harness
  runs the bot. Flush-per-failure is fine — failures are rare by definition; this is not the frame
  hot path.
- Frame count in the row would be nice; it costs one package-private accessor on `Bot`
  (`game.getFrameCount()`, null-guarded) passed as a `LongSupplier` — optional, not sketched.
- **Acceptance test, no mocking framework**, following TelemetryLogTest.java:18-31:

```java
class GuardedListenerTest {
    @Test
    void bareBotHandlerThrowIsSwallowedAndRecorded(@TempDir Path directory) {
        GuardedListener guard = new GuardedListener(new Bot(), new GuardLog(directory.resolve("guard.csv")));

        guard.onFrame();
        guard.onUnitShow(null);
        guard.onUnitDestroy(null);
        guard.onEnd(false);

        assertTrue(Files.exists(directory.resolve("guard.csv")));
    }

    @Test
    void recordingStopsAtCapButSwallowingContinues(@TempDir Path directory) {
        GuardLog log = new GuardLog(directory.resolve("guard.csv"));
        GuardedListener guard = new GuardedListener(new Bot(), log);

        for (int i = 0; i < 10; i++) {
            guard.onFrame();
        }

        assertEquals(3, Files.readAllLines(directory.resolve("guard.csv")).size() - 1);
    }
}
```

Every call NPEs inside the bare `Bot` (section 1.2) — `onUnitShow(null)` at Bot.java:129,
`onUnitDestroy(null)` at Bot.java:158, `onEnd(false)` at Bot.java:179 — so the test needs no BWAPI
connection, no mock, and no game.

### 4.3 The `onEnd` separation

A boundary guard — options 1–4 alike — catches an `onEnd` throw but **cannot resume the remaining
steps**, so if `learningManager.onEnd` throws, the three telemetry teardowns behind it never run
(Bot.java:179-186). The requirement "neither can skip the other" is therefore met inside the
learning write itself, by adopting the pattern its three siblings already use:

```java
// learning/LearningManager.java:113 — the only change:
public void onEnd(boolean isWinner) {
    try {
        GameRecord gameRecord = createGameRecord(isWinner, System.currentTimeMillis());
        recordAccumulator.apply(opponentRecord, gameRecord);
        historyRepository.append(gameRecord);
    } catch (IOException e) {
    } catch (RuntimeException e) {
        historyRepository.append(GameRecord.failedGameRecord(isWinner));
    }
}
```

(Whether a fallback row is written on failure, or nothing at all, is a ticket-level decision; the
structural point is that the write self-catches, exactly like CombatTelemetry.java:143-145,
PlanEventLogger.java:115, and SquadDecisionLogger.java:94 already do. The existing `IOException`
swallow at LearningManager.java:120-121 is kept.) With this, all four `Bot.onEnd` steps are
individually non-propagating, the decorator is a pure backstop, and ordering stops mattering.
Options 2–4 need this same change; option 5 could instead write four per-step `guard(...)` calls at
Bot.java:179-186, at the cost of more call-site boilerplate in the file the operator wants left
alone.

## 5. Acceptance criteria, option by option

**Injected-throw test (`new Bot()`, no mocking framework).** All five options pass it: the guard is
exercised with a bare `Bot` whose handlers NPE on their first statement (section 1.2), asserting
non-propagation plus the written record. Option 2 is the easiest to test (any throwing listener);
option 4 the hardest (tests must be woven or the aspect unit-tested in isolation); option 3 tests
the generated class, which only exists after a build. Option 5 tests the same public handlers.

**`onEnd` learning write vs telemetry teardown.** Options 1–4 require the `LearningManager.onEnd`
self-catch of section 4.3 (a boundary guard cannot resume skipped steps). Option 5 can express it
per-step at `Bot.java:179-186` with four `guard(...)` calls. Either way `LearningManager.onEnd` is
the only step that currently propagates — verified, not assumed.

**Per-game failure cap (scope item 2).** Implementable under all five; section 4.1 shows the
decorator's version and argues record-cap-with-continued-swallowing over rethrow-at-cap.

**No stdout/stderr.** All options write via a `GuardLog`-style file writer under
`bwapi-data/write/` (TelemetryWriter.java:16-19, 23). The catch block must not call
`printStackTrace()` — worth an explicit line in the ticket, because it is the natural thing a
reviewer would add.

## 6. Recommendation and its main risk

**Adopt option 1: the decorating listener**, plus the `LearningManager.onEnd` self-catch. It is the
only option that simultaneously: leaves `Bot.java` untouched except one statement (Bot.java:191);
keeps the hot path free of reflection and allocation; adds no build machinery; fails closed on both
kinds of future change (new `Bot` override, new interface method); and is unit-testable with the
project's existing JUnit-5-and-`@TempDir` idiom and no mocking framework. The proxy (option 2) is
the runner-up and is quantitatively defensible — ~10–50 ns per event against a 1.17 ms median frame
— but it spends permanent indirection on the most-debugged call path to save 18 one-line
delegations, which is a bad trade. Options 3 and 4 are strictly dominated by 1 for the reasons in
section 2. Option 5 works but is the churn the operator rejected and fails open.

**Main risk:** a swallowed exception leaves game state half-mutated, and the bot keeps playing on
it — the guard converts a loud death into a quiet degradation, which can pollute learning history
with degraded-game rows (this is the known cost of the record-cap choice in 4.1). Mitigations: the
cap bounds record spam; every degraded game is identifiable by its `guard.csv` rows, so batch
analysis can exclude them from UCB statistics; and `Errors` are deliberately not swallowed. A
secondary risk: the 18 delegations are boring code that a future editor might "simplify" — the
interface-implements relationship is what keeps them honest, and it should be called out in the
class javadoc.

## 7. What I could not determine

1. **The ticket's exact scope items and acceptance wording.** The Atlassian MCP server is
   unavailable (connection error); this report relies on the scope items as quoted in the brief
   (notably "per-game failure cap" as scope item 2). To close: read IA-303 in Jira.
2. **Measured proxy overhead.** No JDK exists in WSL (`javap`/`javac` absent; only a Windows JRE at
   `/mnt/c/Program Files/Java/jre1.8.0_261`), so the 10–50 ns figure is an informed estimate, not a
   benchmark. To close: a JMH microbenchmark of `Proxy` vs direct interface dispatch on the CI JDK 8.
3. **Listener invocations per frame.** No telemetry counts events (`frames.csv` columns verified —
   `num_actions` is commands sent). To close: add a counter to the guard itself for one batch —
   the decorator makes this a two-line addition.
4. **How the harness actually scores a dead bot.** The brief says "ordinary loss";
   TelemetryWriter.java:17 says the harness scores an escaped exception as "a crash". Both are
   project statements and they are not identical; I could not observe the harness from the repo.
   To close: one controlled batch run with a deliberately throwing build.
5. **Runtime location of `bwapi-data/write/`.** Not present in this worktree (globbed: no
   `bwapi-data/`, no `bot.log` under `tmp/`); the path is relative (TelemetryWriter.java:23) and
   resolves against the game's working directory in the tournament environment. To close: inspect a
   tournament sandbox, or confirm from an existing batch artifact outside this worktree.
6. **Whether the tournament launcher permits `-javaagent`** (would only matter for option 4's LTW
   variant). To close: ask the harness operator — though option 4 is rejected regardless on
   build-weight grounds.

## 8. Discrepancies noted against the brief

- Brief says Lombok 1.18.42; `pom.xml:38` and `:74` say **1.18.46**. No conclusion changes.
- Brief says "twenty handlers"; `Bot` overrides **10** of the interface's **18** methods
  (Bot.java:52-187).
- Brief cites `telemetry/TelemetryWriter.java:15-19` for the no-stdout rule; on disk the statement
  is the javadoc at :16-19.

IA-303 FEASIBILITY COMPLETE
